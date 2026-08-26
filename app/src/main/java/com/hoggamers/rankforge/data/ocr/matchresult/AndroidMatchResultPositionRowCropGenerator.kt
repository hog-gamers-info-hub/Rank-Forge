package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCropCalculator
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCropObservation
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class MatchResultPositionRowBitmapCrop(
    val geometry: MatchResultPositionRowCrop,
    val bitmap: Bitmap,
) {
    fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

sealed interface MatchResultPositionRowCropGenerationResult {
    data class Generated(val crops: List<MatchResultPositionRowBitmapCrop>) : MatchResultPositionRowCropGenerationResult {
        fun release() = crops.forEach(MatchResultPositionRowBitmapCrop::release)
    }

    data object Unavailable : MatchResultPositionRowCropGenerationResult
}

@Singleton
class AndroidMatchResultPositionRowCropGenerator @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) {
    private val calculator = MatchResultPositionRowCropCalculator()

    suspend fun generate(
        source: Bitmap,
        position: Int,
    ): MatchResultPositionRowCropGenerationResult = withContext(Dispatchers.Default) {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) {
            return@withContext MatchResultPositionRowCropGenerationResult.Unavailable
        }
        val recognizer = try {
            recognizerFactory.create()
        } catch (_: Throwable) {
            return@withContext MatchResultPositionRowCropGenerationResult.Unavailable
        }
        val text = try {
            try {
                recognizer.process(InputImage.fromBitmap(source, 0)).awaitRowCropText()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return@withContext MatchResultPositionRowCropGenerationResult.Unavailable
            }
        } finally {
            recognizer.close()
        }
        val observations = text.toRowCropObservations(source.width, source.height)
        val calculation = calculator.calculate(position, source.width, source.height, observations)
        val geometry = calculation as? MatchResultPositionRowCropCalculationResult.Available
            ?: return@withContext MatchResultPositionRowCropGenerationResult.Unavailable
        val crops = mutableListOf<MatchResultPositionRowBitmapCrop>()
        try {
            geometry.crops.forEach { crop ->
                val extracted = Bitmap.createBitmap(
                    source,
                    crop.bounds.left,
                    crop.bounds.top,
                    crop.bounds.width,
                    crop.bounds.height,
                )
                val owned = try {
                    extracted.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    if (extracted !== source && !extracted.isRecycled) extracted.recycle()
                } ?: throw IllegalStateException("Unable to copy result row crop bitmap.")
                crops += MatchResultPositionRowBitmapCrop(crop, owned)
            }
            MatchResultPositionRowCropGenerationResult.Generated(crops)
        } catch (cancellation: CancellationException) {
            crops.forEach(MatchResultPositionRowBitmapCrop::release)
            throw cancellation
        } catch (_: Throwable) {
            crops.forEach(MatchResultPositionRowBitmapCrop::release)
            MatchResultPositionRowCropGenerationResult.Unavailable
        }
    }

    private fun Text.toRowCropObservations(width: Int, height: Int): List<MatchResultPositionRowCropObservation> =
        toRawOcrBlocks().flatMap { block ->
            block.lines.mapNotNull { line ->
                line.geometry?.boundingBox?.let { box ->
                    MatchResultPositionRowCropObservation(
                        text = line.text,
                        boundingBox = box.clampTo(width, height),
                    )
                }
            }
        }
}

private fun RawOcrBoundingBox.clampTo(width: Int, height: Int): RawOcrBoundingBox = RawOcrBoundingBox(
    left = left.coerceIn(0, width),
    top = top.coerceIn(0, height),
    right = right.coerceIn(0, width),
    bottom = bottom.coerceIn(0, height),
)

private suspend fun Task<Text>.awaitRowCropText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { text -> if (continuation.isActive) continuation.resume(text) }
    addOnFailureListener { throwable -> if (continuation.isActive) continuation.resumeWithException(throwable) }
    addOnCanceledListener { continuation.cancel(CancellationException("ML Kit row crop task was cancelled.")) }
}
