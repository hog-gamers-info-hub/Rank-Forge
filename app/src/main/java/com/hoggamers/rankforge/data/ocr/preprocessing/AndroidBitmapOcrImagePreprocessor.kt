package com.hoggamers.rankforge.data.ocr.preprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutValidator
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrImagePreprocessor
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCrop
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingFailure
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingInput
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidBitmapOcrImage(
    val bitmap: Bitmap,
) : OcrPreprocessingImage {
    override val width: Int
        get() = bitmap.width

    override val height: Int
        get() = bitmap.height
}

interface AndroidBitmapOcrPreprocessingOperations {
    fun isReadable(image: OcrPreprocessingImage): Boolean

    fun crop(image: OcrPreprocessingImage, cropRect: OcrPixelRect): OcrPreprocessingImage?

    fun scale(image: OcrPreprocessingImage, targetWidth: Int, targetHeight: Int): OcrPreprocessingImage?

    fun adjustContrast(image: OcrPreprocessingImage): OcrPreprocessingImage?

    fun discardGenerated(image: OcrPreprocessingImage)
}

class AndroidBitmapOcrImagePreprocessor(
    private val layout: ScoreboardLayoutDefinition = FreeFireMaxScoreboardLayout.definition,
    private val layoutValidator: ScoreboardLayoutValidator = ScoreboardLayoutValidator(),
    private val bitmapOperations: AndroidBitmapOcrPreprocessingOperations =
        DefaultAndroidBitmapOcrPreprocessingOperations,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OcrImagePreprocessor {
    override suspend fun preprocess(input: OcrPreprocessingInput): OcrPreprocessingResult =
        withContext(dispatcher) {
            val dimensions = readDimensions(input.image)
                ?: return@withContext OcrPreprocessingResult.Failed(
                    OcrPreprocessingFailure.UNREADABLE_INPUT,
                )
            val (imageWidth, imageHeight) = dimensions
            if (imageWidth <= 0 || imageHeight <= 0) {
                return@withContext OcrPreprocessingResult.Failed(
                    OcrPreprocessingFailure.INVALID_DIMENSIONS,
                )
            }
            if (!isReadable(input.image)) {
                return@withContext OcrPreprocessingResult.Failed(
                    OcrPreprocessingFailure.UNREADABLE_INPUT,
                )
            }

            when (layoutValidator.validate(layout, imageWidth, imageHeight)) {
                ScoreboardLayoutValidationResult.Compatible -> Unit
                is ScoreboardLayoutValidationResult.Incompatible -> {
                    return@withContext OcrPreprocessingResult.Failed(
                        OcrPreprocessingFailure.UNSUPPORTED_LAYOUT,
                    )
                }
            }

            val cropRect = try {
                layout.overallContentRect.toPixelRect(imageWidth, imageHeight)
            } catch (_: IllegalArgumentException) {
                return@withContext OcrPreprocessingResult.Failed(
                    OcrPreprocessingFailure.INVALID_CROP_BOUNDS,
                )
            }
            if (!isSafeCrop(cropRect, imageWidth, imageHeight)) {
                return@withContext OcrPreprocessingResult.Failed(
                    OcrPreprocessingFailure.INVALID_CROP_BOUNDS,
                )
            }
            if (!isSafePixelCount(cropRect.width, cropRect.height)) {
                return@withContext OcrPreprocessingResult.Failed(
                    OcrPreprocessingFailure.RESOURCE_ALLOCATION_FAILED,
                )
            }

            val generatedImages = mutableListOf<OcrPreprocessingImage>()
            fun fail(failure: OcrPreprocessingFailure): OcrPreprocessingResult.Failed {
                generatedImages.forEach { image ->
                    runCatching { bitmapOperations.discardGenerated(image) }
                }
                return OcrPreprocessingResult.Failed(failure)
            }

            val baselineImage = try {
                bitmapOperations.crop(input.image, cropRect)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: OutOfMemoryError) {
                return@withContext fail(OcrPreprocessingFailure.RESOURCE_ALLOCATION_FAILED)
            } catch (_: IllegalArgumentException) {
                return@withContext fail(OcrPreprocessingFailure.INVALID_CROP_BOUNDS)
            } catch (_: RuntimeException) {
                return@withContext fail(OcrPreprocessingFailure.PREPROCESSING_FAILED)
            } ?: return@withContext fail(OcrPreprocessingFailure.INVALID_CROP_BOUNDS)
            generatedImages += baselineImage

            val candidates = mutableListOf(
                OcrPreprocessingCandidate(
                    order = 0,
                    crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
                    cropRect = cropRect,
                    image = baselineImage,
                    appliedSteps = listOf(OcrPreprocessingStep.CROP),
                    scaleFactor = null,
                ),
            )
            var candidatePixels = pixelCount(baselineImage.width, baselineImage.height)

            SCALE_FACTORS.forEach { scaleFactor ->
                val targetDimensions = scaledDimensions(baselineImage, scaleFactor) ?: return@forEach
                val (targetWidth, targetHeight) = targetDimensions
                val targetPixels = pixelCount(targetWidth, targetHeight)
                if (!isSafePixelCount(targetWidth, targetHeight) ||
                    candidatePixels + targetPixels > MAX_TOTAL_CANDIDATE_PIXELS
                ) {
                    return@forEach
                }

                val scaledImage = try {
                    bitmapOperations.scale(baselineImage, targetWidth, targetHeight)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: OutOfMemoryError) {
                    return@withContext fail(OcrPreprocessingFailure.RESOURCE_ALLOCATION_FAILED)
                } catch (_: RuntimeException) {
                    return@withContext fail(OcrPreprocessingFailure.PREPROCESSING_FAILED)
                } ?: return@withContext fail(OcrPreprocessingFailure.PREPROCESSING_FAILED)
                generatedImages += scaledImage
                candidatePixels += targetPixels
                candidates += OcrPreprocessingCandidate(
                    order = candidates.size,
                    crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
                    cropRect = cropRect,
                    image = scaledImage,
                    appliedSteps = listOf(
                        OcrPreprocessingStep.CROP,
                        OcrPreprocessingStep.SCALE,
                    ),
                    scaleFactor = scaleFactor,
                )

                if (candidatePixels + targetPixels > MAX_TOTAL_CANDIDATE_PIXELS) {
                    return@forEach
                }
                val contrastImage = try {
                    bitmapOperations.adjustContrast(scaledImage)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: OutOfMemoryError) {
                    return@withContext fail(OcrPreprocessingFailure.RESOURCE_ALLOCATION_FAILED)
                } catch (_: RuntimeException) {
                    return@withContext fail(OcrPreprocessingFailure.PREPROCESSING_FAILED)
                } ?: return@withContext fail(OcrPreprocessingFailure.PREPROCESSING_FAILED)
                generatedImages += contrastImage
                candidatePixels += targetPixels
                candidates += OcrPreprocessingCandidate(
                    order = candidates.size,
                    crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
                    cropRect = cropRect,
                    image = contrastImage,
                    appliedSteps = listOf(
                        OcrPreprocessingStep.CROP,
                        OcrPreprocessingStep.SCALE,
                        OcrPreprocessingStep.CONTRAST_ADJUSTMENT,
                    ),
                    scaleFactor = scaleFactor,
                )
            }

            OcrPreprocessingResult.Candidates(candidates)
        }

    private fun readDimensions(image: OcrPreprocessingImage): Pair<Int, Int>? = try {
        image.width to image.height
    } catch (_: RuntimeException) {
        null
    }

    private fun isReadable(image: OcrPreprocessingImage): Boolean =
        runCatching { bitmapOperations.isReadable(image) }.getOrDefault(false)

    private fun isSafeCrop(
        cropRect: OcrPixelRect,
        imageWidth: Int,
        imageHeight: Int,
    ): Boolean = cropRect.x >= 0 &&
        cropRect.y >= 0 &&
        cropRect.width > 0 &&
        cropRect.height > 0 &&
        cropRect.x.toLong() + cropRect.width <= imageWidth &&
        cropRect.y.toLong() + cropRect.height <= imageHeight

    private fun scaledDimensions(
        image: OcrPreprocessingImage,
        scaleFactor: Double,
    ): Pair<Int, Int>? {
        val scaledWidth = image.width * scaleFactor
        val scaledHeight = image.height * scaleFactor
        if (scaledWidth > Int.MAX_VALUE || scaledHeight > Int.MAX_VALUE) return null
        val targetWidth = scaledWidth.roundToInt()
        val targetHeight = scaledHeight.roundToInt()
        return if (targetWidth > 0 && targetHeight > 0) targetWidth to targetHeight else null
    }

    private fun isSafePixelCount(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && pixelCount(width, height) <= MAX_CANDIDATE_PIXELS

    private fun pixelCount(width: Int, height: Int): Long = width.toLong() * height.toLong()

    private companion object {
        val SCALE_FACTORS = listOf(1.5, 2.0)
        const val MAX_CANDIDATE_PIXELS = 16_000_000L
        const val MAX_TOTAL_CANDIDATE_PIXELS = 16_000_000L
    }
}

private object DefaultAndroidBitmapOcrPreprocessingOperations : AndroidBitmapOcrPreprocessingOperations {
    override fun isReadable(image: OcrPreprocessingImage): Boolean =
        (image as? AndroidBitmapOcrImage)?.bitmap?.let { bitmap ->
            !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0
        } == true

    override fun crop(
        image: OcrPreprocessingImage,
        cropRect: OcrPixelRect,
    ): OcrPreprocessingImage? {
        val bitmap = image.asReadableBitmap() ?: return null
        return AndroidBitmapOcrImage(
            Bitmap.createBitmap(
                bitmap,
                cropRect.x,
                cropRect.y,
                cropRect.width,
                cropRect.height,
            ),
        )
    }

    override fun scale(
        image: OcrPreprocessingImage,
        targetWidth: Int,
        targetHeight: Int,
    ): OcrPreprocessingImage? {
        val bitmap = image.asReadableBitmap() ?: return null
        return AndroidBitmapOcrImage(Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true))
    }

    override fun adjustContrast(image: OcrPreprocessingImage): OcrPreprocessingImage? {
        val bitmap = image.asReadableBitmap() ?: return null
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.25f
        val offset = (1f - contrast) * 128f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, offset,
                        0f, contrast, 0f, 0f, offset,
                        0f, 0f, contrast, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, paint)
        return AndroidBitmapOcrImage(enhanced)
    }

    override fun discardGenerated(image: OcrPreprocessingImage) {
        (image as? AndroidBitmapOcrImage)?.bitmap?.takeIf { !it.isRecycled }?.recycle()
    }

    private fun OcrPreprocessingImage.asReadableBitmap(): Bitmap? =
        (this as? AndroidBitmapOcrImage)?.bitmap?.takeIf { bitmap -> !bitmap.isRecycled }
}
