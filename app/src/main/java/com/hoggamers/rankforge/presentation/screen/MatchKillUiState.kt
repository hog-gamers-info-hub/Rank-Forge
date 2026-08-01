package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.KillGlobalError
import com.hoggamers.rankforge.domain.tournament.KillValidationError

sealed interface MatchKillNavigation {
    data object Back : MatchKillNavigation

    data class Saved(
        val tournamentId: String,
        val matchId: String,
    ) : MatchKillNavigation
}

data class MatchKillUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val matchNumber: Int? = null,
    val rows: List<MatchKillRowUiState> = emptyList(),
    val validationErrors: Map<Int, KillValidationError> = emptyMap(),
    val globalError: KillGlobalError? = null,
    val isSubmitting: Boolean = false,
    val navigation: MatchKillNavigation? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val canSave: Boolean
        get() = isAvailable && !isSubmitting
}

data class MatchKillRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val killsInput: String,
    val playerNames: List<String> = emptyList(),
)
