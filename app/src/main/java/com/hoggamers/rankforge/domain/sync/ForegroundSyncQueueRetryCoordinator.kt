package com.hoggamers.rankforge.domain.sync

class SyncQueueRetryEligibilityPolicy {
    fun isEligible(entry: SyncQueueEntry, hasAuthenticatedSession: Boolean): Boolean =
        isRetryable(entry.status, hasAuthenticatedSession)

    fun isRetryable(status: SyncQueueStatus, hasAuthenticatedSession: Boolean): Boolean = when (status) {
        SyncQueueStatus.BLOCKED_NETWORK -> true
        SyncQueueStatus.BLOCKED_AUTHENTICATION -> hasAuthenticatedSession
        SyncQueueStatus.PENDING,
        SyncQueueStatus.COMPLETED,
        SyncQueueStatus.FAILED_VALIDATION,
        SyncQueueStatus.FAILED_AUTHORIZATION,
        SyncQueueStatus.FAILED_LOCAL,
        SyncQueueStatus.FAILED_UNKNOWN,
        -> false
    }
}

interface SyncQueueEntryRetryExecutor {
    suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome
}

sealed interface SyncQueueRetryOutcome {
    data object Success : SyncQueueRetryOutcome
    data class Failure(
        val status: SyncQueueStatus,
        val failureCategory: String?,
    ) : SyncQueueRetryOutcome
}

class ForegroundSyncQueueRetryCoordinator(
    private val repository: PersistentSyncQueueRepository,
    private val executor: SyncQueueEntryRetryExecutor,
    private val eligibilityPolicy: SyncQueueRetryEligibilityPolicy = SyncQueueRetryEligibilityPolicy(),
) {
    suspend fun retryEligible(
        entries: Iterable<SyncQueueEntry>,
        hasAuthenticatedSession: Boolean,
    ): List<SyncQueueEntry> = entries.filter {
        eligibilityPolicy.isEligible(it, hasAuthenticatedSession)
    }.map { entry ->
        repository.incrementAttemptCount(entry.id)
        val attemptedEntry = entry.copy(attemptCount = entry.attemptCount + 1)
        when (val outcome = executor.execute(attemptedEntry)) {
            SyncQueueRetryOutcome.Success -> repository.markCompleted(entry.id)
            is SyncQueueRetryOutcome.Failure -> repository.updateRetryFailure(
                id = entry.id,
                status = outcome.status,
                failureCategory = outcome.failureCategory,
            )
        }
        attemptedEntry
    }
}
