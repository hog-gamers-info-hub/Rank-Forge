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
import com.hoggamers.rankforge.domain.sync.queueFailureCategory

class RestoreMatchesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudRepository: MatchCloudRestorationRepository,
    private val localRepository: MatchRestorationLocalRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
    private val matchScreenshotRestorationAction: MatchScreenshotRestorationAction =
        NoOpMatchScreenshotRestorationAction,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
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
        if (deletionIntentRepository.isBlocking(tournamentId)) {
            return MatchCloudRestorationResult.ValidationFailure
        }
        return when (val result = cloudRepository.readOwnedMatches(tournamentId)) {
            is MatchCloudRestorationRemoteResult.Failure -> result.toDomainResult()
            is MatchCloudRestorationRemoteResult.Success -> {
                val cloudRevision = result.value.cloudRevision
                    ?: return MatchCloudRestorationResult.Conflict(
                        com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision,
                    )
                localRepository.detectMatchDivergence(tournamentId, cloudRevision)?.let { conflict ->
                    return MatchCloudRestorationResult.Conflict(
                        conflict = conflict,
                        context = ConflictResolutionContext(
                            tournamentId = tournamentId,
                            operation = ConflictOperation.MATCH_RESTORATION,
                            conflict = conflict,
                            resolvability = ConflictResolvability.FINALIZED_OR_UNSUPPORTED,
                            cloudDraftMatches = result.value.matches.filter { it.status == MatchStatus.DRAFT },
                            currentCloudRevision = cloudRevision,
                        ),
                    )
                }
                if (result.value.matches.isEmpty()) return MatchCloudRestorationResult.NoCloudMatches
                try {
                    localRepository.replaceMatches(result.value)
                    when (
                        val screenshotResult = matchScreenshotRestorationAction(
                            tournamentId = tournamentId,
                            restoredMatchIds = result.value.matches.map { it.id }.toSet(),
                        )
                    ) {
                        MatchCloudRestorationResult.Success,
                        MatchCloudRestorationResult.NoCloudMatches,
                        -> MatchCloudRestorationResult.Success
                        else -> screenshotResult
                    }
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
            failureCategory = result.queueFailureCategory() ?: result.queueStatus().name,
        ),
    )

    private suspend fun isAuthenticated() = try {
        authRepository.observeAuthState().first() is AuthState.SignedIn
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) { false }
}

private fun MatchCloudRestorationResult.queueStatus() = when (this) { MatchCloudRestorationResult.Success, MatchCloudRestorationResult.NoCloudMatches -> SyncQueueStatus.COMPLETED; MatchCloudRestorationResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; MatchCloudRestorationResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; MatchCloudRestorationResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; MatchCloudRestorationResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; MatchCloudRestorationResult.LocalTransactionFailure -> SyncQueueStatus.FAILED_LOCAL; is MatchCloudRestorationResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT }
private fun MatchCloudRestorationResult.queueFailureCategory(): String? = (this as? MatchCloudRestorationResult.Conflict)?.conflict?.queueFailureCategory()

private fun MatchCloudRestorationRemoteResult.Failure.toDomainResult() = when (category) {
    MatchCloudRestorationFailureCategory.AUTHENTICATION -> MatchCloudRestorationResult.AuthenticationRequired
    MatchCloudRestorationFailureCategory.AUTHORIZATION -> MatchCloudRestorationResult.AuthorizationFailure
    MatchCloudRestorationFailureCategory.NETWORK -> MatchCloudRestorationResult.NetworkFailure
    MatchCloudRestorationFailureCategory.VALIDATION -> MatchCloudRestorationResult.ValidationFailure
}
