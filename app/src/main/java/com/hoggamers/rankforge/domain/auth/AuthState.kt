package com.hoggamers.rankforge.domain.auth

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
    data class RestorationWarning(val failure: AuthFailure) : AuthState
    data class SessionExpired(val failure: AuthFailure) : AuthState
    data class Error(val failure: AuthFailure) : AuthState
}
