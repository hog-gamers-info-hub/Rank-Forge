package com.hoggamers.rankforge.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundSyncQueueRetryCoordinatorTest {
    private val policy = SyncQueueRetryEligibilityPolicy()

    @Test fun bothRetryableStatusesAreEligibleForEveryOperationTypeWhenSessionRequirementsAreMet() {
        SyncQueueOperationType.entries.forEach { operationType ->
            assertTrue(policy.isEligible(entry(operationType, SyncQueueStatus.BLOCKED_NETWORK), hasAuthenticatedSession = false))
            assertTrue(policy.isEligible(entry(operationType, SyncQueueStatus.BLOCKED_AUTHENTICATION), hasAuthenticatedSession = true))
        }
    }

    @Test fun blockedAuthenticationIsRetryableOnlyWithAuthenticatedSession() {
        val entry = entry(SyncQueueOperationType.TOURNAMENT_UPLOAD, SyncQueueStatus.BLOCKED_AUTHENTICATION)
        assertFalse(policy.isEligible(entry, hasAuthenticatedSession = false))
        assertTrue(policy.isEligible(entry, hasAuthenticatedSession = true))
    }

    @Test fun nonRetryableStatusesAreSkipped() = runTest {
        val repository = RecordingRepository()
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val entries = listOf(
            SyncQueueStatus.PENDING,
            SyncQueueStatus.BLOCKED_AUTHENTICATION,
            SyncQueueStatus.FAILED_VALIDATION,
            SyncQueueStatus.FAILED_AUTHORIZATION,
            SyncQueueStatus.FAILED_LOCAL,
            SyncQueueStatus.FAILED_UNKNOWN,
            SyncQueueStatus.COMPLETED,
        ).map { entry(SyncQueueOperationType.MATCH_RESTORATION, it) }

        val attempted = ForegroundSyncQueueRetryCoordinator(repository, executor).retryEligible(entries, hasAuthenticatedSession = false)

        assertTrue(attempted.isEmpty())
        assertTrue(repository.incrementedIds.isEmpty())
        assertTrue(executor.executedEntries.isEmpty())
    }

    @Test fun eligibleEntryIncrementsOnceDelegatesAndMarksCompletedWithoutRemovingEntry() = runTest {
        val entry = entry(SyncQueueOperationType.DRAFT_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK, attemptCount = 2)
        val repository = RecordingRepository(listOf(entry))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)

        val attempted = ForegroundSyncQueueRetryCoordinator(repository, executor).retryEligible(listOf(entry), hasAuthenticatedSession = false)

        assertEquals(listOf(entry.id), repository.incrementedIds)
        assertEquals(3, executor.executedEntries.single().attemptCount)
        assertEquals(listOf(entry.id), repository.completedIds)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
        assertEquals(3, repository.entries.single().attemptCount)
        assertEquals(1, repository.entries.size)
        assertEquals(3, attempted.single().attemptCount)
    }

    @Test fun duplicateEligibleEntriesExecuteOnlyTheOldestOperationIdentity() = runTest {
        val oldest = entry(SyncQueueOperationType.DRAFT_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK).copy(
            id = "oldest",
            createdAtEpochMillis = 1,
        )
        val duplicate = oldest.copy(id = "duplicate", createdAtEpochMillis = 2)
        val repository = RecordingRepository(listOf(oldest, duplicate))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)

        ForegroundSyncQueueRetryCoordinator(repository, executor).retryEligible(
            listOf(duplicate, oldest),
            hasAuthenticatedSession = false,
        )

        assertEquals(listOf("oldest"), executor.executedEntries.map { it.id })
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.first { it.id == "oldest" }.status)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.first { it.id == "duplicate" }.status)
    }

    @Test fun failedEligibleRetryUpdatesStatusAndFailureMetadata() = runTest {
        val entry = entry(SyncQueueOperationType.FINALIZED_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingRepository(listOf(entry))
        val executor = RecordingExecutor(
            SyncQueueRetryOutcome.Failure(SyncQueueStatus.BLOCKED_AUTHENTICATION, "session_expired"),
        )

        ForegroundSyncQueueRetryCoordinator(repository, executor).retryEligible(listOf(entry), hasAuthenticatedSession = true)

        assertEquals(listOf(entry.id), repository.incrementedIds)
        assertEquals(emptyList<String>(), repository.completedIds)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.entries.single().status)
        assertEquals("session_expired", repository.entries.single().failureCategory)
        assertEquals(1, repository.entries.single().attemptCount)
    }

    private fun entry(
        operationType: SyncQueueOperationType,
        status: SyncQueueStatus,
        attemptCount: Int = 0,
    ) = SyncQueueEntry(
        id = "$operationType-$status",
        operationType = operationType,
        tournamentId = "tournament-id",
        createdAtEpochMillis = 0,
        status = status,
        failureCategory = status.name,
        attemptCount = attemptCount,
    )

    private class RecordingExecutor(
        private val outcome: SyncQueueRetryOutcome,
    ) : SyncQueueEntryRetryExecutor {
        val executedEntries = mutableListOf<SyncQueueEntry>()
        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome = outcome.also { executedEntries += entry }
    }

    private class RecordingRepository(
        initialEntries: List<SyncQueueEntry> = emptyList(),
    ) : PersistentSyncQueueRepository {
        val entries = initialEntries.toMutableList()
        val incrementedIds = mutableListOf<String>()
        val completedIds = mutableListOf<String>()
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(entries)
        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry = error("not used")
        override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
        override suspend fun incrementAttemptCount(id: String) {
            incrementedIds += id
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
