package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalScreenshotPreviewTest {
    @Test
    fun smallImageUsesNoSampling() {
        assertEquals(1, calculateLocalScreenshotPreviewSampleSize(1024, 768))
    }

    @Test
    fun sixteenHundredPixelLongEdgeUsesSampleTwo() {
        assertEquals(2, calculateLocalScreenshotPreviewSampleSize(1600, 720))
    }

    @Test
    fun nineteenTwentyPixelLongEdgeUsesSampleTwo() {
        assertEquals(2, calculateLocalScreenshotPreviewSampleSize(1920, 1080))
    }

    @Test
    fun edgeJustAboveTwoTimesMaxUsesSampleFour() {
        assertEquals(4, calculateLocalScreenshotPreviewSampleSize(2049, 1080))
    }

    @Test
    fun veryWideImageUsesPowerOfTwoSampleEight() {
        assertEquals(8, calculateLocalScreenshotPreviewSampleSize(8192, 512))
    }

    @Test
    fun invalidDimensionsReturnSafeZeroSample() {
        assertEquals(0, calculateLocalScreenshotPreviewSampleSize(0, 1080))
        assertEquals(0, calculateLocalScreenshotPreviewSampleSize(1080, -1))
    }

    @Test
    fun validImageIsNeverUpscaled() {
        assertEquals(1, calculateLocalScreenshotPreviewSampleSize(320, 240))
    }

    @Test
    fun fullImageCropConvertsToTheFullSourcePixelRect() {
        assertEquals(
            LocalScreenshotPreviewPixelRect(0, 0, 1600, 720),
            normalizedCropToLocalScreenshotPreviewPixelRect(
                OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
                1600,
                720,
            ),
        )
    }

    @Test
    fun centeredCropConvertsUsingFloorAndCeil() {
        assertEquals(
            LocalScreenshotPreviewPixelRect(400, 180, 1200, 540),
            normalizedCropToLocalScreenshotPreviewPixelRect(
                OcrNormalizedCropRect(0.25, 0.25, 0.75, 0.75),
                1600,
                720,
            ),
        )
    }

    @Test
    fun cropRegionControlsSampling() {
        assertEquals(1, calculateLocalScreenshotPreviewSampleSize(800, 400))
        assertEquals(2, calculateLocalScreenshotPreviewSampleSize(1600, 720))
        assertEquals(4, calculateLocalScreenshotPreviewSampleSize(3000, 1200))
        assertEquals(8, calculateLocalScreenshotPreviewSampleSize(8192, 3000))
    }

    @Test
    fun invalidEdgesAreRejected() {
        assertNull(
            normalizedCropToLocalScreenshotPreviewPixelRect(
                OcrNormalizedCropRect(0.5, 0.0, 0.5, 1.0),
                1600,
                720,
            ),
        )
    }

    @Test
    fun outOfBoundsCropIsRejectedSafely() {
        assertNull(
            normalizedCropToLocalScreenshotPreviewPixelRect(
                OcrNormalizedCropRect(-0.1, 0.0, 0.8, 1.0),
                1600,
                720,
            ),
        )
    }

    @Test
    fun nonFiniteCropIsRejectedSafely() {
        assertNull(
            normalizedCropToLocalScreenshotPreviewPixelRect(
                OcrNormalizedCropRect(Double.NaN, 0.0, 1.0, 1.0),
                1600,
                720,
            ),
        )
    }

    @Test
    fun aspectRatioUsesCropDimensions() {
        val crop = LocalScreenshotPreviewPixelRect(320, 72, 1280, 648)
        assertEquals(960.0 / 576.0, calculateLocalScreenshotPreviewAspectRatio(crop)!!, 0.0001)
    }
}
