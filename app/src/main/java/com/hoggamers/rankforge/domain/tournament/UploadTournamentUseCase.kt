package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
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
    ): QueueAwareActionResult<TournamentCloudUploadResult> {
        val ownerUserId = currentOwnerUserId()
            ?: return QueueAwareActionResult(TournamentCloudUploadResult.AuthenticationRequired, QueueRecordingResult.NOT_REQUIRED)
        if (!hasOwnedTournament(tournamentId, ownerUserId)) {
            return QueueAwareActionResult(TournamentCloudUploadResult.ValidationFailure, QueueRecordingResult.NOT_REQUIRED)
        }
        val result = executeForRetry(tournamentId, ownerUserId)
        if (result == TournamentCloudUploadResult.TournamentLimitReached) {
            return QueueAwareActionResult(
                primaryResult = result,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        }
        return record(result = result, tournamentId = tournamentId, ownerUserId = ownerUserId)
    }

    override suspend fun executeForRetry(
        tournamentId: String,
    ): TournamentCloudUploadResult = currentOwnerUserId()?.let { ownerUserId ->
        executeForRetry(tournamentId, ownerUserId)
    } ?: TournamentCloudUploadResult.AuthenticationRequired

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentCloudUploadResult {
        if (currentOwnerUserId() != expectedOwnerUserId) {
            return TournamentCloudUploadResult.AuthorizationFailure
        }
        if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, expectedOwnerUserId)) {
            return TournamentCloudUploadResult.ValidationFailure
        }

        val snapshot = try {
            val tournament = tournamentRepository.observeByIdAndOwner(tournamentId, expectedOwnerUserId).first()
                ?: return TournamentCloudUploadResult.ValidationFailure
            TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = tournamentRepository.observeSlotsByTournamentIdAndOwner(tournamentId, expectedOwnerUserId).first(),
                rosters = tournamentRepository.observeRosterByTournamentIdAndOwner(tournamentId, expectedOwnerUserId).first(),
                expectedCloudRevision = tournamentRepository
                    .readLocalRevisionState(tournamentId)
                    .expectedRevisionForWrite(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentCloudUploadResult.ValidationFailure
        }

        if (currentOwnerUserId() != expectedOwnerUserId) return TournamentCloudUploadResult.AuthorizationFailure
        val result = cloudUploadRepository.upload(snapshot, expectedOwnerUserId)
        if (result is TournamentCloudUploadResult.Success) {
            tournamentRepository.confirmCloudRevisionByOwner(tournamentId, expectedOwnerUserId, result.confirmedCloudRevision)
        }
        return result
    }

    private suspend fun record(
        result: TournamentCloudUploadResult,
        tournamentId: String,
        ownerUserId: String,
    ): QueueAwareActionResult<TournamentCloudUploadResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            ownerUserId = ownerUserId,
            operation = SyncQueueOperationType.TOURNAMENT_UPLOAD,
            tournamentId = tournamentId,
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

private fun TournamentCloudUploadResult.queueStatus() = when (this) {
    is TournamentCloudUploadResult.Success -> SyncQueueStatus.COMPLETED
    TournamentCloudUploadResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
    TournamentCloudUploadResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
    TournamentCloudUploadResult.TournamentLimitReached -> SyncQueueStatus.FAILED_VALIDATION
    TournamentCloudUploadResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION
    TournamentCloudUploadResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
    is TournamentCloudUploadResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
    is TournamentCloudUploadResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN
}

private fun TournamentCloudUploadResult.queueFailureCategory(): String? =
    when (this) {
        TournamentCloudUploadResult.TournamentLimitReached -> "TOURNAMENT_LIMIT_REACHED"
        else -> (this as? TournamentCloudUploadResult.Conflict)?.conflict?.queueFailureCategory()
    }
