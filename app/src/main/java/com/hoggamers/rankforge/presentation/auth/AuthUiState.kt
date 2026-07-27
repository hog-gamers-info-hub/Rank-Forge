package com.hoggamers.rankforge.presentation.auth

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val email: String = "",
    val password: String = "",
    val accountEmail: String? = null,
    val isSessionLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSignedIn: Boolean = false,
    val statusMessage: AuthUiMessage? = null,
    val errorMessage: AuthUiMessage? = null,
) {
    val canSubmit: Boolean =
        email.isNotBlank() && password.isNotBlank() && !isSubmitting
}
