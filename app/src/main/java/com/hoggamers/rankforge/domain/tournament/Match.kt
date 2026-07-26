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
    val kills: List<MatchKill> = emptyList(),
    val correctionHistory: List<MatchCorrectionRecord> = emptyList(),
)

data class MatchPlacement(
    val teamSlotNumber: Int,
    val position: Int,
)

data class MatchKill(
    val teamSlotNumber: Int,
    val kills: Int,
)

data class MatchCorrectionRecord(
    val previousPlacements: List<MatchPlacement>,
    val previousKills: List<MatchKill>,
    val correctedPlacements: List<MatchPlacement>,
    val correctedKills: List<MatchKill>,
)

enum class MatchStatus {
    DRAFT,
    FINALIZED,
}

