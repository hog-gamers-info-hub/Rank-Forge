package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthState

object AuthUiStateReducer {
    fun reduceSession(
        currentState: AuthUiState,
        authState: AuthState,
    ): AuthUiState =
        when (authState) {
            AuthState.Loading -> currentState.copy(
                isSessionLoading = true,
                errorMessage = null,
            )
            AuthState.SignedOut -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
            )
            is AuthState.SignedIn -> currentState.copy(
                accountEmail = authState.user.email,
                isSessionLoading = false,
                isSignedIn = true,
                errorMessage = null,
            )
            is AuthState.Error -> currentState.copy(
                isSessionLoading = false,
                isSignedIn = false,
                errorMessage = AuthUiMessage.Text(authState.message),
            )
        }

    fun startOperation(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            isSubmitting = true,
            statusMessage = null,
            errorMessage = null,
        )

    fun finishOperation(
        currentState: AuthUiState,
        result: AuthOperationResult,
        successMessage: AuthUiMessage,
    ): AuthUiState =
        when (result) {
            AuthOperationResult.Success -> currentState.copy(
                isSubmitting = false,
                statusMessage = successMessage,
                errorMessage = null,
            )
            is AuthOperationResult.Failure -> currentState.copy(
                isSubmitting = false,
                statusMessage = null,
                errorMessage = AuthUiMessage.Text(result.message),
            )
        }
}
