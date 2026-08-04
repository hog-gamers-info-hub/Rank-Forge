package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRetryAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRetryAction
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRetryAction

class QueueOperationRetryExecutor(
    private val tournamentUpload: TournamentCloudUploadRetryAction,
    private val tournamentRestoration: TournamentCloudRestorationRetryAction,
    private val draftMatchSync: DraftMatchCloudSyncRetryAction,
    private val finalizedMatchSync: FinalizedMatchCloudSyncRetryAction,
    private val matchRestoration: MatchCloudRestorationRetryAction,
    private val rosterReplacement: TournamentRosterCloudReplacementRetryAction,
) : SyncQueueEntryRetryExecutor {
    override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome {
        val tournamentId = entry.tournamentId ?: return SyncQueueRetryOutcome.Failure(
            status = SyncQueueStatus.FAILED_VALIDATION,
            failureCategory = SyncQueueStatus.FAILED_VALIDATION.name,
        )
        return when (entry.operationType) {
            SyncQueueOperationType.TOURNAMENT_UPLOAD -> tournamentUpload.executeForRetry(tournamentId).toRetryOutcome()
            SyncQueueOperationType.TOURNAMENT_RESTORATION -> tournamentRestoration.executeForRetry(tournamentId).toRetryOutcome()
            SyncQueueOperationType.DRAFT_MATCH_SYNC -> draftMatchSync.executeForRetry(tournamentId).toRetryOutcome()
            SyncQueueOperationType.FINALIZED_MATCH_SYNC -> finalizedMatchSync.executeForRetry(tournamentId).toRetryOutcome()
            SyncQueueOperationType.MATCH_RESTORATION -> matchRestoration.executeForRetry(tournamentId).toRetryOutcome()
            SyncQueueOperationType.ROSTER_REPLACEMENT -> rosterReplacement.executeForRetry(tournamentId).toRetryOutcome()
        }
    }
}

private fun retryOutcome(status: SyncQueueStatus): SyncQueueRetryOutcome = when (status) {
    SyncQueueStatus.COMPLETED -> SyncQueueRetryOutcome.Success
    else -> SyncQueueRetryOutcome.Failure(status, status.name)
}

private fun TournamentCloudUploadResult.toRetryOutcome(): SyncQueueRetryOutcome = when (this) {
    TournamentCloudUploadResult.Success -> retryOutcome(SyncQueueStatus.COMPLETED)
    TournamentCloudUploadResult.AuthenticationRequired -> retryOutcome(SyncQueueStatus.BLOCKED_AUTHENTICATION)
    TournamentCloudUploadResult.NetworkFailure -> retryOutcome(SyncQueueStatus.BLOCKED_NETWORK)
    TournamentCloudUploadResult.ValidationFailure -> retryOutcome(SyncQueueStatus.FAILED_VALIDATION)
    TournamentCloudUploadResult.AuthorizationFailure -> retryOutcome(SyncQueueStatus.FAILED_AUTHORIZATION)
    is TournamentCloudUploadResult.Conflict -> SyncQueueRetryOutcome.Failure(SyncQueueStatus.FAILED_CONFLICT, conflict.queueFailureCategory())
    is TournamentCloudUploadResult.PartialFailure -> retryOutcome(SyncQueueStatus.FAILED_UNKNOWN)
}

private fun TournamentCloudRestorationResult.toRetryOutcome(): SyncQueueRetryOutcome = when (this) {
    is TournamentCloudRestorationResult.Success,
    is TournamentCloudRestorationResult.Available,
    -> retryOutcome(SyncQueueStatus.COMPLETED)
    TournamentCloudRestorationResult.AuthenticationRequired -> retryOutcome(SyncQueueStatus.BLOCKED_AUTHENTICATION)
    TournamentCloudRestorationResult.NetworkFailure -> retryOutcome(SyncQueueStatus.BLOCKED_NETWORK)
    TournamentCloudRestorationResult.ValidationFailure -> retryOutcome(SyncQueueStatus.FAILED_VALIDATION)
    TournamentCloudRestorationResult.AuthorizationFailure -> retryOutcome(SyncQueueStatus.FAILED_AUTHORIZATION)
    TournamentCloudRestorationResult.LocalTransactionFailure -> retryOutcome(SyncQueueStatus.FAILED_LOCAL)
    is TournamentCloudRestorationResult.Conflict -> SyncQueueRetryOutcome.Failure(SyncQueueStatus.FAILED_CONFLICT, conflict.queueFailureCategory())
}

