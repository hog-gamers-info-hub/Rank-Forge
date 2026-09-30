package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.PaddleRawOcrGeometryMapper
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidOcrImageEnhancer
import com.hoggamers.rankforge.data.ocr.preprocessing.LOBBY_OCR_ENHANCEMENT_PROFILE
import com.hoggamers.rankforge.data.ocr.preprocessing.OcrImageEnhancer
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultMlKitKillFallbackResolver
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultFocusedNumericKillFallbackKey
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultFocusedNumericKillFallbackMerger
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationAnchorText
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultKillFieldLayout
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrFieldMapper
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrInput
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowClassifier
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowClassification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionKillFallbackMerger
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
    private val numericVerifier: AndroidMatchResultPositionPaddleNumericVerifier,
    private val fieldMapper: MatchResultPositionOcrFieldMapper = MatchResultPositionOcrFieldMapper(),
) : MatchResultPairOcrPreviewRunner {
    private val imageEnhancer: OcrImageEnhancer = AndroidOcrImageEnhancer()
    private val lowerProcessingFallback = MatchResultLowerProcessingFallback()
    private val pairSemanticRoleResolver = MatchResultPairSemanticRoleResolver()
    private val mlKitKillFallbackResolver = MatchResultMlKitKillFallbackResolver()
    private val positionKillFallbackMerger = MatchResultPositionKillFallbackMerger()
    private val focusedNumericKillFallbackMerger = MatchResultFocusedNumericKillFallbackMerger()

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
        val decodedSource = decodeCrop(file, pixelCrop)
            ?: return@withContext Prepared.Failed(MatchResultOcrPreviewProcessingResult.DecodeFailed)
        val enhancedBitmap = try {
            imageEnhancer.enhance(decodedSource, LOBBY_OCR_ENHANCEMENT_PROFILE)
        } catch (cancellation: CancellationException) {
            if (!decodedSource.isRecycled) decodedSource.recycle()
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        val source = enhancedBitmap?.takeIf { candidate ->
            !candidate.isRecycled &&
                candidate.width == decodedSource.width &&
                candidate.height == decodedSource.height
        } ?: decodedSource
        if (enhancedBitmap != null && enhancedBitmap !== source && !enhancedBitmap.isRecycled) {
            enhancedBitmap.recycle()
        }
        if (source !== decodedSource && !decodedSource.isRecycled) decodedSource.recycle()
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
                    val panelResult = runPanelPpProduction(
                        inputBitmap = inputBitmap,
                        role = assignedRole,
                        inputPlan = inputPlan,
                        allowUpperPositionElevenFallback = allowUpperFallback,
                        mlKitEvidence = prepared.evidence,
                        sourceCrops = processingGeometry.crops,
                    )
                    val positionPpResult = recoverMissingKillsWithPositionPp(
                        baseSemantics = panelResult.semantics,
                        generatedCrops = generated.crops,
                        role = assignedRole,
                        allowUpperPositionElevenFallback = allowUpperFallback,
                    )
                    val semantics = recoverMissingKillsWithFocusedNumericFallback(
                        baseSemantics = positionPpResult.semantics,
                        generatedCrops = generated.crops,
                        role = assignedRole,
                        rowCropsByPosition = panelResult.rowCropsByPosition +
                            positionPpResult.rowCropsByPosition,
                        ppMarkerSeenSlotsByPosition = unionSlotEvidence(
                            panelResult.ppMarkerSeenSlotsByPosition,
                            positionPpResult.ppMarkerSeenSlotsByPosition,
                        ),
                        mlKitMarkerSeenSlotsByPosition = panelResult.mlKitMarkerSeenSlotsByPosition,
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
                        positionCrops = processingGeometry.crops,
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

    private suspend fun recoverMissingKillsWithPositionPp(
        baseSemantics: List<MatchResultPositionSemanticResult>,
        generatedCrops: List<MatchResultPositionBitmapCrop>,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
    ): PositionPpKillRecoveryResult {
        var engine: MatchResultPositionPaddleOcrEngine? = null
        var engineInitializationAttempted = false
        val rowCropsByPosition = mutableMapOf<Int, List<MatchResultPositionRowCrop>>()
        val ppMarkerSeenSlotsByPosition = mutableMapOf<Int, Set<Int>>()
        val semantics = positionKillFallbackMerger.recover(
            baseSemantics = baseSemantics,
            availablePositions = generatedCrops.mapTo(mutableSetOf()) { it.geometry.position },
            recoverPosition = recover@{ target ->
                val crop = generatedCrops.firstOrNull { it.geometry.position == target.position }
                    ?: return@recover null
                if (!engineInitializationAttempted) {
                    engineInitializationAttempted = true
                    engine = paddleEngineProvider.getOrCreate()
                }
                val activeEngine = engine ?: return@recover null
                val runResult = activeEngine.recognize(crop.bitmap)
                val blocks = PaddleRawOcrGeometryMapper.map(
                    runResult = runResult,
                    cropWidth = crop.bitmap.width,
                    cropHeight = crop.bitmap.height,
                )
                val classification = MatchResultPositionLogicalRowClassifier().classify(
                    position = crop.geometry.position,
                    cropWidth = crop.bitmap.width,
                    cropHeight = crop.bitmap.height,
                    slotCenterYLocal = crop.geometry.structuralCenterYInSource
                        ?.minus(crop.geometry.bounds.top),
                    blocks = blocks,
                    allowSingleRowFallback = allowUpperPositionElevenFallback && crop.geometry.position == 11,
                    upperPhysicalRowSafe = crop.geometry.upperPhysicalRowSafe,
                    lowerPhysicalRowSafe = crop.geometry.lowerPhysicalRowSafe,
                ) as? MatchResultPositionLogicalRowClassification.Available
                    ?: return@recover null
                val targeted = fieldMapper.map(
                    MatchResultPositionOcrInput(
                        role = role,
                        position = crop.geometry.position,
                        cropWidth = crop.bitmap.width,
                        cropHeight = crop.bitmap.height,
                        blocks = classification.blocks,
                        rowCrops = classification.rowCrops,
                        placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                        killVerifications = emptyMap(),
                    ),
                )
                targeted.takeIf { semantic ->
                    semantic.role == role &&
                        semantic.position == target.position &&
                        target.missingSlots.all { slot ->
                            semantic.row?.playerSlots?.any { it.slot == slot } == true
                        }
                }?.also { semantic ->
                    rowCropsByPosition[target.position] = classification.rowCrops
                    ppMarkerSeenSlotsByPosition[target.position] = recognizedPpMarkerSlots(
                        position = crop.geometry.position,
                        cropWidth = crop.bitmap.width,
                        blocks = classification.blocks,
                        rowCrops = classification.rowCrops,
                    ) + semantic.basicKillEvidence
                        .filterValues { it?.markerMatched == true }
                        .keys
                }
            },
        )
        return PositionPpKillRecoveryResult(
            semantics = semantics,
            rowCropsByPosition = rowCropsByPosition,
            ppMarkerSeenSlotsByPosition = ppMarkerSeenSlotsByPosition,
        )
    }

    private suspend fun runPanelPpProduction(
        inputBitmap: Bitmap,
        role: MatchResultScreenshotRole,
        inputPlan: MatchResultPpInputPlan,
        allowUpperPositionElevenFallback: Boolean,
        mlKitEvidence: MatchResultAutoCropEvidence,
        sourceCrops: List<MatchResultPositionCrop>,
    ): PanelPpProductionResult {
        val engine = paddleEngineProvider.getOrCreate()
        val runResult = engine.recognize(inputBitmap)
        val panelBlocks = PaddleRawOcrGeometryMapper.map(
            runResult = runResult,
            cropWidth = inputBitmap.width,
            cropHeight = inputBitmap.height,
        )

        val mapped = MatchResultPanelPpMapper.map(panelBlocks, inputPlan.crops)
        val semanticResults = mapped.map { evidence ->
            val sourceCrop = sourceCrops.firstOrNull { crop ->
                crop.position == evidence.crop.position && crop.column == evidence.crop.column
            }
            mapPanelPosition(
                role = role,
                evidence = evidence,
                allowSingleRowFallback = allowUpperPositionElevenFallback && evidence.crop.position == 11,
                mlKitEvidence = mlKitEvidence,
                sourceCrop = sourceCrop,
            )
        }
        val usableSemantics = semanticResults
            .filter { it.productionReady }
            .mapNotNull { it.semantic }
        if (usableSemantics.isEmpty()) {
            throw IllegalStateException("No usable position OCR for role=$role")
        }
        val usableResults = semanticResults.filter { it.productionReady && it.semantic != null }
        return PanelPpProductionResult(
            semantics = usableSemantics.sortedBy { it.position },
            rowCropsByPosition = usableResults.associate { result ->
                result.semantic!!.position to result.rowCrops
            },
            ppMarkerSeenSlotsByPosition = usableResults.associate { result ->
                result.semantic!!.position to result.ppMarkerSeenSlots
            },
            mlKitMarkerSeenSlotsByPosition = usableResults.associate { result ->
                result.semantic!!.position to result.mlKitMarkerSeenSlots
            },
        )
    }

    private fun mapPanelPosition(
        role: MatchResultScreenshotRole,
        evidence: MatchResultPanelPpPositionEvidence,
        allowSingleRowFallback: Boolean = false,
        mlKitEvidence: MatchResultAutoCropEvidence? = null,
        sourceCrop: MatchResultPositionCrop? = null,
    ): PanelPositionSemantic {
        val crop = evidence.crop
        val classification = MatchResultPositionLogicalRowClassifier().classify(
            position = crop.position,
            cropWidth = crop.bounds.width,
            cropHeight = crop.bounds.height,
            slotCenterYLocal = crop.structuralCenterYInSource?.minus(crop.bounds.top),
            blocks = evidence.blocks,
            allowSingleRowFallback = allowSingleRowFallback,
            upperPhysicalRowSafe = crop.upperPhysicalRowSafe,
            lowerPhysicalRowSafe = crop.lowerPhysicalRowSafe,
        )
        var mlKitMarkerSeenSlots = emptySet<Int>()
        val semantic = if (classification is MatchResultPositionLogicalRowClassification.Available) {
            try {
                val input = MatchResultPositionOcrInput(
                    role = role,
                    position = crop.position,
                    cropWidth = crop.bounds.width,
                    cropHeight = crop.bounds.height,
                    blocks = classification.blocks,
                    rowCrops = classification.rowCrops,
                    placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                    killVerifications = emptyMap(),
                )
                val ppSemantic = fieldMapper.map(input)
                val mlKitResolution = if (mlKitEvidence != null && sourceCrop != null) {
                    mlKitKillFallbackResolver.resolveWithEvidence(
                        positionCrop = sourceCrop,
                        rowCrops = classification.rowCrops,
                        currentPpSemantic = ppSemantic,
                        evidence = mlKitEvidence,
                    )
                } else {
                    null
                }
                mlKitMarkerSeenSlots = mlKitResolution?.recognizedEliminationMarkerSlots.orEmpty()
                val fallbackVerifications = mlKitResolution?.verifications.orEmpty()
                if (fallbackVerifications.isEmpty()) {
                    ppSemantic
                } else {
                    fieldMapper.map(input.copy(killVerifications = fallbackVerifications))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        val ppMarkerSeenSlots = recognizedPpMarkerSlots(
            position = crop.position,
            cropWidth = crop.bounds.width,
            blocks = evidence.blocks,
            rowCrops = (classification as? MatchResultPositionLogicalRowClassification.Available)
                ?.rowCrops.orEmpty(),
        ) + semantic?.basicKillEvidence.orEmpty()
            .filterValues { it?.markerMatched == true }
            .keys
        val localLines = evidence.blocks.sumOf { it.lines.size }
        val productionReady = isPpPositionProductionStructurallyReady(
            localLines = localLines,
            classification = classification,
            semantic = semantic,
        )
        return PanelPositionSemantic(
            semantic = semantic,
            productionReady = productionReady,
            rowCrops = (classification as? MatchResultPositionLogicalRowClassification.Available)
                ?.rowCrops.orEmpty(),
            ppMarkerSeenSlots = ppMarkerSeenSlots,
            mlKitMarkerSeenSlots = mlKitMarkerSeenSlots,
        )
    }

    private suspend fun recoverMissingKillsWithFocusedNumericFallback(
        baseSemantics: List<MatchResultPositionSemanticResult>,
        generatedCrops: List<MatchResultPositionBitmapCrop>,
        role: MatchResultScreenshotRole,
        rowCropsByPosition: Map<Int, List<MatchResultPositionRowCrop>>,
        ppMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
        mlKitMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
    ): List<MatchResultPositionSemanticResult> {
        val targets = focusedNumericKillFallbackMerger.eligibleTargets(
            baseSemantics = baseSemantics,
            rowCropsByPosition = rowCropsByPosition,
            ppMarkerSeenSlotsByPosition = ppMarkerSeenSlotsByPosition,
            mlKitMarkerSeenSlotsByPosition = mlKitMarkerSeenSlotsByPosition,
        )
        if (targets.isEmpty()) return baseSemantics

        val cropsByPosition = generatedCrops.associateBy { it.geometry.position }
        val verifiedKills = mutableMapOf<MatchResultFocusedNumericKillFallbackKey, MatchResultNumericVerification>()
        targets.forEach { target ->
            val crop = cropsByPosition[target.position] ?: return@forEach
            val verification = try {
                numericVerifier.verifyKillFallback(
                    source = crop.bitmap,
                    role = role,
                    position = target.position,
                    slot = target.slot,
                    row = target.row,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (verification is MatchResultNumericVerification.Verified) {
                verifiedKills[MatchResultFocusedNumericKillFallbackKey(target.role, target.position, target.slot)] =
                    verification
            }
        }
        return focusedNumericKillFallbackMerger.merge(baseSemantics, verifiedKills)
    }

    private fun unionSlotEvidence(
        first: Map<Int, Set<Int>>,
        second: Map<Int, Set<Int>>,
    ): Map<Int, Set<Int>> = (first.keys + second.keys).associateWith { position ->
        first[position].orEmpty() + second[position].orEmpty()
    }

    private fun recognizedPpMarkerSlots(
        position: Int,
        cropWidth: Int,
        blocks: List<RawOcrBlock>,
        rowCrops: List<MatchResultPositionRowCrop>,
    ): Set<Int> {
        val lines = blocks.flatMap { it.lines }
        return (1..4).mapNotNull { slot ->
            val rowBounds = rowCrops.firstOrNull {
                it.rowIndex == MatchResultKillFieldLayout.rowIndexForSlot(slot)
            }?.bounds ?: return@mapNotNull null
            val killBounds = MatchResultKillFieldLayout.bounds(
                position = position,
                cropWidth = cropWidth,
                rowBounds = rowBounds,
                firstPlayerColumn = slot == 1 || slot == 2,
            )
            if (lines.any { line ->
                val bounds = line.geometry?.boundingBox ?: return@any false
                val centerX = (bounds.left + bounds.right) / 2.0
                val centerY = (bounds.top + bounds.bottom) / 2.0
                MatchResultEliminationAnchorText.find(line.text) != null &&
                    centerX >= killBounds.left && centerX < killBounds.right &&
                    centerY >= killBounds.top && centerY < killBounds.bottom
            }) slot else null
        }.toSet()
    }

    private data class PanelPositionSemantic(
        val semantic: MatchResultPositionSemanticResult?,
        val productionReady: Boolean,
        val rowCrops: List<MatchResultPositionRowCrop> = emptyList(),
        val ppMarkerSeenSlots: Set<Int> = emptySet(),
        val mlKitMarkerSeenSlots: Set<Int> = emptySet(),
    )

    private data class PanelPpProductionResult(
        val semantics: List<MatchResultPositionSemanticResult>,
        val rowCropsByPosition: Map<Int, List<MatchResultPositionRowCrop>>,
        val ppMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
        val mlKitMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
    )

    private data class PositionPpKillRecoveryResult(
        val semantics: List<MatchResultPositionSemanticResult>,
        val rowCropsByPosition: Map<Int, List<MatchResultPositionRowCrop>>,
        val ppMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
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
}

internal fun List<MatchResultPositionSemanticResult>.toAcceptedExtraction(
    role: MatchResultScreenshotRole,
    allowUpperFallback: Boolean,
): MatchResultOcrExtractionResult? {
    val positions = map { it.position }
    val rows = mapNotNull { it.row }
    if (
        positions.isEmpty() ||
        any { it.role != role } ||
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
