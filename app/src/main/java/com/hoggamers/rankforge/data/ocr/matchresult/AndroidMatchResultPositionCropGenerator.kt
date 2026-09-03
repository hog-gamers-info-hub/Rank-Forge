package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropUnavailableReason
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class MatchResultPositionBitmapCrop(
    val geometry: MatchResultPositionCrop,
    val bitmap: Bitmap,
) {
    fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

sealed interface MatchResultPositionCropObservationResult {
    data class Observed(
        val evidence: MatchResultAutoCropEvidence,
    ) : MatchResultPositionCropObservationResult

    data object OcrFailed : MatchResultPositionCropObservationResult
    data object InvalidSource : MatchResultPositionCropObservationResult
}

sealed interface MatchResultPositionCropGenerationResult {
    data class Generated(
        val crops: List<MatchResultPositionBitmapCrop>,
        val geometry: MatchResultPositionCropCalculationResult.Available,
    ) : MatchResultPositionCropGenerationResult {
        fun release() = crops.forEach(MatchResultPositionBitmapCrop::release)
    }

    data class GeometryUnavailable(
        val reason: MatchResultPositionCropUnavailableReason,
    ) : MatchResultPositionCropGenerationResult

    data object OcrFailed : MatchResultPositionCropGenerationResult
    data object InvalidSource : MatchResultPositionCropGenerationResult
    data object BitmapCropFailed : MatchResultPositionCropGenerationResult
}

/**
 * Phase 1 / Slice 1 crop generator.
 *
 * ML Kit is used only to locate structural anchors. No existing Result OCR parsing,
 * cache, row assembly, matching, or scoring component is invoked here.
 * The caller retains ownership of [source]; generated position bitmaps are independent copies.
 */
@Singleton
class AndroidMatchResultPositionCropGenerator @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) {
    private val calculator = MatchResultPositionCropCalculator()

    suspend fun observe(
        source: Bitmap,
    ): MatchResultPositionCropObservationResult = withContext(Dispatchers.Default) {
        if (!source.isUsable()) {
            return@withContext MatchResultPositionCropObservationResult.InvalidSource
        }
        val dimensions = OcrImageDimensions.from(source.width, source.height)
            ?: return@withContext MatchResultPositionCropObservationResult.InvalidSource
        val inputImage = try {
            InputImage.fromBitmap(source, 0)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return@withContext MatchResultPositionCropObservationResult.InvalidSource
        }
        val recognizer = try {
            recognizerFactory.create()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return@withContext MatchResultPositionCropObservationResult.OcrFailed
        }

        val recognizedText = try {
            recognizer.process(inputImage).awaitPositionCropText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return@withContext MatchResultPositionCropObservationResult.OcrFailed
        } finally {
            recognizer.close()
        }

        MatchResultPositionCropObservationResult.Observed(
            evidence = MatchResultAutoCropEvidence(
                observations = recognizedText.toPositionCropObservations(dimensions),
                imageDimensions = dimensions,
            ),
        )
    }

    fun calculate(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean = false,
    ): MatchResultPositionCropCalculationResult = calculator.calculate(
        evidence = evidence,
        role = role,
        allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
    )

    suspend fun generate(
        source: Bitmap,
        geometry: MatchResultPositionCropCalculationResult.Available,
    ): MatchResultPositionCropGenerationResult = withContext(Dispatchers.Default) {
        generateBitmaps(source, geometry.crops, geometry)
    }

    /** Rasterizes persisted bounds only; it deliberately does not observe or calculate geometry. */
    suspend fun generate(
        source: Bitmap,
        crops: List<MatchResultPositionCrop>,
    ): MatchResultPositionCropGenerationResult = withContext(Dispatchers.Default) {
        generateBitmaps(
            source = source,
            crops = crops,
            geometry = MatchResultPositionCropCalculationResult.Available(
                crops = crops,
                leftRowPitch = null,
                rightRowPitch = 0.0,
                leftPitchSource = null,
                rightPitchSource = com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionPitchSource.RECOVERED_FROM_RIGHT,
            ),
        )
    }

