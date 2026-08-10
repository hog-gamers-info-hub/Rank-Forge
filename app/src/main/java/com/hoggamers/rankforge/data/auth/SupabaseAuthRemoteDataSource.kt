package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthLogoutResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class SupabaseAuthRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : AuthRemoteDataSource {
    private val client get() = clientProvider.client

    override fun observeAuthState(): Flow<AuthState> {
        if (!config.isConfigured) {
            return flowOf(
                AuthState.Error(
                    AuthFailure(AuthFailureCategory.MissingSupabaseConfiguration),
                ),
            )
        }

        return flow {
            client.auth.awaitInitialization()
            emitAll(client.auth.sessionStatus.map { sessionStatus -> sessionStatus.toAuthState() })
        }.catch { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            emit(AuthFailureMapper.map(throwable, AuthFailureContext.Restore).toAuthState())
        }
    }

    override suspend fun restoreSession(): AuthRestorationResult {
        if (!config.isConfigured) {
            return AuthRestorationResult.Failure(
                AuthFailure(AuthFailureCategory.MissingSupabaseConfiguration),
            )
        }

        return try {
            client.auth.awaitInitialization()
            client.auth.currentSessionOrNull()?.let { session ->
                AuthRestorationResult.Restored(
                    AuthUser(
                        id = session.user?.id.orEmpty(),
                        email = session.user?.email,
                    ),
                )
            } ?: when (val status = client.auth.sessionStatus.value) {
                is SessionStatus.RefreshFailure -> status.refreshFailureCause().toRestorationResult()
                SessionStatus.Initializing -> AuthRestorationResult.Failure(
                    AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
                )
                is SessionStatus.Authenticated -> AuthRestorationResult.Restored(
                    AuthUser(
                        id = status.session.user?.id.orEmpty(),
                        email = status.session.user?.email,
                    ),
                )
                is SessionStatus.NotAuthenticated -> AuthRestorationResult.NoSavedSession
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throwable.toRestorationResult()
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
    ): AuthSuccessOutcome {
        ensureConfigured()
        val user = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return if (user == null) {
            AuthSuccessOutcome.SignUpAuthenticated
        } else {
            AuthSuccessOutcome.EmailConfirmationRequired
        }
    }

    override suspend fun login(
        email: String,
        password: String,
    ) {
        ensureConfigured()
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithGoogle() {
        ensureConfigured()
        client.auth.signInWith(Google)
    }

    override suspend fun logout(): AuthLogoutResult {
        ensureConfigured()
        if (client.auth.currentSessionOrNull() == null) {
            client.auth.clearSession()
            return AuthLogoutResult.LocalSessionCleared
        }

        return try {
            client.auth.signOut(SignOutScope.LOCAL)
            AuthLogoutResult.LocalSessionCleared
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val failure = AuthFailureMapper.map(throwable, AuthFailureContext.Logout)
            client.auth.clearSession()
            AuthLogoutResult.LocalSessionClearedWithRemoteFailure(failure)
        }
    }

    private fun ensureConfigured() {
        if (!config.isConfigured) {
            throw AuthConfigurationException()
        }
    }
}

private fun SessionStatus.toAuthState(): AuthState =
    when (this) {
        is SessionStatus.Authenticated -> AuthState.SignedIn(
            AuthUser(
                id = session.user?.id.orEmpty(),
                email = session.user?.email,
            ),
        )
        SessionStatus.Initializing -> AuthState.Loading
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        is SessionStatus.RefreshFailure -> refreshFailureCause().toAuthState()
    }

/**
 * The SDK deprecates the status property in favor of the transient refresh-failure event.
 * Restoration needs the cause attached to the current status after initialization, so keep
 * this compatibility boundary until the SDK exposes an equivalent stateful replacement.
 */
@Suppress("DEPRECATION")
private fun SessionStatus.RefreshFailure.refreshFailureCause(): RefreshFailureCause = cause

private fun RefreshFailureCause.toAuthState(): AuthState =
    toAuthFailure().toAuthState()

private fun RefreshFailureCause.toRestorationResult(): AuthRestorationResult =
    when (val state = toAuthState()) {
        is AuthState.SessionExpired -> AuthRestorationResult.ExpiredOrInvalid(state.failure)
        is AuthState.RestorationWarning -> AuthRestorationResult.TemporaryFailure(state.failure)
        is AuthState.Error -> AuthRestorationResult.Failure(state.failure)
        AuthState.Loading -> AuthRestorationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
        AuthState.SignedOut -> AuthRestorationResult.NoSavedSession
        is AuthState.SignedIn -> AuthRestorationResult.Restored(state.user)
    }

private fun Throwable.toRestorationResult(): AuthRestorationResult {
    val failure = AuthFailureMapper.map(this, AuthFailureContext.Restore)
    return when (failure.category) {
        AuthFailureCategory.ExpiredOrInvalidSession ->
            AuthRestorationResult.ExpiredOrInvalid(failure)
        AuthFailureCategory.NetworkUnavailable,
        AuthFailureCategory.Timeout,
        -> AuthRestorationResult.TemporaryFailure(failure)
        else -> AuthRestorationResult.Failure(failure)
    }
}

private fun RefreshFailureCause.toAuthFailure(): AuthFailure =
    when (this) {
        is RefreshFailureCause.NetworkError -> AuthFailureMapper.map(
            exception,
            AuthFailureContext.Restore,
        )
        is RefreshFailureCause.InternalServerError -> AuthFailureMapper.map(
            exception,
            AuthFailureContext.Restore,
        )
    }

private fun AuthFailure.toAuthState(): AuthState =
    when (category) {
        AuthFailureCategory.ExpiredOrInvalidSession -> AuthState.SessionExpired(this)
        AuthFailureCategory.NetworkUnavailable,
        AuthFailureCategory.Timeout,
        -> AuthState.RestorationWarning(this)
        else -> AuthState.Error(this)
    }
