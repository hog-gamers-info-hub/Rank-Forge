package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import io.github.jan.supabase.auth.auth
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

@Serializable
data class TournamentRosterCloudReplacementV2Parameters(
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

interface TournamentRosterSnapshotRpcInvoker {
    suspend fun invoke(parameters: TournamentRosterCloudReplacementParameters): RevisionWriteResponse
    suspend fun invokeGroupRotation(parameters: TournamentRosterCloudReplacementV2Parameters): RevisionWriteResponse
}

@Singleton
class SupabaseTournamentRosterCloudReplacementRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val sessionProbe: SupabaseAuthSessionProbe,
    private val rpcInvoker: TournamentRosterSnapshotRpcInvoker,
) : TournamentRosterCloudReplacementRemoteDataSource {
    override suspend fun replace(
        payloads: TournamentRosterCloudReplacementPayloads,
        expectedRevision: Int,
    ): TournamentRosterCloudReplacementRemoteResult {
        if (!config.isConfigured) {
            return TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.VALIDATION)
        }

        return try {
            val response = when (payloads.format) {
                TournamentFormat.STANDARD -> {
                    if (clientProvider.client.auth.currentSessionOrNull() == null) {
                        return TournamentRosterCloudReplacementRemoteResult.Failure(
                            CloudUploadFailureCategory.AUTHENTICATION,
                        )
                    }
                    rpcInvoker.invoke(
                        TournamentRosterCloudReplacementParameters(
                            tournamentId = payloads.tournamentId,
                            teamSlots = payloads.teamSlots,
                            players = payloads.players,
                            expectedRevision = expectedRevision,
                        ),
                    )
                }
                TournamentFormat.GROUP_ROTATION -> {
                    sessionProbe.awaitInitialization()
                    if (!sessionProbe.currentReadiness().allowsAuthenticatedState()) {
                        return TournamentRosterCloudReplacementRemoteResult.Failure(
                            CloudUploadFailureCategory.AUTHENTICATION,
                        )
                    }
                    rpcInvoker.invokeGroupRotation(
                        TournamentRosterCloudReplacementV2Parameters(
                            tournamentId = payloads.tournamentId,
                            teamSlots = payloads.teamSlots,
                            players = payloads.players,
                            expectedRevision = expectedRevision,
                        ),
                    )
                }
            }
            if (payloads.format == TournamentFormat.GROUP_ROTATION) {
                response.toGroupRotationResult(expectedRevision)
            } else {
                response.toLegacyResult(expectedRevision)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            TournamentRosterCloudReplacementRemoteResult.Failure(throwable.toCloudUploadFailureCategory())
        }
    }
}

private fun RevisionWriteResponse.toLegacyResult(
    expectedRevision: Int,
): TournamentRosterCloudReplacementRemoteResult = when (outcome) {
    "success" -> revision
        ?.takeIf { it > 0 }
        ?.let(TournamentRosterCloudReplacementRemoteResult::Success)
        ?: TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.VALIDATION)
    "stale_write" -> TournamentRosterCloudReplacementRemoteResult.Conflict(
        toRevisionConflict(expectedRevision),
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

private fun RevisionWriteResponse.toGroupRotationResult(
    expectedRevision: Int,
): TournamentRosterCloudReplacementRemoteResult = when (outcome) {
    "success" -> revision
        ?.takeIf { it > 0 }
        ?.let(TournamentRosterCloudReplacementRemoteResult::Success)
        ?: TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.VALIDATION)
    "stale_write" -> TournamentRosterCloudReplacementRemoteResult.Conflict(
        toRevisionConflict(expectedRevision),
    )
    "missing_revision" -> TournamentRosterCloudReplacementRemoteResult.Conflict(
        RevisionConflict.MissingRevision,
    )
    "matches_exist" -> TournamentRosterCloudReplacementRemoteResult.BlockedByExistingMatches
    "authentication_required" -> TournamentRosterCloudReplacementRemoteResult.Failure(
        CloudUploadFailureCategory.AUTHENTICATION,
    )
    "unauthorized" -> TournamentRosterCloudReplacementRemoteResult.Failure(
        CloudUploadFailureCategory.AUTHORIZATION,
    )
    "validation_failure" -> TournamentRosterCloudReplacementRemoteResult.Failure(
        CloudUploadFailureCategory.VALIDATION,
    )
    else -> TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.UNKNOWN)
}
