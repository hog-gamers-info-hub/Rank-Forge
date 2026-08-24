package com.hoggamers.rankforge.presentation.auth

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
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState(isSessionLoading = true))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val restoration = try {
                restoreSession()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                AuthRestorationResult.Failure(
                    AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
                )
            }
            _uiState.update { currentState ->
                AuthUiStateReducer.reduceRestoration(currentState, restoration)
            }

            if (restoration is AuthRestorationResult.Restored) {
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
}
