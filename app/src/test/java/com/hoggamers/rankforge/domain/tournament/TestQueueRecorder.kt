package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class RecordingTestQueueRepository(
    private val enqueueFailure: Throwable? = null,
) : PersistentSyncQueueRepository {
    val entries = mutableListOf<SyncQueueEntry>()
    override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(entries)
    override suspend fun enqueue(
        operationType: SyncQueueOperationType,
        tournamentId: String?,
        status: SyncQueueStatus,
        failureCategory: String?,
    ): SyncQueueEntry {
        enqueueFailure?.let { throw it }
        return SyncQueueEntry(
            id = "test-${entries.size}",
            operationType = operationType,
            tournamentId = tournamentId,
            createdAtEpochMillis = 0,
            status = status,
            failureCategory = failureCategory,
            attemptCount = 0,
        ).also(entries::add)
    }
    override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
    override suspend fun incrementAttemptCount(id: String) = Unit
    override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
    override suspend fun markCompleted(id: String) = Unit
    override suspend fun remove(id: String) = Unit
}
internal fun RecordingTestQueueRepository.recorder() = RecordSyncQueueOutcome(this)
internal fun testQueueRecorder() = RecordSyncQueueOutcome(RecordingTestQueueRepository())
