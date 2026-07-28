package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRemoteResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class TournamentRevisionRestorePayload(val id: String, val revision: Int)
@Serializable data class MatchCloudRestorePayload(val id: String, @SerialName("tournament_id") val tournamentId: String, @SerialName("match_number") val matchNumber: Int, @SerialName("match_date") val matchDate: String, @SerialName("map_name") val mapName: String, val status: String, val revision: Int)
@Serializable data class MatchResultCloudRestorePayload(val id: String, @SerialName("match_id") val matchId: String, @SerialName("team_slot_id") val teamSlotId: String, val placement: Int?, val kills: Int)

interface MatchCloudRestorationRemoteDataSource { suspend fun readOwnedMatches(tournamentId: String): MatchCloudRestorationRemoteResult<MatchCloudRestorationPayloads> }

@Singleton class SupabaseMatchCloudRestorationRemoteDataSource @Inject constructor(private val config: SupabaseAuthConfig, private val clientProvider: SupabaseClientProvider) : MatchCloudRestorationRemoteDataSource {
    override suspend fun readOwnedMatches(tournamentId: String): MatchCloudRestorationRemoteResult<MatchCloudRestorationPayloads> {
        if (!config.isConfigured) return MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.VALIDATION)
        if (clientProvider.client.auth.currentSessionOrNull() == null) return MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.AUTHENTICATION)
        return try {
            val tournament = clientProvider.client.from("tournaments").select { filter { eq("id", tournamentId) } }.decodeList<TournamentRevisionRestorePayload>().singleOrNull()
                ?: return MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.AUTHORIZATION)
            val matches = clientProvider.client.from("matches").select { filter { eq("tournament_id", tournamentId) } }.decodeList<MatchCloudRestorePayload>()
            val results = matches.flatMap { match -> clientProvider.client.from("match_results").select { filter { eq("match_id", match.id) } }.decodeList<MatchResultCloudRestorePayload>() }
            MatchCloudRestorationRemoteResult.Success(MatchCloudRestorationPayloads(tournamentId, matches, results, tournament.revision))
        } catch (c: CancellationException) { throw c } catch (t: Throwable) { MatchCloudRestorationRemoteResult.Failure(t.category()) }
    }
}
private fun Throwable.category(): MatchCloudRestorationFailureCategory { val m = message.orEmpty().lowercase(); return when { m.contains("42501") || m.contains("row-level security") || m.contains("forbidden") || m.contains("403") -> MatchCloudRestorationFailureCategory.AUTHORIZATION; m.contains("401") || m.contains("unauthorized") || m.contains("session") || m.contains("jwt") -> MatchCloudRestorationFailureCategory.AUTHENTICATION; this is IOException || m.contains("network") || m.contains("timeout") || m.contains("connection") -> MatchCloudRestorationFailureCategory.NETWORK; else -> MatchCloudRestorationFailureCategory.VALIDATION } }
