package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.MatchCsvExportFailure
import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.export.MatchResultExportModelBuildResult
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.export.TournamentCsvExportInput
import com.hoggamers.rankforge.domain.export.TournamentResultExportModelBuildResult
import com.hoggamers.rankforge.domain.export.TournamentCsvExportFailure
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDesignResultRowsResolverTest {
    private val resolver = CustomDesignResultRowsResolver()

    @Test
    fun currentMatchReturnsBuilderRowsWithoutReorderingOrRemapping() {
        val input = validMatchInput()
        val expected = ResultExportModelBuilder().buildMatch(input)
        val actual = resolver.resolve(ResultDownloadRequest.CurrentMatch(input))

        assertEquals(
            (expected as MatchResultExportModelBuildResult.Success).model.rows,
            (actual as CustomDesignResultRowsResult.Success).rows,
        )
        assertEquals(
            expected.model.rows.map { it.teamName },
            actual.rows.map { it.teamName },
        )
        assertEquals(expected.model.rows.first().win, actual.rows.first().win)
        assertEquals(expected.model.rows.first().totalKills, actual.rows.first().totalKills)
        assertEquals(expected.model.rows.first().positionPoints, actual.rows.first().positionPoints)
        assertEquals(expected.model.rows.first().totalPoints, actual.rows.first().totalPoints)
    }

    @Test
    fun wholeTournamentReturnsBuilderRowsWithExistingStandingsOrder() {
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
        val expected = ResultExportModelBuilder().buildTournament(input)
        val actual = resolver.resolve(ResultDownloadRequest.WholeTournament(input))

        assertEquals(
            (expected as TournamentResultExportModelBuildResult.Success).model.rows,
            (actual as CustomDesignResultRowsResult.Success).rows,
        )
        assertEquals(expected.model.rows.first().teamName, actual.rows.first().teamName)
        assertEquals(expected.model.rows.first().win, actual.rows.first().win)
        assertEquals(expected.model.rows.first().totalKills, actual.rows.first().totalKills)
        assertEquals(expected.model.rows.first().positionPoints, actual.rows.first().positionPoints)
        assertEquals(expected.model.rows.first().totalPoints, actual.rows.first().totalPoints)
    }

    @Test
    fun currentMatchFailureIsPropagatedUnchanged() {
        val input = validMatchInput(match = validMatch(status = MatchStatus.DRAFT))
        val expected = ResultExportModelBuilder().buildMatch(input)
        val actual = resolver.resolve(ResultDownloadRequest.CurrentMatch(input))

        assertEquals(
            setOf(MatchCsvExportFailure.MATCH_NOT_FINALIZED),
            (actual as CustomDesignResultRowsResult.MatchFailure).failures,
        )
        assertEquals(
            (expected as MatchResultExportModelBuildResult.Failure).failures,
            actual.failures,
        )
    }

    @Test
    fun tournamentFailureIsPropagatedUnchanged() {
        val input = validTournamentInput(matches = listOf(validMatch(status = MatchStatus.DRAFT)))
        val expected = ResultExportModelBuilder().buildTournament(input)
        val actual = resolver.resolve(ResultDownloadRequest.WholeTournament(input))

        assertEquals(
            setOf(TournamentCsvExportFailure.NO_FINALIZED_MATCHES),
            (actual as CustomDesignResultRowsResult.TournamentFailure).failures,
        )
        assertEquals(
            (expected as TournamentResultExportModelBuildResult.Failure).failures,
            actual.failures,
        )
    }

    private fun validMatchInput(
        match: Match = validMatch(),
        teamSlots: List<TeamSlot> = validTeamSlots(),
        tournament: Tournament = validTournament(),
    ) = MatchCsvExportInput(
        tournament = tournament,
        match = match,
        teamSlots = teamSlots,
        rosterPlayers = validRosterPlayers(),
    )

    private fun validTournamentInput(
        matches: List<Match> = listOf(validMatch()),
        teamSlots: List<TeamSlot> = validTeamSlots(),
    ) = TournamentCsvExportInput(
        tournament = validTournament(),
        matches = matches,
        teamSlots = teamSlots,
        rosterPlayers = validRosterPlayers(),
    )

    private fun validTournament() = Tournament(
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
    ) = Match(
        id = id,
        tournamentId = TOURNAMENT_ID,
        matchNumber = matchNumber,
        date = LocalDate.of(2026, 7, 31),
        mapName = "Bermuda",
        status = status,
        placements = placements,
        kills = kills,
    )

    private fun validPlacements() = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        MatchPlacement(teamSlotNumber = slotNumber, position = slotNumber)
    }

    private fun reversedPlacements() = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        MatchPlacement(teamSlotNumber = slotNumber, position = 13 - slotNumber)
    }

    private fun validKills() = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        MatchKill(teamSlotNumber = slotNumber, kills = slotNumber - 1)
    }

    private fun zeroKills() = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        MatchKill(teamSlotNumber = slotNumber, kills = 0)
    }

    private fun validTeamSlots() = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        TeamSlot(
            tournamentId = TOURNAMENT_ID,
            slotNumber = slotNumber,
            teamName = "Team $slotNumber",
        )
    }

    private fun validRosterPlayers() = TeamSlot.SLOT_NUMBERS.flatMap { slotNumber ->
        (1..4).map { playerNumber ->
            RosterPlayer(
                tournamentId = TOURNAMENT_ID,
                slotNumber = slotNumber,
                displayName = "Player $slotNumber.$playerNumber",
            )
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
    }
}
