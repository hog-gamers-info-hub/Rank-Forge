package com.hoggamers.rankforge.domain.ocr.matchlobby

data class LobbyAutoCropGridCandidate(
    val grid: LobbySlotGrid,
    val directlyObservedAnchorCount: Int,
    val alignmentError: Double,
)

object LobbyAutoCropGroupSelector {
    fun select(candidates: List<LobbyAutoCropGridCandidate>): LobbyAutoCropGridCandidate? {
        if (candidates.isEmpty()) return null
        val mostObserved = candidates.maxOf { it.directlyObservedAnchorCount }
        val observationWinners = candidates.filter {
            it.directlyObservedAnchorCount == mostObserved
        }
        val bestAlignment = observationWinners.minOf { it.alignmentError }
        val alignmentWinners = observationWinners.filter {
            it.alignmentError == bestAlignment
        }
        return alignmentWinners.singleOrNull()
    }
}
