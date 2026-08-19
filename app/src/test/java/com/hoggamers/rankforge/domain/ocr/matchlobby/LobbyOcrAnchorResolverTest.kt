package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyOcrAnchorResolverTest {
    private val resolver = LobbyOcrAnchorResolver()
    private val dimensions = OcrImageDimensions(1600, 720)

    @Test
    fun filtersObservationsToTheExpectedScreenshotGroup() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 100, 100),
                observation("5", 300, 100),
                observation("9", 500, 100),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun acceptsOnlyStrictExactWholeNumberText() {
        val anchors = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("6", 100, 100),
                observation("6.", 200, 100),
                observation("I", 300, 100),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(6), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun deduplicatesBlockLineElementRepresentationsAndPrefersElement() {
        val box = RawOcrBoundingBox(100, 100, 120, 120)
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                LobbyOcrAnchorObservation("1", box, LobbyOcrAnchorLevel.BLOCK, blockIndex = 0),
                LobbyOcrAnchorObservation(
                    "1", box, LobbyOcrAnchorLevel.LINE, blockIndex = 0, lineIndex = 0,
                    parentBoundingBox = box,
                ),
                LobbyOcrAnchorObservation(
                    "1", box, LobbyOcrAnchorLevel.ELEMENT, blockIndex = 0, lineIndex = 0,
                    elementIndex = 0, parentBoundingBox = box,
                ),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(1, anchors.size)
        assertEquals(LobbyOcrAnchorLevel.ELEMENT, anchors.single().level)
    }

    @Test
    fun structuralEvidenceRejectsFalseNumberPositionWhenTrueGridExists() {
        val anchors = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 585, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
                observation("6", 1400, 100),
                observation("6", 1076, 245),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(5, 6, 7, 8), anchors.map { it.anchor.slotNumber })
        assertEquals(1076.0, anchors[1].anchor.centerX, 0.0)
        assertEquals(245.0, anchors[1].anchor.centerY, 0.0)
    }

    @Test
    fun inputOrderingDoesNotChangeResolvedAnchors() {
        val observations = listOf(
            observation("5", 585, 245),
            observation("6", 1076, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        val first = resolver.resolve(2, observations, dimensions)
        val second = resolver.resolve(2, observations.reversed(), dimensions)

        assertEquals(first, second)
    }

    @Test
    fun shotOneTwoAnchorsRemainOnlyTwoAndAreNotReconstructed() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 1076, 232),
                observation("4", 1076, 437),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(2, 4), anchors.map { it.anchor.slotNumber })
        assertTrue(anchors.size <= 2)
        assertEquals(
            LobbyGridReconstructionResult.InsufficientAnchors,
            LobbySlotGridReconstructor().reconstruct(1, anchors.map { it.anchor }),
        )
    }

    @Test
    fun shotTwoThreeAnchorLShapeAndShotThreeFourAnchorsResolve() {
        val shotTwo = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 585, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
            ),
            imageDimensions = dimensions,
        )
        val shotThree = resolver.resolve(
            screenshotIndex = 3,
            observations = listOf(
                observation("9", 585, 250),
                observation("10", 1075, 250),
                observation("11", 584, 455),
                observation("12", 1074, 456),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(5, 7, 8), shotTwo.map { it.anchor.slotNumber })
        assertEquals(listOf(9, 10, 11, 12), shotThree.map { it.anchor.slotNumber })
    }

    @Test
    fun shotTwoAndThreeResolvedGridsProduceLa04Proposals() {
        val calculator = LobbyAutoCropCalculator()
        val shotTwo = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 585, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
            ),
            imageDimensions = dimensions,
        )
        val shotThree = resolver.resolve(
            screenshotIndex = 3,
            observations = listOf(
                observation("9", 585, 250),
                observation("10", 1075, 250),
                observation("11", 584, 455),
                observation("12", 1074, 456),
            ),
            imageDimensions = dimensions,
        )

        val shotTwoGrid = (LobbySlotGridReconstructor().reconstruct(2, shotTwo.map { it.anchor })
            as LobbyGridReconstructionResult.Reconstructed).grid
        val shotThreeGrid = (LobbySlotGridReconstructor().reconstruct(3, shotThree.map { it.anchor })
            as LobbyGridReconstructionResult.Reconstructed).grid
        val shotTwoCrop = (calculator.calculate(
            shotTwoGrid,
            dimensions.width,
            dimensions.height,
            LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        ) as LobbyAutoCropCalculationResult.Proposal).crop
        val shotThreeCrop = (calculator.calculate(
            shotThreeGrid,
            dimensions.width,
            dimensions.height,
            LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        ) as LobbyAutoCropCalculationResult.Proposal).crop

        assertPixels(shotTwoCrop, 548.444315, 133.000028, 1525.938323, 541.360214)
        assertPixels(shotThreeCrop, 547.999800, 138.135343, 1523.999930, 545.999947)
    }

    @Test
    fun invalidScreenshotIndexProducesNoResolvedAnchors() {
        assertTrue(resolver.resolve(0, emptyList(), dimensions).isEmpty())
        assertTrue(resolver.resolve(4, emptyList(), dimensions).isEmpty())
    }

    private fun observation(text: String, centerX: Int, centerY: Int) =
        LobbyOcrAnchorObservation(
            text = text,
            boundingBox = RawOcrBoundingBox(centerX - 10, centerY - 10, centerX + 10, centerY + 10),
            level = LobbyOcrAnchorLevel.ELEMENT,
        )

    private fun assertPixels(
        crop: com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ) {
        assertEquals(left, crop.left * dimensions.width, 1.5)
        assertEquals(top, crop.top * dimensions.height, 1.5)
        assertEquals(right, crop.right * dimensions.width, 1.5)
        assertEquals(bottom, crop.bottom * dimensions.height, 1.5)
    }
}
