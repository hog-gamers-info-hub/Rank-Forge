package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.expectedRevisionForWrite
import com.hoggamers.rankforge.domain.sync.queueFailureCategory

class SyncDraftMatchesUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: DraftMatchCloudSyncRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : DraftMatchCloudSyncAction, DraftMatchCloudSyncRetryAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> = record(
        result = executeForRetry(tournamentId),
        id = tournamentId,
    )

    override suspend fun executeForRetry(
        tournamentId: String,
    ): DraftMatchCloudSyncResult {
        val authenticated = try {
            authRepository.observeAuthState().first() is AuthState.SignedIn
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (!authenticated) return DraftMatchCloudSyncResult.AuthenticationRequired

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return DraftMatchCloudSyncResult.ValidationFailure
            DraftMatchCloudSyncSnapshot(
                tournament = tournament,
                matches = tournamentRepository.observeMatchesByTournamentId(tournamentId).first(),
                expectedCloudRevision = tournamentRepository
                    .readLocalRevisionState(tournamentId)
                    .expectedRevisionForWrite(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DraftMatchCloudSyncResult.ValidationFailure
        }

        val result = cloudSyncRepository.sync(snapshot).withConflictContext(snapshot)
        if (result == DraftMatchCloudSyncResult.Success) {
            snapshot.expectedCloudRevision?.let { expected ->
                tournamentRepository.confirmCloudRevision(tournamentId, expected + 1)
            }
        }
        return result
    }

    private suspend fun record(
        result: DraftMatchCloudSyncResult,
        id: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            operation = SyncQueueOperationType.DRAFT_MATCH_SYNC,
            tournamentId = id,
            status = result.queueStatus(),
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )
}

private fun DraftMatchCloudSyncResult.withConflictContext(
    snapshot: DraftMatchCloudSyncSnapshot,
): DraftMatchCloudSyncResult = when (this) {
    is DraftMatchCloudSyncResult.Conflict -> copy(
        context = context ?: snapshot.toConflictContext(conflict),
    )
    else -> this
}

private fun DraftMatchCloudSyncSnapshot.toConflictContext(
    conflict: com.hoggamers.rankforge.domain.sync.RevisionConflict,
): ConflictResolutionContext {
    val localState = expectedCloudRevision?.let { value -> com.hoggamers.rankforge.domain.sync.CloudRevision(value) }
    val current = when (conflict) {
        is com.hoggamers.rankforge.domain.sync.RevisionConflict.StaleWrite -> conflict.currentCloudRevision
        is com.hoggamers.rankforge.domain.sync.RevisionConflict.LocalCloudDivergence -> conflict.cloudRevision
        com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision -> null
    }
    val draftsOnly = matches.isNotEmpty() && matches.all { it.status == MatchStatus.DRAFT }
    return ConflictResolutionContext(
        tournamentId = tournament.id,
        operation = ConflictOperation.DRAFT_MATCH_SYNC,
        conflict = conflict,
        resolvability = if (draftsOnly && current != null) {
            ConflictResolvability.DRAFT_RESOLVABLE
        } else {
            ConflictResolvability.FINALIZED_OR_UNSUPPORTED
        },
        localDraftMatches = matches.filter { it.status == MatchStatus.DRAFT },
        localRevision = null,
        baseCloudRevision = localState,
        currentCloudRevision = current,
    )
}
private fun DraftMatchCloudSyncResult.queueStatus() = when (this) { DraftMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED; DraftMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; DraftMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; DraftMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; DraftMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; is DraftMatchCloudSyncResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT; is DraftMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN }
private fun DraftMatchCloudSyncResult.queueFailureCategory(): String? = (this as? DraftMatchCloudSyncResult.Conflict)?.conflict?.queueFailureCategory()
