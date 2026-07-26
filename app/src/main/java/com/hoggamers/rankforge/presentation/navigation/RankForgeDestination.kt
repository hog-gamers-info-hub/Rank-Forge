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

@Serializable
data class MatchPlacementDestination(
    val tournamentId: String,
    val matchId: String,
)

@Serializable
data class MatchKillDestination(
    val tournamentId: String,
    val matchId: String,
)

@Serializable
data class MatchReviewDestination(
    val tournamentId: String,
    val matchId: String,
)

@Serializable
data class MatchCorrectionDestination(
    val tournamentId: String,
    val matchId: String,
)
