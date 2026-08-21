package com.hoggamers.rankforge.data.sync

import com.hoggamers.rankforge.data.local.SyncQueueDao
import com.hoggamers.rankforge.data.local.SyncQueueEntity
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncOperationIdentity
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.tournament.DeletionBlockedException
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton class RoomPersistentSyncQueueRepository @Inject constructor(
    private val dao: SyncQueueDao,
    private val deletionIntentRepository: DeletionIntentRepository,
) : PersistentSyncQueueRepository {
    constructor(dao: SyncQueueDao) : this(dao, NoOpDeletionIntentRepository)
    private val enqueueMutex = Mutex()
    override fun observeAll(): Flow<List<SyncQueueEntry>> = dao.observeAll().map { entries -> entries.map { it.toDomain() } }
    override suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?): SyncQueueEntry = enqueueMutex.withLock {
        if (tournamentId != null && deletionIntentRepository.isBlocking(tournamentId)) {
            throw DeletionBlockedException(tournamentId)
        }
        val identity = SyncOperationIdentity.from(operationType, tournamentId)
        val existing = dao.findOldestUnresolved(identity.operationType.name, identity.tournamentId)
        if (existing != null) {
            dao.updateStatus(existing.id, status.name, failureCategory)
            return@withLock existing.copy(status = status.name, failureCategory = failureCategory).toDomain()
        }
        val entry = SyncQueueEntry(UUID.randomUUID().toString(), operationType, tournamentId, System.currentTimeMillis(), status, failureCategory, 0)
        dao.insert(entry.toEntity()); return entry
    }
    override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) {
        enqueueMutex.withLock {
            val identity = SyncOperationIdentity.from(operationType, tournamentId)
            dao.findOldestUnresolved(identity.operationType.name, identity.tournamentId)?.let { entry ->
                dao.updateStatus(entry.id, SyncQueueStatus.COMPLETED.name, null)
            }
        }
    }
    override suspend fun incrementAttemptCount(id: String) { dao.incrementAttemptCount(id) }
    override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) { dao.updateStatus(id, status.name, failureCategory) }
    override suspend fun markCompleted(id: String) { dao.updateStatus(id, SyncQueueStatus.COMPLETED.name, null) }
    override suspend fun remove(id: String) { dao.delete(id) }
    override suspend fun purgeByTournamentId(tournamentId: String) { dao.deleteByTournamentId(tournamentId) }
}
private fun SyncQueueEntity.toDomain() = SyncQueueEntry(id, SyncQueueOperationType.valueOf(operationType), tournamentId, createdAtEpochMillis, SyncQueueStatus.valueOf(status), failureCategory, attemptCount)
private fun SyncQueueEntry.toEntity() = SyncQueueEntity(id, operationType.name, tournamentId, createdAtEpochMillis, status.name, failureCategory, attemptCount)
