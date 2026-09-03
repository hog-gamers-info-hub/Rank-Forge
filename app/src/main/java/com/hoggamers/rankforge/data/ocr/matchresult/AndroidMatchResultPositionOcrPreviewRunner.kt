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
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
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
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Production panel/ROI PP route. */
class AndroidMatchResultPositionOcrPreviewRunner(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localFileResolver: MatchResultOcrPreviewLocalFileResolver,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val positionCropGenerator: AndroidMatchResultPositionCropGenerator,
    private val paddleEngineProvider: MatchResultPositionPaddleOcrEngineProvider,
    private val fieldMapper: MatchResultPositionOcrFieldMapper = MatchResultPositionOcrFieldMapper(),
) : MatchResultPairOcrPreviewRunner {
    private val lowerProcessingFallback = MatchResultLowerProcessingFallback()
    private val pairSemanticRoleResolver = MatchResultPairSemanticRoleResolver()

    override suspend fun process(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultOcrPreviewProcessingResult = processPrepared(
        prepared = prepare(identity),
        assignedRole = MatchResultScreenshotRoleAssignment.forSingleScreenshot(),
    )

    override suspend fun processPair(
        identities: Map<MatchResultScreenshotRole, MatchResultScreenshotIdentity>,
    ): Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult> = coroutineScope {
        val prepared = MatchResultScreenshotRole.entries
            .associateWith { role -> async { prepare(identities.getValue(role)) } }
            .mapValues { (_, deferred) -> deferred.await() }
        val ready = prepared.values.mapNotNull { it as? Prepared.Ready }
        if (ready.size != MatchResultScreenshotRole.entries.size) {
            return@coroutineScope MatchResultScreenshotRole.entries.associateWith { role ->
                async {
                    processPrepared(
                        prepared = prepared.getValue(role),
                        assignedRole = MatchResultScreenshotRoleAssignment.forSingleScreenshot(),
                    )
                }
            }.mapValues { (_, deferred) -> deferred.await() }
        }

        val first = prepared.getValue(MatchResultScreenshotRole.MATCH_RESULT_UPPER) as Prepared.Ready
        val second = prepared.getValue(MatchResultScreenshotRole.MATCH_RESULT_LOWER) as Prepared.Ready
        val pairResolution = pairSemanticRoleResolver.resolve(first.evidence, second.evidence)
        val resolvedPair = pairResolution as? MatchResultPairSemanticRoleResolution.Resolved
        if (resolvedPair == null) {
            prepared.values.forEach(Prepared::release)
            return@coroutineScope MatchResultScreenshotRole.entries.associateWith {
                MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed
            }
        }
        MatchResultScreenshotRole.entries
            .associateWith { role ->
                async {
                    processPrepared(
                        prepared = prepared.getValue(role),
                        assignedRole = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                            resolvedPair.firstRole
                        } else {
                            resolvedPair.secondRole
                        },
                    )
                }
            }
            .mapValues { (_, deferred) -> deferred.await() }
    }

    private suspend fun prepare(
        identity: MatchResultScreenshotIdentity,
    ): Prepared = withContext(Dispatchers.IO) {
        val owner = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.MissingAsset)
        val asset = try {
            assetRepository.getByIdentityAndOwner(identity, owner)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.MissingAsset)
        } ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.MissingAsset)
        val crop = asset.confirmedCropOrNull()
            ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop)
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.InvalidCrop)
        val pixelCrop = when (val validation = OcrCropValidator.validate(
            crop = crop,
            dimensions = dimensions,
            profile = OcrCropValidationProfiles.MatchResult,
        )) {
            is OcrCropValidationResult.Valid -> validation.pixelCrop
                ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.InvalidCrop)
            is OcrCropValidationResult.Invalid ->
                return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.InvalidCrop)
        }
        val file = try {
            localFileResolver.resolve(asset.localRelativePath)
        } catch (_: Throwable) {
            null
        }?.takeIf { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) }
            ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.MissingLocalOriginal)
        val source = decodeCrop(file, pixelCrop)
            ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.DecodeFailed)
        val evidence = try {
            when (val result = positionCropGenerator.observe(source)) {
                is MatchResultPositionCropObservationResult.Observed -> {
                    result.evidence
                }
                MatchResultPositionCropObservationResult.InvalidSource -> {
                    if (!source.isRecycled) source.recycle()
                    return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed)
                }
                MatchResultPositionCropObservationResult.OcrFailed -> {
                    if (!source.isRecycled) source.recycle()
                    return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed)
                }
            }
        } catch (cancellation: CancellationException) {
            if (!source.isRecycled) source.recycle()
            throw cancellation
        } catch (_: Throwable) {
            if (!source.isRecycled) source.recycle()
            return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed)
        }
        Prepared.Ready(identity, owner, pixelCrop, source, evidence)
    }

    private suspend fun processPrepared(
        prepared: Prepared,
        assignedRole: MatchResultScreenshotRole,
    ): MatchResultOcrPreviewProcessingResult = withContext(Dispatchers.IO) {
        if (prepared is Prepared.Failed) return@withContext prepared.result
        prepared as Prepared.Ready
        val source = prepared.source
        try {
            val processingGeometry = when (assignedRole) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER -> positionCropGenerator.calculate(
                    evidence = prepared.evidence,
                    role = assignedRole,
                    allowUpperPositionElevenFallback = !hasConfirmedLowerAsset(prepared.identity, prepared.owner),
                ) as? MatchResultPositionCropCalculationResult.Available

                MatchResultScreenshotRole.MATCH_RESULT_LOWER -> lowerProcessingFallback.recover(prepared.evidence)
            } ?: run {
                return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(assignedRole)
            }
            val allowUpperFallback = assignedRole == MatchResultScreenshotRole.MATCH_RESULT_UPPER &&
                !hasConfirmedLowerAsset(prepared.identity, prepared.owner)
            val generated = when (val result = positionCropGenerator.generate(source, processingGeometry)) {
                is MatchResultPositionCropGenerationResult.Generated -> result
                else -> {
                    return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(assignedRole)
                }
            }
            try {
                val inputPlan = MatchResultPpInputPlanner.plan(
                    role = assignedRole,
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                    crops = generated.geometry.crops,
                ) ?: throw IllegalStateException("Unable to plan Result PP input for role=$assignedRole")
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
                        role = assignedRole,
                        inputPlan = inputPlan,
                        allowUpperPositionElevenFallback = allowUpperFallback,
                    )
                    val extraction = semantics.toAcceptedExtraction(assignedRole, allowUpperFallback)
                        ?: run {
                            return@withContext MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(assignedRole)
                        }
                    MatchResultOcrPreviewProcessingResult.Processed(
                        extraction = extraction,
                        pixelCrop = prepared.pixelCrop,
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
            MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(assignedRole)
        } finally {
            prepared.release()
        }
    }

    private sealed interface Prepared {
        fun release()

        data class Ready(
            val identity: MatchResultScreenshotIdentity,
            val owner: String,
            val pixelCrop: OcrPixelCropRect,
            val source: Bitmap,
            val evidence: MatchResultAutoCropEvidence,
        ) : Prepared {
            override fun release() {
                if (!source.isRecycled) source.recycle()
            }
        }

        data class Failed(
            val result: MatchResultOcrPreviewProcessingResult,
        ) : Prepared {
            override fun release() = Unit
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
        val ppResults = if (ppRoute is MatchResultPairOcrPreviewRunner) {
            ppRoute.processPair(
                MatchResultScreenshotRole.entries.associateWith { role -> identity.copy(role = role) },
            )
        } else {
            MatchResultScreenshotRole.entries.associateWith { role ->
                async {
                    ppRoute.process(identity.copy(role = role))
                }
            }.mapValues { (_, deferred) -> deferred.await() }
        }
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
