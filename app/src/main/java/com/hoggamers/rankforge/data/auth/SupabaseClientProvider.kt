package com.hoggamers.rankforge.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor(
    private val config: SupabaseAuthConfig,
) {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth) {
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Storage)
        }
    }
}
