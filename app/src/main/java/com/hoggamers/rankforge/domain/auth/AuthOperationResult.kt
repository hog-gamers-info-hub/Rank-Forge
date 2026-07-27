package com.hoggamers.rankforge.domain.auth

sealed interface AuthOperationResult {
    data class Success(val outcome: AuthSuccessOutcome) : AuthOperationResult
    data class Failure(val failure: AuthFailure) : AuthOperationResult
}

sealed interface AuthSuccessOutcome {
    data object SignedIn : AuthSuccessOutcome
    data object SignUpAuthenticated : AuthSuccessOutcome
    data object EmailConfirmationRequired : AuthSuccessOutcome
    data object SignedOutLocally : AuthSuccessOutcome
    data class SignedOutLocallyWithRemoteFailure(val failure: AuthFailure) : AuthSuccessOutcome
}

sealed interface AuthLogoutResult {
    data object LocalSessionCleared : AuthLogoutResult
    data class LocalSessionClearedWithRemoteFailure(val failure: AuthFailure) : AuthLogoutResult
}
