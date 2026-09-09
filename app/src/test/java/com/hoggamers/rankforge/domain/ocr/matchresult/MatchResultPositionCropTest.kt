package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(35.0, result.crops.first { it.position == 6 }.structuralCenterYInSource!!, 0.001)
        assertEquals(315, result.crops.first { it.position == 10 }.bounds.top)
        assertEquals(407, result.crops.first { it.position == 10 }.bounds.bottom)
        assertEquals(355.0, result.crops.first { it.position == 10 }.structuralCenterYInSource!!, 0.001)
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
    fun upperFallbackIncludesDerivedPositionElevenWhenAnchorExists() {
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
    fun upperFallbackExtrapolatesPositionElevenWithoutAnchorEvidence() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals((1..11).toList(), result.crops.map { it.position })
    }

    @Test
    fun upperFallbackDerivesPositionElevenDespiteOutsideAnchor() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
            observation("11", 50, 420, 75, 450),
        )

        assertEquals((1..11).toList(), result.crops.map { it.position })
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
    fun upperFallbackOmitsPositionTwelveWhenDerivedCenterIsOutsideImage() {
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
    fun oneOutOfBoundsRightRectangleDoesNotDiscardOtherResolvedRightPositions() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 175, 680, 205),
            observation("7", 650, 275, 680, 305),
            observation("8", 650, 375, 680, 405),
            observation("9", 650, 475, 680, 505),
        )

        assertEquals(
            listOf(6, 7, 8, 9),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
    }

    @Test
    fun rightPositionSixAboveImageIsOmittedWithoutInvokingFallbackThree() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("7", 650, 20, 680, 50),
            observation("8", 650, 84, 680, 114),
            observation("9", 650, 148, 680, 178),
            observation("10", 650, 212, 680, 242),
        )

        assertEquals(
            listOf(7, 8, 9, 10, 11, 12),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
    }

    @Test
    fun noLowerScreenshotDerivesPositionsSixThroughTwelveFromOneRightPitch() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("7", 650, 85, 680, 115),
            observation("8", 650, 149, 680, 179),
            observation("9", 650, 213, 680, 243),
            observation("10", 650, 277, 680, 307),
        )

        val rightCrops = result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }
        assertEquals((6..12).toList(), rightCrops.map { it.position })
        assertEquals(
            listOf(36.0, 100.0, 164.0, 228.0, 292.0, 356.0, 420.0),
            rightCrops.map { it.structuralCenterYInSource },
        )
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
    }

    @Test
    fun missingPositionElevenIsDerivedWhenItsCenterIsInsideTheUpperImage() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("7", 650, 20, 680, 50),
            observation("8", 650, 84, 680, 114),
            observation("9", 650, 148, 680, 178),
            observation("10", 650, 212, 680, 242),
        )

        val positionEleven = result.crops.single { it.position == 11 }
        assertEquals(MatchResultPositionColumn.RIGHT, positionEleven.column)
        assertEquals(291.0, requireNotNull(positionEleven.structuralCenterYInSource), 0.001)
    }

    @Test
    fun positionTwelveIsDerivedWhenItsCenterIsInsideTheUpperImage() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("7", 650, 85, 680, 115),
            observation("8", 650, 149, 680, 179),
            observation("9", 650, 213, 680, 243),
            observation("10", 650, 277, 680, 307),
        )

        assertEquals(12, result.crops.single { it.position == 12 }.position)
    }

    @Test
    fun positionTwelveOutsideTheUpperImageIsOmittedWithoutInvalidatingPositionsSixThroughEleven() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("7", 650, 115, 680, 145),
            observation("8", 650, 195, 680, 225),
            observation("9", 650, 275, 680, 305),
            observation("10", 650, 355, 680, 385),
        )

        assertEquals(
            (6..11).toList(),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
    }

    @Test
    fun multipleOutOfImageBoundaryPositionsAreOmittedIndependently() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("7", 650, 20, 680, 50),
            observation("8", 650, 120, 680, 150),
            observation("9", 650, 220, 680, 250),
            observation("10", 650, 320, 680, 350),
        )

        assertEquals(
            (7..11).toList(),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
    }

    @Test
    fun rightPositionElevenBelowImageIsOmittedWithoutInvalidatingPositionsSixThroughTen() {
        val result = availableUpperFallback(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 185, 680, 215),
            observation("7", 650, 249, 680, 279),
            observation("8", 650, 313, 680, 343),
            observation("9", 650, 377, 680, 407),
            observation("10", 650, 441, 680, 471),
            observation("11", 650, 470, 680, 500),
        )

        assertEquals(
            listOf(6, 7, 8, 9, 10),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
    }

    @Test
    fun oneOutOfBoundsLeftRectangleDoesNotDiscardOtherResolvedLeftPositions() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 235, 75, 265),
            observation("5", 50, 335, 75, 365),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertEquals(
            listOf(2, 3, 4, 5),
            result.crops.filter { it.column == MatchResultPositionColumn.LEFT }.map { it.position },
        )
        assertEquals(MatchResultPositionPitchSource.LEFT_FOUR_TO_FIVE, result.leftPitchSource)
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

    @Test
    fun fallbackThreeUsesC3ForVerticalGeometryAndC4ForRightBoundary() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(includeFourthColumn = true),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals((1..10).toList(), result.crops.map { it.position })
        assertEquals(
            MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY,
            result.leftPitchSource,
        )
        assertEquals(
            MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY,
            result.rightPitchSource,
        )
        assertEquals(100.0, result.leftRowPitch!!, 0.001)
        assertEquals(100.0, result.rightRowPitch, 0.001)

        val left = result.crops.first { it.position == 1 }.bounds
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(20, left.left)
        assertNotEquals(36, left.left)
        assertEquals(600, left.right)
        assertEquals(611, right.left)
        assertEquals(1_160, right.right)
        assertEquals(54, left.top)
        assertEquals(146, left.bottom)
        assertEquals(54, right.top)
        assertEquals(146, right.bottom)
    }

    @Test
    fun fallbackThreeLeftOuterEdgeUsesThreeTimesC1WidthAndClampsAtImageEdge() {
        val result = availableResult(
            evidence = fallbackThreeEvidence(includeFourthColumn = true),
        )
        val left = result.crops.first { it.position == 1 }.bounds
        assertEquals(20, left.left)
        assertNotEquals(36, left.left)

        val clamped = availableResult(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                firstColumnLeft = 5,
            ),
        )
        assertEquals(0, clamped.crops.first { it.position == 1 }.bounds.left)
    }

    @Test
    fun fallbackThreeRightLeftEdgeUsesThirteenPercentOfC3WidthAndKeepsC4Right() {
        val result = availableResult(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                rightAnchorPositions = emptyList(),
                secondColumnRight = 613,
                thirdColumnRight = 971,
            ),
        )
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(633, right.left)
        assertNotEquals(624, right.left)
        assertEquals(1_160, right.right)
    }

    @Test
    fun fallbackThreeAppliesFifteenPercentVerticalPaddingWithoutMovingStructuralCenter() {
        val result = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(100 to 140, 160 to 200),
            ),
        )
        val right = result.crops.first { it.position == 6 }

        assertEquals(85, right.bounds.top)
        assertEquals(215, right.bounds.bottom)
        assertEquals(150.0, right.structuralCenterYInSource!!, 0.0)
    }

    @Test
    fun fallbackThreeVerticalPaddingClampsAtTheTopAndBottomImageEdges() {
        val topClamped = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(5 to 25, 35 to 55),
            ),
        ).crops.first { it.position == 6 }
        assertEquals(0, topClamped.bounds.top)
        assertEquals(63, topClamped.bounds.bottom)

        val bottomClamped = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(645 to 665, 675 to 695),
            ),
        ).crops.first { it.position == 6 }
        assertEquals(637, bottomClamped.bounds.top)
        assertEquals(700, bottomClamped.bounds.bottom)
    }

    @Test
    fun adjacentAnchorCropsUseStructuralMidpointsToPreventPaddedOverlap() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )
        val positionFour = result.crops.first { it.position == 4 }
        val positionFive = result.crops.first { it.position == 5 }
        val midpoint = (
            positionFour.structuralCenterYInSource!! + positionFive.structuralCenterYInSource!!
            ) / 2.0

        assertTrue(positionFour.bounds.bottom <= midpoint)
        assertTrue(positionFive.bounds.top >= midpoint)
        assertEquals(335.0, positionFour.structuralCenterYInSource, 0.0)
        assertEquals(435.0, positionFive.structuralCenterYInSource, 0.0)
    }

    @Test
    fun fallbackThreeLeftVerticalGeometrySurvivesOneMissingC2Observation() {
        val result = calculator.calculate(
            evidence = realLeftFallbackThreeEvidence(realSixTeamC2Observations()),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals((1..5).toList(), result.crops.filter { it.column == MatchResultPositionColumn.LEFT }.map { it.position })
    }

    @Test
    fun fallbackThreeLeftVerticalGeometryIsUnchangedWhenC2CountDropsFromTenToNine() {
        val complete = availableResult(
            evidence = realLeftFallbackThreeEvidence(realSixTeamC2CompleteObservations()),
        )
        val missingOneC2Observation = availableResult(
            evidence = realLeftFallbackThreeEvidence(realSixTeamC2Observations()),
        )

        assertEquals(
            complete.crops.filter { it.column == MatchResultPositionColumn.LEFT },
            missingOneC2Observation.crops.filter { it.column == MatchResultPositionColumn.LEFT },
        )
        assertEquals(complete.leftRowPitch, missingOneC2Observation.leftRowPitch)
    }

    @Test
    fun fallbackThreeRightVerticalGeometryIsUnchangedWhenC4CountDropsToTwo() {
        val complete = availableResult(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                rightAnchorPositions = emptyList(),
            ),
        )
        val sparseC4Base = fallbackThreeEvidence(
            includeFourthColumn = false,
            rightAnchorPositions = emptyList(),
        )
        val sparseC4 = availableResult(
            evidence = sparseC4Base.copy(
                observations = sparseC4Base.observations + fallbackThreeColumnObservationsAtCenters(
                    label = "Eliminations",
                    left = 1_080,
                    right = 1_160,
                    centers = listOf(100.0, 200.0),
                    heights = listOf(30, 30),
                ),
            ),
        )

        assertEquals(
            complete.crops.filter { it.column == MatchResultPositionColumn.RIGHT },
            sparseC4.crops.filter { it.column == MatchResultPositionColumn.RIGHT },
        )
        assertEquals(complete.rightRowPitch, sparseC4.rightRowPitch, 0.001)
    }

    @Test
    fun sixTeamFallbackThreeWithC1TenC2NineC3TwoAndC4TwoSurvivesLeftGeometry() {
        val result = calculator.calculate(sixTeamFallbackThreeEvidence(), MatchResultScreenshotRole.MATCH_RESULT_UPPER)

        assertTrue("Expected sparse fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(
            listOf(6),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
    }

    @Test
    fun sparseFallbackThreeFailsClosedWhenC3HasNoObservations() {
        val result = calculator.calculate(
            evidence = sparseRightFallbackThreeEvidence(emptyList()),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertEquals(
            MatchResultPositionCropCalculationResult.Unavailable(
                MatchResultPositionCropUnavailableReason.RIGHT_POSITION_ANCHOR_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun sparseFallbackThreeWithOneC3ObservationAssignsOnlyPositionSixWithoutPitch() {
        val result = availableResult(
            evidence = sparseRightFallbackThreeEvidence(listOf(100 to 120)),
        )

        assertEquals(
            listOf(6),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(20.0, result.rightRowPitch, 0.001)
    }

    @Test
    fun sparseFallbackThreeWithTwoCloseC3ObservationsPairsAtPositionSixWithoutPitch() {
        val result = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(16 to 36, 48 to 68),
            ),
        )

        assertEquals(
            listOf(6),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(20.0, result.rightRowPitch, 0.001)
    }

    @Test
    fun sparseFallbackThreeUsesTheExactNormalizedGapThresholdForPairing() {
        val atThreshold = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(100 to 120, 138 to 158),
            ),
        )
        val aboveThreshold = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(100 to 120, 139 to 159),
            ),
        )

        assertEquals(
            listOf(6),
            atThreshold.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(
            listOf(6, 7),
            aboveThreshold.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
    }

    @Test
    fun sparseFallbackThreeWithTwoFarC3ObservationsUsesValidatedCenterPitch() {
        val result = availableResult(
            evidence = sparseRightFallbackThreeEvidence(
                listOf(100 to 120, 200 to 220),
            ),
        )

        assertEquals(
            listOf(6, 7),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(100.0, result.rightRowPitch, 0.001)
    }

    @Test
    fun eliminationClassifierAcceptsExactCommonAndMergedForms() {
        listOf(
            "Eliminations",
            "Elimination",
            "Eliminati",
            "Eliminat",
            "1Eliminations",
            "0Eliminations",
            "EliminationATX.HADEX",
            "EliminationRRIMEPVT_!!",
            "1EliminationsTL-LEG3N4",
            "EliminatiHNGODNIKAA",
        ).forEach { label ->
            assertEliminationClassifierAccepts(label)
        }
    }

    @Test
    fun eliminationClassifierAcceptsObservedOcrCorruptionWithAtMostOnePrefixEdit() {
        listOf(
            "1El├╝iminations",
            "1E├╝iminations",
            "Eiminations",
            "Elminations",
            "Elimnations",
            "Eliminatons",
        ).forEach { label ->
            assertEliminationClassifierAccepts(label)
        }
    }

    @Test
    fun eliminationClassifierRejectsUnrelatedText() {
        listOf(
            "Player",
            "Position",
            "Eliminate",
            "TeamName",
            "Leave",
            "LegendAKY",
        ).forEach { label ->
            val result = calculator.calculate(
                evidence = fallbackThreeEvidence(
                    includeFourthColumn = true,
                    eliminationLabel = label,
                    rightAnchorPositions = emptyList(),
                ),
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            )
            assertEquals(
                MatchResultPositionCropCalculationResult.Unavailable(
                    MatchResultPositionCropUnavailableReason.ELIMINATION_GEOMETRY_UNAVAILABLE,
                ),
                result,
            )
        }
    }

    @Test
    fun fallbackThreeAcceptsTheFailingMojibakeLabelAndRecoversRightPositionPitch() {
        val centers = listOf(38.5, 90.0, 158.5, 211.5, 280.0, 331.0)
        val baseEvidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeThirdColumn = false,
            rightAnchorPositions = emptyList(),
        )
        val evidence = baseEvidence.copy(
            observations = baseEvidence.observations + fallbackThreeColumnObservationsAtCenters(
                label = "1El├╝iminations",
                left = 820,
                right = 900,
                centers = centers,
                heights = listOf(29, 30, 29, 29, 30, 30),
            ),
        )

        val result = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(
            MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY,
            result.rightPitchSource,
        )
        assertEquals(120.5, result.rightRowPitch, 0.001)
        assertEquals(
            (6..8).toList(),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertTrue(result.crops.any { it.position == 6 })
        assertTrue(result.crops.any { it.position == 7 })
        assertTrue(result.crops.any { it.position == 8 })
    }

    @Test
    fun fallbackThreeDoesNotUseWideMergedEliminationBoxForWidth() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                wideMergedFirstColumnBox = true,
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(20, result.crops.first { it.position == 1 }.bounds.left)
        assertEquals(600, result.crops.first { it.position == 1 }.bounds.right)
    }

    @Test
    fun fallbackThreeUsesAverageNormalHeightForCenteredLeftEvidence() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                centeredLeftGroup = 3,
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        val centered = result.crops.first { it.position == 3 }.bounds
        assertEquals(254, centered.top)
        assertEquals(346, centered.bottom)
    }

    @Test
    fun fallbackThreeUsesAverageNormalHeightForAOnePlayerLeftEvidence() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                onePlayerLeftGroup = 3,
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        val onePlayer = result.crops.first { it.position == 3 }.bounds
        assertEquals(254, onePlayer.top)
        assertEquals(346, onePlayer.bottom)
    }

    @Test
    fun fallbackThreeClampsC4RightBoundaryToImageWidth() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                fourthColumnRight = 1_246,
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(611, right.left)
        assertEquals(1_200, right.right)
    }

    @Test
    fun fallbackThreeEightTeamUsesMaximumC4RightAndClampsAtImageWidth() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                imageWidth = 1_740,
                secondColumnRight = 944,
                thirdColumnRight = 953,
                fourthColumnLeft = 1_600,
                fourthColumnRightEdges = listOf(1_732, 1_741, 1_740),
                rightAnchorPositions = emptyList(),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(962, right.left)
        assertEquals(1_740, right.right)
        assertNotEquals(1_549, right.right)
    }

    @Test
    fun fallbackThreeElevenTeamUsesMaximumC4RightAndClampsAtImageWidth() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                imageWidth = 1_744,
                secondColumnRight = 943,
                thirdColumnRight = 1_060,
                fourthColumnLeft = 1_600,
                fourthColumnRightEdges = listOf(1_734, 1_737, 1_744, 1_746),
                rightAnchorPositions = emptyList(),
                rightGroupCenters = (0..5).map { 100 + it * 100 },
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            allowUpperPositionElevenFallback = true,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(975, right.left)
        assertEquals(1_744, right.right)
        assertEquals((6..11).toList(), result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position })
    }

    @Test
    fun fallbackThreeUsesC4MaximumInsteadOfItsMedianForTheRightBoundary() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                fourthColumnRightEdges = listOf(1_120, 1_140, 1_195),
                rightAnchorPositions = emptyList(),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        val right = result.crops.first { it.position == 6 }.bounds
        assertEquals(1_195, right.right)
        assertNotEquals(1_140, right.right)
    }

    @Test
    fun fallbackThreeKeepsRightVerticalGeometryIndependentFromLeftGeometry() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                rightCenterOffset = 50,
                rightAnchorPositions = emptyList(),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(54, result.crops.first { it.position == 1 }.bounds.top)
        assertEquals(104, result.crops.first { it.position == 6 }.bounds.top)
    }

    @Test
    fun fallbackThreeRightIdentityStartsAtSixWithoutAnyRightAnchors() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                rightAnchorPositions = emptyList(),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(
            (6..10).toList(),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.rightPitchSource)
    }

    @Test
    fun fallbackThreeRightIdentityAcceptsPartialAnchorsAsValidation() {
        val baseEvidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeFirstColumn = false,
            imageWidth = 2_000,
            rightAnchorPositions = listOf(7, 9),
            rightAnchorLeft = 1_121,
        )
        val result = calculator.calculate(
            evidence = baseEvidence.copy(
                observations = baseEvidence.observations + listOf(
                    observation("4", 50, 320, 75, 350),
                    observation("5", 50, 420, 75, 450),
                ),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.rightPitchSource)
        assertEquals(
            (6..10).toList(),
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT }.map { it.position },
        )
    }

    @Test
    fun fallbackThreeRightIdentityFailsClosedForAContradictoryAnchor() {
        val baseEvidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeFirstColumn = false,
            imageWidth = 2_000,
            rightAnchorPositions = emptyList(),
            rightAnchorLeft = 1_121,
        )
        val result = calculator.calculate(
            evidence = baseEvidence.copy(
                observations = baseEvidence.observations + listOf(
                    observation("4", 50, 320, 75, 350),
                    observation("5", 50, 420, 75, 450),
                    observation("8", 1_120, 85, 1_150, 115),
                ),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertEquals(
            MatchResultPositionCropCalculationResult.Unavailable(
                MatchResultPositionCropUnavailableReason.RIGHT_COLUMN_BOUNDARY_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun fallbackThreeRightIdentityRejectsMoreGroupsThanUpperPositionsAllow() {
        val baseEvidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeFirstColumn = false,
            imageWidth = 2_000,
            rightGroupCenters = listOf(100, 180, 260, 340, 420, 500, 580, 660),
            rightAnchorLeft = 1_121,
        )
        val result = calculator.calculate(
            evidence = baseEvidence.copy(
                observations = baseEvidence.observations + listOf(
                    observation("4", 50, 320, 75, 350),
                    observation("5", 50, 420, 75, 450),
                ),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertEquals(
            MatchResultPositionCropCalculationResult.Unavailable(
                MatchResultPositionCropUnavailableReason.RIGHT_COLUMN_BOUNDARY_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun hybridFallbackThreeRecoversOnlyTheLeftSideAndPreservesExistingRightGeometry() {
        val evidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeThirdColumn = false,
            rightAnchorPositions = listOf(6, 7, 11),
        )
        val existingBaseline = evidence.copy(
            observations = evidence.observations + listOf(
                observation("4", 50, 320, 75, 350),
                observation("5", 50, 420, 75, 450),
            ),
        )
        val expectedRight = availableResult(
            evidence = existingBaseline,
            allowUpperPositionElevenFallback = true,
        )

        val result = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            allowUpperPositionElevenFallback = true,
        )

        assertTrue("Expected hybrid geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.leftPitchSource)
        assertEquals(expectedRight.rightPitchSource, result.rightPitchSource)
        assertEquals(expectedRight.rightRowPitch, result.rightRowPitch, 0.001)
        assertEquals(
            expectedRight.crops.filter { it.column == MatchResultPositionColumn.RIGHT },
            result.crops.filter { it.column == MatchResultPositionColumn.RIGHT },
        )
    }

    @Test
    fun hybridFallbackThreeRecoversOnlyTheRightSideAndPreservesExistingLeftGeometry() {
        val baseEvidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeFirstColumn = false,
            imageWidth = 2_000,
            rightAnchorLeft = 1_121,
        )
        val evidence = baseEvidence.copy(
            observations = baseEvidence.observations + listOf(
                observation("4", 50, 320, 75, 350),
                observation("5", 50, 420, 75, 450),
            ),
        )
        val baseline = evidence.copy(
            observations = evidence.observations + fallbackThreeColumnObservations(
                label = "Eliminations",
                left = 1_080,
                right = 1_160,
                centers = (1..5).map { 100 + (it - 1) * 100 },
                omitCenter = null,
            ),
        )
        val expectedLeft = availableResult(baseline)

        val result = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected hybrid geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(expectedLeft.leftPitchSource, result.leftPitchSource)
        assertEquals(expectedLeft.leftRowPitch!!, result.leftRowPitch!!, 0.001)
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.rightPitchSource)
        assertEquals(
            expectedLeft.crops.filter { it.column == MatchResultPositionColumn.LEFT },
            result.crops.filter { it.column == MatchResultPositionColumn.LEFT },
        )
    }

    @Test
    fun hybridFallbackThreeRecoversBothSidesWhenBothExistingSidesAreUnavailable() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(includeFourthColumn = true),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.leftPitchSource)
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.rightPitchSource)
    }

    @Test
    fun hybridFallbackThreeLeftRecoveryDoesNotRequireTheThirdEliminationsColumn() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                includeThirdColumn = false,
                rightAnchorPositions = listOf(6, 7),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected hybrid geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals((1..10).toList(), result.crops.map { it.position })
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.leftPitchSource)
        assertEquals(MatchResultPositionPitchSource.RIGHT_CONSECUTIVE, result.rightPitchSource)
    }

    @Test
    fun hybridFallbackThreeRightRecoveryDoesNotRequireTheFirstEliminationsColumn() {
        val baseEvidence = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeFirstColumn = false,
            imageWidth = 2_000,
            rightAnchorLeft = 1_121,
        )
        val result = calculator.calculate(
            evidence = baseEvidence.copy(
                observations = baseEvidence.observations + listOf(
                    observation("4", 50, 320, 75, 350),
                    observation("5", 50, 420, 75, 450),
                ),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected hybrid geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals((1..10).toList(), result.crops.map { it.position })
        assertEquals(MatchResultPositionPitchSource.LEFT_FOUR_TO_FIVE, result.leftPitchSource)
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.rightPitchSource)
    }

    @Test
    fun fallbackThreeSucceedsWhenRightIdentityAnchorsAreUnavailable() {
        val evidence = fallbackThreeEvidence(includeFourthColumn = true)
            .let { it.copy(observations = it.observations.filterNot { observation -> observation.text == "6" }) }

        val result = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertTrue("Expected fallback-three geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        result as MatchResultPositionCropCalculationResult.Available
        assertEquals((1..10).toList(), result.crops.map { it.position })
        assertEquals(MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY, result.rightPitchSource)
    }

    @Test
    fun fallbackThreeFailsClosedWhenC4IsMissing() {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = false,
                rightAnchorPositions = emptyList(),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )

        assertEquals(
            MatchResultPositionCropCalculationResult.Unavailable(
                MatchResultPositionCropUnavailableReason.RIGHT_POSITION_ANCHOR_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun existingMainRuleWinsBeforeFallbackThree() {
        val result = available(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        )

        assertNotEquals(
            MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY,
            result.rightPitchSource,
        )
    }

    @Test
    fun eliminationGroupingAcceptsMixedPairSingletonPairGapFamilies() {
        assertEquals(
            listOf("NORMAL_PAIR", "SINGLETON", "NORMAL_PAIR"),
            fallbackThreeGroupKinds(listOf(38, 56, 56, 39)),
        )
    }

    @Test
    fun eliminationGroupingPairsAllConfidentNormalGaps() {
        assertEquals(
            listOf("NORMAL_PAIR", "NORMAL_PAIR", "NORMAL_PAIR", "NORMAL_PAIR"),
            fallbackThreeGroupKinds(listOf(38, 56, 39, 55, 38, 56, 39)),
        )
    }

    @Test
    fun eliminationGroupingLeavesAllCenteredRowsAsSingletons() {
        assertEquals(
            listOf("SINGLETON", "SINGLETON", "SINGLETON", "SINGLETON", "SINGLETON"),
            fallbackThreeGroupKinds(listOf(93, 95, 92, 94)),
        )
    }

    @Test
    fun eliminationGroupingDoesNotPairOnlyTwoObservations() {
        assertEquals(
            listOf("SINGLETON", "SINGLETON"),
            fallbackThreeGroupKinds(listOf(40)),
        )
    }

    @Test
    fun eliminationGroupingRejectsAnIsolatedSmallGapWithoutFamilyEvidence() {
        assertEquals(
            listOf("SINGLETON", "SINGLETON", "SINGLETON"),
            fallbackThreeGroupKinds(listOf(38, 56)),
        )
    }

    @Test
    fun eliminationGroupingRejectsASequenceWithoutMeaningfulGapSplit() {
        assertEquals(
            listOf("SINGLETON", "SINGLETON", "SINGLETON", "SINGLETON", "SINGLETON"),
            fallbackThreeGroupKinds(listOf(38, 40, 39, 41)),
        )
    }

    @Test
    fun eliminationGroupingAcceptsOnlyTheClearlySmallerGapFamily() {
        val groups = fallbackThreeGroupKinds(listOf(37, 38, 39, 55, 56, 57))

        assertEquals(1, groups.count { it == "NORMAL_PAIR" })
        assertEquals(5, groups.count { it == "SINGLETON" })
    }

    @Test
    fun eliminationGroupingRejectsAWithinGapThatFailsTextHeightSanity() {
        assertEquals(
            listOf("SINGLETON", "SINGLETON", "SINGLETON", "SINGLETON", "SINGLETON"),
            fallbackThreeGroupKinds(
                gaps = listOf(38, 39, 120, 121),
                textHeight = 10,
            ),
        )
    }

    @Test
    fun eliminationGroupingDoesNotReuseAnObservationAcrossOverlappingPairs() {
        val groups = fallbackThreeGroupKinds(listOf(38, 38, 56))

        assertEquals(1, groups.count { it == "NORMAL_PAIR" })
        assertEquals(2, groups.count { it == "SINGLETON" })
    }

    @Test
    fun eliminationGroupingResolvesLeftAndRightGapFamiliesIndependently() {
        val leftGroups = fallbackThreeGroupKinds(listOf(38, 56, 56))
        val rightGroups = fallbackThreeGroupKinds(listOf(31, 47, 47))

        assertEquals(1, leftGroups.count { it == "NORMAL_PAIR" })
        assertEquals(1, rightGroups.count { it == "NORMAL_PAIR" })
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

    private fun availableResult(
        evidence: MatchResultAutoCropEvidence,
        allowUpperPositionElevenFallback: Boolean = false,
    ): MatchResultPositionCropCalculationResult.Available {
        val result = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
        )
        assertTrue("Expected available geometry, got $result", result is MatchResultPositionCropCalculationResult.Available)
        return result as MatchResultPositionCropCalculationResult.Available
    }

    @Suppress("UNCHECKED_CAST")
    private fun fallbackThreeGroupKinds(
        gaps: List<Int>,
        textHeight: Int = 30,
    ): List<String> {
        val boxes = mutableListOf<RawOcrBoundingBox>()
        var centerY = 100
        boxes += RawOcrBoundingBox(
            left = 100,
            top = centerY - textHeight / 2,
            right = 140,
            bottom = centerY + textHeight / 2,
        )
        gaps.forEach { gap ->
            centerY += gap
            boxes += RawOcrBoundingBox(
                left = 100,
                top = centerY - textHeight / 2,
                right = 140,
                bottom = centerY + textHeight / 2,
            )
        }

        val groupingMethod = MatchResultPositionCropCalculator::class.java.getDeclaredMethod(
            "pairFallbackThreeColumnBoxes",
            List::class.java,
            java.lang.Double.TYPE,
        )
        groupingMethod.isAccessible = true
        val groups = groupingMethod.invoke(calculator, boxes, textHeight.toDouble()) as List<Any?>
        return groups.map { group ->
            val nonNullGroup = requireNotNull(group)
            val kindField = nonNullGroup.javaClass.getDeclaredField("kind")
            kindField.isAccessible = true
            requireNotNull(kindField.get(nonNullGroup)).toString()
        }
    }

    private fun evidence(
        vararg observations: MatchResultAutoCropObservation,
    ): MatchResultAutoCropEvidence = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + observations,
        imageDimensions = dimensions,
    )

    private fun realLeftFallbackThreeEvidence(
        c2Observations: List<MatchResultAutoCropObservation>,
    ): MatchResultAutoCropEvidence {
        val base = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeFirstColumn = false,
            includeSecondColumn = false,
            rightAnchorPositions = emptyList(),
        )
        return base.copy(
            observations = base.observations + realSixTeamC1Observations() + c2Observations,
        )
    }

    private fun realSixTeamC1Observations(): List<MatchResultAutoCropObservation> = listOf(
        observation("Eliminations", 255, 23, 333, 37),
        observation("Eliminations", 254, 58, 332, 76),
        observation("Eliminations", 254, 113, 332, 130),
        observation("Eliminations", 254, 151, 332, 167),
        observation("Eliminations", 254, 205, 332, 221),
        observation("Eliminations", 255, 243, 333, 260),
        observation("Eliminations", 254, 296, 332, 313),
        observation("Eliminations", 254, 332, 332, 353),
        observation("Eliminations", 258, 388, 332, 406),
        observation("Eliminations", 243, 426, 330, 442),
    )

    private fun realSixTeamC2Observations(): List<MatchResultAutoCropObservation> = listOf(
        observation("Eliminations", 536, 22, 614, 38),
        observation("Eliminations", 535, 58, 612, 76),
        observation("Eliminations", 524, 113, 611, 130),
        observation("Eliminations", 535, 150, 613, 168),
        observation("Eliminations", 539, 203, 617, 223),
        observation("Eliminations", 537, 241, 612, 260),
        observation("Eliminations", 536, 298, 614, 313),
        observation("Eliminations", 535, 331, 613, 351),
        observation("Eliminations", 535, 388, 613, 404),
    )

    private fun realSixTeamC2CompleteObservations(): List<MatchResultAutoCropObservation> =
        realSixTeamC2Observations() + observation("Eliminations", 535, 426, 613, 443)

    private fun sixTeamFallbackThreeEvidence(): MatchResultAutoCropEvidence =
        MatchResultAutoCropEvidence(
            observations = realSixTeamC1Observations() + realSixTeamC2Observations() + listOf(
                observation("Eliminations", 871, 16, 1023, 36),
                observation("Eliminations", 871, 48, 1021, 68),
                observation("Eliminations", 1078, 19, 1130, 36),
                observation("Eliminations", 1078, 52, 1130, 68),
            ),
            imageDimensions = OcrImageDimensions(width = 1_200, height = 503),
        )

    private fun sparseRightFallbackThreeEvidence(
        c3Bounds: List<Pair<Int, Int>>,
    ): MatchResultAutoCropEvidence {
        val base = fallbackThreeEvidence(
            includeFourthColumn = true,
            includeThirdColumn = false,
            rightAnchorPositions = emptyList(),
        )
        return base.copy(
            observations = base.observations + c3Bounds.map { (top, bottom) ->
                observation("Eliminations", 820, top, 900, bottom)
            },
        )
    }

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

    private fun fallbackThreeEvidence(
        includeFourthColumn: Boolean,
        wideMergedFirstColumnBox: Boolean = false,
        centeredLeftGroup: Int? = null,
        onePlayerLeftGroup: Int? = null,
        imageWidth: Int = 1_200,
        rightCenterOffset: Int = 0,
        firstColumnLeft: Int = 260,
        includeFirstColumn: Boolean = true,
        includeSecondColumn: Boolean = true,
        includeThirdColumn: Boolean = true,
        secondColumnRight: Int = 600,
        secondColumnMissingObservationCenter: Int? = null,
        thirdColumnLeft: Int = 820,
        thirdColumnRight: Int = 900,
        fourthColumnLeft: Int = 1_080,
        fourthColumnRight: Int = 1_160,
        fourthColumnRightEdges: List<Int>? = null,
        rightAnchorPositions: List<Int> = listOf(6),
        rightAnchorLeft: Int = 620,
        rightGroupCenters: List<Int>? = null,
        eliminationLabel: String = "Eliminations",
    ): MatchResultAutoCropEvidence {
        val centers = (1..5).map { 100 + (it - 1) * 100 }
        val resolvedRightGroupCenters = rightGroupCenters ?: centers
        val columns = mutableListOf<MatchResultAutoCropObservation>()
        if (includeFirstColumn) {
            columns += fallbackThreeColumnObservations(
                label = eliminationLabel,
                left = firstColumnLeft,
                right = 340,
                centers = centers,
                omitCenter = centeredLeftGroup ?: onePlayerLeftGroup,
                wideMergedBox = wideMergedFirstColumnBox,
            )
        }
        if (includeSecondColumn) {
            columns += fallbackThreeColumnObservations(
                label = eliminationLabel,
                left = 520,
                right = secondColumnRight,
                centers = centers,
                omitCenter = centeredLeftGroup ?: onePlayerLeftGroup,
                omitSingleObservationCenter = secondColumnMissingObservationCenter?.toDouble(),
            )
        }
        if (centeredLeftGroup != null || onePlayerLeftGroup != null) {
            val center = centeredLeftGroup ?: onePlayerLeftGroup ?: return MatchResultAutoCropEvidence(
                observations = columns,
                imageDimensions = OcrImageDimensions(width = imageWidth, height = 700),
            )
            columns += observation(
                eliminationLabel,
                260,
                center * 100 - 15,
                340,
                center * 100 + 15,
            )
            if (centeredLeftGroup != null) {
                columns += observation(
                    eliminationLabel,
                    520,
                    center * 100 - 15,
                    600,
                    center * 100 + 15,
                )
            }
        }
        if (includeThirdColumn) {
            columns += fallbackThreeColumnObservations(
                label = eliminationLabel,
                left = thirdColumnLeft,
                right = thirdColumnRight,
                centers = resolvedRightGroupCenters.map { it + rightCenterOffset },
                omitCenter = null,
            )
        }
        if (includeFourthColumn) {
            val rightEdges = fourthColumnRightEdges ?: centers.map { fourthColumnRight }
            columns += rightEdges.mapIndexed { index, right ->
                val center = centers.getOrElse(index) { centers.last() + (index - centers.lastIndex) * 100 }
                observation(eliminationLabel, fourthColumnLeft, center - 15, right, center + 15)
            }
        }
        columns += rightAnchorPositions.map { position ->
            val center = (resolvedRightGroupCenters.getOrNull(position - 6)
                ?: (100 + (position - 6) * 100)) + rightCenterOffset
            observation(position.toString(), rightAnchorLeft, center - 15, rightAnchorLeft + 30, center + 15)
        }
        return MatchResultAutoCropEvidence(
            observations = columns,
            imageDimensions = OcrImageDimensions(width = imageWidth, height = 700),
        )
    }

    private fun fallbackThreeColumnObservations(
        label: String,
        left: Int,
        right: Int,
        centers: List<Int>,
        omitCenter: Int?,
        wideMergedBox: Boolean = false,
        omitSingleObservationCenter: Double? = null,
    ): List<MatchResultAutoCropObservation> = centers
        .filter { omitCenter == null || it != omitCenter * 100 }
        .flatMapIndexed { index, center ->
            val upperTop = center - 35
            val lowerTop = center + 5
            val upperRight = if (wideMergedBox && index == 0) right + 100 else right
            val pair = listOf(
                observation(label, left, upperTop, upperRight, upperTop + 30),
                observation(label, left, lowerTop, right, lowerTop + 30),
            )
            if (omitSingleObservationCenter == center.toDouble()) pair.drop(1) else pair
        }

    private fun fallbackThreeColumnObservationsAtCenters(
        label: String,
        left: Int,
        right: Int,
        centers: List<Double>,
        heights: List<Int>,
    ): List<MatchResultAutoCropObservation> = centers.mapIndexed { index, center ->
        val height = heights[index]
        val top = (center - height / 2.0).toInt()
        observation(label, left, top, right, top + height)
    }

    private fun assertEliminationClassifierAccepts(label: String) {
        val result = calculator.calculate(
            evidence = fallbackThreeEvidence(
                includeFourthColumn = true,
                eliminationLabel = label,
                rightAnchorPositions = emptyList(),
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        assertTrue("Expected elimination label to be accepted: $label; got $result", result is MatchResultPositionCropCalculationResult.Available)
    }

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
