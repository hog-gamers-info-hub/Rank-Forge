package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRemoteDataSource {
    fun observeAuthState(): Flow<AuthState>

    suspend fun restoreSession(): AuthState

    suspend fun signUp(
        email: String,
        password: String,
    )

    suspend fun login(
        email: String,
        password: String,
    )

    suspend fun logout()
}
