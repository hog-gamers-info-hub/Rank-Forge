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
    fun emailSignupDelegatesToAuthRemoteDataSource() = runTest {
        val remoteDataSource = FakeAuthRemoteDataSource()
        val repository = SupabaseAuthRepository(remoteDataSource)

        assertEquals(
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated),
            repository.signUp("new@example.com", "password"),
        )
        assertEquals(
            listOf("new@example.com" to "password"),
            remoteDataSource.signUpCalls,
        )
    }

    @Test
    fun emailSignupDuplicateFailureUsesNeutralCategory() = runTest {
        val repository = SupabaseAuthRepository(
            FakeAuthRemoteDataSource(
                signUpFailure = IllegalStateException("User already registered"),
            ),
        )

        assertEquals(
            AuthOperationResult.Failure(
                AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
            ),
            repository.signUp("existing@example.com", "password"),
        )
    }

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

    @Test
    fun passwordResetRequestMapsToEmailRequestedOutcome() = runTest {
        val repository = SupabaseAuthRepository(FakeAuthRemoteDataSource())

        assertEquals(
            AuthOperationResult.Success(AuthSuccessOutcome.PasswordResetEmailRequested),
            repository.requestPasswordReset("user@example.com"),
        )
    }

    @Test
    fun passwordResetRequestNetworkFailureMapsToNetworkUnavailable() = runTest {
        val repository = SupabaseAuthRepository(
            FakeAuthRemoteDataSource(passwordResetFailure = IOException("network unavailable")),
        )

        assertEquals(
            AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.NetworkUnavailable)),
            repository.requestPasswordReset("user@example.com"),
        )
    }

    @Test
    fun passwordUpdateMapsToPasswordUpdatedOutcome() = runTest {
        val repository = SupabaseAuthRepository(FakeAuthRemoteDataSource())

        assertEquals(
            AuthOperationResult.Success(AuthSuccessOutcome.PasswordUpdated),
            repository.updateRecoveredPassword("new-password"),
        )
    }

    @Test
    fun passwordUpdateExpiredRecoverySessionMapsToExpiredOrInvalidSession() = runTest {
        val repository = SupabaseAuthRepository(
            FakeAuthRemoteDataSource(
                passwordUpdateFailure = IllegalStateException("Invalid recovery session token"),
            ),
        )

        assertEquals(
            AuthOperationResult.Failure(
                AuthFailure(AuthFailureCategory.ExpiredOrInvalidSession),
            ),
            repository.updateRecoveredPassword("new-password"),
        )
    }

    private class FakeAuthRemoteDataSource(
        private val signUpFailure: Throwable? = null,
        private val googleFailure: Throwable? = null,
        private val passwordResetFailure: Throwable? = null,
        private val passwordUpdateFailure: Throwable? = null,
    ) : AuthRemoteDataSource {
        val signUpCalls = mutableListOf<Pair<String, String>>()

        override fun observeAuthState(): Flow<AuthState> = emptyFlow()

        override suspend fun restoreSession(): AuthRestorationResult =
            AuthRestorationResult.NoSavedSession

        override suspend fun signUp(
            email: String,
            password: String,
        ): AuthSuccessOutcome {
            signUpCalls += email to password
            signUpFailure?.let { throw it }
            return AuthSuccessOutcome.SignUpAuthenticated
        }

        override suspend fun login(email: String, password: String) = Unit

        override suspend fun requestPasswordReset(email: String) {
            passwordResetFailure?.let { throw it }
        }

        override suspend fun updateRecoveredPassword(newPassword: String) {
            passwordUpdateFailure?.let { throw it }
        }

        override suspend fun signInWithGoogle() {
            googleFailure?.let { throw it }
        }

        override suspend fun logout(): AuthLogoutResult = AuthLogoutResult.LocalSessionCleared

        override suspend fun clearLocalSession() = Unit
    }
}
