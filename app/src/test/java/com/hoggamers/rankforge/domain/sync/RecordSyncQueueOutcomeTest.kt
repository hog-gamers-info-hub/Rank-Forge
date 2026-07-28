package com.hoggamers.rankforge.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSyncQueueOutcomeTest {
    @Test fun completedDoesNotCreatePendingEntryAndFailuresPersistDeterministically() = runTest {
        val repository = RecordingRepository(); val recorder = RecordSyncQueueOutcome(repository)
        recorder.record(SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament", SyncQueueStatus.COMPLETED)
        assertTrue(repository.entries.isEmpty())
        listOf(SyncQueueStatus.BLOCKED_AUTHENTICATION, SyncQueueStatus.BLOCKED_NETWORK, SyncQueueStatus.FAILED_VALIDATION, SyncQueueStatus.FAILED_AUTHORIZATION, SyncQueueStatus.FAILED_LOCAL, SyncQueueStatus.FAILED_UNKNOWN).forEach { status -> recorder.record(SyncQueueOperationType.MATCH_RESTORATION, "tournament", status) }
        assertEquals(6, repository.entries.size)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.entries.first().status)
        assertTrue(repository.entries.all { it.tournamentId == "tournament" && it.attemptCount == 0 })
    }
    private class RecordingRepository : PersistentSyncQueueRepository {
        val entries = mutableListOf<SyncQueueEntry>()
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(entries)
        override suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?) = SyncQueueEntry("${entries.size}", operationType, tournamentId, 0, status, failureCategory, 0).also(entries::add)
        override suspend fun incrementAttemptCount(id: String) = Unit
        override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
        override suspend fun markCompleted(id: String) = Unit
        override suspend fun remove(id: String) = Unit
    }
}
