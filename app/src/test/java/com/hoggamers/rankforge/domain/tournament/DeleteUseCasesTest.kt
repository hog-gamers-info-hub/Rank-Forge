package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteUseCasesTest {
    @Test
    fun matchDeletionPurgesQueueThenDeletesStorageRemoteAndLocalInOrder() = runTest {
        val events = mutableListOf<String>()
        val repository = testRepository(withMatch = true)
        val cloud = RecordingCloud(events)
        val queue = RecordingQueue(events)
        val local = RecordingLocal(events)

        val result = DeleteMatchUseCase(
            repository,
            signedInAuth(),
            queue,
            cloud,
            local,
        )("match-1")

        assertEquals(DeleteMatchResult.Success, result)
        assertEquals(listOf("queue:tournament-1", "match-storage", "match-remote", "local-match:match-1"), events)
        assertEquals("match-1", cloud.matchId)
        assertEquals(listOf("tournament-1" to "owner-1"), queue.purgeCalls)
        assertEquals(listOf("match-1" to "owner-1"), local.matchOwnerCalls)
    }

    @Test
    fun matchStorageFailureStopsBeforeRemoteAndLocalDeletion() = runTest {
        val events = mutableListOf<String>()
        val cloud = RecordingCloud(events).apply {
            matchStorageResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.NETWORK)
        }

        val result = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            cloud,
            RecordingLocal(events),
        )("match-1")

        assertEquals(DeleteMatchResult.StorageDeletionFailed(CloudDeletionFailureCategory.NETWORK), result)
        assertEquals(listOf("queue:tournament-1", "match-storage"), events)
    }

    @Test
    fun matchRemoteFailureLeavesLocalTargetForRetry() = runTest {
        val events = mutableListOf<String>()
        val cloud = RecordingCloud(events).apply {
            matchRemoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.AUTHORIZATION)
        }
        val local = RecordingLocal(events)

        val result = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            cloud,
            local,
        )("match-1")

        assertEquals(DeleteMatchResult.RemoteDeletionFailed(CloudDeletionFailureCategory.AUTHORIZATION), result)
        assertTrue(local.matchCalls.isEmpty())
        assertEquals(listOf("queue:tournament-1", "match-storage", "match-remote"), events)
    }

    @Test
    fun matchLocalFailureHasDeterministicLocalOnlyRetry() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository()
        val local = RecordingLocal(events).apply {
            matchResults += LocalDeletionResult.FileCleanupFailed
            matchResults += LocalDeletionResult.Deleted
        }
        val useCase = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            RecordingCloud(events),
            local,
            intents,
        )

        assertEquals(DeleteMatchResult.RemoteDeletedLocalCleanupFailed, useCase("match-1"))
        assertEquals(
            DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
            intents.read(DeletionTargetType.MATCH, "match-1")?.phase,
        )
        assertEquals(DeleteMatchResult.Success, useCase.retryLocalCleanup("match-1"))
        assertNull(intents.read(DeletionTargetType.MATCH, "match-1"))
        assertEquals(
            listOf(
                "queue:tournament-1",
                "match-storage",
                "match-remote",
                "local-match:match-1",
                "local-match:match-1",
            ),
            events,
        )
    }

    @Test
    fun retryAfterUnauthorizedMatchDoesNotDeleteLocalData() = runTest {
        val events = mutableListOf<String>()
        val useCase = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            RecordingCloud(events).apply {
                matchRemoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.AUTHORIZATION)
            },
            RecordingLocal(events),
        )

        assertEquals(DeleteMatchResult.RemoteDeletionFailed(CloudDeletionFailureCategory.AUTHORIZATION), useCase("match-1"))
        assertEquals(DeleteMatchResult.TargetNotFound, useCase.retryLocalCleanup("match-1"))
        assertTrue(events.none { it.startsWith("local-") })
    }

    @Test
    fun signedOutMatchCannotStartDestructiveWork() = runTest {
        val events = mutableListOf<String>()

        val result = DeleteMatchUseCase(
            testRepository(withMatch = true),
            FakeAuthRepository(AuthState.SignedOut),
            RecordingQueue(events),
            RecordingCloud(events),
            RecordingLocal(events),
        )("match-1")

        assertEquals(DeleteMatchResult.AuthenticationRequired, result)
        assertTrue(events.isEmpty())
    }

    @Test
    fun blankForeignAndNullOwnerTargetsProduceNoDeletionSideEffects() = runTest {
        val scenarios = listOf(
            FakeAuthRepository(AuthState.SignedIn(AuthUser("", "blank@example.com"))) to "owner-1",
            signedInAuth() to "owner-2",
            signedInAuth() to null,
        )
        scenarios.forEach { (auth, targetOwner) ->
            val matchEvents = mutableListOf<String>()
            val tournamentEvents = mutableListOf<String>()
            val matchIntents = RecordingDeletionIntentRepository()
            val tournamentIntents = RecordingDeletionIntentRepository()
            assertEquals(
                if (targetOwner == "owner-1") DeleteMatchResult.AuthenticationRequired else DeleteMatchResult.TargetNotFound,
                DeleteMatchUseCase(
                    testRepository(withMatch = true, ownerUserId = targetOwner),
                    auth,
                    RecordingQueue(matchEvents),
                    RecordingCloud(matchEvents),
                    RecordingLocal(matchEvents),
                    matchIntents,
                )("match-1"),
            )
            assertEquals(
                if (targetOwner == "owner-1") DeleteTournamentResult.AuthenticationRequired else DeleteTournamentResult.TargetNotFound,
                DeleteTournamentUseCase(
                    testRepository(withMatch = true, ownerUserId = targetOwner),
                    auth,
                    RecordingQueue(tournamentEvents),
                    RecordingCloud(tournamentEvents),
                    RecordingLocal(tournamentEvents),
                    tournamentIntents,
                )("tournament-1"),
            )
            assertTrue(matchEvents.isEmpty())
            assertTrue(tournamentEvents.isEmpty())
            assertEquals(0, matchIntents.ownerScopedMutationCallCount)
            assertEquals(0, tournamentIntents.ownerScopedMutationCallCount)
        }
    }

    @Test
    fun foreignIntentCollisionsAreNotOverwrittenOrExecuted() = runTest {
        val matchEvents = mutableListOf<String>()
        val tournamentEvents = mutableListOf<String>()
        val matchIntents = RecordingDeletionIntentRepository().apply {
            put(intentFor(DeletionTargetType.MATCH, "match-1", "owner-2"))
        }
        val tournamentIntents = RecordingDeletionIntentRepository().apply {
            put(intentFor(DeletionTargetType.TOURNAMENT, "tournament-1", "owner-2"))
        }

        assertEquals(
            DeleteMatchResult.TargetNotFound,
            DeleteMatchUseCase(
                testRepository(withMatch = true), signedInAuth(), RecordingQueue(matchEvents),
                RecordingCloud(matchEvents), RecordingLocal(matchEvents), matchIntents,
            )("match-1"),
        )
        assertEquals(
            DeleteTournamentResult.TargetNotFound,
            DeleteTournamentUseCase(
                testRepository(withMatch = true), signedInAuth(), RecordingQueue(tournamentEvents),
                RecordingCloud(tournamentEvents), RecordingLocal(tournamentEvents), tournamentIntents,
            )("tournament-1"),
        )

        assertEquals("owner-2", matchIntents.read(DeletionTargetType.MATCH, "match-1")?.ownerUserId)
        assertEquals("owner-2", tournamentIntents.read(DeletionTargetType.TOURNAMENT, "tournament-1")?.ownerUserId)
        assertTrue(matchEvents.isEmpty())
        assertTrue(tournamentEvents.isEmpty())
    }

    @Test
    fun insertIgnoreSameOwnerRereadResumesSafely() = runTest {
        val matchEvents = mutableListOf<String>()
        val tournamentEvents = mutableListOf<String>()
        val matchIntents = RecordingDeletionIntentRepository().apply { collideNextInsertWithSameOwner = true }
        val tournamentIntents = RecordingDeletionIntentRepository().apply { collideNextInsertWithSameOwner = true }

        assertEquals(
            DeleteMatchResult.Success,
            DeleteMatchUseCase(
                testRepository(withMatch = true), signedInAuth(), RecordingQueue(matchEvents),
                RecordingCloud(matchEvents), RecordingLocal(matchEvents), matchIntents,
            )("match-1"),
        )
        assertEquals(
            DeleteTournamentResult.Success,
            DeleteTournamentUseCase(
                testRepository(withMatch = true), signedInAuth(), RecordingQueue(tournamentEvents),
                RecordingCloud(tournamentEvents), RecordingLocal(tournamentEvents), tournamentIntents,
            )("tournament-1"),
        )

        assertNull(matchIntents.read(DeletionTargetType.MATCH, "match-1"))
        assertNull(tournamentIntents.read(DeletionTargetType.TOURNAMENT, "tournament-1"))
        assertEquals(listOf("queue:tournament-1", "match-storage", "match-remote", "local-match:match-1"), matchEvents)
        assertEquals(
            listOf("queue:tournament-1", "tournament-storage", "tournament-remote", "local-tournament:tournament-1"),
            tournamentEvents,
        )
    }

    @Test
    fun tournamentDeletionPassesAllLocalMatchIdsWithoutRenumbering() = runTest {
        val events = mutableListOf<String>()
        val cloud = RecordingCloud(events)
        val queue = RecordingQueue(events)
        val local = RecordingLocal(events)
        val result = DeleteTournamentUseCase(
            testRepository(withMatch = true, withSecondMatch = true),
            signedInAuth(),
            queue,
            cloud,
            local,
        )("tournament-1")

        assertEquals(DeleteTournamentResult.Success, result)
        assertEquals(setOf("match-1", "match-3"), cloud.tournamentMatchIds)
        assertEquals(
            listOf("queue:tournament-1", "tournament-storage", "tournament-remote", "local-tournament:tournament-1"),
            events,
        )
        assertEquals(listOf("tournament-1" to "owner-1"), queue.purgeCalls)
        assertEquals(listOf("tournament-1" to "owner-1"), local.tournamentOwnerCalls)
    }

    @Test
    fun tournamentStorageFailureDoesNotDeleteRemoteParentOrLocalData() = runTest {
        val events = mutableListOf<String>()
        val cloud = RecordingCloud(events).apply {
            tournamentStorageResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.STORAGE)
        }

        val result = DeleteTournamentUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            cloud,
            RecordingLocal(events),
        )("tournament-1")

        assertEquals(DeleteTournamentResult.StorageDeletionFailed(CloudDeletionFailureCategory.STORAGE), result)
        assertEquals(listOf("queue:tournament-1", "tournament-storage"), events)
    }

    @Test
    fun tournamentRemoteFailurePreventsLocalDeletion() = runTest {
        val events = mutableListOf<String>()
        val cloud = RecordingCloud(events).apply {
            tournamentRemoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.NETWORK)
        }

        val result = DeleteTournamentUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            cloud,
            RecordingLocal(events),
        )("tournament-1")

        assertEquals(DeleteTournamentResult.RemoteDeletionFailed(CloudDeletionFailureCategory.NETWORK), result)
        assertEquals(listOf("queue:tournament-1", "tournament-storage", "tournament-remote"), events)
    }

    @Test
    fun tournamentRemoteSuccessFollowedByLocalFailureCanBeRetriedWithoutRemoteRecreation() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository()
        val local = RecordingLocal(events).apply {
            tournamentResults += LocalDeletionResult.FileCleanupFailed
            tournamentResults += LocalDeletionResult.NotFound
        }
        val useCase = DeleteTournamentUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            RecordingCloud(events),
            local,
            intents,
        )

        assertEquals(DeleteTournamentResult.RemoteDeletedLocalCleanupFailed, useCase("tournament-1"))
        assertEquals(
            DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
            intents.read(DeletionTargetType.TOURNAMENT, "tournament-1")?.phase,
        )
        assertEquals(DeleteTournamentResult.Success, useCase.retryLocalCleanup("tournament-1"))
        assertNull(intents.read(DeletionTargetType.TOURNAMENT, "tournament-1"))
        assertEquals(1, events.count { it == "tournament-remote" })
    }

    @Test
    fun clearFailureLeavesPendingIntentForARepeatableLocalOnlyRetry() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository().apply { failNextClear = true }
        val useCase = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            RecordingCloud(events),
            RecordingLocal(events),
            intents,
        )

        assertEquals(DeleteMatchResult.RemoteDeletedLocalCleanupFailed, useCase("match-1"))
        assertEquals(DeleteMatchResult.Success, useCase.retryLocalCleanup("match-1"))
        assertNull(intents.read(DeletionTargetType.MATCH, "match-1"))
        assertEquals(1, events.count { it == "match-remote" })
    }

    @Test
    fun pendingIntentBlocksQueueAndRemoteWorkUntilLocalDeletionFinishes() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository()
        intents.start(
            DeletionIntent(
                targetType = DeletionTargetType.MATCH,
                targetId = "match-1",
                tournamentId = "tournament-1",
                ownerUserId = "owner-1",
                phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
                updatedAtEpochMillis = 1,
            ),
        )
        val local = RecordingLocal(events).apply {
            matchResults += LocalDeletionResult.FileCleanupFailed
            matchResults += LocalDeletionResult.Deleted
        }
        val useCase = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            RecordingCloud(events),
            local,
            intents,
        )

        assertEquals(DeleteMatchResult.RemoteDeletedLocalCleanupFailed, useCase("match-1"))
        assertEquals(DeleteMatchResult.Success, useCase("match-1"))
        assertEquals(listOf("local-match:match-1", "local-match:match-1"), events)
    }

    @Test
    fun lostCleanupClaimIsReportedAndPendingIntentRemainsForRetry() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository().apply {
            put(intentFor(
                targetType = DeletionTargetType.MATCH,
                targetId = "match-1",
                ownerUserId = "owner-1",
                phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
            ))
        }
        val local = RecordingLocal(events).apply { matchResults += LocalDeletionResult.CleanupClaimLost }
        val useCase = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            RecordingCloud(events),
            local,
            intents,
        )

        assertEquals(DeleteMatchResult.RemoteDeletedLocalCleanupFailed, useCase("match-1"))
        assertEquals(
            DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
            intents.read(DeletionTargetType.MATCH, "match-1")?.phase,
        )
        assertEquals(listOf("local-match:match-1"), events)
    }

    @Test
    fun sameOwnerPendingTournamentIntentResumesLocalOnlyCleanup() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository().apply {
            put(intentFor(
                targetType = DeletionTargetType.TOURNAMENT,
                targetId = "tournament-1",
                ownerUserId = "owner-1",
                phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
            ))
        }

        assertEquals(
            DeleteTournamentResult.Success,
            DeleteTournamentUseCase(
                testRepository(withMatch = true), signedInAuth(), RecordingQueue(events),
                RecordingCloud(events), RecordingLocal(events), intents,
            )("tournament-1"),
        )
        assertEquals(listOf("local-tournament:tournament-1"), events)
        assertNull(intents.read(DeletionTargetType.TOURNAMENT, "tournament-1"))
    }

    @Test
    fun retryAfterRemoteCommitBeforePendingPhaseCompletesLocalCleanup() = runTest {
        val events = mutableListOf<String>()
        val intents = RecordingDeletionIntentRepository().apply {
            failNextMarkRemoteDeleted = true
        }
        val cloud = RecordingCloud(events)
        val local = RecordingLocal(events)
        val useCase = DeleteMatchUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events),
            cloud,
            local,
            intents,
        )

        assertEquals(DeleteMatchResult.RemoteDeletedLocalCleanupFailed, useCase("match-1"))
        assertTrue(local.matchCalls.isEmpty())
        assertEquals(DeleteMatchResult.Success, useCase("match-1"))
        assertEquals(2, events.count { it == "match-remote" })
        assertEquals(1, events.count { it == "local-match:match-1" })
    }

    @Test
    fun queuePreparationFailureStopsAllDeletionStages() = runTest {
        val events = mutableListOf<String>()
        val result = DeleteTournamentUseCase(
            testRepository(withMatch = true),
            signedInAuth(),
            RecordingQueue(events, fail = true),
            RecordingCloud(events),
            RecordingLocal(events),
        )("tournament-1")

        assertEquals(DeleteTournamentResult.PendingSyncPreparationFailed, result)
        assertEquals(listOf("queue:tournament-1"), events)
    }

    private fun testRepository(
        withMatch: Boolean,
        withSecondMatch: Boolean = false,
        ownerUserId: String? = "owner-1",
    ) = TestTournamentRepository(
        tournament = Tournament(
            id = "tournament-1",
            name = "Test Cup",
            date = LocalDate.of(2026, 8, 21),
            organizerName = "Test Organizer",
            organizerContactNumber = "000",
            status = TournamentStatus.DRAFT,
            ownerUserId = ownerUserId,
        ),
        matches = buildList {
            if (withMatch) add(Match("match-1", "tournament-1", 1, LocalDate.of(2026, 8, 21), "Map A", MatchStatus.DRAFT))
            if (withSecondMatch) add(Match("match-3", "tournament-1", 3, LocalDate.of(2026, 8, 21), "Map B", MatchStatus.DRAFT))
        },
    )

    private fun signedInAuth() = FakeAuthRepository(
        AuthState.SignedIn(AuthUser("owner-1", "owner@example.com")),
    )

    private fun intentFor(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
        phase: DeletionIntentPhase = DeletionIntentPhase.DELETE_STARTED,
    ) = DeletionIntent(
        targetType = targetType,
        targetId = targetId,
        tournamentId = "tournament-1",
        ownerUserId = ownerUserId,
        phase = phase,
        updatedAtEpochMillis = 1,
    )
}

