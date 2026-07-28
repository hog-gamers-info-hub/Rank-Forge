package com.hoggamers.rankforge.domain.sync

import javax.inject.Inject
import java.util.concurrent.CancellationException

enum class QueueRecordingResult { NOT_REQUIRED, RECORDED, PERSISTENCE_FAILED }

class RecordSyncQueueOutcome @Inject constructor(private val repository: PersistentSyncQueueRepository) {
    suspend fun record(
        operation: SyncQueueOperationType,
        tournamentId: String?,
        status: SyncQueueStatus,
        failureCategory: String? = status.name,
    ): QueueRecordingResult {
        if (status == SyncQueueStatus.COMPLETED) {
            try {
                repository.completeOldestUnresolved(operation, tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // A successful cloud action remains successful if completion persistence is unavailable.
            }
            return QueueRecordingResult.NOT_REQUIRED
        }
        return try {
            repository.enqueue(operation, tournamentId, status, failureCategory)
            QueueRecordingResult.RECORDED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            QueueRecordingResult.PERSISTENCE_FAILED
        }
    }
}
