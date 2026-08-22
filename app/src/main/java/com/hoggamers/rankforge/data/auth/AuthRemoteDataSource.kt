package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthLogoutResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import kotlinx.coroutines.flow.Flow

interface AuthRemoteDataSource {
    fun observeAuthState(): Flow<AuthState>

    suspend fun restoreSession(): AuthRestorationResult

    suspend fun signUp(
        email: String,
        password: String,
    ): com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome

    suspend fun login(
        email: String,
        password: String,
    )

    suspend fun requestPasswordReset(email: String)

    suspend fun updateRecoveredPassword(newPassword: String)

    suspend fun signInWithGoogle()

    suspend fun logout(): AuthLogoutResult
}
