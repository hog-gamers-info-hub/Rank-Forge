package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource,
) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = remoteDataSource.observeAuthState()

    override suspend fun restoreSession(): AuthOperationResult =
        when (val authState = remoteDataSource.restoreSession()) {
            is AuthState.Error -> AuthOperationResult.Failure(authState.message)
            AuthState.Loading,
            AuthState.SignedOut,
            is AuthState.SignedIn,
            -> AuthOperationResult.Success
        }

    override suspend fun signUp(
        email: String,
        password: String,
    ): AuthOperationResult = runAuthOperation {
        remoteDataSource.signUp(email, password)
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthOperationResult = runAuthOperation {
        remoteDataSource.login(email, password)
    }

    override suspend fun logout(): AuthOperationResult = runAuthOperation {
        remoteDataSource.logout()
    }

    private suspend fun runAuthOperation(
        operation: suspend () -> Unit,
    ): AuthOperationResult =
        runCatching {
            operation()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { throwable -> AuthOperationResult.Failure(throwable.toUserMessage()) },
        )
}
