package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrVisualCropGeometryTest {
    @Test
    fun move_updatesNormalizedCrop() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val moved = OcrVisualCropGeometry.move(
            crop = crop,
            normalizedDeltaX = 0.10,
            normalizedDeltaY = -0.05,
        )

        assertCrop(moved, left = 0.30, top = 0.20, right = 0.80, bottom = 0.70)
    }

    @Test
    fun move_clampsToPreviewBounds() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val moved = OcrVisualCropGeometry.move(
            crop = crop,
            normalizedDeltaX = 0.80,
            normalizedDeltaY = -0.80,
        )

        assertCrop(moved, left = 0.50, top = 0.00, right = 1.00, bottom = 0.50)
    }

    @Test
    fun resizeTop_changesOnlyTop() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.TOP,
            normalizedDeltaX = 0.10,
            normalizedDeltaY = -0.05,
            profile = OcrCropValidationProfiles.Roster,
        )

        assertCrop(resized, left = 0.20, top = 0.20, right = 0.70, bottom = 0.75)
    }

    @Test
    fun resizeTop_clampsAndPreservesMinimumHeight() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.50,
            right = 0.70,
            bottom = 0.60,
        )

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.TOP,
            normalizedDeltaX = 0.90,
            normalizedDeltaY = 0.90,
            profile = OcrCropValidationProfiles.Roster,
        )

        assertCrop(resized, left = 0.20, top = 0.50, right = 0.70, bottom = 0.60)
    }

    @Test
    fun resizeBottom_changesOnlyBottom() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.BOTTOM,
            normalizedDeltaX = 0.10,
            normalizedDeltaY = 0.05,
            profile = OcrCropValidationProfiles.Roster,
        )

        assertCrop(resized, left = 0.20, top = 0.25, right = 0.70, bottom = 0.80)
    }

    @Test
    fun resizeLeft_changesOnlyLeft() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.LEFT,
            normalizedDeltaX = -0.10,
            normalizedDeltaY = 0.05,
            profile = OcrCropValidationProfiles.Roster,
        )

        assertCrop(resized, left = 0.10, top = 0.25, right = 0.70, bottom = 0.75)
    }

    @Test
    fun resizeLeft_clampsAndPreservesMinimumWidth() {
        val crop = OcrNormalizedCropRect(
            left = 0.50,
            top = 0.20,
            right = 0.60,
            bottom = 0.70,
        )

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.LEFT,
            normalizedDeltaX = 0.90,
            normalizedDeltaY = 0.90,
            profile = OcrCropValidationProfiles.Roster,
        )

        assertCrop(resized, left = 0.50, top = 0.20, right = 0.60, bottom = 0.70)
    }

    @Test
    fun resizeRight_changesOnlyRight() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.RIGHT,
            normalizedDeltaX = 0.10,
            normalizedDeltaY = 0.05,
            profile = OcrCropValidationProfiles.Roster,
        )

        assertCrop(resized, left = 0.20, top = 0.25, right = 0.80, bottom = 0.75)
    }

    @Test
    fun move_preservesCropSize() {
        val crop = OcrNormalizedCropRect(
            left = 0.20,
            top = 0.25,
            right = 0.70,
            bottom = 0.75,
        )

        val moved = OcrVisualCropGeometry.move(
            crop = crop,
            normalizedDeltaX = 0.10,
            normalizedDeltaY = 0.05,
        )

        assertEquals(crop.normalizedWidth, moved.normalizedWidth, 0.000_001)
        assertEquals(crop.normalizedHeight, moved.normalizedHeight, 0.000_001)
    }

    @Test
    fun visualCropPixelSizeUsesSharedPixelCropContract() {
        val crop = OcrNormalizedCropRect(
            left = 0.10,
            top = 0.15,
            right = 0.90,
            bottom = 0.85,
        )
        val dimensions = OcrImageDimensions(width = 1600, height = 720)
        val expectedPixelCrop = crop.toPixelRectOrNull(dimensions)!!

        val pixelSize = calculateVisualCropPixelSize(
            crop = crop,
            sourceWidth = dimensions.width,
            sourceHeight = dimensions.height,
        )

        assertEquals(expectedPixelCrop.width, pixelSize?.width)
        assertEquals(expectedPixelCrop.height, pixelSize?.height)
    }

    @Test
    fun visualCropPixelSizeReturnsNullForMissingOrInvalidSourceDimensions() {
        val crop = OcrVisualCropDefaults.FullImageCrop

        assertEquals(null, calculateVisualCropPixelSize(crop, sourceWidth = null, sourceHeight = 720))
        assertEquals(null, calculateVisualCropPixelSize(crop, sourceWidth = 1600, sourceHeight = null))
        assertEquals(null, calculateVisualCropPixelSize(crop, sourceWidth = 0, sourceHeight = 720))
        assertEquals(null, calculateVisualCropPixelSize(crop, sourceWidth = 1600, sourceHeight = -1))
    }

    @Test
    fun verticalResizeChangesPixelHeightAndPreservesPixelWidth() {
        val crop = OcrNormalizedCropRect(
            left = 0.10,
            top = 0.15,
            right = 0.90,
            bottom = 0.85,
        )
        val sourceWidth = 1600
        val sourceHeight = 720
        val originalSize = calculateVisualCropPixelSize(crop, sourceWidth, sourceHeight)!!

        val resized = OcrVisualCropGeometry.resize(
            crop = crop,
            handle = OcrVisualCropResizeHandle.TOP,
            normalizedDeltaX = 0.20,
            normalizedDeltaY = 0.10,
            profile = OcrCropValidationProfiles.Roster,
        )
        val resizedSize = calculateVisualCropPixelSize(resized, sourceWidth, sourceHeight)!!

        assertEquals(originalSize.width, resizedSize.width)
        assertEquals(432, resizedSize.height)
    }

    private fun assertCrop(
        actual: OcrNormalizedCropRect,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ) {
        assertEquals(left, actual.left, 0.000_001)
        assertEquals(top, actual.top, 0.000_001)
        assertEquals(right, actual.right, 0.000_001)
        assertEquals(bottom, actual.bottom, 0.000_001)
    }
}
