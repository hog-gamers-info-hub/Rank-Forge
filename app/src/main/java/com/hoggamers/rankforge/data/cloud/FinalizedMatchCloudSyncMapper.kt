package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FinalizedMatchUploadPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("match_number") val matchNumber: Int,
    @SerialName("match_date") val matchDate: String,
    @SerialName("map_name") val mapName: String,
    val status: String,
)

@Serializable
data class FinalizedMatchResultUploadPayload(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("team_slot_id") val teamSlotId: String,
    val placement: Int?,
    val kills: Int,
    val source: String,
    @SerialName("review_status") val reviewStatus: String,
    @SerialName("participation_status") val participationStatus: String = "PARTICIPATED",
)

data class FinalizedMatchCloudSyncPayloads(
    val matches: List<FinalizedMatchUploadPayload>,
    val matchResults: List<FinalizedMatchResultUploadPayload>,
)

sealed interface FinalizedMatchCloudSyncMappingResult {
    data class Success(val payloads: FinalizedMatchCloudSyncPayloads) : FinalizedMatchCloudSyncMappingResult
    data object Invalid : FinalizedMatchCloudSyncMappingResult
}

object FinalizedMatchCloudSyncMapper {
    fun map(snapshot: FinalizedMatchCloudSyncSnapshot): FinalizedMatchCloudSyncMappingResult {
        val tournamentUuid = snapshot.tournament.id.toUuidOrNull()
            ?: return FinalizedMatchCloudSyncMappingResult.Invalid
        if (
            snapshot.teamSlots.size != TeamSlot.SLOT_NUMBERS.count() ||
            snapshot.teamSlots.map { it.slotNumber }.toSet() != TeamSlot.SLOT_NUMBERS.toSet() ||
            snapshot.teamSlots.map { it.slotNumber }.distinct().size != snapshot.teamSlots.size ||
            snapshot.teamSlots.any { it.tournamentId != snapshot.tournament.id }
        ) {
            return FinalizedMatchCloudSyncMappingResult.Invalid
        }
        if (snapshot.matches.any { it.tournamentId != snapshot.tournament.id }) {
            return FinalizedMatchCloudSyncMappingResult.Invalid
        }

        val finalizedMatches = snapshot.matches.filter { it.status == MatchStatus.FINALIZED }
        if (finalizedMatches.map { it.id }.distinct().size != finalizedMatches.size ||
            finalizedMatches.map { it.matchNumber }.distinct().size != finalizedMatches.size ||
            finalizedMatches.any { it.matchNumber !in 1..10 }
        ) {
            return FinalizedMatchCloudSyncMappingResult.Invalid
        }

        val orderedFinalizedMatches = finalizedMatches.sortedBy { it.matchNumber }
        val matchPayloadByLocalId = orderedFinalizedMatches
            .associate { match ->
                match.id to FinalizedMatchUploadPayload(
                    id = MatchCloudIdentity.matchId(tournamentUuid, match.id),
                    tournamentId = snapshot.tournament.id,
                    matchNumber = match.matchNumber,
                    matchDate = match.date.toString(),
                    mapName = match.mapName,
                    status = "finalized",
                )
            }
        val resultPayloads = orderedFinalizedMatches.flatMap { match ->
            match.toFinalizedResultPayloads(
                tournamentId = tournamentUuid,
                cloudMatchId = matchPayloadByLocalId.getValue(match.id).id,
            ) ?: return FinalizedMatchCloudSyncMappingResult.Invalid
        }

        return FinalizedMatchCloudSyncMappingResult.Success(
            FinalizedMatchCloudSyncPayloads(
                matches = matchPayloadByLocalId.values.sortedBy { it.matchNumber },
                matchResults = resultPayloads,
            ),
        )
    }

    private fun Match.toFinalizedResultPayloads(
        tournamentId: UUID,
        cloudMatchId: String,
    ): List<FinalizedMatchResultUploadPayload>? {
        val participantResults = finalizedParticipantResultsOrNull() ?: return null

        return participantResults.map { result ->
            val slotNumber = result.teamSlotNumber
            val teamSlotId = TournamentCloudIdentity.teamSlotId(tournamentId, slotNumber)
            FinalizedMatchResultUploadPayload(
                id = MatchCloudIdentity.matchResultId(cloudMatchId, teamSlotId),
                matchId = cloudMatchId,
                teamSlotId = teamSlotId,
                placement = result.placement,
                kills = result.kills,
                source = "manual",
                reviewStatus = "confirmed",
                participationStatus = result.participationStatus.name,
            )
        }
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
