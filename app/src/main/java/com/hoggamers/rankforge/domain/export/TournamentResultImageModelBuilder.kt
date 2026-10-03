package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStanding
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MAX_TEAMS_PER_GROUP
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull

enum class TournamentResultImageModelFailure {
    NO_FINALIZED_MATCHES,
    TOURNAMENT_IDENTITY_MISMATCH,
    DUPLICATE_MATCH_IDENTITY,
    INVALID_TOURNAMENT_CONFIGURATION,
    INVALID_TEAM_SLOT,
    DUPLICATE_TEAM_SLOT,
    MISSING_TEAM_SLOT,
    MISSING_TEAM_IDENTITY,
    INVALID_FINALIZED_MATCH,
    STANDINGS_GENERATION_FAILURE,
}

sealed interface TournamentResultImageModelBuildResult {
    data class Success(
        val model: TournamentResultExportModel,
    ) : TournamentResultImageModelBuildResult

    data class Failure(
        val failures: Set<TournamentResultImageModelFailure>,
    ) : TournamentResultImageModelBuildResult
}

/** Builds the existing PointIQ image model without widening the CSV/PDF export contract. */
class TournamentResultImageModelBuilder(
    private val standingsEngine: CumulativeTournamentStandingsEngine =
        CumulativeTournamentStandingsEngine(),
    private val tieBreakRules: TieBreakRules = TieBreakRules(),
) {
    fun build(input: TournamentCsvExportInput): TournamentResultImageModelBuildResult {
        val failures = linkedSetOf<TournamentResultImageModelFailure>()
        val expectedSlots = expectedSlotsOrNull(input.tournament, failures)
        validateTournamentIdentity(input, failures)
        validateTeamSlots(input, expectedSlots, failures)

        val finalizedMatches = input.matches.filter { match ->
            match.status == MatchStatus.FINALIZED
        }
        if (finalizedMatches.isEmpty()) {
            failures += TournamentResultImageModelFailure.NO_FINALIZED_MATCHES
        }
        if (input.matches.map { match -> match.id }.duplicates().isNotEmpty()) {
            failures += TournamentResultImageModelFailure.DUPLICATE_MATCH_IDENTITY
        }
        finalizedMatches.forEach { match ->
            validateFinalizedMatch(
                tournament = input.tournament,
                match = match,
                expectedSlots = expectedSlots,
                failures = failures,
            )
        }

        if (failures.isNotEmpty() || expectedSlots == null) {
            return TournamentResultImageModelBuildResult.Failure(failures)
        }

        val standings = runCatching {
            tieBreakRules(
                standingsEngine(
                    matches = finalizedMatches,
                    expectedTeamSlots = input.teamSlots,
                ),
            )
        }.getOrElse {
            return TournamentResultImageModelBuildResult.Failure(
                setOf(TournamentResultImageModelFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        if (standings.map { it.standing.teamSlotNumber }.toSet() != expectedSlots.map { it.slotNumber }.toSet()) {
            return TournamentResultImageModelBuildResult.Failure(
                setOf(TournamentResultImageModelFailure.STANDINGS_GENERATION_FAILURE),
            )
        }

        val teamNamesBySlot = input.teamSlots.associate { slot ->
            slot.slotNumber to slot.teamName.trim()
        }
        return TournamentResultImageModelBuildResult.Success(
            TournamentResultExportModel(
                tournamentName = input.tournament.name,
                stageName = input.tournament.stageName,
                finalizedMatchCount = finalizedMatches.size,
                rows = standings.mapIndexed { index, tieBreakStanding ->
                    tieBreakStanding.toResultExportRow(
                        rank = index + 1,
                        teamName = teamNamesBySlot.getValue(tieBreakStanding.standing.teamSlotNumber),
                    )
                },
            ),
        )
    }

    private fun expectedSlotsOrNull(
        tournament: Tournament,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ): List<TeamSlot>? {
        val expected = runCatching { tournament.expectedPermanentSlots() }.getOrElse {
            failures += TournamentResultImageModelFailure.INVALID_TOURNAMENT_CONFIGURATION
            return null
        }
        return expected
    }

    private fun validateTournamentIdentity(
        input: TournamentCsvExportInput,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        if (
            input.matches.any { match -> match.tournamentId != input.tournament.id } ||
            input.teamSlots.any { slot -> slot.tournamentId != input.tournament.id } ||
            input.rosterPlayers.any { player -> player.tournamentId != input.tournament.id }
        ) {
            failures += TournamentResultImageModelFailure.TOURNAMENT_IDENTITY_MISMATCH
        }
    }

    private fun validateTeamSlots(
        input: TournamentCsvExportInput,
        expectedSlots: List<TeamSlot>?,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        if (expectedSlots == null) return
        val actualSlotNumbers = input.teamSlots.map { slot -> slot.slotNumber }
        if (actualSlotNumbers.any { slotNumber -> slotNumber !in TeamSlot.TOURNAMENT_SLOT_NUMBERS }) {
            failures += TournamentResultImageModelFailure.INVALID_TEAM_SLOT
        }
        if (actualSlotNumbers.duplicates().isNotEmpty()) {
            failures += TournamentResultImageModelFailure.DUPLICATE_TEAM_SLOT
        }
        val expectedByNumber = expectedSlots.associateBy { slot -> slot.slotNumber }
        if (actualSlotNumbers.toSet() != expectedByNumber.keys) {
            failures += TournamentResultImageModelFailure.MISSING_TEAM_SLOT
        }
        input.teamSlots.forEach { slot ->
            if (slot.teamName.trim().isBlank()) {
                failures += TournamentResultImageModelFailure.MISSING_TEAM_IDENTITY
            }
            if (slot.group != expectedByNumber[slot.slotNumber]?.group) {
                failures += TournamentResultImageModelFailure.INVALID_TOURNAMENT_CONFIGURATION
            }
        }
    }

    private fun validateFinalizedMatch(
        tournament: Tournament,
        match: Match,
        expectedSlots: List<TeamSlot>?,
        failures: MutableSet<TournamentResultImageModelFailure>,
    ) {
        val participantResults = match.finalizedParticipantResultsOrNull()
        if (participantResults == null || expectedSlots == null) {
            failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
            return
        }
        val expectedSlotNumbers = expectedSlots.map { slot -> slot.slotNumber }.toSet()
        if (
            participantResults.size !in 1..TeamSlot.MAX_SLOT_NUMBER ||
            participantResults.any { result -> result.teamSlotNumber !in expectedSlotNumbers }
        ) {
            failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
        }

        when (tournament.format) {
            TournamentFormat.STANDARD -> {
                if (match.groupPairing != null) {
                    failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
                }
            }
            TournamentFormat.GROUP_ROTATION -> {
                val pairing = match.groupPairing
                val validPairing = pairing != null && pairing in tournament.selectedGroupPairings
                if (!validPairing) {
                    failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
                    return
                }
                val selectedPairing = requireNotNull(pairing)
                val eligibleSlots = expectedSlots.filter { slot ->
                    slot.group == selectedPairing.firstGroup || slot.group == selectedPairing.secondGroup
                }.map { slot -> slot.slotNumber }.toSet()
                if (
                    eligibleSlots.size != MAX_TEAMS_PER_GROUP * 2 ||
                    participantResults.any { result -> result.teamSlotNumber !in eligibleSlots }
                ) {
                    failures += TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH
                }
            }
        }
    }

    private fun Tournament.expectedPermanentSlots(): List<TeamSlot> = when (format) {
        TournamentFormat.STANDARD -> TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            TeamSlot.create(id, slotNumber)
        }
        TournamentFormat.GROUP_ROTATION -> {
            val count = groupCount ?: error("Group Rotation requires a group count.")
            require(count == 3 || count == 4)
            formatDerivedSlots()
        }
    }

    private fun com.hoggamers.rankforge.domain.tournament.TieBreakStanding.toResultExportRow(
        rank: Int,
        teamName: String,
    ): ResultExportRow {
        val standing: CumulativeTournamentStanding = standing
        return ResultExportRow(
            rank = rank,
            teamName = teamName,
            win = standing.firstPlaceFinishes,
            totalKills = standing.totalKillPoints,
            positionPoints = standing.totalPositionPoints,
            totalPoints = standing.totalPoints,
            matchesPlayed = standing.matchesPlayed,
        )
    }

    private fun <T> List<T>.duplicates(): Set<T> =
        groupingBy { value -> value }.eachCount().filterValues { count -> count > 1 }.keys
}
