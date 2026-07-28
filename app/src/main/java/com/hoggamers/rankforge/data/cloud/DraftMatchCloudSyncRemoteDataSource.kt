package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
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
                        val response = clientProvider.client.postgrest.rpc(
                            "write_match_snapshot",
                            MatchSnapshotWriteParameters(
                                tournamentId = payloads.matches.firstOrNull()?.tournamentId
                                    ?: return@withContext DraftMatchCloudSyncExecutionResult.Failure(
                                        null,
                                        DraftMatchCloudSyncFailureCategory.VALIDATION,
                                    ),
                                matches = payloads.matches,
                                matchResults = payloads.matchResults,
                                expectedRevision = expectedRevision,
                            ),
                        ).decodeSingle<RevisionWriteResponse>()
                        if (response.outcome == "success") DraftMatchCloudSyncExecutionResult.Success else {
                            DraftMatchCloudSyncExecutionResult.Failure(
                                null,
                                DraftMatchCloudSyncFailureCategory.CONFLICT,
                                response.toRevisionConflict(expectedRevision),
                            )
                        }
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
