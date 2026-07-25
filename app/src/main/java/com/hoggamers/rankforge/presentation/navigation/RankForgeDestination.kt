package com.hoggamers.rankforge.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TournamentListDestination

@Serializable
data object TournamentCreationDestination

@Serializable
data class TournamentDetailsDestination(
    val tournamentId: String,
)

@Serializable
data class TeamEntryDestination(
    val tournamentId: String,
    val focusSlotNumber: Int? = null,
)

@Serializable
data class RosterEntryDestination(
    val tournamentId: String,
    val slotNumber: Int,
)

@Serializable
data class RosterReviewDestination(
    val tournamentId: String,
)

@Serializable
data class MatchCreationDestination(
    val tournamentId: String,
)
