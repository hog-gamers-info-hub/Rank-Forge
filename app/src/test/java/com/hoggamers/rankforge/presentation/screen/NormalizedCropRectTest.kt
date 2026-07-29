package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedCropRectTest {
    @Test
    fun acceptsFiniteInBoundsCropAtMinimumSize() {
        val result = NormalizedCropRectValidator.validate(
            NormalizedCropRect(left = 0.00, top = 0.00, right = 0.10, bottom = 0.10),
        )

        assertTrue(result is RosterScreenshotCropValidationResult.Valid)
    }

    @Test
    fun rejectsOutOfBoundsAndInvalidEdges() {
        val outOfBounds = NormalizedCropRectValidator.validate(
            NormalizedCropRect(left = -0.01, top = 0.10, right = 0.50, bottom = 0.60),
        )
        val invalidEdges = NormalizedCropRectValidator.validate(
            NormalizedCropRect(left = 0.50, top = 0.10, right = 0.50, bottom = 0.60),
        )

        assertEquals(
            NormalizedCropRectValidationError.OUT_OF_BOUNDS,
            (outOfBounds as RosterScreenshotCropValidationResult.Invalid).error,
        )
        assertEquals(
            NormalizedCropRectValidationError.INVALID_EDGES,
            (invalidEdges as RosterScreenshotCropValidationResult.Invalid).error,
        )
    }

    @Test
    fun rejectsNonFiniteAndTooSmallCrops() {
        val nonFinite = NormalizedCropRectValidator.validate(
            NormalizedCropRect(left = Double.NaN, top = 0.10, right = 0.50, bottom = 0.60),
        )
        val tooSmall = NormalizedCropRectValidator.validate(
            NormalizedCropRect(left = 0.10, top = 0.10, right = 0.19, bottom = 0.20),
        )

        assertEquals(
            NormalizedCropRectValidationError.NON_FINITE_VALUE,
            (nonFinite as RosterScreenshotCropValidationResult.Invalid).error,
        )
        assertEquals(
            NormalizedCropRectValidationError.TOO_SMALL,
            (tooSmall as RosterScreenshotCropValidationResult.Invalid).error,
        )
    }
}
