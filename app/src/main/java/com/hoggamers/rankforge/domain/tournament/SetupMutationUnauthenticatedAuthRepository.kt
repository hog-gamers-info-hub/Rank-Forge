package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Compatibility construction must fail closed. Production construction is provided by Hilt with
 * the authenticated repository and never uses this object.
 */
internal object SetupMutationUnauthenticatedAuthRepository : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedOut)

    override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

    override suspend fun signUp(email: String, password: String): AuthOperationResult =
        error("Authentication is unavailable")

    override suspend fun login(email: String, password: String): AuthOperationResult =
        error("Authentication is unavailable")

    override suspend fun logout(): AuthOperationResult = error("Authentication is unavailable")
}
