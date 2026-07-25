package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.RosterPlayer

data class RosterEntryUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val slotNumber: Int? = null,
    val teamName: String = "",
    val players: List<RosterPlayerUiState> = emptyList(),
    val isSaving: Boolean = false,
    val hasSaveError: Boolean = false,
    val validationIssues: List<RosterValidationIssueUiState> = emptyList(),
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val playerCount: Int
        get() = players.size

    val isIncomplete: Boolean
        get() = playerCount < MIN_COMPLETE_PLAYERS

    val isAtMaximum: Boolean
        get() = playerCount >= RosterPlayer.MAX_PLAYERS

    val canAddPlayer: Boolean
        get() = !isSaving && !isAtMaximum

    companion object {
        const val MIN_COMPLETE_PLAYERS = 4
    }
}

data class RosterPlayerUiState(
    val displayName: String,
)

fun List<RosterPlayer>.toRosterPlayerUiState(): List<RosterPlayerUiState> = map { player ->
    RosterPlayerUiState(displayName = player.displayName)
}
