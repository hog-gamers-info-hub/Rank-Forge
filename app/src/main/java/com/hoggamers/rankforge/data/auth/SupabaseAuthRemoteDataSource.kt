package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class SupabaseAuthRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
) : AuthRemoteDataSource {
    private val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth)
        }
    }

    override fun observeAuthState(): Flow<AuthState> {
        if (!config.isConfigured) {
            return flowOf(AuthState.SignedOut)
        }

        return client.auth.sessionStatus
            .map { sessionStatus -> sessionStatus.toAuthState() }
            .catch { throwable -> emit(AuthState.Error(throwable.toUserMessage())) }
    }

    override suspend fun restoreSession(): AuthState {
        if (!config.isConfigured) {
            return AuthState.SignedOut
        }

        return runCatching {
            client.auth.currentSessionOrNull()?.let { session ->
                AuthState.SignedIn(
                    AuthUser(
                        id = session.user?.id.orEmpty(),
                        email = session.user?.email,
                    ),
                )
            } ?: AuthState.SignedOut
        }.getOrElse { throwable ->
            AuthState.Error(throwable.toUserMessage())
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
    ) {
        ensureConfigured()
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
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

    override suspend fun logout() {
        ensureConfigured()
        if (client.auth.currentSessionOrNull() != null) {
            client.auth.signOut()
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
        is SessionStatus.RefreshFailure -> AuthState.Error("Your saved session expired. Sign in again.")
    }

internal fun Throwable.toUserMessage(): String =
    when (this) {
        is AuthConfigurationException -> "Supabase URL and publishable key are not configured."
        else -> message?.takeIf { it.isNotBlank() } ?: "Authentication failed. Try again."
    }
