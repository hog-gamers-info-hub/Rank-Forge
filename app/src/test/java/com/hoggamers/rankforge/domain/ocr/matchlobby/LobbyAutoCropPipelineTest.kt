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
    fun shotOneWithOnlyRightColumnTwoAndFourProducesRatioAssistedProposal() {
        val observations = listOf(
            observation("2", 1076, 236),
            observation("4", 1076, 441),
        )

        val resolved = resolver.resolve(1, observations, dimensions)
        assertEquals(listOf(2, 4), resolved.map { it.anchor.slotNumber })

        val grid = reconstructedGrid(1, resolved)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.TOP_LEFT).source)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT).source)
        assertTrue(calculate(1, observations) is LobbyAutoCropCalculationResult.Proposal)
        assertTrue(calculateContent(observations) is LobbyAutoCropCalculationResult.Proposal)
    }

    @Test
    fun shotOneWithOnlyTopRowOneAndTwoProducesRatioAssistedProposal() {
        val observations = listOf(
            observation("1", 585, 236),
            observation("2", 1076, 236),
        )

        val resolved = resolver.resolve(1, observations, dimensions)
        assertEquals(listOf(1, 2), resolved.map { it.anchor.slotNumber })

        val grid = reconstructedGrid(1, resolved)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT).source)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.BOTTOM_RIGHT).source)
        assertTrue(calculateContent(observations) is LobbyAutoCropCalculationResult.Proposal)
    }

    @Test
    fun shotOneWithOnlyDiagonalOneAndFourProducesDirectGeometryProposal() {
        val observations = listOf(
            observation("1", 585, 236),
            observation("4", 1076, 441),
        )

        val resolved = resolver.resolve(1, observations, dimensions)
        assertEquals(listOf(1, 4), resolved.map { it.anchor.slotNumber })

        val grid = reconstructedGrid(1, resolved)
        assertEquals(491.0, grid.columnPitch, 0.0)
        assertEquals(205.0, grid.rowPitch, 0.0)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.TOP_RIGHT).source)
        assertEquals(LobbyGridPointSource.INFERRED, grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT).source)
        assertTrue(calculateContent(observations) is LobbyAutoCropCalculationResult.Proposal)
    }

    @Test
    fun ambiguousPhysicalTwoAnchorPairsInsideOneGroupProduceNoProposal() {
        val observations = listOf(
            observation("1", 585, 236),
            observation("1", 700, 236),
            observation("2", 1076, 236),
        )

        assertTrue(resolver.resolve(1, observations, dimensions).isEmpty())
        assertEquals(null, calculateContent(observations))
    }

    @Test
    fun twoDifferentScreenshotGroupsWithTwoAnchorEvidenceProduceNoProposal() {
        val observations = listOf(
            observation("1", 585, 236),
            observation("2", 1076, 236),
            observation("5", 585, 236),
            observation("6", 1076, 236),
        )

        val candidates = reconstructedCandidates(observations)
        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.directlyObservedAnchorCount == 2 })
        assertEquals(null, LobbyAutoCropGroupSelector.select(candidates))
        assertEquals(null, calculateContent(observations))
    }

    @Test
    fun threeAnchorGroupOutranksASeparateTwoAnchorGroup() {
        val observations = listOf(
            observation("1", 585, 236),
            observation("2", 1076, 236),
            observation("5", 585, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        val candidates = reconstructedCandidates(observations)
        val selected = LobbyAutoCropGroupSelector.select(candidates)

        assertEquals(2, selected?.grid?.screenshotIndex)
        assertEquals(3, selected?.directlyObservedAnchorCount)
        assertTrue(calculateContent(observations) is LobbyAutoCropCalculationResult.Proposal)
    }

    @Test
    fun shotTwoInfersSlotSixFromThreeDirectAnchorsAndProducesProposal() {
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
    fun shotThreeDirectFourAnchorsProduceProposalWithoutRatioFallback() {
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
    fun falseNumberOutsideCoherentFourAnchorStructureDoesNotChangeProposal() {
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
    fun equallyCredibleThreeAnchorGroupsStillProduceNoProposal() {
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

    @Test
    fun shuffledStrongEvidenceProducesIdenticalFinalCrop() {
        val observations = listOf(
            observation("5", 585, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        assertEquals(
            calculateContent(observations),
            calculateContent(observations.reversed()),
        )
    }

    private fun calculate(
        screenshotIndex: Int,
        observations: List<LobbyOcrAnchorObservation>,
    ): LobbyAutoCropCalculationResult? {
        val resolved = resolver.resolve(screenshotIndex, observations, dimensions)
        val grid = (reconstructor.reconstruct(
            screenshotIndex = screenshotIndex,
            observedAnchors = resolved.map { it.anchor },
        ) as? LobbyGridReconstructionResult.Reconstructed)
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
        val selected = LobbyAutoCropGroupSelector.select(
            reconstructedCandidates(observations),
        ) ?: return null

        return calculator.calculate(
            grid = selected.grid,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )
    }

    private fun reconstructedCandidates(
        observations: List<LobbyOcrAnchorObservation>,
    ): List<LobbyAutoCropGridCandidate> =
        resolver.resolveAll(observations, dimensions).mapNotNull { resolved ->
            val grid = (reconstructor.reconstruct(
                screenshotIndex = resolved.screenshotIndex,
                observedAnchors = resolved.anchors.map { it.anchor },
            ) as? LobbyGridReconstructionResult.Reconstructed)?.grid
                ?: return@mapNotNull null

            LobbyAutoCropGridCandidate(
                grid = grid,
                directlyObservedAnchorCount = resolved.directlyObservedAnchorCount,
                alignmentError = resolved.alignmentError,
            )
        }

    private fun reconstructedGrid(
        screenshotIndex: Int,
        resolved: List<LobbyResolvedOcrAnchor>,
    ): LobbySlotGrid =
        (reconstructor.reconstruct(
            screenshotIndex = screenshotIndex,
            observedAnchors = resolved.map { it.anchor },
        ) as LobbyGridReconstructionResult.Reconstructed).grid

    private fun observation(
        text: String,
        centerX: Int,
        centerY: Int,
    ) = LobbyOcrAnchorObservation(
        text = text,
        boundingBox = RawOcrBoundingBox(
            centerX - 10,
            centerY - 10,
            centerX + 10,
            centerY + 10,
        ),
        level = LobbyOcrAnchorLevel.ELEMENT,
    )
}