private class TestTournamentRepository(
    private val tournament: Tournament,
    private val matches: List<Match>,
) : TournamentRepository {
    override suspend fun create(tournament: Tournament) = Unit
    override fun observeAll(): Flow<List<Tournament>> = flowOf(listOf(tournament))
    override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(tournament.takeIf { it.id == tournamentId })
    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())
    override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
    override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
    override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
    override suspend fun confirmTournament(tournamentId: String): Boolean = false
    override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> = flowOf(matches.filter { it.tournamentId == tournamentId })
    override fun observeMatchById(matchId: String): Flow<Match?> = flowOf(matches.firstOrNull { it.id == matchId })
}

private class FakeAuthRepository(private val state: AuthState) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = flowOf(state)
    override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
    override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()
    override suspend fun login(email: String, password: String): AuthOperationResult = failure()
    override suspend fun logout(): AuthOperationResult = failure()
    private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
}

private class RecordingQueue(
    private val events: MutableList<String>,
    private val fail: Boolean = false,
) : PersistentSyncQueueRepository {
    val purgeCalls = mutableListOf<Pair<String, String>>()
    override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())
    override suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?) =
        SyncQueueEntry("queue-id", operationType, tournamentId, 0L, status, failureCategory, 0)
    override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
    override suspend fun incrementAttemptCount(id: String) = Unit
    override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
    override suspend fun markCompleted(id: String) = Unit
    override suspend fun remove(id: String) = Unit
    override suspend fun purgeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) {
        purgeCalls += tournamentId to ownerUserId
        events += "queue:$tournamentId"
        if (fail) error("queue unavailable")
    }
}

