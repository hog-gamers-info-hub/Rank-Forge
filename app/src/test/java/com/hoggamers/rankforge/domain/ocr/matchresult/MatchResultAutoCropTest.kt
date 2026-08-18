package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultAutoCropTest {
    private val calculator = MatchResultAutoCropCalculator()
    private val dimensions = OcrImageDimensions(width = 1_000, height = 800)

    @Test
    fun exactFourIsAccepted() {
        assertEquals(
            box(left = 100, top = 100, right = 130, bottom = 140),
            MatchResultAutoCropAnchorDetector().findAnchorFour(
                evidence(observation("4", 100, 100, 130, 140)),
            ),
        )
    }

    @Test
    fun trimmedFourIsAccepted() {
        assertEquals(
            box(left = 100, top = 100, right = 130, bottom = 140),
            MatchResultAutoCropAnchorDetector().findAnchorFour(
                evidence(observation(" 4 ", 100, 100, 130, 140)),
            ),
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

        assertEquals(null, result)
    }

    @Test
    fun multipleFourCandidatesSelectMinimumLeft() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFour(
            evidence(
                observation("4", 300, 100, 330, 140),
                observation("4", 120, 200, 150, 240),
            ),
        )

        assertEquals(box(left = 120, top = 200, right = 150, bottom = 240), result)
    }

    @Test
    fun equalLeftFourCandidatesUseGeometryTieBreakers() {
        val detector = MatchResultAutoCropAnchorDetector()
        val first = observation("4", 120, 300, 160, 340)
        val second = observation("4", 120, 100, 150, 140)

        assertEquals(
            box(left = 120, top = 100, right = 150, bottom = 140),
            detector.findAnchorFour(evidence(first, second)),
        )
        assertEquals(
            detector.findAnchorFour(evidence(first, second)),
            detector.findAnchorFour(evidence(second, first)),
        )
    }

    @Test
    fun exactFiveIsAccepted() {
        assertEquals(
            box(left = 100, top = 500, right = 130, bottom = 540),
            MatchResultAutoCropAnchorDetector().findAnchorFive(
                evidence(observation("5", 100, 500, 130, 540)),
            ),
        )
    }

    @Test
    fun trimmedFiveIsAccepted() {
        assertEquals(
            box(left = 100, top = 500, right = 130, bottom = 540),
            MatchResultAutoCropAnchorDetector().findAnchorFive(
                evidence(observation(" 5 ", 100, 500, 130, 540)),
            ),
        )
    }

    @Test
    fun letterSIsNotAcceptedAsFive() {
        assertEquals(
            null,
            MatchResultAutoCropAnchorDetector().findAnchorFive(
                evidence(observation("S", 100, 500, 130, 540)),
            ),
        )
    }

    @Test
    fun multipleFiveCandidatesSelectMinimumLeft() {
        val result = MatchResultAutoCropAnchorDetector().findAnchorFive(
            evidence(
                observation("5", 300, 500, 330, 540),
                observation("5", 120, 600, 150, 640),
            ),
        )

        assertEquals(box(left = 120, top = 600, right = 150, bottom = 640), result)
    }

    @Test
    fun equalLeftFiveCandidatesUseGeometryTieBreakers() {
        val detector = MatchResultAutoCropAnchorDetector()
        val first = observation("5", 120, 600, 160, 640)
        val second = observation("5", 120, 500, 150, 540)

        assertEquals(
            box(left = 120, top = 500, right = 150, bottom = 540),
            detector.findAnchorFive(evidence(first, second)),
        )
        assertEquals(
            detector.findAnchorFive(evidence(first, second)),
            detector.findAnchorFive(evidence(second, first)),
        )
    }

    @Test
    fun missingAnchorFourReturnsAnchorFourMissing() {
        assertEquals(
            MatchResultAutoCropResult.AnchorFourMissing,
            calculator.calculate(evidence(observation("5", 100, 500, 130, 540))),
        )
    }

    @Test
    fun missingAnchorFiveReturnsAnchorFiveMissing() {
        assertEquals(
            MatchResultAutoCropResult.AnchorFiveMissing,
            calculator.calculate(evidence(observation("4", 100, 100, 130, 140))),
        )
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

        assertEquals(OcrNormalizedCropRect(0.13, 0.0, 0.9, 421.0 / dimensions.height), result.crop)
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
                    observation("right", 1300, 300, 1369, 350),
                ),
                imageDimensions = OcrImageDimensions(width = 1_600, height = 720),
            ),
        )

        assertEquals(
            OcrNormalizedCropRect(208.0 / 1_600, 162.0 / 720, 1369.0 / 1_600, 630.0 / 720),
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
                    observation("right", 1_600, 400, 1_800, 500),
                ),
                imageDimensions = OcrImageDimensions(2_000, 1_600),
            ),
        )

        assertEquals(first.crop, second.crop)
    }

    @Test
    fun sameEvidenceInDifferentOrderProducesSameResult() {
        val observations = listOf(
            observation("4", 300, 100, 330, 140),
            observation("4", 100, 100, 130, 140),
            observation("5", 400, 300, 440, 340),
            observation("5", 200, 300, 240, 340),
            observation("right", 800, 200, 900, 250),
        )

        val first = calculator.calculate(evidence(*observations.toTypedArray()))
        val second = calculator.calculate(evidence(*observations.reversed().toTypedArray()))

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
        MatchResultAutoCropEvidence(observations.toList(), dimensions)

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
