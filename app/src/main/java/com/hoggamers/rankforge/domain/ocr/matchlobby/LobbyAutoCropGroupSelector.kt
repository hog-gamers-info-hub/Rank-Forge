package com.hoggamers.rankforge.domain.ocr.matchlobby

data class LobbyAutoCropGridCandidate(
    val grid: LobbySlotGrid,
    val directlyObservedAnchorCount: Int,
    val alignmentError: Double,
)

object LobbyAutoCropGroupSelector {
    /**
     * Selection hierarchy:
     *
     * 1. Prefer the candidate group with the most directly observed OCR anchors.
     *
     * 2. If the strongest evidence is 3 or 4 anchors, preserve the existing
     *    alignment-based selection behavior.
     *
     * 3. If the strongest evidence is exactly 2 anchors, do NOT use alignment
     *    error to guess between screenshot groups. Two-anchor geometry may be
     *    ratio-assisted or diagonal and is accepted only when exactly one group
     *    survives as a reconstructable candidate.
     *
     * This complements the per-group physical-pair ambiguity gate in
     * LobbyOcrAnchorResolver.
     */
    fun select(candidates: List<LobbyAutoCropGridCandidate>): LobbyAutoCropGridCandidate? {
        if (candidates.isEmpty()) return null

        val mostObserved = candidates.maxOf { it.directlyObservedAnchorCount }
        val observationWinners = candidates.filter {
            it.directlyObservedAnchorCount == mostObserved
        }

        if (mostObserved == TWO_ANCHOR_COUNT) {
            return observationWinners.singleOrNull()
        }

        val bestAlignment = observationWinners.minOf { it.alignmentError }
        val alignmentWinners = observationWinners.filter {
            it.alignmentError == bestAlignment
        }
        return alignmentWinners.singleOrNull()
    }

    private const val TWO_ANCHOR_COUNT = 2
}
