package com.hoggamers.rankforge.data.ocr.preprocessing

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

data class OcrEnhancementProfile(
    val clarityStrength: Float,
    val clarityRadius: Float,
    val sharpnessStrength: Float,
    val sharpnessRadius: Float,
) {
    init {
        require(clarityStrength.isFinite() && clarityStrength >= 0f)
        require(clarityRadius.isFinite() && clarityRadius >= 0f)
        require(sharpnessStrength.isFinite() && sharpnessStrength >= 0f)
        require(sharpnessRadius.isFinite() && sharpnessRadius >= 0f)
    }
}

interface OcrImageEnhancer {
    fun enhance(source: Bitmap, profile: OcrEnhancementProfile): Bitmap?
}

class AndroidOcrImageEnhancer : OcrImageEnhancer {
    override fun enhance(source: Bitmap, profile: OcrEnhancementProfile): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val pixelCount = source.width.toLong() * source.height.toLong()
        if (pixelCount > Int.MAX_VALUE) return null

        var enhancedBitmap: Bitmap? = null
        return try {
            val sourcePixels = IntArray(pixelCount.toInt())
            source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
            val enhancedPixels = enhancePixels(sourcePixels, source.width, source.height, profile)
            enhancedBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            enhancedBitmap.setPixels(
                enhancedPixels,
                0,
                source.width,
                0,
                0,
                source.width,
                source.height,
            )
            enhancedBitmap
        } catch (_: OutOfMemoryError) {
            enhancedBitmap?.recycleIfNeeded()
            null
        } catch (_: RuntimeException) {
            enhancedBitmap?.recycleIfNeeded()
            null
        }
    }

    internal fun enhancePixels(
        sourcePixels: IntArray,
        width: Int,
        height: Int,
        profile: OcrEnhancementProfile,
    ): IntArray {
        require(width > 0 && height > 0 && sourcePixels.size == width * height)
        if (profile.clarityStrength == 0f && profile.sharpnessStrength == 0f) {
            return sourcePixels.copyOf()
        }

        val sourceLuminance = FloatArray(sourcePixels.size)
        for (index in sourcePixels.indices) {
            sourceLuminance[index] = luminance(sourcePixels[index])
        }

        val clarityLuminance = if (profile.clarityStrength > 0f && profile.clarityRadius > 0f) {
            val blurred = gaussianBlur(sourceLuminance, width, height, profile.clarityRadius)
            FloatArray(sourceLuminance.size) { index ->
                sourceLuminance[index] +
                    (sourceLuminance[index] - blurred[index]) * profile.clarityStrength
            }
        } else {
            sourceLuminance.copyOf()
        }

        val outputLuminance = if (profile.sharpnessStrength > 0f && profile.sharpnessRadius > 0f) {
            val blurred = gaussianBlur(clarityLuminance, width, height, profile.sharpnessRadius)
            FloatArray(clarityLuminance.size) { index ->
                clarityLuminance[index] +
                    (clarityLuminance[index] - blurred[index]) * profile.sharpnessStrength
            }
        } else {
            clarityLuminance
        }

        return IntArray(sourcePixels.size) { index ->
            val sourceColor = sourcePixels[index]
            val luminanceDelta = outputLuminance[index] - sourceLuminance[index]
            val red = adjustChannel(sourceColor shr 16 and 0xff, luminanceDelta)
            val green = adjustChannel(sourceColor shr 8 and 0xff, luminanceDelta)
            val blue = adjustChannel(sourceColor and 0xff, luminanceDelta)
            (sourceColor and (0xff shl 24)) or
                (red shl 16) or
                (green shl 8) or
                blue
        }
    }

    private fun gaussianBlur(
        source: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
    ): FloatArray {
        val kernelRadius = ceil(sigma * 3f).roundToInt().coerceAtLeast(1)
        val kernel = gaussianKernel(kernelRadius, sigma)
        val horizontal = FloatArray(source.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var total = 0f
                for (offset in -kernelRadius..kernelRadius) {
                    val sampleX = (x + offset).coerceIn(0, width - 1)
                    total += source[y * width + sampleX] * kernel[offset + kernelRadius]
                }
                horizontal[y * width + x] = total
            }
        }

        val blurred = FloatArray(source.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var total = 0f
                for (offset in -kernelRadius..kernelRadius) {
                    val sampleY = (y + offset).coerceIn(0, height - 1)
                    total += horizontal[sampleY * width + x] * kernel[offset + kernelRadius]
                }
                blurred[y * width + x] = total
            }
        }
        return blurred
    }

    private fun gaussianKernel(radius: Int, sigma: Float): FloatArray {
        val kernel = FloatArray(radius * 2 + 1)
        val denominator = 2f * sigma * sigma
        var total = 0f
        for (index in kernel.indices) {
            val distance = index - radius
            val value = exp(-(distance * distance) / denominator)
            kernel[index] = value
            total += value
        }
        for (index in kernel.indices) kernel[index] /= total
        return kernel
    }

    private fun luminance(color: Int): Float =
        0.2126f * (color shr 16 and 0xff) +
            0.7152f * (color shr 8 and 0xff) +
            0.0722f * (color and 0xff)

    private fun adjustChannel(channel: Int, delta: Float): Int =
        (channel + delta).roundToInt().coerceIn(0, 255)

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }
}

/** Initial strong Lobby OCR local-contrast profile, to be calibrated against ML Kit results. */
internal val LOBBY_OCR_ENHANCEMENT_PROFILE = OcrEnhancementProfile(
    clarityStrength = 1.0f,
    clarityRadius = 5.0f,
    sharpnessStrength = 0.20f,
    sharpnessRadius = 1.25f,
)
