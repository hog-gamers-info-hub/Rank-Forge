package com.hoggamers.rankforge.presentation.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.auth.LoginUseCase
import com.hoggamers.rankforge.domain.auth.LogoutUseCase
import com.hoggamers.rankforge.domain.auth.ObserveAuthStateUseCase
import com.hoggamers.rankforge.domain.auth.RestoreSessionUseCase
import com.hoggamers.rankforge.domain.auth.RequestPasswordResetUseCase
import com.hoggamers.rankforge.domain.auth.SignUpUseCase
import com.hoggamers.rankforge.domain.auth.SignInWithGoogleUseCase
import com.hoggamers.rankforge.domain.auth.UpdateRecoveredPasswordUseCase
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AccountDeletionRepository
import com.hoggamers.rankforge.domain.auth.AccountDeletionResult
import com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory
import com.hoggamers.rankforge.domain.auth.AccountDeletionLocalCleanupRepository
import com.hoggamers.rankforge.domain.auth.AccountDeletionLocalCleanupResult
import com.hoggamers.rankforge.domain.auth.AccountDeletionPhase
import com.hoggamers.rankforge.domain.sync.ForegroundSyncQueueRecoveryAction
import com.hoggamers.rankforge.domain.tournament.RecoverPendingLocalDeletionCleanupUseCase
import com.hoggamers.rankforge.domain.tournament.ReconcileLegacyTournamentOwnershipUseCase
import java.util.concurrent.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
    private val restoreSession: RestoreSessionUseCase,
    private val signUp: SignUpUseCase,
    private val login: LoginUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val logout: LogoutUseCase,
    private val requestPasswordReset: RequestPasswordResetUseCase,
    private val updateRecoveredPassword: UpdateRecoveredPasswordUseCase,
    private val recoverForegroundSyncQueue: ForegroundSyncQueueRecoveryAction,
    private val recoverPendingLocalDeletionCleanup: RecoverPendingLocalDeletionCleanupUseCase,
    private val reconcileLegacyTournamentOwnership: ReconcileLegacyTournamentOwnershipUseCase,
    private val accountDeletionRepository: AccountDeletionRepository,
    private val accountDeletionLocalCleanupRepository: AccountDeletionLocalCleanupRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AuthUiState(
            isSessionLoading = true,
            accountDeletionState = when (savedStateHandle.get<String>(ACCOUNT_DELETION_STATE_KEY)) {
                AccountDeletionUiState.REMOTE_DELETED_PENDING_LOCAL_CLEANUP.name ->
                    AccountDeletionUiState.REMOTE_DELETED_PENDING_LOCAL_CLEANUP
                AccountDeletionUiState.DELETING.name -> AccountDeletionUiState.RECOVERY_REQUIRED
                else -> AccountDeletionUiState.IDLE
            },
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val deletionRecovery = recoverAccountDeletionBeforeSessionRestore()
            val restoration = try {
                restoreSession()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                AuthRestorationResult.Failure(
                    AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
                )
            }
            if (deletionRecovery != AccountDeletionRecovery.COMPLETED) {
                _uiState.update { currentState ->
                    AuthUiStateReducer.reduceRestoration(currentState, restoration)
                }
            }

            if (deletionRecovery == AccountDeletionRecovery.NONE &&
                restoration is AuthRestorationResult.Restored
            ) {
                try {
                    recoverForegroundSyncQueue.recoverAfterAuthenticatedSession()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Queue recovery must not change the restored authentication result.
                }
            }

            observeAuthState().collectLatest { authState ->
                _uiState.update { currentState -> AuthUiStateReducer.reduceSession(currentState, authState) }
                val ownerUserId = (authState as? com.hoggamers.rankforge.domain.auth.AuthState.SignedIn)
                    ?.user?.id?.takeIf { it.isNotBlank() }
                    ?: return@collectLatest
                recoverPendingLocalDeletionCleanup(ownerUserId)
                reconcileLegacyTournamentOwnership(ownerUserId)
            }
        }
    }

    fun setMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, statusMessage = null, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, statusMessage = null, errorMessage = null) }
    }

    fun beginPasswordRecovery() {
        _uiState.update(AuthUiStateReducer::beginPasswordRecoveryRequest)
    }

    fun cancelPasswordRecovery() {
        _uiState.update(AuthUiStateReducer::cancelPasswordRecovery)
    }

    fun onPasswordRecoveryLinkReceived() {
        _uiState.update(AuthUiStateReducer::beginPasswordRecoveryLinkVerification)
    }

    fun onPasswordRecoveryLinkVerified() {
        _uiState.update(AuthUiStateReducer::completePasswordRecoveryLinkVerification)
    }

    fun onPasswordRecoveryLinkFailed() {
        _uiState.update(AuthUiStateReducer::failPasswordRecoveryLinkVerification)
    }

    fun onExternalAuthCallbackReceived() {
        _uiState.update(AuthUiStateReducer::beginExternalAuthCallback)
    }

    fun onExternalAuthCallbackFailed() {
        _uiState.update(AuthUiStateReducer::failExternalAuthCallback)
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                newPassword = value,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onConfirmNewPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                confirmNewPassword = value,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun updateRecoveredPassword() {
        val currentState = _uiState.value
        if (currentState.passwordRecoveryStage != PasswordRecoveryStage.SET_NEW_PASSWORD ||
            currentState.isSubmitting
        ) {
            return
        }

        val validationMessage = when {
            currentState.newPassword.length < MIN_AUTH_PASSWORD_LENGTH ||
                currentState.newPassword.isBlank() -> AuthUiMessage.PasswordTooShort
            currentState.confirmNewPassword.isBlank() ||
                currentState.newPassword != currentState.confirmNewPassword ->
                AuthUiMessage.PasswordsDoNotMatch
            else -> null
        }
        if (validationMessage != null) {
            _uiState.update {
                it.copy(
                    statusMessage = null,
                    warningMessage = null,
                    errorMessage = validationMessage,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val updateResult = updateRecoveredPassword(currentState.newPassword)
            if (updateResult is AuthOperationResult.Failure) {
                _uiState.update { state ->
                    AuthUiStateReducer.finishOperation(state, updateResult)
                }
                return@launch
            }

            val logoutResult = logout.invoke()
            when (logoutResult) {
                is AuthOperationResult.Success -> {
                    val warning = when (logoutResult.outcome) {
                        is AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure ->
                            AuthUiMessage.LogoutRemoteWarning
                        else -> null
                    }
                    _uiState.update { state ->
                        AuthUiStateReducer.completePasswordRecovery(
                            currentState = state,
                            warningMessage = warning,
                        )
                    }
                }
                is AuthOperationResult.Failure -> _uiState.update { state -> state.copy(
                        isSubmitting = false,
                        statusMessage = null,
                        errorMessage = AuthUiMessage.AuthenticationFailure(
                            logoutResult.failure.category,
                        ),
                    ) }
                }
            }
        }

    fun exitPasswordRecovery() {
        val currentState = _uiState.value
        if (!currentState.isPasswordRecoveryActive || currentState.isSubmitting) {
            return
        }

        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val logoutResult = logout.invoke()
            when (logoutResult) {
                is AuthOperationResult.Success -> {
                    val warning = when (logoutResult.outcome) {
                        is AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure ->
                            AuthUiMessage.LogoutRemoteWarning
                        else -> null
                    }
                    _uiState.update { state ->
                        AuthUiStateReducer.cancelPasswordRecovery(state).copy(
                            warningMessage = warning,
                        )
                    }
                }
                is AuthOperationResult.Failure -> _uiState.update { state -> state.copy(
                        isSubmitting = false,
                        statusMessage = null,
                        errorMessage = AuthUiMessage.AuthenticationFailure(
                            logoutResult.failure.category,
                        ),
                    ) }
                }
            }
        }

    fun requestPasswordReset() {
        val currentState = _uiState.value
        if (currentState.isSubmitting) {
            return
        }

        val email = currentState.email.trim()
        if (email.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = AuthUiMessage.AuthenticationFailure(
                        AuthFailureCategory.InvalidEmail,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val result = requestPasswordReset(email)
            _uiState.update { state ->
                AuthUiStateReducer.finishOperation(state, result)
            }
        }
    }

    fun submit() {
        val currentState = _uiState.value
        if (!currentState.canSubmit) {
            _uiState.update { it.copy(errorMessage = AuthUiMessage.MissingCredentials) }
            return
        }

        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val result = when (currentState.mode) {
                AuthMode.Login -> login(
                    currentState.email.trim(),
                    currentState.password,
                )
                AuthMode.SignUp -> signUp(
                    currentState.email.trim(),
                    currentState.password,
                )
            }
            _uiState.update { state ->
                AuthUiStateReducer.finishOperation(
                    currentState = state.copy(password = ""),
                    result = result,
                )
            }
        }
    }

    fun logout() {
        if (_uiState.value.isSubmitting) {
            return
        }
        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val result = logout.invoke()
            _uiState.update { state ->
                when (result) {
                    is AuthOperationResult.Success ->
                        AuthUiStateReducer.finishOperation(
                            currentState = state.copy(password = ""),
                            result = result,
                        )
                    is AuthOperationResult.Failure -> state.copy(
                        accountEmail = null,
                        isSubmitting = false,
                        isSignedIn = false,
                        password = "",
                        statusMessage = null,
                        warningMessage = null,
                        errorMessage = AuthUiMessage.AuthenticationFailure(
                            result.failure.category,
                        ),
                    )
                }
            }
        }
    }

    fun deleteAccount() {
        val currentState = _uiState.value
        if (!currentState.isSignedIn ||
            currentState.isSubmitting ||
            currentState.accountDeletionState != AccountDeletionUiState.IDLE
        ) {
            return
        }
        val ownerUserId = currentState.accountUserId
        if (ownerUserId.isNullOrBlank()) {
            _uiState.update {
                AuthUiStateReducer.failAccountDeletion(
                    it,
                    AccountDeletionFailureCategory.NO_SESSION,
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                accountDeletionLocalCleanupRepository.markRemoteRequested(ownerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    AuthUiStateReducer.failAccountDeletion(
                        it,
                        AccountDeletionFailureCategory.UNKNOWN,
                    )
                }
                return@launch
            }
            savedStateHandle[ACCOUNT_DELETION_STATE_KEY] = AccountDeletionUiState.DELETING.name
            _uiState.update(AuthUiStateReducer::beginAccountDeletion)
            val result = try {
                accountDeletionRepository.deleteCurrentAccount()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                AccountDeletionResult.Failure(
                    com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory.UNKNOWN,
                )
            }
            when (result) {
                AccountDeletionResult.Success -> {
                    completeRemoteDeletion(ownerUserId, _uiState.value)
                }
                is AccountDeletionResult.Failure -> {
                    runCatching {
                        accountDeletionLocalCleanupRepository.clearMarker(ownerUserId)
                    }
                    savedStateHandle.remove<String>(ACCOUNT_DELETION_STATE_KEY)
                    _uiState.update { state ->
                        AuthUiStateReducer.failAccountDeletion(state, result.category)
                    }
                }
            }
        }
    }

    fun signInWithGoogle() {
        if (_uiState.value.isSubmitting || _uiState.value.isSignedIn) {
            return
        }
        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val result = signInWithGoogleUseCase()
            _uiState.update { state ->
                AuthUiStateReducer.finishOperation(state, result)
            }
        }
    }

    private companion object {
        const val ACCOUNT_DELETION_STATE_KEY = "account_deletion_state"
    }

    private suspend fun completeRemoteDeletion(
        ownerUserId: String,
        currentState: AuthUiState,
    ): AuthUiState {
        accountDeletionLocalCleanupRepository.markRemoteConfirmed(ownerUserId)
        savedStateHandle[ACCOUNT_DELETION_STATE_KEY] =
            AccountDeletionUiState.REMOTE_DELETED_PENDING_LOCAL_CLEANUP.name
        val pendingState = AuthUiStateReducer.completeAccountDeletion(currentState)
        _uiState.value = pendingState
        val finalState = when (accountDeletionLocalCleanupRepository.purgeLocalDataForOwner(ownerUserId)) {
            AccountDeletionLocalCleanupResult.Failed -> pendingState
            AccountDeletionLocalCleanupResult.Completed -> try {
                logout.clearLocalSession()
                accountDeletionLocalCleanupRepository.markLocalCleanupComplete(ownerUserId)
                accountDeletionLocalCleanupRepository.clearMarker(ownerUserId)
                savedStateHandle.remove<String>(ACCOUNT_DELETION_STATE_KEY)
                AuthUiStateReducer.completeLocalAccountDeletion(pendingState)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                pendingState
            }
        }
        _uiState.value = finalState
        return finalState
    }

    private suspend fun recoverAccountDeletionBeforeSessionRestore(): AccountDeletionRecovery {
        val marker = try {
            accountDeletionLocalCleanupRepository.readMarker()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            _uiState.update(AuthUiStateReducer::restoreAccountDeletionAfterProcessDeath)
            return AccountDeletionRecovery.BLOCKED
        } ?: return AccountDeletionRecovery.NONE

        return when (marker.phase) {
            AccountDeletionPhase.REMOTE_REQUESTED -> {
                _uiState.update(AuthUiStateReducer::restoreAccountDeletionAfterProcessDeath)
                AccountDeletionRecovery.BLOCKED
            }
            AccountDeletionPhase.REMOTE_CONFIRMED -> {
                _uiState.update {
                    it.copy(
                        accountDeletionState = AccountDeletionUiState.REMOTE_DELETED_PENDING_LOCAL_CLEANUP,
                        isSubmitting = false,
                    )
                }
                when (accountDeletionLocalCleanupRepository.purgeLocalDataForOwner(marker.ownerUserId)) {
                    AccountDeletionLocalCleanupResult.Failed -> AccountDeletionRecovery.BLOCKED
                    AccountDeletionLocalCleanupResult.Completed -> finalizeRecoveredLocalDeletion(marker.ownerUserId)
                }
            }
            AccountDeletionPhase.LOCAL_CLEANUP_COMPLETE ->
                finalizeRecoveredLocalDeletion(marker.ownerUserId)
        }
    }

    private suspend fun finalizeRecoveredLocalDeletion(ownerUserId: String): AccountDeletionRecovery = try {
        logout.clearLocalSession()
        accountDeletionLocalCleanupRepository.markLocalCleanupComplete(ownerUserId)
        accountDeletionLocalCleanupRepository.clearMarker(ownerUserId)
        savedStateHandle.remove<String>(ACCOUNT_DELETION_STATE_KEY)
        _uiState.update { state -> AuthUiStateReducer.completeLocalAccountDeletion(state) }
        AccountDeletionRecovery.COMPLETED
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        AccountDeletionRecovery.BLOCKED
    }

    private enum class AccountDeletionRecovery {
        NONE,
        BLOCKED,
        COMPLETED,
    }
}
