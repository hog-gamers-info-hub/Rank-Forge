package com.hoggamers.rankforge.data.ocr.matchlobby

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidOcrImageEnhancer
import com.hoggamers.rankforge.data.ocr.preprocessing.LOBBY_OCR_ENHANCEMENT_PROFILE
import com.hoggamers.rankforge.data.ocr.preprocessing.OcrImageEnhancer
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyAutoCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyAutoCropCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyAutoCropGridCandidate
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyAutoCropGroupSelector
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyCropCalibrationProfiles
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyOcrAnchorLevel
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyOcrAnchorObservation
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyOcrAnchorResolver
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridReconstructor
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropResult
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class AndroidMatchLobbyAutoCropProposer @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : MatchLobbyAutoCropProposer {
    private val anchorResolver = LobbyOcrAnchorResolver()
    private val gridReconstructor = LobbySlotGridReconstructor()
    private val cropCalculator = LobbyAutoCropCalculator()
    private var imageEnhancer: OcrImageEnhancer = AndroidOcrImageEnhancer()

    internal constructor(
        recognizerFactory: MlKitTextRecognizerFactory,
        imageEnhancer: OcrImageEnhancer,
    ) : this(recognizerFactory) {
        this.imageEnhancer = imageEnhancer
    }

    override suspend fun propose(
        localFile: File,
    ): MatchLobbyAutoCropResult = withContext(Dispatchers.IO) {
        val original = try {
            BitmapFactory.decodeFile(localFile.absolutePath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return@withContext MatchLobbyAutoCropResult.NoProposal

        if (!original.isUsable()) {
            original.recycleIfNeeded()
            return@withContext MatchLobbyAutoCropResult.NoProposal
        }

        try {
            val dimensions = OcrImageDimensions.from(original.width, original.height)
                ?: return@withContext MatchLobbyAutoCropResult.NoProposal
            when (val originalAttempt = attemptAutoCrop(original, dimensions)) {
                AutoCropAttempt.Failed,
                AutoCropAttempt.CompletedNoProposal,
                -> MatchLobbyAutoCropResult.NoProposal

                is AutoCropAttempt.CompletedProposal -> originalAttempt.result

                AutoCropAttempt.RetryWithEnhancement -> {
                    val enhancedBitmap = try {
                        imageEnhancer.enhance(original, LOBBY_OCR_ENHANCEMENT_PROFILE)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        null
                    } ?: return@withContext MatchLobbyAutoCropResult.NoProposal

                    try {
                        when (val enhancedAttempt = attemptAutoCrop(enhancedBitmap, dimensions)) {
                            is AutoCropAttempt.CompletedProposal -> enhancedAttempt.result
                            AutoCropAttempt.Failed,
                            AutoCropAttempt.CompletedNoProposal,
                            AutoCropAttempt.RetryWithEnhancement,
                            -> MatchLobbyAutoCropResult.NoProposal
                        }
                    } finally {
                        if (enhancedBitmap !== original) {
                            enhancedBitmap.recycleIfNeeded()
                        }
                    }
                }
            }
        } finally {
            original.recycleIfNeeded()
        }
    }

    private suspend fun attemptAutoCrop(
        bitmap: Bitmap,
        dimensions: OcrImageDimensions,
    ): AutoCropAttempt {
        val inputImage = try {
            InputImage.fromBitmap(bitmap, 0)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return AutoCropAttempt.Failed
        }
        val recognizer = try {
            recognizerFactory.create()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return AutoCropAttempt.Failed
        }
        val recognizedText = try {
            try {
                recognizer.process(inputImage).awaitText()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return AutoCropAttempt.Failed
            }
        } finally {
            recognizer.close()
        }
        return when (val decision = calculateDecision(recognizedText, dimensions)) {
            is AutoCropDecision.Proposed -> AutoCropAttempt.CompletedProposal(decision.result)
            AutoCropDecision.RetryWithEnhancement -> AutoCropAttempt.RetryWithEnhancement
            AutoCropDecision.NoProposal -> AutoCropAttempt.CompletedNoProposal
        }
    }

    private fun calculateDecision(
        recognizedText: Text,
        dimensions: OcrImageDimensions,
    ): AutoCropDecision {
        val mlKitObservations = recognizedText.toLobbyAnchorObservations()
        return calculateLobbyAutoCropDecision(
            observations = mlKitObservations,
            dimensions = dimensions,
            anchorResolver = anchorResolver,
            gridReconstructor = gridReconstructor,
            cropCalculator = cropCalculator,
        )
    }

    private fun Text.toLobbyAnchorObservations(): List<LobbyOcrAnchorObservation> = buildList {
        toRawOcrBlocks().forEachIndexed { blockIndex, block ->
            block.geometry?.boundingBox?.let { box ->
                add(
                    LobbyOcrAnchorObservation(
                        text = block.text,
                        boundingBox = box,
                        level = LobbyOcrAnchorLevel.BLOCK,
                        blockIndex = blockIndex,
                    ),
                )
            }
            block.lines.forEachIndexed { lineIndex, line ->
                line.geometry?.boundingBox?.let { box ->
                    add(
                        LobbyOcrAnchorObservation(
                            text = line.text,
                            boundingBox = box,
                            level = LobbyOcrAnchorLevel.LINE,
                            blockIndex = blockIndex,
                            lineIndex = lineIndex,
                            parentBoundingBox = block.geometry?.boundingBox,
                        ),
                    )
                }
                line.elements.forEachIndexed { elementIndex, element ->
                    element.geometry?.boundingBox?.let { box ->
                        add(
                            LobbyOcrAnchorObservation(
                                text = element.text,
                                boundingBox = box,
                                level = LobbyOcrAnchorLevel.ELEMENT,
                                blockIndex = blockIndex,
                                lineIndex = lineIndex,
                                elementIndex = elementIndex,
                                parentBoundingBox = line.geometry?.boundingBox,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun Bitmap.isUsable(): Boolean = !isRecycled && width > 0 && height > 0

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }
}

internal fun calculateLobbyAutoCropDecision(
    observations: List<LobbyOcrAnchorObservation>,
    dimensions: OcrImageDimensions,
    anchorResolver: LobbyOcrAnchorResolver,
    gridReconstructor: LobbySlotGridReconstructor,
    cropCalculator: LobbyAutoCropCalculator,
): AutoCropDecision {
    val resolvedGroups = anchorResolver.resolveAll(observations, dimensions)
    val potentialAnchorCount = resolvedGroups.maxOfOrNull { it.potentialAnchorCount } ?: 0

    val candidates = resolvedGroups.mapNotNull { resolved ->
        val reconstruction = gridReconstructor.reconstruct(
            screenshotIndex = resolved.screenshotIndex,
            observedAnchors = resolved.anchors.map { it.anchor },
        )
        val grid = (
            reconstruction as? com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyGridReconstructionResult.Reconstructed
            )?.grid ?: return@mapNotNull null
        LobbyAutoCropGridCandidate(
            grid = grid,
            directlyObservedAnchorCount = resolved.directlyObservedAnchorCount,
            alignmentError = resolved.alignmentError,
        )
    }
    val selected = LobbyAutoCropGroupSelector.select(candidates)
        ?: return potentialAnchorCount.retryDecision()
    return when (
        val calculation = cropCalculator.calculate(
            grid = selected.grid,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )
    ) {
        is LobbyAutoCropCalculationResult.Proposal ->
            AutoCropDecision.Proposed(MatchLobbyAutoCropResult.Proposed(calculation.crop))
        LobbyAutoCropCalculationResult.InvalidGridGeometry ->
            potentialAnchorCount.retryDecision()
        LobbyAutoCropCalculationResult.InvalidImageDimensions,
        LobbyAutoCropCalculationResult.InvalidCalibration,
        -> AutoCropDecision.NoProposal
    }
}

private sealed interface AutoCropAttempt {
    data class CompletedProposal(
        val result: MatchLobbyAutoCropResult.Proposed,
    ) : AutoCropAttempt

    data object CompletedNoProposal : AutoCropAttempt

    data object RetryWithEnhancement : AutoCropAttempt

    data object Failed : AutoCropAttempt
}

internal sealed interface AutoCropDecision {
    data class Proposed(
        val result: MatchLobbyAutoCropResult.Proposed,
    ) : AutoCropDecision

    data object RetryWithEnhancement : AutoCropDecision

    data object NoProposal : AutoCropDecision
}

private fun Int.retryDecision(): AutoCropDecision =
    if (this >= 2) AutoCropDecision.RetryWithEnhancement else AutoCropDecision.NoProposal

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { text ->
        if (continuation.isActive) continuation.resume(text)
    }
    addOnFailureListener { throwable ->
        if (continuation.isActive) continuation.resumeWithException(throwable)
    }
    addOnCanceledListener {
        continuation.cancel(CancellationException("ML Kit task was cancelled."))
    }
}
