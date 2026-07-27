package com.hoggamers.rankforge.domain.auth

enum class AuthFailureCategory {
    InvalidCredentials,
    InvalidEmail,
    WeakPassword,
    AccountAlreadyRegistered,
    EmailConfirmationRequired,
    RateLimited,
    NetworkUnavailable,
    Timeout,
    ExpiredOrInvalidSession,
    MissingSupabaseConfiguration,
    UnknownAuthenticationFailure,
}

data class AuthFailure(
    val category: AuthFailureCategory,
)
