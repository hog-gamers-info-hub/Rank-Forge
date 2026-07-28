package com.hoggamers.rankforge.domain.tournament

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreTournamentUseCaseTest {
    @Test
    fun unauthenticatedRestoreDoesNotReadCloudOrChangeLocalData() = runTest {
        val cloud = RecordingCloudRepository()
        val local = RecordingLocalRepository()
        val queue = RecordingTestQueueRepository()
        val useCase = RestoreTournamentUseCase(
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            cloudRepository = cloud,
            localRepository = local,
            queueRecorder = queue.recorder(),
        )

        val result = useCase.restore(TOURNAMENT_ID)

        assertEquals(TournamentCloudRestorationResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertFalse(cloud.readCalled)
        assertFalse(local.restoreCalled)
        assertEquals(SyncQueueOperationType.TOURNAMENT_RESTORATION, queue.entries.single().operationType)
        assertEquals(TOURNAMENT_ID, queue.entries.single().tournamentId)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, queue.entries.single().status)
        assertEquals(0, queue.entries.single().attemptCount)
    }

    @Test
    fun authenticatedUserCanListAndRestoreOwnedSnapshot() = runTest {
        val snapshot = snapshot()
        val cloud = RecordingCloudRepository(snapshot = snapshot)
        val local = RecordingLocalRepository()
        val queue = RecordingTestQueueRepository()
        val useCase = RestoreTournamentUseCase(
            authRepository = FakeAuthRepository(
                AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.com")),
            ),
            cloudRepository = cloud,
            localRepository = local,
            queueRecorder = queue.recorder(),
        )

        val available = useCase.loadAvailable()
        val restored = useCase.restore(TOURNAMENT_ID)

        assertEquals(
            TournamentCloudRestorationResult.Available(cloud.summaries),
            available,
        )
        assertEquals(TournamentCloudRestorationResult.Success("Summer Cup"), restored.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, restored.queueRecordingResult)
        assertTrue(queue.entries.isEmpty())
        assertTrue(cloud.readCalled)
        assertEquals(snapshot, local.snapshot)
    }

    @Test
    fun networkFailureIsRecordedAndQueuePersistenceFailureIsExposed() = runTest {
        val networkCloud = RecordingCloudRepository(
            readResult = TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NETWORK,
            ),
        )
        val queue = RecordingTestQueueRepository()
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))

        val networkResult = RestoreTournamentUseCase(
            auth,
            networkCloud,
            RecordingLocalRepository(),
            queue.recorder(),
        ).restore(TOURNAMENT_ID)
        assertEquals(TournamentCloudRestorationResult.NetworkFailure, networkResult.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, networkResult.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)

        val persistenceFailureResult = RestoreTournamentUseCase(
            auth,
            networkCloud,
            RecordingLocalRepository(),
            RecordingTestQueueRepository(IllegalStateException()).recorder(),
        ).restore(TOURNAMENT_ID)
        assertEquals(TournamentCloudRestorationResult.NetworkFailure, persistenceFailureResult.primaryResult)
        assertEquals(QueueRecordingResult.PERSISTENCE_FAILED, persistenceFailureResult.queueRecordingResult)
    }

    @Test
    fun cloudAndLocalFailuresAreReportedWithoutClaimingSuccess() = runTest {
        val cloudFailure = RecordingCloudRepository(
            readResult = TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NETWORK,
            ),
        )
        val localFailure = RecordingLocalRepository(throwOnRestore = true)
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))

        val readResult = RestoreTournamentUseCase(auth, cloudFailure, localFailure, testQueueRecorder()).restore(TOURNAMENT_ID)
        val transactionResult = RestoreTournamentUseCase(
            auth,
            RecordingCloudRepository(snapshot = snapshot()),
            localFailure,
            testQueueRecorder(),
        ).restore(TOURNAMENT_ID)

        assertEquals(TournamentCloudRestorationResult.NetworkFailure, readResult.primaryResult)
        assertEquals(TournamentCloudRestorationResult.LocalTransactionFailure, transactionResult.primaryResult)

        val authorizationResult = RestoreTournamentUseCase(
            auth,
            RecordingCloudRepository(
                readResult = TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.AUTHORIZATION,
                ),
            ),
            RecordingLocalRepository(),
            testQueueRecorder(),
        ).restore(TOURNAMENT_ID)
        assertEquals(TournamentCloudRestorationResult.AuthorizationFailure, authorizationResult.primaryResult)
    }

    private fun snapshot() = TournamentCloudRestorationSnapshot(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        ),
        slots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID),
        players = listOf(
            RestoredRosterPlayer(TOURNAMENT_ID, 1, 1, "Player One"),
        ),
    )

    private class RecordingCloudRepository(
        private val snapshot: TournamentCloudRestorationSnapshot? = null,
        private val readResult: TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot>? = null,
    ) : TournamentCloudRestorationRepository {
        val summaries = listOf(
            TournamentCloudRestorationSummary(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                date = "2026-07-24",
                organizerName = "Organizer",
                status = "draft",
            ),
        )
        var readCalled = false

        override suspend fun listOwnedTournaments() =
            TournamentCloudRestorationRemoteResult.Success(summaries)

        override suspend fun readOwnedTournament(tournamentId: String) = run {
            readCalled = true
            readResult ?: TournamentCloudRestorationRemoteResult.Success(snapshot ?: error("snapshot required"))
        }
    }

    private class RecordingLocalRepository(
        private val throwOnRestore: Boolean = false,
    ) : TournamentRestorationLocalRepository {
        var restoreCalled = false
        var snapshot: TournamentCloudRestorationSnapshot? = null

        override suspend fun restore(snapshot: TournamentCloudRestorationSnapshot) {
            restoreCalled = true
            if (throwOnRestore) error("transaction failed")
            this.snapshot = snapshot
        }
    }

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String) =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout() =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
