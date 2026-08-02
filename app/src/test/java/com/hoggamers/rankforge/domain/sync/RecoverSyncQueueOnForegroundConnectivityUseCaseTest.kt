package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoverSyncQueueOnForegroundConnectivityUseCaseTest {
    @Test fun availableForegroundNetworkWithSignedInSessionTriggersQueueRecovery() = runTest {
        val recovery = RecordingRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = recovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(1, recovery.calls)
    }

    @Test fun availableForegroundNetworkWhileSignedOutDoesNotRetry() = runTest {
        val recovery = RecordingRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            queueRecovery = recovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(0, recovery.calls)
    }

    @Test fun unavailableNetworkDoesNotRetryEvenWithSignedInSession() = runTest {
        val recovery = RecordingRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = recovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = false)

        assertEquals(0, recovery.calls)
    }

    @Test fun retryFailureIsIsolatedFromForegroundConnectivitySignal() = runTest {
        val recovery = RecordingRecoveryAction(failure = IllegalStateException("queue unavailable"))
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = recovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(1, recovery.calls)
    }

    @Test fun authenticatedConnectivityTransitionRecoversOnlyWhenNetworkBecomesAvailable() = runTest {
        val recovery = RecordingRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = recovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = false)
        assertEquals(0, recovery.calls)

        useCase.onConnectivityChanged(isNetworkAvailable = true)
        assertEquals(1, recovery.calls)
    }

    private fun signedInState() = AuthState.SignedIn(
        AuthUser(id = "user-id", email = "user@example.com"),
    )

    private class RecordingRecoveryAction(
        private val failure: Throwable? = null,
    ) : ForegroundSyncQueueRecoveryAction {
        var calls = 0
        override suspend fun recoverAfterAuthenticatedSession() {
            calls += 1
            failure?.let { throw it }
        }
    }

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()
        override suspend fun login(email: String, password: String): AuthOperationResult = failure()
        override suspend fun logout(): AuthOperationResult = failure()
        private fun failure() = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
    }
}
