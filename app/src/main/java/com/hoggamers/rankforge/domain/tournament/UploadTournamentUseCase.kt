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

class UploadTournamentUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudUploadRepository: TournamentCloudUploadRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : TournamentCloudUploadAction {
    override suspend operator fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudUploadResult> {
        val authState = try {
            authRepository.observeAuthState().first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return record(TournamentCloudUploadResult.AuthenticationRequired, tournamentId)
        }

        val ownerId = (authState as? AuthState.SignedIn)?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return record(TournamentCloudUploadResult.AuthenticationRequired, tournamentId)

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return record(TournamentCloudUploadResult.ValidationFailure, tournamentId)
            TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = tournamentRepository.observeSlotsByTournamentId(tournamentId).first(),
                rosters = tournamentRepository.observeRosterByTournamentId(tournamentId).first(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return record(TournamentCloudUploadResult.ValidationFailure, tournamentId)
        }

        return record(cloudUploadRepository.upload(snapshot, ownerId), tournamentId)
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
        ),
    )
}

private fun TournamentCloudUploadResult.queueStatus() = when (this) {
    TournamentCloudUploadResult.Success -> SyncQueueStatus.COMPLETED
    TournamentCloudUploadResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
    TournamentCloudUploadResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
    TournamentCloudUploadResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION
    TournamentCloudUploadResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
    is TournamentCloudUploadResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN
}
