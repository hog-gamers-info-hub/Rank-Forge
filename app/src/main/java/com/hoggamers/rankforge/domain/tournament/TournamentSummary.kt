package com.hoggamers.rankforge.domain.tournament

data class TournamentSummary(
    val tournament: Tournament,
    val totalTeams: Int,
    val totalMatches: Int,
    val lastUpdatedEpochMillis: Long?,
)
