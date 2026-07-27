package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterNameNormalizer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TournamentUploadPayload(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("tournament_date") val tournamentDate: String,
    @SerialName("organizer_name") val organizerName: String,
    @SerialName("organizer_contact") val organizerContact: String,
    val status: String,
)

@Serializable
data class TeamSlotUploadPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("slot_number") val slotNumber: Int,
    @SerialName("team_name") val teamName: String,
    val status: String,
)

@Serializable
data class PlayerUploadPayload(
    val id: String,
    @SerialName("team_slot_id") val teamSlotId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("normalized_name") val normalizedName: String,
)

data class TournamentCloudUploadPayloads(
    val tournament: TournamentUploadPayload,
    val teamSlots: List<TeamSlotUploadPayload>,
    val players: List<PlayerUploadPayload>,
)

sealed interface TournamentCloudUploadMappingResult {
    data class Success(val payloads: TournamentCloudUploadPayloads) : TournamentCloudUploadMappingResult
    data object Invalid : TournamentCloudUploadMappingResult
}

object TournamentCloudUploadMapper {
    fun map(
        snapshot: TournamentCloudUploadSnapshot,
        ownerId: String,
    ): TournamentCloudUploadMappingResult {
        val tournamentUuid = snapshot.tournament.id.toUuidOrNull() ?: return TournamentCloudUploadMappingResult.Invalid
        if (ownerId.isBlank()) return TournamentCloudUploadMappingResult.Invalid

        val slotsByNumber = snapshot.slots.groupBy { it.slotNumber }
        if (slotsByNumber.values.any { it.size > 1 }) return TournamentCloudUploadMappingResult.Invalid
        if (snapshot.slots.any { it.tournamentId != snapshot.tournament.id }) {
            return TournamentCloudUploadMappingResult.Invalid
        }
        if (snapshot.rosters.keys.any { it !in TeamSlot.SLOT_NUMBERS }) {
            return TournamentCloudUploadMappingResult.Invalid
        }

        val slotPayloads = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            val localSlot = slotsByNumber[slotNumber]?.singleOrNull()
                ?: TeamSlot.create(snapshot.tournament.id, slotNumber)
            TeamSlotUploadPayload(
                id = deterministicUuid("rank-forge:team-slot:${tournamentUuid}:$slotNumber"),
                tournamentId = snapshot.tournament.id,
                slotNumber = slotNumber,
                teamName = localSlot.teamName,
                status = "draft",
            )
        }

        val playerPayloads = snapshot.rosters
            .toSortedMap()
            .flatMap { (slotNumber, players) ->
                players.mapIndexed { index, player ->
                    if (player.tournamentId != snapshot.tournament.id || player.slotNumber != slotNumber) {
                        return TournamentCloudUploadMappingResult.Invalid
                    }
                    val position = index + 1
                    PlayerUploadPayload(
                        id = deterministicUuid(
                            "rank-forge:player:${tournamentUuid}:$slotNumber:$position",
                        ),
                        teamSlotId = deterministicUuid(
                            "rank-forge:team-slot:${tournamentUuid}:$slotNumber",
                        ),
                        displayName = player.displayName,
                        normalizedName = RosterNameNormalizer.normalize(player.displayName),
                    )
                }
            }

        return TournamentCloudUploadMappingResult.Success(
            TournamentCloudUploadPayloads(
                tournament = TournamentUploadPayload(
                    id = snapshot.tournament.id,
                    ownerId = ownerId,
                    name = snapshot.tournament.name,
                    tournamentDate = snapshot.tournament.date.toString(),
                    organizerName = snapshot.tournament.organizerName,
                    organizerContact = snapshot.tournament.organizerContactNumber,
                    status = "draft",
                ),
                teamSlots = slotPayloads,
                players = playerPayloads,
            ),
        )
    }

    private fun deterministicUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
