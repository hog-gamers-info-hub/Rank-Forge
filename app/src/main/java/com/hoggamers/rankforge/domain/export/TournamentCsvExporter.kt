package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStanding
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.tournament.TieBreakStanding
import com.hoggamers.rankforge.domain.tournament.Tournament

data class TournamentCsvExportInput(
    val tournament: Tournament,
    val matches: List<Match>,
    val teamSlots: List<TeamSlot>,
    val rosterPlayers: List<RosterPlayer>,
)

enum class TournamentCsvExportFailure {
    NO_FINALIZED_MATCHES,
    TOURNAMENT_IDENTITY_MISMATCH,
    DUPLICATE_MATCH_IDENTITY,
    INVALID_FINALIZED_MATCH_ROW_COUNT,
    INVALID_PLACEMENT,
    MISSING_PLACEMENT,
    DUPLICATE_PLACEMENT,
    DUPLICATE_TEAM_SLOT,
    MISSING_TEAM_SLOT,
    MISSING_TEAM_IDENTITY,
    INVALID_TEAM_SLOT,
    MISSING_KILL_VALUE,
    INVALID_KILL_COUNT,
    STANDINGS_GENERATION_FAILURE,
}

sealed interface TournamentCsvExportResult {
    data class Success(
        val csv: String,
    ) : TournamentCsvExportResult

    data class Failure(
        val failures: Set<TournamentCsvExportFailure>,
    ) : TournamentCsvExportResult
}

