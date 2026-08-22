package com.hoggamers.rankforge.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<AuthState>

    suspend fun restoreSession(): AuthRestorationResult

    suspend fun signUp(
        email: String,
        password: String,
    ): AuthOperationResult

    suspend fun login(
        email: String,
        password: String,
    ): AuthOperationResult

    suspend fun requestPasswordReset(email: String): AuthOperationResult =
        AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )

    suspend fun updateRecoveredPassword(newPassword: String): AuthOperationResult =
        AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )

    suspend fun signInWithGoogle(): AuthOperationResult = AuthOperationResult.Failure(
        AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
    )

    suspend fun logout(): AuthOperationResult
}
