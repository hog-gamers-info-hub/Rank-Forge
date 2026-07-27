package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.auth.LoginUseCase
import com.hoggamers.rankforge.domain.auth.LogoutUseCase
import com.hoggamers.rankforge.domain.auth.ObserveAuthStateUseCase
import com.hoggamers.rankforge.domain.auth.RestoreSessionUseCase
import com.hoggamers.rankforge.domain.auth.SignUpUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoreSessionFailureShowsErrorWithoutStuckLoading() = runTest {
        repository.restoreResult = AuthOperationResult.Failure("Session expired.")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSessionLoading)
        assertEquals(AuthUiMessage.Text("Session expired."), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun restoreSessionSuccessUsesObservedSignedInState() = runTest {
        repository.authState.value = AuthState.SignedIn(AuthUser(id = "user-id", email = "stored@example.com"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSessionLoading)
        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("stored@example.com", viewModel.uiState.value.accountEmail)
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
    fun loginFailureShowsErrorState() = runTest {
        repository.loginResult = AuthOperationResult.Failure("Invalid login credentials.")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.Text("Invalid login credentials."), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun signUpSuccessShowsSubmittedState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMode(AuthMode.SignUp)
        viewModel.onEmailChanged("new@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AuthUiMessage.SignUpSubmitted, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun signUpFailureShowsErrorState() = runTest {
        repository.signUpResult = AuthOperationResult.Failure("User already registered.")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMode(AuthMode.SignUp)
        viewModel.onEmailChanged("new@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AuthUiMessage.Text("User already registered."), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun logoutClearsOnlyAuthState() = runTest {
        repository.authState.value = AuthState.SignedIn(AuthUser(id = "user-id", email = "user@example.com"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.SignedOut, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun logoutDoesNotDeleteLocalTournamentData() = runTest {
        val tournamentRepository = com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository()
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
        repository.authState.value = AuthState.SignedIn(AuthUser(id = "user-id", email = "user@example.com"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        val tournaments = tournamentRepository.observeAll().first()
        assertEquals(listOf("Local Cup"), tournaments.map { it.name })
    }

    private fun createViewModel(): AuthViewModel =
        AuthViewModel(
            observeAuthState = ObserveAuthStateUseCase(repository),
            restoreSession = RestoreSessionUseCase(repository),
            signUp = SignUpUseCase(repository),
            login = LoginUseCase(repository),
            logout = LogoutUseCase(repository),
        )

    private class FakeAuthRepository : AuthRepository {
        val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var restoreResult: AuthOperationResult = AuthOperationResult.Success
        var signUpResult: AuthOperationResult = AuthOperationResult.Success
        var loginResult: AuthOperationResult = AuthOperationResult.Success
        var logoutResult: AuthOperationResult = AuthOperationResult.Success

        override fun observeAuthState(): Flow<AuthState> = authState

        override suspend fun restoreSession(): AuthOperationResult = restoreResult

        override suspend fun signUp(
            email: String,
            password: String,
        ): AuthOperationResult = signUpResult

        override suspend fun login(
            email: String,
            password: String,
        ): AuthOperationResult {
            if (loginResult == AuthOperationResult.Success) {
                authState.value = AuthState.SignedIn(AuthUser(id = "user-id", email = email))
            }
            return loginResult
        }

        override suspend fun logout(): AuthOperationResult {
            if (logoutResult == AuthOperationResult.Success) {
                authState.value = AuthState.SignedOut
            }
            return logoutResult
        }
    }
}
