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

class RestoreTournamentUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudRepository: TournamentCloudRestorationRepository,
    private val localRepository: TournamentRestorationLocalRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : TournamentCloudRestorationAction {
    override suspend fun loadAvailable(): TournamentCloudRestorationResult {
        if (!isAuthenticated()) return TournamentCloudRestorationResult.AuthenticationRequired
        return cloudRepository.listOwnedTournaments().toResult()
    }

    override suspend fun restore(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudRestorationResult> {
        if (!isAuthenticated()) return record(TournamentCloudRestorationResult.AuthenticationRequired, tournamentId)
        return when (val result = cloudRepository.readOwnedTournament(tournamentId)) {
            is TournamentCloudRestorationRemoteResult.Failure -> record(result.toDomainResult(), tournamentId)
            is TournamentCloudRestorationRemoteResult.Success -> {
                try {
                    localRepository.restore(result.value)
                    record(TournamentCloudRestorationResult.Success(result.value.tournament.name), tournamentId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    record(TournamentCloudRestorationResult.LocalTransactionFailure, tournamentId)
                }
            }
        }
    }

    private suspend fun record(
        result: TournamentCloudRestorationResult,
        id: String,
    ): QueueAwareActionResult<TournamentCloudRestorationResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            operation = SyncQueueOperationType.TOURNAMENT_RESTORATION,
            tournamentId = id,
            status = result.queueStatus(),
        ),
    )

    private suspend fun isAuthenticated(): Boolean = try {
        authRepository.observeAuthState().first() is AuthState.SignedIn
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}

private fun TournamentCloudRestorationResult.queueStatus() = when (this) { is TournamentCloudRestorationResult.Success -> SyncQueueStatus.COMPLETED; is TournamentCloudRestorationResult.Available -> SyncQueueStatus.COMPLETED; TournamentCloudRestorationResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; TournamentCloudRestorationResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; TournamentCloudRestorationResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; TournamentCloudRestorationResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; TournamentCloudRestorationResult.LocalTransactionFailure -> SyncQueueStatus.FAILED_LOCAL }

private fun TournamentCloudRestorationRemoteResult.Failure.toDomainResult(): TournamentCloudRestorationResult = when (category) {
    TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
        TournamentCloudRestorationResult.AuthenticationRequired
    TournamentCloudRestorationFailureCategory.AUTHORIZATION ->
        TournamentCloudRestorationResult.AuthorizationFailure
    TournamentCloudRestorationFailureCategory.NETWORK ->
        TournamentCloudRestorationResult.NetworkFailure
    TournamentCloudRestorationFailureCategory.VALIDATION ->
        TournamentCloudRestorationResult.ValidationFailure
}

private fun TournamentCloudRestorationRemoteResult<List<TournamentCloudRestorationSummary>>.toResult(): TournamentCloudRestorationResult = when (this) {
    is TournamentCloudRestorationRemoteResult.Success ->
        TournamentCloudRestorationResult.Available(value)
    is TournamentCloudRestorationRemoteResult.Failure -> toDomainResult()
}
