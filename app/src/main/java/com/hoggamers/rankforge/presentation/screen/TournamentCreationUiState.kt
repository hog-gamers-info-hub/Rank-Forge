package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentValidationError

enum class TournamentCreationSubmissionError {
    UNKNOWN,
}

sealed interface TournamentCreationNavigation {
    data object Back : TournamentCreationNavigation

    data class Created(
        val tournamentId: String,
    ) : TournamentCreationNavigation
}

data class TournamentCreationUiState(
    val tournamentName: String = "",
    val tournamentDate: LocalDate? = null,
    val organizerName: String = "",
    val organizerContactNumber: String = "",
    val validationErrors: Map<TournamentField, TournamentValidationError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val submissionError: TournamentCreationSubmissionError? = null,
    val showDiscardDialog: Boolean = false,
    val navigation: TournamentCreationNavigation? = null,
) {
    val isDirty: Boolean
        get() = tournamentName.isNotEmpty() ||
            tournamentDate != null ||
            organizerName.isNotEmpty() ||
            organizerContactNumber.isNotEmpty()
}
