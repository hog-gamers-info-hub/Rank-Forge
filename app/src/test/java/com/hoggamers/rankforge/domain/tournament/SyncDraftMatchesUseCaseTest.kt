package com.hoggamers.rankforge.domain.tournament

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
import org.junit.Test

class SyncDraftMatchesUseCaseTest {
    @Test
    fun unauthenticatedSyncIsRejectedBeforeReadingOrWritingCloudData() = runTest {
        val cloud = RecordingCloudRepository()
        val queue = RecordingTestQueueRepository()
        val useCase = SyncDraftMatchesUseCase(
            tournamentRepository = localRepository(),
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            cloudSyncRepository = cloud,
            queueRecorder = queue.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)
        assertEquals(DraftMatchCloudSyncResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertNull(cloud.snapshot)
        assertEquals(SyncQueueOperationType.DRAFT_MATCH_SYNC, queue.entries.single().operationType)
        assertEquals(TOURNAMENT_ID, queue.entries.single().tournamentId)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, queue.entries.single().status)
        assertEquals(0, queue.entries.single().attemptCount)
    }

    @Test
    fun authenticatedSyncSendsLocalSnapshotAndPreservesLocalDraft() = runTest {
        val local = localRepository()
        val before = local.observeMatchesByTournamentId(TOURNAMENT_ID).first()
        val cloud = RecordingCloudRepository()
        val queue = RecordingTestQueueRepository()
        val useCase = SyncDraftMatchesUseCase(
            tournamentRepository = local,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", "owner@example.com"))),
            cloudSyncRepository = cloud,
            queueRecorder = queue.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)
        assertEquals(DraftMatchCloudSyncResult.Success, result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertEquals(TOURNAMENT_ID, cloud.snapshot?.tournament?.id)
        assertEquals(before, cloud.snapshot?.matches)
        assertEquals(before, local.observeMatchesByTournamentId(TOURNAMENT_ID).first())
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun networkFailureIsRecordedAndQueuePersistenceFailureIsExposed() = runTest {
        val local = localRepository()
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null)))
        val queue = RecordingTestQueueRepository()
        val networkResult = SyncDraftMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(DraftMatchCloudSyncResult.NetworkFailure),
            queue.recorder(),
        )(TOURNAMENT_ID)
        assertEquals(DraftMatchCloudSyncResult.NetworkFailure, networkResult.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, networkResult.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)

        val persistenceFailure = SyncDraftMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(DraftMatchCloudSyncResult.NetworkFailure),
            RecordingTestQueueRepository(IllegalStateException()).recorder(),
        )(TOURNAMENT_ID)
        assertEquals(DraftMatchCloudSyncResult.NetworkFailure, persistenceFailure.primaryResult)
        assertEquals(QueueRecordingResult.PERSISTENCE_FAILED, persistenceFailure.queueRecordingResult)
    }

    @Test
    fun authorizationAndNetworkFailuresPassThroughWithoutLocalMutation() = runTest {
        val local = localRepository()
        val before = local.observeMatchesByTournamentId(TOURNAMENT_ID).first()
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null)))

        val authorizationResult = SyncDraftMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(DraftMatchCloudSyncResult.AuthorizationFailure),
            testQueueRecorder(),
        )(TOURNAMENT_ID)
        val networkResult = SyncDraftMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(DraftMatchCloudSyncResult.NetworkFailure),
            testQueueRecorder(),
        )(TOURNAMENT_ID)

        assertEquals(DraftMatchCloudSyncResult.AuthorizationFailure, authorizationResult.primaryResult)
        assertEquals(DraftMatchCloudSyncResult.NetworkFailure, networkResult.primaryResult)
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
                id = "draft-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
    }

    private class RecordingCloudRepository(
        private val result: DraftMatchCloudSyncResult = DraftMatchCloudSyncResult.Success,
    ) : DraftMatchCloudSyncRepository {
        var snapshot: DraftMatchCloudSyncSnapshot? = null

        override suspend fun sync(snapshot: DraftMatchCloudSyncSnapshot): DraftMatchCloudSyncResult {
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