private class RecordingCloud(private val events: MutableList<String>) : CloudDeletionRepository {
    var matchStorageResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var matchRemoteResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var tournamentStorageResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var tournamentRemoteResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var matchId: String? = null
    var tournamentMatchIds: Set<String> = emptySet()

    override suspend fun deleteMatchStorage(tournamentId: String, matchId: String): CloudDeletionStageResult {
        events += "match-storage"
        return matchStorageResult
    }
    override suspend fun deleteMatchRemote(tournamentId: String, matchId: String): CloudDeletionStageResult {
        events += "match-remote"
        this.matchId = matchId
        return matchRemoteResult
    }
    override suspend fun deleteTournamentStorage(tournamentId: String, matchIds: Set<String>): CloudDeletionStageResult {
        events += "tournament-storage"
        tournamentMatchIds = matchIds
        return tournamentStorageResult
    }
    override suspend fun deleteTournamentRemote(tournamentId: String): CloudDeletionStageResult {
        events += "tournament-remote"
        return tournamentRemoteResult
    }
}

private class RecordingLocal(private val events: MutableList<String>) : LocalDeletionRepository {
    val matchCalls = mutableListOf<String>()
    val matchOwnerCalls = mutableListOf<Pair<String, String>>()
    val tournamentOwnerCalls = mutableListOf<Pair<String, String>>()
    val matchResults = mutableListOf<LocalDeletionResult>()
    val tournamentResults = mutableListOf<LocalDeletionResult>()

