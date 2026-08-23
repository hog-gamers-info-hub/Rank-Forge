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
import com.hoggamers.rankforge.domain.auth.RequestPasswordResetUseCase
import com.hoggamers.rankforge.domain.auth.SignUpUseCase
import com.hoggamers.rankforge.domain.auth.SignInWithGoogleUseCase
import com.hoggamers.rankforge.domain.auth.UpdateRecoveredPasswordUseCase
import com.hoggamers.rankforge.domain.sync.ForegroundSyncQueueRecoveryAction
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.RecoverPendingLocalDeletionCleanupUseCase
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
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
import org.junit.Assert.assertNull
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
        assertNull(viewModel.uiState.value.statusMessage)
        assertEquals(1, recoveryCalls)

        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "user-id", email = "stored@example.com"),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("stored@example.com", viewModel.uiState.value.accountEmail)
        assertNull(viewModel.uiState.value.statusMessage)
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
    fun signedInStateInvokesOwnerScopedPendingLocalCleanup() = runTest {
        val pending = RecordingPendingCleanup()
        val viewModel = createViewModel(pending.action(repository))

        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        advanceUntilIdle()

        assertEquals(listOf("owner-a"), pending.intentQueries)
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
        assertEquals(1, repository.loginCalls)
        assertNull(viewModel.uiState.value.errorMessage)
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
    fun failedSignUpRemainsSignedOutWithControlledError() = runTest {
        repository.signUpResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.InvalidCredentials),
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
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.InvalidCredentials),
            viewModel.uiState.value.errorMessage,
        )
        assertNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun googleLaunchWithoutCallbackRemainsSignedOutAndRetryable() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signInWithGoogle()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(null, viewModel.uiState.value.accountEmail)
        assertEquals(AuthUiMessage.ExternalAuthenticationLaunched, viewModel.uiState.value.statusMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun repeatedEquivalentSignedInObservationKeepsStableAuthenticatedState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password")
        viewModel.submit()
        advanceUntilIdle()

        val authenticatedState = viewModel.uiState.value
        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "user-id", email = "user@example.com"),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("user@example.com", viewModel.uiState.value.accountEmail)
        assertEquals(authenticatedState.statusMessage, viewModel.uiState.value.statusMessage)
        assertNull(viewModel.uiState.value.errorMessage)
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
    fun googleLaunchReturnsToIdleAndSessionStateRemainsAuthoritative() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("typed@example.com")

        viewModel.signInWithGoogle()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(null, viewModel.uiState.value.accountEmail)
        assertEquals(AuthUiMessage.ExternalAuthenticationLaunched, viewModel.uiState.value.statusMessage)

        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "google-user-id", email = "google@example.com"),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals("google@example.com", viewModel.uiState.value.accountEmail)
    }

    @Test
    fun duplicateGoogleLaunchIsIgnoredWhileOperationRuns() = runTest {
        repository.googleGate = CompletableDeferred()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signInWithGoogle()
        runCurrent()
        viewModel.signInWithGoogle()

        assertEquals(1, repository.googleCalls)
        repository.googleGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun googleLaunchFailureShowsTypedErrorAndCanBeRetried() = runTest {
        repository.googleResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.NetworkUnavailable),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signInWithGoogle()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.NetworkUnavailable),
            viewModel.uiState.value.errorMessage,
        )

        repository.googleResult = AuthOperationResult.Success(
            AuthSuccessOutcome.ExternalAuthenticationLaunched,
        )
        viewModel.signInWithGoogle()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.ExternalAuthenticationLaunched, viewModel.uiState.value.statusMessage)
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

    @Test
    fun beginPasswordRecoveryPreservesTypedEmail() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("user@example.com")

        viewModel.beginPasswordRecovery()

        assertEquals(PasswordRecoveryStage.REQUEST_EMAIL, viewModel.uiState.value.passwordRecoveryStage)
        assertEquals("user@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun passwordResetRequestTrimsEmailAndCompletesWithoutAuthenticating() = runTest {
        repository.passwordResetGate = CompletableDeferred()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("  user@example.com  ")
        viewModel.beginPasswordRecovery()

        viewModel.requestPasswordReset()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.passwordResetCalls)
        assertEquals("user@example.com", repository.requestedPasswordResetEmail)

        repository.passwordResetGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(PasswordRecoveryStage.EMAIL_SENT, viewModel.uiState.value.passwordRecoveryStage)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertNull(viewModel.uiState.value.accountEmail)
    }

    @Test
    fun passwordResetFailureStopsSubmittingAndKeepsRequestStage() = runTest {
        repository.passwordResetResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.NetworkUnavailable),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("user@example.com")
        viewModel.beginPasswordRecovery()

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(PasswordRecoveryStage.REQUEST_EMAIL, viewModel.uiState.value.passwordRecoveryStage)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.NetworkUnavailable),
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun blankPasswordResetRequestDoesNotInvokeUseCase() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.beginPasswordRecovery()

        viewModel.requestPasswordReset()

        assertEquals(0, repository.passwordResetCalls)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.InvalidEmail),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun cancelPasswordRecoveryReturnsToLoginAndPreservesEmail() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("user@example.com")
        viewModel.beginPasswordRecovery()

        viewModel.cancelPasswordRecovery()

        assertEquals(PasswordRecoveryStage.NONE, viewModel.uiState.value.passwordRecoveryStage)
        assertEquals("user@example.com", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun passwordRecoveryLinkCallbacksUseRecoveryStages() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPasswordRecoveryLinkReceived()
        assertEquals(PasswordRecoveryStage.VERIFYING_LINK, viewModel.uiState.value.passwordRecoveryStage)

        viewModel.onPasswordRecoveryLinkVerified()
        assertEquals(PasswordRecoveryStage.SET_NEW_PASSWORD, viewModel.uiState.value.passwordRecoveryStage)

        viewModel.onPasswordRecoveryLinkReceived()
        viewModel.onPasswordRecoveryLinkFailed()
        assertEquals(PasswordRecoveryStage.LINK_ERROR, viewModel.uiState.value.passwordRecoveryStage)
    }

    @Test
    fun passwordUpdateIsRejectedOutsideSetNewPasswordStage() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onNewPasswordChanged("password")
        viewModel.onConfirmNewPasswordChanged("password")

        viewModel.updateRecoveredPassword()

        assertEquals(0, repository.passwordUpdateCalls)
        assertEquals(PasswordRecoveryStage.NONE, viewModel.uiState.value.passwordRecoveryStage)
    }

    @Test
    fun shortPasswordDoesNotCallPasswordUpdateUseCase() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("short")
        viewModel.onConfirmNewPasswordChanged("short")

        viewModel.updateRecoveredPassword()

        assertEquals(0, repository.passwordUpdateCalls)
        assertEquals(AuthUiMessage.PasswordTooShort, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun mismatchedPasswordsDoNotCallPasswordUpdateUseCase() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("password-one")
        viewModel.onConfirmNewPasswordChanged("password-two")

        viewModel.updateRecoveredPassword()

        assertEquals(0, repository.passwordUpdateCalls)
        assertEquals(AuthUiMessage.PasswordsDoNotMatch, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun validMatchingPasswordsCallUpdateUseCaseOnce() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("new-password")
        viewModel.onConfirmNewPasswordChanged("new-password")

        viewModel.updateRecoveredPassword()
        advanceUntilIdle()

        assertEquals(1, repository.passwordUpdateCalls)
        assertEquals("new-password", repository.updatedPassword)
    }

    @Test
    fun passwordUpdateFailureRemainsInRecoveryWithoutAuthenticating() = runTest {
        repository.passwordUpdateResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.NetworkUnavailable),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("new-password")
        viewModel.onConfirmNewPasswordChanged("new-password")

        viewModel.updateRecoveredPassword()
        advanceUntilIdle()

        assertEquals(PasswordRecoveryStage.SET_NEW_PASSWORD, viewModel.uiState.value.passwordRecoveryStage)
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.NetworkUnavailable),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun expiredRecoveryPasswordUpdateRemainsBlockedFromAuthenticatedApp() = runTest {
        repository.passwordUpdateResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.ExpiredOrInvalidSession),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("new-password")
        viewModel.onConfirmNewPasswordChanged("new-password")

        viewModel.updateRecoveredPassword()
        advanceUntilIdle()

        assertEquals(PasswordRecoveryStage.SET_NEW_PASSWORD, viewModel.uiState.value.passwordRecoveryStage)
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(null, viewModel.uiState.value.accountEmail)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.ExpiredOrInvalidSession),
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun successfulPasswordUpdateSignsOutLocallyAndCompletesRecovery() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("new-password")
        viewModel.onConfirmNewPasswordChanged("new-password")

        viewModel.updateRecoveredPassword()
        advanceUntilIdle()

        assertEquals(1, repository.passwordUpdateCalls)
        assertEquals(1, repository.logoutCalls)
        assertEquals(PasswordRecoveryStage.NONE, viewModel.uiState.value.passwordRecoveryStage)
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(null, viewModel.uiState.value.accountEmail)
        assertEquals("", viewModel.uiState.value.newPassword)
        assertEquals("", viewModel.uiState.value.confirmNewPassword)
        assertEquals(AuthUiMessage.PasswordUpdated, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun remoteLogoutWarningStillCompletesRecoveryAfterLocalClear() = runTest {
        repository.logoutResult = AuthOperationResult.Success(
            AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure(
                AuthFailure(AuthFailureCategory.NetworkUnavailable),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)
        viewModel.onNewPasswordChanged("new-password")
        viewModel.onConfirmNewPasswordChanged("new-password")

        viewModel.updateRecoveredPassword()
        advanceUntilIdle()

        assertEquals(PasswordRecoveryStage.NONE, viewModel.uiState.value.passwordRecoveryStage)
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(AuthUiMessage.PasswordUpdated, viewModel.uiState.value.statusMessage)
        assertEquals(AuthUiMessage.LogoutRemoteWarning, viewModel.uiState.value.warningMessage)
    }

    @Test
    fun exitingRecoveryLogsOutBeforeReturningToLogin() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        enterSetNewPasswordStage(viewModel)

        viewModel.exitPasswordRecovery()
        advanceUntilIdle()

        assertEquals(1, repository.logoutCalls)
        assertEquals(PasswordRecoveryStage.NONE, viewModel.uiState.value.passwordRecoveryStage)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    private fun enterSetNewPasswordStage(viewModel: AuthViewModel) {
        viewModel.onPasswordRecoveryLinkReceived()
        viewModel.onPasswordRecoveryLinkVerified()
    }

    private fun createViewModel(
        pendingCleanup: RecoverPendingLocalDeletionCleanupUseCase = noOpPendingCleanup(),
    ): AuthViewModel =
        AuthViewModel(
            observeAuthState = ObserveAuthStateUseCase(repository),
            restoreSession = RestoreSessionUseCase(repository),
            signUp = SignUpUseCase(repository),
            login = LoginUseCase(repository),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(repository),
            logout = LogoutUseCase(repository),
            requestPasswordReset = RequestPasswordResetUseCase(repository),
            updateRecoveredPassword = UpdateRecoveredPasswordUseCase(repository),
            recoverForegroundSyncQueue = foregroundRecovery,
            recoverPendingLocalDeletionCleanup = pendingCleanup,
        )

    private fun noOpPendingCleanup() = RecoverPendingLocalDeletionCleanupUseCase(
        authRepository = repository,
        deletionIntentRepository = NoOpDeletionIntentRepository,
        localDeletionRepository = object : LocalDeletionRepository {
            override suspend fun deleteMatchLocallyByOwner(
                matchId: String,
                ownerUserId: String,
            ): LocalDeletionResult = LocalDeletionResult.NotFound

            override suspend fun deleteTournamentLocallyByOwner(
                tournamentId: String,
                ownerUserId: String,
            ): LocalDeletionResult = LocalDeletionResult.NotFound
        },
    )

    private class RecordingPendingCleanup {
        val intentQueries = mutableListOf<String>()

        fun action(authRepository: AuthRepository) = RecoverPendingLocalDeletionCleanupUseCase(
            authRepository = authRepository,
            deletionIntentRepository = object : DeletionIntentRepository {
                override suspend fun findByTargetAndOwner(
                    targetType: DeletionTargetType,
                    targetId: String,
                    ownerUserId: String,
                ): DeletionIntent? = null

                override suspend fun startIfAbsent(intent: DeletionIntent) = false
                override suspend fun markRemoteDeletedByTargetAndOwner(
                    targetType: DeletionTargetType,
                    targetId: String,
                    ownerUserId: String,
                ) = false

                override suspend fun clearByTargetAndOwner(
                    targetType: DeletionTargetType,
                    targetId: String,
                    ownerUserId: String,
                ) = false

                override suspend fun isBlockingByTournamentIdAndOwner(
                    tournamentId: String,
                    ownerUserId: String,
                ) = false

                override suspend fun readPendingLocalCleanupByOwner(ownerUserId: String): List<DeletionIntent> {
                    intentQueries += ownerUserId
                    return emptyList()
                }
            },
            localDeletionRepository = object : LocalDeletionRepository {},
        )
    }

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
        var googleResult: AuthOperationResult = AuthOperationResult.Success(
            AuthSuccessOutcome.ExternalAuthenticationLaunched,
        )
        var googleGate: CompletableDeferred<Unit>? = null
        var googleCalls: Int = 0
        var passwordResetResult: AuthOperationResult = AuthOperationResult.Success(
            AuthSuccessOutcome.PasswordResetEmailRequested,
        )
        var passwordResetGate: CompletableDeferred<Unit>? = null
        var passwordResetCalls: Int = 0
        var requestedPasswordResetEmail: String? = null
        var passwordUpdateResult: AuthOperationResult = AuthOperationResult.Success(
            AuthSuccessOutcome.PasswordUpdated,
        )
        var passwordUpdateCalls: Int = 0
        var updatedPassword: String? = null
        var logoutCalls: Int = 0
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

        override suspend fun signInWithGoogle(): AuthOperationResult {
            googleCalls += 1
            googleGate?.await()
            return googleResult
        }

        override suspend fun requestPasswordReset(email: String): AuthOperationResult {
            passwordResetCalls += 1
            requestedPasswordResetEmail = email
            passwordResetGate?.await()
            return passwordResetResult
        }

        override suspend fun updateRecoveredPassword(newPassword: String): AuthOperationResult {
            passwordUpdateCalls += 1
            updatedPassword = newPassword
            return passwordUpdateResult
        }

        override suspend fun logout(): AuthOperationResult {
            logoutCalls += 1
            if (logoutResult is AuthOperationResult.Success) {
                authState.value = AuthState.SignedOut
            }
            return logoutResult
        }
    }
}
