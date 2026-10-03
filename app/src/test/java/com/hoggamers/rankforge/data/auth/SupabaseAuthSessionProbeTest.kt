package com.hoggamers.rankforge.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
