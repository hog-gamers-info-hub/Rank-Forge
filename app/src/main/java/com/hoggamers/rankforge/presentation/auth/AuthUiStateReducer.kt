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
            is AuthRestorationResult.Restored -> if (currentState.isPasswordRecoveryActive) {
                currentState.copy(
                    accountEmail = null,
                    isSessionLoading = false,
                    isExternalAuthCallbackProcessing = false,
                    isSignedIn = false,
                    warningMessage = null,
                    errorMessage = null,
                )
            } else {
                currentState.copy(
                    accountEmail = result.user.email,
                    isSessionLoading = false,
                    isExternalAuthCallbackProcessing = false,
                    isSignedIn = true,
                    warningMessage = null,
                    errorMessage = null,
                )
            }
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
            is AuthState.SignedIn -> if (currentState.isPasswordRecoveryActive) {
                currentState.copy(
                    accountEmail = null,
                    isSessionLoading = false,
                    isExternalAuthCallbackProcessing = false,
                    isSignedIn = false,
                    warningMessage = null,
                    errorMessage = null,
                )
            } else {
                currentState.copy(
                    accountEmail = authState.user.email,
                    isSessionLoading = false,
                    isExternalAuthCallbackProcessing = false,
                    isSignedIn = true,
                    warningMessage = null,
                    errorMessage = null,
                )
            }
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

    fun beginExternalAuthCallback(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            isSessionLoading = true,
            isExternalAuthCallbackProcessing = true,
            isSignedIn = false,
            statusMessage = null,
            warningMessage = null,
            errorMessage = null,
        )

    fun failExternalAuthCallback(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            accountEmail = null,
            isSessionLoading = false,
            isExternalAuthCallbackProcessing = false,
            isSignedIn = false,
        )

    fun startOperation(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            isSubmitting = true,
            statusMessage = null,
            warningMessage = null,
            errorMessage = null,
        )

    fun beginPasswordRecoveryRequest(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            passwordRecoveryStage = PasswordRecoveryStage.REQUEST_EMAIL,
            newPassword = "",
            confirmNewPassword = "",
            isSessionLoading = false,
            isSubmitting = false,
            warningMessage = null,
            errorMessage = null,
        )

    fun cancelPasswordRecovery(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            passwordRecoveryStage = PasswordRecoveryStage.NONE,
            newPassword = "",
            confirmNewPassword = "",
            isSessionLoading = false,
            isSubmitting = false,
            warningMessage = null,
            errorMessage = null,
        )

    fun completePasswordRecovery(
        currentState: AuthUiState,
        warningMessage: AuthUiMessage? = null,
    ): AuthUiState = currentState.copy(
        passwordRecoveryStage = PasswordRecoveryStage.NONE,
        newPassword = "",
        confirmNewPassword = "",
        isSubmitting = false,
        isSignedIn = false,
        accountEmail = null,
        statusMessage = AuthUiMessage.PasswordUpdated,
        warningMessage = warningMessage,
        errorMessage = null,
    )

    fun beginPasswordRecoveryLinkVerification(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            passwordRecoveryStage = PasswordRecoveryStage.VERIFYING_LINK,
            isSessionLoading = false,
            isSubmitting = false,
            warningMessage = null,
            errorMessage = null,
        )

    fun completePasswordRecoveryLinkVerification(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            passwordRecoveryStage = PasswordRecoveryStage.SET_NEW_PASSWORD,
            isSessionLoading = false,
            isSubmitting = false,
            warningMessage = null,
            errorMessage = null,
        )

    fun failPasswordRecoveryLinkVerification(currentState: AuthUiState): AuthUiState =
        currentState.copy(
            passwordRecoveryStage = PasswordRecoveryStage.LINK_ERROR,
            isSessionLoading = false,
            isSubmitting = false,
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
            AuthSuccessOutcome.ExternalAuthenticationLaunched -> currentState.copy(
                isSubmitting = false,
                statusMessage = AuthUiMessage.ExternalAuthenticationLaunched,
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
            AuthSuccessOutcome.PasswordResetEmailRequested -> currentState.copy(
                passwordRecoveryStage = PasswordRecoveryStage.EMAIL_SENT,
                accountEmail = null,
                isSubmitting = false,
                isSignedIn = false,
                statusMessage = null,
                warningMessage = null,
                errorMessage = null,
            )
            AuthSuccessOutcome.PasswordUpdated -> currentState.copy(
                isSubmitting = false,
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
