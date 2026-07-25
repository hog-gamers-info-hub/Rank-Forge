package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate

const val MAX_MATCHES_PER_TOURNAMENT = 10

data class Match(
    val id: String,
    val tournamentId: String,
    val matchNumber: Int,
    val date: LocalDate,
    val mapName: String,
    val status: MatchStatus,
    val placements: List<MatchPlacement> = emptyList(),
)

data class MatchPlacement(
    val teamSlotNumber: Int,
    val position: Int,
)

enum class MatchStatus {
    DRAFT,
}

