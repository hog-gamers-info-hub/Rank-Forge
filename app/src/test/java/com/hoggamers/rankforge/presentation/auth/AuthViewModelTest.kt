package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.auth.LoginUseCase
import com.hoggamers.rankforge.domain.auth.LogoutUseCase
import com.hoggamers.rankforge.domain.auth.ObserveAuthStateUseCase
import com.hoggamers.rankforge.domain.auth.RestoreSessionUseCase
import com.hoggamers.rankforge.domain.auth.SignUpUseCase
import com.hoggamers.rankforge.domain.sync.ForegroundSyncQueueRecoveryAction
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private var recoveryCalls = 0
    private var foregroundRecovery = ForegroundSyncQueueRecoveryAction { recoveryCalls += 1 }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
        recoveryCalls = 0
        foregroundRecovery = ForegroundSyncQueueRecoveryAction { recoveryCalls += 1 }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializationRemainsLoadingUntilPersistedSessionInitializationCompletes() = runTest {
        repository.restoreGate = CompletableDeferred()
        val viewModel = createViewModel()

        runCurrent()

        assertTrue(viewModel.uiState.value.isSessionLoading)

        repository.restoreGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSessionLoading)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun validPersistedSessionRestores() = runTest {
        repository.restoreResult = AuthRestorationResult.Restored(
            AuthUser(id = "user-id", email = "stored@example.com"),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("stored@example.com", viewModel.uiState.value.accountEmail)
        assertEquals(1, recoveryCalls)
    }

    @Test
    fun absentPersistedSessionRemainsSignedOut() = runTest {
        repository.restoreResult = AuthRestorationResult.NoSavedSession

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals(0, recoveryCalls)
    }

    @Test
    fun retryRecoveryFailureDoesNotChangeRestoredSessionState() = runTest {
        repository.restoreResult = AuthRestorationResult.Restored(
            AuthUser(id = "user-id", email = "stored@example.com"),
        )
        foregroundRecovery = ForegroundSyncQueueRecoveryAction {
            recoveryCalls += 1
            throw IllegalStateException("queue unavailable")
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, recoveryCalls)
        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("stored@example.com", viewModel.uiState.value.accountEmail)
    }

    @Test
    fun expiredSessionShowsActionableTypedError() = runTest {
        repository.restoreResult = AuthRestorationResult.ExpiredOrInvalid(
            AuthFailure(AuthFailureCategory.ExpiredOrInvalidSession),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.ExpiredOrInvalidSession),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun temporaryNetworkFailureShowsRecoverableWarning() = runTest {
        repository.restoreResult = AuthRestorationResult.TemporaryFailure(
            AuthFailure(AuthFailureCategory.NetworkUnavailable),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(
            AuthUiMessage.RestorationWarning(AuthFailureCategory.NetworkUnavailable),
            viewModel.uiState.value.warningMessage,
        )
    }

    @Test
    fun timeoutFailureShowsRecoverableWarning() = runTest {
        repository.restoreResult = AuthRestorationResult.TemporaryFailure(
            AuthFailure(AuthFailureCategory.Timeout),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            AuthUiMessage.RestorationWarning(AuthFailureCategory.Timeout),
            viewModel.uiState.value.warningMessage,
        )
    }

    @Test
    fun missingConfigurationDoesNotExposeRawFailure() = runTest {
        repository.restoreResult = AuthRestorationResult.Failure(
            AuthFailure(AuthFailureCategory.MissingSupabaseConfiguration),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.MissingSupabaseConfiguration),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun unknownRestorationFailureUsesStableCategory() = runTest {
        repository.restoreResult = AuthRestorationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.UnknownAuthenticationFailure),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun loginSuccessShowsSignedInState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("user@example.com", viewModel.uiState.value.accountEmail)
        assertEquals(AuthUiMessage.SignedIn, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun loginFailureShowsTypedError() = runTest {
        repository.loginResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.InvalidCredentials),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.InvalidCredentials),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun immediateSignUpShowsAuthenticatedOutcome() = runTest {
        repository.signUpResult = AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMode(AuthMode.SignUp)
        viewModel.onEmailChanged("new@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.SignUpAuthenticated, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun confirmationRequiredSignUpRemainsSignedOut() = runTest {
        repository.signUpResult = AuthOperationResult.Success(
            AuthSuccessOutcome.EmailConfirmationRequired,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMode(AuthMode.SignUp)
        viewModel.onEmailChanged("new@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(
            AuthUiMessage.SignUpConfirmationRequired,
            viewModel.uiState.value.statusMessage,
        )
    }

    @Test
    fun duplicateSubmitIsIgnoredWhileOperationRuns() = runTest {
        repository.loginGate = CompletableDeferred()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password")

        viewModel.submit()
        runCurrent()
        viewModel.submit()

        assertEquals(1, repository.loginCalls)
        repository.loginGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun logoutClearsOnlyAuthState() = runTest {
        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "user-id", email = "user@example.com"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.SignedOut, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun logoutFailureCannotLeaveStaleSignedInUi() = runTest {
        repository.logoutResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.NetworkUnavailable),
        )
        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "user-id", email = "user@example.com"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(null, viewModel.uiState.value.accountEmail)
    }

    @Test
    fun remoteLogoutFailureStillLeavesLocalDeviceSignedOutWithWarning() = runTest {
        repository.logoutResult = AuthOperationResult.Success(
            AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure(
                AuthFailure(AuthFailureCategory.NetworkUnavailable),
            ),
        )
        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "user-id", email = "user@example.com"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.LogoutRemoteWarning, viewModel.uiState.value.warningMessage)
    }

    @Test
    fun logoutDoesNotDeleteLocalTournamentData() = runTest {
        val tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            com.hoggamers.rankforge.domain.tournament.Tournament(
                id = "local-id",
                name = "Local Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = com.hoggamers.rankforge.domain.tournament.TournamentStatus.DRAFT,
            ),
        )
        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "user-id", email = "user@example.com"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        val tournaments = tournamentRepository.observeAll().first()
        assertEquals(listOf("Local Cup"), tournaments.map { it.name })
    }

    @Test
    fun restartAfterLogoutRemainsSignedOut() = runTest {
        repository.restoreResult = AuthRestorationResult.NoSavedSession
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    private fun createViewModel(): AuthViewModel =
        AuthViewModel(
            observeAuthState = ObserveAuthStateUseCase(repository),
            restoreSession = RestoreSessionUseCase(repository),
            signUp = SignUpUseCase(repository),
            login = LoginUseCase(repository),
            logout = LogoutUseCase(repository),
            recoverForegroundSyncQueue = foregroundRecovery,
        )

    private class FakeAuthRepository : AuthRepository {
        val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var restoreResult: AuthRestorationResult = AuthRestorationResult.NoSavedSession
        var restoreGate: CompletableDeferred<Unit>? = null
        var signUpResult: AuthOperationResult = AuthOperationResult.Success(
            AuthSuccessOutcome.SignUpAuthenticated,
        )
        var loginResult: AuthOperationResult = AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        var loginGate: CompletableDeferred<Unit>? = null
        var loginCalls: Int = 0
        var logoutResult: AuthOperationResult = AuthOperationResult.Success(
            AuthSuccessOutcome.SignedOutLocally,
        )

        override fun observeAuthState(): Flow<AuthState> = authState

        override suspend fun restoreSession(): AuthRestorationResult {
            restoreGate?.await()
            when (val result = restoreResult) {
                is AuthRestorationResult.Restored -> authState.value = AuthState.SignedIn(result.user)
                AuthRestorationResult.NoSavedSession -> authState.value = AuthState.SignedOut
                is AuthRestorationResult.ExpiredOrInvalid -> authState.value = AuthState.SessionExpired(result.failure)
                is AuthRestorationResult.TemporaryFailure -> authState.value = AuthState.RestorationWarning(result.failure)
                is AuthRestorationResult.Failure -> authState.value = AuthState.Error(result.failure)
            }
            return restoreResult
        }

        override suspend fun signUp(
            email: String,
            password: String,
        ): AuthOperationResult = signUpResult

        override suspend fun login(
            email: String,
            password: String,
        ): AuthOperationResult {
            loginCalls += 1
            loginGate?.await()
            if (loginResult is AuthOperationResult.Success) {
                authState.value = AuthState.SignedIn(AuthUser(id = "user-id", email = email))
            }
            return loginResult
        }

        override suspend fun logout(): AuthOperationResult {
            if (logoutResult is AuthOperationResult.Success) {
                authState.value = AuthState.SignedOut
            }
            return logoutResult
        }
    }
}
