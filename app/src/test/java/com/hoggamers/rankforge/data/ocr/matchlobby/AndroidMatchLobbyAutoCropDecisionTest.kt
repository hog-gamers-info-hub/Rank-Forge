package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyAutoCropCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyOcrAnchorLevel
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyOcrAnchorObservation
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyOcrAnchorResolver
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridReconstructor
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidMatchLobbyAutoCropDecisionTest {
    private val dimensions = OcrImageDimensions(1600, 720)
    private val resolver = LobbyOcrAnchorResolver()
    private val reconstructor = LobbySlotGridReconstructor()
    private val calculator = LobbyAutoCropCalculator()

    @Test
    fun validFourAnchorEvidenceProducesProposalWithoutRetry() {
        assertEquals(
            AutoCropDecision.Proposed::class,
            decision(
                listOf(
                    observation("1", 300, 100),
                    observation("2", 600, 100),
                    observation("3", 300, 200),
                    observation("4", 600, 200),
                ),
            )::class,
        )
    }

    @Test
    fun validThreeAndTwoAnchorEvidenceProduceProposalWithoutRetry() {
        assertEquals(
            AutoCropDecision.Proposed::class,
            decision(
                listOf(
                    observation("2", 600, 100),
                    observation("3", 300, 200),
                    observation("4", 600, 200),
                ),
            )::class,
        )
        assertEquals(
            AutoCropDecision.Proposed::class,
            decision(
                listOf(
                    observation("1", 585, 236),
                    observation("2", 1076, 236),
                ),
            )::class,
        )
    }

    @Test
    fun invalidTwoAnchorEvidenceRequestsOneRetry() {
        assertEquals(
            AutoCropDecision.RetryWithEnhancement,
            decision(
                listOf(
                    observation("1", 300, 100),
                    observation("4", 900, 300),
                ),
            ),
        )
    }

    @Test
    fun invalidStrongEvidenceRequestsRetryForThreeOrFourCandidates() {
        assertEquals(
            AutoCropDecision.RetryWithEnhancement,
            decision(
                listOf(
                    observation("1", 300, 100),
                    observation("2", 600, 130),
                    observation("3", 300, 200),
                    observation("4", 600, 211),
                ),
            ),
        )
    }

    @Test
    fun zeroOrOnePotentialAnchorDoesNotRequestRetry() {
        assertEquals(
            AutoCropDecision.NoProposal,
            decision(emptyList()),
        )
        assertEquals(
            AutoCropDecision.NoProposal,
            decision(listOf(observation("1", 300, 100))),
        )
    }

    @Test
    fun ambiguousGroupsRequestRetryButDoNotSelectAGroup() {
        assertEquals(
            AutoCropDecision.RetryWithEnhancement,
            decision(
                listOf(
                    observation("1", 300, 100),
                    observation("2", 700, 100),
                    observation("5", 300, 100),
                    observation("6", 700, 100),
                ),
            ),
        )
    }

    private fun decision(observations: List<LobbyOcrAnchorObservation>): AutoCropDecision =
        calculateLobbyAutoCropDecision(
            observations = observations,
            dimensions = dimensions,
            anchorResolver = resolver,
            gridReconstructor = reconstructor,
            cropCalculator = calculator,
        )

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
