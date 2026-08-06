package com.hoggamers.rankforge.domain.ocr.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCropContractTest {
    @Test
    fun validNormalizedRectangleIsAccepted() {
        val result = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.10, top = 0.20, right = 0.90, bottom = 0.80),
            profile = OcrCropValidationProfiles.Roster,
        )

        assertTrue(result is OcrCropValidationResult.Valid)
    }

    @Test
    fun invalidNaNAndInfiniteValuesAreRejected() {
        val nan = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = Double.NaN, top = 0.20, right = 0.90, bottom = 0.80),
            profile = OcrCropValidationProfiles.Roster,
        )
        val infinite = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.10, top = Double.POSITIVE_INFINITY, right = 0.90, bottom = 0.80),
            profile = OcrCropValidationProfiles.Roster,
        )

        assertEquals(
            OcrCropValidationError.NON_FINITE_VALUE,
            (nan as OcrCropValidationResult.Invalid).error,
        )
        assertEquals(
            OcrCropValidationError.NON_FINITE_VALUE,
            (infinite as OcrCropValidationResult.Invalid).error,
        )
    }

    @Test
    fun outOfBoundsValuesAreRejected() {
        val result = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = -0.01, top = 0.20, right = 0.90, bottom = 0.80),
            profile = OcrCropValidationProfiles.Roster,
        )

        assertEquals(
            OcrCropValidationError.OUT_OF_BOUNDS,
            (result as OcrCropValidationResult.Invalid).error,
        )
    }

    @Test
    fun zeroAndNegativeCropAreasAreRejected() {
        val zeroWidth = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.50, top = 0.20, right = 0.50, bottom = 0.80),
            profile = OcrCropValidationProfiles.Roster,
        )
        val negativeHeight = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.10, top = 0.80, right = 0.90, bottom = 0.20),
            profile = OcrCropValidationProfiles.Roster,
        )

        assertEquals(
            OcrCropValidationError.INVALID_EDGES,
            (zeroWidth as OcrCropValidationResult.Invalid).error,
        )
        assertEquals(
            OcrCropValidationError.INVALID_EDGES,
            (negativeHeight as OcrCropValidationResult.Invalid).error,
        )
    }

    @Test
    fun minimumNormalizedWidthAndHeightAreEnforced() {
        val tooNarrow = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.10, top = 0.10, right = 0.19, bottom = 0.50),
            profile = OcrCropValidationProfiles.Roster,
        )
        val tooShort = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.10, top = 0.10, right = 0.50, bottom = 0.19),
            profile = OcrCropValidationProfiles.Roster,
        )
        val atMinimum = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.10, top = 0.10, right = 0.20, bottom = 0.20),
            profile = OcrCropValidationProfiles.Roster,
        )

        assertEquals(
            OcrCropValidationError.TOO_SMALL,
            (tooNarrow as OcrCropValidationResult.Invalid).error,
        )
        assertEquals(
            OcrCropValidationError.TOO_SMALL,
            (tooShort as OcrCropValidationResult.Invalid).error,
        )
        assertTrue(atMinimum is OcrCropValidationResult.Valid)
    }

    @Test
    fun normalizedToPixelConversionUsesFloorLeftTopAndCeilRightBottom() {
        val dimensions = OcrImageDimensions(width = 100, height = 80)
        val crop = OcrNormalizedCropRect(left = 0.101, top = 0.126, right = 0.899, bottom = 0.874)

        val pixelCrop = crop.toPixelRectOrNull(dimensions)

        assertEquals(OcrPixelCropRect(left = 10, top = 10, right = 90, bottom = 70), pixelCrop)
    }

    @Test
    fun edgeRoundingClampsToOriginalImageBounds() {
        val dimensions = OcrImageDimensions(width = 10, height = 10)
        val crop = OcrNormalizedCropRect(left = 0.0, top = 0.0, right = 1.0, bottom = 1.0)

        val result = OcrCropValidator.validate(
            crop = crop,
            dimensions = dimensions,
            profile = OcrCropValidationProfiles.MatchResult,
        )

        assertEquals(
            OcrPixelCropRect(left = 0, top = 0, right = 10, bottom = 10),
            (result as OcrCropValidationResult.Valid).pixelCrop,
        )
    }

    @Test
    fun invalidImageDimensionsAreControlledValidationFailure() {
        val result = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.0, top = 0.0, right = 1.0, bottom = 1.0),
            dimensions = OcrImageDimensions.from(width = 0, height = 100),
            profile = OcrCropValidationProfiles.MatchResult,
        )

        assertEquals(
            OcrCropValidationError.INVALID_IMAGE_DIMENSIONS,
            (result as OcrCropValidationResult.Invalid).error,
        )
    }

    @Test
    fun candidateLocalCoordinatesMapBackToOriginalCoordinates() {
        val mapper = OcrCandidateToOriginalCoordinateMapper(
            cropInOriginal = OcrPixelCropRect.fromLeftTopWidthHeight(
                left = 208,
                top = 158,
                width = 1168,
                height = 468,
            ),
        )

        val point = mapper.mapPointToOriginal(OcrCropPoint(x = 10, y = 20))
        val rect = mapper.mapRectToOriginal(
            OcrPixelCropRect.fromLeftTopWidthHeight(left = 10, top = 20, width = 100, height = 60),
        )

        assertEquals(OcrCropPoint(x = 218, y = 178), point)
        assertEquals(OcrPixelCropRect(left = 218, top = 178, right = 318, bottom = 238), rect)
    }

    @Test
    fun scaledCandidateLocalCoordinatesMapBackToOriginalCoordinates() {
        val mapper = OcrCandidateToOriginalCoordinateMapper(
            cropInOriginal = OcrPixelCropRect.fromLeftTopWidthHeight(
                left = 208,
                top = 158,
                width = 1168,
                height = 468,
            ),
            candidateScaleFactor = 2.0,
        )

        val rect = mapper.mapRectToOriginal(
            OcrPixelCropRect.fromLeftTopWidthHeight(left = 21, top = 41, width = 39, height = 59),
        )

        assertEquals(OcrPixelCropRect(left = 218, top = 178, right = 238, bottom = 208), rect)
    }

    @Test
    fun rosterProfilePreservesExistingMinimumCropBehavior() {
        assertEquals("roster", OcrCropValidationProfiles.Roster.id)
        assertEquals(0.10, OcrCropValidationProfiles.Roster.minimumNormalizedWidth, 0.0)
        assertEquals(0.10, OcrCropValidationProfiles.Roster.minimumNormalizedHeight, 0.0)

        val result = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.0, top = 0.0, right = 0.10, bottom = 0.10),
            profile = OcrCropValidationProfiles.Roster,
        )

        assertTrue(result is OcrCropValidationResult.Valid)
    }

    @Test
    fun matchProfileProvidesBasicValidationForFutureMatchResultCrops() {
        val valid = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.13, top = 0.22, right = 0.86, bottom = 0.87),
            profile = OcrCropValidationProfiles.MatchResult,
        )
        val invalid = OcrCropValidator.validate(
            crop = OcrNormalizedCropRect(left = 0.13, top = 0.22, right = 0.14, bottom = 0.87),
            profile = OcrCropValidationProfiles.MatchResult,
        )

        assertTrue(valid is OcrCropValidationResult.Valid)
        assertEquals(
            OcrCropValidationError.TOO_SMALL,
            (invalid as OcrCropValidationResult.Invalid).error,
        )
    }

    @Test
    fun knownEvidenceExampleNormalizesAndMapsDeterministically() {
        val dimensions = OcrImageDimensions(width = 1600, height = 720)
        val originalPixelCrop = OcrPixelCropRect.fromLeftTopWidthHeight(
            left = 208,
            top = 158,
            width = 1168,
            height = 468,
        )

        val normalized = OcrNormalizedCropRect.fromPixelRect(originalPixelCrop, dimensions)
        val roundTripPixelCrop = normalized.toPixelRectOrNull(dimensions)
        val mappedRect = OcrCandidateToOriginalCoordinateMapper(originalPixelCrop).mapRectToOriginal(
            OcrPixelCropRect.fromLeftTopWidthHeight(left = 100, top = 50, width = 200, height = 80),
        )

        assertEquals(0.13, normalized.left, 0.0)
        assertEquals(158.0 / 720.0, normalized.top, 0.0)
        assertEquals(0.86, normalized.right, 0.0)
        assertEquals(626.0 / 720.0, normalized.bottom, 0.0)
        assertEquals(0.73, normalized.normalizedWidth, 0.0)
        assertEquals(0.65, normalized.normalizedHeight, 0.0)
        assertEquals(originalPixelCrop, roundTripPixelCrop)
        assertEquals(OcrPixelCropRect(left = 308, top = 208, right = 508, bottom = 288), mappedRect)
    }
}
