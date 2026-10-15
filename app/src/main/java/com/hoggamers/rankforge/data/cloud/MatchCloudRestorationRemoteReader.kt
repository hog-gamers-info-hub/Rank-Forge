package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

interface MatchCloudRestorationRemoteReader {
    suspend fun readTournament(tournamentId: String): TournamentRevisionRestorePayload?

    suspend fun readMatches(tournamentId: String): List<MatchCloudRestorePayload>

    suspend fun readResults(matchId: String): List<MatchResultCloudRestorePayload>
}

@Singleton
class SupabaseMatchCloudRestorationRemoteReader @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : MatchCloudRestorationRemoteReader {
    override suspend fun readTournament(tournamentId: String): TournamentRevisionRestorePayload? =
        clientProvider.client
            .from("tournaments")
            .select { filter { eq("id", tournamentId) } }
            .decodeList<TournamentRevisionRestorePayload>()
            .singleOrNull()

    override suspend fun readMatches(tournamentId: String): List<MatchCloudRestorePayload> =
        clientProvider.client
            .from("matches")
            .select { filter { eq("tournament_id", tournamentId) } }
            .decodeList()

    override suspend fun readResults(matchId: String): List<MatchResultCloudRestorePayload> =
        clientProvider.client
            .from("match_results")
            .select { filter { eq("match_id", matchId) } }
            .decodeList()
}
