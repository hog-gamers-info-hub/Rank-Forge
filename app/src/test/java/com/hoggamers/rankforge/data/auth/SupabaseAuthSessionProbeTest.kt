package com.hoggamers.rankforge.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.jan.supabase.auth.user.UserSession
import kotlin.time.Instant

class SupabaseAuthSessionProbeTest {
    @Test
    fun onlyAuthenticatedStatusWithUsableSessionAllowsSignedInState() {
        assertTrue(
            SupabaseAuthSessionReadiness(
                SupabaseAuthSessionStatus.AUTHENTICATED,
                hasUsableSession = true,
            ).allowsAuthenticatedState(),
        )

        listOf(
            SupabaseAuthSessionReadiness(
                SupabaseAuthSessionStatus.AUTHENTICATED,
                hasUsableSession = false,
            ),
            SupabaseAuthSessionReadiness(
                SupabaseAuthSessionStatus.INITIALIZING,
                hasUsableSession = false,
            ),
            SupabaseAuthSessionReadiness(
                SupabaseAuthSessionStatus.REFRESH_FAILURE,
                hasUsableSession = false,
            ),
            SupabaseAuthSessionReadiness(
                SupabaseAuthSessionStatus.NOT_AUTHENTICATED,
                hasUsableSession = false,
            ),
        ).forEach { readiness ->
            assertFalse(readiness.toString(), readiness.allowsAuthenticatedState())
        }
    }

    @Test
    fun cachedSessionIsUsableOnlyWhileItsAccessTokenHasNotExpired() {
        val now = Instant.fromEpochMilliseconds(2_000)
        val unexpired = UserSession(
            accessToken = "access",
            refreshToken = "refresh",
            expiresIn = 3_600,
            tokenType = "bearer",
            expiresAt = Instant.fromEpochMilliseconds(3_000),
        )
        val expired = unexpired.copy(expiresAt = Instant.fromEpochMilliseconds(1_000))

        assertTrue(unexpired.isLocallyUsable(now))
        assertFalse(expired.isLocallyUsable(now))
    }
}
