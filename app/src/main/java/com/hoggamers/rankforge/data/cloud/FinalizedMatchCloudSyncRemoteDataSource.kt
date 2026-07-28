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

interface FinalizedMatchCloudSyncRemoteDataSource {
    suspend fun sync(payloads: FinalizedMatchCloudSyncPayloads, expectedRevision: Int): FinalizedMatchCloudSyncExecutionResult
}

@Singleton
class SupabaseFinalizedMatchCloudSyncRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
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
                        val tournamentId = payloads.matches.firstOrNull()?.tournamentId
                            ?: return@withContext FinalizedMatchCloudSyncExecutionResult.Failure(
                                null,
                                FinalizedMatchCloudSyncFailureCategory.VALIDATION,
                            )
                        var revision = expectedRevision
                        payloads.matches.sortedBy { it.matchNumber }.forEach { match ->
                            val response = clientProvider.client.postgrest.rpc(
                                "finalize_match_snapshot",
                                ProtectedMatchFinalizationParameters(
                                    tournamentId = tournamentId,
                                    match = match,
                                    matchResults = payloads.matchResults.filter { it.matchId == match.id },
                                    expectedRevision = revision,
                                ),
                            ).decodeSingle<RevisionWriteResponse>()
                            when (response.outcome) {
                                "success" -> revision = response.revision
                                    ?: return@withContext FinalizedMatchCloudSyncExecutionResult.Failure(
                                        null,
                                        FinalizedMatchCloudSyncFailureCategory.VALIDATION,
                                    )
                                "already_finalized" -> Unit
                                "stale_write", "missing_revision", "finalized_protected" ->
                                    return@withContext FinalizedMatchCloudSyncExecutionResult.Failure(
                                        null,
                                        FinalizedMatchCloudSyncFailureCategory.CONFLICT,
                                        response.toRevisionConflict(revision),
                                    )
                                "authentication_required" -> return@withContext FinalizedMatchCloudSyncExecutionResult.Failure(
                                    null, FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION,
                                )
                                "unauthorized" -> return@withContext FinalizedMatchCloudSyncExecutionResult.Failure(
                                    null, FinalizedMatchCloudSyncFailureCategory.AUTHORIZATION,
                                )
                                else -> return@withContext FinalizedMatchCloudSyncExecutionResult.Failure(
                                    null, FinalizedMatchCloudSyncFailureCategory.VALIDATION,
                                )
                            }
                        }
                        FinalizedMatchCloudSyncExecutionResult.Success
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
