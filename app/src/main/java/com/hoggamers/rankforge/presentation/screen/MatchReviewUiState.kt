package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord

enum class MatchReviewNavigation {
    PLACEMENTS,
    KILLS,
    CORRECTION,
    DETAILS,
}

data class MatchReviewUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val matchNumber: Int? = null,
    val status: MatchStatus = MatchStatus.DRAFT,
    val rows: List<MatchReviewRowUiState> = emptyList(),
    val correctionHistory: List<MatchCorrectionRecord> = emptyList(),
    val validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
    val navigation: MatchReviewNavigation? = null,
    val isFinalizing: Boolean = false,
    val finalizationError: FinalizeMatchGlobalError? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val isValid: Boolean
        get() = isAvailable && validationErrors.isEmpty()

    val isEditable: Boolean
        get() = isAvailable && status == MatchStatus.DRAFT
}

data class MatchReviewRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val playerNames: List<String> = emptyList(),
    val placementInput: String = "",
    val killsInput: String = "",
    val validationErrors: Set<MatchResultValidationError> = emptySet(),
)
