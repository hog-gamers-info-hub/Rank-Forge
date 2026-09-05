package com.hoggamers.rankforge.presentation.auth

import androidx.lifecycle.SavedStateHandle
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory
import com.hoggamers.rankforge.domain.auth.AccountDeletionRepository
import com.hoggamers.rankforge.domain.auth.AccountDeletionResult
import com.hoggamers.rankforge.domain.auth.AccountDeletionLocalCleanupRepository
import com.hoggamers.rankforge.domain.auth.AccountDeletionLocalCleanupResult
import com.hoggamers.rankforge.domain.auth.AccountDeletionMarker
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
import com.hoggamers.rankforge.domain.tournament.ReconcileLegacyTournamentOwnershipUseCase
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
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
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var accountDeletionRepository: FakeAccountDeletionRepository
    private lateinit var accountDeletionLocalCleanupRepository: FakeAccountDeletionLocalCleanupRepository
    private var recoveryCalls = 0
    private var foregroundRecovery = ForegroundSyncQueueRecoveryAction { recoveryCalls += 1 }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
        accountDeletionRepository = FakeAccountDeletionRepository()
        accountDeletionLocalCleanupRepository = FakeAccountDeletionLocalCleanupRepository()
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
    fun signedOutDoesNotInvokeLegacyOwnershipReconciliation() = runTest {
        val reconciliation = RecordingLegacyReconciliation(repository)
        val viewModel = createViewModel(reconciliation = reconciliation.action())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertTrue(reconciliation.lookupOwners.isEmpty())
    }

    @Test
    fun signedInStateInvokesLegacyOwnershipReconciliationWithCurrentOwner() = runTest {
        val reconciliation = RecordingLegacyReconciliation(repository)
        val viewModel = createViewModel(reconciliation = reconciliation.action())

        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals(listOf("owner-a"), reconciliation.lookupOwners)
    }

    @Test
    fun accountSwitchCancelsLegacyOwnershipReconciliationAndStartsTheNewOwner() = runTest {
        val reconciliation = RecordingLegacyReconciliation(repository).apply {
            firstLookupGate = CompletableDeferred()
        }
        val viewModel = createViewModel(reconciliation = reconciliation.action())

        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        runCurrent()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-b", "b@example.test"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals(listOf("owner-a", "owner-b"), reconciliation.lookupOwners)
    }

    @Test
    fun restoredSessionInvokesLegacyOwnershipReconciliation() = runTest {
        repository.restoreResult = AuthRestorationResult.Restored(AuthUser("owner-a", "a@example.test"))
        val reconciliation = RecordingLegacyReconciliation(repository)
        val viewModel = createViewModel(reconciliation = reconciliation.action())

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertEquals(listOf("owner-a"), reconciliation.lookupOwners)
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
    fun externalAuthCallbackReceivedShowsSessionLoadingWithoutSigningIn() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onExternalAuthCallbackReceived()

        assertTrue(viewModel.uiState.value.isSessionLoading)
        assertTrue(viewModel.uiState.value.isExternalAuthCallbackProcessing)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun externalAuthCallbackFailureClearsSessionLoadingWithoutSigningIn() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onExternalAuthCallbackReceived()

        viewModel.onExternalAuthCallbackFailed()

        assertFalse(viewModel.uiState.value.isSessionLoading)
        assertFalse(viewModel.uiState.value.isExternalAuthCallbackProcessing)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun authenticatedSessionObservationCompletesExternalAuthCallbackLoading() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onExternalAuthCallbackReceived()

        repository.authState.value = AuthState.SignedIn(
            AuthUser(id = "google-user-id", email = "google@example.com"),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isSignedIn)
        assertFalse(viewModel.uiState.value.isSessionLoading)
        assertFalse(viewModel.uiState.value.isExternalAuthCallbackProcessing)
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
    fun accountDeletionBlocksDuplicatesAndHandsOffAfterRemoteSuccess() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        advanceUntilIdle()
        accountDeletionRepository.gate = CompletableDeferred()

        viewModel.deleteAccount()
        runCurrent()
        viewModel.deleteAccount()
        runCurrent()

        assertEquals(1, accountDeletionRepository.calls)
        assertEquals(AccountDeletionUiState.DELETING, viewModel.uiState.value.accountDeletionState)

        accountDeletionRepository.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(AccountDeletionUiState.IDLE, viewModel.uiState.value.accountDeletionState)
        assertFalse(viewModel.uiState.value.isSignedIn)
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        runCurrent()
        assertEquals(AccountDeletionUiState.IDLE, viewModel.uiState.value.accountDeletionState)
    }

    @Test
    fun accountDeletionFailureIsVisibleAndCanBeRetriedManually() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        advanceUntilIdle()
        accountDeletionRepository.result = AccountDeletionResult.Failure(
            AccountDeletionFailureCategory.NETWORK,
        )

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(AccountDeletionUiState.IDLE, viewModel.uiState.value.accountDeletionState)
        assertEquals(
            AuthUiMessage.AccountDeletionFailure(AccountDeletionFailureCategory.NETWORK),
            viewModel.uiState.value.errorMessage,
        )

        accountDeletionRepository.result = AccountDeletionResult.Success
        viewModel.deleteAccount()
        advanceUntilIdle()
        assertEquals(
            AccountDeletionUiState.IDLE,
            viewModel.uiState.value.accountDeletionState,
        )
        assertFalse(viewModel.uiState.value.isSignedIn)
        assertEquals(2, accountDeletionRepository.calls)
    }

    @Test
    fun accountDeletionCleansLocalOwnerDataBeforeClearingSession() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(listOf("owner-a"), accountDeletionLocalCleanupRepository.purgeOwners)
        assertEquals(1, repository.clearLocalSessionCalls)
        assertEquals(null, accountDeletionLocalCleanupRepository.marker)
        assertEquals(AccountDeletionUiState.IDLE, viewModel.uiState.value.accountDeletionState)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun failedLocalAccountCleanupRetainsRemoteConfirmedMarkerAndSession() = runTest {
        accountDeletionLocalCleanupRepository.purgeResult = AccountDeletionLocalCleanupResult.Failed
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.authState.value = AuthState.SignedIn(AuthUser("owner-a", "a@example.test"))
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(
            com.hoggamers.rankforge.domain.auth.AccountDeletionPhase.REMOTE_CONFIRMED,
            accountDeletionLocalCleanupRepository.marker?.phase,
        )
        assertEquals(AccountDeletionUiState.REMOTE_DELETED_PENDING_LOCAL_CLEANUP, viewModel.uiState.value.accountDeletionState)
        assertEquals(0, repository.clearLocalSessionCalls)
    }

    @Test
    fun remoteConfirmedMarkerRecoversLocallyWithoutAnotherRemoteRequest() = runTest {
        accountDeletionLocalCleanupRepository.marker = AccountDeletionMarker(
            ownerUserId = "owner-a",
            phase = com.hoggamers.rankforge.domain.auth.AccountDeletionPhase.REMOTE_CONFIRMED,
            updatedAtEpochMillis = 1L,
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, accountDeletionRepository.calls)
        assertEquals(listOf("owner-a"), accountDeletionLocalCleanupRepository.purgeOwners)
        assertEquals(1, repository.clearLocalSessionCalls)
        assertEquals(null, accountDeletionLocalCleanupRepository.marker)
        assertEquals(AccountDeletionUiState.IDLE, viewModel.uiState.value.accountDeletionState)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun processDeathDuringAccountDeletionFailsClosedWithoutAutomaticRetry() = runTest {
        val viewModel = createViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("account_deletion_state" to AccountDeletionUiState.DELETING.name),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            AccountDeletionUiState.RECOVERY_REQUIRED,
            viewModel.uiState.value.accountDeletionState,
        )
        assertEquals(0, accountDeletionRepository.calls)
        assertFalse(viewModel.uiState.value.isSignedIn)
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
        reconciliation: ReconcileLegacyTournamentOwnershipUseCase = noOpReconciliation(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
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
            reconcileLegacyTournamentOwnership = reconciliation,
            accountDeletionRepository = accountDeletionRepository,
            accountDeletionLocalCleanupRepository = accountDeletionLocalCleanupRepository,
            savedStateHandle = savedStateHandle,
        )

    private fun noOpReconciliation() = ReconcileLegacyTournamentOwnershipUseCase(
        authRepository = repository,
        tournamentRepository = object : LegacyReconciliationTournamentRepository() {},
        cloudRepository = object : TournamentCloudRestorationRepository {
            override suspend fun listOwnedTournaments() =
                TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())

            override suspend fun readOwnedTournament(tournamentId: String): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot> =
                TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.NOT_FOUND)
        },
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

    private open class LegacyReconciliationTournamentRepository : TournamentRepository {
        override suspend fun create(tournament: Tournament) = Unit
        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())
        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(null)
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())
        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
        override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
        override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
        override suspend fun confirmTournament(tournamentId: String) = false
    }

    private class RecordingLegacyReconciliation(
        private val authRepository: FakeAuthRepository,
    ) {
        val lookupOwners = mutableListOf<String>()
        var firstLookupGate: CompletableDeferred<Unit>? = null
        private var lookupCount = 0

        fun action() = ReconcileLegacyTournamentOwnershipUseCase(
            authRepository = authRepository,
            tournamentRepository = object : LegacyReconciliationTournamentRepository() {
                override suspend fun readOwnerlessLegacyTournaments() = listOf(
                    Tournament(
                        id = "legacy-tournament",
                        name = "Legacy",
                        date = LocalDate.of(2026, 1, 1),
                        organizerName = "Organizer",
                        organizerContactNumber = "123",
                        status = TournamentStatus.DRAFT,
                        ownerUserId = null,
                    ),
                )
            },
            cloudRepository = object : TournamentCloudRestorationRepository {
                override suspend fun listOwnedTournaments() =
                    TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())

                override suspend fun readOwnedTournament(tournamentId: String): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot> {
                    lookupOwners += (authRepository.authState.value as AuthState.SignedIn).user.id
                    if (lookupCount++ == 0) firstLookupGate?.await()
                    return TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.NOT_FOUND)
                }
            },
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
        var clearLocalSessionCalls: Int = 0
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

        override suspend fun clearLocalSession() {
            clearLocalSessionCalls += 1
            authState.value = AuthState.SignedOut
        }
    }

    private class FakeAccountDeletionRepository : AccountDeletionRepository {
        var result: AccountDeletionResult = AccountDeletionResult.Success
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0

        override suspend fun deleteCurrentAccount(): AccountDeletionResult {
            calls += 1
            gate?.await()
            return result
        }
    }

    private class FakeAccountDeletionLocalCleanupRepository : AccountDeletionLocalCleanupRepository {
        var marker: AccountDeletionMarker? = null
        var purgeResult: AccountDeletionLocalCleanupResult = AccountDeletionLocalCleanupResult.Completed
        var purgeOwners = mutableListOf<String>()

        override suspend fun readMarker(): AccountDeletionMarker? = marker

        override suspend fun markRemoteRequested(ownerUserId: String) {
            marker = AccountDeletionMarker(ownerUserId, com.hoggamers.rankforge.domain.auth.AccountDeletionPhase.REMOTE_REQUESTED, 1L)
        }

        override suspend fun markRemoteConfirmed(ownerUserId: String) {
            marker = AccountDeletionMarker(ownerUserId, com.hoggamers.rankforge.domain.auth.AccountDeletionPhase.REMOTE_CONFIRMED, 2L)
        }

        override suspend fun purgeLocalDataForOwner(ownerUserId: String): AccountDeletionLocalCleanupResult {
            purgeOwners += ownerUserId
            return purgeResult
        }

        override suspend fun markLocalCleanupComplete(ownerUserId: String) {
            marker = AccountDeletionMarker(ownerUserId, com.hoggamers.rankforge.domain.auth.AccountDeletionPhase.LOCAL_CLEANUP_COMPLETE, 3L)
        }

        override suspend fun clearMarker(ownerUserId: String) {
            if (marker?.ownerUserId == ownerUserId) marker = null
        }
    }
}
