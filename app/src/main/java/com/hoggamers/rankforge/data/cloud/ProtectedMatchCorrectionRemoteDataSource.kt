package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionRequest
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SupabaseProtectedMatchCorrectionRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val rpcInvoker: ProtectedMatchCorrectionRpcInvoker,
) {
    suspend fun correct(
        request: ProtectedMatchCorrectionRequest,
        parameters: ProtectedMatchCorrectionParameters,
    ): RevisionWriteResponse =
        withContext(Dispatchers.IO) {
            require(config.isConfigured) { "Supabase is not configured." }
            check(clientProvider.client.auth.currentSessionOrNull() != null) { "Authentication required." }
            invokeProtectedMatchCorrectionRpc(
                request = request,
                parameters = parameters,
                rpcInvoker = rpcInvoker,
            )
        }
}
