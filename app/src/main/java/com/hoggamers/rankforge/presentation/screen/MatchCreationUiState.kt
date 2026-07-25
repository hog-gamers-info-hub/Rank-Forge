package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.MatchField
import com.hoggamers.rankforge.domain.tournament.MatchValidationError

enum class MatchCreationSubmissionError {
    UNKNOWN,
}

enum class MatchCreationNavigation {
    BACK,
    CREATED,
}

data class MatchCreationUiState(
    val tournamentId: String? = null,
    val matchNumber: String = "",
    val matchDate: LocalDate? = null,
    val mapName: String = "",
    val validationErrors: Map<MatchField, MatchValidationError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val submissionError: MatchCreationSubmissionError? = null,
    val navigation: MatchCreationNavigation? = null,
) {
    val isDirty: Boolean
        get() = matchNumber.isNotEmpty() || matchDate != null || mapName.isNotEmpty()
}

