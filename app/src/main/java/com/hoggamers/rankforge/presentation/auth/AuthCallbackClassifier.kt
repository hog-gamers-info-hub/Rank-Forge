package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig

enum class AuthCallbackKind {
    NORMAL_AUTH_CALLBACK,
    PASSWORD_RECOVERY_CALLBACK,
}

object AuthCallbackClassifier {
    fun classify(
        scheme: String?,
        host: String?,
        path: String?,
    ): AuthCallbackKind =
        if (
            scheme == SupabaseAuthConfig.AUTH_CALLBACK_SCHEME &&
                host == SupabaseAuthConfig.AUTH_CALLBACK_HOST &&
                path == "/password-recovery"
        ) {
            AuthCallbackKind.PASSWORD_RECOVERY_CALLBACK
        } else {
            AuthCallbackKind.NORMAL_AUTH_CALLBACK
        }
}
