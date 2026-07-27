package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome

object AuthUiStateReducer {
    fun reduceRestoration(
        currentState: AuthUiState,
        result: AuthRestorationResult,
    ): AuthUiState =
        when (result) {
            is AuthRestorationResult.Restored -> currentState.copy(
                accountEmail = result.user.email,
                isSessionLoading = false,
                isSignedIn = true,
                warningMessage = null,
                errorMessage = null,
            )
            AuthRestorationResult.NoSavedSession -> signedOut(currentState)
            is AuthRestorationResult.ExpiredOrInvalid -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
                warningMessage = null,
                errorMessage = AuthUiMessage.AuthenticationFailure(
                    result.failure.category,
                ),
            )
            is AuthRestorationResult.TemporaryFailure -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
                warningMessage = AuthUiMessage.RestorationWarning(
                    result.failure.category,
                ),
                errorMessage = null,
            )
            is AuthRestorationResult.Failure -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
                warningMessage = null,
                errorMessage = AuthUiMessage.AuthenticationFailure(
                    result.failure.category,
                ),
            )
        }

    fun reduceSession(
        currentState: AuthUiState,
        authState: AuthState,
    ): AuthUiState =
        when (authState) {
            AuthState.Loading -> currentState.copy(
                isSessionLoading = true,
                errorMessage = null,
            )
            AuthState.SignedOut -> signedOut(currentState)
            is AuthState.SignedIn -> currentState.copy(
                accountEmail = authState.user.email,
                isSessionLoading = false,
                isSignedIn = true,
                warningMessage = null,
                errorMessage = null,
            )
            is AuthState.RestorationWarning -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
                warningMessage = AuthUiMessage.RestorationWarning(authState.failure.category),
                errorMessage = null,
            )
            is AuthState.SessionExpired -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
                warningMessage = null,
                errorMessage = AuthUiMessage.AuthenticationFailure(authState.failure.category),
            )
            is AuthState.Error -> currentState.copy(
                accountEmail = null,
                isSessionLoading = false,
                isSignedIn = false,
                warningMessage = null,
                errorMessage = AuthUiMessage.AuthenticationFailure(authState.failure.category),
            )
        }

    fun startOperation(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            isSubmitting = true,
            statusMessage = null,
            warningMessage = null,
            errorMessage = null,
        )

    fun finishOperation(
        currentState: AuthUiState,
        result: AuthOperationResult,
    ): AuthUiState =
        when (result) {
            is AuthOperationResult.Success -> reduceSuccess(currentState, result.outcome)
            is AuthOperationResult.Failure -> currentState.copy(
                isSubmitting = false,
                statusMessage = null,
                errorMessage = AuthUiMessage.AuthenticationFailure(result.failure.category),
            )
        }

    private fun reduceSuccess(
        currentState: AuthUiState,
        outcome: AuthSuccessOutcome,
    ): AuthUiState =
        when (outcome) {
            AuthSuccessOutcome.SignedIn -> currentState.copy(
                isSubmitting = false,
                accountEmail = currentState.email.trim(),
                isSignedIn = true,
                statusMessage = AuthUiMessage.SignedIn,
                warningMessage = null,
                errorMessage = null,
            )
            AuthSuccessOutcome.SignUpAuthenticated -> currentState.copy(
                isSubmitting = false,
                accountEmail = currentState.email.trim(),
                isSignedIn = true,
                statusMessage = AuthUiMessage.SignUpAuthenticated,
                warningMessage = null,
                errorMessage = null,
            )
            AuthSuccessOutcome.EmailConfirmationRequired -> currentState.copy(
                isSubmitting = false,
                accountEmail = null,
                isSignedIn = false,
                statusMessage = AuthUiMessage.SignUpConfirmationRequired,
                warningMessage = null,
                errorMessage = null,
            )
            AuthSuccessOutcome.SignedOutLocally -> signedOut(
                currentState.copy(
                    isSubmitting = false,
                    statusMessage = AuthUiMessage.SignedOut,
                ),
            )
            is AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure -> signedOut(
                currentState.copy(
                    isSubmitting = false,
                    statusMessage = AuthUiMessage.SignedOut,
                ),
            ).copy(warningMessage = AuthUiMessage.LogoutRemoteWarning)
        }

    private fun signedOut(currentState: AuthUiState): AuthUiState = currentState.copy(
        accountEmail = null,
        isSessionLoading = false,
        isSignedIn = false,
        warningMessage = null,
        errorMessage = null,
    )
}
