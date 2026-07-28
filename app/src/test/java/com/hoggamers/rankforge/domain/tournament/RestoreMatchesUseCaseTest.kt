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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreMatchesUseCaseTest {
    @Test
    fun noCloudMatchesPreservesResultWithoutQueueEntry() = runTest {
        val queue = RecordingTestQueueRepository()
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            FakeCloudRepository(
                MatchCloudRestorationRemoteResult.Success(
                    MatchCloudRestorationSnapshot(TOURNAMENT_ID, emptyList()),
                ),
            ),
            RecordingLocalRepository(),
            queue.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.NoCloudMatches, result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun authenticationFailureIsRecordedWithQueueMetadata() = runTest {
        val queue = RecordingTestQueueRepository()
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedOut),
            FakeCloudRepository(MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.NETWORK)),
            RecordingLocalRepository(),
            queue.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertEquals(SyncQueueOperationType.MATCH_RESTORATION, queue.entries.single().operationType)
        assertEquals(TOURNAMENT_ID, queue.entries.single().tournamentId)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, queue.entries.single().status)
        assertEquals(0, queue.entries.single().attemptCount)
    }

    @Test
    fun networkFailureIsRecordedAndQueuePersistenceFailureIsExposed() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val networkFailure = MatchCloudRestorationRemoteResult.Failure(
            MatchCloudRestorationFailureCategory.NETWORK,
        )
        val queue = RecordingTestQueueRepository()
        val networkResult = RestoreMatchesUseCase(
            auth,
            FakeCloudRepository(networkFailure),
            RecordingLocalRepository(),
            queue.recorder(),
        )(TOURNAMENT_ID)
        assertEquals(MatchCloudRestorationResult.NetworkFailure, networkResult.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, networkResult.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)

        val persistenceFailure = RestoreMatchesUseCase(
            auth,
            FakeCloudRepository(networkFailure),
            RecordingLocalRepository(),
            RecordingTestQueueRepository(IllegalStateException()).recorder(),
        )(TOURNAMENT_ID)
        assertEquals(MatchCloudRestorationResult.NetworkFailure, persistenceFailure.primaryResult)
        assertEquals(QueueRecordingResult.PERSISTENCE_FAILED, persistenceFailure.queueRecordingResult)
    }

    private class FakeCloudRepository(
        private val result: MatchCloudRestorationRemoteResult<MatchCloudRestorationSnapshot>,
    ) : MatchCloudRestorationRepository {
        override suspend fun readOwnedMatches(
            tournamentId: String,
        ): MatchCloudRestorationRemoteResult<MatchCloudRestorationSnapshot> = result
    }

    private class RecordingLocalRepository : MatchRestorationLocalRepository {
        override suspend fun replaceMatches(snapshot: MatchCloudRestorationSnapshot) = Unit
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
        const val OWNER_ID = "22222222-2222-2222-222222222222"
    }
}
