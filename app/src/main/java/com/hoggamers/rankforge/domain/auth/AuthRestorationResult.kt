package com.hoggamers.rankforge.domain.auth

sealed interface AuthRestorationResult {
    data class Restored(val user: AuthUser) : AuthRestorationResult
    data object NoSavedSession : AuthRestorationResult
    data class ExpiredOrInvalid(val failure: AuthFailure) : AuthRestorationResult
    data class TemporaryFailure(val failure: AuthFailure) : AuthRestorationResult
    data class Failure(val failure: AuthFailure) : AuthRestorationResult
}
