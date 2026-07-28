package com.hoggamers.rankforge.domain.sync

import kotlinx.coroutines.flow.Flow

enum class SyncQueueOperationType { TOURNAMENT_UPLOAD, TOURNAMENT_RESTORATION, DRAFT_MATCH_SYNC, FINALIZED_MATCH_SYNC, MATCH_RESTORATION }
enum class SyncQueueStatus { PENDING, BLOCKED_AUTHENTICATION, BLOCKED_NETWORK, COMPLETED, FAILED_VALIDATION, FAILED_AUTHORIZATION, FAILED_LOCAL, FAILED_UNKNOWN }
data class SyncQueueEntry(val id: String, val operationType: SyncQueueOperationType, val tournamentId: String?, val createdAtEpochMillis: Long, val status: SyncQueueStatus, val failureCategory: String?, val attemptCount: Int)
interface PersistentSyncQueueRepository { fun observeAll(): Flow<List<SyncQueueEntry>>; suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String? = null): SyncQueueEntry; suspend fun markCompleted(id: String); suspend fun remove(id: String) }
