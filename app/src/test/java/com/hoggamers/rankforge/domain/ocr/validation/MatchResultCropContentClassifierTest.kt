package com.hoggamers.rankforge.domain.ocr.validation

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultCropContentClassifierTest {
    private val classifier = MatchResultCropContentClassifier()

    @Test
    fun upperKnownValidCombinationPasses() {
        assertValid(upperEvidence())
    }

    @Test
    fun upperAspectBoundariesAreInclusive() {
        assertValid(upperEvidence(cropWidth = 230, cropHeight = 100))
        assertValid(upperEvidence(cropWidth = 260, cropHeight = 100))
        assertInvalid(upperEvidence(cropWidth = 229, cropHeight = 100))
        assertInvalid(upperEvidence(cropWidth = 261, cropHeight = 100))
    }

    @Test
    fun upperRequiresAtLeastFourAlignedPlacements() {
        assertValid(upperEvidence(alignedPlacements = 4))
        assertInvalid(upperEvidence(alignedPlacements = 3))
    }

    @Test
    fun upperRequiresAtLeastThirtyOccupiedPlayerRegions() {
        assertValid(upperEvidence(occupiedPlayerFields = 30))
        assertInvalid(upperEvidence(occupiedPlayerFields = 29))
    }

    @Test
    fun upperRequiresEverySpatialBandAndIgnoresKillEvidence() {
        assertValid(upperEvidence(killFields = 0))
        assertInvalid(upperEvidence(horizontalBands = listOf(1, 1, 1, 0)))
        assertInvalid(upperEvidence(verticalBands = listOf(1, 1, 0, 1)))
    }

    @Test
    fun lowerKnownValidCombinationPasses() {
        assertValid(lowerEvidence())
    }

    @Test
    fun lowerAspectBoundariesAreInclusive() {
        assertValid(lowerEvidence(cropWidth = 230, cropHeight = 100))
        assertValid(lowerEvidence(cropWidth = 285, cropHeight = 100))
        assertInvalid(lowerEvidence(cropWidth = 229, cropHeight = 100))
        assertInvalid(lowerEvidence(cropWidth = 286, cropHeight = 100))
    }

    @Test
    fun lowerRequiresPlacementsElevenAndTwelveAtOrBelowTheDistanceThreshold() {
        assertValid(lowerEvidence(distance = 0.05))
        assertInvalid(lowerEvidence(distance = 0.0500001))
        assertInvalid(lowerEvidence(includeTwelve = false))
        assertInvalid(lowerEvidence(includeEleven = false))
    }

    @Test
    fun lowerRequiresAtLeastSevenOccupiedPlayerRegionsAndIgnoresKillEvidence() {
        assertValid(lowerEvidence(occupiedPlayerFields = 7, killFields = 0))
        assertInvalid(lowerEvidence(occupiedPlayerFields = 6))
    }

    private fun assertValid(evidence: MatchResultCropContentEvidence) {
        assertEquals(OcrCropContentValidationResult.Valid, classifier.classify(evidence))
    }

    private fun assertInvalid(evidence: MatchResultCropContentEvidence) {
        assertTrue(classifier.classify(evidence) is OcrCropContentValidationResult.Invalid)
    }

    private fun upperEvidence(
        cropWidth: Int = 250,
        cropHeight: Int = 100,
        alignedPlacements: Int = 10,
        occupiedPlayerFields: Int = 40,
        horizontalBands: List<Int> = listOf(1, 1, 1, 1),
        verticalBands: List<Int> = listOf(1, 1, 1, 1),
        killFields: Int = 40,
    ) = evidence(
        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        cropWidth = cropWidth,
        cropHeight = cropHeight,
        placements = (1..10).map { placement ->
            placementEvidence(
                expectedPlacement = placement,
                aligned = placement <= alignedPlacements,
            )
        },
        playerFieldCount = 40,
        occupiedPlayerFields = occupiedPlayerFields,
        killFields = killFields,
        horizontalBands = horizontalBands,
        verticalBands = verticalBands,
    )

    private fun lowerEvidence(
        cropWidth: Int = 250,
        cropHeight: Int = 100,
        distance: Double = 0.0,
        occupiedPlayerFields: Int = 8,
        killFields: Int = 8,
        includeEleven: Boolean = true,
        includeTwelve: Boolean = true,
    ) = evidence(
        role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        cropWidth = cropWidth,
        cropHeight = cropHeight,
        placements = listOfNotNull(
            if (includeEleven) placementEvidence(11, distance) else null,
            if (includeTwelve) placementEvidence(12, distance) else null,
        ),
        playerFieldCount = 8,
        occupiedPlayerFields = occupiedPlayerFields,
        killFields = killFields,
        horizontalBands = listOf(1, 1, 1, 1),
        verticalBands = listOf(1, 1, 1, 1),
    )

    private fun evidence(
        role: MatchResultScreenshotRole,
        cropWidth: Int,
        cropHeight: Int,
        placements: List<MatchResultCropPlacementEvidence>,
        playerFieldCount: Int,
        occupiedPlayerFields: Int,
        killFields: Int,
        horizontalBands: List<Int>,
        verticalBands: List<Int>,
    ) = MatchResultCropContentEvidence(
        role = role,
        cropWidth = cropWidth,
        cropHeight = cropHeight,
        nonBlankObservationCount = 1,
        playerLikeObservationCount = occupiedPlayerFields,
        killLikeObservationCount = killFields,
        placementEvidence = placements,
        playerFieldEvidence = (0 until playerFieldCount).map { index ->
            MatchResultCropFieldEvidence(
                fieldId = "PLAYER_$index",
                fieldType = MatchResultOcrFieldType.PLAYER,
                maximumExpectedRegionCoverageRatio = if (index < occupiedPlayerFields) 1.0 else 0.0,
                maximumObservationContainmentRatio = 1.0,
                minimumNormalizedCenterDistance = 0.0,
            )
        },
        killFieldEvidence = (0 until killFields).map { index ->
            MatchResultCropFieldEvidence(
                fieldId = "KILL_$index",
                fieldType = MatchResultOcrFieldType.KILL,
                maximumExpectedRegionCoverageRatio = 1.0,
                maximumObservationContainmentRatio = 1.0,
                minimumNormalizedCenterDistance = 0.0,
            )
        },
        spatialDistribution = MatchResultCropSpatialDistribution(horizontalBands, verticalBands),
    )

    private fun placementEvidence(
        expectedPlacement: Int,
        aligned: Boolean = true,
    ) = placementEvidence(expectedPlacement, if (aligned) 0.0 else 0.0500001)

    private fun placementEvidence(
        expectedPlacement: Int,
        distance: Double,
    ) = MatchResultCropPlacementEvidence(
        expectedPlacement = expectedPlacement,
        matchingCandidateCount = 1,
        minimumNormalizedCenterDistance = distance,
    )
}
