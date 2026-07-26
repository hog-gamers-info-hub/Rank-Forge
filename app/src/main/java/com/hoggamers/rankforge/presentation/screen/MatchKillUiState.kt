package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.KillGlobalError
import com.hoggamers.rankforge.domain.tournament.KillValidationError

enum class MatchKillNavigation {
    BACK,
    SAVED,
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
)
