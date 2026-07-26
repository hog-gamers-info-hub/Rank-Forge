package com.hoggamers.rankforge.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data class TournamentStandingsDestination(
    val tournamentId: String,
)
