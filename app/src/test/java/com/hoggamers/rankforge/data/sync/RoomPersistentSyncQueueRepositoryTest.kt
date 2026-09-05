package com.hoggamers.rankforge.data.sync

import com.hoggamers.rankforge.data.local.SyncQueueDao
import com.hoggamers.rankforge.data.local.SyncQueueEntity
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPersistentSyncQueueRepositoryTest {
    @Test fun persistsOperationTournamentStatusAndZeroAttemptCountThenUpdatesAndDeletes() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val entry = repository.enqueue(OWNER_A, SyncQueueOperationType.DRAFT_MATCH_SYNC, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        assertEquals("tournament-id", entry.tournamentId); assertEquals(0, entry.attemptCount)
        assertEquals(SyncQueueOperationType.DRAFT_MATCH_SYNC, repository.observeAll().first().single().operationType)
        repository.incrementAttemptCountByOwner(entry.id, OWNER_A); assertEquals(1, repository.observeAll().first().single().attemptCount)
        repository.updateRetryFailureByOwner(entry.id, OWNER_A, SyncQueueStatus.FAILED_UNKNOWN, "retry_unknown")
        assertEquals(SyncQueueStatus.FAILED_UNKNOWN, repository.observeAll().first().single().status)
        assertEquals("retry_unknown", repository.observeAll().first().single().failureCategory)
        repository.markCompletedByOwner(entry.id, OWNER_A); assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().single().status)
        assertEquals(1, repository.observeAll().first().single().attemptCount)
        repository.removeByOwner(entry.id, OWNER_A); assertTrue(repository.observeAll().first().isEmpty())
    }
    @Test fun preservesOldestUnresolvedEntryForTheSameOperationIdentity() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val first = repository.enqueue(OWNER_A, SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        repository.incrementAttemptCountByOwner(first.id, OWNER_A)

        val duplicate = repository.enqueue(OWNER_A, SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id", SyncQueueStatus.BLOCKED_AUTHENTICATION, "authentication")

        assertEquals(first.id, duplicate.id)
        assertEquals(1, repository.observeAll().first().size)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.observeAll().first().single().status)
        assertEquals("authentication", repository.observeAll().first().single().failureCategory)
        assertEquals(1, repository.observeAll().first().single().attemptCount)
    }
    @Test fun completedEntriesAreRetainedButDoNotBlockNewUnresolvedWork() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val completed = repository.enqueue(OWNER_A, SyncQueueOperationType.MATCH_RESTORATION, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        repository.markCompletedByOwner(completed.id, OWNER_A)

        val unresolved = repository.enqueue(OWNER_A, SyncQueueOperationType.MATCH_RESTORATION, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")

        assertTrue(completed.id != unresolved.id)
        assertEquals(2, repository.observeAll().first().size)
        assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().first { it.id == completed.id }.status)
    }
    @Test fun completesMatchingUnresolvedEntryWithoutAddingAnotherRow() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val entry = repository.enqueue(OWNER_A, SyncQueueOperationType.DRAFT_MATCH_SYNC, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
        repository.incrementAttemptCountByOwner(entry.id, OWNER_A)

        repository.completeOldestUnresolvedByOwner(OWNER_A, SyncQueueOperationType.DRAFT_MATCH_SYNC, "tournament-id")

        assertEquals(1, repository.observeAll().first().size)
        assertEquals(SyncQueueStatus.COMPLETED, repository.observeAll().first().single().status)
        assertEquals(1, repository.observeAll().first().single().attemptCount)
    }
    @Test fun preventsDuplicateUnresolvedEntriesForEveryOperationType() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        SyncQueueOperationType.entries.forEach { operationType ->
            val first = repository.enqueue(OWNER_A, operationType, "tournament-id", SyncQueueStatus.BLOCKED_NETWORK, "network")
            val duplicate = repository.enqueue(OWNER_A, operationType, "tournament-id", SyncQueueStatus.FAILED_UNKNOWN, "unknown")
            assertEquals(first.id, duplicate.id)
        }
        assertEquals(6, repository.observeAll().first().size)
    }
    @Test fun ownerScopedObservationMutationCompletionAndPurgeLeaveForeignAndLegacyRowsUntouched() = runTest {
        val dao = FakeDao(); val repository = RoomPersistentSyncQueueRepository(dao)
        val a = repository.enqueue(OWNER_A, SyncQueueOperationType.DRAFT_MATCH_SYNC, "shared", SyncQueueStatus.BLOCKED_NETWORK)
        val b = repository.enqueue(OWNER_B, SyncQueueOperationType.DRAFT_MATCH_SYNC, "shared", SyncQueueStatus.BLOCKED_NETWORK)
        dao.insert(
            SyncQueueEntity("legacy", SyncQueueOperationType.DRAFT_MATCH_SYNC.name, "shared", 0, SyncQueueStatus.BLOCKED_NETWORK.name, null, 0),
        )

        assertEquals(listOf(a.id), repository.observePendingByOwner(OWNER_A).first().map { it.id })
        assertEquals(listOf(b.id), repository.observePendingByOwner(OWNER_B).first().map { it.id })
        repository.incrementAttemptCountByOwner(b.id, OWNER_A)
        repository.completeOldestUnresolvedByOwner(OWNER_A, SyncQueueOperationType.DRAFT_MATCH_SYNC, "shared")
        repository.purgeByTournamentIdAndOwner("shared", OWNER_A)

        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.observeAll().first().single { it.id == b.id }.status)
        assertEquals(0, repository.observeAll().first().single { it.id == b.id }.attemptCount)
        assertEquals(null, repository.observeAll().first().single { it.id == "legacy" }.ownerUserId)
    }
    private class FakeDao : SyncQueueDao {
        private val entries = MutableStateFlow<List<SyncQueueEntity>>(emptyList())
        override fun observeAll() = entries
        override fun observeByOwner(ownerUserId: String) = entries.map { rows -> rows.filter { it.ownerUserId == ownerUserId } }
        override suspend fun findOldestUnresolvedByOwner(ownerUserId: String, operationType: String, tournamentId: String?) = entries.value
            .filter { it.ownerUserId == ownerUserId && it.operationType == operationType && it.tournamentId == tournamentId && it.status != SyncQueueStatus.COMPLETED.name }
            .minWithOrNull(compareBy<SyncQueueEntity> { it.createdAtEpochMillis }.thenBy { it.id })
        override suspend fun insert(entry: SyncQueueEntity) { entries.value += entry }
        override suspend fun updateStatusByIdAndOwner(id: String, ownerUserId: String, status: String, failureCategory: String?) { entries.value = entries.value.map { if (it.id == id && it.ownerUserId == ownerUserId) it.copy(status = status, failureCategory = failureCategory) else it } }
        override suspend fun incrementAttemptCountByIdAndOwner(id: String, ownerUserId: String) { entries.value = entries.value.map { if (it.id == id && it.ownerUserId == ownerUserId) it.copy(attemptCount = it.attemptCount + 1) else it } }
        override suspend fun deleteByIdAndOwner(id: String, ownerUserId: String) { entries.value = entries.value.filterNot { it.id == id && it.ownerUserId == ownerUserId } }
        override suspend fun deleteByTournamentId(tournamentId: String) {
            entries.value = entries.value.filterNot { it.tournamentId == tournamentId }
        }
        override suspend fun deleteByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) {
            entries.value = entries.value.filterNot { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId }
        }
        override suspend fun deleteByOwner(ownerUserId: String) {
            entries.value = entries.value.filterNot { it.ownerUserId == ownerUserId }
        }
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
    }
}
