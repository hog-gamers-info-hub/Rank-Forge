package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthCallbackClassifierTest {
    @Test
    fun exactPasswordRecoveryCallbackIsClassifiedAsRecovery() {
        assertEquals(
            AuthCallbackKind.PASSWORD_RECOVERY_CALLBACK,
            AuthCallbackClassifier.classify(
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME,
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST,
                path = "/password-recovery",
            ),
        )
    }

    @Test
    fun callbackWithoutPathRemainsNormalAuthCallback() {
        assertEquals(
            AuthCallbackKind.NORMAL_AUTH_CALLBACK,
            AuthCallbackClassifier.classify(
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME,
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST,
                path = null,
            ),
        )
    }

    @Test
    fun callbackWithEmptyPathRemainsNormalAuthCallback() {
        assertEquals(
            AuthCallbackKind.NORMAL_AUTH_CALLBACK,
            AuthCallbackClassifier.classify(
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME,
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST,
                path = "",
            ),
        )
    }

    @Test
    fun unexpectedCallbackPathIsRejected() {
        assertEquals(
            AuthCallbackKind.INVALID_CALLBACK,
            AuthCallbackClassifier.classify(
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME,
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST,
                path = "/unexpected",
            ),
        )
    }

    @Test
    fun wrongSchemeOrHostIsRejected() {
        assertEquals(
            AuthCallbackKind.INVALID_CALLBACK,
            AuthCallbackClassifier.classify(
                scheme = "https",
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST,
                path = null,
            ),
        )

        assertEquals(
            AuthCallbackKind.INVALID_CALLBACK,
            AuthCallbackClassifier.classify(
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME,
                host = "other-host",
                path = null,
            ),
        )
    }
}
