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

class SyncDraftMatchesUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: DraftMatchCloudSyncRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : DraftMatchCloudSyncAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> {
        val authenticated = try {
            authRepository.observeAuthState().first() is AuthState.SignedIn
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (!authenticated) return record(DraftMatchCloudSyncResult.AuthenticationRequired, tournamentId)

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return record(DraftMatchCloudSyncResult.ValidationFailure, tournamentId)
            DraftMatchCloudSyncSnapshot(
                tournament = tournament,
                matches = tournamentRepository.observeMatchesByTournamentId(tournamentId).first(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return record(DraftMatchCloudSyncResult.ValidationFailure, tournamentId)
        }

        return record(cloudSyncRepository.sync(snapshot), tournamentId)
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
        ),
    )
}
private fun DraftMatchCloudSyncResult.queueStatus() = when (this) { DraftMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED; DraftMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; DraftMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; DraftMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; DraftMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; is DraftMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN }
