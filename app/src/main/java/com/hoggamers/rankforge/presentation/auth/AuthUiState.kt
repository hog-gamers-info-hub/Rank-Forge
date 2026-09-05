package com.hoggamers.rankforge.presentation.auth

const val MIN_AUTH_PASSWORD_LENGTH = 6

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val email: String = "",
    val password: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val accountEmail: String? = null,
    val isSessionLoading: Boolean = false,
    val isExternalAuthCallbackProcessing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSignedIn: Boolean = false,
    val accountDeletionState: AccountDeletionUiState = AccountDeletionUiState.IDLE,
    val passwordRecoveryStage: PasswordRecoveryStage = PasswordRecoveryStage.NONE,
    val statusMessage: AuthUiMessage? = null,
    val warningMessage: AuthUiMessage? = null,
    val errorMessage: AuthUiMessage? = null,
) {
    val isPasswordRecoveryActive: Boolean
        get() = passwordRecoveryStage != PasswordRecoveryStage.NONE

    val canSubmit: Boolean =
        email.isNotBlank() && password.isNotBlank() && !isSessionLoading && !isSubmitting

    val canRequestPasswordReset: Boolean =
        email.isNotBlank() && !isSubmitting

    val canUpdateRecoveredPassword: Boolean =
        newPassword.isNotBlank() &&
            confirmNewPassword.isNotBlank() &&
            newPassword == confirmNewPassword &&
            newPassword.length >= MIN_AUTH_PASSWORD_LENGTH &&
            !isSubmitting

    val isAccountDeletionInProgress: Boolean
        get() = accountDeletionState == AccountDeletionUiState.DELETING
}
