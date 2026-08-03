package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceTournamentRosterInCloudUseCaseTest {
    @Test
    fun authenticatedReplacementSendsCompleteLocalStateAndConfirmsReturnedRevision() = runTest {
        val repository = localRepository()
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(7))
        val result = useCase(repository, cloud).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(7), result)
        assertEquals(OWNER_ID, cloud.ownerId)
        assertEquals(12, cloud.snapshot?.slots?.size)
        assertEquals(1, cloud.snapshot?.expectedCloudRevision)
        assertEquals(7, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun conflictDoesNotAdvanceLocalCloudRevision() = runTest {
        val repository = localRepository()
        val result = useCase(
            repository,
            FakeCloud(
                TournamentRosterCloudReplacementResult.Conflict(
                    RevisionConflict.StaleWrite(
                        com.hoggamers.rankforge.domain.sync.CloudRevision(1),
                        com.hoggamers.rankforge.domain.sync.CloudRevision(2),
                    ),
                ),
            ),
        ).executeForRetry(TOURNAMENT_ID)

        assertTrue(result is TournamentRosterCloudReplacementResult.Conflict)
        assertEquals(1, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun retryRereadsCurrentLocalRosterAfterLocalMutation() = runTest {
        val repository = localRepository()
        repository.confirmTournament(TOURNAMENT_ID)
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(7))
        val action = useCase(repository, cloud)

        assertEquals(
            TournamentRosterCloudReplacementResult.Success(7),
            action.executeForRetry(TOURNAMENT_ID),
        )
        assertEquals(
            listOf("Player One"),
            cloud.snapshots[0].rosters.getValue(1).map { it.displayName },
        )

        repository.saveRoster(
            TOURNAMENT_ID,
            1,
            listOf(RosterPlayer(TOURNAMENT_ID, 1, "Player Two")),
        )
        repository.confirmTournament(TOURNAMENT_ID)

        assertEquals(
            TournamentRosterCloudReplacementResult.Success(7),
            action.executeForRetry(TOURNAMENT_ID),
        )
        assertEquals(
            listOf("Player Two"),
            cloud.snapshots[1].rosters.getValue(1).map { it.displayName },
        )
    }

    @Test
    fun normalInvocationRecordsRosterReplacementFailureWithoutChangingPrimaryResult() = runTest {
        val queue = RecordingQueueRepository()
        val result = useCase(
            localRepository(),
            FakeCloud(TournamentRosterCloudReplacementResult.NetworkFailure),
            queue,
        )(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.NetworkFailure, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertEquals(SyncQueueOperationType.ROSTER_REPLACEMENT, queue.entries.single().operationType)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)
    }

    private fun useCase(
        repository: InMemoryTournamentRepository,
        cloud: FakeCloud,
        queue: RecordingQueueRepository = RecordingQueueRepository(),
    ) = ReplaceTournamentRosterInCloudUseCase(
        repository,
        FakeAuthRepository,
        cloud,
        RecordSyncQueueOutcome(queue),
    )

    private suspend fun localRepository(): InMemoryTournamentRepository = InMemoryTournamentRepository().also { repository ->
        repository.create(
            Tournament(
                TOURNAMENT_ID,
                "Roster Cup",
                java.time.LocalDate.of(2026, 8, 3),
                "Organizer",
                "123",
                TournamentStatus.DRAFT,
            ),
        )
        repository.saveTeamNames(
            TOURNAMENT_ID,
            TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.saveRoster(
            TOURNAMENT_ID,
            1,
            listOf(RosterPlayer(TOURNAMENT_ID, 1, "Player One")),
        )
    }

    private object FakeAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")),
        )
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private class FakeCloud(
        private val result: TournamentRosterCloudReplacementResult,
    ) : TournamentRosterCloudReplacementRepository {
        var snapshot: TournamentRosterCloudReplacement? = null
        var ownerId: String? = null
        val snapshots = mutableListOf<TournamentRosterCloudReplacement>()

        override suspend fun replace(
            snapshot: TournamentRosterCloudReplacement,
            ownerId: String,
        ): TournamentRosterCloudReplacementResult {
            this.snapshot = snapshot
            this.ownerId = ownerId
            snapshots += snapshot
            return result
        }
    }

    private class RecordingQueueRepository : PersistentSyncQueueRepository {
        private val state = MutableStateFlow<List<SyncQueueEntry>>(emptyList())
        val entries get() = state.value

        override fun observeAll(): Flow<List<SyncQueueEntry>> = state
        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry {
            val entry = SyncQueueEntry(
                id = "entry",
                operationType = operationType,
                tournamentId = tournamentId,
                createdAtEpochMillis = 0,
                status = status,
                failureCategory = failureCategory,
                attemptCount = 0,
            )
            state.value = listOf(entry)
            return entry
        }
        override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
        override suspend fun incrementAttemptCount(id: String) = Unit
        override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
        override suspend fun markCompleted(id: String) = Unit
        override suspend fun remove(id: String) = Unit
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
