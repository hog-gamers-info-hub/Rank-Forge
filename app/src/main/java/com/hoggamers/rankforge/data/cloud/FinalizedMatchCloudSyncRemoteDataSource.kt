package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FinalizedMatchCloudSyncRemoteDataSource {
    suspend fun sync(payloads: FinalizedMatchCloudSyncPayloads): FinalizedMatchCloudSyncExecutionResult
}

@Singleton
class SupabaseFinalizedMatchCloudSyncRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : FinalizedMatchCloudSyncRemoteDataSource {
    override suspend fun sync(payloads: FinalizedMatchCloudSyncPayloads): FinalizedMatchCloudSyncExecutionResult =
        withContext(Dispatchers.IO) {
            when {
                !config.isConfigured -> FinalizedMatchCloudSyncExecutionResult.Failure(
                    completedStage = null,
                    category = FinalizedMatchCloudSyncFailureCategory.VALIDATION,
                )

                clientProvider.client.auth.currentSessionOrNull() == null ->
                    FinalizedMatchCloudSyncExecutionResult.Failure(
                        completedStage = null,
                        category = FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION,
                    )

                else -> {
                    val client = clientProvider.client
                    FinalizedMatchCloudSyncExecutor(
                        upsertMatches = { matches -> client.from("matches").upsert(matches) },
                        upsertMatchResults = { results -> client.from("match_results").upsert(results) },
                    ).execute(payloads)
                }
            }
        }
}
