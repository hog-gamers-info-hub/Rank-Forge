package com.hoggamers.rankforge.data.ocr.preprocessing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOcrImageEnhancerTest {
    private val enhancer = AndroidOcrImageEnhancer()

    @Test
    fun disabledProfilePreservesPixelsAndDimensions() {
        val width = 1600
        val height = 702
        val source = IntArray(width * height) { index ->
            0xff000000.toInt() or (index % 256 shl 16) or (index % 128 shl 8) or (index % 64)
        }

        val output = enhancer.enhancePixels(
            sourcePixels = source,
            width = width,
            height = height,
            profile = OcrEnhancementProfile(0f, 0f, 0f, 0f),
        )

        assertEquals(width * height, output.size)
        assertArrayEquals(source, output)
    }

    @Test
    fun clarityIncreasesMediumScaleLuminanceSeparation() {
        val width = 15
        val height = 15
        val source = IntArray(width * height) { 0xff282828.toInt() }
        for (y in 6..8) {
            for (x in 6..8) {
                source[y * width + x] = 0xff464646.toInt()
            }
        }

        val output = enhancer.enhancePixels(
            sourcePixels = source,
            width = width,
            height = height,
            profile = OcrEnhancementProfile(
                clarityStrength = 1.0f,
                clarityRadius = 5.0f,
                sharpnessStrength = 0f,
                sharpnessRadius = 0f,
            ),
        )

        val originalSeparation = luminance(source[7 * width + 7]) - luminance(source[0])
        val outputSeparation = luminance(output[7 * width + 7]) - luminance(output[0])
        assertTrue(outputSeparation > originalSeparation)
    }

    @Test
    fun sharpnessIncreasesFineEdgeSeparationWithoutResizing() {
        val width = 9
        val height = 9
        val source = IntArray(width * height) { 0xff282828.toInt() }
        for (x in 2..6) source[4 * width + x] = 0xff464646.toInt()

        val output = enhancer.enhancePixels(
            sourcePixels = source,
            width = width,
            height = height,
            profile = OcrEnhancementProfile(
                clarityStrength = 0f,
                clarityRadius = 0f,
                sharpnessStrength = 0.20f,
                sharpnessRadius = 1.25f,
            ),
        )

        assertEquals(source.size, output.size)
        val originalSeparation = luminance(source[4 * width + 4]) - luminance(source[0])
        val outputSeparation = luminance(output[4 * width + 4]) - luminance(output[0])
        assertTrue(outputSeparation > originalSeparation)
    }

    @Test
    fun outputChannelsStayInRangeAtBlackAndWhiteExtremes() {
        val source = intArrayOf(
            0xff000000.toInt(), 0xffffffff.toInt(),
            0xffffffff.toInt(), 0xff000000.toInt(),
        )

        val output = enhancer.enhancePixels(
            sourcePixels = source,
            width = 2,
            height = 2,
            profile = OcrEnhancementProfile(1.0f, 5.0f, 0.20f, 1.25f),
        )

        output.forEach { color ->
            assertTrue(color ushr 24 == 0xff)
            assertTrue(color shr 16 and 0xff in 0..255)
            assertTrue(color shr 8 and 0xff in 0..255)
            assertTrue(color and 0xff in 0..255)
        }
    }

    private fun luminance(color: Int): Float =
        0.2126f * (color shr 16 and 0xff) +
            0.7152f * (color shr 8 and 0xff) +
            0.0722f * (color and 0xff)
}
