package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterNameNormalizer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacement
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TournamentRosterTeamSlotPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("slot_number") val slotNumber: Int,
    @SerialName("team_name") val teamName: String,
    val status: String,
)

@Serializable
data class TournamentRosterPlayerPayload(
    val id: String,
    @SerialName("team_slot_id") val teamSlotId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("normalized_name") val normalizedName: String,
)

data class TournamentRosterCloudReplacementPayloads(
    val tournamentId: String,
    val teamSlots: List<TournamentRosterTeamSlotPayload>,
    val players: List<TournamentRosterPlayerPayload>,
)

sealed interface TournamentRosterCloudReplacementMappingResult {
    data class Success(val payloads: TournamentRosterCloudReplacementPayloads) : TournamentRosterCloudReplacementMappingResult
    data object Invalid : TournamentRosterCloudReplacementMappingResult
}

object TournamentRosterCloudReplacementMapper {
    fun map(
        snapshot: TournamentRosterCloudReplacement,
        ownerId: String,
    ): TournamentRosterCloudReplacementMappingResult {
        val tournamentUuid = snapshot.tournament.id.toUuidOrNull() ?: return TournamentRosterCloudReplacementMappingResult.Invalid
        if (ownerId.isBlank()) return TournamentRosterCloudReplacementMappingResult.Invalid
        if (snapshot.slots.size != TeamSlot.SLOT_NUMBERS.count()) return TournamentRosterCloudReplacementMappingResult.Invalid
        if (snapshot.slots.any { it.tournamentId != snapshot.tournament.id }) return TournamentRosterCloudReplacementMappingResult.Invalid

        val slotsByNumber = snapshot.slots.groupBy { it.slotNumber }
        if (slotsByNumber.keys != TeamSlot.SLOT_NUMBERS.toSet() || slotsByNumber.values.any { it.size != 1 }) {
            return TournamentRosterCloudReplacementMappingResult.Invalid
        }
        if (snapshot.rosters.keys.any { it !in TeamSlot.SLOT_NUMBERS }) return TournamentRosterCloudReplacementMappingResult.Invalid

        val slotPayloads = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            val slot = slotsByNumber.getValue(slotNumber).single()
            TournamentRosterTeamSlotPayload(
                id = TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber),
                tournamentId = snapshot.tournament.id,
                slotNumber = slotNumber,
                teamName = slot.teamName,
                status = "draft",
            )
        }

        val playerPayloads = snapshot.rosters.toSortedMap().flatMap { (slotNumber, players) ->
            if (players.size > 6) return TournamentRosterCloudReplacementMappingResult.Invalid
            val normalizedNames = players.map { RosterNameNormalizer.normalize(it.displayName) }
            if (normalizedNames.any { it.isBlank() } || normalizedNames.toSet().size != normalizedNames.size) {
                return TournamentRosterCloudReplacementMappingResult.Invalid
            }
            players.mapIndexed { index, player ->
                if (player.tournamentId != snapshot.tournament.id || player.slotNumber != slotNumber) {
                    return TournamentRosterCloudReplacementMappingResult.Invalid
                }
                TournamentRosterPlayerPayload(
                    id = TournamentCloudIdentity.playerId(tournamentUuid, slotNumber, index + 1),
                    teamSlotId = TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber),
                    displayName = player.displayName,
                    normalizedName = normalizedNames[index],
                )
            }
        }

        return TournamentRosterCloudReplacementMappingResult.Success(
            TournamentRosterCloudReplacementPayloads(snapshot.tournament.id, slotPayloads, playerPayloads),
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
