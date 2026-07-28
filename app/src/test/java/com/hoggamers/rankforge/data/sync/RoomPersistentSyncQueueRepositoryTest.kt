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
        repository.markCompleted(entry.id); assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().single().status)
        repository.remove(entry.id); assertTrue(repository.observeAll().first().isEmpty())
    }
    private class FakeDao : SyncQueueDao {
        private val entries = MutableStateFlow<List<SyncQueueEntity>>(emptyList())
        override fun observeAll() = entries
        override suspend fun insert(entry: SyncQueueEntity) { entries.value += entry }
        override suspend fun updateStatus(id: String, status: String, failureCategory: String?) { entries.value = entries.value.map { if (it.id == id) it.copy(status = status, failureCategory = failureCategory) else it } }
        override suspend fun delete(id: String) { entries.value = entries.value.filterNot { it.id == id } }
    }
}
