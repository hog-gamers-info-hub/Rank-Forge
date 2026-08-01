package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.TournamentStatus

sealed interface RosterReviewNavigation {
    data class TournamentDetails(
        val tournamentId: String,
    ) : RosterReviewNavigation
}

data class RosterReviewUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val status: TournamentStatus = TournamentStatus.DRAFT,
    val teams: List<RosterReviewTeamUiState> = emptyList(),
    val validationIssues: List<RosterValidationIssueUiState> = emptyList(),
    val isConfirming: Boolean = false,
    val hasConfirmError: Boolean = false,
    val navigation: RosterReviewNavigation? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val isConfirmed: Boolean
        get() = status == TournamentStatus.CONFIRMED

    val canConfirm: Boolean
        get() = isAvailable && !isConfirmed && !isConfirming && validationIssues.isEmpty()
}

data class RosterReviewTeamUiState(
    val slotNumber: Int,
    val teamName: String,
    val players: List<RosterReviewPlayerUiState>,
)

data class RosterReviewPlayerUiState(
    val playerIndex: Int,
    val displayName: String,
)
