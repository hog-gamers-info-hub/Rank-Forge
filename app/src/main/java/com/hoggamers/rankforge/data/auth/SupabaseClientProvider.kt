package com.hoggamers.rankforge.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
            install(Auth)
            install(Postgrest)
        }
    }
}
