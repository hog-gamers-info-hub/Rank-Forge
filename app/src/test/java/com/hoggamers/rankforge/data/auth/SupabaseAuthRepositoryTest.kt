package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthLogoutResult
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseAuthRepositoryTest {
    @Test
    fun googleLaunchMapsToNonAuthenticatedOutcome() = runTest {
        val repository = SupabaseAuthRepository(FakeAuthRemoteDataSource())

        assertEquals(
            AuthOperationResult.Success(AuthSuccessOutcome.ExternalAuthenticationLaunched),
            repository.signInWithGoogle(),
        )
    }

    @Test
    fun googleLaunchFailureMapsToControlledFailure() = runTest {
        val repository = SupabaseAuthRepository(
            FakeAuthRemoteDataSource(googleFailure = IOException("network unavailable")),
        )

        assertEquals(
            AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.NetworkUnavailable)),
            repository.signInWithGoogle(),
        )
    }

    @Test
    fun emailLoginStillMapsToSignedInOutcome() = runTest {
        val repository = SupabaseAuthRepository(FakeAuthRemoteDataSource())

        assertEquals(
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn),
            repository.login("user@example.com", "password"),
        )
    }

    private class FakeAuthRemoteDataSource(
        private val googleFailure: Throwable? = null,
    ) : AuthRemoteDataSource {
        override fun observeAuthState(): Flow<AuthState> = emptyFlow()

        override suspend fun restoreSession(): AuthRestorationResult =
            AuthRestorationResult.NoSavedSession

        override suspend fun signUp(
            email: String,
            password: String,
        ): AuthSuccessOutcome = AuthSuccessOutcome.SignUpAuthenticated

        override suspend fun login(email: String, password: String) = Unit

        override suspend fun signInWithGoogle() {
            googleFailure?.let { throw it }
        }

        override suspend fun logout(): AuthLogoutResult = AuthLogoutResult.LocalSessionCleared
    }
}
