package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStanding
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.tournament.TieBreakStanding
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull

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
        return when (val rowsResult = buildStandingsRows(input)) {
            is TournamentStandingsExportRowsResult.Success ->
                TournamentCsvExportResult.Success(
                    csv = (listOf(TOURNAMENT_CSV_HEADER) + rowsResult.rows.map { row ->
                        row.orderedFields().toCsvRecord()
                    }).joinToString(CRLF),
                )
            is TournamentStandingsExportRowsResult.Failure ->
                TournamentCsvExportResult.Failure(rowsResult.failures)
        }
    }

    fun buildStandingsRows(
        input: TournamentCsvExportInput,
    ): TournamentStandingsExportRowsResult {
        val failures = input.validate()
        if (failures.isNotEmpty()) {
            return TournamentStandingsExportRowsResult.Failure(failures)
        }

        val finalizedMatches = input.matches.filter { match ->
            match.status == MatchStatus.FINALIZED
        }

        val orderedStandings = runCatching {
            tieBreakRules(standingsEngine(finalizedMatches))
        }.getOrElse {
            return TournamentStandingsExportRowsResult.Failure(
                setOf(TournamentCsvExportFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        val expectedStandingSlots = finalizedMatches
            .flatMap { match -> match.finalizedParticipantResultsOrNull().orEmpty() }
            .map { result -> result.teamSlotNumber }
            .toSet()
        if (!orderedStandings.hasCompleteParticipantCoverage(expectedStandingSlots)) {
            return TournamentStandingsExportRowsResult.Failure(
                setOf(TournamentCsvExportFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        val teamSlotsByNumber = input.teamSlots.associateBy { teamSlot ->
            teamSlot.slotNumber
        }
        val rosterPlayersBySlot = input.rosterPlayers.groupBy { player ->
            player.slotNumber
        }
        val totalKillsBySlot = orderedStandings.associate { tieBreakStanding ->
            val slotNumber = tieBreakStanding.standing.teamSlotNumber
            slotNumber to finalizedMatches.sumOf { match ->
                match.finalizedParticipantResultsOrNull()
                    ?.firstOrNull { result -> result.teamSlotNumber == slotNumber }
                    ?.takeIf { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
                    ?.kills ?: 0
            }
        }
        val bestPlacementBySlot = orderedStandings.associate { tieBreakStanding ->
            val slotNumber = tieBreakStanding.standing.teamSlotNumber
            val placements = finalizedMatches.mapNotNull { match ->
                match.finalizedParticipantResultsOrNull()
                    ?.firstOrNull { result -> result.teamSlotNumber == slotNumber }
                    ?.takeIf { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
                    ?.placement
            }
            slotNumber to placements.minOrNull()
        }
        val totalPointCounts = orderedStandings
            .groupingBy { tieBreakStanding ->
                tieBreakStanding.standing.totalPoints
            }
            .eachCount()

        val rows = orderedStandings.mapIndexed { index, tieBreakStanding ->
            val standing = tieBreakStanding.standing
            val players = rosterPlayersBySlot[standing.teamSlotNumber].orEmpty()

            TournamentStandingsExportRow(
                exportSchemaVersion = EXPORT_SCHEMA_VERSION,
                exportType = EXPORT_TYPE,
                tournamentId = input.tournament.id,
                tournamentName = input.tournament.name,
                exportedMatchCount = finalizedMatches.size,
                standingsRank = index + 1,
                teamSlot = standing.teamSlotNumber,
                teamName = teamSlotsByNumber.getValue(standing.teamSlotNumber).teamName,
                player1Name = players.getOrNull(0)?.displayName.orEmpty(),
                player2Name = players.getOrNull(1)?.displayName.orEmpty(),
                player3Name = players.getOrNull(2)?.displayName.orEmpty(),
                player4Name = players.getOrNull(3)?.displayName.orEmpty(),
                matchesPlayed = standing.matchesIncluded,
                totalPositionPoints = standing.totalPositionPoints,
                totalKills = totalKillsBySlot.getValue(standing.teamSlotNumber),
                totalKillPoints = standing.totalKillPoints,
                totalPoints = standing.totalPoints,
                bestPlacement = bestPlacementBySlot.getValue(standing.teamSlotNumber),
                firstPlaceCount = standing.firstPlaceFinishes,
                tieBreakStatus = tieBreakStanding.tieBreakStatus(
                    totalPointCount = totalPointCounts.getValue(standing.totalPoints),
                ),
            )
        }

        return TournamentStandingsExportRowsResult.Success(rows)
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

        val referencedParticipantSlots = finalizedMatches
            .flatMap { match -> match.finalizedParticipantResultsOrNull().orEmpty() }
            .map { result -> result.teamSlotNumber }
            .toSet()
        val structuralTeamSlotsByNumber = teamSlots.associateBy { teamSlot ->
            teamSlot.slotNumber
        }
        referencedParticipantSlots.forEach { slotNumber ->
            val teamSlot = structuralTeamSlotsByNumber[slotNumber]
            if (teamSlot == null) {
                failures += TournamentCsvExportFailure.MISSING_TEAM_SLOT
            } else if (teamSlot.teamName.isBlank()) {
                failures += TournamentCsvExportFailure.MISSING_TEAM_IDENTITY
            }
        }

        return failures
    }

    private fun TournamentCsvExportInput.validateTeamSlots(
        failures: MutableSet<TournamentCsvExportFailure>,
    ) {
        val slotNumbers = teamSlots.map { teamSlot -> teamSlot.slotNumber }

        if (teamSlots.size != TeamSlot.SLOT_NUMBERS.count()) {
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
        if (match.participantResults.isEmpty()) {
            val participantCount = match.placements.size
            if (participantCount !in 1..TeamSlot.MAX_SLOT_NUMBER ||
                match.kills.size != participantCount
            ) {
                failures += TournamentCsvExportFailure.INVALID_FINALIZED_MATCH_ROW_COUNT
            }
            val expectedPlacements = (1..participantCount).toSet()
            val placementSlots = match.placements.map { it.teamSlotNumber }
            val killSlots = match.kills.map { it.teamSlotNumber }
            val placementValues = match.placements.map { it.position }
            if (placementSlots.any { it !in TeamSlot.SLOT_NUMBERS } ||
                killSlots.any { it !in TeamSlot.SLOT_NUMBERS }
            ) failures += TournamentCsvExportFailure.INVALID_TEAM_SLOT
            if (placementSlots.duplicates().isNotEmpty() || killSlots.duplicates().isNotEmpty()) {
                failures += TournamentCsvExportFailure.DUPLICATE_TEAM_SLOT
            }
            if (placementValues.any { it !in expectedPlacements }) {
                failures += TournamentCsvExportFailure.INVALID_PLACEMENT
            }
            if (placementValues.duplicates().isNotEmpty()) {
                failures += TournamentCsvExportFailure.DUPLICATE_PLACEMENT
            }
            if (placementValues.toSet() != expectedPlacements) {
                failures += TournamentCsvExportFailure.MISSING_PLACEMENT
            }
            if (killSlots.toSet() != placementSlots.toSet()) {
                failures += TournamentCsvExportFailure.MISSING_KILL_VALUE
            }
            if (match.kills.any { it.kills < 0 }) {
                failures += TournamentCsvExportFailure.INVALID_KILL_COUNT
            }
            return
        }
        val participantResults = match.finalizedParticipantResultsOrNull()
        if (participantResults == null) {
            failures += TournamentCsvExportFailure.INVALID_FINALIZED_MATCH_ROW_COUNT
            return
        }
        val participated = participantResults.filter {
            it.participationStatus == MatchParticipationStatus.PARTICIPATED
        }
        val expectedPlacements = (1..participated.size).toSet()
        val participantSlots = participantResults.map { it.teamSlotNumber }
        val placementValues = participated.mapNotNull { it.placement }
        if (participantResults.isEmpty() || participated.isEmpty() ||
            participantSlots.size > TeamSlot.MAX_SLOT_NUMBER ||
            participantSlots.duplicates().isNotEmpty() ||
            participantSlots.any { it !in TeamSlot.SLOT_NUMBERS }
        ) {
            failures += TournamentCsvExportFailure.INVALID_TEAM_SLOT
        }
        if (placementValues.any { placement -> placement !in expectedPlacements }) {
            failures += TournamentCsvExportFailure.INVALID_PLACEMENT
        }

        if (placementValues.duplicates().isNotEmpty()) {
            failures += TournamentCsvExportFailure.DUPLICATE_PLACEMENT
        }

        if (placementValues.toSet() != expectedPlacements) {
            failures += TournamentCsvExportFailure.MISSING_PLACEMENT
        }

        if (participantResults.any {
                it.participationStatus == MatchParticipationStatus.NO_SHOW &&
                    (it.placement != null || it.kills != 0)
            }) {
            failures += TournamentCsvExportFailure.MISSING_KILL_VALUE
        }

        if (participantResults.any { result -> result.kills < 0 }) {
            failures += TournamentCsvExportFailure.INVALID_KILL_COUNT
        }
    }

    private fun List<TieBreakStanding>.hasCompleteParticipantCoverage(
        expectedParticipantSlots: Set<Int>,
    ): Boolean =
        size == expectedParticipantSlots.size &&
            map { tieBreakStanding ->
                tieBreakStanding.standing.teamSlotNumber
            }.toSet() == expectedParticipantSlots

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
        const val EXPORT_SCHEMA_VERSION = "phase_10_v2"
        const val EXPORT_TYPE = "tournament_standings"
        const val UNIQUE_ORDER = "unique_order"
        const val TIE_BREAK_APPLIED = "tie_break_applied"
        const val UNRESOLVED_TIE = "unresolved_tie"
        const val CRLF = "\r\n"

        val REQUIRED_SLOT_NUMBERS = TeamSlot.SLOT_NUMBERS.toSet()

        const val TOURNAMENT_CSV_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,exported_match_count,standings_rank,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,matches_played,total_position_points,total_kills,total_kill_points,total_points,best_placement,first_place_count,tie_break_status"
    }
}
