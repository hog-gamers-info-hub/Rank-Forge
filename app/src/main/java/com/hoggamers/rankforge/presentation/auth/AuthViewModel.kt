package com.hoggamers.rankforge.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.auth.LoginUseCase
import com.hoggamers.rankforge.domain.auth.LogoutUseCase
import com.hoggamers.rankforge.domain.auth.ObserveAuthStateUseCase
import com.hoggamers.rankforge.domain.auth.RestoreSessionUseCase
import com.hoggamers.rankforge.domain.auth.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
    private val restoreSession: RestoreSessionUseCase,
    private val signUp: SignUpUseCase,
    private val login: LoginUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState(isSessionLoading = true))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAuthState().collect { authState ->
                _uiState.update { currentState ->
                    AuthUiStateReducer.reduceSession(currentState, authState)
                }
            }
        }
        viewModelScope.launch {
            val result = restoreSession()
            _uiState.update { currentState ->
                when (result) {
                    com.hoggamers.rankforge.domain.auth.AuthOperationResult.Success ->
                        currentState.copy(isSessionLoading = false)
                    is com.hoggamers.rankforge.domain.auth.AuthOperationResult.Failure ->
                        currentState.copy(
                            isSessionLoading = false,
                            errorMessage = AuthUiMessage.Text(result.message),
                        )
                }
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
                    successMessage = when (currentState.mode) {
                        AuthMode.Login -> AuthUiMessage.SignedIn
                        AuthMode.SignUp -> AuthUiMessage.SignUpSubmitted
                    },
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update(AuthUiStateReducer::startOperation)
            val result = logout.invoke()
            _uiState.update { state ->
                AuthUiStateReducer.finishOperation(
                    currentState = state.copy(password = ""),
                    result = result,
                    successMessage = AuthUiMessage.SignedOut,
                )
            }
        }
    }
}
