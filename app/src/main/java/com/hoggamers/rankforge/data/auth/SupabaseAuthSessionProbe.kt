package com.hoggamers.rankforge.data.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

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
        val session = auth.currentSessionOrNull()
        return SupabaseAuthSessionReadiness(
            status = auth.sessionStatus.value.toSupabaseAuthSessionStatus(),
            hasUsableSession = session?.isLocallyUsable() == true,
        )
    }
}

internal fun SupabaseAuthSessionReadiness.allowsAuthenticatedState(): Boolean =
    status == SupabaseAuthSessionStatus.AUTHENTICATED && hasUsableSession

internal fun UserSession.isLocallyUsable(now: kotlin.time.Instant = Clock.System.now()): Boolean =
    expiresAt > now

private fun SessionStatus.toSupabaseAuthSessionStatus(): SupabaseAuthSessionStatus =
    when (this) {
        SessionStatus.Initializing -> SupabaseAuthSessionStatus.INITIALIZING
        is SessionStatus.Authenticated -> SupabaseAuthSessionStatus.AUTHENTICATED
        is SessionStatus.RefreshFailure -> SupabaseAuthSessionStatus.REFRESH_FAILURE
        is SessionStatus.NotAuthenticated -> SupabaseAuthSessionStatus.NOT_AUTHENTICATED
    }
