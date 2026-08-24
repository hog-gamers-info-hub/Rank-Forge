package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.isSignedInAs
import java.util.concurrent.CancellationException

class SyncQueueRetryEligibilityPolicy {
    fun isEligible(entry: SyncQueueEntry, hasAuthenticatedSession: Boolean): Boolean =
        if (entry.status == SyncQueueStatus.FAILED_CONFLICT) {
            entry.failureCategory == RevisionConflict.MissingRevision.queueFailureCategory()
        } else {
            isRetryable(entry.status, hasAuthenticatedSession)
        }

    fun isRetryable(status: SyncQueueStatus, hasAuthenticatedSession: Boolean): Boolean = when (status) {
        SyncQueueStatus.BLOCKED_NETWORK -> true
        SyncQueueStatus.BLOCKED_AUTHENTICATION -> hasAuthenticatedSession
        SyncQueueStatus.PENDING,
        SyncQueueStatus.COMPLETED,
        SyncQueueStatus.FAILED_VALIDATION,
        SyncQueueStatus.FAILED_AUTHORIZATION,
        SyncQueueStatus.FAILED_LOCAL,
        SyncQueueStatus.FAILED_CONFLICT,
        SyncQueueStatus.FAILED_UNKNOWN,
        -> false
    }
}

interface SyncQueueEntryRetryExecutor {
    suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome
}

sealed interface SyncQueueRetryOutcome {
    /** The authentication identity changed; leave the selected row pending for its owner. */
    data object Skipped : SyncQueueRetryOutcome
    data object Success : SyncQueueRetryOutcome
    data class Failure(
        val status: SyncQueueStatus,
        val failureCategory: String?,
    ) : SyncQueueRetryOutcome
}

class ForegroundSyncQueueRetryCoordinator(
    private val repository: PersistentSyncQueueRepository,
    private val executor: SyncQueueEntryRetryExecutor,
    private val authRepository: AuthRepository,
    private val eligibilityPolicy: SyncQueueRetryEligibilityPolicy = SyncQueueRetryEligibilityPolicy(),
) {
    suspend fun retryEligible(
        entries: Iterable<SyncQueueEntry>,
        ownerUserId: String,
    ): List<SyncQueueEntry> = entries
        .filter { it.ownerUserId == ownerUserId }
        .filter { eligibilityPolicy.isEligible(it, hasAuthenticatedSession = true) }
        .groupBy { SyncOperationIdentity.from(it.operationType, it.tournamentId) }
        .values
        .map { duplicates ->
            duplicates.minWith(compareBy<SyncQueueEntry> { it.createdAtEpochMillis }.thenBy { it.id })
        }
        .map { entry ->
            if (!authRepository.isSignedInAs(ownerUserId)) return@map null
            val attemptedEntry = entry.copy(attemptCount = entry.attemptCount + 1)
            val outcome = try {
                executor.execute(attemptedEntry)
            } catch (cancellation: CancellationException) {
                if (authRepository.isSignedInAs(ownerUserId)) {
                    repository.incrementAttemptCountByOwner(entry.id, ownerUserId)
                }
                throw cancellation
            } catch (failure: Throwable) {
                if (authRepository.isSignedInAs(ownerUserId)) {
                    repository.incrementAttemptCountByOwner(entry.id, ownerUserId)
                }
                throw failure
            }
            if (!authRepository.isSignedInAs(ownerUserId) || outcome == SyncQueueRetryOutcome.Skipped) {
                return@map null
            }
            repository.incrementAttemptCountByOwner(entry.id, ownerUserId)
            when (outcome) {
                SyncQueueRetryOutcome.Skipped -> Unit
                SyncQueueRetryOutcome.Success -> repository.markCompletedByOwner(entry.id, ownerUserId)
                is SyncQueueRetryOutcome.Failure -> repository.updateRetryFailureByOwner(
                    id = entry.id,
                    ownerUserId = ownerUserId,
                    status = outcome.status,
                    failureCategory = outcome.failureCategory,
                )
            }
            attemptedEntry
        }
        .filterNotNull()
}
