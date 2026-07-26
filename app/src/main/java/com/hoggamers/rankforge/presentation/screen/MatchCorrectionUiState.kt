package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.MatchCorrectionGlobalError
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError

enum class MatchCorrectionNavigation {
    REVIEW,
    DETAILS,
}

data class MatchCorrectionUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val matchNumber: Int? = null,
    val rows: List<MatchCorrectionRowUiState> = emptyList(),
    val validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
    val globalError: MatchCorrectionGlobalError? = null,
    val isSubmitting: Boolean = false,
    val navigation: MatchCorrectionNavigation? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val isValid: Boolean
        get() = isAvailable && validationErrors.isEmpty()

    val canSubmit: Boolean
        get() = isValid && !isSubmitting
}

data class MatchCorrectionRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val playerNames: List<String> = emptyList(),
    val previousPlacement: String,
    val previousKills: String,
    val placementInput: String,
    val killsInput: String,
    val validationErrors: Set<MatchResultValidationError> = emptySet(),
)
