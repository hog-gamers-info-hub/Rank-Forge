package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ForegroundSyncQueueRetryCoordinatorTest {
    private val policy = SyncQueueRetryEligibilityPolicy()

    @Test fun bothRetryableStatusesAreEligibleForEveryOperationTypeWhenSessionRequirementsAreMet() {
        SyncQueueOperationType.entries.forEach { operationType ->
            assertTrue(policy.isEligible(entry(operationType, SyncQueueStatus.BLOCKED_NETWORK), hasAuthenticatedSession = false))
            assertTrue(policy.isEligible(entry(operationType, SyncQueueStatus.BLOCKED_AUTHENTICATION), hasAuthenticatedSession = true))
        }
    }

    @Test fun blockedAuthenticationIsRetryableOnlyWithAuthenticatedSession() {
        val entry = entry(SyncQueueOperationType.TOURNAMENT_UPLOAD, SyncQueueStatus.BLOCKED_AUTHENTICATION)
        assertFalse(policy.isEligible(entry, hasAuthenticatedSession = false))
        assertTrue(policy.isEligible(entry, hasAuthenticatedSession = true))
    }

    @Test fun missingRevisionConflictIsRetryableForRecovery() {
        val entry = entry(
            SyncQueueOperationType.ROSTER_REPLACEMENT,
            SyncQueueStatus.FAILED_CONFLICT,
        ).copy(failureCategory = "MISSING_REVISION")

        assertTrue(policy.isEligible(entry, hasAuthenticatedSession = false))
    }

    @Test fun otherConflictCategoriesRemainNonRetryable() {
        listOf("STALE_WRITE_CONFLICT", "LOCAL_CLOUD_DIVERGENCE", "AUTHORIZATION").forEach { category ->
            val entry = entry(
                SyncQueueOperationType.ROSTER_REPLACEMENT,
                SyncQueueStatus.FAILED_CONFLICT,
            ).copy(failureCategory = category)
            assertFalse(policy.isEligible(entry, hasAuthenticatedSession = true))
        }
    }

    @Test fun nonRetryableStatusesAreSkipped() = runTest {
        val repository = RecordingRepository()
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val entries = listOf(
            SyncQueueStatus.PENDING,
            SyncQueueStatus.FAILED_VALIDATION,
            SyncQueueStatus.FAILED_AUTHORIZATION,
            SyncQueueStatus.FAILED_LOCAL,
            SyncQueueStatus.FAILED_UNKNOWN,
            SyncQueueStatus.COMPLETED,
        ).map { entry(SyncQueueOperationType.MATCH_RESTORATION, it) }

        val attempted = ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()).retryEligible(entries, ownerUserId = OWNER_A)

        assertTrue(attempted.isEmpty())
        assertTrue(repository.incrementedIds.isEmpty())
        assertTrue(executor.executedEntries.isEmpty())
    }

    @Test fun eligibleEntryIncrementsOnceDelegatesAndMarksCompletedWithoutRemovingEntry() = runTest {
        val entry = entry(SyncQueueOperationType.DRAFT_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK, attemptCount = 2)
        val repository = RecordingRepository(listOf(entry))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)

        val attempted = ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()).retryEligible(listOf(entry), ownerUserId = OWNER_A)

        assertEquals(listOf(entry.id), repository.incrementedIds)
        assertEquals(3, executor.executedEntries.single().attemptCount)
        assertEquals(listOf(entry.id), repository.completedIds)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
        assertEquals(3, repository.entries.single().attemptCount)
        assertEquals(1, repository.entries.size)
        assertEquals(3, attempted.single().attemptCount)
    }

    @Test fun duplicateEligibleEntriesExecuteOnlyTheOldestOperationIdentity() = runTest {
        val oldest = entry(SyncQueueOperationType.DRAFT_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK).copy(
            id = "oldest",
            createdAtEpochMillis = 1,
        )
        val duplicate = oldest.copy(id = "duplicate", createdAtEpochMillis = 2)
        val repository = RecordingRepository(listOf(oldest, duplicate))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)

        ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()).retryEligible(
            listOf(duplicate, oldest),
            ownerUserId = OWNER_A,
        )

        assertEquals(listOf("oldest"), executor.executedEntries.map { it.id })
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.first { it.id == "oldest" }.status)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.first { it.id == "duplicate" }.status)
    }

    @Test fun failedEligibleRetryUpdatesStatusAndFailureMetadata() = runTest {
        val entry = entry(SyncQueueOperationType.FINALIZED_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingRepository(listOf(entry))
        val executor = RecordingExecutor(
            SyncQueueRetryOutcome.Failure(SyncQueueStatus.BLOCKED_AUTHENTICATION, "session_expired"),
        )

        ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()).retryEligible(listOf(entry), ownerUserId = OWNER_A)

        assertEquals(listOf(entry.id), repository.incrementedIds)
        assertEquals(emptyList<String>(), repository.completedIds)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.entries.single().status)
        assertEquals("session_expired", repository.entries.single().failureCategory)
        assertEquals(1, repository.entries.single().attemptCount)
    }

    @Test fun interruptedRetryRetainsSameEntryForLaterSuccessfulRetry() = runTest {
        val entry = entry(SyncQueueOperationType.FINALIZED_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingRepository(listOf(entry))
        val executor = InterruptingThenSuccessExecutor()
        val coordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth())

        try {
            coordinator.retryEligible(repository.entries.toList(), ownerUserId = OWNER_A)
            fail("The interrupted retry should propagate its execution failure.")
        } catch (_: IllegalStateException) {
            // The interrupted execution must leave the existing entry unresolved.
        }

        assertEquals(listOf(entry.id), repository.incrementedIds)
        assertTrue(repository.completedIds.isEmpty())
        assertEquals(1, repository.entries.size)
        assertEquals(entry.id, repository.entries.single().id)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.single().status)
        assertEquals(1, repository.entries.single().attemptCount)

        val attempted = coordinator.retryEligible(repository.entries.toList(), ownerUserId = OWNER_A)

        assertEquals(listOf(entry.id, entry.id), executor.executedEntries.map { it.id })
        assertEquals(listOf(1, 2), executor.executedEntries.map { it.attemptCount })
        assertEquals(listOf(entry.id, entry.id), repository.incrementedIds)
        assertEquals(listOf(entry.id), repository.completedIds)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
        assertEquals(2, repository.entries.single().attemptCount)
        assertEquals(entry.id, attempted.single().id)
        assertEquals(0, repository.enqueueCalls)
    }

    @Test fun ownerSwitchDuringDispatchLeavesQueueRowAndAttemptUnchanged() = runTest {
        val entry = entry(SyncQueueOperationType.DRAFT_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingRepository(listOf(entry))
        val auth = SwitchingAuth(AuthState.SignedIn(AuthUser(OWNER_A, null)))
        val executor = SuspendingExecutor()
        val job = launch {
            ForegroundSyncQueueRetryCoordinator(repository, executor, auth)
                .retryEligible(listOf(entry), ownerUserId = OWNER_A)
        }

        executor.started.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OWNER_B, null))
        executor.resume.complete(Unit)
        job.join()

        assertEquals(entry, repository.entries.single())
        assertTrue(repository.incrementedIds.isEmpty())
        assertTrue(repository.completedIds.isEmpty())
    }

    @Test fun signOutDuringDispatchLeavesQueueRowAndAttemptUnchanged() = runTest {
        val entry = entry(SyncQueueOperationType.DRAFT_MATCH_SYNC, SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingRepository(listOf(entry))
        val auth = SwitchingAuth(AuthState.SignedIn(AuthUser(OWNER_A, null)))
        val executor = SuspendingExecutor()
        val job = launch {
            ForegroundSyncQueueRetryCoordinator(repository, executor, auth)
                .retryEligible(listOf(entry), ownerUserId = OWNER_A)
        }

        executor.started.await()
        auth.state.value = AuthState.SignedOut
        executor.resume.complete(Unit)
        job.join()

        assertEquals(entry, repository.entries.single())
        assertTrue(repository.incrementedIds.isEmpty())
        assertTrue(repository.completedIds.isEmpty())
    }

    private fun entry(
        operationType: SyncQueueOperationType,
        status: SyncQueueStatus,
        attemptCount: Int = 0,
    ) = SyncQueueEntry(
        id = "$operationType-$status",
        operationType = operationType,
        tournamentId = "tournament-id",
        createdAtEpochMillis = 0,
        status = status,
        failureCategory = status.name,
        attemptCount = attemptCount,
        ownerUserId = OWNER_A,
    )

    private class RecordingExecutor(
        private val outcome: SyncQueueRetryOutcome,
    ) : SyncQueueEntryRetryExecutor {
        val executedEntries = mutableListOf<SyncQueueEntry>()
        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome = outcome.also { executedEntries += entry }
    }

    private class InterruptingThenSuccessExecutor : SyncQueueEntryRetryExecutor {
        val executedEntries = mutableListOf<SyncQueueEntry>()
        private var executionCount = 0

        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome {
            executedEntries += entry
            executionCount += 1
            if (executionCount == 1) throw IllegalStateException("retry interrupted")
            return SyncQueueRetryOutcome.Success
        }
    }

    private class SuspendingExecutor : SyncQueueEntryRetryExecutor {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome {
            started.complete(Unit)
            resume.await()
            return SyncQueueRetryOutcome.Success
        }
    }

    private class RecordingRepository(
        initialEntries: List<SyncQueueEntry> = emptyList(),
    ) : PersistentSyncQueueRepository {
        val entries = initialEntries.toMutableList()
        val incrementedIds = mutableListOf<String>()
        val completedIds = mutableListOf<String>()
        var enqueueCalls = 0
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(entries)
        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry {
            enqueueCalls += 1
            error("not used")
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
            completedIds += id
            replace(id) { it.copy(status = SyncQueueStatus.COMPLETED, failureCategory = null) }
        }
        override suspend fun remove(id: String) = Unit
        override suspend fun incrementAttemptCountByOwner(id: String, ownerUserId: String) {
            require(ownerUserId == OWNER_A)
            incrementAttemptCount(id)
        }
        override suspend fun updateRetryFailureByOwner(id: String, ownerUserId: String, status: SyncQueueStatus, failureCategory: String?) {
            require(ownerUserId == OWNER_A)
            updateRetryFailure(id, status, failureCategory)
        }
        override suspend fun markCompletedByOwner(id: String, ownerUserId: String) {
            require(ownerUserId == OWNER_A)
            markCompleted(id)
        }
        private fun replace(id: String, transform: (SyncQueueEntry) -> SyncQueueEntry) {
            val index = entries.indexOfFirst { it.id == id }
            entries[index] = transform(entries[index])
        }
    }

    private fun testAuth(ownerUserId: String = OWNER_A): AuthRepository = object : AuthRepository {
        override fun observeAuthState() = flowOf(AuthState.SignedIn(AuthUser(ownerUserId, "$ownerUserId@example.test")))
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = failure()
        override suspend fun login(email: String, password: String) = failure()
        override suspend fun logout() = failure()
        private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
    }

    private class SwitchingAuth(initial: AuthState) : AuthRepository {
        val state = MutableStateFlow(initial)
        override fun observeAuthState() = state
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = failure()
        override suspend fun login(email: String, password: String) = failure()
        override suspend fun logout() = failure()
        private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
    }
}
