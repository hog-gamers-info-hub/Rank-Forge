package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchTotalEngine
import com.hoggamers.rankforge.domain.tournament.PositionPointsEngine
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament

data class MatchCsvExportInput(
    val tournament: Tournament,
    val match: Match,
    val teamSlots: List<TeamSlot>,
    val rosterPlayers: List<RosterPlayer>,
)

enum class MatchCsvExportFailure {
    MATCH_NOT_FINALIZED,
    INVALID_ROW_COUNT,
    MISSING_PLACEMENT,
    DUPLICATE_PLACEMENT,
    DUPLICATE_TEAM_SLOT,
    INVALID_TEAM_SLOT,
    MISSING_TEAM_IDENTITY,
    MISSING_KILL_VALUE,
    INVALID_KILL_COUNT,
}

sealed interface MatchCsvExportResult {
    data class Success(val csv: String) : MatchCsvExportResult

    data class Failure(val failures: Set<MatchCsvExportFailure>) : MatchCsvExportResult
}

class MatchCsvExporter(
    private val positionPointsEngine: PositionPointsEngine = PositionPointsEngine(),
    private val killPointsEngine: KillPointsEngine = KillPointsEngine(),
    private val matchTotalEngine: MatchTotalEngine = MatchTotalEngine(),
) {
    fun export(input: MatchCsvExportInput): MatchCsvExportResult {
        val failures = input.validate()
        if (failures.isNotEmpty()) {
            return MatchCsvExportResult.Failure(failures)
        }

        val teamSlotsByNumber = input.teamSlots
            .filter { teamSlot -> teamSlot.tournamentId == input.tournament.id }
            .associateBy { it.slotNumber }
        val rosterPlayersBySlot = input.rosterPlayers
            .filter { player -> player.tournamentId == input.tournament.id }
            .groupBy { it.slotNumber }
        val killsBySlot = input.match.kills.associateBy { it.teamSlotNumber }
        val correctionStatus = if (input.match.correctionHistory.isEmpty()) {
            ORIGINAL_FINALIZED
        } else {
            CORRECTED_FINALIZED
        }
        val dataRows = input.match.placements
            .sortedBy { it.position }
            .mapIndexed { index, placement ->
                val kills = killsBySlot.getValue(placement.teamSlotNumber).kills
                val players = rosterPlayersBySlot[placement.teamSlotNumber].orEmpty()
                listOf(
                    EXPORT_SCHEMA_VERSION,
                    EXPORT_TYPE,
                    input.tournament.id,
                    input.tournament.name,
                    input.match.id,
                    "Match ${input.match.matchNumber}",
                    "",
                    (index + 1).toString(),
                    placement.position.toString(),
                    placement.teamSlotNumber.toString(),
                    teamSlotsByNumber.getValue(placement.teamSlotNumber).teamName,
                    players.getOrNull(0)?.displayName.orEmpty(),
                    players.getOrNull(1)?.displayName.orEmpty(),
                    players.getOrNull(2)?.displayName.orEmpty(),
                    players.getOrNull(3)?.displayName.orEmpty(),
                    positionPointsEngine(placement.position).toString(),
                    kills.toString(),
                    killPointsEngine(kills).toString(),
                    matchTotalEngine(placement.position, kills).toString(),
                    correctionStatus,
                )
            }

        return MatchCsvExportResult.Success(
            csv = (listOf(MATCH_CSV_HEADER) + dataRows.map { row -> row.toCsvRecord() })
                .joinToString(CRLF),
        )
    }

    private fun MatchCsvExportInput.validate(): Set<MatchCsvExportFailure> {
        val failures = linkedSetOf<MatchCsvExportFailure>()
        if (match.status != MatchStatus.FINALIZED) {
            failures += MatchCsvExportFailure.MATCH_NOT_FINALIZED
        }
        if (match.placements.size != REQUIRED_ROW_COUNT || match.kills.size != REQUIRED_ROW_COUNT) {
            failures += MatchCsvExportFailure.INVALID_ROW_COUNT
        }

        val placementSlots = match.placements.map { it.teamSlotNumber }
        val killSlots = match.kills.map { it.teamSlotNumber }
        val placementValues = match.placements.map { it.position }
        val duplicatePlacementSlots = placementSlots.duplicates()
        val duplicateKillSlots = killSlots.duplicates()

        if (match.placements.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS } ||
            match.kills.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }
        ) {
            failures += MatchCsvExportFailure.INVALID_TEAM_SLOT
        }
        if (duplicatePlacementSlots.isNotEmpty() || duplicateKillSlots.isNotEmpty()) {
            failures += MatchCsvExportFailure.DUPLICATE_TEAM_SLOT
        }
        if (placementValues.duplicates().isNotEmpty()) {
            failures += MatchCsvExportFailure.DUPLICATE_PLACEMENT
        }
        if (placementValues.toSet() != TeamSlot.SLOT_NUMBERS.toSet()) {
            failures += MatchCsvExportFailure.MISSING_PLACEMENT
        }
        if (killSlots.toSet() != TeamSlot.SLOT_NUMBERS.toSet() || placementSlots.toSet() != killSlots.toSet()) {
            failures += MatchCsvExportFailure.MISSING_KILL_VALUE
        }
        if (match.kills.any { it.kills < 0 }) {
            failures += MatchCsvExportFailure.INVALID_KILL_COUNT
        }

        val teamSlotsByNumber = teamSlots
            .filter { teamSlot -> teamSlot.tournamentId == tournament.id }
            .associateBy { it.slotNumber }
        placementSlots
            .filter { slotNumber -> slotNumber in TeamSlot.SLOT_NUMBERS }
            .forEach { slotNumber ->
                val teamSlot = teamSlotsByNumber[slotNumber]
                if (teamSlot == null || teamSlot.teamName.isBlank()) {
                    failures += MatchCsvExportFailure.MISSING_TEAM_IDENTITY
                }
            }

        return failures
    }

    private fun List<String>.toCsvRecord(): String =
        joinToString(",") { field -> field.toCsvField() }

    private fun String.toCsvField(): String {
        val shouldQuote = any { it == ',' || it == '"' || it == '\r' || it == '\n' } ||
            firstOrNull()?.isWhitespace() == true ||
            lastOrNull()?.isWhitespace() == true
        return if (shouldQuote) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }

    private fun List<Int>.duplicates(): Set<Int> =
        groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

    private companion object {
        const val EXPORT_SCHEMA_VERSION = "phase_10_v1"
        const val EXPORT_TYPE = "match_result"
        const val ORIGINAL_FINALIZED = "original_finalized"
        const val CORRECTED_FINALIZED = "corrected_finalized"
        const val REQUIRED_ROW_COUNT = 12
        const val CRLF = "\r\n"
        const val MATCH_CSV_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,match_id,match_label,match_finalized_at,row_number,placement,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,placement_points,kills,kill_points,total_points,correction_status"
    }
}