class TournamentCsvExporter(
    private val standingsEngine: CumulativeTournamentStandingsEngine =
        CumulativeTournamentStandingsEngine(),
    private val tieBreakRules: TieBreakRules = TieBreakRules(),
) {
    fun export(input: TournamentCsvExportInput): TournamentCsvExportResult {
        val failures = input.validate()
        if (failures.isNotEmpty()) {
            return TournamentCsvExportResult.Failure(failures)
        }

        val finalizedMatches = input.matches.filter { match ->
            match.status == MatchStatus.FINALIZED
        }

        val orderedStandings = runCatching {
            tieBreakRules(standingsEngine(finalizedMatches))
        }.getOrElse {
            return TournamentCsvExportResult.Failure(
                setOf(TournamentCsvExportFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        if (!orderedStandings.hasCompleteTwelveSlotCoverage()) {
            return TournamentCsvExportResult.Failure(
                setOf(TournamentCsvExportFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        val teamSlotsByNumber = input.teamSlots.associateBy { teamSlot ->
            teamSlot.slotNumber
        }
        val rosterPlayersBySlot = input.rosterPlayers.groupBy { player ->
            player.slotNumber
        }
        val totalKillsBySlot = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber ->
            finalizedMatches.sumOf { match ->
                match.kills.single { kill ->
                    kill.teamSlotNumber == slotNumber
                }.kills
            }
        }
        val bestPlacementBySlot = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber ->
            finalizedMatches.minOf { match ->
                match.placements.single { placement ->
                    placement.teamSlotNumber == slotNumber
                }.position
            }
        }
        val totalPointCounts = orderedStandings
            .groupingBy { tieBreakStanding ->
                tieBreakStanding.standing.totalPoints
            }
            .eachCount()

        val dataRows = orderedStandings.mapIndexed { index, tieBreakStanding ->
            val standing = tieBreakStanding.standing
            val players = rosterPlayersBySlot[standing.teamSlotNumber].orEmpty()

            listOf(
                EXPORT_SCHEMA_VERSION,
                EXPORT_TYPE,
                input.tournament.id,
                input.tournament.name,
                finalizedMatches.size.toString(),
                (index + 1).toString(),
                standing.teamSlotNumber.toString(),
                teamSlotsByNumber.getValue(standing.teamSlotNumber).teamName,
                players.getOrNull(0)?.displayName.orEmpty(),
                players.getOrNull(1)?.displayName.orEmpty(),
                players.getOrNull(2)?.displayName.orEmpty(),
                players.getOrNull(3)?.displayName.orEmpty(),
                standing.matchesIncluded.toString(),
                standing.totalPositionPoints.toString(),
                totalKillsBySlot.getValue(standing.teamSlotNumber).toString(),
                standing.totalKillPoints.toString(),
                standing.totalPoints.toString(),
                bestPlacementBySlot.getValue(standing.teamSlotNumber).toString(),
                standing.firstPlaceFinishes.toString(),
                tieBreakStanding.tieBreakStatus(
                    totalPointCount = totalPointCounts.getValue(standing.totalPoints),
                ),
            )
        }

        return TournamentCsvExportResult.Success(
            csv = (listOf(TOURNAMENT_CSV_HEADER) + dataRows.map { row ->
                row.toCsvRecord()
            }).joinToString(CRLF),
        )
    }

    private fun TournamentCsvExportInput.validate(): Set<TournamentCsvExportFailure> {
        val failures = linkedSetOf<TournamentCsvExportFailure>()

        if (
            matches.any { match -> match.tournamentId != tournament.id } ||
            teamSlots.any { teamSlot -> teamSlot.tournamentId != tournament.id } ||
            rosterPlayers.any { player -> player.tournamentId != tournament.id }
        ) {
            failures += TournamentCsvExportFailure.TOURNAMENT_IDENTITY_MISMATCH
        }

        if (matches.map { match -> match.id }.duplicates().isNotEmpty()) {
            failures += TournamentCsvExportFailure.DUPLICATE_MATCH_IDENTITY
        }

        validateTeamSlots(failures)
        validateRosterPlayers(failures)

        val finalizedMatches = matches.filter { match ->
            match.status == MatchStatus.FINALIZED
        }

        if (finalizedMatches.isEmpty()) {
            failures += TournamentCsvExportFailure.NO_FINALIZED_MATCHES
        }

        if (finalizedMatches.size > MAX_MATCHES_PER_TOURNAMENT) {
            failures += TournamentCsvExportFailure.STANDINGS_GENERATION_FAILURE
        }

        finalizedMatches.forEach { match ->
            validateFinalizedMatch(
                match = match,
                failures = failures,
            )
        }

        return failures
    }

    private fun TournamentCsvExportInput.validateTeamSlots(
        failures: MutableSet<TournamentCsvExportFailure>,
    ) {
        val slotNumbers = teamSlots.map { teamSlot -> teamSlot.slotNumber }

        if (teamSlots.size != REQUIRED_ROW_COUNT) {
            failures += TournamentCsvExportFailure.MISSING_TEAM_SLOT
        }

        if (slotNumbers.any { slotNumber -> slotNumber !in TeamSlot.SLOT_NUMBERS }) {
            failures += TournamentCsvExportFailure.INVALID_TEAM_SLOT
        }

        if (slotNumbers.duplicates().isNotEmpty()) {
            failures += TournamentCsvExportFailure.DUPLICATE_TEAM_SLOT
        }

        if (slotNumbers.toSet() != REQUIRED_SLOT_NUMBERS) {
            failures += TournamentCsvExportFailure.MISSING_TEAM_SLOT
        }

        if (teamSlots.any { teamSlot -> teamSlot.teamName.isBlank() }) {
            failures += TournamentCsvExportFailure.MISSING_TEAM_IDENTITY
        }
    }

    private fun TournamentCsvExportInput.validateRosterPlayers(
        failures: MutableSet<TournamentCsvExportFailure>,
    ) {
        if (rosterPlayers.any { player ->
                player.slotNumber !in TeamSlot.SLOT_NUMBERS
            }
        ) {
            failures += TournamentCsvExportFailure.INVALID_TEAM_SLOT
        }
    }

    private fun validateFinalizedMatch(
        match: Match,
        failures: MutableSet<TournamentCsvExportFailure>,
    ) {
        if (
            match.placements.size != REQUIRED_ROW_COUNT ||
            match.kills.size != REQUIRED_ROW_COUNT
        ) {
            failures += TournamentCsvExportFailure.INVALID_FINALIZED_MATCH_ROW_COUNT
        }

        val placementSlots = match.placements.map { placement ->
            placement.teamSlotNumber
        }
        val killSlots = match.kills.map { kill ->
            kill.teamSlotNumber
        }
        val placementValues = match.placements.map { placement ->
            placement.position
        }

        if (
            placementSlots.any { slotNumber -> slotNumber !in TeamSlot.SLOT_NUMBERS } ||
            killSlots.any { slotNumber -> slotNumber !in TeamSlot.SLOT_NUMBERS }
        ) {
            failures += TournamentCsvExportFailure.INVALID_TEAM_SLOT
        }

        if (
            placementSlots.duplicates().isNotEmpty() ||
            killSlots.duplicates().isNotEmpty()
        ) {
            failures += TournamentCsvExportFailure.DUPLICATE_TEAM_SLOT
        }

        if (placementValues.any { placement -> placement !in VALID_PLACEMENTS }) {
            failures += TournamentCsvExportFailure.INVALID_PLACEMENT
        }

        if (placementValues.duplicates().isNotEmpty()) {
            failures += TournamentCsvExportFailure.DUPLICATE_PLACEMENT
        }

        if (placementValues.toSet() != REQUIRED_PLACEMENTS) {
            failures += TournamentCsvExportFailure.MISSING_PLACEMENT
        }

        if (placementSlots.toSet() != REQUIRED_SLOT_NUMBERS) {
            failures += TournamentCsvExportFailure.MISSING_TEAM_SLOT
        }

        if (
            killSlots.toSet() != REQUIRED_SLOT_NUMBERS ||
            placementSlots.toSet() != killSlots.toSet()
        ) {
            failures += TournamentCsvExportFailure.MISSING_KILL_VALUE
        }

        if (match.kills.any { kill -> kill.kills < 0 }) {
            failures += TournamentCsvExportFailure.INVALID_KILL_COUNT
        }
    }

    private fun List<TieBreakStanding>.hasCompleteTwelveSlotCoverage(): Boolean =
        size == REQUIRED_ROW_COUNT &&
            map { tieBreakStanding ->
                tieBreakStanding.standing.teamSlotNumber
            }.toSet() == REQUIRED_SLOT_NUMBERS

    private fun TieBreakStanding.tieBreakStatus(
        totalPointCount: Int,
    ): String =
        when {
            isCompleteTie -> UNRESOLVED_TIE
            totalPointCount > 1 -> TIE_BREAK_APPLIED
            else -> UNIQUE_ORDER
        }

    private fun List<String>.toCsvRecord(): String =
        joinToString(",") { field -> field.toCsvField() }

    private fun String.toCsvField(): String {
        val shouldQuote =
            any { character ->
                character == ',' ||
                    character == '"' ||
                    character == '\r' ||
                    character == '\n'
            } ||
                firstOrNull()?.isWhitespace() == true ||
                lastOrNull()?.isWhitespace() == true

        return if (shouldQuote) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }

    private fun <T> List<T>.duplicates(): Set<T> =
        groupingBy { value -> value }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

    private companion object {
        const val EXPORT_SCHEMA_VERSION = "phase_10_v1"
        const val EXPORT_TYPE = "tournament_standings"
        const val UNIQUE_ORDER = "unique_order"
        const val TIE_BREAK_APPLIED = "tie_break_applied"
        const val UNRESOLVED_TIE = "unresolved_tie"
        const val REQUIRED_ROW_COUNT = 12
        const val CRLF = "\r\n"

        val VALID_PLACEMENTS = 1..12
        val REQUIRED_PLACEMENTS = VALID_PLACEMENTS.toSet()
        val REQUIRED_SLOT_NUMBERS = TeamSlot.SLOT_NUMBERS.toSet()

        const val TOURNAMENT_CSV_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,exported_match_count,standings_rank,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,matches_played,total_position_points,total_kills,total_kill_points,total_points,best_placement,first_place_count,tie_break_status"
    }
}
