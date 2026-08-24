package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RestoredRosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.sync.CloudRevision
import java.time.LocalDate
import java.util.UUID

data class TournamentCloudRestorationPayloads(
    val tournament: TournamentUploadPayload,
    val teamSlots: List<TeamSlotUploadPayload>,
    val players: List<PlayerUploadPayload>,
)

sealed interface TournamentCloudRestorationMappingResult<out T> {
    data class Success<T>(val value: T) : TournamentCloudRestorationMappingResult<T>

    data object Invalid : TournamentCloudRestorationMappingResult<Nothing>
}

object TournamentCloudRestorationMapper {
    fun mapSummaries(
        payloads: List<TournamentUploadPayload>,
    ): TournamentCloudRestorationMappingResult<List<TournamentCloudRestorationSummary>> {
        val summaries = payloads.map { payload ->
            val parsedDate = payload.tournamentDate.toLocalDateOrNull() ?: return TournamentCloudRestorationMappingResult.Invalid
            if (payload.id.toUuidOrNull() == null || payload.ownerId.isBlank()) {
                return TournamentCloudRestorationMappingResult.Invalid
            }
            TournamentCloudRestorationSummary(
                id = payload.id,
                name = payload.name,
                date = parsedDate.toString(),
                organizerName = payload.organizerName,
                status = payload.status,
            )
        }
        return TournamentCloudRestorationMappingResult.Success(summaries)
    }

    fun mapSnapshot(
        payloads: TournamentCloudRestorationPayloads,
    ): TournamentCloudRestorationMappingResult<TournamentCloudRestorationSnapshot> {
        val tournamentUuid = payloads.tournament.id.toUuidOrNull()
            ?: return TournamentCloudRestorationMappingResult.Invalid
        val date = payloads.tournament.tournamentDate.toLocalDateOrNull()
            ?: return TournamentCloudRestorationMappingResult.Invalid
        val status = payloads.tournament.status.toLocalStatusOrNull()
            ?: return TournamentCloudRestorationMappingResult.Invalid
        val cloudRevision = payloads.tournament.revision
            ?.takeIf { it > 0 }
            ?.let(::CloudRevision)
            ?: return TournamentCloudRestorationMappingResult.Invalid
        if (payloads.tournament.ownerId.isBlank()) {
            return TournamentCloudRestorationMappingResult.Invalid
        }

        val slotsByNumber = payloads.teamSlots.groupBy { it.slotNumber }
        if (
            slotsByNumber.values.any { it.size > 1 } ||
            payloads.teamSlots.any {
                it.tournamentId != payloads.tournament.id ||
                    it.slotNumber !in TeamSlot.SLOT_NUMBERS
            }
        ) {
            return TournamentCloudRestorationMappingResult.Invalid
        }

        val slots = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            val payload = slotsByNumber[slotNumber]?.singleOrNull()
            TeamSlot.create(
                tournamentId = payloads.tournament.id,
                slotNumber = slotNumber,
                teamName = payload?.teamName.orEmpty(),
            )
        }
        val slotIds = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber ->
            TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber)
        }
        if (payloads.players.map { it.id }.distinct().size != payloads.players.size) {
            return TournamentCloudRestorationMappingResult.Invalid
        }

        val players = payloads.players
            .groupBy { player -> player.teamSlotId }
            .map { (teamSlotId, playerPayloads) ->
                val slotNumber = slotIds.entries.firstOrNull { it.value == teamSlotId }?.key
                    ?: return TournamentCloudRestorationMappingResult.Invalid
                if (playerPayloads.size > 6) {
                    return TournamentCloudRestorationMappingResult.Invalid
                }
                val positionsByPlayerId = (1..6).associateWith { position ->
                    TournamentCloudIdentity.playerId(tournamentUuid, slotNumber, position)
                }.entries.associate { (position, playerId) -> playerId to position }
                val positionedPlayers = playerPayloads.map { player ->
                    val position = positionsByPlayerId[player.id]
                        ?: return TournamentCloudRestorationMappingResult.Invalid
                    position to player
                }
                if (positionedPlayers.map { it.first }.distinct().size != positionedPlayers.size) {
                    return TournamentCloudRestorationMappingResult.Invalid
                }
                positionedPlayers
                    .sortedBy { it.first }
                    .map { player ->
                        RestoredRosterPlayer(
                            tournamentId = payloads.tournament.id,
                            slotNumber = slotNumber,
                            rosterPosition = player.first,
                            displayName = player.second.displayName,
                        )
                    }
            }
            .flatten()

        return TournamentCloudRestorationMappingResult.Success(
            TournamentCloudRestorationSnapshot(
                tournament = Tournament(
                    id = payloads.tournament.id,
                    name = payloads.tournament.name,
                    date = date,
                    organizerName = payloads.tournament.organizerName,
                    organizerContactNumber = payloads.tournament.organizerContact,
                    status = status,
                    ownerUserId = payloads.tournament.ownerId,
                ),
                slots = slots,
                players = players,
                cloudRevision = cloudRevision,
            ),
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    private fun String.toLocalStatusOrNull(): TournamentStatus? = when (lowercase()) {
        "draft" -> TournamentStatus.DRAFT
        "confirmed", "active", "completed", "archived" -> TournamentStatus.CONFIRMED
        else -> null
    }
}
