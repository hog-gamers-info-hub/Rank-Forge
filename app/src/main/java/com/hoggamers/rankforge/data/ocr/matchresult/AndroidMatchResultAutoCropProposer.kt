package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropCalculator
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropResult
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
class AndroidMatchResultAutoCropProposer @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : MatchResultAutoCropProposer {
    override suspend fun propose(localFile: File): MatchResultAutoCropResult = withContext(Dispatchers.IO) {
        val original = try {
            BitmapFactory.decodeFile(localFile.absolutePath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return@withContext MatchResultAutoCropResult.OcrFailed

        if (!original.isUsable()) {
            original.recycleIfNeeded()
            return@withContext MatchResultAutoCropResult.OcrFailed
        }

        try {
            val dimensions = OcrImageDimensions.from(original.width, original.height)
                ?: return@withContext MatchResultAutoCropResult.OcrFailed
            val inputImage = try {
                InputImage.fromBitmap(original, 0)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return@withContext MatchResultAutoCropResult.OcrFailed
            }

            val recognizer = try {
                recognizerFactory.create()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return@withContext MatchResultAutoCropResult.OcrFailed
            }

            try {
                val recognizedText = try {
                    recognizer.process(inputImage).awaitText()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return@withContext MatchResultAutoCropResult.OcrFailed
                }

                val observations = recognizedText.toElementObservations(dimensions)
                MatchResultAutoCropCalculator().calculate(
                    MatchResultAutoCropEvidence(
                        observations = observations,
                        imageDimensions = dimensions,
                    ),
                )
            } finally {
                recognizer.close()
            }
        } finally {
            original.recycleIfNeeded()
        }
    }

    private fun Text.toElementObservations(
        dimensions: OcrImageDimensions,
    ): List<MatchResultAutoCropObservation> = toRawOcrBlocks()
        .asSequence()
        .flatMap { block ->
            block.lines.asSequence().flatMap { line -> line.elements.asSequence() }
        }
        .mapNotNull { element ->
            val boundingBox = element.geometry?.boundingBox
                ?.takeIf { it.isUsableFor(dimensions) }
                ?: return@mapNotNull null
            MatchResultAutoCropObservation(
                text = element.text,
                boundingBox = boundingBox,
            )
        }
        .toList()

    private fun RawOcrBoundingBox.isUsableFor(dimensions: OcrImageDimensions): Boolean =
        right > left &&
            bottom > top &&
            right > 0 &&
            bottom > 0 &&
            left < dimensions.width &&
            top < dimensions.height

    private fun Bitmap.isUsable(): Boolean =
        !isRecycled && width > 0 && height > 0

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
        continuation.cancel(CancellationException("ML Kit OCR task was cancelled."))
    }
}
