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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadTournamentUseCaseTest {
    @Test
    fun authenticationFailureIsRecordedAndDoesNotCallCloudOrChangeLocalData() = runTest {
        val repository = localRepository()
        val cloud = RecordingCloudRepository()
        val queueRepository = RecordingTestQueueRepository()
        val before = repository.observeById(TOURNAMENT_ID).first()
        val useCase = UploadTournamentUseCase(
            tournamentRepository = repository,
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            cloudUploadRepository = cloud,
            queueRecorder = queueRepository.recorder(),
        )

        val result = useCase(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertNull(cloud.snapshot)
        assertEquals(before, repository.observeById(TOURNAMENT_ID).first())
        assertEquals(1, queueRepository.entries.size)
        assertEquals(SyncQueueOperationType.TOURNAMENT_UPLOAD, queueRepository.entries.single().operationType)
        assertEquals(TOURNAMENT_ID, queueRepository.entries.single().tournamentId)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, queueRepository.entries.single().status)
        assertEquals(0, queueRepository.entries.single().attemptCount)
    }

    @Test
    fun authenticatedUploadSendsLocalSnapshotWithOwnerId() = runTest {
        val repository = localRepository()
        val cloud = RecordingCloudRepository()
        val queueRepository = RecordingTestQueueRepository()
        val useCase = UploadTournamentUseCase(
            tournamentRepository = repository,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.com"))),
            cloudUploadRepository = cloud,
            queueRecorder = queueRepository.recorder(),
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
    fun networkFailureIsRecorded() = runTest {
        val queueRepository = RecordingTestQueueRepository()
        val result = UploadTournamentUseCase(
            localRepository(),
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            RecordingCloudRepository(TournamentCloudUploadResult.NetworkFailure),
            queueRepository.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(TournamentCloudUploadResult.NetworkFailure, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queueRepository.entries.single().status)
    }

    @Test
    fun queuePersistenceFailurePreservesCloudFailure() = runTest {
        val result = UploadTournamentUseCase(
            localRepository(),
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
        val authorizationResult = UploadTournamentUseCase(
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
        val partialResult = UploadTournamentUseCase(
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
                    date = LocalDate.of(2026, 7, 24),
                    organizerName = "Organizer",
                    organizerContactNumber = "123",
                    status = TournamentStatus.DRAFT,
                ),
            )
            repository.saveRoster(
                TOURNAMENT_ID,
                1,
                listOf(RosterPlayer.create(TOURNAMENT_ID, 1, "Player One")),
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
    }
}
