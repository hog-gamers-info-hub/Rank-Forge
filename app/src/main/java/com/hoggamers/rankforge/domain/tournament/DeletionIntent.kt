package com.hoggamers.rankforge.domain.tournament

enum class DeletionTargetType { MATCH, TOURNAMENT }

enum class DeletionIntentPhase {
    DELETE_STARTED,
    REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
}

data class DeletionIntent(
    val targetType: DeletionTargetType,
    val targetId: String,
    val tournamentId: String,
    val ownerUserId: String,
    val phase: DeletionIntentPhase,
    val updatedAtEpochMillis: Long,
)

class DeletionBlockedException(tournamentId: String) :
    IllegalStateException("Deletion is active for tournament $tournamentId")

interface DeletionIntentRepository {
    suspend fun read(targetType: DeletionTargetType, targetId: String): DeletionIntent?

    suspend fun start(intent: DeletionIntent): DeletionIntent

    suspend fun markRemoteDeleted(targetType: DeletionTargetType, targetId: String)

    suspend fun clear(targetType: DeletionTargetType, targetId: String)

    suspend fun isBlocking(tournamentId: String): Boolean

    suspend fun readAll(): List<DeletionIntent>

    suspend fun readPendingLocalCleanup(): List<DeletionIntent>
}

object NoOpDeletionIntentRepository : DeletionIntentRepository {
    override suspend fun read(targetType: DeletionTargetType, targetId: String): DeletionIntent? = null
    override suspend fun start(intent: DeletionIntent): DeletionIntent = intent
    override suspend fun markRemoteDeleted(targetType: DeletionTargetType, targetId: String) = Unit
    override suspend fun clear(targetType: DeletionTargetType, targetId: String) = Unit
    override suspend fun isBlocking(tournamentId: String): Boolean = false
    override suspend fun readAll(): List<DeletionIntent> = emptyList()
    override suspend fun readPendingLocalCleanup(): List<DeletionIntent> = emptyList()
}
