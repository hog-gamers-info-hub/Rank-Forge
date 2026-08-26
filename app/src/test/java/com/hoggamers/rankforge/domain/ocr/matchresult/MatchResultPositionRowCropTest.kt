package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionRowCropTest {
    private val calculator = MatchResultPositionRowCropCalculator()

    @Test
    fun oneClusterProducesExactlyOneFullWidthRow() {
        val result = available(
            observation("Player A", 100, 40, 390, 60),
            observation("2 Eliminations", 20, 42, 95, 58),
        )

        assertEquals(listOf(1), result.crops.map { it.rowIndex })
        assertEquals(0, result.crops.single().bounds.left)
        assertEquals(400, result.crops.single().bounds.right)
    }

    @Test
    fun twoClustersProduceExactlyTwoRows() {
        val result = available(
            observation("Upper player", 90, 10, 390, 25),
            observation("Lower player", 90, 65, 390, 80),
        )

        assertEquals(listOf(1, 2), result.crops.map { it.rowIndex })
    }

    @Test
    fun centeredSinglePlayerIsOneRowNotTwo() {
        val result = available(
            observation("Centered player", 80, 42, 390, 58),
        )

        assertEquals(1, result.crops.size)
    }

    @Test
    fun placementNumberAloneDoesNotCreateAFalseRow() {
        val result = calculator.calculate(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = listOf(observation("7", 0, 42, 30, 58)),
        )

        assertEquals(MatchResultPositionRowCropCalculationResult.Unavailable, result)
    }

    @Test
    fun overlappingWideContentRemainsOneFullWidthHorizontalRow() {
        val result = available(
            observation("Player A 2 Eliminations Player B", 10, 35, 395, 60),
        )

        assertEquals(0, result.crops.single().bounds.left)
        assertEquals(400, result.crops.single().bounds.right)
    }

    @Test
    fun verticalPaddingIsAppliedAndClamped() {
        val result = available(
            observation("Bottom player", 90, 92, 390, 100),
        )

        assertEquals(90, result.crops.single().bounds.top)
        assertEquals(100, result.crops.single().bounds.bottom)
    }

    @Test
    fun edgeOvershootIsClampedToSourceDimensions() {
        val result = available(
            observation("Bottom player", 90, 92, 405, 105),
        )

        assertEquals(0, result.crops.single().bounds.left)
        assertEquals(400, result.crops.single().bounds.right)
        assertEquals(100, result.crops.single().bounds.bottom)
    }

    @Test
    fun completelyOutsideObservationIsRejected() {
        val result = calculator.calculate(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = listOf(observation("outside", 450, 120, 500, 150)),
        )

        assertTrue(result is MatchResultPositionRowCropCalculationResult.Unavailable)
    }

    @Test
    fun inBoundsObservationRetainsItsRowGeometry() {
        val result = available(
            observation("Player", 90, 40, 390, 60),
        )

        assertEquals(38, result.crops.single().bounds.top)
        assertEquals(62, result.crops.single().bounds.bottom)
    }

    @Test
    fun tinySecondClusterIsIgnoredWithoutInvalidatingFirstRow() {
        val result = available(
            observation("First player", 90, 10, 390, 25),
            observation("noise", 90, 80, 390, 80),
        )

        assertEquals(listOf(1), result.crops.map { it.rowIndex })
    }

    @Test
    fun noValidRowsReturnsUnavailable() {
        val result = calculator.calculate(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = emptyList(),
        )

        assertTrue(result is MatchResultPositionRowCropCalculationResult.Unavailable)
    }

    @Test
    fun positionSevenPhysicalGeometrySplitsAtLargestAnchoredCenterGap() {
        val result = calculateRows(
            position = 7,
            imageWidth = 491,
            imageHeight = 82,
            placement = observation("7", 0, 36, 30, 53),
            observations = listOf(
                observation("upper 1", 80, 14, 240, 33),
                observation("upper 2", 250, 16, 400, 35),
                observation("upper 3", 100, 19, 230, 38),
                observation("lower 1", 80, 50, 240, 69),
                observation("lower 2", 250, 50, 400, 69),
                observation("lower 3", 100, 51, 230, 71),
            ),
        )

        assertEquals(2, result.crops.size)
    }

    @Test
    fun positionNineTallLowerBoxIsAssignedByCenterNotEdges() {
        val result = calculateRows(
            position = 9,
            imageWidth = 491,
            imageHeight = 82,
            placement = observation("9", 0, 34, 30, 50),
            observations = listOf(
                observation("upper 1", 80, 15, 240, 30),
                observation("upper 2", 250, 16, 400, 32),
                observation("upper 3", 100, 17, 230, 33),
                observation("lower tall", 80, 37, 240, 71),
                observation("lower 2", 250, 49, 400, 66),
            ),
        )

        assertEquals(2, result.crops.size)
    }

    @Test
    fun centeredSinglePlayerAroundPlacementCenterRemainsOneRow() {
        val result = calculateRows(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            placement = observation("7", 0, 34, 30, 50),
            observations = listOf(
                observation("center 1", 80, 33, 180, 45),
                observation("center 2", 190, 34, 290, 47),
                observation("center 3", 300, 36, 390, 48),
                observation("center 4", 100, 36, 200, 50),
            ),
        )

        assertEquals(1, result.crops.size)
    }

    @Test
    fun singleCandidateProducesOneRow() {
        val result = calculateRows(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = listOf(observation("single", 100, 40, 300, 58)),
        )

        assertEquals(1, result.crops.size)
    }

    @Test
    fun cleanTwoRowsSplitByCenterGap() {
        val result = calculateRows(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            placement = observation("7", 0, 42, 30, 58),
            observations = listOf(
                observation("upper 1", 80, 14, 180, 26),
                observation("upper 2", 190, 16, 290, 28),
                observation("lower 1", 80, 64, 180, 76),
                observation("lower 2", 190, 66, 290, 78),
            ),
        )

        assertEquals(2, result.crops.size)
    }

    @Test
    fun missingPlacementAnchorUsesCenterGapFallback() {
        val result = calculateRows(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = listOf(
                observation("upper 1", 80, 18, 180, 30),
                observation("upper 2", 190, 21, 290, 33),
                observation("lower 1", 80, 54, 180, 66),
                observation("lower 2", 190, 56, 290, 68),
            ),
        )

        assertEquals(2, result.crops.size)
    }

    @Test
    fun missingPlacementAnchorWithCompactCentersRemainsOneRow() {
        val result = calculateRows(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = listOf(
                observation("compact 1", 80, 32, 180, 44),
                observation("compact 2", 190, 34, 290, 46),
                observation("compact 3", 300, 36, 390, 48),
            ),
        )

        assertEquals(1, result.crops.size)
    }

    @Test
    fun noisyGapNotCrossingPlacementAnchorDoesNotForceSplit() {
        val result = calculateRows(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            placement = observation("7", 0, 42, 30, 58),
            observations = listOf(
                observation("upper 1", 80, 4, 180, 16),
                observation("upper 2", 190, 24, 290, 36),
                observation("upper 3", 300, 43, 390, 55),
                observation("lower", 100, 46, 200, 58),
            ),
        )

        assertEquals(1, result.crops.size)
    }

    private fun available(
        vararg observations: MatchResultPositionRowCropObservation,
    ): MatchResultPositionRowCropCalculationResult.Available {
        val result = calculator.calculate(
            position = 7,
            imageWidth = 400,
            imageHeight = 100,
            observations = observations.toList(),
        )
        assertTrue("Expected available rows, got $result", result is MatchResultPositionRowCropCalculationResult.Available)
        return result as MatchResultPositionRowCropCalculationResult.Available
    }

    private fun calculateRows(
        position: Int,
        imageWidth: Int,
        imageHeight: Int,
        placement: MatchResultPositionRowCropObservation? = null,
        observations: List<MatchResultPositionRowCropObservation>,
    ): MatchResultPositionRowCropCalculationResult.Available {
        val result = calculator.calculate(
            position = position,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            observations = listOfNotNull(placement) + observations,
        )
        assertTrue("Expected available rows, got $result", result is MatchResultPositionRowCropCalculationResult.Available)
        return result as MatchResultPositionRowCropCalculationResult.Available
    }

    private fun observation(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = MatchResultPositionRowCropObservation(
        text = text,
        boundingBox = RawOcrBoundingBox(left, top, right, bottom),
    )
}
