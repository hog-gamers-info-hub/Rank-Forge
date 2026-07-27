package com.hoggamers.rankforge.presentation.auth

sealed interface AuthUiMessage {
    data object MissingCredentials : AuthUiMessage
    data object SessionRestored : AuthUiMessage
    data object SignedIn : AuthUiMessage
    data object SignUpSubmitted : AuthUiMessage
    data object SignedOut : AuthUiMessage
    data class Text(val value: String) : AuthUiMessage
}
