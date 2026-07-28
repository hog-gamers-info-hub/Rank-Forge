package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DraftMatchUploadPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("match_number") val matchNumber: Int,
    @SerialName("match_date") val matchDate: String,
    @SerialName("map_name") val mapName: String,
    val status: String,
)

@Serializable
data class DraftMatchResultUploadPayload(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("team_slot_id") val teamSlotId: String,
    val placement: Int?,
    val kills: Int,
    val source: String,
    @SerialName("review_status") val reviewStatus: String,
)

data class DraftMatchCloudSyncPayloads(
    val matches: List<DraftMatchUploadPayload>,
    val matchResults: List<DraftMatchResultUploadPayload>,
)

sealed interface DraftMatchCloudSyncMappingResult {
    data class Success(val payloads: DraftMatchCloudSyncPayloads) : DraftMatchCloudSyncMappingResult
    data object Invalid : DraftMatchCloudSyncMappingResult
}

object DraftMatchCloudSyncMapper {
    fun map(snapshot: DraftMatchCloudSyncSnapshot): DraftMatchCloudSyncMappingResult {
        val tournamentUuid = snapshot.tournament.id.toUuidOrNull()
            ?: return DraftMatchCloudSyncMappingResult.Invalid
        if (snapshot.matches.any { it.tournamentId != snapshot.tournament.id }) {
            return DraftMatchCloudSyncMappingResult.Invalid
        }

        val draftMatches = snapshot.matches.filter { it.status == MatchStatus.DRAFT }
        if (draftMatches.map { it.id }.distinct().size != draftMatches.size ||
            draftMatches.map { it.matchNumber }.distinct().size != draftMatches.size ||
            draftMatches.any { it.matchNumber !in 1..10 }
        ) {
            return DraftMatchCloudSyncMappingResult.Invalid
        }

        val matchPayloadByLocalId = draftMatches
            .sortedBy { it.matchNumber }
            .associate { match ->
                match.id to
                DraftMatchUploadPayload(
                    id = MatchCloudIdentity.matchId(tournamentUuid, match.id),
                    tournamentId = snapshot.tournament.id,
                    matchNumber = match.matchNumber,
                    matchDate = match.date.toString(),
                    mapName = match.mapName,
                    status = "draft",
                )
            }
        val matchPayloads = matchPayloadByLocalId.values.sortedBy { it.matchNumber }
        val resultPayloads = draftMatches.flatMap { match ->
            match.toResultPayloads(
                tournamentId = tournamentUuid,
                cloudMatchId = matchPayloadByLocalId.getValue(match.id).id,
            ) ?: return DraftMatchCloudSyncMappingResult.Invalid
        }

        return DraftMatchCloudSyncMappingResult.Success(
            DraftMatchCloudSyncPayloads(
                matches = matchPayloads,
                matchResults = resultPayloads.sortedWith(
                    compareBy(DraftMatchResultUploadPayload::matchId, DraftMatchResultUploadPayload::teamSlotId),
                ),
            ),
        )
    }

    private fun Match.toResultPayloads(
        tournamentId: UUID,
        cloudMatchId: String,
    ): List<DraftMatchResultUploadPayload>? {
        val placementsBySlot = placements.associateBy { it.teamSlotNumber }
        val killsBySlot = kills.associateBy { it.teamSlotNumber }
        if (placementsBySlot.size != placements.size || killsBySlot.size != kills.size) return null
        val slots = placementsBySlot.keys + killsBySlot.keys
        if (slots.any { it !in TeamSlot.SLOT_NUMBERS } ||
            placements.any { it.position !in TeamSlot.SLOT_NUMBERS } ||
            kills.any { it.kills < 0 }
        ) {
            return null
        }
        return slots.sorted().map { slotNumber ->
            val teamSlotId = TournamentCloudIdentity.teamSlotId(tournamentId, slotNumber)
            DraftMatchResultUploadPayload(
                id = MatchCloudIdentity.matchResultId(cloudMatchId, teamSlotId),
                matchId = cloudMatchId,
                teamSlotId = teamSlotId,
                placement = placementsBySlot[slotNumber]?.position,
                kills = killsBySlot[slotNumber]?.kills ?: 0,
                source = "manual",
                reviewStatus = "draft",
            )
        }
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}

internal object MatchCloudIdentity {
    fun matchId(
        tournamentId: UUID,
        localMatchId: String,
    ): String = deterministicUuid("rank-forge:match:$tournamentId:$localMatchId")

    fun matchResultId(
        matchId: String,
        teamSlotId: String,
    ): String = deterministicUuid("rank-forge:match-result:$matchId:$teamSlotId")

    private fun deterministicUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()
}
