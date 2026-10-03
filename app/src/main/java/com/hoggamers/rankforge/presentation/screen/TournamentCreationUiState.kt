package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentValidationError

enum class TournamentCreationSubmissionError {
    UNKNOWN,
    TOURNAMENT_LIMIT_REACHED,
    QUOTA_CHECK_FAILED,
    AUTHENTICATION_REQUIRED,
}

sealed interface TournamentCreationNavigation {
    data object Back : TournamentCreationNavigation

    data class Created(
        val tournamentId: String,
    ) : TournamentCreationNavigation
}

data class TournamentCreationUiState(
    val tournamentName: String = "",
    val stageName: String = "",
    val organizerContactNumber: String = "",
    val format: TournamentFormat = TournamentFormat.STANDARD,
    val groupCount: Int? = null,
    val selectedGroupPairings: List<GroupPairing> = emptyList(),
    val validationErrors: Map<TournamentField, TournamentValidationError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val submissionError: TournamentCreationSubmissionError? = null,
    val showDiscardDialog: Boolean = false,
    val navigation: TournamentCreationNavigation? = null,
) {
    val groupParticipationCounts: Map<TournamentGroup, Int>
        get() = if (format == TournamentFormat.GROUP_ROTATION) {
            TournamentGroup.entries
                .take(groupCount ?: 0)
                .associateWith { group ->
                    selectedGroupPairings.count { pairing ->
                        pairing.firstGroup == group || pairing.secondGroup == group
                    }
                }
        } else {
            emptyMap()
        }

    val hasZeroGroupParticipation: Boolean
        get() = groupParticipationCounts.values.any { count -> count == 0 }

    val hasUnevenGroupParticipation: Boolean
        get() = groupParticipationCounts.values.distinct().size > 1

    val isDirty: Boolean
        get() = tournamentName.isNotEmpty() ||
            stageName.isNotEmpty() ||
            organizerContactNumber.isNotEmpty() ||
            format != TournamentFormat.STANDARD
}
