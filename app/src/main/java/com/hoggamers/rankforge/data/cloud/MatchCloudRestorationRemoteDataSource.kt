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
@Serializable data class MatchCloudRestorePayload(val id: String, @SerialName("tournament_id") val tournamentId: String, @SerialName("match_number") val matchNumber: Int, @SerialName("match_date") val matchDate: String, @SerialName("map_name") val mapName: String, val status: String, val revision: Int, @SerialName("group_pairing_key") val groupPairingKey: String? = null)
@Serializable data class MatchResultCloudRestorePayload(val id: String, @SerialName("match_id") val matchId: String, @SerialName("team_slot_id") val teamSlotId: String, val placement: Int?, val kills: Int, @SerialName("participation_status") val participationStatus: String? = null)

interface MatchCloudRestorationRemoteDataSource { suspend fun readOwnedMatches(tournamentId: String): MatchCloudRestorationRemoteResult<MatchCloudRestorationPayloads> }

@Singleton
class SupabaseMatchCloudRestorationRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val reader: MatchCloudRestorationRemoteReader,
) : MatchCloudRestorationRemoteDataSource {
    override suspend fun readOwnedMatches(tournamentId: String): MatchCloudRestorationRemoteResult<MatchCloudRestorationPayloads> {
        if (!config.isConfigured) return MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.VALIDATION)
        if (clientProvider.client.auth.currentSessionOrNull() == null) return MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.AUTHENTICATION)
        return try {
            readRevisionFencedSnapshot(tournamentId)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            MatchCloudRestorationRemoteResult.Failure(t.category())
        }
    }

    private suspend fun readRevisionFencedSnapshot(
        tournamentId: String,
    ): MatchCloudRestorationRemoteResult<MatchCloudRestorationPayloads> {
        for (attempt in 0 until MAX_REVISION_READ_ATTEMPTS) {
            val firstParent = reader.readTournament(tournamentId)
                ?: return MatchCloudRestorationRemoteResult.Failure(
                    MatchCloudRestorationFailureCategory.AUTHORIZATION,
                )
            if (firstParent.id != tournamentId || firstParent.revision <= 0) {
                return MatchCloudRestorationRemoteResult.Failure(
                    MatchCloudRestorationFailureCategory.VALIDATION,
                )
            }

            val matches = reader.readMatches(tournamentId)
            val results = matches.flatMap { match -> reader.readResults(match.id) }

            val secondParent = reader.readTournament(tournamentId)
                ?: return MatchCloudRestorationRemoteResult.Failure(
                    MatchCloudRestorationFailureCategory.AUTHORIZATION,
                )
            if (secondParent.id != tournamentId || secondParent.revision <= 0) {
                return MatchCloudRestorationRemoteResult.Failure(
                    MatchCloudRestorationFailureCategory.VALIDATION,
                )
            }

            if (firstParent.revision == secondParent.revision) {
                return MatchCloudRestorationRemoteResult.Success(
                    MatchCloudRestorationPayloads(
                        tournamentId = tournamentId,
                        matches = matches,
                        results = results,
                        cloudRevision = firstParent.revision,
                    ),
                )
            }

            if (attempt == MAX_REVISION_READ_ATTEMPTS - 1) {
                return MatchCloudRestorationRemoteResult.Failure(
                    MatchCloudRestorationFailureCategory.NETWORK,
                )
            }
        }
        error("Revision-fenced match restoration read did not complete.")
    }
}

private const val MAX_REVISION_READ_ATTEMPTS = 2

private fun Throwable.category(): MatchCloudRestorationFailureCategory { val m = message.orEmpty().lowercase(); return when { m.contains("42501") || m.contains("row-level security") || m.contains("forbidden") || m.contains("403") -> MatchCloudRestorationFailureCategory.AUTHORIZATION; m.contains("401") || m.contains("unauthorized") || m.contains("session") || m.contains("jwt") -> MatchCloudRestorationFailureCategory.AUTHENTICATION; this is IOException || m.contains("network") || m.contains("timeout") || m.contains("connection") -> MatchCloudRestorationFailureCategory.NETWORK; else -> MatchCloudRestorationFailureCategory.VALIDATION } }
