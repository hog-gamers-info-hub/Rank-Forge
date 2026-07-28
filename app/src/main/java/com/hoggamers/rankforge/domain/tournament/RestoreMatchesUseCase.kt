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

class RestoreMatchesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudRepository: MatchCloudRestorationRepository,
    private val localRepository: MatchRestorationLocalRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
) : MatchCloudRestorationAction, MatchCloudRestorationRetryAction {
    override suspend fun invoke(
        tournamentId: String,
    ): QueueAwareActionResult<MatchCloudRestorationResult> = record(
        result = executeForRetry(tournamentId),
        id = tournamentId,
    )

    override suspend fun executeForRetry(
        tournamentId: String,
    ): MatchCloudRestorationResult {
        if (!isAuthenticated()) return MatchCloudRestorationResult.AuthenticationRequired
        return when (val result = cloudRepository.readOwnedMatches(tournamentId)) {
            is MatchCloudRestorationRemoteResult.Failure -> result.toDomainResult()
            is MatchCloudRestorationRemoteResult.Success -> {
                if (result.value.matches.isEmpty()) return MatchCloudRestorationResult.NoCloudMatches
                try {
                    localRepository.replaceMatches(result.value)
                    MatchCloudRestorationResult.Success
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    MatchCloudRestorationResult.LocalTransactionFailure
                }
            }
        }
    }
    private suspend fun record(
        result: MatchCloudRestorationResult,
        id: String,
    ): QueueAwareActionResult<MatchCloudRestorationResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            operation = SyncQueueOperationType.MATCH_RESTORATION,
            tournamentId = id,
            status = result.queueStatus(),
        ),
    )

    private suspend fun isAuthenticated() = try {
        authRepository.observeAuthState().first() is AuthState.SignedIn
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) { false }
}

private fun MatchCloudRestorationResult.queueStatus() = when (this) { MatchCloudRestorationResult.Success, MatchCloudRestorationResult.NoCloudMatches -> SyncQueueStatus.COMPLETED; MatchCloudRestorationResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; MatchCloudRestorationResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; MatchCloudRestorationResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; MatchCloudRestorationResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; MatchCloudRestorationResult.LocalTransactionFailure -> SyncQueueStatus.FAILED_LOCAL }

private fun MatchCloudRestorationRemoteResult.Failure.toDomainResult() = when (category) {
    MatchCloudRestorationFailureCategory.AUTHENTICATION -> MatchCloudRestorationResult.AuthenticationRequired
    MatchCloudRestorationFailureCategory.AUTHORIZATION -> MatchCloudRestorationResult.AuthorizationFailure
    MatchCloudRestorationFailureCategory.NETWORK -> MatchCloudRestorationResult.NetworkFailure
    MatchCloudRestorationFailureCategory.VALIDATION -> MatchCloudRestorationResult.ValidationFailure
}
