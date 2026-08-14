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
import kotlinx.coroutines.CancellationException
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

    @Test fun signedInRecoveryRunsParentQueueBeforeScreenshotRecovery() = runTest {
        val events = mutableListOf<String>()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = ForegroundSyncQueueRecoveryAction {
                events += "parent"
            },
            screenshotRecovery = ForegroundScreenshotRecoveryAction {
                events += "screenshots"
            },
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(listOf("parent", "screenshots"), events)
    }

    @Test fun availableForegroundNetworkWhileSignedOutDoesNotRetry() = runTest {
        val recovery = RecordingRecoveryAction()
        val screenshotRecovery = RecordingScreenshotRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            queueRecovery = recovery,
            screenshotRecovery = screenshotRecovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(0, recovery.calls)
        assertEquals(0, screenshotRecovery.calls)
    }

    @Test fun parentRecoveryFailureDoesNotStartScreenshotRecovery() = runTest {
        val screenshotRecovery = RecordingScreenshotRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = RecordingRecoveryAction(IllegalStateException("offline")),
            screenshotRecovery = screenshotRecovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(0, screenshotRecovery.calls)
    }

    @Test(expected = CancellationException::class)
    fun screenshotRecoveryCancellationPropagates() = runTest {
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = RecordingRecoveryAction(),
            screenshotRecovery = RecordingScreenshotRecoveryAction(CancellationException("cancelled")),
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)
    }

    @Test fun unavailableNetworkDoesNotRetryEvenWithSignedInSession() = runTest {
        val recovery = RecordingRecoveryAction()
        val screenshotRecovery = RecordingScreenshotRecoveryAction()
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = recovery,
            screenshotRecovery = screenshotRecovery,
        )

        useCase.onConnectivityChanged(isNetworkAvailable = false)

        assertEquals(0, recovery.calls)
        assertEquals(0, screenshotRecovery.calls)
    }

    @Test fun internallyHandledParentFailureKeepsScreenshotRecoveryControlled() = runTest {
        var parentAttempted = false
        var screenshotCalls = 0
        val useCase = RecoverSyncQueueOnForegroundConnectivityUseCase(
            authRepository = FakeAuthRepository(signedInState()),
            queueRecovery = ForegroundSyncQueueRecoveryAction {
                parentAttempted = true
                // Mirrors RecoverForegroundSyncQueueUseCase handling an ordinary retry failure.
            },
            screenshotRecovery = ForegroundScreenshotRecoveryAction {
                check(parentAttempted)
                screenshotCalls += 1
            },
        )

        useCase.onConnectivityChanged(isNetworkAvailable = true)

        assertEquals(true, parentAttempted)
        assertEquals(1, screenshotCalls)
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

    private class RecordingScreenshotRecoveryAction(
        private val failure: Throwable? = null,
    ) : ForegroundScreenshotRecoveryAction {
        var calls = 0
        override suspend fun recoverAfterParentQueue() {
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
