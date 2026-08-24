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
    override suspend fun findByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): DeletionIntent? = dao.findByTargetAndOwner(targetType.name, targetId, ownerUserId)?.toDomain()

    override suspend fun startIfAbsent(intent: DeletionIntent): Boolean =
        dao.insertIfAbsent(intent.toEntity()) != -1L

    override suspend fun markRemoteDeletedByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = dao.markRemoteDeletedByTargetAndOwner(
        targetType.name,
        targetId,
        ownerUserId,
        System.currentTimeMillis(),
    ) != 0

    override suspend fun clearByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = dao.deleteByTargetAndOwner(targetType.name, targetId, ownerUserId) != 0

    override suspend fun isBlockingByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean = dao.isBlockingByTournamentIdAndOwner(tournamentId, ownerUserId)

    override suspend fun readPendingLocalCleanupByOwner(ownerUserId: String): List<DeletionIntent> =
        dao.readPendingLocalCleanupByOwner(ownerUserId).map { it.toDomain() }

    override suspend fun hasLocalCleanupClaim(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = dao.hasLocalCleanupClaim(targetType.name, targetId, ownerUserId)
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
