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

class SyncFinalizedMatchesUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: FinalizedMatchCloudSyncRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : FinalizedMatchCloudSyncAction, FinalizedMatchCloudSyncRetryAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> = record(
        result = executeForRetry(tournamentId),
        id = tournamentId,
    )

    override suspend fun executeForRetry(
        tournamentId: String,
    ): FinalizedMatchCloudSyncResult {
        val authenticated = try {
            authRepository.observeAuthState().first() is AuthState.SignedIn
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (!authenticated) return FinalizedMatchCloudSyncResult.AuthenticationRequired

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return FinalizedMatchCloudSyncResult.ValidationFailure
            val matches = tournamentRepository.observeMatchesByTournamentId(tournamentId).first()
            FinalizedMatchCloudSyncSnapshot(
                tournament = tournament,
                matches = matches,
                expectedCloudRevision = tournamentRepository
                    .readLocalRevisionState(tournamentId)
                    .expectedRevisionForWrite(),
                ocrEvidence = matches
                    .filter { it.status == MatchStatus.FINALIZED }
                    .mapNotNull { match -> tournamentRepository.readPreservedMatchOcrEvidence(match.id) },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return FinalizedMatchCloudSyncResult.ValidationFailure
        }

        val result = cloudSyncRepository.sync(snapshot)
        result.confirmedCloudRevision()?.let { cloudRevision ->
            tournamentRepository.confirmCloudRevision(tournamentId, cloudRevision)
        }
        return result
    }
    private suspend fun record(
        result: FinalizedMatchCloudSyncResult,
        id: String,
    ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            operation = SyncQueueOperationType.FINALIZED_MATCH_SYNC,
            tournamentId = id,
            status = result.queueStatus(),
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )
}
private fun FinalizedMatchCloudSyncResult.queueStatus() = when (this) { is FinalizedMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED; FinalizedMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; FinalizedMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; FinalizedMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; FinalizedMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; is FinalizedMatchCloudSyncResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT; is FinalizedMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN }
private fun FinalizedMatchCloudSyncResult.queueFailureCategory(): String? = (this as? FinalizedMatchCloudSyncResult.Conflict)?.conflict?.queueFailureCategory()

private fun FinalizedMatchCloudSyncResult.confirmedCloudRevision(): Int? = when (this) {
    is FinalizedMatchCloudSyncResult.Success -> confirmedCloudRevision
    is FinalizedMatchCloudSyncResult.Conflict -> confirmedCloudRevision
    is FinalizedMatchCloudSyncResult.PartialFailure -> confirmedCloudRevision
    else -> null
}?.takeIf { it > 0 }
