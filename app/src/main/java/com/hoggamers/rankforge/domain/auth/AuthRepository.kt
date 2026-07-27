package com.hoggamers.rankforge.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<AuthState>

    suspend fun restoreSession(): AuthOperationResult

    suspend fun signUp(
        email: String,
        password: String,
    ): AuthOperationResult

    suspend fun login(
        email: String,
        password: String,
    ): AuthOperationResult

    suspend fun logout(): AuthOperationResult
}
