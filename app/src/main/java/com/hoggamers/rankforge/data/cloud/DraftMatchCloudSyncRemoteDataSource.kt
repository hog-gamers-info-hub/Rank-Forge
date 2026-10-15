package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DraftMatchCloudSyncRemoteDataSource {
    suspend fun sync(payloads: DraftMatchCloudSyncPayloads, expectedRevision: Int): DraftMatchCloudSyncExecutionResult
}

@Singleton
class SupabaseDraftMatchCloudSyncRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val rpcInvoker: MatchSnapshotRpcInvoker,
) : DraftMatchCloudSyncRemoteDataSource {
    override suspend fun sync(payloads: DraftMatchCloudSyncPayloads, expectedRevision: Int): DraftMatchCloudSyncExecutionResult =
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
                    try {
                        DraftMatchCloudSyncRpcRouter(rpcInvoker).invoke(payloads, expectedRevision)
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        DraftMatchCloudSyncExecutionResult.Failure(
                            null,
                            throwable.toDraftMatchCloudSyncFailureCategory(),
                        )
                    }
                }
            }
        }
}
