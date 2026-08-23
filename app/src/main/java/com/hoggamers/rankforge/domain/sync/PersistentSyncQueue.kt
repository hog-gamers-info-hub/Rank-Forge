package com.hoggamers.rankforge.domain.sync

import kotlinx.coroutines.flow.Flow

enum class SyncQueueOperationType {
    TOURNAMENT_UPLOAD,
    TOURNAMENT_RESTORATION,
    DRAFT_MATCH_SYNC,
    FINALIZED_MATCH_SYNC,
    MATCH_RESTORATION,
    ROSTER_REPLACEMENT,
}
enum class SyncQueueStatus { PENDING, BLOCKED_AUTHENTICATION, BLOCKED_NETWORK, COMPLETED, FAILED_VALIDATION, FAILED_AUTHORIZATION, FAILED_LOCAL, FAILED_CONFLICT, FAILED_UNKNOWN }
data class SyncQueueEntry(
    val id: String,
    val operationType: SyncQueueOperationType,
    val tournamentId: String?,
    val createdAtEpochMillis: Long,
    val status: SyncQueueStatus,
    val failureCategory: String?,
    val attemptCount: Int,
    /** Null only for ownerless rows preserved by the 18 -> 19 migration. */
    val ownerUserId: String? = null,
)
data class SyncOperationIdentity(
    val operationType: SyncQueueOperationType,
    val tournamentId: String?,
) {
    val stableKey: String = "${operationType.name}|${tournamentId?.length ?: -1}:${tournamentId.orEmpty()}"

    companion object {
        fun from(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
        ): SyncOperationIdentity = SyncOperationIdentity(operationType, tournamentId)
    }
}
interface PersistentSyncQueueRepository {
    /** Trusted compatibility path for Phase 4B local deletion only; recovery must not use it. */
    fun observeAll(): Flow<List<SyncQueueEntry>>
    fun observePendingByOwner(ownerUserId: String): Flow<List<SyncQueueEntry>> =
        throw UnsupportedOperationException("Owner-scoped queue observation is required.")
    suspend fun enqueue(ownerUserId: String, operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String? = null): SyncQueueEntry =
        throw UnsupportedOperationException("Owner-scoped queue enqueue is required.")
    suspend fun completeOldestUnresolvedByOwner(ownerUserId: String, operationType: SyncQueueOperationType, tournamentId: String?): Unit =
        throw UnsupportedOperationException("Owner-scoped queue completion is required.")
    suspend fun incrementAttemptCountByOwner(id: String, ownerUserId: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")
    suspend fun updateRetryFailureByOwner(id: String, ownerUserId: String, status: SyncQueueStatus, failureCategory: String?): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")
    suspend fun markCompletedByOwner(id: String, ownerUserId: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")
    suspend fun removeByOwner(id: String, ownerUserId: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")
    suspend fun purgeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue purge is required.")

    /**
     * Legacy test-double surface. Production callers must use the owner-scoped overloads above;
     * these defaults fail closed so a stale double cannot create or mutate an ownerless row.
     */
    suspend fun enqueue(
        operationType: SyncQueueOperationType,
        tournamentId: String?,
        status: SyncQueueStatus,
        failureCategory: String? = null,
    ): SyncQueueEntry = throw UnsupportedOperationException("Owner-scoped queue enqueue is required.")

    suspend fun completeOldestUnresolved(
        operationType: SyncQueueOperationType,
        tournamentId: String?,
    ): Unit = throw UnsupportedOperationException("Owner-scoped queue completion is required.")

    suspend fun incrementAttemptCount(id: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")

    suspend fun updateRetryFailure(
        id: String,
        status: SyncQueueStatus,
        failureCategory: String?,
    ): Unit = throw UnsupportedOperationException("Owner-scoped queue mutation is required.")

    suspend fun markCompleted(id: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")

    suspend fun remove(id: String): Unit =
        throw UnsupportedOperationException("Owner-scoped queue mutation is required.")

    /** Trusted Phase 4B compatibility path; user-facing recovery must not call it. */
    suspend fun purgeByTournamentId(tournamentId: String) {
        throw UnsupportedOperationException("Global queue purge is only available to a trusted implementation.")
    }
}
