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
    override fun observePendingByOwner(ownerUserId: String): Flow<List<SyncQueueEntry>> {
        require(ownerUserId.isNotBlank())
        return dao.observeByOwner(ownerUserId).map { entries -> entries.map { it.toDomain() } }
    }
    override suspend fun enqueue(ownerUserId: String, operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?): SyncQueueEntry = enqueueMutex.withLock {
        require(ownerUserId.isNotBlank())
        if (tournamentId != null && deletionIntentRepository.isBlocking(tournamentId)) {
            throw DeletionBlockedException(tournamentId)
        }
        val identity = SyncOperationIdentity.from(operationType, tournamentId)
        val existing = dao.findOldestUnresolvedByOwner(ownerUserId, identity.operationType.name, identity.tournamentId)
        if (existing != null) {
            dao.updateStatusByIdAndOwner(existing.id, ownerUserId, status.name, failureCategory)
            return@withLock existing.copy(status = status.name, failureCategory = failureCategory).toDomain()
        }
        val entry = SyncQueueEntry(UUID.randomUUID().toString(), operationType, tournamentId, System.currentTimeMillis(), status, failureCategory, 0, ownerUserId)
        dao.insert(entry.toEntity()); return entry
    }
    override suspend fun completeOldestUnresolvedByOwner(ownerUserId: String, operationType: SyncQueueOperationType, tournamentId: String?) {
        require(ownerUserId.isNotBlank())
        enqueueMutex.withLock {
            val identity = SyncOperationIdentity.from(operationType, tournamentId)
            dao.findOldestUnresolvedByOwner(ownerUserId, identity.operationType.name, identity.tournamentId)?.let { entry ->
                dao.updateStatusByIdAndOwner(entry.id, ownerUserId, SyncQueueStatus.COMPLETED.name, null)
            }
        }
    }
    override suspend fun incrementAttemptCountByOwner(id: String, ownerUserId: String) { dao.incrementAttemptCountByIdAndOwner(id, ownerUserId) }
    override suspend fun updateRetryFailureByOwner(id: String, ownerUserId: String, status: SyncQueueStatus, failureCategory: String?) { dao.updateStatusByIdAndOwner(id, ownerUserId, status.name, failureCategory) }
    override suspend fun markCompletedByOwner(id: String, ownerUserId: String) { dao.updateStatusByIdAndOwner(id, ownerUserId, SyncQueueStatus.COMPLETED.name, null) }
    override suspend fun removeByOwner(id: String, ownerUserId: String) { dao.deleteByIdAndOwner(id, ownerUserId) }
    override suspend fun purgeByTournamentId(tournamentId: String) { dao.deleteByTournamentId(tournamentId) }
    override suspend fun purgeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) { dao.deleteByTournamentIdAndOwner(tournamentId, ownerUserId) }
}
private fun SyncQueueEntity.toDomain() = SyncQueueEntry(id, SyncQueueOperationType.valueOf(operationType), tournamentId, createdAtEpochMillis, SyncQueueStatus.valueOf(status), failureCategory, attemptCount, ownerUserId)
private fun SyncQueueEntry.toEntity() = SyncQueueEntity(id, operationType.name, tournamentId, createdAtEpochMillis, status.name, failureCategory, attemptCount, ownerUserId)
