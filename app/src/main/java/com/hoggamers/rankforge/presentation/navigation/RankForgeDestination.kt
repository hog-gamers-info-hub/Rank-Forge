package com.hoggamers.rankforge.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TournamentListDestination

@Serializable
data object AllTournamentsDestination

@Serializable
data object AuthDestination

@Serializable
data object TournamentCreationDestination

@Serializable
data class TournamentDetailsDestination(
    val tournamentId: String,
)

@Serializable
data class DraftConflictResolutionDestination(
    val tournamentId: String,
    val currentCloudRevision: Int,
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
data class RosterScreenshotCropDestination(
    val tournamentId: String,
    val screenshotIndex: Int,
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
data class MatchResultScreenshotCropDestination(
    val tournamentId: String,
    val matchId: String,
    val screenshotRole: String,
)

@Serializable
data class MatchLobbyScreenshotCropDestination(
    val tournamentId: String,
    val matchId: String,
    val lobbyScreenshotIndex: Int,
)

@Serializable
data class MatchOcrReviewDestination(
    val tournamentId: String,
    val matchId: String,
)

@Serializable
data class MatchCorrectionDestination(
    val tournamentId: String,
    val matchId: String,
)
