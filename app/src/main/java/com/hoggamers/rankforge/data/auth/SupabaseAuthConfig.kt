package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.BuildConfig

data class SupabaseAuthConfig(
    val supabaseUrl: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean =
        supabaseUrl.isNotBlank() &&
            publishableKey.isNotBlank() &&
            supabaseUrl != DEFAULT_SUPABASE_URL &&
            publishableKey != DEFAULT_SUPABASE_PUBLISHABLE_KEY

    companion object {
        const val DEFAULT_SUPABASE_URL = "https://example.supabase.co"
        const val DEFAULT_SUPABASE_PUBLISHABLE_KEY = "replace-with-supabase-publishable-key"
        const val AUTH_CALLBACK_SCHEME = "com.pointiq.app"
        const val AUTH_CALLBACK_HOST = "auth-callback"
        const val PASSWORD_RECOVERY_REDIRECT_URL =
            "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST/password-recovery"

        fun fromBuildConfig(): SupabaseAuthConfig = SupabaseAuthConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}
