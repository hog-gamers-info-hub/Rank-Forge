package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyPpPlayerCandidateSelectorTest {
    @Test
    fun oneRegionIsSelectedEvenWhenConfidenceIsLower() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(region(4, "maybe", 0.21f, 0, 0, 80, 20)),
        )

        assertEquals("maybe", result.candidateText)
        assertEquals(listOf(4), result.selectedRegionIndices)
        assertEquals(LobbyPpPlayerCandidateSelectionStatus.SINGLE_REGION, result.status)
    }

    @Test
    fun emptyRegionsAreIgnored() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(
                region(0, "", 0.99f, 0, 0, 20, 20),
                region(1, "  ", 0.99f, 25, 0, 45, 20),
            ),
        )

        assertNull(result.candidateText)
        assertEquals(emptyList<Int>(), result.selectedRegionIndices)
        assertEquals(LobbyPpPlayerCandidateSelectionStatus.EMPTY, result.status)
    }

    @Test
    fun lowConfidenceNoiseDoesNotContaminateStrongCandidate() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(
                region(0, "τÜä", 0.127f, 0, 0, 30, 20),
                region(1, "FH-ADI", 0.999f, 35, 0, 103, 20),
            ),
        )

        assertEquals("FH-ADI", result.candidateText)
        assertEquals(listOf(1), result.selectedRegionIndices)
        assertEquals(LobbyPpPlayerCandidateSelectionStatus.MULTI_REGION_SELECTED, result.status)
    }

    @Test
    fun rawRegionIndicesRemainAvailableForDiscardedAndSelectedEvidence() {
        val regions = listOf(
            region(7, "noise", 0.1f, 0, 0, 25, 20),
            region(8, "PLAYER", 0.99f, 30, 0, 100, 20),
        )

        val result = LobbyPpPlayerCandidateSelector.select(regions)

        assertEquals(listOf(7, 8), regions.map { it.index })
        assertEquals(listOf(8), result.selectedRegionIndices)
    }

    @Test
    fun adjacentHighConfidenceFragmentsMergeLeftToRight() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(
                region(3, "FE.", 0.98f, 20, 10, 45, 30),
                region(4, "PHANTOM", 0.97f, 48, 11, 120, 30),
            ),
        )

        assertEquals("FE. PHANTOM", result.candidateText)
        assertEquals(listOf(3, 4), result.selectedRegionIndices)
        assertEquals(LobbyPpPlayerCandidateSelectionStatus.MULTI_REGION_MERGED, result.status)
    }

    @Test
    fun distantDetectionsDoNotConcatenate() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(
                region(1, "PLAYER", 0.91f, 10, 0, 80, 20),
                region(2, "2U5T", 0.90f, 220, 0, 260, 20),
            ),
        )

        assertEquals("PLAYER", result.candidateText)
        assertEquals(listOf(1), result.selectedRegionIndices)
    }

    @Test
    fun regionIndexKeepsTextPairedAfterInputReordering() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(
                region(8, "RIGHT", 0.95f, 50, 0, 90, 20),
                region(2, "LEFT", 0.95f, 10, 0, 45, 20),
            ),
        )

        assertEquals("LEFT RIGHT", result.candidateText)
        assertEquals(listOf(2, 8), result.selectedRegionIndices)
    }

    @Test
    fun onlyRegionBelowCompetitionThresholdIsPreserved() {
        val result = LobbyPpPlayerCandidateSelector.select(
            listOf(region(5, "LOW", 0.3f, 0, 0, 45, 20)),
        )

        assertEquals("LOW", result.candidateText)
    }

    private fun region(
        index: Int,
        text: String,
        confidence: Float,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = LobbyPpPlayerTextRegion(
        index = index,
        bounds = RawOcrBoundingBox(left, top, right, bottom),
        text = text,
        confidence = confidence,
    )
}
