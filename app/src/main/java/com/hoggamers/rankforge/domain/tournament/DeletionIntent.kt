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
    /** Trusted legacy/test compatibility only; user-facing flows must use owner-scoped APIs. */
    @Deprecated("Use findByTargetAndOwner")
    suspend fun read(targetType: DeletionTargetType, targetId: String): DeletionIntent? = null

    /** Trusted legacy/test compatibility only; user-facing flows must use startIfAbsent. */
    @Deprecated("Use startIfAbsent")
    suspend fun start(intent: DeletionIntent): DeletionIntent = intent

    suspend fun findByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): DeletionIntent? = error("Owner-scoped deletion intent lookup is not supported.")

    /** Returns false when another owner already holds the target's intent key. */
    suspend fun startIfAbsent(intent: DeletionIntent): Boolean =
        error("Owner-scoped deletion intent creation is not supported.")

    suspend fun markRemoteDeletedByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = error("Owner-scoped deletion intent mutation is not supported.")

    suspend fun clearByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = error("Owner-scoped deletion intent mutation is not supported.")

    suspend fun isBlockingByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Boolean =
        error("Owner-scoped deletion intent lookup is not supported.")

    suspend fun readPendingLocalCleanupByOwner(ownerUserId: String): List<DeletionIntent> =
        error("Owner-scoped deletion intent lookup is not supported.")

    suspend fun hasLocalCleanupClaim(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = false

    @Deprecated("Use owner-scoped APIs")
    suspend fun markRemoteDeleted(targetType: DeletionTargetType, targetId: String) = Unit

    @Deprecated("Use clearByTargetAndOwner")
    suspend fun clear(targetType: DeletionTargetType, targetId: String) = Unit

    @Deprecated("Use isBlockingByTournamentIdAndOwner")
    suspend fun isBlocking(tournamentId: String): Boolean = false

    @Deprecated("Use readPendingLocalCleanupByOwner")
    suspend fun readAll(): List<DeletionIntent> = emptyList()

    @Deprecated("Use readPendingLocalCleanupByOwner")
    suspend fun readPendingLocalCleanup(): List<DeletionIntent> = emptyList()
}

object NoOpDeletionIntentRepository : DeletionIntentRepository {
    override suspend fun findByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): DeletionIntent? = null

    override suspend fun startIfAbsent(intent: DeletionIntent): Boolean = true

    override suspend fun markRemoteDeletedByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = true

    override suspend fun clearByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = true

    override suspend fun isBlockingByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean = false

    override suspend fun readPendingLocalCleanupByOwner(ownerUserId: String): List<DeletionIntent> = emptyList()

    override suspend fun hasLocalCleanupClaim(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean = false
}
