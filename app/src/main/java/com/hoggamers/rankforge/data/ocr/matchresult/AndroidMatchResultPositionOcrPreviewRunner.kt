package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.PaddleRawOcrGeometryMapper
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
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Production panel/ROI PP route. */
class AndroidMatchResultPositionOcrPreviewRunner(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localFileResolver: MatchResultOcrPreviewLocalFileResolver,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val positionCropGenerator: AndroidMatchResultPositionCropGenerator,
    private val paddleEngineProvider: MatchResultPositionPaddleOcrEngineProvider,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val fieldMapper: MatchResultPositionOcrFieldMapper = MatchResultPositionOcrFieldMapper(),
    private val semanticRoleResolver: MatchResultSemanticRoleResolver = MatchResultSemanticRoleResolver(),
) : MatchResultOcrPreviewRunner {
    private val lowerProcessingFallback = MatchResultLowerProcessingFallback()
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
        var resolvedSemanticRole: MatchResultScreenshotRole? = null
        try {
            val observation = when (val result = positionCropGenerator.observe(source)) {
                is MatchResultPositionCropObservationResult.Observed -> result
                MatchResultPositionCropObservationResult.InvalidSource,
                MatchResultPositionCropObservationResult.OcrFailed,
                -> return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
            }
            val semanticResolution = semanticRoleResolver.resolve(observation.evidence)
            var fallbackGeometry: MatchResultPositionCropCalculationResult.Available? = null
            val semanticRole = when (semanticResolution) {
                is MatchResultSemanticRoleResolution.Resolved -> semanticResolution.role
                MatchResultSemanticRoleResolution.Ambiguous ->
                    return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
                MatchResultSemanticRoleResolution.Unresolved -> {
                    if (identity.role != MatchResultScreenshotRole.MATCH_RESULT_LOWER) {
                        return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
                    }
                    val activeTeamCount = readActiveTeamCount(identity.tournamentId)
                    fallbackGeometry = lowerProcessingFallback.recover(
                        semanticResolution = semanticResolution,
                        requestedRole = identity.role,
                        evidence = observation.evidence,
                        activeTeamCount = activeTeamCount,
                    ) ?: return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER
                }
            }
            resolvedSemanticRole = semanticRole
            val allowUpperFallback = semanticRole == MatchResultScreenshotRole.MATCH_RESULT_UPPER &&
                !hasConfirmedLowerAsset(identity, owner)
            val processingGeometry = fallbackGeometry ?: when (
                val result = positionCropGenerator.calculate(
                    evidence = observation.evidence,
                    role = semanticRole,
                    allowUpperPositionElevenFallback = allowUpperFallback,
                )
            ) {
                is MatchResultPositionCropCalculationResult.Available -> result
                is MatchResultPositionCropCalculationResult.Unavailable ->
                    return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(semanticRole)
            }
            val generated = when (val result = positionCropGenerator.generate(source, processingGeometry)) {
                is MatchResultPositionCropGenerationResult.Generated -> result
                else -> return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(semanticRole)
            }
            try {
                val inputPlan = MatchResultPpInputPlanner.plan(
                    role = semanticRole,
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                    crops = generated.geometry.crops,
                ) ?: throw IllegalStateException("Unable to plan Result PP input for role=$semanticRole")
                val inputBitmap = if (inputPlan.mode == MatchResultPpInputMode.FULL_PANEL) {
                    source
                } else {
                    Bitmap.createBitmap(
                        source,
                        inputPlan.bounds.left,
                        inputPlan.bounds.top,
                        inputPlan.bounds.width,
                        inputPlan.bounds.height,
                    )
                }
                try {
                    val semantics = runPanelPpProduction(
                        inputBitmap = inputBitmap,
                        role = semanticRole,
                        inputPlan = inputPlan,
                        allowUpperPositionElevenFallback = allowUpperFallback,
                    )
                    val extraction = semantics.toAcceptedExtraction(semanticRole, allowUpperFallback)
                        ?: return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(semanticRole)
                    MatchResultOcrPreviewProcessingResult.Processed(
                        extraction = extraction,
                        pixelCrop = pixelCrop,
                        cropWidth = source.width,
                        cropHeight = source.height,
                        source = MatchResultOcrPreviewSource.NEW_PP_POSITION,
                    )
                } finally {
                    if (inputBitmap !== source && !inputBitmap.isRecycled) inputBitmap.recycle()
                }
            } finally {
                generated.release()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            resolvedSemanticRole?.let(MatchResultOcrPreviewProcessingResult::SemanticRoleProcessingFailed)
                ?: MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private suspend fun runPanelPpProduction(
        inputBitmap: Bitmap,
        role: MatchResultScreenshotRole,
        inputPlan: MatchResultPpInputPlan,
        allowUpperPositionElevenFallback: Boolean,
    ): List<MatchResultPositionSemanticResult> {
        val engine = paddleEngineProvider.getOrCreate()
        val runResult = engine.recognize(inputBitmap)
        val panelBlocks = PaddleRawOcrGeometryMapper.map(
            runResult = runResult,
            cropWidth = inputBitmap.width,
            cropHeight = inputBitmap.height,
        )

        val mapped = MatchResultPanelPpMapper.map(panelBlocks, inputPlan.crops)
        val semanticResults = mapped.map { evidence ->
            mapPanelPosition(
                role = role,
                evidence = evidence,
                allowSingleRowFallback = allowUpperPositionElevenFallback && evidence.crop.position == 11,
            )
        }
        val usableSemantics = semanticResults
            .filter { it.productionReady }
            .mapNotNull { it.semantic }
        if (usableSemantics.isEmpty()) {
            throw IllegalStateException("No usable position OCR for role=$role")
        }
        return usableSemantics.sortedBy { it.position }
    }

    private fun mapPanelPosition(
        role: MatchResultScreenshotRole,
        evidence: MatchResultPanelPpPositionEvidence,
        allowSingleRowFallback: Boolean = false,
    ): PanelPositionSemantic {
        val crop = evidence.crop
        val classification = MatchResultPositionLogicalRowClassifier().classify(
            position = crop.position,
            cropWidth = crop.bounds.width,
            cropHeight = crop.bounds.height,
            slotCenterYLocal = crop.structuralCenterYInSource?.minus(crop.bounds.top),
            blocks = evidence.blocks,
            allowSingleRowFallback = allowSingleRowFallback,
        )
        val semantic = if (classification is MatchResultPositionLogicalRowClassification.Available) {
            try {
                fieldMapper.map(
                    MatchResultPositionOcrInput(
                        role = role,
                        position = crop.position,
                        cropWidth = crop.bounds.width,
                        cropHeight = crop.bounds.height,
                        blocks = classification.blocks,
                        rowCrops = classification.rowCrops,
                        placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                        killVerifications = emptyMap(),
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        val localLines = evidence.blocks.sumOf { it.lines.size }
        val productionReady = isPpPositionProductionStructurallyReady(
            localLines = localLines,
            classification = classification,
            semantic = semantic,
        )
        return PanelPositionSemantic(
            semantic = semantic,
            productionReady = productionReady,
        )
    }

    private data class PanelPositionSemantic(
        val semantic: MatchResultPositionSemanticResult?,
        val productionReady: Boolean,
    )

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

    private suspend fun readActiveTeamCount(tournamentId: String): Int? = try {
        observeTournamentSlots(tournamentId)
            .first()
            .analyzeTeamSlotParticipation()
            .activeCount
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
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

}

class MatchResultPpOnlyPairReconciliationRunner(
    private val ppRoute: MatchResultOcrPreviewRunner,
    private val semanticRoleReconciler: MatchResultSemanticRoleReconciler = MatchResultSemanticRoleReconciler(),
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
        val ppResults = MatchResultScreenshotRole.entries.associateWith { role ->
            async {
                ppRoute.process(identity.copy(role = role))
            }
        }.mapValues { (_, deferred) -> deferred.await() }
        val reconciliation = semanticRoleReconciler.reconcile(ppResults)
        val canonicalPpResults = (reconciliation as? MatchResultSemanticRoleReconciliation.Resolved)?.results
        if (canonicalPpResults != null && canonicalPpResults.values.all(::isAcceptable)) {
            logAcceptedRoute()
            return@coroutineScope canonicalPpResults
        }
        if (ppResults.requiresSemanticSafeFailure(reconciliation)) {
            val failures = MatchResultScreenshotRole.entries.associateWith {
                MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
            }
            return@coroutineScope failures
        }
        if (canonicalPpResults != null) {
            val ppFailures = MatchResultScreenshotRole.entries.associateWith { role ->
                MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(role)
            }
            return@coroutineScope ppFailures
        }
        return@coroutineScope ppResults
    }

    private fun Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>
        .requiresSemanticSafeFailure(
            reconciliation: MatchResultSemanticRoleReconciliation,
        ): Boolean {
        if (values.any { it == MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed }) {
            return true
        }
        if (entries.any { (requestedRole, result) ->
                result is MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed &&
                    result.role != requestedRole
            }
        ) {
            return true
        }
        // A complete physical pair with a non-bijective semantic assignment cannot
        // be safely reinterpreted by physical role.
        return reconciliation is MatchResultSemanticRoleReconciliation.Conflict
    }

    private fun logAcceptedRoute() {
        runCatching {
            Log.i(RESULT_OCR_ROUTE_LOG_TAG, "RESULT_OCR_ROUTE route=NEW_PP_POSITION status=ACCEPTED")
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

internal fun List<MatchResultPositionSemanticResult>.toAcceptedExtraction(
    role: MatchResultScreenshotRole,
    allowUpperFallback: Boolean,
): MatchResultOcrExtractionResult? {
    val expected = when (role) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
            if (allowUpperFallback) (1..11).toList() else (1..10).toList()
        MatchResultScreenshotRole.MATCH_RESULT_LOWER -> (11..12).toList()
    }
    val positions = map { it.position }
    val rows = mapNotNull { it.row }
    if (
        positions.isEmpty() ||
        any { it.role != role } ||
        positions.any { it !in expected } ||
        positions.distinct().size != positions.size ||
        rows.size != size ||
        rows.map { it.position } != positions ||
        rows.map { it.position }.distinct().size != rows.size
    ) return null
    val fields = flatMap { it.fields }
    if (fields.isEmpty() || rows.isEmpty()) return null
    return MatchResultOcrExtractionResult(role = role, fields = fields, rows = rows)
}

internal fun isPpPositionProductionStructurallyReady(
    localLines: Int,
    classification: MatchResultPositionLogicalRowClassification,
    semantic: MatchResultPositionSemanticResult?,
): Boolean {
    if (localLines <= 0 || classification !is MatchResultPositionLogicalRowClassification.Available) {
        return false
    }
    return semantic != null &&
        semantic.fields.isNotEmpty() &&
        semantic.row?.playerSlots.orEmpty().isNotEmpty() &&
        semantic.structuralIdentityValid &&
        semantic.placementVerification !is MatchResultNumericVerification.Conflict &&
        semantic.killVerifications.values.none { it is MatchResultNumericVerification.Conflict }
}

private fun com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity.confirmedCropOrNull(): OcrNormalizedCropRect? {
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    return OcrNormalizedCropRect(left, top, right, bottom)
}
