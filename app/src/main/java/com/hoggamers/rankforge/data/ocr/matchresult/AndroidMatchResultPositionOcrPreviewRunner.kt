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
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPlayerBoundaryDecision
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

/** Production BASIC PP position route. It does not consume temporary diagnostics. */
class AndroidMatchResultPositionOcrPreviewRunner(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localFileResolver: MatchResultOcrPreviewLocalFileResolver,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val positionCropGenerator: AndroidMatchResultPositionCropGenerator,
    private val rowCropGenerator: AndroidMatchResultPositionRowCropGenerator,
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
                    val rows = when (val result = rowCropGenerator.generate(
                        positionCrop.bitmap,
                        positionCrop.geometry.position,
                    )) {
                        is MatchResultPositionRowCropGenerationResult.Generated -> result
                        else -> throw IllegalStateException("Position row geometry failed.")
                    }
                    try {
                        val selectedRows = rows.crops.sortedBy { it.geometry.rowIndex }.map { row ->
                            val selection = recognizeEnhancedRow(
                                positionCrop = positionCrop,
                                row = row,
                                role = identity.role,
                            ) ?: throw IllegalStateException("Position row PP OCR failed.")
                            val evidence = (selection.selected.result as MatchResultPositionPaddleOcrResult.Success).evidence
                            SelectedRowSemanticEvidence(
                                rowIndex = row.geometry.rowIndex,
                                selected = selection.selected.candidate,
                                blocks = MatchResultRowOcrGeometryMapper.mapBlocks(
                                    blocks = evidence.blocks,
                                    scale = selection.selected.candidate,
                                    row = row.geometry,
                                    positionWidth = positionCrop.bitmap.width,
                                    positionHeight = positionCrop.bitmap.height,
                                ),
                            )
                        }
                        val semantic = fieldMapper.map(
                            MatchResultPositionOcrInput(
                                role = identity.role,
                                position = positionCrop.geometry.position,
                                cropWidth = positionCrop.bitmap.width,
                                cropHeight = positionCrop.bitmap.height,
                                blocks = selectedRows.flatMap { it.blocks },
                                rowCrops = rows.crops.map { it.geometry },
                                placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                                killVerifications = emptyMap(),
                            ),
                        )
                        selectedRows.forEach { selectedRow ->
                            logRowSemantic(
                                role = identity.role,
                                position = positionCrop.geometry.position,
                                summary = MatchResultRowOcrSemanticDiagnostic.summarize(
                                    semantic = semantic,
                                    rowIndex = selectedRow.rowIndex,
                                    selected = selectedRow.selected,
                                ),
                            )
                            semantic.playerBoundaryEvidence[
                                if (selectedRow.rowIndex == 1) 3 else 4
                            ]?.let { boundary ->
                                logPlayerBoundary(
                                    role = identity.role,
                                    position = positionCrop.geometry.position,
                                    row = selectedRow.rowIndex,
                                    selected = selectedRow.selected,
                                    boundary = boundary,
                                )
                            }
                        }
                        semantic
                    } finally {
                        rows.release()
                    }
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

    private suspend fun recognizeEnhancedRow(
        positionCrop: MatchResultPositionBitmapCrop,
        row: MatchResultPositionRowBitmapCrop,
        role: MatchResultScreenshotRole,
    ): MatchResultRowOcrCandidateSelection? {
        val evaluations = MatchResultRowOcrCandidate.entries.map { candidate ->
            var enhanced: Bitmap? = null
            var enhancedSize = "invalid"
            val evaluation = try {
                enhanced = rowOcrPreprocessor.create(row.bitmap, candidate)
                enhanced?.let { enhancedSize = "${it.width}x${it.height}" }
                val result = enhanced?.let {
                    paddleRecognizer.recognize(
                        MatchResultPositionBitmapCrop(positionCrop.geometry, it),
                        role,
                    )
                } ?: MatchResultPositionPaddleOcrResult.Failed(
                    MatchResultPositionPaddleOcrFailure.INVALID_SOURCE,
                )
                MatchResultRowOcrCandidateSelector.evaluate(candidate, result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchResultRowOcrCandidateSelector.evaluate(
                    candidate,
                    MatchResultPositionPaddleOcrResult.Failed(
                        MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED,
                    ),
                )
            } finally {
                enhanced?.takeUnless { it.isRecycled }?.recycle()
            }
            logRowCandidate(role, positionCrop.geometry.position, row.geometry.rowIndex, row.bitmap, evaluation, enhancedSize)
            evaluation
        }
        return MatchResultRowOcrCandidateSelector.select(evaluations[0], evaluations[1])?.also { selection ->
            logRowSelection(role, positionCrop.geometry.position, row.geometry.rowIndex, selection)
        }
    }

    private fun logRowCandidate(
        role: MatchResultScreenshotRole,
        position: Int,
        row: Int,
        original: Bitmap,
        evaluation: MatchResultRowOcrCandidateEvaluation,
        enhancedSize: String,
    ) {
        runCatching {
            Log.i(
                RESULT_ROW_PP_ENHANCE_LOG_TAG,
                "RESULT_ROW_PP_ENHANCE role=$role position=$position row=$row " +
                    "originalSize=${original.width}x${original.height} enhancedSize=$enhancedSize " +
                    "candidate=${evaluation.candidate.name.removePrefix("SCALE_")} " +
                    "resultCount=${evaluation.resultCount} markerCount=${evaluation.markerCount} " +
                    "explicitKillCount=${evaluation.explicitKillCount} " +
                    "avgConfidence=${"%.3f".format(java.util.Locale.US, evaluation.averageConfidence)} " +
                    "status=${if (evaluation.result is MatchResultPositionPaddleOcrResult.Success) "SUCCESS" else "FAILURE"}",
            )
        }
    }

    private fun logRowSelection(
        role: MatchResultScreenshotRole,
        position: Int,
        row: Int,
        selection: MatchResultRowOcrCandidateSelection,
    ) {
        runCatching {
            Log.i(
                RESULT_ROW_PP_ENHANCE_LOG_TAG,
                "RESULT_ROW_PP_ENHANCE role=$role position=$position row=$row " +
                    "selected=${selection.selected.candidate.name.removePrefix("SCALE_")} reason=${selection.reason}",
            )
        }
    }

    private fun logRowSemantic(
        role: MatchResultScreenshotRole,
        position: Int,
        summary: MatchResultRowOcrSemanticSummary,
    ) {
        runCatching {
            Log.i(
                RESULT_ROW_PP_SEMANTIC_LOG_TAG,
                "RESULT_ROW_PP_SEMANTIC role=$role position=$position row=${summary.rowIndex} " +
                    "selected=${summary.selected.name.removePrefix("SCALE_")} " +
                    "killA=${summary.first.value} killASource=${summary.first.source} " +
                    "killAStatus=${summary.first.fieldStatus} " +
                    "killAMarkerMatched=${summary.first.markerMatched} killAPrefixType=${summary.first.prefixType} " +
                    "killB=${summary.second.value} killBSource=${summary.second.source} " +
                    "killBStatus=${summary.second.fieldStatus} " +
                    "killBMarkerMatched=${summary.second.markerMatched} killBPrefixType=${summary.second.prefixType}",
            )
        }
    }

    private fun logPlayerBoundary(
        role: MatchResultScreenshotRole,
        position: Int,
        row: Int,
        selected: MatchResultRowOcrCandidate,
        boundary: MatchResultPlayerBoundaryDecision,
    ) {
        runCatching {
            Log.i(
                RESULT_ROW_PLAYER_BOUNDARY_LOG_TAG,
                "RESULT_ROW_PLAYER_BOUNDARY role=$role position=$position row=$row " +
                    "selected=${selected.name.removePrefix("SCALE_")} anchorFound=${boundary.anchorFound} " +
                    "anchorPrefixType=${boundary.anchorPrefixType ?: "NONE"} " +
                    "markerType=${boundary.markerType ?: "NONE"} anchorRegion=${boundary.anchorRegion} " +
                    "boundaryAccepted=${boundary.boundaryAccepted} reason=${boundary.reason}",
            )
        }
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
        const val RESULT_ROW_PP_ENHANCE_LOG_TAG = "RESULT_ROW_PP_ENHANCE"
        const val RESULT_ROW_PP_SEMANTIC_LOG_TAG = "RESULT_ROW_PP_SEMANTIC"
        const val RESULT_ROW_PLAYER_BOUNDARY_LOG_TAG = "RESULT_ROW_PLAYER_BOUNDARY"
    }

    private data class SelectedRowSemanticEvidence(
        val rowIndex: Int,
        val selected: MatchResultRowOcrCandidate,
        val blocks: List<com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock>,
    )
}

class HybridMatchResultOcrPreviewRunner(
    private val newRoute: MatchResultOcrPreviewRunner,
    private val legacyRoute: MatchResultOcrPreviewRunner,
) : MatchResultOcrPreviewRunner {
    private val lock = Any()
    private val runs = mutableMapOf<RunKey, CompletableDeferred<Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>>>()

    override suspend fun process(identity: MatchResultScreenshotIdentity): MatchResultOcrPreviewProcessingResult {
        val key = RunKey(identity.tournamentId, identity.matchId)
        val (deferred, owner) = synchronized(lock) {
            val existing = runs[key]
            if (existing != null) existing to false
            else CompletableDeferred<Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>>().also {
                runs[key] = it
            } to true
        }
        if (owner) {
            try {
                deferred.complete(runPair(identity))
            } catch (cancellation: CancellationException) {
                deferred.cancel(cancellation)
                synchronized(lock) { runs.remove(key, deferred) }
                throw cancellation
            } catch (failure: Throwable) {
                deferred.completeExceptionally(failure)
                synchronized(lock) { runs.remove(key, deferred) }
                throw failure
            }
        }
        val result = deferred.await()[identity.role]
            ?: MatchResultOcrPreviewProcessingResult.RecognitionFailed
        synchronized(lock) { runs.remove(key, deferred) }
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
