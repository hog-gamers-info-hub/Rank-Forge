package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrFieldMapper
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrInput
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowClassifier
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowClassification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Production whole-position PP route. */
class AndroidMatchResultPositionOcrPreviewRunner(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localFileResolver: MatchResultOcrPreviewLocalFileResolver,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val positionCropGenerator: AndroidMatchResultPositionCropGenerator,
    private val paddleRecognizer: AndroidMatchResultPositionPaddleOcrRecognizer,
    private val fieldMapper: MatchResultPositionOcrFieldMapper = MatchResultPositionOcrFieldMapper(),
    private val rowOcrPreprocessor: AndroidMatchResultRowOcrPreprocessor = AndroidMatchResultRowOcrPreprocessor(),
) : MatchResultOcrPreviewRunner {
    override suspend fun process(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultOcrPreviewProcessingResult = withContext(Dispatchers.IO) {
        val owner = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            ?: return@withContext MatchResultOcrPreviewProcessingResult.MissingAsset
        val asset = try {
            assetRepository.getByIdentityAndOwner(identity, owner)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return@withContext MatchResultOcrPreviewProcessingResult.MissingAsset
        } ?: return@withContext MatchResultOcrPreviewProcessingResult.MissingAsset
        val crop = asset.confirmedCropOrNull()
            ?: return@withContext MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return@withContext MatchResultOcrPreviewProcessingResult.InvalidCrop
        val pixelCrop = when (val validation = OcrCropValidator.validate(
            crop = crop,
            dimensions = dimensions,
            profile = OcrCropValidationProfiles.MatchResult,
        )) {
            is OcrCropValidationResult.Valid -> validation.pixelCrop
                ?: return@withContext MatchResultOcrPreviewProcessingResult.InvalidCrop
            is OcrCropValidationResult.Invalid ->
                return@withContext MatchResultOcrPreviewProcessingResult.InvalidCrop
        }
        val file = try {
            localFileResolver.resolve(asset.localRelativePath)
        } catch (_: Throwable) {
            null
        }?.takeIf { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) }
            ?: return@withContext MatchResultOcrPreviewProcessingResult.MissingLocalOriginal
        val source = decodeCrop(file, pixelCrop)
            ?: return@withContext MatchResultOcrPreviewProcessingResult.DecodeFailed
        val allowUpperFallback = identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER &&
            !hasConfirmedLowerAsset(identity, owner)
        try {
            val generated = when (val result = positionCropGenerator.generate(source, identity.role, allowUpperFallback)) {
                is MatchResultPositionCropGenerationResult.Generated -> result
                else -> return@withContext MatchResultOcrPreviewProcessingResult.RecognitionFailed
            }
            try {
                val semantics = generated.crops.sortedBy { it.geometry.position }.map { positionCrop ->
                    val wholeAttempt = wholePositionSemantic(positionCrop, identity.role)
                    wholeAttempt.semantic
                        ?: throw IllegalStateException(
                            "Whole-position OCR unavailable: ${wholeAttempt.reason}",
                        )
                }
                val extraction = semantics.toAcceptedExtraction(identity.role, allowUpperFallback)
                    ?: return@withContext MatchResultOcrPreviewProcessingResult.RecognitionFailed
                MatchResultOcrPreviewProcessingResult.Processed(
                    extraction = extraction,
                    pixelCrop = pixelCrop,
                    cropWidth = source.width,
                    cropHeight = source.height,
                    source = MatchResultOcrPreviewSource.NEW_PP_POSITION,
                )
            } finally {
                generated.release()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchResultOcrPreviewProcessingResult.RecognitionFailed
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private suspend fun wholePositionSemantic(
        positionCrop: MatchResultPositionBitmapCrop,
        role: MatchResultScreenshotRole,
    ): WholePositionAttempt {
        val structuralCenter = positionCrop.geometry.structuralCenterYInSource ?: return WholePositionAttempt(
            semantic = null,
            reason = WholePositionFallbackReason.STRUCTURAL_CENTER_UNAVAILABLE.name,
        )
        val localCenter = structuralCenter - positionCrop.geometry.bounds.top
        if (!localCenter.isFinite() || localCenter !in 0.0..positionCrop.bitmap.height.toDouble()) {
            return WholePositionAttempt(
                semantic = null,
                reason = WholePositionFallbackReason.INVALID_STRUCTURAL_CENTER.name,
            )
        }
        var enhanced: Bitmap? = null
        return try {
            enhanced = try {
                rowOcrPreprocessor.create(
                    positionCrop.bitmap,
                    MatchResultRowOcrCandidate.SCALE_3X,
                )
            } catch (_: Throwable) {
                null
            }
            enhanced ?: return WholePositionAttempt(
                semantic = null,
                reason = WholePositionFallbackReason.PREPROCESS_FAILED.name,
            )
            val result = try {
                paddleRecognizer.recognize(
                    MatchResultPositionBitmapCrop(positionCrop.geometry, enhanced),
                    role,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return WholePositionAttempt(
                    semantic = null,
                    reason = WholePositionFallbackReason.PADDLE_FAILED.name,
                )
            }
            val evidence = (result as? MatchResultPositionPaddleOcrResult.Success)?.evidence
                ?: return WholePositionAttempt(
                    semantic = null,
                    reason = WholePositionFallbackReason.PADDLE_FAILED.name,
                )
            val blocks = MatchResultPositionOcrGeometryMapper.mapBlocks(
                blocks = evidence.blocks,
                scale = MatchResultRowOcrCandidate.SCALE_3X,
                positionWidth = positionCrop.bitmap.width,
                positionHeight = positionCrop.bitmap.height,
            )
            if (blocks.flatMap { it.lines }.isEmpty()) return WholePositionAttempt(
                semantic = null,
                reason = WholePositionFallbackReason.NO_MAPPED_LINES.name,
            )
            val classification = MatchResultPositionLogicalRowClassifier().classify(
                position = positionCrop.geometry.position,
                cropWidth = positionCrop.bitmap.width,
                cropHeight = positionCrop.bitmap.height,
                slotCenterYLocal = localCenter,
                blocks = blocks,
            )
            if (classification !is MatchResultPositionLogicalRowClassification.Available) {
                return WholePositionAttempt(
                    semantic = null,
                    reason = classification.diagnostics.reason?.name ?: WholePositionFallbackReason.UNKNOWN.name,
                )
            }
            val semantic = try {
                fieldMapper.map(
                    MatchResultPositionOcrInput(
                        role = role,
                        position = positionCrop.geometry.position,
                        cropWidth = positionCrop.bitmap.width,
                        cropHeight = positionCrop.bitmap.height,
                        blocks = classification.blocks,
                        rowCrops = classification.rowCrops,
                        placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                        killVerifications = emptyMap(),
                    ),
                )
            } catch (_: Throwable) {
                return WholePositionAttempt(
                    null,
                    WholePositionFallbackReason.SEMANTIC_MAPPING_FAILED.name,
                )
            }
            val players = semantic.row?.playerSlots.orEmpty()
            val reason = when {
                semantic.fields.isEmpty() -> WholePositionFallbackReason.SEMANTIC_NO_FIELDS
                players.isEmpty() -> WholePositionFallbackReason.SEMANTIC_NO_PLAYERS
                !semantic.isAutoAcceptable -> WholePositionFallbackReason.SEMANTIC_WEAK_KILL_RECOVERY_REQUIRED
                else -> null
            }
            if (reason != null) WholePositionAttempt(null, reason.name)
            else WholePositionAttempt(semantic, WholePositionFallbackReason.UNKNOWN.name)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            WholePositionAttempt(
                semantic = null,
                reason = WholePositionFallbackReason.UNKNOWN.name,
            )
        } finally {
            enhanced?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    private data class WholePositionAttempt(
        val semantic: MatchResultPositionSemanticResult?,
        val reason: String,
    )

    private enum class WholePositionFallbackReason {
        PREPROCESS_FAILED,
        PADDLE_FAILED,
        NO_MAPPED_LINES,
        STRUCTURAL_CENTER_UNAVAILABLE,
        INVALID_STRUCTURAL_CENTER,
        SEMANTIC_MAPPING_FAILED,
        SEMANTIC_NO_FIELDS,
        SEMANTIC_NO_PLAYERS,
        SEMANTIC_WEAK_KILL_RECOVERY_REQUIRED,
        UNKNOWN,
    }

    private suspend fun hasConfirmedLowerAsset(
        identity: MatchResultScreenshotIdentity,
        owner: String,
    ): Boolean = try {
        assetRepository.getByIdentityAndOwner(
            identity.copy(role = MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            owner,
        )?.confirmedCropOrNull() != null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        true
    }

    private fun decodeCrop(file: File, pixelCrop: OcrPixelCropRect): Bitmap? {
        val decoded = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Throwable) { null } ?: return null
        if (
            pixelCrop.left < 0 || pixelCrop.top < 0 ||
            pixelCrop.right > decoded.width || pixelCrop.bottom > decoded.height
        ) {
            decoded.recycle()
            return null
        }
        return try {
            val extracted = Bitmap.createBitmap(
                decoded,
                pixelCrop.left,
                pixelCrop.top,
                pixelCrop.width,
                pixelCrop.height,
            )
            try {
                extracted.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                if (extracted !== decoded && !extracted.isRecycled) extracted.recycle()
            }
        } catch (_: Throwable) {
            null
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    private companion object {
    }

}

class HybridMatchResultOcrPreviewRunner(
    private val newRoute: MatchResultOcrPreviewRunner,
    private val legacyRoute: MatchResultOcrPreviewRunner,
) : MatchResultOcrPreviewRunner {
    private val lock = Any()
    private val runs = mutableMapOf<RunKey, SharedRun>()

    override suspend fun process(identity: MatchResultScreenshotIdentity): MatchResultOcrPreviewProcessingResult {
        val key = RunKey(identity.tournamentId, identity.matchId)
        val (run, owner) = synchronized(lock) {
            val existing = runs[key]
            val reusable = existing?.takeUnless {
                it.deferred.isCompleted && identity.role in it.requestedRoles
            }
            if (reusable != null) {
                reusable.requestedRoles += identity.role
                reusable to false
            } else {
                if (existing != null) {
                    runs.remove(key, existing)
                }
                SharedRun(
                    deferred = CompletableDeferred(),
                    requestedRoles = mutableSetOf(identity.role),
                ).also {
                    runs[key] = it
                } to true
            }
        }
        if (owner) {
            try {
                run.deferred.complete(runPair(identity))
            } catch (cancellation: CancellationException) {
                run.deferred.cancel(cancellation)
                synchronized(lock) { runs.remove(key, run) }
                throw cancellation
            } catch (failure: Throwable) {
                run.deferred.completeExceptionally(failure)
                synchronized(lock) { runs.remove(key, run) }
                throw failure
            }
        }
        val result = run.deferred.await()[identity.role]
            ?: MatchResultOcrPreviewProcessingResult.RecognitionFailed
        synchronized(lock) {
            if (run.requestedRoles.containsAll(MatchResultScreenshotRole.entries)) {
                runs.remove(key, run)
            }
        }
        return result
    }

    private suspend fun runPair(
        identity: MatchResultScreenshotIdentity,
    ): Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult> = coroutineScope {
        val newResults = MatchResultScreenshotRole.entries.associateWith { role ->
            async {
                newRoute.process(identity.copy(role = role))
            }
        }.mapValues { (_, deferred) -> deferred.await() }
        if (newResults.values.all(::isAcceptable)) {
            logAcceptedRoute()
            return@coroutineScope newResults
        }
        val legacyResults = MatchResultScreenshotRole.entries.associateWith { role ->
            legacyRoute.process(identity.copy(role = role))
        }
        logFallbackRoute(fallbackReason(newResults))
        return@coroutineScope legacyResults
    }

    private fun fallbackReason(
        results: Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>,
    ): String = when {
        results.values.any { it is MatchResultOcrPreviewProcessingResult.Processed } &&
            results.values.any { !isAcceptable(it) } -> "SEMANTIC_OUTPUT_INVALID"
        results.values.any {
            it == MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop ||
                it == MatchResultOcrPreviewProcessingResult.InvalidCrop ||
                it == MatchResultOcrPreviewProcessingResult.DecodeFailed
        } -> "POSITION_CROP_FAILURE"
        else -> "NEW_ROUTE_FAILURE"
    }

    private fun logAcceptedRoute() {
        runCatching {
            Log.i(RESULT_OCR_ROUTE_LOG_TAG, "RESULT_OCR_ROUTE route=NEW_PP_POSITION status=ACCEPTED")
        }
    }

    private fun logFallbackRoute(reason: String) {
        runCatching {
            Log.i(
                RESULT_OCR_ROUTE_LOG_TAG,
                "RESULT_OCR_ROUTE route=LEGACY_FULL_SCREENSHOT status=FALLBACK reason=$reason",
            )
        }
    }

    private fun isAcceptable(result: MatchResultOcrPreviewProcessingResult): Boolean =
        result is MatchResultOcrPreviewProcessingResult.Processed &&
            result.source == MatchResultOcrPreviewSource.NEW_PP_POSITION &&
            result.extraction.rows.isNotEmpty() &&
            result.extraction.fields.isNotEmpty() &&
            result.extraction.rows.map { it.position }.distinct().size == result.extraction.rows.size

    private data class SharedRun(
        val deferred: CompletableDeferred<Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>>,
        val requestedRoles: MutableSet<MatchResultScreenshotRole>,
    )

    private data class RunKey(val tournamentId: String, val matchId: String)

    private companion object {
        const val RESULT_OCR_ROUTE_LOG_TAG = "RESULT_OCR_ROUTE"
    }
}

private fun List<MatchResultPositionSemanticResult>.toAcceptedExtraction(
    role: MatchResultScreenshotRole,
    allowUpperFallback: Boolean,
): MatchResultOcrExtractionResult? {
    val expected = when (role) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
            if (allowUpperFallback) (1..11).toList() else (1..10).toList()
        MatchResultScreenshotRole.MATCH_RESULT_LOWER -> (11..12).toList()
    }
    val rows = mapNotNull { it.row }
    if (
        map { it.position } != expected ||
        rows.size != size ||
        rows.map { it.position }.distinct().size != rows.size
    ) return null
    val fields = flatMap { it.fields }
    if (fields.isEmpty() || rows.isEmpty()) return null
    return MatchResultOcrExtractionResult(role = role, fields = fields, rows = rows)
}

private fun com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity.confirmedCropOrNull(): OcrNormalizedCropRect? {
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    return OcrNormalizedCropRect(left, top, right, bottom)
}