    override suspend fun deleteMatchLocallyByOwner(matchId: String, ownerUserId: String): LocalDeletionResult {
        matchOwnerCalls += matchId to ownerUserId
        events += "local-match:$matchId"
        matchCalls += matchId
        return matchResults.removeFirstOrNull() ?: LocalDeletionResult.Deleted
    }
    override suspend fun deleteTournamentLocallyByOwner(
        tournamentId: String,
        ownerUserId: String,
    ): LocalDeletionResult {
        tournamentOwnerCalls += tournamentId to ownerUserId
        events += "local-tournament:$tournamentId"
        return tournamentResults.removeFirstOrNull() ?: LocalDeletionResult.Deleted
    }
}

private class RecordingDeletionIntentRepository : DeletionIntentRepository {
    private val intents = mutableMapOf<Pair<DeletionTargetType, String>, DeletionIntent>()
    var failNextClear: Boolean = false
    var failNextMarkRemoteDeleted: Boolean = false
    var collideNextInsertWithSameOwner: Boolean = false
    var ownerScopedMutationCallCount: Int = 0

    fun put(intent: DeletionIntent) {
        intents[intent.targetType to intent.targetId] = intent
    }

    override suspend fun read(targetType: DeletionTargetType, targetId: String): DeletionIntent? =
        intents[targetType to targetId]

