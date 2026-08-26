package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionCropTest {
    private val calculator = MatchResultPositionCropCalculator()
    private val dimensions = OcrImageDimensions(width = 1_200, height = 500)

    @Test
    fun upperScreenshotBuildsPositionsOneThroughTenFromIndependentColumnPitches() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals((1..10).toList(), result.crops.map { it.position })
        assertEquals(MatchResultPositionPitchSource.LEFT_FOUR_TO_FIVE, result.leftPitchSource)
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
        assertEquals(100.0, result.leftRowPitch!!, 0.001)
        assertEquals(80.0, result.rightRowPitch, 0.001)
        assertEquals(38, result.crops.first { it.position == 4 }.bounds.left)
        assertEquals(620, result.crops.first { it.position == 4 }.bounds.right)
        assertEquals(638, result.crops.first { it.position == 6 }.bounds.left)
        assertEquals(1_190, result.crops.first { it.position == 6 }.bounds.right)
        assertEquals(285, result.crops.first { it.position == 4 }.bounds.top)
        assertEquals(385, result.crops.first { it.position == 4 }.bounds.bottom)
        assertEquals(0, result.crops.first { it.position == 6 }.bounds.top)
        assertEquals(75, result.crops.first { it.position == 6 }.bounds.bottom)
    }

    @Test
    fun upperNormalPolicyDoesNotIncludeDetectedPositionEleven() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
            observation("11", 650, 420, 680, 450),
        )

        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun upperFallbackIncludesPositionElevenOnlyWhenResolvedRightAnchorExists() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
            observation("11", 650, 420, 680, 450),
        )

        assertEquals((1..11).toList(), result.crops.map { it.position })
    }

    @Test
    fun upperFallbackDoesNotExtrapolatePositionElevenWithoutAnchorEvidence() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun upperFallbackIgnoresPositionElevenOutsideResolvedRightPlacementColumn() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
            observation("11", 50, 420, 75, 450),
        )

        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun clippedUpperPositionElevenDoesNotInvalidateCoreUpperPositions() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 80, 680, 110),
            observation("7", 650, 160, 680, 190),
            observation("11", 650, 480, 680, 500),
        )

        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun upperFallbackNeverIncludesPositionTwelve() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
            observation("11", 650, 420, 680, 450),
            observation("12", 650, 470, 680, 500),
        )

        assertEquals((1..11).toList(), result.crops.map { it.position })
        assertTrue(result.crops.none { it.position == 12 })
    }

    @Test
    fun lowerScreenshotBuildsOnlyPositionsElevenAndTwelve() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            observation("11", 646, 320, 684, 350),
            observation("12", 646, 400, 684, 430),
        )

        assertEquals(listOf(11, 12), result.crops.map { it.position })
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
        assertEquals(80.0, result.rightRowPitch, 0.001)
        assertTrue(result.crops.all { it.column == MatchResultPositionColumn.RIGHT })
    }

    @Test
    fun missingFourRecoversLeftPitchFromConsecutiveRightPositions() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals(MatchResultPositionPitchSource.RECOVERED_FROM_RIGHT, result.leftPitchSource)
        assertEquals(80.0 * 1.172, result.leftRowPitch!!, 0.001)
        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun missingFiveRecoversLeftPitchFromConsecutiveRightPositions() {
        val recoveredLeftPitch = 80.0 * 1.172
        val positionFourCenter = 435.0 - recoveredLeftPitch
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation(
                "4",
                50,
                (positionFourCenter - 15).toInt(),
                75,
                (positionFourCenter + 15).toInt(),
            ),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals(MatchResultPositionPitchSource.RECOVERED_FROM_RIGHT, result.leftPitchSource)
        assertEquals(recoveredLeftPitch, result.leftRowPitch!!, 1.0)
        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun nonConsecutiveRightAnchorsCanResolveRightPitchWhenConsecutivePairIsMissing() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("8", 650, 180, 680, 210),
        )

        assertEquals(MatchResultPositionPitchSource.RIGHT_NON_CONSECUTIVE, result.rightPitchSource)
        assertEquals(80.0, result.rightRowPitch, 0.001)
        assertEquals((6..10).toList(), result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position })
    }

    @Test
    fun singleRightAnchorUsesDirectLeftPitchAsFallback() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
        )

        assertEquals(MatchResultPositionPitchSource.RECOVERED_FROM_LEFT, result.rightPitchSource)
        assertEquals(100.0 / 1.172, result.rightRowPitch, 0.001)
        assertEquals((1..10).toList(), result.crops.map { it.position })
    }

    @Test
    fun oneLeftAnchorAndOneRightAnchorFailsClosedBecauseNeitherPitchCanBeEstablished() {
        val result = calculator.calculate(
            evidence(
                observation("5", 50, 420, 75, 450),
                observation("6", 650, 20, 680, 50),
            ),
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertEquals(
            MatchResultPositionCropCalculationResult.Unavailable(
                MatchResultPositionCropUnavailableReason.RIGHT_ROW_PITCH_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun lowerScreenshotCanInferMissingTwelveFromLeftPitchAndDetectedEleven() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("11", 646, 330, 684, 360),
        )

        assertEquals(listOf(11, 12), result.crops.map { it.position })
        assertEquals(MatchResultPositionPitchSource.RECOVERED_FROM_LEFT, result.rightPitchSource)
    }

    @Test
    fun leftWidthEndsAtSecondLeftEliminationColumnAndRightWidthEndsAtRightmostElimination() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 52, 320, 76, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 648, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        val left = result.crops.first { it.position == 5 }.bounds
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(38, left.left)
        assertEquals(620, left.right)
        assertEquals(636, right.left)
        assertEquals(1_190, right.right)
    }

    @Test
    fun placementLeftPaddingClampsAtTheImageEdge() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 4, 320, 29, 350),
            observation("5", 5, 420, 30, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals(0, result.crops.first { it.position == 4 }.bounds.left)
        assertEquals(0, result.crops.first { it.position == 5 }.bounds.left)
    }

    private fun available(
        role: MatchResultScreenshotRole,
        vararg observations: MatchResultAutoCropObservation,
    ): MatchResultPositionCropCalculationResult.Available {
        val result = calculator.calculate(evidence(*observations), role)
        assertTrue("Expected available geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        return result as MatchResultPositionCropCalculationResult.Available
    }

    private fun availableUpperFallback(
        vararg observations: MatchResultAutoCropObservation,
    ): MatchResultPositionCropCalculationResult.Available {
        val result = calculator.calculate(
            evidence = evidence(*observations),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            allowUpperPositionElevenFallback = true,
        )
        assertTrue("Expected available geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        return result as MatchResultPositionCropCalculationResult.Available
    }

    private fun evidence(
        vararg observations: MatchResultAutoCropObservation,
    ): MatchResultAutoCropEvidence = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + observations,
        imageDimensions = dimensions,
    )

    private fun eliminationObservations(): List<MatchResultAutoCropObservation> = listOf(
        observation("Eliminations", 250, 40, 340, 70),
        observation("Eliminations", 252, 220, 342, 250),
        observation("Eliminations", 520, 40, 620, 70),
        observation("Eliminations", 522, 220, 618, 250),
        observation("Eliminations", 820, 40, 930, 70),
        observation("Eliminations", 822, 220, 928, 250),
        observation("Eliminations", 1_070, 40, 1_190, 70),
        observation("Eliminations", 1_072, 220, 1_188, 250),
    )

    private fun observation(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): MatchResultAutoCropObservation = MatchResultAutoCropObservation(
        text = text,
        boundingBox = RawOcrBoundingBox(left, top, right, bottom),
    )
}
