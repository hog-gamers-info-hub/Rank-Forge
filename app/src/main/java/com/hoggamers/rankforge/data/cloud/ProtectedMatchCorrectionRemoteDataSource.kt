package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SupabaseProtectedMatchCorrectionRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) {
    suspend fun correct(parameters: ProtectedMatchCorrectionParameters): RevisionWriteResponse =
        withContext(Dispatchers.IO) {
            require(config.isConfigured) { "Supabase is not configured." }
            check(clientProvider.client.auth.currentSessionOrNull() != null) { "Authentication required." }
            clientProvider.client.postgrest.rpc(
                "correct_finalized_match_snapshot",
                parameters,
            ).decodeSingle()
        }
}
