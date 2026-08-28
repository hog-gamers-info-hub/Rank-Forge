package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowAssembler
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrVisualRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationPrefixType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrFieldMapper
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrInput
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
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
                        val rowCrops = rows.crops.sortedBy { it.geometry.rowIndex }
                        val threeXRows = rowCrops.map { row ->
                            recognizeEnhancedRow(
                                positionCrop = positionCrop,
                                row = row,
                                role = identity.role,
                                candidate = MatchResultRowOcrCandidate.SCALE_3X,
                            )
                        }
                        val semanticInput = MatchResultPositionOcrInput(
                            role = identity.role,
                            position = positionCrop.geometry.position,
                            cropWidth = positionCrop.bitmap.width,
                            cropHeight = positionCrop.bitmap.height,
                            blocks = threeXRows.flatMap { it.blocks },
                            rowCrops = rows.crops.map { it.geometry },
                            placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                            killVerifications = emptyMap(),
                        )
                        val threeXSemantic = fieldMapper.map(semanticInput)
                        val finalRows = threeXRows.map { threeX ->
                            if (!shouldRetryRow(threeX, threeXSemantic)) {
                                threeX
                            } else {
                                val fourX = recognizeEnhancedRow(
                                    positionCrop = positionCrop,
                                    row = rowCrops.first { it.geometry.rowIndex == threeX.rowIndex },
                                    role = identity.role,
                                    candidate = MatchResultRowOcrCandidate.SCALE_4X,
                                )
                                threeX.copy(fourX = fourX)
                            }
                        }
                        val semantic = mergeKillRecovery(
                            base = threeXSemantic,
                            retries = finalRows,
                            input = semanticInput,
                        )
                        finalRows.forEach { selectedRow ->
                            if (selectedRow.evaluation.resultCount <= 0 &&
                                (selectedRow.fourX?.evaluation?.resultCount ?: 0) <= 0
                            ) {
                                throw IllegalStateException("Position row PP OCR failed.")
                            }
                            logRowSemantic(
                                role = identity.role,
                                position = positionCrop.geometry.position,
                                summary = MatchResultRowOcrSemanticDiagnostic.summarize(
                                    semantic = semantic,
                                    rowIndex = selectedRow.rowIndex,
                                    selected = selectedRow.fourX?.candidate ?: selectedRow.candidate,
                                ),
                            )
                            semantic.playerBoundaryEvidence[
                                if (selectedRow.rowIndex == 1) 3 else 4
                            ]?.let { boundary ->
                                logPlayerBoundary(
                                    role = identity.role,
                                    position = positionCrop.geometry.position,
                                    row = selectedRow.rowIndex,
                                    selected = selectedRow.fourX?.candidate ?: selectedRow.candidate,
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

    private fun shouldRetryRow(
        threeX: RowOcrAttempt,
        semantic: MatchResultPositionSemanticResult,
    ): Boolean {
        val slots = if (threeX.rowIndex == 1) listOf(1, 3) else listOf(2, 4)
        val detectedPlayerSlots = semantic.fields.filter {
            it.type == MatchResultOcrFieldType.PLAYER &&
                it.slot in slots && it.resolvedText.isNotBlank()
        }.mapNotNull { it.slot }.toSet()
        val strongKills = detectedPlayerSlots.count { slot ->
            MatchResultRowOcrFallbackDecision.isStrongKill(semantic.basicKillEvidence[slot])
        }
        val hasEmptyPrefix = slots.any { slot ->
            semantic.basicKillEvidence[slot]?.let { it.markerMatched &&
                it.prefixType == MatchResultEliminationPrefixType.EMPTY_PREFIX
            } == true
        }
        return MatchResultRowOcrFallbackDecision.shouldRetry(
            MatchResultRowOcrFallbackSignals(
                detectedPlayerCount = detectedPlayerSlots.size,
                strongKillCount = strongKills,
                hasEmptyPrefixMarker = hasEmptyPrefix,
                ocrFailed = threeX.evaluation.result !is MatchResultPositionPaddleOcrResult.Success,
            ),
        )
    }

    private fun mergeKillRecovery(
        base: MatchResultPositionSemanticResult,
        retries: List<RowOcrAttempt>,
        input: MatchResultPositionOcrInput,
    ): MatchResultPositionSemanticResult {
        var fields = base.fields
        var evidence = base.basicKillEvidence
        retries.filter { it.fourX != null }.forEach { row ->
            val retry = row.fourX ?: return@forEach
            val retryBlocks = retry.blocks
            if (retryBlocks.isEmpty()) return@forEach
            val retrySemantic = fieldMapper.map(input.copy(blocks = retryBlocks))
            val slots = if (row.rowIndex == 1) listOf(1, 3) else listOf(2, 4)
            slots.forEach { slot ->
                val retryEvidence = retrySemantic.basicKillEvidence[slot]
                val hasThreeXPlayer = fields.any {
                    it.type == MatchResultOcrFieldType.PLAYER &&
                        it.slot == slot && it.resolvedText.isNotBlank()
                }
                if (!hasThreeXPlayer ||
                    !MatchResultRowOcrFallbackDecision.isStrongKill(retryEvidence) ||
                    MatchResultRowOcrFallbackDecision.isStrongKill(evidence[slot])
                ) return@forEach
                val retryKill = retrySemantic.fields.firstOrNull {
                    it.type == MatchResultOcrFieldType.KILL && it.slot == slot
                } ?: return@forEach
                fields = fields.map { field ->
                    if (field.type == MatchResultOcrFieldType.KILL &&
                        field.slot == slot
                    ) retryKill else field
                }
                evidence = evidence + (slot to retryEvidence)
            }
        }
        if (fields === base.fields) return base
        val source = base.row?.source ?: when (input.role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultOcrRowSource.UPPER_TEMPLATE
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> if (input.position == 11) {
                MatchResultOcrRowSource.LOWER_ROW_A
            } else {
                MatchResultOcrRowSource.LOWER_ROW_B
            }
        }
        val row = runCatching {
            MatchResultOcrRowAssembler.assemble(
                position = input.position,
                source = source,
                fields = fields,
                visualRow = when (input.role) {
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER -> null
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                        if (input.position == 11) MatchResultOcrVisualRow.A
                        else MatchResultOcrVisualRow.B
                },
            )
        }.getOrNull()
        val players = fields.filter {
            it.type == MatchResultOcrFieldType.PLAYER && it.resolvedText.isNotBlank()
        }
        val allPresentPlayersHaveKills = players.all { player ->
            fields.firstOrNull { it.type == MatchResultOcrFieldType.KILL && it.slot == player.slot }
                ?.resolvedText?.isNotBlank() == true
        }
        return base.copy(
            fields = fields,
            row = row,
            basicKillEvidence = evidence,
            isAutoAcceptable = base.structuralIdentityValid &&
                base.placementVerification !is MatchResultNumericVerification.Conflict &&
                base.killVerifications.values.none { it is MatchResultNumericVerification.Conflict } &&
                allPresentPlayersHaveKills,
        )
    }

    private suspend fun recognizeEnhancedRow(
        positionCrop: MatchResultPositionBitmapCrop,
        row: MatchResultPositionRowBitmapCrop,
        role: MatchResultScreenshotRole,
        candidate: MatchResultRowOcrCandidate,
    ): RowOcrAttempt {
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
        val blocks = (evaluation.result as? MatchResultPositionPaddleOcrResult.Success)?.evidence?.let { evidence ->
            MatchResultRowOcrGeometryMapper.mapBlocks(
                blocks = evidence.blocks,
                scale = candidate,
                row = row.geometry,
                positionWidth = positionCrop.bitmap.width,
                positionHeight = positionCrop.bitmap.height,
            )
        }.orEmpty()
        return RowOcrAttempt(row.geometry.rowIndex, candidate, evaluation, blocks)
    }

    private data class RowOcrAttempt(
        val rowIndex: Int,
        val candidate: MatchResultRowOcrCandidate,
        val evaluation: MatchResultRowOcrCandidateEvaluation,
        val blocks: List<RawOcrBlock>,
        val fourX: RowOcrAttempt? = null,
    )

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
