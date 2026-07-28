package com.hoggamers.rankforge.data.sync

import com.hoggamers.rankforge.data.local.SyncQueueDao
import com.hoggamers.rankforge.data.local.SyncQueueEntity
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton class RoomPersistentSyncQueueRepository @Inject constructor(private val dao: SyncQueueDao) : PersistentSyncQueueRepository {
    override fun observeAll(): Flow<List<SyncQueueEntry>> = dao.observeAll().map { entries -> entries.map { it.toDomain() } }
    override suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?): SyncQueueEntry {
        val entry = SyncQueueEntry(UUID.randomUUID().toString(), operationType, tournamentId, System.currentTimeMillis(), status, failureCategory, 0)
        dao.insert(entry.toEntity()); return entry
    }
    override suspend fun markCompleted(id: String) { dao.updateStatus(id, SyncQueueStatus.COMPLETED.name, null) }
    override suspend fun remove(id: String) { dao.delete(id) }
}
private fun SyncQueueEntity.toDomain() = SyncQueueEntry(id, SyncQueueOperationType.valueOf(operationType), tournamentId, createdAtEpochMillis, SyncQueueStatus.valueOf(status), failureCategory, attemptCount)
private fun SyncQueueEntry.toEntity() = SyncQueueEntity(id, operationType.name, tournamentId, createdAtEpochMillis, status.name, failureCategory, attemptCount)
