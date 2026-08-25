package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultAutoCropTest {
    private val calculator = MatchResultAutoCropCalculator()
    private val dimensions = OcrImageDimensions(width = 1_000, height = 800)

    @Test
    fun exactFourIsAcceptedWhenStructurallyLeftOfEliminationColumn() {
        assertEquals(
            box(100, 100, 130, 140),
            MatchResultAutoCropAnchorDetector().findAnchorFour(
                evidence(observation("4", 100, 100, 130, 140)),
            ),
        )
    }

    @Test
    fun trimmedFourIsAccepted() {
        assertEquals(
            box(100, 100, 130, 140),
            MatchResultAutoCropAnchorDetector().findAnchorFour(
                evidence(observation(" 4 ", 100, 100, 130, 140)),
            ),
        )
    }

    @Test
    fun exactFiveIsAcceptedWhenStructurallyLeftOfEliminationColumn() {
        assertEquals(
            box(100, 500, 130, 540),
            MatchResultAutoCropAnchorDetector().findAnchorFive(
                evidence(observation("5", 100, 500, 130, 540)),
            ),
        )
    }

    @Test
    fun trimmedFiveIsAccepted() {
        assertEquals(
            box(100, 500, 130, 540),
            MatchResultAutoCropAnchorDetector().findAnchorFive(
                evidence(observation(" 5 ", 100, 500, 130, 540)),
            ),
        )
    }

    @Test
    fun anchorIsRejectedWhenEliminationReferenceIsMissing() {
        val rawEvidence = MatchResultAutoCropEvidence(
            observations = listOf(observation("4", 100, 100, 130, 140)),
            imageDimensions = dimensions,
        )

        assertNull(MatchResultAutoCropAnchorDetector().findAnchorFour(rawEvidence))
    }

    @Test
    fun commonEliminationOcrMisspellingStillProvidesStructuralBoundary() {
        val rawEvidence = MatchResultAutoCropEvidence(
            observations = listOf(
                observation("4", 100, 100, 130, 140),
                observation("Eiminations", 500, 100, 580, 130),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(
            box(100, 100, 130, 140),
            MatchResultAutoCropAnchorDetector().findAnchorFour(rawEvidence),
        )
    }

    @Test
    fun nonExactFourTextIsRejected() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFour(
            evidence(
                observation("14", 100, 100, 130, 140),
                observation("40", 150, 100, 180, 140),
                observation("4 Eliminations", 200, 100, 230, 140),
                observation("PLAYER4", 250, 100, 280, 140),
            ),
        )

        assertNull(result)
    }

    @Test
    fun letterSIsNotAcceptedAsFive() {
        assertNull(
            MatchResultAutoCropAnchorDetector().findAnchorFive(
                evidence(observation("S", 100, 500, 130, 540)),
            ),
        )
    }

    @Test
    fun exactFourInsideOrNearEliminationColumnIsRejected() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFour(
            evidence(observation("4", 420, 100, 440, 140)),
        )

        // Elimination boundary is x=500. Gap is only 60px = 6% of width.
        assertNull(result)
    }

    @Test
    fun exactFiveInsideOrNearEliminationColumnIsRejected() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFive(
            evidence(observation("5", 420, 500, 440, 540)),
        )

        assertNull(result)
    }

    @Test
    fun playerNameDigitsFourAndFiveTooCloseToEliminationColumnAreRejected() {
        val detector = MatchResultAutoCropAnchorDetector()
        val rawEvidence = evidence(
            observation("4", 390, 200, 410, 230),
            observation("5", 395, 250, 415, 280),
        )

        assertNull(detector.findAnchorFour(rawEvidence))
        assertNull(detector.findAnchorFive(rawEvidence))
    }

    @Test
    fun realPlacementFourWinsWhenFalseExactFoursExistElsewhere() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFour(
            evidence(
                observation("4", 100, 200, 130, 240),
                observation("4", 390, 200, 410, 230),
                observation("4", 520, 200, 540, 230),
            ),
        )

        assertEquals(box(100, 200, 130, 240), result)
    }

    @Test
    fun realPlacementFiveWinsWhenFalseExactFivesExistElsewhere() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFive(
            evidence(
                observation("5", 100, 500, 130, 540),
                observation("5", 390, 500, 410, 530),
                observation("5", 520, 500, 540, 530),
            ),
        )

        assertEquals(box(100, 500, 130, 540), result)
    }

    @Test
    fun multipleValidFourCandidatesSelectMinimumLeft() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFour(
            evidence(
                observation("4", 300, 100, 330, 140),
                observation("4", 120, 200, 150, 240),
            ),
        )

        assertEquals(box(120, 200, 150, 240), result)
    }

    @Test
    fun equalLeftFourCandidatesUseGeometryTieBreakers() {
        val detector = MatchResultAutoCropAnchorDetector()
        val first = observation("4", 120, 300, 160, 340)
        val second = observation("4", 120, 100, 150, 140)

        assertEquals(
            box(120, 100, 150, 140),
            detector.findAnchorFour(evidence(first, second)),
        )
        assertEquals(
            detector.findAnchorFour(evidence(first, second)),
            detector.findAnchorFour(evidence(second, first)),
        )
    }

    @Test
    fun multipleValidFiveCandidatesSelectMinimumLeft() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFive(
            evidence(
                observation("5", 300, 500, 330, 540),
                observation("5", 120, 600, 150, 640),
            ),
        )

        assertEquals(box(120, 600, 150, 640), result)
    }

    @Test
    fun equalLeftFiveCandidatesUseGeometryTieBreakers() {
        val detector = MatchResultAutoCropAnchorDetector()
        val first = observation("5", 120, 600, 160, 640)
        val second = observation("5", 120, 500, 150, 540)

        assertEquals(
            box(120, 500, 150, 540),
            detector.findAnchorFive(evidence(first, second)),
        )
        assertEquals(
            detector.findAnchorFive(evidence(first, second)),
            detector.findAnchorFive(evidence(second, first)),
        )
    }

    @Test
    fun missingAnchorFourWithoutRecoveryEvidenceReturnsAnchorFourMissing() {
        assertEquals(
            MatchResultAutoCropResult.AnchorFourMissing,
            calculator.calculate(evidence(observation("5", 100, 500, 130, 540))),
        )
    }

    @Test
    fun missingAnchorFiveWithoutRecoveryEvidenceReturnsAnchorFiveMissing() {
        assertEquals(
            MatchResultAutoCropResult.AnchorFiveMissing,
            calculator.calculate(evidence(observation("4", 100, 100, 130, 140))),
        )
    }

    @Test
    fun bothLeftAnchorsMissingDoesNotAttemptRightColumnOnlyRecovery() {
        assertEquals(
            MatchResultAutoCropResult.AnchorFourMissing,
            calculator.calculate(
                evidence(
                    observation("6", 600, 100, 620, 120),
                    observation("7", 600, 150, 620, 170),
                ),
            ),
        )
    }

    @Test
    fun missingFourIsRecoveredFromConsecutiveSixAndSeven() {
        val result = proposed(
            evidence(
                observation("5", 180, 334, 220, 355),
                observation("6", 600, 100, 620, 120),
                observation("7", 600, 150, 620, 170),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(
                173.0 / dimensions.width,
                80.0 / dimensions.height,
                900.0 / dimensions.width,
                374.0 / dimensions.height,
            ),
            result.crop,
        )
    }

    @Test
    fun missingFiveIsRecoveredFromConsecutiveSixAndSeven() {
        val result = proposed(
            evidence(
                observation("4", 180, 276, 220, 296),
                observation("6", 600, 100, 620, 120),
                observation("7", 600, 150, 620, 170),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(
                173.0 / dimensions.width,
                80.0 / dimensions.height,
                900.0 / dimensions.width,
                374.0 / dimensions.height,
            ),
            result.crop,
        )
    }

    @Test
    fun tenAndElevenCanRecoverMissingFour() {
        val result = proposed(
            evidence(
                observation("5", 180, 334, 220, 355),
                observation("10", 600, 300, 620, 320),
                observation("11", 600, 350, 620, 370),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(
                173.0 / dimensions.width,
                80.0 / dimensions.height,
                900.0 / dimensions.width,
                374.0 / dimensions.height,
            ),
            result.crop,
        )
    }

    @Test
    fun nonConsecutiveRightPositionsDoNotRecoverMissingFour() {
        val result = calculator.calculate(
            evidence(
                observation("5", 180, 334, 220, 355),
                observation("6", 600, 100, 620, 120),
                observation("8", 600, 200, 620, 220),
            ),
        )

        assertEquals(MatchResultAutoCropResult.AnchorFourMissing, result)
    }

    @Test
    fun nonExactRightPositionTextDoesNotRecoverMissingFour() {
        val result = calculator.calculate(
            evidence(
                observation("5", 180, 334, 220, 355),
                observation("6 Eliminations", 600, 100, 620, 120),
                observation("7 Eliminations", 600, 150, 620, 170),
            ),
        )

        assertEquals(MatchResultAutoCropResult.AnchorFourMissing, result)
    }

    @Test
    fun misleadingConsecutiveDigitsAreRejectedByKnownAnchorGeometry() {
        val result = proposed(
            evidence(
                observation("5", 180, 334, 220, 355),
                observation("6", 300, 100, 320, 120),
                observation("7", 300, 250, 320, 270),
                observation("8", 600, 200, 620, 220),
                observation("9", 600, 250, 620, 270),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(
                173.0 / dimensions.width,
                80.0 / dimensions.height,
                900.0 / dimensions.width,
                374.0 / dimensions.height,
            ),
            result.crop,
        )
    }

    @Test
    fun horizontallyMisalignedRightPairDoesNotRecoverMissingFour() {
        val result = calculator.calculate(
            evidence(
                observation("5", 180, 334, 220, 355),
                observation("6", 600, 100, 620, 120),
                observation("7", 800, 150, 820, 170),
            ),
        )

        assertEquals(MatchResultAutoCropResult.AnchorFourMissing, result)
    }

    @Test
    fun blurredFourFixtureRejectsEliminationFourAndUsesSevenEightRecovery() {
        val fullHdEvidence = MatchResultAutoCropEvidence(
            observations = listOf(
                // Real placement 4 is absent/blurred.
                observation("5", 367, 860, 383, 890),

                // False exact digits from elimination columns.
                observation("4", 1_114, 271, 1_128, 294),
                observation("5", 1_114, 328, 1_127, 353),

                // Leftmost elimination-column reference.
                observation("Eliminations", 703, 270, 823, 298),

                // Real right-side placement anchors from the physical trace.
                observation("7", 1_341, 413, 1_355, 437),
                observation("8", 1_341, 534, 1_355, 560),

                observation("right", 2_000, 600, 2_057, 630),
            ),
            imageDimensions = OcrImageDimensions(width = 2_400, height = 1_080),
        )

        val detector = MatchResultAutoCropAnchorDetector()
        assertNull(detector.findAnchorFour(fullHdEvidence))
        assertEquals(box(367, 860, 383, 890), detector.findAnchorFive(fullHdEvidence))

        val result = proposed(fullHdEvidence)
        assertEquals(
            OcrNormalizedCropRect(
                310.0 / 2_400,
                231.0 / 1_080,
                2_057.0 / 2_400,
                947.0 / 1_080,
            ),
            result.crop,
        )
    }

    @Test
    fun recoveryIsIndependentOfObservationOrder() {
        val observations = listOf(
            observation("5", 180, 334, 220, 355),
            observation("6", 600, 100, 620, 120),
            observation("7", 600, 150, 620, 170),
            observation("right", 800, 200, 900, 250),
            eliminationReference(),
        )

        val first = calculator.calculate(
            MatchResultAutoCropEvidence(observations, dimensions),
        )
        val second = calculator.calculate(
            MatchResultAutoCropEvidence(observations.reversed(), dimensions),
        )

        assertEquals(first, second)
    }

    @Test
    fun bothRealAnchorsRemainAuthoritativeWhenRightColumnEvidenceAlsoExists() {
        val withoutRightRecoveryEvidence = proposed(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 300, 240, 340),
                observation("right", 800, 200, 900, 250),
            ),
        )
        val withRightRecoveryEvidence = proposed(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 300, 240, 340),
                observation("6", 600, 100, 620, 120),
                observation("7", 600, 400, 620, 420),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(withoutRightRecoveryEvidence.crop, withRightRecoveryEvidence.crop)
    }

    @Test
    fun rowPitchUsesDoubleCenterYGeometry() {
        val result = proposed(
            evidence(
                observation("4", 100, 100, 130, 141),
                observation("5", 200, 300, 240, 341),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(0.13, 0.0, 0.9, 421.0 / dimensions.height),
            result.crop,
        )
    }

    @Test
    fun zeroRowPitchReturnsInvalidRowPitch() {
        val result = calculator.calculate(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 110, 240, 130),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(MatchResultAutoCropResult.InvalidRowPitch, result)
    }

    @Test
    fun negativeRowPitchReturnsInvalidRowPitch() {
        val result = calculator.calculate(
            evidence(
                observation("4", 100, 300, 130, 340),
                observation("5", 200, 100, 240, 140),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(MatchResultAutoCropResult.InvalidRowPitch, result)
    }

    @Test
    fun leftUsesP5CenterXMinusPointFourFiveTimesRowPitch() {
        val result = proposed(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 300, 240, 340),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(0.13, result.crop.left, 0.0)
    }

    @Test
    fun increasingRowPitchMovesLeftOutwardProportionally() {
        val shorterPitch = proposed(
            evidence(
                observation("4", 100, 200, 130, 240),
                observation("5", 200, 300, 240, 340),
                observation("right", 800, 200, 900, 250),
            ),
        )
        val longerPitch = proposed(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 300, 240, 340),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertTrue(longerPitch.crop.left < shorterPitch.crop.left)
    }

    @Test
    fun topUsesP5CenterYMinusFourPointFiveTimesRowPitch() {
        val result = proposed(
            evidence(
                observation("4", 100, 440, 130, 460),
                observation("5", 200, 490, 240, 510),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(275.0 / dimensions.height, result.crop.top, 0.0)
    }

    @Test
    fun bottomUsesP5CenterYPlusZeroPointFiveTimesRowPitch() {
        val result = proposed(
            evidence(
                observation("4", 100, 440, 130, 460),
                observation("5", 200, 490, 240, 510),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(525.0 / dimensions.height, result.crop.bottom, 0.0)
    }

    @Test
    fun rightIsMaximumAcrossAllUsableObservations() {
        val result = proposed(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 300, 240, 340),
                observation("outside row band", 900, 50, 990, 80),
            ),
        )

        assertEquals(0.99, result.crop.right, 0.0)
    }

    @Test
    fun invalidObservationsDoNotBecomeRightBoundary() {
        val result = proposed(
            evidence(
                observation("4", 100, 100, 130, 140),
                observation("5", 200, 300, 240, 340),
                MatchResultAutoCropObservation("invalid", box(950, 50, 950, 80)),
                MatchResultAutoCropObservation("missing box", null),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(0.9, result.crop.right, 0.0)
    }

    @Test
    fun fractionalGeometryUsesOutwardPixelConversion() {
        val result = proposed(
            MatchResultAutoCropEvidence(
                observations = listOf(
                    observation("4", 244, 480, 256, 499),
                    observation("5", 245, 573, 256, 593),
                    observation("Eliminations", 469, 180, 549, 199),
                    observation("right", 1_300, 300, 1_369, 350),
                ),
                imageDimensions = OcrImageDimensions(width = 1_600, height = 720),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(
                208.0 / 1_600,
                162.0 / 720,
                1_369.0 / 1_600,
                630.0 / 720,
            ),
            result.crop,
        )
    }

    @Test
    fun calculatedGeometryClampsToImageBounds() {
        val result = proposed(
            evidence(
                observation("4", -20, -30, 100, 100),
                observation("5", -40, 300, 50, 900),
                observation("right", 900, 200, 1_200, 250),
            ),
        )

        assertEquals(OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0), result.crop)
    }

    @Test
    fun invalidCalculatedRectangleReturnsInvalidCalculatedCrop() {
        val result = calculator.calculate(
            evidence(
                observation("4", 100, 797, 130, 899),
                observation("5", 200, 798, 240, 900),
                observation("right", 800, 200, 900, 250),
            ),
        )

        assertEquals(MatchResultAutoCropResult.InvalidCalculatedCrop, result)
    }

    @Test
    fun normalizedCropIsResolutionIndependentForProportionallyScaledGeometry() {
        val first = proposed(
            MatchResultAutoCropEvidence(
                observations = listOf(
                    observation("4", 100, 100, 130, 140),
                    observation("5", 200, 300, 240, 340),
                    observation("Eliminations", 500, 100, 580, 130),
                    observation("right", 800, 200, 900, 250),
                ),
                imageDimensions = OcrImageDimensions(1_000, 800),
            ),
        )
        val second = proposed(
            MatchResultAutoCropEvidence(
                observations = listOf(
                    observation("4", 200, 200, 260, 280),
                    observation("5", 400, 600, 480, 680),
                    observation("Eliminations", 1_000, 200, 1_160, 260),
                    observation("right", 1_600, 400, 1_800, 500),
                ),
                imageDimensions = OcrImageDimensions(2_000, 1_600),
            ),
        )

        assertEquals(first.crop, second.crop)
    }

    @Test
    fun recoveryIsResolutionIndependentForProportionallyScaledGeometry() {
        val first = proposed(
            MatchResultAutoCropEvidence(
                observations = listOf(
                    observation("5", 180, 334, 220, 355),
                    observation("Eliminations", 500, 100, 580, 130),
                    observation("6", 600, 100, 620, 120),
                    observation("7", 600, 150, 620, 170),
                    observation("right", 800, 200, 900, 250),
                ),
                imageDimensions = OcrImageDimensions(1_000, 800),
            ),
        )
        val second = proposed(
            MatchResultAutoCropEvidence(
                observations = listOf(
                    observation("5", 360, 668, 440, 710),
                    observation("Eliminations", 1_000, 200, 1_160, 260),
                    observation("6", 1_200, 200, 1_240, 240),
                    observation("7", 1_200, 300, 1_240, 340),
                    observation("right", 1_600, 400, 1_800, 500),
                ),
                imageDimensions = OcrImageDimensions(2_000, 1_600),
            ),
        )

        // The crop is converted outward to integer pixels before normalization.
        // Proportionally scaled geometry can therefore differ by up to one source
        // pixel after floor/ceil even though the underlying recovered geometry scales
        // exactly. Verify resolution independence within that quantization tolerance.
        val widthTolerance = 1.0 / 1_000.0
        val heightTolerance = 1.0 / 800.0

        assertEquals(first.crop.left, second.crop.left, widthTolerance)
        assertEquals(first.crop.top, second.crop.top, heightTolerance)
        assertEquals(first.crop.right, second.crop.right, widthTolerance)
        assertEquals(first.crop.bottom, second.crop.bottom, heightTolerance)
    }

    @Test
    fun sameEvidenceInDifferentOrderProducesSameResult() {
        val observations = listOf(
            observation("4", 300, 100, 330, 140),
            observation("4", 100, 100, 130, 140),
            observation("5", 400, 300, 440, 340),
            observation("5", 200, 300, 240, 340),
            observation("right", 800, 200, 900, 250),
            eliminationReference(),
        )

        val first = calculator.calculate(MatchResultAutoCropEvidence(observations, dimensions))
        val second = calculator.calculate(
            MatchResultAutoCropEvidence(observations.reversed(), dimensions),
        )

        assertEquals(first, second)
    }

    @Test
    fun ocrFailedResultRemainsRepresented() {
        val result: MatchResultAutoCropResult = MatchResultAutoCropResult.OcrFailed
        assertEquals(MatchResultAutoCropResult.OcrFailed, result)
    }

    @Test
    fun rightBoundaryMissingResultRemainsRepresented() {
        val result: MatchResultAutoCropResult = MatchResultAutoCropResult.RightBoundaryMissing
        assertEquals(MatchResultAutoCropResult.RightBoundaryMissing, result)
    }

    private fun proposed(evidence: MatchResultAutoCropEvidence): MatchResultAutoCropResult.Proposed =
        calculator.calculate(evidence) as MatchResultAutoCropResult.Proposed

    private fun evidence(vararg observations: MatchResultAutoCropObservation) =
        MatchResultAutoCropEvidence(
            observations = observations.toList() + eliminationReference(),
            imageDimensions = dimensions,
        )

    private fun eliminationReference() =
        observation("Eliminations", 500, 50, 580, 80)

    private fun observation(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = MatchResultAutoCropObservation(text, box(left, top, right, bottom))

    private fun box(left: Int, top: Int, right: Int, bottom: Int) =
        RawOcrBoundingBox(left, top, right, bottom)
}
