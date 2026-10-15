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
class SupabaseClientProvider private constructor(
    private val config: SupabaseAuthConfig,
    private val clientOverride: SupabaseClient?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    @Inject
    constructor(config: SupabaseAuthConfig) : this(config, null, Unit)

    internal constructor(
        config: SupabaseAuthConfig,
        client: SupabaseClient,
    ) : this(config, client, Unit)

    val client: SupabaseClient by lazy {
        clientOverride ?: createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth) {
                scheme = SupabaseAuthConfig.AUTH_CALLBACK_SCHEME
                host = SupabaseAuthConfig.AUTH_CALLBACK_HOST
                flowType = FlowType.PKCE
                autoLoadFromStorage = false
            }
            install(Postgrest)
            install(Storage)
        }
    }
}
