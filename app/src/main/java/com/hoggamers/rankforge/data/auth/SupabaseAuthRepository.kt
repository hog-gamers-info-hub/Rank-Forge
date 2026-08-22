package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource,
) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = remoteDataSource.observeAuthState()

    override suspend fun restoreSession(): AuthRestorationResult =
        try {
            remoteDataSource.restoreSession()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AuthRestorationResult.Failure(
                AuthFailureMapper.map(throwable, AuthFailureContext.Restore),
            )
        }

    override suspend fun signUp(
        email: String,
        password: String,
    ): AuthOperationResult = try {
        AuthOperationResult.Success(remoteDataSource.signUp(email, password))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AuthOperationResult.Failure(
            AuthFailureMapper.map(throwable, AuthFailureContext.SignUp),
        )
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthOperationResult = runAuthOperation(AuthFailureContext.Login) {
        remoteDataSource.login(email, password)
    }

    override suspend fun requestPasswordReset(email: String): AuthOperationResult =
        runAuthOperation(
            context = AuthFailureContext.PasswordResetRequest,
            successOutcome = AuthSuccessOutcome.PasswordResetEmailRequested,
        ) {
            remoteDataSource.requestPasswordReset(email)
        }

    override suspend fun updateRecoveredPassword(newPassword: String): AuthOperationResult =
        runAuthOperation(
            context = AuthFailureContext.PasswordUpdate,
            successOutcome = AuthSuccessOutcome.PasswordUpdated,
        ) {
            remoteDataSource.updateRecoveredPassword(newPassword)
        }

    override suspend fun signInWithGoogle(): AuthOperationResult = runAuthOperation(
        context = AuthFailureContext.GoogleSignIn,
        successOutcome = AuthSuccessOutcome.ExternalAuthenticationLaunched,
    ) {
        remoteDataSource.signInWithGoogle()
    }

    override suspend fun logout(): AuthOperationResult =
        try {
            when (val result = remoteDataSource.logout()) {
                com.hoggamers.rankforge.domain.auth.AuthLogoutResult.LocalSessionCleared ->
                    AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
                is com.hoggamers.rankforge.domain.auth.AuthLogoutResult.LocalSessionClearedWithRemoteFailure ->
                    AuthOperationResult.Success(
                        AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure(result.failure),
                    )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AuthOperationResult.Failure(
                AuthFailureMapper.map(throwable, AuthFailureContext.Logout),
            )
        }

    private suspend fun runAuthOperation(
        context: AuthFailureContext,
        successOutcome: AuthSuccessOutcome = AuthSuccessOutcome.SignedIn,
        operation: suspend () -> Unit,
    ): AuthOperationResult = try {
        operation()
        AuthOperationResult.Success(successOutcome)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AuthOperationResult.Failure(
            AuthFailureMapper.map(throwable, context),
        )
    }
}
