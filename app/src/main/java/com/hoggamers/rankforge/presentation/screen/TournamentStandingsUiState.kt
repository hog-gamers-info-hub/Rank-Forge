package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.TieBreakStanding

data class TournamentStandingsUiState(
    val isLoading: Boolean = true,
    val rows: List<TournamentStandingRowUiState> = emptyList(),
)

data class TournamentStandingRowUiState(
    val displayOrder: Int,
    val teamSlotNumber: Int,
    val totalPoints: Int,
    val totalPositionPoints: Int,
    val totalKillPoints: Int,
    val firstPlaceFinishes: Int,
    val latestMatchPlacement: Int,
    val matchesIncluded: Int,
    val isCompleteTie: Boolean,
)

fun List<TieBreakStanding>.toTournamentStandingsUiState(): List<TournamentStandingRowUiState> =
    mapIndexed { index, tieBreakStanding ->
        val standing = tieBreakStanding.standing
        TournamentStandingRowUiState(
            displayOrder = index + 1,
            teamSlotNumber = standing.teamSlotNumber,
            totalPoints = standing.totalPoints,
            totalPositionPoints = standing.totalPositionPoints,
            totalKillPoints = standing.totalKillPoints,
            firstPlaceFinishes = standing.firstPlaceFinishes,
            latestMatchPlacement = standing.latestMatchPlacement,
            matchesIncluded = standing.matchesIncluded,
            isCompleteTie = tieBreakStanding.isCompleteTie,
        )
    }
