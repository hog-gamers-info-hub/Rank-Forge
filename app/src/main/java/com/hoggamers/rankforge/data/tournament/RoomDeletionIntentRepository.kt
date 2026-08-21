package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.DeletionIntentDao
import com.hoggamers.rankforge.data.local.DeletionIntentEntity
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDeletionIntentRepository @Inject constructor(
    private val dao: DeletionIntentDao,
) : DeletionIntentRepository {
    override suspend fun read(targetType: DeletionTargetType, targetId: String): DeletionIntent? =
        dao.read(targetType.name, targetId)?.toDomain()

    override suspend fun start(intent: DeletionIntent): DeletionIntent {
        dao.upsert(intent.toEntity())
        return intent
    }

    override suspend fun markRemoteDeleted(targetType: DeletionTargetType, targetId: String) {
        dao.markRemoteDeleted(targetType.name, targetId, System.currentTimeMillis())
    }

    override suspend fun clear(targetType: DeletionTargetType, targetId: String) {
        dao.delete(targetType.name, targetId)
    }

    override suspend fun isBlocking(tournamentId: String): Boolean = dao.isBlocking(tournamentId)

    override suspend fun readAll(): List<DeletionIntent> = dao.readAll().map { it.toDomain() }

    override suspend fun readPendingLocalCleanup(): List<DeletionIntent> =
        dao.readPendingLocalCleanup().map { it.toDomain() }
}

private fun DeletionIntentEntity.toDomain() = DeletionIntent(
    targetType = DeletionTargetType.valueOf(targetType),
    targetId = targetId,
    tournamentId = tournamentId,
    ownerUserId = ownerUserId,
    phase = DeletionIntentPhase.valueOf(phase),
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun DeletionIntent.toEntity() = DeletionIntentEntity(
    targetType = targetType.name,
    targetId = targetId,
    tournamentId = tournamentId,
    ownerUserId = ownerUserId,
    phase = phase.name,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
