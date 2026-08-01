package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.PlacementGlobalError
import com.hoggamers.rankforge.domain.tournament.PlacementValidationError

sealed interface MatchPlacementNavigation {
    data object Back : MatchPlacementNavigation

    data class Saved(
        val tournamentId: String,
        val matchId: String,
    ) : MatchPlacementNavigation
}

data class MatchPlacementUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val matchNumber: Int? = null,
    val rows: List<MatchPlacementRowUiState> = emptyList(),
    val validationErrors: Map<Int, PlacementValidationError> = emptyMap(),
    val globalError: PlacementGlobalError? = null,
    val isSubmitting: Boolean = false,
    val navigation: MatchPlacementNavigation? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val canSave: Boolean
        get() = isAvailable && !isSubmitting
}

data class MatchPlacementRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val placementInput: String,
    val playerNames: List<String> = emptyList(),
)
