package com.hoggamers.rankforge.data.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton

enum class SupabaseAuthSessionStatus {
    INITIALIZING,
    AUTHENTICATED,
    REFRESH_FAILURE,
    NOT_AUTHENTICATED,
}

data class SupabaseAuthSessionReadiness(
    val status: SupabaseAuthSessionStatus,
    val hasUsableSession: Boolean,
)

interface SupabaseAuthSessionProbe {
    suspend fun awaitInitialization()

    fun currentReadiness(): SupabaseAuthSessionReadiness
}

@Singleton
class SupabaseClientAuthSessionProbe @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : SupabaseAuthSessionProbe {
    override suspend fun awaitInitialization() {
        clientProvider.client.auth.awaitInitialization()
    }

    override fun currentReadiness(): SupabaseAuthSessionReadiness {
        val auth = clientProvider.client.auth
        return SupabaseAuthSessionReadiness(
            status = auth.sessionStatus.value.toSupabaseAuthSessionStatus(),
            hasUsableSession = auth.currentSessionOrNull() != null,
        )
    }
}

internal fun SupabaseAuthSessionReadiness.allowsAuthenticatedState(): Boolean =
    status == SupabaseAuthSessionStatus.AUTHENTICATED && hasUsableSession

private fun SessionStatus.toSupabaseAuthSessionStatus(): SupabaseAuthSessionStatus =
    when (this) {
        SessionStatus.Initializing -> SupabaseAuthSessionStatus.INITIALIZING
        is SessionStatus.Authenticated -> SupabaseAuthSessionStatus.AUTHENTICATED
        is SessionStatus.RefreshFailure -> SupabaseAuthSessionStatus.REFRESH_FAILURE
        is SessionStatus.NotAuthenticated -> SupabaseAuthSessionStatus.NOT_AUTHENTICATED
    }
