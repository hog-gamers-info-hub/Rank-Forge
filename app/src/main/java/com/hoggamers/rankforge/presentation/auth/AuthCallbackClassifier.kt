package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig

enum class AuthCallbackKind {
    NORMAL_AUTH_CALLBACK,
    PASSWORD_RECOVERY_CALLBACK,
    INVALID_CALLBACK,
}

object AuthCallbackClassifier {
    fun classify(
        scheme: String?,
        host: String?,
        path: String?,
    ): AuthCallbackKind {
        if (
            scheme != SupabaseAuthConfig.AUTH_CALLBACK_SCHEME ||
                host != SupabaseAuthConfig.AUTH_CALLBACK_HOST
        ) {
            return AuthCallbackKind.INVALID_CALLBACK
        }

        return when (path) {
            null, "" -> AuthCallbackKind.NORMAL_AUTH_CALLBACK
            "/password-recovery" -> AuthCallbackKind.PASSWORD_RECOVERY_CALLBACK
            else -> AuthCallbackKind.INVALID_CALLBACK
        }
    }
}