    suspend fun generate(
        source: Bitmap,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean = false,
    ): MatchResultPositionCropGenerationResult {
        return when (val observation = observe(source)) {
            MatchResultPositionCropObservationResult.InvalidSource ->
                MatchResultPositionCropGenerationResult.InvalidSource
            MatchResultPositionCropObservationResult.OcrFailed ->
                MatchResultPositionCropGenerationResult.OcrFailed
            is MatchResultPositionCropObservationResult.Observed -> when (
                val geometry = calculate(
                    evidence = observation.evidence,
                    role = role,
                    allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
                )
            ) {
                is MatchResultPositionCropCalculationResult.Unavailable ->
                    MatchResultPositionCropGenerationResult.GeometryUnavailable(geometry.reason)
                is MatchResultPositionCropCalculationResult.Available -> generate(source, geometry)
            }
        }
    }

    private fun generateBitmaps(
        source: Bitmap,
        crops: List<MatchResultPositionCrop>,
        geometry: MatchResultPositionCropCalculationResult.Available,
    ): MatchResultPositionCropGenerationResult {
        val generated = mutableListOf<MatchResultPositionBitmapCrop>()
        try {
            crops.forEach { crop ->
                val extracted = Bitmap.createBitmap(
                    source,
                    crop.bounds.left,
                    crop.bounds.top,
                    crop.bounds.width,
                    crop.bounds.height,
                )
                val ownedCopy = try {
                    extracted.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    if (extracted !== source && !extracted.isRecycled) {
                        extracted.recycle()
                    }
                } ?: throw IllegalStateException("Unable to copy result position crop bitmap.")
                generated += MatchResultPositionBitmapCrop(crop, ownedCopy)
            }
            return MatchResultPositionCropGenerationResult.Generated(
                crops = generated,
                geometry = geometry,
            )
        } catch (cancellation: CancellationException) {
            generated.forEach(MatchResultPositionBitmapCrop::release)
            throw cancellation
        } catch (_: Throwable) {
            generated.forEach(MatchResultPositionBitmapCrop::release)
            return MatchResultPositionCropGenerationResult.BitmapCropFailed
        }
    }

    private fun Text.toPositionCropObservations(
        dimensions: OcrImageDimensions,
    ): List<MatchResultAutoCropObservation> = toRawOcrBlocks()
        .asSequence()
        .flatMap { block ->
            block.lines.asSequence().flatMap { line -> line.elements.asSequence() }
        }
        .mapNotNull { element ->
            val boundingBox = element.geometry?.boundingBox
                ?.clampToPositionCropImageOrNull(dimensions)
                ?: return@mapNotNull null
            MatchResultAutoCropObservation(
                text = element.text,
                boundingBox = boundingBox,
            )
        }
        .toList()

    private fun Bitmap.isUsable(): Boolean = !isRecycled && width > 0 && height > 0

}

/**
 * ML Kit can report an element just beyond the decoded bitmap edge. Position-crop geometry uses
 * source coordinates, so retain only the visible part and reject boxes with no visible area.
 */
internal fun RawOcrBoundingBox.clampToPositionCropImageOrNull(
    dimensions: OcrImageDimensions,
): RawOcrBoundingBox? = RawOcrBoundingBox(
    left = left.coerceIn(0, dimensions.width),
    top = top.coerceIn(0, dimensions.height),
    right = right.coerceIn(0, dimensions.width),
    bottom = bottom.coerceIn(0, dimensions.height),
).takeIf { normalized ->
    normalized.right > normalized.left && normalized.bottom > normalized.top
}

private suspend fun Task<Text>.awaitPositionCropText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { text ->
        if (continuation.isActive) continuation.resume(text)
    }
    addOnFailureListener { throwable ->
        if (continuation.isActive) continuation.resumeWithException(throwable)
    }
    addOnCanceledListener {
        continuation.cancel(CancellationException("ML Kit position-crop task was cancelled."))
    }
}
