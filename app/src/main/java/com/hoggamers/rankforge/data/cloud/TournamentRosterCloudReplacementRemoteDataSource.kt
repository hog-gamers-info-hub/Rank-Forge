package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TournamentRosterCloudReplacementParameters(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_team_slots") val teamSlots: List<TournamentRosterTeamSlotPayload>,
    @SerialName("p_players") val players: List<TournamentRosterPlayerPayload>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
)

sealed interface TournamentRosterCloudReplacementRemoteResult {
    data class Success(val newCloudRevision: Int) : TournamentRosterCloudReplacementRemoteResult
    data object BlockedByExistingMatches : TournamentRosterCloudReplacementRemoteResult
    data class Conflict(val conflict: RevisionConflict) : TournamentRosterCloudReplacementRemoteResult
    data class Failure(val category: CloudUploadFailureCategory) : TournamentRosterCloudReplacementRemoteResult
}

interface TournamentRosterCloudReplacementRemoteDataSource {
    suspend fun replace(
        payloads: TournamentRosterCloudReplacementPayloads,
        expectedRevision: Int,
    ): TournamentRosterCloudReplacementRemoteResult
}

@Singleton
class SupabaseTournamentRosterCloudReplacementRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : TournamentRosterCloudReplacementRemoteDataSource {
    override suspend fun replace(
        payloads: TournamentRosterCloudReplacementPayloads,
        expectedRevision: Int,
    ): TournamentRosterCloudReplacementRemoteResult {
        if (!config.isConfigured) {
            return TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.VALIDATION)
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.AUTHENTICATION)
        }

        return try {
            val response = clientProvider.client.postgrest.rpc(
                "replace_tournament_roster_snapshot",
                TournamentRosterCloudReplacementParameters(
                    tournamentId = payloads.tournamentId,
                    teamSlots = payloads.teamSlots,
                    players = payloads.players,
                    expectedRevision = expectedRevision,
                ),
            ).decodeSingle<RevisionWriteResponse>()
            when (response.outcome) {
                "success" -> response.revision
                    ?.takeIf { it > 0 }
                    ?.let(TournamentRosterCloudReplacementRemoteResult::Success)
                    ?: TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.VALIDATION)
                "stale_write" -> TournamentRosterCloudReplacementRemoteResult.Conflict(
                    response.toRevisionConflict(expectedRevision),
                )
                "missing_revision" -> TournamentRosterCloudReplacementRemoteResult.Conflict(
                    RevisionConflict.MissingRevision,
                )
                "matches_exist" -> TournamentRosterCloudReplacementRemoteResult.BlockedByExistingMatches
                "validation_failure" -> TournamentRosterCloudReplacementRemoteResult.Failure(
                    CloudUploadFailureCategory.VALIDATION,
                )
                else -> TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.UNKNOWN)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            TournamentRosterCloudReplacementRemoteResult.Failure(throwable.toCloudUploadFailureCategory())
        }
    }
}
