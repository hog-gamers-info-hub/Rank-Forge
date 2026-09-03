package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResultExportModelBuilderTest {
    private lateinit var builder: ResultExportModelBuilder

    @Before
    fun setUp() {
        builder = ResultExportModelBuilder()
    }

    @Test
    fun finalizedMatchBuildsWithApprovedMetadata() {
        val result = builder.buildMatch(
            validMatchInput(
                tournament = validTournament().copy(date = LocalDate.of(2026, 9, 3)),
                match = validMatch().copy(date = LocalDate.of(2026, 8, 31)),
            ),
        )

        val model = matchSuccess(result)
        assertEquals("Synthetic Cup", model.tournamentName)
        assertEquals("Organizer", model.organizerName)
        assertEquals(LocalDate.of(2026, 9, 3), model.tournamentDate)
        assertEquals(3, model.matchNumber)
        assertEquals(LocalDate.of(2026, 8, 31), model.matchDate)
        assertEquals("Bermuda", model.mapName)
    }

    @Test
    fun finalizedMatchBuildsExactlyTwelveRows() {
        val model = matchSuccess(builder.buildMatch(validMatchInput()))

        assertEquals(12, model.rows.size)
    }

    @Test
    fun matchRowsMapAuthoritativeValuesAndPreserveRankOrder() {
        val input = validMatchInput(
            teamSlots = validTeamSlots().replaceTeamName(1, "Exact Team Name"),
        )
        val sourceRows = matchSourceRows(input)
        val resultRows = matchSuccess(builder.buildMatch(input)).rows

        assertEquals((1..12).toList(), resultRows.map { row -> row.rank })
        assertEquals("Exact Team Name", resultRows.first().teamName)
        sourceRows.zip(resultRows).forEach { (source, result) ->
            assertEquals(source.teamName, result.teamName)
            assertEquals(source.kills, result.totalKills)
            assertEquals(source.placementPoints, result.positionPoints)
            assertEquals(source.totalPoints, result.totalPoints)
        }
    }

    @Test
    fun currentMatchRankUsesSequentialTotalPointOrderAndLeavesNoShowBlank() {
        val participantResults = listOf(
            MatchParticipantResult(10, MatchParticipationStatus.PARTICIPATED, 4, 80),
            MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 3, 32),
            MatchParticipantResult(7, MatchParticipationStatus.PARTICIPATED, 2, 49),
            MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 1, 55),
            MatchParticipantResult(12, MatchParticipationStatus.NO_SHOW, null, 0),
        )
        val model = matchSuccess(
            builder.buildMatch(
                validMatchInput(
                    match = validMatch().copy(participantResults = participantResults),
                ),
            ),
        )

        assertEquals(listOf("Team 10", "Team 2", "Team 7", "Team 1", "Team 12"), model.rows.map { it.teamName })
        assertEquals(listOf(1, 2, 3, 4, null), model.rows.map { it.rank })
        assertEquals(0, model.rows.last().win)
        assertEquals(0, model.rows.last().totalKills)
        assertEquals(0, model.rows.last().positionPoints)
        assertEquals(0, model.rows.last().totalPoints)
    }

    @Test
    fun matchWinIsOneOnlyForRankOne() {
        val rows = matchSuccess(builder.buildMatch(validMatchInput())).rows

        assertEquals(1, rows.first().win)
        assertEquals(List(11) { 0 }, rows.drop(1).map { row -> row.win })
    }

    @Test
    fun draftMatchReturnsExistingSourceFailure() {
        val input = validMatchInput(match = validMatch(status = MatchStatus.DRAFT))

        val result = builder.buildMatch(input)

        assertEquals(
            setOf(MatchCsvExportFailure.MATCH_NOT_FINALIZED),
            matchFailure(result).failures,
        )
    }

    @Test
    fun invalidFinalizedMatchPropagatesExistingSourceFailure() {
        val input = validMatchInput(
            match = validMatch(kills = validKills().dropLast(1)),
        )
        val sourceFailure = matchSourceFailure(input)

        val result = builder.buildMatch(input)

        assertEquals(sourceFailure.failures, matchFailure(result).failures)
    }

    @Test
    fun tournamentStandingsBuildWithExactlyTwelveRowsAndMatchCount() {
        val input = validTournamentInput(
            matches = listOf(validMatch(matchNumber = 1), validMatch(id = "match-2", matchNumber = 2)),
        )

        val model = tournamentSuccess(builder.buildTournament(input))

        assertEquals("Synthetic Cup", model.tournamentName)
        assertEquals("Organizer", model.organizerName)
        assertEquals(LocalDate.of(2026, 7, 31), model.tournamentDate)
        assertEquals(2, model.finalizedMatchCount)
        assertEquals(12, model.rows.size)
    }

    @Test
    fun tournamentRowsPreserveAuthoritativeStandingsValuesAndOrder() {
        val input = validTournamentInput(
            teamSlots = validTeamSlots().replaceTeamName(1, "Exact Team Name"),
        )
        val sourceRows = tournamentSourceRows(input)
        val resultRows = tournamentSuccess(builder.buildTournament(input)).rows

        assertEquals(
            sourceRows.map { row -> row.standingsRank },
            resultRows.map { row -> row.rank },
        )
        assertEquals(
            sourceRows.map { row -> row.teamName },
            resultRows.map { row -> row.teamName },
        )
        assertEquals("Exact Team Name", resultRows.first().teamName)
        sourceRows.zip(resultRows).forEach { (source, result) ->
            assertEquals(source.firstPlaceCount, result.win)
            assertEquals(source.totalKills, result.totalKills)
            assertEquals(source.totalPositionPoints, result.positionPoints)
            assertEquals(source.totalPoints, result.totalPoints)
        }
    }

    @Test
    fun tiedStandingsPreserveExistingTieBreakOrder() {
        val input = validTournamentInput(
            matches = listOf(
                validMatch(matchNumber = 1, kills = zeroKills()),
                validMatch(
                    id = "match-2",
                    matchNumber = 2,
                    placements = reversedPlacements(),
                    kills = zeroKills(),
                ),
            ),
        )
        val sourceRows = tournamentSourceRows(input)
        val resultRows = tournamentSuccess(builder.buildTournament(input)).rows

        assertEquals(
            sourceRows.map { row -> row.teamName },
            resultRows.map { row -> row.teamName },
        )
        assertEquals("Team 12", resultRows.first().teamName)
    }

    @Test
    fun noFinalizedMatchesReturnsExistingSourceFailure() {
        val input = validTournamentInput(
            matches = listOf(validMatch(status = MatchStatus.DRAFT)),
        )

        val result = builder.buildTournament(input)

        assertEquals(
            setOf(TournamentCsvExportFailure.NO_FINALIZED_MATCHES),
            tournamentFailure(result).failures,
        )
    }

    @Test
    fun invalidTournamentStandingsPropagateExistingSourceFailure() {
        val input = validTournamentInput(
            matches = listOf(
                validMatch(placements = validPlacements().dropLast(1)),
            ),
        )
        val sourceFailure = tournamentSourceFailure(input)

        val result = builder.buildTournament(input)

        assertEquals(sourceFailure.failures, tournamentFailure(result).failures)
    }

    private fun matchSuccess(
        result: MatchResultExportModelBuildResult,
    ): MatchResultExportModel =
        (result as MatchResultExportModelBuildResult.Success).model

    private fun matchFailure(
        result: MatchResultExportModelBuildResult,
    ): MatchResultExportModelBuildResult.Failure =
        (result as MatchResultExportModelBuildResult.Failure)

    private fun tournamentSuccess(
        result: TournamentResultExportModelBuildResult,
    ): TournamentResultExportModel =
        (result as TournamentResultExportModelBuildResult.Success).model

    private fun tournamentFailure(
        result: TournamentResultExportModelBuildResult,
    ): TournamentResultExportModelBuildResult.Failure =
        (result as TournamentResultExportModelBuildResult.Failure)

    private fun matchSourceRows(
        input: MatchCsvExportInput,
    ): List<MatchExportRow> =
        (MatchCsvExporter().buildMatchRows(input) as MatchExportRowsResult.Success).rows

    private fun matchSourceFailure(
        input: MatchCsvExportInput,
    ): MatchExportRowsResult.Failure =
        (MatchCsvExporter().buildMatchRows(input) as MatchExportRowsResult.Failure)

    private fun tournamentSourceRows(
        input: TournamentCsvExportInput,
    ): List<TournamentStandingsExportRow> =
        (TournamentCsvExporter().buildStandingsRows(input) as TournamentStandingsExportRowsResult.Success).rows

    private fun tournamentSourceFailure(
        input: TournamentCsvExportInput,
    ): TournamentStandingsExportRowsResult.Failure =
        (TournamentCsvExporter().buildStandingsRows(input) as TournamentStandingsExportRowsResult.Failure)

    private fun validMatchInput(
        match: Match = validMatch(),
        teamSlots: List<TeamSlot> = validTeamSlots(),
        tournament: Tournament = validTournament(),
    ): MatchCsvExportInput =
        MatchCsvExportInput(
            tournament = tournament,
            match = match,
            teamSlots = teamSlots,
            rosterPlayers = validRosterPlayers(),
        )

    private fun validTournamentInput(
        matches: List<Match> = listOf(validMatch()),
        teamSlots: List<TeamSlot> = validTeamSlots(),
    ): TournamentCsvExportInput =
        TournamentCsvExportInput(
            tournament = validTournament(),
            matches = matches,
            teamSlots = teamSlots,
            rosterPlayers = validRosterPlayers(),
        )

    private fun validTournament(): Tournament =
        Tournament(
            id = TOURNAMENT_ID,
            name = "Synthetic Cup",
            date = LocalDate.of(2026, 7, 31),
            organizerName = "Organizer",
            organizerContactNumber = "1234567890",
            status = TournamentStatus.CONFIRMED,
        )

    private fun validMatch(
        id: String = "match-id",
        matchNumber: Int = 3,
        status: MatchStatus = MatchStatus.FINALIZED,
        placements: List<MatchPlacement> = validPlacements(),
        kills: List<MatchKill> = validKills(),
    ): Match =
        Match(
            id = id,
            tournamentId = TOURNAMENT_ID,
            matchNumber = matchNumber,
            date = LocalDate.of(2026, 7, 31),
            mapName = "Bermuda",
            status = status,
            placements = placements,
            kills = kills,
        )

    private fun validPlacements(): List<MatchPlacement> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchPlacement(
                teamSlotNumber = slotNumber,
                position = slotNumber,
            )
        }

    private fun reversedPlacements(): List<MatchPlacement> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchPlacement(
                teamSlotNumber = slotNumber,
                position = 13 - slotNumber,
            )
        }

    private fun validKills(): List<MatchKill> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchKill(
                teamSlotNumber = slotNumber,
                kills = slotNumber - 1,
            )
        }

    private fun zeroKills(): List<MatchKill> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchKill(
                teamSlotNumber = slotNumber,
                kills = 0,
            )
        }

    private fun validTeamSlots(): List<TeamSlot> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            TeamSlot(
                tournamentId = TOURNAMENT_ID,
                slotNumber = slotNumber,
                teamName = "Team $slotNumber",
            )
        }

    private fun validRosterPlayers(): List<RosterPlayer> =
        TeamSlot.SLOT_NUMBERS.flatMap { slotNumber ->
            (1..4).map { playerNumber ->
                RosterPlayer(
                    tournamentId = TOURNAMENT_ID,
                    slotNumber = slotNumber,
                    displayName = "Player $slotNumber.$playerNumber",
                )
            }
        }

    private fun List<TeamSlot>.replaceTeamName(
        slotNumber: Int,
        teamName: String,
    ): List<TeamSlot> =
        map { teamSlot ->
            if (teamSlot.slotNumber == slotNumber) {
                teamSlot.copy(teamName = teamName)
            } else {
                teamSlot
            }
        }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
    }
}
