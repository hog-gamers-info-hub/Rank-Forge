package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

interface TournamentCloudUploadRemoteDataSource {
    suspend fun upload(payloads: TournamentCloudUploadPayloads, expectedRevision: Int): CloudUploadExecutionResult
}

@Singleton
class SupabaseTournamentCloudUploadRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : TournamentCloudUploadRemoteDataSource {
    override suspend fun upload(payloads: TournamentCloudUploadPayloads, expectedRevision: Int): CloudUploadExecutionResult {
        if (!config.isConfigured) {
            return CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.VALIDATION,
            )
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = CloudUploadFailureCategory.AUTHENTICATION,
            )
        }

        return try {
            val response = clientProvider.client.postgrest.rpc(
                "write_tournament_snapshot",
                TournamentSnapshotWriteParameters(
                    tournament = payloads.tournament,
                    teamSlots = payloads.teamSlots,
                    players = payloads.players,
                    expectedRevision = expectedRevision,
                ),
            ).decodeSingle<RevisionWriteResponse>()
            if (response.outcome == "success") {
                CloudUploadExecutionResult.Success(response.revision)
            } else {
                CloudUploadExecutionResult.Failure(
                    completedStage = null,
                    category = CloudUploadFailureCategory.CONFLICT,
                    conflict = response.toRevisionConflict(expectedRevision),
                )
            }
        } catch (cancellation: java.util.concurrent.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            CloudUploadExecutionResult.Failure(
                completedStage = null,
                category = throwable.toCloudUploadFailureCategory(),
            )
        }
    }
}