private fun DraftMatchCloudSyncResult.toRetryOutcome(): SyncQueueRetryOutcome = when (this) {
    DraftMatchCloudSyncResult.Success -> retryOutcome(SyncQueueStatus.COMPLETED)
    DraftMatchCloudSyncResult.AuthenticationRequired -> retryOutcome(SyncQueueStatus.BLOCKED_AUTHENTICATION)
    DraftMatchCloudSyncResult.NetworkFailure -> retryOutcome(SyncQueueStatus.BLOCKED_NETWORK)
    DraftMatchCloudSyncResult.ValidationFailure -> retryOutcome(SyncQueueStatus.FAILED_VALIDATION)
    DraftMatchCloudSyncResult.AuthorizationFailure -> retryOutcome(SyncQueueStatus.FAILED_AUTHORIZATION)
    is DraftMatchCloudSyncResult.Conflict -> SyncQueueRetryOutcome.Failure(SyncQueueStatus.FAILED_CONFLICT, conflict.queueFailureCategory())
    is DraftMatchCloudSyncResult.PartialFailure -> retryOutcome(SyncQueueStatus.FAILED_UNKNOWN)
}

private fun FinalizedMatchCloudSyncResult.toRetryOutcome(): SyncQueueRetryOutcome = when (this) {
    is FinalizedMatchCloudSyncResult.Success -> retryOutcome(SyncQueueStatus.COMPLETED)
    FinalizedMatchCloudSyncResult.AuthenticationRequired -> retryOutcome(SyncQueueStatus.BLOCKED_AUTHENTICATION)
    FinalizedMatchCloudSyncResult.NetworkFailure -> retryOutcome(SyncQueueStatus.BLOCKED_NETWORK)
    FinalizedMatchCloudSyncResult.ValidationFailure -> retryOutcome(SyncQueueStatus.FAILED_VALIDATION)
    FinalizedMatchCloudSyncResult.AuthorizationFailure -> retryOutcome(SyncQueueStatus.FAILED_AUTHORIZATION)
    is FinalizedMatchCloudSyncResult.Conflict -> SyncQueueRetryOutcome.Failure(SyncQueueStatus.FAILED_CONFLICT, conflict.queueFailureCategory())
    is FinalizedMatchCloudSyncResult.PartialFailure -> retryOutcome(SyncQueueStatus.FAILED_UNKNOWN)
}

private fun MatchCloudRestorationResult.toRetryOutcome(): SyncQueueRetryOutcome = when (this) {
    MatchCloudRestorationResult.Success,
    MatchCloudRestorationResult.NoCloudMatches,
    -> retryOutcome(SyncQueueStatus.COMPLETED)
    MatchCloudRestorationResult.AuthenticationRequired -> retryOutcome(SyncQueueStatus.BLOCKED_AUTHENTICATION)
    MatchCloudRestorationResult.NetworkFailure -> retryOutcome(SyncQueueStatus.BLOCKED_NETWORK)
    MatchCloudRestorationResult.ValidationFailure -> retryOutcome(SyncQueueStatus.FAILED_VALIDATION)
    MatchCloudRestorationResult.AuthorizationFailure -> retryOutcome(SyncQueueStatus.FAILED_AUTHORIZATION)
    MatchCloudRestorationResult.LocalTransactionFailure -> retryOutcome(SyncQueueStatus.FAILED_LOCAL)
    is MatchCloudRestorationResult.Conflict -> SyncQueueRetryOutcome.Failure(SyncQueueStatus.FAILED_CONFLICT, conflict.queueFailureCategory())
}

private fun TournamentRosterCloudReplacementResult.toRetryOutcome(): SyncQueueRetryOutcome = when (this) {
    is TournamentRosterCloudReplacementResult.Success -> retryOutcome(SyncQueueStatus.COMPLETED)
    TournamentRosterCloudReplacementResult.AuthenticationRequired -> retryOutcome(SyncQueueStatus.BLOCKED_AUTHENTICATION)
    TournamentRosterCloudReplacementResult.NetworkFailure -> retryOutcome(SyncQueueStatus.BLOCKED_NETWORK)
    TournamentRosterCloudReplacementResult.ValidationFailure -> retryOutcome(SyncQueueStatus.FAILED_VALIDATION)
    TournamentRosterCloudReplacementResult.BlockedByExistingMatches -> SyncQueueRetryOutcome.Failure(
        SyncQueueStatus.FAILED_VALIDATION,
        "ROSTER_REPLACEMENT_BLOCKED_BY_MATCHES",
    )
    TournamentRosterCloudReplacementResult.AuthorizationFailure -> retryOutcome(SyncQueueStatus.FAILED_AUTHORIZATION)
    is TournamentRosterCloudReplacementResult.Conflict -> SyncQueueRetryOutcome.Failure(
        SyncQueueStatus.FAILED_CONFLICT,
        conflict.queueFailureCategory(),
    )
    TournamentRosterCloudReplacementResult.UnknownFailure -> retryOutcome(SyncQueueStatus.FAILED_UNKNOWN)
}
