package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrCorrectionSnapshot
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrRowEvidence
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.sync.CloudRevision
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

data class MatchCloudRestorationPayloads(
    val tournamentId: String,
    val matches: List<MatchCloudRestorePayload>,
    val results: List<MatchResultCloudRestorePayload>,
    val cloudRevision: Int,
    val ocrEvidence: List<MatchOcrEvidenceCloudSnapshot> = emptyList(),
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
        if (payloads.ocrEvidence.map { it.evidence.matchId }.distinct().size != payloads.ocrEvidence.size) {
            return MatchCloudRestorationMappingResult.Invalid
        }
        val ocrEvidence = payloads.ocrEvidence.map { snapshot ->
            val evidence = snapshot.evidence
            if (
                evidence.tournamentId != payloads.tournamentId ||
                matches.none { it.id == evidence.matchId && it.status == MatchStatus.FINALIZED } ||
                evidence.provenance.isBlank() ||
                snapshot.rows.any { row ->
                    row.matchId != evidence.matchId ||
                        row.tournamentId != evidence.tournamentId ||
                        row.rowIndex < 0 ||
                        row.originalPlacement?.let { it !in TeamSlot.SLOT_NUMBERS } == true ||
                        row.originalKills?.let { it < 0 } == true ||
                        row.originalSuggestedTeamSlot?.let { it !in TeamSlot.SLOT_NUMBERS } == true
                } ||
                snapshot.correctionSnapshots.any { correction ->
                    correction.matchId != evidence.matchId ||
                        correction.tournamentId != evidence.tournamentId ||
                        correction.rowIndex < 0 ||
                        correction.correctedPlacement !in TeamSlot.SLOT_NUMBERS ||
                        correction.correctedKills < 0 ||
                        correction.correctedTeamSlot !in TeamSlot.SLOT_NUMBERS
                } ||
                snapshot.rows.map { it.rowIndex }.distinct().size != snapshot.rows.size ||
                snapshot.correctionSnapshots.map { it.rowIndex }.distinct().size != snapshot.correctionSnapshots.size
            ) return MatchCloudRestorationMappingResult.Invalid
            val preservedAt = runCatching { Instant.parse(evidence.preservedAt).toEpochMilli() }.getOrNull()
                ?: return MatchCloudRestorationMappingResult.Invalid
            PreservedMatchOcrEvidence(
                tournamentId = evidence.tournamentId,
                matchId = evidence.matchId,
                sourceScreenshotId = evidence.sourceScreenshotId,
                preservedAt = preservedAt,
                provenance = evidence.provenance,
                rows = snapshot.rows.sortedBy { it.rowIndex }.map { row ->
                    PreservedMatchOcrRowEvidence(
                        rowIndex = row.rowIndex,
                        originalOcrText = row.originalOcrText,
                        originalPlacement = row.originalPlacement,
                        originalKills = row.originalKills,
                        originalSuggestedTeamSlot = row.originalSuggestedTeamSlot,
                        confidenceSummary = row.confidenceSummary,
                        safetySummary = row.safetySummary,
                        manualReviewRequired = row.manualReviewRequired,
                    )
                },
                correctionSnapshots = snapshot.correctionSnapshots.sortedBy { it.rowIndex }.map { correction ->
                    val correctionPreservedAt = runCatching {
                        Instant.parse(correction.preservedAt).toEpochMilli()
                    }.getOrNull()
                    if (correctionPreservedAt != preservedAt || correction.provenance != evidence.provenance) {
                        return MatchCloudRestorationMappingResult.Invalid
                    }
                    PreservedMatchOcrCorrectionSnapshot(
                        rowIndex = correction.rowIndex,
                        correctedPlacement = correction.correctedPlacement,
                        correctedKills = correction.correctedKills,
                        correctedTeamSlot = correction.correctedTeamSlot,
                        placementChanged = correction.placementChanged,
                        killsChanged = correction.killsChanged,
                        teamSlotChanged = correction.teamSlotChanged,
                    )
                },
            )
        }
        return MatchCloudRestorationMappingResult.Success(
            MatchCloudRestorationSnapshot(
                tournamentId = payloads.tournamentId,
                matches = matches.sortedBy { it.matchNumber },
                cloudRevision = cloudRevision,
                ocrEvidence = ocrEvidence,
            ),
        )
    }

    private fun teamSlotNumber(tournamentId: UUID, teamSlotId: String): Int? = TeamSlot.SLOT_NUMBERS.firstOrNull {
        TournamentCloudIdentity.teamSlotId(tournamentId, it) == teamSlotId
    }
    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
