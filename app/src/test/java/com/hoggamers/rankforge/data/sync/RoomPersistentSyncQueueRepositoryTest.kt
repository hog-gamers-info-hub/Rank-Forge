package com.hoggamers.rankforge.data.sync

import com.hoggamers.rankforge.data.local.SyncQueueDao
import com.hoggamers.rankforge.data.local.SyncQueueEntity
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPersistentSyncQueueRepositoryTest {
    @Test fun persistsOperationTournamentStatusAndZeroAttemptCountThenUpdatesAndDeletes() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val entry = repository.enqueue(SyncQueueOperationType.DRAFT_MATCH_SYNC, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        assertEquals("tournament-id", entry.tournamentId); assertEquals(0, entry.attemptCount)
        assertEquals(SyncQueueOperationType.DRAFT_MATCH_SYNC, repository.observeAll().first().single().operationType)
        repository.incrementAttemptCount(entry.id); assertEquals(1, repository.observeAll().first().single().attemptCount)
        repository.updateRetryFailure(entry.id, SyncQueueStatus.FAILED_UNKNOWN, "retry_unknown")
        assertEquals(SyncQueueStatus.FAILED_UNKNOWN, repository.observeAll().first().single().status)
        assertEquals("retry_unknown", repository.observeAll().first().single().failureCategory)
        repository.markCompleted(entry.id); assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().single().status)
        assertEquals(1, repository.observeAll().first().single().attemptCount)
        repository.remove(entry.id); assertTrue(repository.observeAll().first().isEmpty())
    }
    @Test fun preservesOldestUnresolvedEntryForTheSameOperationIdentity() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val first = repository.enqueue(SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        repository.incrementAttemptCount(first.id)

        val duplicate = repository.enqueue(SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id", SyncQueueStatus.BLOCKED_AUTHENTICATION, "authentication")

        assertEquals(first.id, duplicate.id)
        assertEquals(1, repository.observeAll().first().size)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.observeAll().first().single().status)
        assertEquals("authentication", repository.observeAll().first().single().failureCategory)
        assertEquals(1, repository.observeAll().first().single().attemptCount)
    }
    @Test fun completedEntriesAreRetainedButDoNotBlockNewUnresolvedWork() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val completed = repository.enqueue(SyncQueueOperationType.MATCH_RESTORATION, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        repository.markCompleted(completed.id)

        val unresolved = repository.enqueue(SyncQueueOperationType.MATCH_RESTORATION, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")

        assertTrue(completed.id != unresolved.id)
        assertEquals(2, repository.observeAll().first().size)
        assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().first { it.id == completed.id }.status)
    }
    @Test fun completesMatchingUnresolvedEntryWithoutAddingAnotherRow() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val entry = repository.enqueue(SyncQueueOperationType.DRAFT_MATCH_SYNC, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        repository.incrementAttemptCount(entry.id)

        repository.completeOldestUnresolved(SyncQueueOperationType.DRAFT_MATCH_SYNC, "tournament-id")

        assertEquals(1, repository.observeAll().first().size)
        assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().single().status)
        assertEquals(1, repository.observeAll().first().single().attemptCount)
    }
    @Test fun preventsDuplicateUnresolvedEntriesForEveryOperationType() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        SyncQueueOperationType.entries.forEach { operationType ->
            val first = repository.enqueue(operationType, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
            val duplicate = repository.enqueue(operationType, "tournament-id", SyncQueueStatus.FAILED_UNKNOWN, "unknown")
            assertEquals(first.id, duplicate.id)
        }
        assertEquals(6, repository.observeAll().first().size)
    }
    private class FakeDao : SyncQueueDao {
        private val entries = MutableStateFlow<List<SyncQueueEntity>>(emptyList())
        override fun observeAll() = entries
        override suspend fun findOldestUnresolved(operationType: String, tournamentId: String?) = entries.value
            .filter { it.operationType == operationType && it.tournamentId == tournamentId && it.status != SyncQueueStatus.COMPLETED.name }
            .minWithOrNull(compareBy<SyncQueueEntity> { it.createdAtEpochMillis }.thenBy { it.id })
        override suspend fun insert(entry: SyncQueueEntity) { entries.value += entry }
        override suspend fun updateStatus(id: String, status: String, failureCategory: String?) { entries.value = entries.value.map { if (it.id == id) it.copy(status = status, failureCategory = failureCategory) else it } }
        override suspend fun incrementAttemptCount(id: String) { entries.value = entries.value.map { if (it.id == id) it.copy(attemptCount = it.attemptCount + 1) else it } }
        override suspend fun delete(id: String) { entries.value = entries.value.filterNot { it.id == id } }
        override suspend fun deleteByTournamentId(tournamentId: String) {
            entries.value = entries.value.filterNot { it.tournamentId == tournamentId }
        }
    }
}
