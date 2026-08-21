package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.cloud.FinalizedMatchCloudSyncMapper
import com.hoggamers.rankforge.data.cloud.FinalizedMatchCloudSyncMappingResult
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncFinalizedMatchesUseCaseTest {
    @Test
    fun unauthenticatedSyncIsRejectedBeforeCloudAccess() = runTest {
        val cloud = RecordingCloudRepository()
        val queue = RecordingTestQueueRepository()
        val useCase = SyncFinalizedMatchesUseCase(
            tournamentRepository = localRepository(),
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            cloudSyncRepository = cloud,
            queueRecorder = queue.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)
        assertEquals(FinalizedMatchCloudSyncResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertNull(cloud.snapshot)
        assertEquals(SyncQueueOperationType.FINALIZED_MATCH_SYNC, queue.entries.single().operationType)
        assertEquals(TOURNAMENT_ID, queue.entries.single().tournamentId)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, queue.entries.single().status)
        assertEquals(0, queue.entries.single().attemptCount)
    }

    @Test
    fun authenticatedSyncSendsAndPreservesOnlyLocalFinalizedData() = runTest {
        val local = localRepository()
        val before = local.observeMatchesByTournamentId(TOURNAMENT_ID).first()
        val cloud = RecordingCloudRepository(FinalizedMatchCloudSyncResult.Success(9))
        val queue = RecordingTestQueueRepository()
        val useCase = SyncFinalizedMatchesUseCase(
            tournamentRepository = local,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null))),
            cloudSyncRepository = cloud,
            queueRecorder = queue.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)
        assertEquals(FinalizedMatchCloudSyncResult.Success(9), result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertEquals(before, cloud.snapshot?.matches)
        assertEquals(before, local.observeMatchesByTournamentId(TOURNAMENT_ID).first())
        assertEquals(9, local.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun retryReconstructsTenTeamSnapshotWithAuthoritativeSlotsAndTenCloudRows() = runTest {
        val local = InMemoryTournamentRepository()
        local.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Ten Team Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        local.saveTeamNames(
            TOURNAMENT_ID,
            (1..10).associateWith { slotNumber -> "Team $slotNumber" },
        )
        assertTrue(local.confirmTournament(TOURNAMENT_ID))
        local.createDraftMatch(
            Match(
                id = "ten-team-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        local.finalizeDraftMatch(
            matchId = "ten-team-match",
            placements = (1..10).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..10).map { slot -> MatchKill(slot, slot - 1) },
        )

        val cloud = RecordingCloudRepository(FinalizedMatchCloudSyncResult.Success(2))
        val result = SyncFinalizedMatchesUseCase(
            tournamentRepository = local,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null))),
            cloudSyncRepository = cloud,
            queueRecorder = testQueueRecorder(),
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(FinalizedMatchCloudSyncResult.Success(2), result)
        val snapshot = cloud.snapshot!!
        assertEquals(12, snapshot.teamSlots.size)
        assertEquals(
            (1..10).toList(),
            snapshot.teamSlots.filter { it.teamName.isNotBlank() }.map { it.slotNumber },
        )
        val mapped = FinalizedMatchCloudSyncMapper.map(snapshot) as FinalizedMatchCloudSyncMappingResult.Success
        assertEquals(10, mapped.payloads.matchResults.size)
    }

    @Test
    fun partialFailurePersistsOnlyItsLastConfirmedRevision() = runTest {
        val local = localRepository()
        val result = SyncFinalizedMatchesUseCase(
            local,
            FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null))),
            RecordingCloudRepository(
                FinalizedMatchCloudSyncResult.PartialFailure(
                    completedStage = FinalizedMatchCloudSyncStage.MATCHES,
                    confirmedCloudRevision = 7,
                ),
            ),
            testQueueRecorder(),
        )(TOURNAMENT_ID)

        assertEquals(7, local.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertTrue(result.primaryResult is FinalizedMatchCloudSyncResult.PartialFailure)
    }

    @Test
    fun failureBeforeConfirmedServerMutationLeavesBaselineAndQueueStatusUnchanged() = runTest {
        val local = localRepository()
        val queue = RecordingTestQueueRepository()
        val result = SyncFinalizedMatchesUseCase(
            local,
            FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null))),
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.ValidationFailure),
            queue.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(FinalizedMatchCloudSyncResult.ValidationFailure, result.primaryResult)
        assertEquals(1, local.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertEquals(SyncQueueStatus.FAILED_VALIDATION, queue.entries.single().status)
    }

    @Test
    fun domainSuccessRejectsNonpositiveRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            FinalizedMatchCloudSyncResult.Success(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinalizedMatchCloudSyncResult.Success(-1)
        }
    }

    @Test
    fun networkFailureIsRecordedAndQueuePersistenceFailureIsExposed() = runTest {
        val local = localRepository()
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null)))
        val queue = RecordingTestQueueRepository()
        val networkResult = SyncFinalizedMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.NetworkFailure),
            queue.recorder(),
        )(TOURNAMENT_ID)
        assertEquals(FinalizedMatchCloudSyncResult.NetworkFailure, networkResult.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, networkResult.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)

        val persistenceFailure = SyncFinalizedMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.NetworkFailure),
            RecordingTestQueueRepository(IllegalStateException()).recorder(),
        )(TOURNAMENT_ID)
        assertEquals(FinalizedMatchCloudSyncResult.NetworkFailure, persistenceFailure.primaryResult)
        assertEquals(QueueRecordingResult.PERSISTENCE_FAILED, persistenceFailure.queueRecordingResult)
    }

    @Test
    fun authorizationAndNetworkFailuresLeaveLocalFinalizedMatchUntouched() = runTest {
        val local = localRepository()
        val before = local.observeMatchesByTournamentId(TOURNAMENT_ID).first()
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null)))

        val authorization = SyncFinalizedMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.AuthorizationFailure),
            testQueueRecorder(),
        )(TOURNAMENT_ID)
        val network = SyncFinalizedMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.NetworkFailure),
            testQueueRecorder(),
        )(TOURNAMENT_ID)

        assertEquals(FinalizedMatchCloudSyncResult.AuthorizationFailure, authorization.primaryResult)
        assertEquals(FinalizedMatchCloudSyncResult.NetworkFailure, network.primaryResult)
        assertEquals(before, local.observeMatchesByTournamentId(TOURNAMENT_ID).first())
    }

    private suspend fun localRepository(): InMemoryTournamentRepository = InMemoryTournamentRepository().also { repository ->
        repository.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.createDraftMatch(
            Match(
                id = "finalized-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        repository.finalizeDraftMatch(
            matchId = "finalized-match",
            placements = TeamSlot.SLOT_NUMBERS.map { MatchPlacement(it, it) },
            kills = TeamSlot.SLOT_NUMBERS.map { MatchKill(it, it - 1) },
        )
    }

    private class RecordingCloudRepository(
        private val result: FinalizedMatchCloudSyncResult = FinalizedMatchCloudSyncResult.Success(1),
    ) : FinalizedMatchCloudSyncRepository {
        var snapshot: FinalizedMatchCloudSyncSnapshot? = null

        override suspend fun sync(snapshot: FinalizedMatchCloudSyncSnapshot): FinalizedMatchCloudSyncResult {
            this.snapshot = snapshot
            return result
        }
    }

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)

        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)

        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
