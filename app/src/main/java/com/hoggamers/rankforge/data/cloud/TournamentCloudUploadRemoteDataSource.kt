package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

interface TournamentCloudUploadRemoteDataSource {
    suspend fun upload(payloads: TournamentCloudUploadPayloads): CloudUploadExecutionResult
}

@Singleton
class SupabaseTournamentCloudUploadRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : TournamentCloudUploadRemoteDataSource {
    override suspend fun upload(payloads: TournamentCloudUploadPayloads): CloudUploadExecutionResult {
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

        val client = clientProvider.client
        return TournamentCloudUploadExecutor(
            upsertTournament = { payload ->
                client.from("tournaments").upsert(payload)
            },
            upsertTeamSlots = { payloadsToUpload ->
                client.from("tournament_team_slots").upsert(payloadsToUpload)
            },
            upsertPlayers = { payloadsToUpload ->
                client.from("players").upsert(payloadsToUpload)
            },
        ).execute(payloads)
    }
}
