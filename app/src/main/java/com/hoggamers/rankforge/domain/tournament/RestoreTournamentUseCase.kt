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

class RestoreTournamentUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudRepository: TournamentCloudRestorationRepository,
    private val localRepository: TournamentRestorationLocalRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
    private val matchCloudRestorationAction: MatchCloudRestorationAction,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) : TournamentCloudRestorationAction, TournamentCloudRestorationRetryAction {
    override suspend fun loadAvailable(): TournamentCloudRestorationResult {
        if (currentOwnerUserId() == null) return TournamentCloudRestorationResult.AuthenticationRequired
        return cloudRepository.listOwnedTournaments().toResult()
    }

    override suspend fun restore(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudRestorationResult> {
        val ownerUserId = currentOwnerUserId()
            ?: return QueueAwareActionResult(TournamentCloudRestorationResult.AuthenticationRequired, com.hoggamers.rankforge.domain.sync.QueueRecordingResult.NOT_REQUIRED)
        return record(executeForRetry(tournamentId, ownerUserId), tournamentId, ownerUserId)
    }

    override suspend fun executeForRetry(
        tournamentId: String,
    ): TournamentCloudRestorationResult = currentOwnerUserId()?.let { ownerUserId ->
        executeForRetry(tournamentId, ownerUserId)
    } ?: TournamentCloudRestorationResult.AuthenticationRequired

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentCloudRestorationResult {
        if (currentOwnerUserId() != expectedOwnerUserId) return TournamentCloudRestorationResult.AuthorizationFailure
        if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, expectedOwnerUserId)) {
            return TournamentCloudRestorationResult.ValidationFailure
        }
        if (currentOwnerUserId() != expectedOwnerUserId) return TournamentCloudRestorationResult.AuthorizationFailure
        return when (val result = cloudRepository.readOwnedTournament(tournamentId)) {
            is TournamentCloudRestorationRemoteResult.Failure -> result.toDomainResult()
            is TournamentCloudRestorationRemoteResult.Success -> {
                val snapshot = result.value
                if (
                    snapshot.tournament.id != tournamentId ||
                    snapshot.tournament.ownerUserId.isNullOrBlank() ||
                    snapshot.tournament.ownerUserId != expectedOwnerUserId
                ) {
                    return TournamentCloudRestorationResult.ValidationFailure
                }
                val cloudRevision = result.value.cloudRevision
                    ?: return TournamentCloudRestorationResult.Conflict(
                        com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision,
                    )
                localRepository.detectTournamentDivergence(tournamentId, cloudRevision)?.let { conflict ->
                    return TournamentCloudRestorationResult.Conflict(conflict)
                }
                if (currentOwnerUserId() != expectedOwnerUserId) {
                    return TournamentCloudRestorationResult.AuthorizationFailure
                }
                try {
                    localRepository.restoreByOwner(snapshot, expectedOwnerUserId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: SecurityException) {
                    return TournamentCloudRestorationResult.AuthorizationFailure
                } catch (_: Throwable) {
                    return TournamentCloudRestorationResult.LocalTransactionFailure
                }
                if (currentOwnerUserId() != expectedOwnerUserId) {
                    return TournamentCloudRestorationResult.AuthorizationFailure
                }
                try {
                    matchCloudRestorationAction(tournamentId, expectedOwnerUserId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Child restoration owns its result and retry recording; parent success remains valid.
                }
                TournamentCloudRestorationResult.Success(result.value.tournament.name)
            }
        }
    }

    private suspend fun record(
        result: TournamentCloudRestorationResult,
        id: String,
        ownerUserId: String,
    ): QueueAwareActionResult<TournamentCloudRestorationResult> = QueueAwareActionResult(
        primaryResult = result,
        queueRecordingResult = queueRecorder.record(
            ownerUserId = ownerUserId,
            operation = SyncQueueOperationType.TOURNAMENT_RESTORATION,
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
}

private fun TournamentCloudRestorationResult.queueStatus() = when (this) { is TournamentCloudRestorationResult.Success -> SyncQueueStatus.COMPLETED; is TournamentCloudRestorationResult.Available -> SyncQueueStatus.COMPLETED; TournamentCloudRestorationResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION; TournamentCloudRestorationResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK; TournamentCloudRestorationResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION; TournamentCloudRestorationResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION; TournamentCloudRestorationResult.LocalTransactionFailure -> SyncQueueStatus.FAILED_LOCAL; is TournamentCloudRestorationResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT }
private fun TournamentCloudRestorationResult.queueFailureCategory(): String? = (this as? TournamentCloudRestorationResult.Conflict)?.conflict?.queueFailureCategory()

private fun TournamentCloudRestorationRemoteResult.Failure.toDomainResult(): TournamentCloudRestorationResult = when (category) {
    TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
        TournamentCloudRestorationResult.AuthenticationRequired
    TournamentCloudRestorationFailureCategory.AUTHORIZATION ->
        TournamentCloudRestorationResult.AuthorizationFailure
    TournamentCloudRestorationFailureCategory.NOT_FOUND ->
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
