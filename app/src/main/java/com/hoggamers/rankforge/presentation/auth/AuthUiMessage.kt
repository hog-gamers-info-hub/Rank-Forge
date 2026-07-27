package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.domain.auth.AuthFailureCategory

sealed interface AuthUiMessage {
    data object MissingCredentials : AuthUiMessage
    data object SignedIn : AuthUiMessage
    data object SignUpAuthenticated : AuthUiMessage
    data object SignUpConfirmationRequired : AuthUiMessage
    data object SignedOut : AuthUiMessage
    data object LogoutRemoteWarning : AuthUiMessage
    data class AuthenticationFailure(val category: AuthFailureCategory) : AuthUiMessage
    data class RestorationWarning(val category: AuthFailureCategory) : AuthUiMessage
}
