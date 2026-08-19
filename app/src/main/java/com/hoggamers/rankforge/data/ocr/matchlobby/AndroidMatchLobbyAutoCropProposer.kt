package com.hoggamers.rankforge.data.ocr.matchlobby

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
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
            val inputImage = try {
                InputImage.fromBitmap(original, 0)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return@withContext MatchLobbyAutoCropResult.NoProposal
            }
            val recognizer = try {
                recognizerFactory.create()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return@withContext MatchLobbyAutoCropResult.NoProposal
            }

            try {
                val recognizedText = try {
                    recognizer.process(inputImage).awaitText()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return@withContext MatchLobbyAutoCropResult.NoProposal
                }
                val observations = recognizedText.toLobbyAnchorObservations()
                val candidates = anchorResolver.resolveAll(observations, dimensions).mapNotNull { resolved ->
                    val reconstruction = gridReconstructor.reconstruct(
                        screenshotIndex = resolved.screenshotIndex,
                        observedAnchors = resolved.anchors.map { it.anchor },
                    )
                    val grid = (reconstruction as? com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyGridReconstructionResult.Reconstructed)
                        ?.grid
                        ?: return@mapNotNull null
                    LobbyAutoCropGridCandidate(
                        grid = grid,
                        directlyObservedAnchorCount = resolved.directlyObservedAnchorCount,
                        alignmentError = resolved.alignmentError,
                    )
                }
                val selected = LobbyAutoCropGroupSelector.select(candidates)
                    ?: return@withContext MatchLobbyAutoCropResult.NoProposal
                when (
                    val calculation = cropCalculator.calculate(
                        grid = selected.grid,
                        imageWidth = dimensions.width,
                        imageHeight = dimensions.height,
                        calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
                    )
                ) {
                    is LobbyAutoCropCalculationResult.Proposal ->
                        MatchLobbyAutoCropResult.Proposed(calculation.crop)
                    LobbyAutoCropCalculationResult.InvalidImageDimensions,
                    LobbyAutoCropCalculationResult.InvalidGridGeometry,
                    LobbyAutoCropCalculationResult.InvalidCalibration,
                    -> MatchLobbyAutoCropResult.NoProposal
                }
            } finally {
                recognizer.close()
            }
        } finally {
            original.recycleIfNeeded()
        }
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
