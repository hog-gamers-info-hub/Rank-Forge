package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverForegroundSyncQueueUseCaseTest {
    @Test fun authenticatedForegroundRecoveryInspectsQueueAndRetriesEligibleEntries() = runTest {
        val networkEntry = entry("network", SyncQueueStatus.BLOCKED_NETWORK)
        val authenticationEntry = entry(
            id = "authentication",
            status = SyncQueueStatus.BLOCKED_AUTHENTICATION,
            operationType = SyncQueueOperationType.DRAFT_MATCH_SYNC,
        )
        val repository = RecordingQueueRepository(listOf(networkEntry, authenticationEntry))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()),
            authRepository = testAuth(),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertEquals(listOf(networkEntry.id, authenticationEntry.id), executor.executedIds)
        assertEquals(1, repository.entries.first { it.id == networkEntry.id }.attemptCount)
        assertEquals(1, repository.entries.first { it.id == authenticationEntry.id }.attemptCount)
        assertTrue(repository.entries.all { it.status == SyncQueueStatus.COMPLETED })
    }

    @Test fun signedOutAndBlankSessionsDispatchNoQueueWork() = runTest {
        listOf(
            AuthState.SignedOut,
            AuthState.SignedIn(AuthUser("   ", "blank@example.test")),
        ).forEach { state ->
            val entry = entry("entry", SyncQueueStatus.BLOCKED_NETWORK)
            val repository = RecordingQueueRepository(listOf(entry))
            val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
            val auth = testAuthState(state)
            RecoverForegroundSyncQueueUseCase(
                queueRepository = repository,
                retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, auth),
                authRepository = auth,
            ).recoverAfterAuthenticatedSession()
            assertTrue(executor.executedIds.isEmpty())
            assertEquals(0, repository.entries.single().attemptCount)
        }
    }

    @Test fun recoveryReadsAndRetriesOnlyTheCurrentOwnersRows() = runTest {
        val a = entry("a", SyncQueueStatus.BLOCKED_NETWORK, ownerUserId = OWNER_A)
        val b = entry("b", SyncQueueStatus.BLOCKED_NETWORK, ownerUserId = "owner-b")
        val legacy = entry("legacy", SyncQueueStatus.BLOCKED_NETWORK, ownerUserId = null)
        val repository = RecordingQueueRepository(listOf(a, b, legacy))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val auth = testAuth("owner-b")
        RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, auth),
            authRepository = auth,
        ).recoverAfterAuthenticatedSession()

        assertEquals(listOf("b"), executor.executedIds)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.first { it.id == "a" }.status)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.first { it.id == "legacy" }.status)
    }

    @Test fun accountSwitchAfterOwnerSelectionDoesNotDispatchThePreviousOwnersWork() = runTest {
        val entry = entry("a", SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingQueueRepository(listOf(entry))
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val auth = switchingAuth(OWNER_A, "owner-b")
        RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, auth),
            authRepository = auth,
        ).recoverAfterAuthenticatedSession()

        assertTrue(executor.executedIds.isEmpty())
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.single().status)
        assertEquals(0, repository.entries.single().attemptCount)
    }

    @Test fun nonRetryablePersistedEntriesRemainUnchanged() = runTest {
        val entries = listOf(
            SyncQueueStatus.PENDING,
            SyncQueueStatus.FAILED_VALIDATION,
            SyncQueueStatus.FAILED_AUTHORIZATION,
            SyncQueueStatus.FAILED_LOCAL,
            SyncQueueStatus.FAILED_UNKNOWN,
            SyncQueueStatus.COMPLETED,
        ).map { entry(it.name, it) }
        val repository = RecordingQueueRepository(entries)
        val executor = RecordingExecutor(SyncQueueRetryOutcome.Success)
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()),
            authRepository = testAuth(),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertTrue(executor.executedIds.isEmpty())
        assertEquals(entries, repository.entries)
    }

    @Test fun queueInspectionFailureIsIsolatedFromForegroundSessionRecovery() = runTest {
        val repository = RecordingQueueRepository(emptyList(), failOnObserve = true)
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(
                repository,
                RecordingExecutor(SyncQueueRetryOutcome.Success),
                testAuth(),
            ),
            authRepository = testAuth(),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertTrue(repository.entries.isEmpty())
    }

    @Test fun interruptedQueueRetryIsIsolatedAndLaterRecoveryCompletesSameEntry() = runTest {
        val entry = entry("recovery-entry", SyncQueueStatus.BLOCKED_NETWORK)
        val repository = RecordingQueueRepository(listOf(entry))
        val executor = InterruptingThenSuccessExecutor()
        val recovery = RecoverForegroundSyncQueueUseCase(
            queueRepository = repository,
            retryCoordinator = ForegroundSyncQueueRetryCoordinator(repository, executor, testAuth()),
            authRepository = testAuth(),
        )

        recovery.recoverAfterAuthenticatedSession()

        assertEquals(listOf(entry.id), executor.executedEntries.map { it.id })
        assertEquals(1, executor.executedEntries.single().attemptCount)
        assertEquals(1, repository.entries.size)
        assertEquals(entry.id, repository.entries.single().id)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, repository.entries.single().status)
        assertEquals(1, repository.entries.single().attemptCount)
        assertTrue(repository.completedIds.isEmpty())

        recovery.recoverAfterAuthenticatedSession()

        assertEquals(listOf(entry.id, entry.id), executor.executedEntries.map { it.id })
        assertEquals(listOf(1, 2), executor.executedEntries.map { it.attemptCount })
        assertEquals(entry.id, repository.entries.single().id)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
        assertEquals(2, repository.entries.single().attemptCount)
        assertEquals(listOf(entry.id), repository.completedIds)
        assertEquals(0, repository.enqueueCalls)
    }

    private fun entry(
        id: String,
        status: SyncQueueStatus,
        operationType: SyncQueueOperationType = SyncQueueOperationType.TOURNAMENT_UPLOAD,
        ownerUserId: String? = OWNER_A,
    ) = SyncQueueEntry(
        id = id,
        operationType = operationType,
        tournamentId = "tournament-id",
        createdAtEpochMillis = 0,
        status = status,
        failureCategory = status.name,
        attemptCount = 0,
        ownerUserId = ownerUserId,
    )

    private class RecordingExecutor(
        private val outcome: SyncQueueRetryOutcome,
    ) : SyncQueueEntryRetryExecutor {
        val executedIds = mutableListOf<String>()
        override suspend fun execute(entry: SyncQueueEntry): SyncQueueRetryOutcome = outcome.also {
            executedIds += entry.id
        }
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

    private class RecordingQueueRepository(
        initialEntries: List<SyncQueueEntry>,
        private val failOnObserve: Boolean = false,
    ) : PersistentSyncQueueRepository {
        val entries = initialEntries.toMutableList()
        val completedIds = mutableListOf<String>()
        var enqueueCalls = 0
        override fun observeAll(): Flow<List<SyncQueueEntry>> = if (failOnObserve) {
            flow { throw IllegalStateException("queue unavailable") }
        } else {
            flowOf(entries)
        }
        override fun observePendingByOwner(ownerUserId: String): Flow<List<SyncQueueEntry>> = if (failOnObserve) {
            flow { throw IllegalStateException("queue unavailable") }
        } else {
            flowOf(entries.filter { it.ownerUserId == ownerUserId })
        }
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
            replaceOwned(id, ownerUserId) { it.copy(attemptCount = it.attemptCount + 1) }
        }
        override suspend fun updateRetryFailureByOwner(id: String, ownerUserId: String, status: SyncQueueStatus, failureCategory: String?) {
            replaceOwned(id, ownerUserId) { it.copy(status = status, failureCategory = failureCategory) }
        }
        override suspend fun markCompletedByOwner(id: String, ownerUserId: String) {
            completedIds += id
            replaceOwned(id, ownerUserId) { it.copy(status = SyncQueueStatus.COMPLETED, failureCategory = null) }
        }
        private fun replace(id: String, transform: (SyncQueueEntry) -> SyncQueueEntry) {
            val index = entries.indexOfFirst { it.id == id }
            entries[index] = transform(entries[index])
        }
        private fun replaceOwned(id: String, ownerUserId: String, transform: (SyncQueueEntry) -> SyncQueueEntry) {
            val index = entries.indexOfFirst { it.id == id && it.ownerUserId == ownerUserId }
            if (index >= 0) entries[index] = transform(entries[index])
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

    private fun testAuthState(state: AuthState): AuthRepository = object : AuthRepository {
        override fun observeAuthState() = flowOf(state)
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = failure()
        override suspend fun login(email: String, password: String) = failure()
        override suspend fun logout() = failure()
        private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
    }

    private fun switchingAuth(firstOwnerId: String, laterOwnerId: String): AuthRepository = object : AuthRepository {
        private var observations = 0
        override fun observeAuthState() = flowOf(
            AuthState.SignedIn(
                AuthUser(
                    if (observations++ == 0) firstOwnerId else laterOwnerId,
                    "owner@example.test",
                ),
            ),
        )
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = failure()
        override suspend fun login(email: String, password: String) = failure()
        override suspend fun logout() = failure()
        private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
    }

    private companion object { const val OWNER_A = "owner-a" }
}