    override suspend fun findByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): DeletionIntent? {
        return intents[targetType to targetId]?.takeIf { it.ownerUserId == ownerUserId }
    }

    override suspend fun start(intent: DeletionIntent): DeletionIntent {
        intents[intent.targetType to intent.targetId] = intent
        return intent
    }

    override suspend fun startIfAbsent(intent: DeletionIntent): Boolean {
        ownerScopedMutationCallCount++
        val key = intent.targetType to intent.targetId
        if (collideNextInsertWithSameOwner) {
            collideNextInsertWithSameOwner = false
            intents[key] = intent
            return false
        }
        if (key in intents) return false
        intents[key] = intent
        return true
    }

    override suspend fun markRemoteDeleted(targetType: DeletionTargetType, targetId: String) {
        if (failNextMarkRemoteDeleted) {
            failNextMarkRemoteDeleted = false
            error("process ended before intent phase update")
        }
        val current = requireNotNull(intents[targetType to targetId])
        intents[targetType to targetId] = current.copy(
            phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
        )
    }

    override suspend fun markRemoteDeletedByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean {
        ownerScopedMutationCallCount++
        val current = intents[targetType to targetId]?.takeIf { it.ownerUserId == ownerUserId } ?: return false
        if (failNextMarkRemoteDeleted) {
            failNextMarkRemoteDeleted = false
            error("process ended before intent phase update")
        }
        intents[targetType to targetId] = current.copy(
            phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
        )
        return true
    }

    override suspend fun clear(targetType: DeletionTargetType, targetId: String) {
        if (failNextClear) {
            failNextClear = false
            error("intent clear unavailable")
        }
        intents.remove(targetType to targetId)
    }

    override suspend fun clearByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean {
        ownerScopedMutationCallCount++
        val current = intents[targetType to targetId]?.takeIf { it.ownerUserId == ownerUserId } ?: return false
        if (failNextClear) {
            failNextClear = false
            error("intent clear unavailable")
        }
        return intents.remove(targetType to targetId, current)
    }

    override suspend fun isBlocking(tournamentId: String): Boolean =
        intents.values.any { it.tournamentId == tournamentId }

    override suspend fun isBlockingByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean {
        return intents.values.any { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId }
    }

    override suspend fun readAll(): List<DeletionIntent> = intents.values.toList()

    override suspend fun readPendingLocalCleanup(): List<DeletionIntent> =
        intents.values.filter { it.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING }

    override suspend fun readPendingLocalCleanupByOwner(ownerUserId: String): List<DeletionIntent> =
        intents.values.filter {
            it.ownerUserId == ownerUserId &&
                it.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
        }
}
