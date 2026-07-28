package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionAction
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionRequest
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionResult
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProtectedMatchCorrectionParameters(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_match_id") val matchId: String,
    @SerialName("p_match_results") val matchResults: List<FinalizedMatchResultUploadPayload>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
    @SerialName("p_correction_reason") val correctionReason: String? = null,
)

@Singleton
class SupabaseProtectedMatchCorrectionAction @Inject constructor(
    private val remoteDataSource: SupabaseProtectedMatchCorrectionRemoteDataSource,
) : ProtectedMatchCorrectionAction {
    override suspend fun invoke(request: ProtectedMatchCorrectionRequest): ProtectedMatchCorrectionResult {
        val payload = request.toParameters() ?: return ProtectedMatchCorrectionResult.ValidationFailure
        return try {
            when (val response = remoteDataSource.correct(payload)) {
                else -> when (response.outcome) {
                    "success" -> response.revision?.let(ProtectedMatchCorrectionResult::Success)
                        ?: ProtectedMatchCorrectionResult.ValidationFailure
                    "already_corrected" -> response.revision?.let(ProtectedMatchCorrectionResult::AlreadyCorrected)
                        ?: ProtectedMatchCorrectionResult.ValidationFailure
                    "stale_write" -> ProtectedMatchCorrectionResult.Conflict(
                        response.toRevisionConflict(request.expectedRevision),
                    )
                    "missing_revision" -> ProtectedMatchCorrectionResult.Conflict(RevisionConflict.MissingRevision)
                    "authentication_required" -> ProtectedMatchCorrectionResult.AuthenticationRequired
                    "unauthorized" -> ProtectedMatchCorrectionResult.AuthorizationFailure
                    "match_not_finalized" -> ProtectedMatchCorrectionResult.MatchNotFinalized
                    else -> ProtectedMatchCorrectionResult.ValidationFailure
                }
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: IllegalStateException) {
            if (error.message == "Authentication required.") {
                ProtectedMatchCorrectionResult.AuthenticationRequired
            } else {
                ProtectedMatchCorrectionResult.ValidationFailure
            }
        } catch (_: Throwable) {
            ProtectedMatchCorrectionResult.NetworkFailure
        }
    }
}

private fun ProtectedMatchCorrectionRequest.toParameters(): ProtectedMatchCorrectionParameters? {
    val tournamentId = runCatching { UUID.fromString(tournament.id) }.getOrNull() ?: return null
    val cloudMatchId = MatchCloudIdentity.matchId(tournamentId, match.id)
    val placementBySlot = placements.associateBy { it.teamSlotNumber }
    val killsBySlot = kills.associateBy { it.teamSlotNumber }
    if (placementBySlot.keys != TeamSlot.SLOT_NUMBERS.toSet() ||
        killsBySlot.keys != TeamSlot.SLOT_NUMBERS.toSet() ||
        placementBySlot.values.map(MatchPlacement::position).toSet() != TeamSlot.SLOT_NUMBERS.toSet() ||
        killsBySlot.values.any { it.kills < 0 }
    ) return null
    return ProtectedMatchCorrectionParameters(
        tournamentId = tournament.id,
        matchId = cloudMatchId,
        expectedRevision = expectedRevision,
        matchResults = TeamSlot.SLOT_NUMBERS.map { slot ->
            val teamSlotId = TournamentCloudIdentity.teamSlotId(tournamentId, slot)
            FinalizedMatchResultUploadPayload(
                id = MatchCloudIdentity.matchResultId(cloudMatchId, teamSlotId),
                matchId = cloudMatchId,
                teamSlotId = teamSlotId,
                placement = placementBySlot.getValue(slot).position,
                kills = killsBySlot.getValue(slot).kills,
                source = "manual",
                reviewStatus = "confirmed",
            )
        },
    )
}
