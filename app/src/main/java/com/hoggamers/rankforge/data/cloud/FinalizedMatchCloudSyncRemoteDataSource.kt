package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FinalizedMatchCloudSyncRemoteDataSource {
    suspend fun sync(payloads: FinalizedMatchCloudSyncPayloads, expectedRevision: Int): FinalizedMatchCloudSyncExecutionResult
}

@Singleton
class SupabaseFinalizedMatchCloudSyncRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val rpcInvoker: MatchSnapshotRpcInvoker,
) : FinalizedMatchCloudSyncRemoteDataSource {
    override suspend fun sync(payloads: FinalizedMatchCloudSyncPayloads, expectedRevision: Int): FinalizedMatchCloudSyncExecutionResult =
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
                    try {
                        FinalizedMatchCloudSyncRpcRouter(rpcInvoker)
                            .invoke(payloads, expectedRevision)
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        FinalizedMatchCloudSyncExecutionResult.Failure(
                            null,
                            throwable.toFinalizedMatchCloudSyncFailureCategory(),
                        )
                    }
                }
            }
        }
}
