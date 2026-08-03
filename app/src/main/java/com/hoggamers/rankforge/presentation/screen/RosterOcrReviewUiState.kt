package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrEvidence
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrFailure
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult

sealed interface RosterOcrReviewUiState {
    data class LoadingTeamContext(
        val tournamentId: String,
    ) : RosterOcrReviewUiState

    data class ReadyToProcess(
        val tournamentId: String,
        val teamSlots: List<TeamSlot>,
        val processingFailure: RosterOcrReviewProcessingFailure? = null,
    ) : RosterOcrReviewUiState

    data class Processing(
        val tournamentId: String,
        val teamSlots: List<TeamSlot>,
    ) : RosterOcrReviewUiState

    data class Reviewing(
        val tournamentId: String,
        val teamSlots: List<TeamSlot>,
        val evidence: ProcessRosterOcrEvidence,
        val draft: RosterOcrReviewDraft,
        val confirmation: RosterOcrReviewConfirmationState = RosterOcrReviewConfirmationState.NotRequested,
        val localReplacement: RosterOcrLocalReplacementState = RosterOcrLocalReplacementState.Ready,
    ) : RosterOcrReviewUiState

    data class LocalReplacementCommitted(
        val tournamentId: String,
        val teamSlots: List<TeamSlot>,
        val evidence: ProcessRosterOcrEvidence,
        val draft: RosterOcrReviewDraft,
        val cloudSynchronization: RosterOcrCloudSynchronizationState,
    ) : RosterOcrReviewUiState

    data class Completed(
        val tournamentId: String,
        val teamSlots: List<TeamSlot>,
        val evidence: ProcessRosterOcrEvidence,
        val draft: RosterOcrReviewDraft,
        val cloudResult: QueueAwareActionResult<TournamentRosterCloudReplacementResult>,
    ) : RosterOcrReviewUiState

    data class Unavailable(
        val tournamentId: String?,
        val failure: RosterOcrReviewLoadFailure,
    ) : RosterOcrReviewUiState
}

enum class RosterOcrReviewLoadFailure {
    INVALID_TOURNAMENT_CONTEXT,
    TOURNAMENT_NOT_FOUND,
    INCOMPLETE_TEAM_CONTEXT,
    UNEXPECTED_FAILURE,
}

sealed interface RosterOcrReviewProcessingFailure {
    data class Controlled(val failure: ProcessRosterOcrFailure) : RosterOcrReviewProcessingFailure

    data class DraftCreation(
        val failure: RosterOcrReviewDraftCreationFailure,
    ) : RosterOcrReviewProcessingFailure

    data object UnexpectedFailure : RosterOcrReviewProcessingFailure
}

enum class RosterOcrReviewConfirmationState {
    NotRequested,
    Requested,
}

sealed interface RosterOcrLocalReplacementState {
    data object Ready : RosterOcrLocalReplacementState

    data object InProgress : RosterOcrLocalReplacementState

    data class Failed(val error: RosterOcrReviewLocalReplacementError) : RosterOcrLocalReplacementState
}

enum class RosterOcrReviewLocalReplacementError {
    DRAFT_BLOCKED,
    TOURNAMENT_NOT_FOUND,
    INVALID_CANDIDATE,
    BLOCKED_BY_EXISTING_MATCHES,
    UNEXPECTED_FAILURE,
}

sealed interface RosterOcrCloudSynchronizationState {
    data object InProgress : RosterOcrCloudSynchronizationState

    data object UnexpectedFailure : RosterOcrCloudSynchronizationState

    data class Failed(
        val result: QueueAwareActionResult<TournamentRosterCloudReplacementResult>,
    ) : RosterOcrCloudSynchronizationState
}
