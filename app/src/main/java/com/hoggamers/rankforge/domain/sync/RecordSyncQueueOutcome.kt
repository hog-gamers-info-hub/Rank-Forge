package com.hoggamers.rankforge.domain.sync

import javax.inject.Inject
import java.util.concurrent.CancellationException

enum class QueueRecordingResult { NOT_REQUIRED, RECORDED, PERSISTENCE_FAILED }

class RecordSyncQueueOutcome @Inject constructor(private val repository: PersistentSyncQueueRepository) {
    /** Legacy test-only overload; production callers must provide an authenticated owner. */
    suspend fun record(
        operation: SyncQueueOperationType,
        tournamentId: String?,
        status: SyncQueueStatus,
        failureCategory: String? = status.name,
    ): QueueRecordingResult = record(
        ownerUserId = null,
        operation = operation,
        tournamentId = tournamentId,
        status = status,
        failureCategory = failureCategory,
    )

    suspend fun record(
        ownerUserId: String?,
        operation: SyncQueueOperationType,
        tournamentId: String?,
        status: SyncQueueStatus,
        failureCategory: String? = status.name,
    ): QueueRecordingResult {
        if (ownerUserId.isNullOrBlank()) return QueueRecordingResult.NOT_REQUIRED
        if (status == SyncQueueStatus.COMPLETED) {
            try {
                repository.completeOldestUnresolvedByOwner(ownerUserId, operation, tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // A successful cloud action remains successful if completion persistence is unavailable.
            }
            return QueueRecordingResult.NOT_REQUIRED
        }
        return try {
            repository.enqueue(ownerUserId, operation, tournamentId, status, failureCategory)
            QueueRecordingResult.RECORDED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            QueueRecordingResult.PERSISTENCE_FAILED
        }
    }
}
