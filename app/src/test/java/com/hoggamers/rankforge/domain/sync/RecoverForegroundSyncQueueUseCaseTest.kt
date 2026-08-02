package com.hoggamers.rankforge.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverForegroundSyncQueueUseCaseTest {
    @Test fun authenticatedForegroundRecoveryInspectsQueueAndRetriesEligibleEntries() = runTest {
        val networkEntry = entry("network", SyncQueueStatus.BLOCKED_NETWORK)
        val authenticationEntry = entry(
            id = "authentication",
            status = SyncQueueStatus.BLOCKED_AUTHENTICATION,
            operationType = SyncQueueOperationType.DRAFT_MATCH_SYNC,
        )
        val repository = RecordingQueueRepository(listOf(networkEntry, authenticationEntry))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertEquals(listOf(networkEntry.id, authenticationEntry.id), executor.executedIds)
        assertEquals(1, repository.entries.first { it.id == networkEntry.id }.attemptCount)
        assertEquals(1, repository.entries.first { it.id == authenticationEntry.id }.attemptCount)
        assertTrue(repository.entries.all { it.status == SyncQueueStatus.COMPLETED })
    }

    @Test fun nonRetryablePersistedEntriesRemainUnchanged() = runTest {
        val entries = listOf(
            SyncQueueStatus.PENDING,
            SyncQueueStatus.FAILED_VALIDATION,
            SyncQueueStatus.FAILED_AUTHORIZATION,
            SyncQueueStatus.FAILED_LOCAL,
            SyncQueueStatus.FAILED_UNKNOWN,
            SyncQueueStatus.COMPLETED,
        ).map { entry(it.name, it) }
        val repository = RecordingQueueRepository(entries)
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertTrue(executor.executedIds.isEmpty())
        assertEquals(entries, repository.entries)
    }

    @Test fun queueInspectionFailureIsIsolatedFromForegroundSessionRecovery() = runTest {
        val repository = RecordingQueueRepository(emptyList(), failOnObserve = true)
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(
                repository,
                RecordingExecutor(SyncQueueRetryOutcome.Success),
            ),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertTrue(repository.entries.isEmpty())
    }

    @Test fun interruptedQueueRetryIsIsolatedAndLaterRecoveryCompletesSameEntry() = runTest {
        val entry = entry("recovery-entry", SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingQueueRepository(listOf(entry))
        val executor = InterruptingThenSuccessExecutor()
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertEquals(listOf(entry.id), executor.executedEntries.map { it.id })
        assertEquals(1, executor.executedEntries.single().attemptCount)
        assertEquals(1, repository.entries.size)
        assertEquals(entry.id, repository.entries.single().id)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.single().status)
        assertEquals(1, repository.entries.single().attemptCount)
        assertTrue(repository.completedIds.isEmpty())

        recovery.recoverAfterAuthenticatedSession()

        assertEquals(listOf(entry.id, entry.id), executor.executedEntries.map { it.id })
        assertEquals(listOf(1, 2), executor.executedEntries.map { it.attemptCount })
        assertEquals(entry.id, repository.entries.single().id)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
        assertEquals(2, repository.entries.single().attemptCount)
        assertEquals(listOf(entry.id), repository.completedIds)
        assertEquals(0, repository.enqueueCalls)
    }

    private fun entry(
        id: String,
        status: SyncQueueStatus,
        operationType: SyncQueueOperationType = SyncQueueOperationType.TOURNAMENT_UPLOAD,
    ) = SyncQueueEntry(
        id = id,
        operationType = operationType,
        tournamentId = "tournament-id",
        createdAtEpochMillis = 0,
        status = status,
        failureCategory = status.name,
        attemptCount = 0,
    )

    private class RecordingExecutor(
        private val outcome: SyncQueueRetryOutcome,
    ) : SyncQueueEntryRetryExecutor {
        val executedIds = mutableListOf<String>()
        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome = outcome.also {
            executedIds += entry.id
        }
    }

    private class InterruptingThenSuccessExecutor : SyncQueueEntryRetryExecutor {
        val executedEntries = mutableListOf<SyncQueueEntry>()
        private var executionCount = 0

        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome {
            executedEntries += entry
            executionCount += 1
            if (executionCount == 1) throw IllegalStateException("retry interrupted")
            return SyncQueueRetryOutcome.Success
        }
    }

    private class RecordingQueueRepository(
        initialEntries: List<SyncQueueEntry>,
        private val failOnObserve: Boolean = false,
    ) : PersistentSyncQueueRepository {
        val entries = initialEntries.toMutableList()
        val completedIds = mutableListOf<String>()
        var enqueueCalls = 0
        override fun observeAll(): Flow<List<SyncQueueEntry>> = if (failOnObserve) {
            flow { throw IllegalStateException("queue unavailable") }
        } else {
            flowOf(entries)
        }
        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry {
            enqueueCalls += 1
            error("not used")
        }
        override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
        override suspend fun incrementAttemptCount(id: String) {
            replace(id) { it.copy(attemptCount = it.attemptCount + 1) }
        }
        override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) {
            replace(id) { it.copy(status = status, failureCategory = failureCategory) }
        }
        override suspend fun markCompleted(id: String) {
            completedIds += id
            replace(id) { it.copy(status = SyncQueueStatus.COMPLETED, failureCategory = null) }
        }
        override suspend fun remove(id: String) = Unit
        private fun replace(id: String, transform: (SyncQueueEntry) -> SyncQueueEntry) {
            val index = entries.indexOfFirst { it.id == id }
            entries[index] = transform(entries[index])
        }
    }
}
