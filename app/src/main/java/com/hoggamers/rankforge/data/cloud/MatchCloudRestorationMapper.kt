package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
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
                payload.matchNumber !in 1..10 || payload.mapName.isBlank()) return MatchCloudRestorationMappingResult.Invalid
            val date = runCatching { LocalDate.parse(payload.matchDate) }.getOrNull() ?: return MatchCloudRestorationMappingResult.Invalid
            val rows = payloads.results.filter { it.matchId == payload.id }
            if (rows.map { it.teamSlotId }.distinct().size != rows.size || rows.any { row ->
                    row.id.toUuidOrNull() == null || row.kills < 0 || teamSlotNumber(tournamentUuid, row.teamSlotId) == null
                }) return MatchCloudRestorationMappingResult.Invalid
            val placements = rows.mapNotNull { row -> row.placement?.let { MatchPlacement(teamSlotNumber(tournamentUuid, row.teamSlotId)!!, it) } }
            if (placements.any { it.position !in TeamSlot.SLOT_NUMBERS } || placements.map { it.position }.distinct().size != placements.size) return MatchCloudRestorationMappingResult.Invalid
            if (status == MatchStatus.FINALIZED && (rows.size != 12 || placements.size != 12 || placements.map { it.position }.toSet() != TeamSlot.SLOT_NUMBERS.toSet())) return MatchCloudRestorationMappingResult.Invalid
            Match(id = payload.id, tournamentId = payloads.tournamentId, matchNumber = payload.matchNumber, date = date, mapName = payload.mapName, status = status,
                placements = placements.sortedBy { it.teamSlotNumber }, kills = rows.map { MatchKill(teamSlotNumber(tournamentUuid, it.teamSlotId)!!, it.kills) }.sortedBy { it.teamSlotNumber })
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
