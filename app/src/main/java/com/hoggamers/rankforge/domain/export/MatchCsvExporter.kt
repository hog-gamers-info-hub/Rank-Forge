package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchTotalEngine
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.PositionPointsEngine
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull

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
        return when (val rowsResult = buildMatchRows(input)) {
            is MatchExportRowsResult.Success -> MatchCsvExportResult.Success(
                csv = (listOf(MATCH_CSV_HEADER) + rowsResult.rows.map { row ->
                    row.orderedFields().toCsvRecord()
                }).joinToString(CRLF),
            )
            is MatchExportRowsResult.Failure -> MatchCsvExportResult.Failure(rowsResult.failures)
        }
    }

    fun buildMatchRows(input: MatchCsvExportInput): MatchExportRowsResult {
        val failures = input.validate()
        if (failures.isNotEmpty()) {
            return MatchExportRowsResult.Failure(failures)
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
        val participantResults = input.match.finalizedParticipantResultsOrNull()
            ?: return MatchExportRowsResult.Failure(
                setOf(MatchCsvExportFailure.INVALID_ROW_COUNT),
            )
        val rows = participantResults
            .sortedWith(compareBy< com.hoggamers.rankforge.domain.tournament.MatchParticipantResult> {
                it.placement ?: Int.MAX_VALUE
            }.thenBy { it.teamSlotNumber })
            .mapIndexed { index, result ->
                val players = rosterPlayersBySlot[result.teamSlotNumber].orEmpty()
                MatchExportRow(
                    exportSchemaVersion = EXPORT_SCHEMA_VERSION,
                    exportType = EXPORT_TYPE,
                    tournamentId = input.tournament.id,
                    tournamentName = input.tournament.name,
                    matchId = input.match.id,
                    matchLabel = "Match ${input.match.matchNumber}",
                    matchFinalizedAt = "",
                    rowNumber = index + 1,
                    placement = result.placement,
                    participationStatus = result.participationStatus.name,
                    teamSlot = result.teamSlotNumber,
                    teamName = teamSlotsByNumber.getValue(result.teamSlotNumber).teamName,
                    player1Name = players.getOrNull(0)?.displayName.orEmpty(),
                    player2Name = players.getOrNull(1)?.displayName.orEmpty(),
                    player3Name = players.getOrNull(2)?.displayName.orEmpty(),
                    player4Name = players.getOrNull(3)?.displayName.orEmpty(),
                    placementPoints = result.placement?.let(positionPointsEngine::invoke) ?: 0,
                    kills = result.kills,
                    killPoints = if (result.isNoShow) 0 else killPointsEngine(result.kills),
                    totalPoints = result.placement?.let {
                        matchTotalEngine(it, result.kills)
                    } ?: 0,
                    correctionStatus = correctionStatus,
                )
            }

        return MatchExportRowsResult.Success(rows)
    }

    private fun MatchCsvExportInput.validate(): Set<MatchCsvExportFailure> {
        val failures = linkedSetOf<MatchCsvExportFailure>()
        if (match.status != MatchStatus.FINALIZED) {
            failures += MatchCsvExportFailure.MATCH_NOT_FINALIZED
        }

        val tournamentTeamSlots = teamSlots.filter { teamSlot ->
            teamSlot.tournamentId == tournament.id
        }
        if (
            tournamentTeamSlots.size != TeamSlot.SLOT_NUMBERS.count() ||
            tournamentTeamSlots.map { it.slotNumber }.distinct().size != tournamentTeamSlots.size ||
            tournamentTeamSlots.map { it.slotNumber }.toSet() != TeamSlot.SLOT_NUMBERS.toSet()
        ) {
            failures += MatchCsvExportFailure.INVALID_TEAM_SLOT
        }

        if (match.participantResults.isEmpty()) {
            val participation = tournamentTeamSlots.analyzeTeamSlotParticipation()
            val activeSlots = participation.activeSlotNumbers.toSet()
            val expectedPlacements = (1..participation.activeCount).toSet()
            if (participation.activeCount == 0) {
                failures += MatchCsvExportFailure.MISSING_TEAM_IDENTITY
            }
            if (match.placements.size != participation.activeCount ||
                match.kills.size != participation.activeCount
            ) {
                failures += MatchCsvExportFailure.INVALID_ROW_COUNT
            }
            val placementSlots = match.placements.map { it.teamSlotNumber }
            val killSlots = match.kills.map { it.teamSlotNumber }
            val placementValues = match.placements.map { it.position }
            if (match.placements.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS } ||
                match.kills.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }
            ) {
                failures += MatchCsvExportFailure.INVALID_TEAM_SLOT
            }
            if (placementSlots.duplicates().isNotEmpty() || killSlots.duplicates().isNotEmpty()) {
                failures += MatchCsvExportFailure.DUPLICATE_TEAM_SLOT
            }
            if (placementValues.duplicates().isNotEmpty()) {
                failures += MatchCsvExportFailure.DUPLICATE_PLACEMENT
            }
            if (placementValues.toSet() != expectedPlacements) {
                failures += MatchCsvExportFailure.MISSING_PLACEMENT
            }
            if (killSlots.toSet() != activeSlots || placementSlots.toSet() != activeSlots) {
                failures += MatchCsvExportFailure.MISSING_KILL_VALUE
            }
            if (match.kills.any { it.kills < 0 }) {
                failures += MatchCsvExportFailure.INVALID_KILL_COUNT
            }
            val teamSlotsByNumber = tournamentTeamSlots.associateBy { it.slotNumber }
            placementSlots.filter { it in activeSlots }.forEach { slotNumber ->
                if (teamSlotsByNumber[slotNumber]?.teamName.isNullOrBlank()) {
                    failures += MatchCsvExportFailure.MISSING_TEAM_IDENTITY
                }
            }
            return failures
        }

        val participantResults = match.finalizedParticipantResultsOrNull()
        if (participantResults == null) {
            failures += MatchCsvExportFailure.MISSING_TEAM_IDENTITY
            return failures
        }
        val participantSlots = participantResults.map { it.teamSlotNumber }
        val participated = participantResults.filter {
            it.participationStatus == MatchParticipationStatus.PARTICIPATED
        }
        val expectedPlacements = (1..participated.size).toSet()
        if (participantResults.isEmpty() || participated.isEmpty()) {
            failures += MatchCsvExportFailure.INVALID_ROW_COUNT
        }
        if (participantSlots.size > TeamSlot.MAX_SLOT_NUMBER ||
            participantSlots.duplicates().isNotEmpty() ||
            participantSlots.any { it !in TeamSlot.SLOT_NUMBERS }
        ) {
            failures += MatchCsvExportFailure.INVALID_TEAM_SLOT
        }
        if (participated.mapNotNull { it.placement }.duplicates().isNotEmpty()) {
            failures += MatchCsvExportFailure.DUPLICATE_PLACEMENT
        }
        if (participated.mapNotNull { it.placement }.toSet() != expectedPlacements) {
            failures += MatchCsvExportFailure.MISSING_PLACEMENT
        }
        if (participantResults.any {
                it.participationStatus == MatchParticipationStatus.NO_SHOW &&
                    (it.placement != null || it.kills != 0)
            }) {
            failures += MatchCsvExportFailure.MISSING_KILL_VALUE
        }
        if (participantResults.any { it.kills < 0 }) {
            failures += MatchCsvExportFailure.INVALID_KILL_COUNT
        }

        val teamSlotsByNumber = teamSlots
            .filter { teamSlot -> teamSlot.tournamentId == tournament.id }
            .associateBy { it.slotNumber }
        participantSlots
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
        const val EXPORT_SCHEMA_VERSION = "phase_10_v2"
        const val EXPORT_TYPE = "match_result"
        const val ORIGINAL_FINALIZED = "original_finalized"
        const val CORRECTED_FINALIZED = "corrected_finalized"
        const val CRLF = "\r\n"
        const val MATCH_CSV_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,match_id,match_label,match_finalized_at,row_number,placement,participation_status,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,placement_points,kills,kill_points,total_points,correction_status"
    }
}
