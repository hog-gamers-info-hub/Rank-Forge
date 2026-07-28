package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRetryAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRetryAction
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRetryAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOperationRetryExecutorTest {
    @Test fun dispatchesEveryOperationTypeToItsNoRecordRetryAction() = runTest {
        val calls = mutableListOf<String>()
        val executor = executor(
            tournamentUpload = TournamentCloudUploadRetryAction { id -> calls += "upload:$id"; TournamentCloudUploadResult.Success },
            tournamentRestoration = TournamentCloudRestorationRetryAction { id -> calls += "tournament_restore:$id"; TournamentCloudRestorationResult.Success("Tournament") },
            draftMatchSync = DraftMatchCloudSyncRetryAction { id -> calls += "draft_sync:$id"; DraftMatchCloudSyncResult.Success },
            finalizedMatchSync = FinalizedMatchCloudSyncRetryAction { id -> calls += "finalized_sync:$id"; FinalizedMatchCloudSyncResult.Success },
            matchRestoration = MatchCloudRestorationRetryAction { id -> calls += "match_restore:$id"; MatchCloudRestorationResult.Success },
        )

        val outcomes = SyncQueueOperationType.entries.map { operationType ->
            executor.execute(entry(operationType))
        }

        assertEquals(
            listOf(
                "upload:tournament-id",
                "tournament_restore:tournament-id",
                "draft_sync:tournament-id",
                "finalized_sync:tournament-id",
                "match_restore:tournament-id",
            ),
            calls,
        )
        assertTrue(outcomes.all { it == SyncQueueRetryOutcome.Success })
    }

    @Test fun failedRetryReturnsDeterministicStatusAndFailureMetadata() = runTest {
        val executor = executor(
            draftMatchSync = DraftMatchCloudSyncRetryAction { DraftMatchCloudSyncResult.NetworkFailure },
        )

        val outcome = executor.execute(entry(SyncQueueOperationType.DRAFT_MATCH_SYNC))

        assertEquals(
            SyncQueueRetryOutcome.Failure(
                status = SyncQueueStatus.BLOCKED_NETWORK,
                failureCategory = SyncQueueStatus.BLOCKED_NETWORK.name,
            ),
            outcome,
        )
    }

    @Test fun coordinatorWithExecutorUpdatesOnlyTheExistingEntryOnSuccess() = runTest {
        val queuedEntry = entry(SyncQueueOperationType.TOURNAMENT_UPLOAD)
        val repository = RecordingQueueRepository(listOf(queuedEntry))
        val coordinator = ForegroundSyncQueueRetryCoordinator(
            repository = repository,
            executor = executor(),
        )

        coordinator.retryEligible(listOf(queuedEntry), hasAuthenticatedSession = false)

        assertEquals(0, repository.enqueueCalls)
        assertEquals(1, repository.entries.size)
        assertEquals(1, repository.entries.single().attemptCount)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
    }

    @Test fun coordinatorWithExecutorUpdatesExistingEntryForFailureWithoutEnqueueing() = runTest {
        val queuedEntry = entry(SyncQueueOperationType.FINALIZED_MATCH_SYNC)
        val repository = RecordingQueueRepository(listOf(queuedEntry))
        val coordinator = ForegroundSyncQueueRetryCoordinator(
            repository = repository,
            executor = executor(
                finalizedMatchSync = FinalizedMatchCloudSyncRetryAction {
                    FinalizedMatchCloudSyncResult.AuthorizationFailure
                },
            ),
        )

        coordinator.retryEligible(listOf(queuedEntry), hasAuthenticatedSession = false)

        assertEquals(0, repository.enqueueCalls)
        assertEquals(1, repository.entries.size)
        assertEquals(1, repository.entries.single().attemptCount)
        assertEquals(SyncQueueStatus.FAILED_AUTHORIZATION, repository.entries.single().status)
        assertEquals(SyncQueueStatus.FAILED_AUTHORIZATION.name, repository.entries.single().failureCategory)
    }

    @Test fun coordinatorSkipsAuthenticationBlockedEntryWithoutSignedInSession() = runTest {
        val queuedEntry = entry(
            operationType = SyncQueueOperationType.MATCH_RESTORATION,
            status = SyncQueueStatus.BLOCKED_AUTHENTICATION,
        )
        val repository = RecordingQueueRepository(listOf(queuedEntry))
        var executed = false
        val coordinator = ForegroundSyncQueueRetryCoordinator(
            repository = repository,
            executor = executor(
                matchRestoration = MatchCloudRestorationRetryAction {
                    executed = true
                    MatchCloudRestorationResult.Success
                },
            ),
        )

        coordinator.retryEligible(listOf(queuedEntry), hasAuthenticatedSession = false)

        assertFalse(executed)
        assertTrue(repository.incrementedIds.isEmpty())
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.entries.single().status)
    }

    private fun executor(
        tournamentUpload: TournamentCloudUploadRetryAction = TournamentCloudUploadRetryAction { TournamentCloudUploadResult.Success },
        tournamentRestoration: TournamentCloudRestorationRetryAction = TournamentCloudRestorationRetryAction { TournamentCloudRestorationResult.Success("Tournament") },
        draftMatchSync: DraftMatchCloudSyncRetryAction = DraftMatchCloudSyncRetryAction { DraftMatchCloudSyncResult.Success },
        finalizedMatchSync: FinalizedMatchCloudSyncRetryAction = FinalizedMatchCloudSyncRetryAction { FinalizedMatchCloudSyncResult.Success },
        matchRestoration: MatchCloudRestorationRetryAction = MatchCloudRestorationRetryAction { MatchCloudRestorationResult.Success },
    ) = QueueOperationRetryExecutor(
        tournamentUpload = tournamentUpload,
        tournamentRestoration = tournamentRestoration,
        draftMatchSync = draftMatchSync,
        finalizedMatchSync = finalizedMatchSync,
        matchRestoration = matchRestoration,
    )

    private fun entry(
        operationType: SyncQueueOperationType,
        status: SyncQueueStatus = SyncQueueStatus.BLOCKED_NETWORK,
    ) = SyncQueueEntry(
        id = "entry-$operationType",
        operationType = operationType,
        tournamentId = "tournament-id",
        createdAtEpochMillis = 0,
        status = status,
        failureCategory = status.name,
        attemptCount = 0,
    )

    private class RecordingQueueRepository(
        initialEntries: List<SyncQueueEntry>,
    ) : PersistentSyncQueueRepository {
        val entries = initialEntries.toMutableList()
        val incrementedIds = mutableListOf<String>()
        var enqueueCalls = 0
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(entries)
        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry {
            enqueueCalls += 1
            error("Retry execution must not enqueue")
        }
        override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
        override suspend fun incrementAttemptCount(id: String) {
            incrementedIds += id
            replace(id) { it.copy(attemptCount = it.attemptCount + 1) }
        }
        override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) {
            replace(id) { it.copy(status = status, failureCategory = failureCategory) }
        }
        override suspend fun markCompleted(id: String) {
            replace(id) { it.copy(status = SyncQueueStatus.COMPLETED, failureCategory = null) }
        }
        override suspend fun remove(id: String) = Unit
        private fun replace(id: String, transform: (SyncQueueEntry) -> SyncQueueEntry) {
            val index = entries.indexOfFirst { it.id == id }
            entries[index] = transform(entries[index])
        }
    }
}
