package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DraftMatchCloudSyncRemoteDataSource {
    suspend fun sync(payloads: DraftMatchCloudSyncPayloads): DraftMatchCloudSyncExecutionResult
}

@Singleton
class SupabaseDraftMatchCloudSyncRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : DraftMatchCloudSyncRemoteDataSource {
    override suspend fun sync(payloads: DraftMatchCloudSyncPayloads): DraftMatchCloudSyncExecutionResult =
        withContext(Dispatchers.IO) {
            when {
                !config.isConfigured -> DraftMatchCloudSyncExecutionResult.Failure(
                    completedStage = null,
                    category = DraftMatchCloudSyncFailureCategory.VALIDATION,
                )

                clientProvider.client.auth.currentSessionOrNull() == null ->
                    DraftMatchCloudSyncExecutionResult.Failure(
                        completedStage = null,
                        category = DraftMatchCloudSyncFailureCategory.AUTHENTICATION,
                    )

                else -> {
                    val client = clientProvider.client
                    DraftMatchCloudSyncExecutor(
                        upsertMatches = { matches -> client.from("matches").upsert(matches) },
                        upsertMatchResults = { results -> client.from("match_results").upsert(results) },
                    ).execute(payloads)
                }
            }
        }
}
