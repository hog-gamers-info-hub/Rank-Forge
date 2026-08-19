package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyAutoCropPipelineTest {
    private val dimensions = OcrImageDimensions(1600, 720)
    private val resolver = LobbyOcrAnchorResolver()
    private val reconstructor = LobbySlotGridReconstructor()
    private val calculator = LobbyAutoCropCalculator()

    @Test
    fun shotOneWithOnlyAnchorsTwoAndFourProducesNoProposal() {
        val observations = listOf(
            observation("2", 1076, 232),
            observation("4", 1076, 437),
        )

        val resolved = resolver.resolve(1, observations, dimensions)
        assertEquals(listOf(2, 4), resolved.map { it.anchor.slotNumber })
        assertEquals(
            LobbyGridReconstructionResult.InsufficientAnchors,
            reconstructor.reconstruct(1, resolved.map { it.anchor }),
        )
        assertEquals(null, calculate(1, observations))
        assertEquals(null, calculateContent(observations))
    }

    @Test
    fun shotTwoInfersSlotSixAndProducesAProposal() {
        val observations = listOf(
            observation("5", 585, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        val resolved = resolver.resolve(2, observations, dimensions)
        assertEquals(listOf(5, 7, 8), resolved.map { it.anchor.slotNumber })
        val grid = reconstructedGrid(2, resolved)
        assertEquals(6, grid.pointFor(LobbySlotGridRole.TOP_RIGHT).slotNumber)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.TOP_RIGHT).source)
        assertTrue(calculate(2, observations) is LobbyAutoCropCalculationResult.Proposal)
    }

    @Test
    fun shotThreeDirectAnchorsProduceAProposal() {
        val observations = listOf(
            observation("9", 585, 250),
            observation("10", 1075, 250),
            observation("11", 584, 455),
            observation("12", 1074, 456),
        )

        val resolved = resolver.resolve(3, observations, dimensions)
        assertEquals(listOf(9, 10, 11, 12), resolved.map { it.anchor.slotNumber })
        val grid = reconstructedGrid(3, resolved)
        assertTrue(grid.points.all { it.source == LobbyGridPointSource.OBSERVED })
        assertTrue(calculate(3, observations) is LobbyAutoCropCalculationResult.Proposal)
    }

    @Test
    fun shotThreeContentProducesTheSameProposalWithoutStorageIndexInput() {
        val observations = listOf(
            observation("9", 585, 250),
            observation("10", 1075, 250),
            observation("11", 584, 455),
            observation("12", 1074, 456),
        )

        val proposalsForStorageButtons = (1..3).map { calculateContent(observations) }

        assertTrue(proposalsForStorageButtons.all { it is LobbyAutoCropCalculationResult.Proposal })
        assertEquals(proposalsForStorageButtons.first(), proposalsForStorageButtons[1])
        assertEquals(proposalsForStorageButtons.first(), proposalsForStorageButtons[2])
    }

    @Test
    fun shotTwoContentProducesAProposalWithoutStorageIndexInput() {
        val observations = listOf(
            observation("5", 585, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        val proposalsForStorageButtons = (1..3).map { calculateContent(observations) }

        assertTrue(proposalsForStorageButtons.all { it is LobbyAutoCropCalculationResult.Proposal })
        assertEquals(proposalsForStorageButtons.first(), proposalsForStorageButtons[1])
        assertEquals(proposalsForStorageButtons.first(), proposalsForStorageButtons[2])
    }

    @Test
    fun falseNumberOutsideCoherentStructureDoesNotChangeProposal() {
        val trueObservations = listOf(
            observation("5", 585, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
            observation("6", 1076, 245),
        )
        val withFalseCandidate = trueObservations + listOf(
            observation("6", 1400, 100),
            observation("1", 100, 100),
        )

        val baseline = calculateContent(trueObservations)
        val filtered = resolver.resolve(2, withFalseCandidate, dimensions)
        assertEquals(listOf(5, 6, 7, 8), filtered.map { it.anchor.slotNumber })
        assertEquals(1076.0, filtered[1].anchor.centerX, 0.0)
        assertEquals(245.0, filtered[1].anchor.centerY, 0.0)
        assertEquals(baseline, calculateContent(withFalseCandidate))
    }

    @Test
    fun shuffledObservationsProduceIdenticalFinalCrop() {
        val observations = listOf(
            observation("5", 585, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        assertEquals(calculateContent(observations), calculateContent(observations.reversed()))
    }

    @Test
    fun equallyCredibleGroupsProduceNoProposal() {
        val observations = listOf(
            observation("1", 585, 245),
            observation("2", 1076, 245),
            observation("3", 585, 451),
            observation("5", 585, 245),
            observation("6", 1076, 245),
            observation("7", 585, 451),
        )

        assertEquals(null, calculateContent(observations))
    }

    private fun calculate(
        screenshotIndex: Int,
        observations: List<LobbyOcrAnchorObservation>,
    ): LobbyAutoCropCalculationResult? {
        val resolved = resolver.resolve(screenshotIndex, observations, dimensions)
        val grid = (reconstructor.reconstruct(screenshotIndex, resolved.map { it.anchor })
            as? LobbyGridReconstructionResult.Reconstructed)
            ?.grid
            ?: return null
        return calculator.calculate(
            grid = grid,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )
    }

    private fun calculateContent(
        observations: List<LobbyOcrAnchorObservation>,
    ): LobbyAutoCropCalculationResult? {
        val candidates = resolver.resolveAll(observations, dimensions).mapNotNull { resolved ->
            val grid = (reconstructor.reconstruct(
                screenshotIndex = resolved.screenshotIndex,
                observedAnchors = resolved.anchors.map { it.anchor },
            ) as? LobbyGridReconstructionResult.Reconstructed)?.grid ?: return@mapNotNull null
            LobbyAutoCropGridCandidate(
                grid = grid,
                directlyObservedAnchorCount = resolved.directlyObservedAnchorCount,
                alignmentError = resolved.alignmentError,
            )
        }
        val selected = LobbyAutoCropGroupSelector.select(candidates) ?: return null
        return calculator.calculate(
            grid = selected.grid,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )
    }

    private fun reconstructedGrid(
        screenshotIndex: Int,
        resolved: List<LobbyResolvedOcrAnchor>,
    ): LobbySlotGrid = (reconstructor.reconstruct(screenshotIndex, resolved.map { it.anchor })
        as LobbyGridReconstructionResult.Reconstructed)
        .grid

    private fun observation(text: String, centerX: Int, centerY: Int) =
        LobbyOcrAnchorObservation(
            text = text,
            boundingBox = RawOcrBoundingBox(centerX - 10, centerY - 10, centerX + 10, centerY + 10),
            level = LobbyOcrAnchorLevel.ELEMENT,
        )
}
