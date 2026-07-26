package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError

enum class MatchReviewNavigation {
    PLACEMENTS,
    KILLS,
    DETAILS,
}

data class MatchReviewUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val matchNumber: Int? = null,
    val rows: List<MatchReviewRowUiState> = emptyList(),
    val validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
    val navigation: MatchReviewNavigation? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val isValid: Boolean
        get() = isAvailable && validationErrors.isEmpty()
}

data class MatchReviewRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val playerNames: List<String> = emptyList(),
    val placementInput: String = "",
    val killsInput: String = "",
    val validationErrors: Set<MatchResultValidationError> = emptySet(),
)
