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
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) : FinalizedMatchCloudSyncAction, FinalizedMatchCloudSyncRetryAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
        val ownerUserId = currentOwnerUserId()
            ?: return QueueAwareActionResult(FinalizedMatchCloudSyncResult.AuthenticationRequired, com.hoggamers.rankforge.domain.sync.QueueRecordingResult.NOT_REQUIRED)
        if (!hasOwnedTournament(tournamentId, ownerUserId)) {
            return QueueAwareActionResult(FinalizedMatchCloudSyncResult.ValidationFailure, com.hoggamers.rankforge.domain.sync.QueueRecordingResult.NOT_REQUIRED)
        }
        val result = executeForRetry(tournamentId, ownerUserId)
        return record(result, tournamentId, ownerUserId)
    }

    override suspend fun executeForRetry(
        tournamentId: String,
    ): FinalizedMatchCloudSyncResult = currentOwnerUserId()?.let { ownerUserId ->
        executeForRetry(tournamentId, ownerUserId)
    } ?: FinalizedMatchCloudSyncResult.AuthenticationRequired

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): FinalizedMatchCloudSyncResult {
        if (currentOwnerUserId() != expectedOwnerUserId) return FinalizedMatchCloudSyncResult.AuthorizationFailure
        if (deletionIntentRepository.isBlocking(tournamentId)) {
            return FinalizedMatchCloudSyncResult.ValidationFailure
        }

        val snapshot = try {
            val tournament = tournamentRepository.observeByIdAndOwner(tournamentId, expectedOwnerUserId).first()
                ?: return FinalizedMatchCloudSyncResult.ValidationFailure
            FinalizedMatchCloudSyncSnapshot(
                tournament = tournament,
                teamSlots = tournamentRepository.observeSlotsByTournamentIdAndOwner(tournamentId, expectedOwnerUserId).first(),
                matches = tournamentRepository.observeMatchesByTournamentIdAndOwner(tournamentId, expectedOwnerUserId).first(),
                expectedCloudRevision = tournamentRepository
                    .readLocalRevisionState(tournamentId)
                    .expectedRevisionForWrite(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return FinalizedMatchCloudSyncResult.ValidationFailure
        }

        if (currentOwnerUserId() != expectedOwnerUserId) return FinalizedMatchCloudSyncResult.AuthorizationFailure
        val result = cloudSyncRepository.sync(snapshot)
        result.confirmedCloudRevision()?.let { cloudRevision ->
            tournamentRepository.confirmCloudRevisionByOwner(tournamentId, expectedOwnerUserId, cloudRevision)
        }
        return result
    }
    private suspend fun record(
        result: FinalizedMatchCloudSyncResult,
        id: String,
        ownerUserId: String,
    ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            ownerUserId = ownerUserId,
            operation = SyncQueueOperationType.FINALIZED_MATCH_SYNC,
            tournamentId = id,
            status = result.queueStatus(),
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )

    private suspend fun currentOwnerUserId(): String? = try {
        (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun hasOwnedTournament(tournamentId: String, ownerUserId: String): Boolean =
        tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first() != null
}
private fun FinalizedMatchCloudSyncResult.queueStatus() = when (this) { is FinalizedMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED; FinalizedMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; FinalizedMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; FinalizedMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; FinalizedMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; is FinalizedMatchCloudSyncResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT; is FinalizedMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN }
private fun FinalizedMatchCloudSyncResult.queueFailureCategory(): String? = (this as? FinalizedMatchCloudSyncResult.Conflict)?.conflict?.queueFailureCategory()

private fun FinalizedMatchCloudSyncResult.confirmedCloudRevision(): Int? = when (this) {
    is FinalizedMatchCloudSyncResult.Success -> confirmedCloudRevision
    is FinalizedMatchCloudSyncResult.Conflict -> confirmedCloudRevision
    is FinalizedMatchCloudSyncResult.PartialFailure -> confirmedCloudRevision
    else -> null
}?.takeIf { it > 0 }
