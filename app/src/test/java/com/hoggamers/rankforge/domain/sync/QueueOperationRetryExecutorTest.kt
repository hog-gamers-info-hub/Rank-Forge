package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
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
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementRetryAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOperationRetryExecutorTest {
    @Test fun ownerSwitchAfterDownstreamActionReturnsSkippedOutcome() = runTest {
        val auth = SwitchingAuth(AuthState.SignedIn(AuthUser(OWNER_A, null)))
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val upload = object : TournamentCloudUploadRetryAction {
            override suspend fun executeForRetry(tournamentId: String) = error("expected owner is required")
            override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String): TournamentCloudUploadResult {
                started.complete(Unit)
                resume.await()
                return TournamentCloudUploadResult.Success(1)
            }
        }
        val executor = QueueOperationRetryExecutor(
            authRepository = auth,
            tournamentUpload = upload,
            tournamentRestoration = ownerBound(TournamentCloudRestorationRetryAction { TournamentCloudRestorationResult.Success("Tournament") }),
            draftMatchSync = ownerBound(DraftMatchCloudSyncRetryAction { DraftMatchCloudSyncResult.Success }),
            finalizedMatchSync = ownerBound(FinalizedMatchCloudSyncRetryAction { FinalizedMatchCloudSyncResult.Success(1) }),
            matchRestoration = ownerBound(MatchCloudRestorationRetryAction { MatchCloudRestorationResult.Success }),
            rosterReplacement = ownerBound(TournamentRosterCloudReplacementRetryAction { TournamentRosterCloudReplacementResult.Success(1) }),
        )

        var result: SyncQueueRetryOutcome? = null
        val job = launch { result = executor.execute(entry(SyncQueueOperationType.TOURNAMENT_UPLOAD)) }
        started.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OWNER_B, null))
        resume.complete(Unit)
        job.join()

        assertEquals(SyncQueueRetryOutcome.Skipped, result)
    }

    @Test fun dispatchesEveryOperationTypeToItsNoRecordRetryAction() = runTest {
        val calls = mutableListOf<String>()
        val executor = executor(
            tournamentUpload = TournamentCloudUploadRetryAction { id -> calls += "upload:$id"; TournamentCloudUploadResult.Success(1) },
            tournamentRestoration = TournamentCloudRestorationRetryAction { id -> calls += "tournament_restore:$id"; TournamentCloudRestorationResult.Success("Tournament") },
            draftMatchSync = DraftMatchCloudSyncRetryAction { id -> calls += "draft_sync:$id"; DraftMatchCloudSyncResult.Success },
            finalizedMatchSync = FinalizedMatchCloudSyncRetryAction { id -> calls += "finalized_sync:$id"; FinalizedMatchCloudSyncResult.Success(8) },
            matchRestoration = MatchCloudRestorationRetryAction { id -> calls += "match_restore:$id"; MatchCloudRestorationResult.Success },
            rosterReplacement = TournamentRosterCloudReplacementRetryAction { id -> calls += "roster_replacement:$id"; TournamentRosterCloudReplacementResult.Success(2) },
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
                "roster_replacement:tournament-id",
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

    @Test fun foreignOrLegacyQueueOwnershipNeverDispatchesCloudWork() = runTest {
        var cloudCalls = 0
        val foreign = executor(
            authRepository = testAuth("owner-b"),
            tournamentUpload = TournamentCloudUploadRetryAction {
                cloudCalls += 1
                TournamentCloudUploadResult.Success(1)
            },
        )

        assertEquals(SyncQueueRetryOutcome.Skipped, foreign.execute(entry(SyncQueueOperationType.TOURNAMENT_UPLOAD)))
        assertEquals(SyncQueueRetryOutcome.Skipped, executor().execute(entry(SyncQueueOperationType.TOURNAMENT_UPLOAD).copy(ownerUserId = null)))
        assertEquals(0, cloudCalls)
    }

    @Test fun dispatchPassesTheQueueOwnerToTheRetryAction() = runTest {
        var expectedOwner: String? = null
        val upload = object : TournamentCloudUploadRetryAction {
            override suspend fun executeForRetry(tournamentId: String) = error("expected owner is required")
            override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String): TournamentCloudUploadResult {
                expectedOwner = expectedOwnerUserId
                return TournamentCloudUploadResult.Success(1)
            }
        }
        val executor = QueueOperationRetryExecutor(
            authRepository = testAuth(),
            tournamentUpload = upload,
            tournamentRestoration = ownerBound(TournamentCloudRestorationRetryAction { TournamentCloudRestorationResult.Success("Tournament") }),
            draftMatchSync = ownerBound(DraftMatchCloudSyncRetryAction { DraftMatchCloudSyncResult.Success }),
            finalizedMatchSync = ownerBound(FinalizedMatchCloudSyncRetryAction { FinalizedMatchCloudSyncResult.Success(1) }),
            matchRestoration = ownerBound(MatchCloudRestorationRetryAction { MatchCloudRestorationResult.Success }),
            rosterReplacement = ownerBound(TournamentRosterCloudReplacementRetryAction { TournamentRosterCloudReplacementResult.Success(1) }),
        )

        assertEquals(SyncQueueRetryOutcome.Success, executor.execute(entry(SyncQueueOperationType.TOURNAMENT_UPLOAD)))
        assertEquals(OWNER_A, expectedOwner)
    }

    @Test fun rosterReplacementBlockedByMatchesIsFailedAsValidationWithoutEnqueueing() = runTest {
        val outcome = executor(
            rosterReplacement = TournamentRosterCloudReplacementRetryAction {
                TournamentRosterCloudReplacementResult.BlockedByExistingMatches
            },
        ).execute(entry(SyncQueueOperationType.ROSTER_REPLACEMENT))

        assertEquals(
            SyncQueueRetryOutcome.Failure(
                SyncQueueStatus.FAILED_VALIDATION,
                "ROSTER_REPLACEMENT_BLOCKED_BY_MATCHES",
            ),
            outcome,
        )
    }

    @Test fun tournamentLimitRetryIsPermanentlyClassifiedAsValidation() = runTest {
        val outcome = executor(
            tournamentUpload = TournamentCloudUploadRetryAction {
                TournamentCloudUploadResult.TournamentLimitReached
            },
        ).execute(entry(SyncQueueOperationType.TOURNAMENT_UPLOAD))

        assertEquals(
            SyncQueueRetryOutcome.Failure(
                SyncQueueStatus.FAILED_VALIDATION,
                "TOURNAMENT_LIMIT_REACHED",
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
            authRepository = testAuth(),
        )

        coordinator.retryEligible(listOf(queuedEntry), ownerUserId = OWNER_A)

        assertEquals(0, repository.enqueueCalls)
        assertEquals(1, repository.entries.size)
        assertEquals(1, repository.entries.single().attemptCount)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
    }

    @Test fun revisionCarryingFinalizedSuccessCompletesExistingQueueEntry() = runTest {
        val queuedEntry = entry(SyncQueueOperationType.FINALIZED_MATCH_SYNC)
        val repository = RecordingQueueRepository(listOf(queuedEntry))
        val coordinator = ForegroundSyncQueueRetryCoordinator(
            repository = repository,
            executor = executor(
                finalizedMatchSync = FinalizedMatchCloudSyncRetryAction {
                    FinalizedMatchCloudSyncResult.Success(8)
                },
            ),
            authRepository = testAuth(),
        )

        coordinator.retryEligible(listOf(queuedEntry), ownerUserId = OWNER_A)

        assertEquals(0, repository.enqueueCalls)
        assertEquals(1, repository.entries.single().attemptCount)
        assertEquals(SyncQueueStatus.COMPLETED, repository.entries.single().status)
        assertEquals(null, repository.entries.single().failureCategory)
    }

    @Test fun finalizedValidationFailureDoesNotCompleteQueueRetry() = runTest {
        val outcome = executor(
            finalizedMatchSync = FinalizedMatchCloudSyncRetryAction {
                FinalizedMatchCloudSyncResult.ValidationFailure
            },
        ).execute(entry(SyncQueueOperationType.FINALIZED_MATCH_SYNC))

        assertEquals(
            SyncQueueRetryOutcome.Failure(
                SyncQueueStatus.FAILED_VALIDATION,
                SyncQueueStatus.FAILED_VALIDATION.name,
            ),
            outcome,
        )
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
            authRepository = testAuth(),
        )

        coordinator.retryEligible(listOf(queuedEntry), ownerUserId = OWNER_A)

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
            authRepository = testAuthState(AuthState.SignedOut),
        )

        coordinator.retryEligible(listOf(queuedEntry), ownerUserId = OWNER_A)

        assertFalse(executed)
        assertTrue(repository.incrementedIds.isEmpty())
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, repository.entries.single().status)
    }

    private fun executor(
        authRepository: AuthRepository = testAuth(),
        tournamentUpload: TournamentCloudUploadRetryAction = TournamentCloudUploadRetryAction { TournamentCloudUploadResult.Success(1) },
        tournamentRestoration: TournamentCloudRestorationRetryAction = TournamentCloudRestorationRetryAction { TournamentCloudRestorationResult.Success("Tournament") },
        draftMatchSync: DraftMatchCloudSyncRetryAction = DraftMatchCloudSyncRetryAction { DraftMatchCloudSyncResult.Success },
        finalizedMatchSync: FinalizedMatchCloudSyncRetryAction = FinalizedMatchCloudSyncRetryAction { FinalizedMatchCloudSyncResult.Success(8) },
        matchRestoration: MatchCloudRestorationRetryAction = MatchCloudRestorationRetryAction { MatchCloudRestorationResult.Success },
        rosterReplacement: TournamentRosterCloudReplacementRetryAction = TournamentRosterCloudReplacementRetryAction { TournamentRosterCloudReplacementResult.Success(2) },
    ) = QueueOperationRetryExecutor(
        authRepository = authRepository,
        tournamentUpload = ownerBound(tournamentUpload),
        tournamentRestoration = ownerBound(tournamentRestoration),
        draftMatchSync = ownerBound(draftMatchSync),
        finalizedMatchSync = ownerBound(finalizedMatchSync),
        matchRestoration = ownerBound(matchRestoration),
        rosterReplacement = ownerBound(rosterReplacement),
    )

    private fun ownerBound(action: TournamentCloudUploadRetryAction) = object : TournamentCloudUploadRetryAction {
        override suspend fun executeForRetry(tournamentId: String) = action.executeForRetry(tournamentId)
        override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String) = action.executeForRetry(tournamentId)
    }
    private fun ownerBound(action: TournamentCloudRestorationRetryAction) = object : TournamentCloudRestorationRetryAction {
        override suspend fun executeForRetry(tournamentId: String) = action.executeForRetry(tournamentId)
        override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String) = action.executeForRetry(tournamentId)
    }
    private fun ownerBound(action: DraftMatchCloudSyncRetryAction) = object : DraftMatchCloudSyncRetryAction {
        override suspend fun executeForRetry(tournamentId: String) = action.executeForRetry(tournamentId)
        override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String) = action.executeForRetry(tournamentId)
    }
    private fun ownerBound(action: FinalizedMatchCloudSyncRetryAction) = object : FinalizedMatchCloudSyncRetryAction {
        override suspend fun executeForRetry(tournamentId: String) = action.executeForRetry(tournamentId)
        override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String) = action.executeForRetry(tournamentId)
    }
    private fun ownerBound(action: MatchCloudRestorationRetryAction) = object : MatchCloudRestorationRetryAction {
        override suspend fun executeForRetry(tournamentId: String) = action.executeForRetry(tournamentId)
        override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String) = action.executeForRetry(tournamentId)
    }
    private fun ownerBound(action: TournamentRosterCloudReplacementRetryAction) = object : TournamentRosterCloudReplacementRetryAction {
        override suspend fun executeForRetry(tournamentId: String) = action.executeForRetry(tournamentId)
        override suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String) = action.executeForRetry(tournamentId)
    }

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
        ownerUserId = OWNER_A,
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

    private fun testAuth(ownerUserId: String = OWNER_A): AuthRepository = testAuthState(
        AuthState.SignedIn(AuthUser(ownerUserId, "$ownerUserId@example.test")),
    )

    private fun testAuthState(state: AuthState): AuthRepository = object : AuthRepository {
        override fun observeAuthState() = flowOf(state)
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
