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
import com.hoggamers.rankforge.domain.sync.expectedRevisionForWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadTournamentUseCaseTest {
    @Test
    fun cloudResponseAfterOwnerSwitchDoesNotConfirmRevisionOrRecordQueue() = runTest {
        val auth = SwitchingAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val cloud = SuspendingCloudRepository()
        val repository = CountingRevisionRepository(localRepository())
        val queue = RecordingTestQueueRepository()
        val useCase = useCase(repository, auth, cloud, queue.recorder())

        val job = launch { assertEquals(TournamentCloudUploadResult.AuthorizationFailure, useCase(TOURNAMENT_ID).primaryResult) }
        cloud.started.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OTHER_OWNER_ID, null))
        cloud.resume.complete(Unit)
        job.join()

        assertEquals(0, repository.revisionWrites)
        assertEquals(0, repository.baselineWrites)
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun authenticationFailureIsRecordedAndDoesNotCallCloudOrChangeLocalData() = runTest {
        val repository = localRepository()
        val cloud = RecordingCloudRepository()
        val queueRepository = RecordingTestQueueRepository()
        val before = repository.observeById(TOURNAMENT_ID).first()
        val useCase = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedOut),
            cloud,
            queueRepository.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertNull(cloud.snapshot)
        assertEquals(before, repository.observeById(TOURNAMENT_ID).first())
        assertTrue(queueRepository.entries.isEmpty())
    }

    @Test
    fun authenticatedUploadSendsLocalSnapshotWithOwnerId() = runTest {
        val repository = localRepository()
        val cloud = RecordingCloudRepository()
        val queueRepository = RecordingTestQueueRepository()
        val useCase = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.com"))),
            cloud,
            queueRepository.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.Success(7), result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertEquals(OWNER_ID, cloud.ownerId)
        assertEquals(TOURNAMENT_ID, cloud.snapshot?.tournament?.id)
        assertEquals(12, cloud.snapshot?.slots?.size)
        assertTrue(cloud.snapshot?.rosters?.get(1)?.single()?.displayName == "Player One")
        assertEquals(7, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertTrue(queueRepository.entries.isEmpty())
    }

    @Test
    fun authenticatedUploadUsesTheAtomicSnapshotBoundaryForAllUploadData() = runTest {
        val baseRepository = localRepository()
        val snapshot = TournamentCloudUploadSnapshot(
            tournament = baseRepository.observeByIdAndOwner(TOURNAMENT_ID, OWNER_ID).first()!!,
            slots = baseRepository.observeSlotsByTournamentIdAndOwner(TOURNAMENT_ID, OWNER_ID).first(),
            rosters = baseRepository.observeRosterByTournamentIdAndOwner(TOURNAMENT_ID, OWNER_ID).first(),
            expectedCloudRevision = baseRepository.readLocalRevisionState(TOURNAMENT_ID)
                .expectedRevisionForWrite(),
        )
        val repository = CountingSnapshotReadRepository(baseRepository)
        val localSnapshotRepository = RecordingSnapshotRepository(snapshot)

        val result = UploadTournamentUseCase(
            tournamentRepository = repository,
            localSnapshotRepository = localSnapshotRepository,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            cloudUploadRepository = RecordingCloudRepository(),
            queueRecorder = testQueueRecorder(),
        )(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.Success(7), result.primaryResult)
        assertEquals(1, localSnapshotRepository.calls)
        assertEquals(0, repository.slotsReads)
        assertEquals(0, repository.rosterReads)
        assertEquals(0, repository.revisionReads)
    }

    @Test
    fun networkFailureIsRecorded() = runTest {
        val queueRepository = RecordingTestQueueRepository()
        val repository = localRepository()
        val result = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            RecordingCloudRepository(TournamentCloudUploadResult.NetworkFailure),
            queueRepository.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.NetworkFailure, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queueRepository.entries.single().status)
        assertEquals(OWNER_ID, queueRepository.entries.single().ownerUserId)
    }

    @Test
    fun tournamentLimitFailureIsNotQueuedForRetry() = runTest {
        val queueRepository = RecordingTestQueueRepository()
        val repository = localRepository()
        val result = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            RecordingCloudRepository(TournamentCloudUploadResult.TournamentLimitReached),
            queueRepository.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.TournamentLimitReached, result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertTrue(queueRepository.entries.isEmpty())
    }

    @Test
    fun queuePersistenceFailurePreservesCloudFailure() = runTest {
        val repository = localRepository()
        val result = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            RecordingCloudRepository(TournamentCloudUploadResult.NetworkFailure),
            RecordingTestQueueRepository(enqueueFailure = IllegalStateException()).recorder(),
        )(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.NetworkFailure, result.primaryResult)
        assertEquals(QueueRecordingResult.PERSISTENCE_FAILED, result.queueRecordingResult)
    }

    @Test
    fun authorizationAndPartialFailuresArePreserved() = runTest {
        val repository = localRepository()
        val authorizationCloud = RecordingCloudRepository(TournamentCloudUploadResult.AuthorizationFailure)
        val authorizationResult = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            authorizationCloud,
            testQueueRecorder(),
        )(TOURNAMENT_ID)
        assertEquals(TournamentCloudUploadResult.AuthorizationFailure, authorizationResult.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, authorizationResult.queueRecordingResult)

        val partialCloud = RecordingCloudRepository(
            TournamentCloudUploadResult.PartialFailure(TournamentCloudUploadStage.TOURNAMENT),
        )
        val partialResult = useCase(
            repository,
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            partialCloud,
            testQueueRecorder(),
        )(TOURNAMENT_ID)
        assertEquals(
            TournamentCloudUploadResult.PartialFailure(TournamentCloudUploadStage.TOURNAMENT),
            partialResult.primaryResult,
        )
        assertEquals(QueueRecordingResult.RECORDED, partialResult.queueRecordingResult)
    }

    private fun localRepository(): InMemoryTournamentRepository = InMemoryTournamentRepository().also { repository ->
        kotlinx.coroutines.runBlocking {
            repository.create(
                Tournament(
                    id = TOURNAMENT_ID,
                    name = "Summer Cup",
                    stageName = "Organizer",
                    organizerContactNumber = "123",
                    status = TournamentStatus.DRAFT,
                    ownerUserId = OWNER_ID,
                ),
            )
            repository.saveRoster(
                TOURNAMENT_ID,
                1,
                listOf(RosterPlayer.create(TOURNAMENT_ID, 1, "Player One")),
            )
        }
    }

    private fun useCase(
        repository: TournamentRepository,
        authRepository: AuthRepository,
        cloudUploadRepository: TournamentCloudUploadRepository,
        queueRecorder: com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome,
    ): UploadTournamentUseCase = UploadTournamentUseCase(
        tournamentRepository = repository,
        localSnapshotRepository = snapshotRepository(repository),
        authRepository = authRepository,
        cloudUploadRepository = cloudUploadRepository,
        queueRecorder = queueRecorder,
    )

    private fun snapshotRepository(
        repository: TournamentRepository,
    ): TournamentCloudUploadLocalSnapshotRepository = object : TournamentCloudUploadLocalSnapshotRepository {
        override suspend fun readCloudUploadSnapshotByOwner(
            tournamentId: String,
            ownerUserId: String,
        ): TournamentCloudUploadSnapshot? {
            val tournament = repository.observeByIdAndOwner(tournamentId, ownerUserId).first() ?: return null
            return TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = repository.observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId).first(),
                rosters = repository.observeRosterByTournamentIdAndOwner(tournamentId, ownerUserId).first(),
                expectedCloudRevision = repository.readLocalRevisionState(tournamentId).expectedRevisionForWrite(),
            )
        }
    }

    private class RecordingCloudRepository(
        private val result: TournamentCloudUploadResult = TournamentCloudUploadResult.Success(7),
    ) : TournamentCloudUploadRepository {
        var snapshot: TournamentCloudUploadSnapshot? = null
        var ownerId: String? = null

        override suspend fun upload(
            snapshot: TournamentCloudUploadSnapshot,
            ownerId: String,
        ): TournamentCloudUploadResult {
            this.snapshot = snapshot
            this.ownerId = ownerId
            return result
        }
    }

    private class SuspendingCloudRepository : TournamentCloudUploadRepository {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        override suspend fun upload(
            snapshot: TournamentCloudUploadSnapshot,
            ownerId: String,
        ): TournamentCloudUploadResult {
            started.complete(Unit)
            resume.await()
            return TournamentCloudUploadResult.Success(7)
        }
    }

    private class CountingRevisionRepository(
        private val delegate: InMemoryTournamentRepository,
    ) : TournamentRepository by delegate {
        var revisionWrites = 0
        var baselineWrites = 0

        override suspend fun confirmCloudRevisionByOwner(
            tournamentId: String,
            ownerUserId: String,
            cloudRevision: Int,
        ): OwnerScopedTournamentMutationResult {
            revisionWrites += 1
            return delegate.confirmCloudRevisionByOwner(tournamentId, ownerUserId, cloudRevision)
        }

        override suspend fun establishCloudBaselineByOwner(
            tournamentId: String,
            ownerUserId: String,
            cloudRevision: Int,
        ): OwnerScopedTournamentMutationResult {
            baselineWrites += 1
            return delegate.establishCloudBaselineByOwner(tournamentId, ownerUserId, cloudRevision)
        }
    }

    private class CountingSnapshotReadRepository(
        private val delegate: InMemoryTournamentRepository,
    ) : TournamentRepository by delegate {
        var slotsReads = 0
        var rosterReads = 0
        var revisionReads = 0

        override fun observeSlotsByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<TeamSlot>> {
            slotsReads += 1
            return delegate.observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId)
        }

        override fun observeRosterByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<Map<Int, List<RosterPlayer>>> {
            rosterReads += 1
            return delegate.observeRosterByTournamentIdAndOwner(tournamentId, ownerUserId)
        }

        override suspend fun readLocalRevisionState(tournamentId: String) =
            delegate.readLocalRevisionState(tournamentId).also { revisionReads += 1 }
    }

    private class RecordingSnapshotRepository(
        private val snapshot: TournamentCloudUploadSnapshot,
    ) : TournamentCloudUploadLocalSnapshotRepository {
        var calls = 0

        override suspend fun readCloudUploadSnapshotByOwner(
            tournamentId: String,
            ownerUserId: String,
        ): TournamentCloudUploadSnapshot? {
            calls += 1
            return snapshot.takeIf {
                it.tournament.id == tournamentId && it.tournament.ownerUserId == ownerUserId
            }
        }
    }

    private class SwitchingAuthRepository(initial: AuthState) : AuthRepository {
        val state = MutableStateFlow(initial)

        override fun observeAuthState(): Flow<AuthState> = state
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
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
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
        const val OTHER_OWNER_ID = "33333333-3333-3333-3333-333333333333"
    }
}
