package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import kotlin.math.roundToInt

enum class MatchResultRowOcrCandidate(val scale: Int) {
    SCALE_3X(3),
    SCALE_4X(4),
}

/** Creates owned, lossless-in-memory row candidates without changing Phase 1 geometry. */
class AndroidMatchResultRowOcrPreprocessor {
    fun create(source: Bitmap, scale: MatchResultRowOcrCandidate): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val width = source.width.toLong() * scale.scale
        val height = source.height.toLong() * scale.scale
        if (width !in 1..MAX_BITMAP_DIMENSION || height !in 1..MAX_BITMAP_DIMENSION) return null
        val pixelCount = width * height
        if (pixelCount > MAX_PIXEL_COUNT) return null

        val scaled = try {
            Bitmap.createScaledBitmap(source, width.toInt(), height.toInt(), true)
        } catch (_: Throwable) {
            return null
        }
        try {
            val pixels = IntArray(pixelCount.toInt())
            scaled.getPixels(pixels, 0, width.toInt(), 0, 0, width.toInt(), height.toInt())

            val grayscale = IntArray(pixels.size)
            for (index in pixels.indices) {
                val color = pixels[index]
                grayscale[index] = ((77 * ((color shr 16) and 0xff) +
                    150 * ((color shr 8) and 0xff) + 29 * (color and 0xff)) / 256)
            }

            // A restrained global contrast pass is deterministic and keeps the row text intact.
            val contrasted = IntArray(pixels.size)
            for (index in grayscale.indices) {
                contrasted[index] = ((grayscale[index] - 128) * CONTRAST + 128)
                    .roundToInt().coerceIn(0, 255)
            }

            // Small unsharp kernel; this is intentionally bounded to avoid ringing.
            val sharpened = IntArray(pixels.size)
            for (y in 0 until height.toInt()) {
                for (x in 0 until width.toInt()) {
                    val center = contrasted[y * width.toInt() + x] * 5
                    val left = contrasted[y * width.toInt() + x.coerceAtLeast(1) - 1]
                    val right = contrasted[y * width.toInt() + x.coerceAtMost(width.toInt() - 2) + 1]
                    val top = contrasted[(y.coerceAtLeast(1) - 1) * width.toInt() + x]
                    val bottom = contrasted[(y.coerceAtMost(height.toInt() - 2) + 1) * width.toInt() + x]
                    val neighbors = left + right + top + bottom
                    sharpened[y * width.toInt() + x] = (center - neighbors).coerceIn(0, 255)
                }
            }

            val histogram = IntArray(256)
            sharpened.forEach { histogram[it]++ }
            val threshold = otsuThreshold(histogram, sharpened.size)
            var output: Bitmap? = null
            try {
                output = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
                for (index in sharpened.indices) {
                    val value = if (sharpened[index] > threshold) 0xff else 0x00
                    pixels[index] = (0xff shl 24) or (value shl 16) or (value shl 8) or value
                }
                output.setPixels(pixels, 0, width.toInt(), 0, 0, width.toInt(), height.toInt())
                return output
            } catch (_: Throwable) {
                output?.takeUnless { it.isRecycled }?.recycle()
                return null
            }
        } catch (_: Throwable) {
            return null
        } finally {
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0L
        histogram.forEachIndexed { index, count -> sum += index.toLong() * count }
        var weightBackground = 0L
        var sumBackground = 0L
        var bestThreshold = 127
        var bestVariance = -1.0
        for (threshold in histogram.indices) {
            weightBackground += histogram[threshold]
            if (weightBackground == 0L) continue
            val weightForeground = total - weightBackground
            if (weightForeground <= 0) break
            sumBackground += threshold.toLong() * histogram[threshold]
            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sum - sumBackground).toDouble() / weightForeground
            val variance = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }
        return bestThreshold
    }

    private companion object {
        const val CONTRAST = 1.35
        const val MAX_BITMAP_DIMENSION = 16_384L
        const val MAX_PIXEL_COUNT = 64_000_000L
    }
}
