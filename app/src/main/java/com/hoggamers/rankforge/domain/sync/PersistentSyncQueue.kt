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
data class SyncQueueEntry(val id: String, val operationType: SyncQueueOperationType, val tournamentId: String?, val createdAtEpochMillis: Long, val status: SyncQueueStatus, val failureCategory: String?, val attemptCount: Int)
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
interface PersistentSyncQueueRepository { fun observeAll(): Flow<List<SyncQueueEntry>>; suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String? = null): SyncQueueEntry; suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?); suspend fun incrementAttemptCount(id: String); suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?); suspend fun markCompleted(id: String); suspend fun remove(id: String) }
