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

class UploadTournamentUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudUploadRepository: TournamentCloudUploadRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) : TournamentCloudUploadAction, TournamentCloudUploadRetryAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudUploadResult> = record(
        result = executeForRetry(tournamentId),
        tournamentId = tournamentId,
    )

    override suspend fun executeForRetry(
        tournamentId: String,
    ): TournamentCloudUploadResult {
        val authState = try {
            authRepository.observeAuthState().first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentCloudUploadResult.AuthenticationRequired
        }

        val ownerId = (authState as? AuthState.SignedIn)?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return TournamentCloudUploadResult.AuthenticationRequired
        if (deletionIntentRepository.isBlocking(tournamentId)) {
            return TournamentCloudUploadResult.ValidationFailure
        }

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return TournamentCloudUploadResult.ValidationFailure
            TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = tournamentRepository.observeSlotsByTournamentId(tournamentId).first(),
                rosters = tournamentRepository.observeRosterByTournamentId(tournamentId).first(),
                expectedCloudRevision = tournamentRepository
                    .readLocalRevisionState(tournamentId)
                    .expectedRevisionForWrite(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentCloudUploadResult.ValidationFailure
        }

        val result = cloudUploadRepository.upload(snapshot, ownerId)
        if (result is TournamentCloudUploadResult.Success) {
            tournamentRepository.confirmCloudRevision(tournamentId, result.confirmedCloudRevision)
        }
        return result
    }

    private suspend fun record(
        result: TournamentCloudUploadResult,
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudUploadResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            operation = SyncQueueOperationType.TOURNAMENT_UPLOAD,
            tournamentId = tournamentId,
            status = result.queueStatus(),
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )
}

private fun TournamentCloudUploadResult.queueStatus() = when (this) {
    is TournamentCloudUploadResult.Success -> SyncQueueStatus.COMPLETED
    TournamentCloudUploadResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
    TournamentCloudUploadResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
    TournamentCloudUploadResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION
    TournamentCloudUploadResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
    is TournamentCloudUploadResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
    is TournamentCloudUploadResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN
}

private fun TournamentCloudUploadResult.queueFailureCategory(): String? =
    (this as? TournamentCloudUploadResult.Conflict)?.conflict?.queueFailureCategory()
