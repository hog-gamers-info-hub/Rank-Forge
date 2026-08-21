package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.sync.CloudRevision
import java.time.LocalDate
import java.util.UUID

data class MatchCloudRestorationPayloads(
    val tournamentId: String,
    val matches: List<MatchCloudRestorePayload>,
    val results: List<MatchResultCloudRestorePayload>,
    val cloudRevision: Int,
)

sealed interface MatchCloudRestorationMappingResult {
    data class Success(val value: MatchCloudRestorationSnapshot) : MatchCloudRestorationMappingResult
    data object Invalid : MatchCloudRestorationMappingResult
}

object MatchCloudRestorationMapper {
    fun map(payloads: MatchCloudRestorationPayloads): MatchCloudRestorationMappingResult {
        val tournamentUuid = payloads.tournamentId.toUuidOrNull() ?: return MatchCloudRestorationMappingResult.Invalid
        if (payloads.matches.map { it.id }.distinct().size != payloads.matches.size ||
            payloads.matches.map { it.matchNumber }.distinct().size != payloads.matches.size
        ) return MatchCloudRestorationMappingResult.Invalid
        val matches = payloads.matches.map { payload ->
            val status = when (payload.status.lowercase()) { "draft" -> MatchStatus.DRAFT; "finalized" -> MatchStatus.FINALIZED; else -> return MatchCloudRestorationMappingResult.Invalid }
            if (payload.id.toUuidOrNull() == null || payload.tournamentId != payloads.tournamentId ||
                payload.matchNumber !in 1..10) return MatchCloudRestorationMappingResult.Invalid
            val date = runCatching { LocalDate.parse(payload.matchDate) }.getOrNull() ?: return MatchCloudRestorationMappingResult.Invalid
            val rows = payloads.results.filter { it.matchId == payload.id }
            if (rows.map { it.id }.distinct().size != rows.size ||
                rows.map { it.teamSlotId }.distinct().size != rows.size || rows.any { row ->
                    row.id.toUuidOrNull() == null || row.kills < 0 || teamSlotNumber(tournamentUuid, row.teamSlotId) == null ||
                        row.matchId != payload.id
                }) return MatchCloudRestorationMappingResult.Invalid
            val participantResults = if (status == MatchStatus.FINALIZED) rows.map { row ->
                val slot = teamSlotNumber(tournamentUuid, row.teamSlotId)!!
                val participationStatus = when (row.participationStatus?.uppercase()) {
                    null, "PARTICIPATED" -> MatchParticipationStatus.PARTICIPATED
                    "NO_SHOW" -> MatchParticipationStatus.NO_SHOW
                    else -> return MatchCloudRestorationMappingResult.Invalid
                }
                runCatching { MatchParticipantResult(slot, participationStatus, row.placement, row.kills) }
                    .getOrNull() ?: return MatchCloudRestorationMappingResult.Invalid
            } else emptyList()
            if (status == MatchStatus.FINALIZED) {
                val participated = participantResults.filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
                if (participantResults.isEmpty() || participated.isEmpty() ||
                    participated.mapNotNull { it.placement }.toSet() != (1..participated.size).toSet()
                ) return MatchCloudRestorationMappingResult.Invalid
            }
            val placements = participantResults.mapNotNull { it.placement?.let { position -> MatchPlacement(it.teamSlotNumber, position) } }
            val draftPlacements = if (status == MatchStatus.DRAFT) rows.mapNotNull { row ->
                row.placement?.let { position -> MatchPlacement(teamSlotNumber(tournamentUuid, row.teamSlotId)!!, position) }
            } else emptyList()
            val draftKills = if (status == MatchStatus.DRAFT) rows.map {
                MatchKill(teamSlotNumber(tournamentUuid, it.teamSlotId)!!, it.kills)
            } else emptyList()
            if (draftPlacements.any { it.position !in TeamSlot.SLOT_NUMBERS } ||
                draftPlacements.map { it.position }.distinct().size != draftPlacements.size
            ) return MatchCloudRestorationMappingResult.Invalid
            Match(id = payload.id, tournamentId = payloads.tournamentId, matchNumber = payload.matchNumber, date = date, mapName = payload.mapName, status = status,
                placements = (if (status == MatchStatus.DRAFT) draftPlacements else placements).sortedBy { it.teamSlotNumber },
                kills = (if (status == MatchStatus.DRAFT) draftKills else participantResults
                    .filter { it.placement != null }
                    .map { MatchKill(it.teamSlotNumber, it.kills) }).sortedBy { it.teamSlotNumber },
                participantResults = participantResults.sortedBy { it.teamSlotNumber },
            )
        }
        if (payloads.results.any { it.matchId !in payloads.matches.map { match -> match.id }.toSet() }) return MatchCloudRestorationMappingResult.Invalid
        val cloudRevision = payloads.cloudRevision.takeIf { it > 0 }?.let(::CloudRevision)
            ?: return MatchCloudRestorationMappingResult.Invalid
        return MatchCloudRestorationMappingResult.Success(MatchCloudRestorationSnapshot(payloads.tournamentId, matches.sortedBy { it.matchNumber }, cloudRevision))
    }

    private fun teamSlotNumber(tournamentId: UUID, teamSlotId: String): Int? = TeamSlot.SLOT_NUMBERS.firstOrNull {
        TournamentCloudIdentity.teamSlotId(tournamentId, it) == teamSlotId
    }
    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
