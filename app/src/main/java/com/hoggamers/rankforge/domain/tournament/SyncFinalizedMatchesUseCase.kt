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

class SyncFinalizedMatchesUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: FinalizedMatchCloudSyncRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : FinalizedMatchCloudSyncAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
        val authenticated = try {
            authRepository.observeAuthState().first() is AuthState.SignedIn
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (!authenticated) return record(FinalizedMatchCloudSyncResult.AuthenticationRequired, tournamentId)

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return record(FinalizedMatchCloudSyncResult.ValidationFailure, tournamentId)
            FinalizedMatchCloudSyncSnapshot(
                tournament = tournament,
                matches = tournamentRepository.observeMatchesByTournamentId(tournamentId).first(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return record(FinalizedMatchCloudSyncResult.ValidationFailure, tournamentId)
        }

        return record(cloudSyncRepository.sync(snapshot), tournamentId)
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
        ),
    )
}
private fun FinalizedMatchCloudSyncResult.queueStatus() = when (this) { FinalizedMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED; FinalizedMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; FinalizedMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; FinalizedMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; FinalizedMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; is FinalizedMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN }
