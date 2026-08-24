package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class SignedInTournamentTestAuthRepository(
    private val userId: String = OWNER_USER_ID,
) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> =
        flowOf(AuthState.SignedIn(AuthUser(userId, "$userId@example.test")))

    override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

    override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()

    override suspend fun login(email: String, password: String): AuthOperationResult = failure()

    override suspend fun logout(): AuthOperationResult =
        AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)

    private fun failure() = AuthOperationResult.Failure(
        AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
    )

    companion object {
        const val OWNER_USER_ID = "test-owner"
    }
}
