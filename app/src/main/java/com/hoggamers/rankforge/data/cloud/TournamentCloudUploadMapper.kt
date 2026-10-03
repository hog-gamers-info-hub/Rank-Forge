package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterNameNormalizer
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import com.hoggamers.rankforge.domain.tournament.toCloudValue
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TournamentUploadPayload(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("organizer_name") val stageName: String,
    @SerialName("organizer_contact") val organizerContact: String,
    val status: String,
    val revision: Int? = null,
    val format: String = "standard",
    @SerialName("group_count") val groupCount: Int? = null,
    @SerialName("selected_group_pairings") val selectedGroupPairings: List<GroupPairingUploadPayload> = emptyList(),
)

@Serializable
data class GroupPairingUploadPayload(
    @SerialName("first_group") val firstGroup: String,
    @SerialName("second_group") val secondGroup: String,
    @SerialName("pairing_key") val pairingKey: String,
)

@Serializable
data class TeamSlotUploadPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("slot_number") val slotNumber: Int,
    @SerialName("team_name") val teamName: String,
    val status: String,
    @SerialName("group") val group: String? = null,
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
        val expectedSlots = snapshot.tournament.formatDerivedSlots()
        val expectedSlotNumbers = expectedSlots.map { it.slotNumber }.toSet()
        if (snapshot.slots.any { it.slotNumber !in expectedSlotNumbers } ||
            snapshot.rosters.keys.any { it !in expectedSlotNumbers }
        ) {
            return TournamentCloudUploadMappingResult.Invalid
        }

        val slotPayloads = expectedSlots.map { expectedSlot ->
            val slotNumber = expectedSlot.slotNumber
            val localSlot = slotsByNumber[slotNumber]?.singleOrNull()
                ?: expectedSlot
            if (localSlot.group != expectedSlot.group) {
                return TournamentCloudUploadMappingResult.Invalid
            }
            TeamSlotUploadPayload(
                id = TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber),
                tournamentId = snapshot.tournament.id,
                slotNumber = slotNumber,
                teamName = localSlot.teamName,
                status = "draft",
                group = localSlot.group?.name,
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
                        id = TournamentCloudIdentity.playerId(tournamentUuid, slotNumber, position),
                        teamSlotId = TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber),
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
                    stageName = snapshot.tournament.stageName,
                    organizerContact = snapshot.tournament.organizerContactNumber,
                    status = "draft",
                    format = snapshot.tournament.format.toCloudValue(),
                    groupCount = snapshot.tournament.groupCount,
                    selectedGroupPairings = snapshot.tournament.selectedGroupPairings.map { pairing ->
                        GroupPairingUploadPayload(
                            firstGroup = pairing.firstGroup.name,
                            secondGroup = pairing.secondGroup.name,
                            pairingKey = pairing.canonicalKey,
                        )
                    },
                ),
                teamSlots = slotPayloads,
                players = playerPayloads,
            ),
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}

internal object TournamentCloudIdentity {
    fun teamSlotId(
        tournamentId: UUID,
        slotNumber: Int,
    ): String = deterministicUuid("rank-forge:team-slot:$tournamentId:$slotNumber")

    fun playerId(
        tournamentId: UUID,
        slotNumber: Int,
        rosterPosition: Int,
    ): String = deterministicUuid("rank-forge:player:$tournamentId:$slotNumber:$rosterPosition")

    private fun deterministicUuid(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()
}
