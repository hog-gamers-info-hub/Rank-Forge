package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.PositionPointsEngine
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TournamentCsvExporterTest {
    private lateinit var exporter: TournamentCsvExporter

    @Before
    fun setUp() {
        exporter = TournamentCsvExporter()
    }

    @Test
    fun finalizedTournamentStandingsExportSuccessfully() {
        val result = exporter.export(validInput())

        val success = result as TournamentCsvExportResult.Success
        assertTrue(success.csv.startsWith(EXPECTED_HEADER))
        assertTrue(success.csv.contains("phase_10_v1,tournament_standings"))
    }

    @Test
    fun headerUsesExactApprovedColumnOrder() {
        val csv = exportSuccess(validInput())

        assertEquals(EXPECTED_HEADER, csv.linesByRecord().first())
    }

    @Test
    fun exportContainsExactlyTwelveStandingsRows() {
        val csv = exportSuccess(validInput())

        assertEquals(13, csv.linesByRecord().size)
        assertEquals(12, csv.dataRows().size)
        assertFalse(csv.endsWith("\r\n"))
    }

    @Test
    fun draftMatchesAreExcludedFromExport() {
        val input = validInput(
            matches = listOf(
                validMatch(
                    id = "finalized-match",
                    matchNumber = 1,
                ),
                validMatch(
                    id = "draft-match",
                    matchNumber = 2,
                    status = MatchStatus.DRAFT,
                    kills = validKills(killsPerTeam = 99),
                ),
            ),
        )

        val csv = exportSuccess(input)
        val row = csv.rowForTeamSlot(1)

        assertEquals("1", row[EXPORTED_MATCH_COUNT_COLUMN])
        assertEquals("1", row[MATCHES_PLAYED_COLUMN])
        assertEquals("0", row[TOTAL_KILLS_COLUMN])
    }

    @Test
    fun noFinalizedMatchesAreRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(status = MatchStatus.DRAFT),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.NO_FINALIZED_MATCHES,
        )
    }

    @Test
    fun mismatchedTournamentIdentityIsRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(tournamentId = "different-tournament"),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.TOURNAMENT_IDENTITY_MISMATCH,
        )
    }

    @Test
    fun duplicateMatchIdentityIsRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(
                        id = "duplicate-match",
                        matchNumber = 1,
                    ),
                    validMatch(
                        id = "duplicate-match",
                        matchNumber = 2,
                    ),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.DUPLICATE_MATCH_IDENTITY,
        )
    }

    @Test
    fun invalidFinalizedMatchRowCountIsRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(
                        placements = validPlacements().dropLast(1),
                    ),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.INVALID_FINALIZED_MATCH_ROW_COUNT,
        )
    }

    @Test
    fun duplicatePlacementIsRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(
                        placements = validPlacements()
                            .replacePlacement(
                                slotNumber = 2,
                                position = 1,
                            ),
                    ),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.DUPLICATE_PLACEMENT,
        )
    }

    @Test
    fun duplicateResultTeamSlotIsRejected() {
        val duplicateSlotPlacements =
            validPlacements().dropLast(1) +
                MatchPlacement(
                    teamSlotNumber = 1,
                    position = 12,
                )

        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(
                        placements = duplicateSlotPlacements,
                    ),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.DUPLICATE_TEAM_SLOT,
        )
    }

    @Test
    fun missingKillValueIsRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(
                        kills = validKills().dropLast(1),
                    ),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.MISSING_KILL_VALUE,
        )
    }

    @Test
    fun negativeKillValueIsRejected() {
        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(
                        kills = validKills()
                            .replaceKill(
                                slotNumber = 1,
                                kills = -1,
                            ),
                    ),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.INVALID_KILL_COUNT,
        )
    }

    @Test
    fun invalidMatchTeamSlotIsRejected() {
        val invalidPlacements = validPlacements().map { placement ->
            if (placement.teamSlotNumber == 1) {
                placement.copy(teamSlotNumber = 13)
            } else {
                placement
            }
        }

        val result = exporter.export(
            validInput(
                matches = listOf(
                    validMatch(placements = invalidPlacements),
                ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.INVALID_TEAM_SLOT,
        )
    }

    @Test
    fun missingTournamentTeamSlotIsRejected() {
        val result = exporter.export(
            validInput(
                teamSlots = validTeamSlots().dropLast(1),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.MISSING_TEAM_SLOT,
        )
    }

    @Test
    fun duplicateTournamentTeamSlotIsRejected() {
        val duplicateSlots =
            validTeamSlots().dropLast(1) +
                TeamSlot(
                    tournamentId = TOURNAMENT_ID,
                    slotNumber = 1,
                    teamName = "Duplicate Team",
                )

        val result = exporter.export(
            validInput(teamSlots = duplicateSlots),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.DUPLICATE_TEAM_SLOT,
        )
    }

    @Test
    fun blankTeamNameIsRejected() {
        val result = exporter.export(
            validInput(
                teamSlots = validTeamSlots()
                    .replaceTeamName(
                        slotNumber = 1,
                        teamName = " ",
                    ),
            ),
        )

        assertFailure(
            result,
            TournamentCsvExportFailure.MISSING_TEAM_IDENTITY,
        )
    }

    @Test
    fun standingsRowsFollowExistingTieBreakOrder() {
        val exportedSlots = exportSuccess(validInput())
            .dataRows()
            .map { row -> row[TEAM_SLOT_COLUMN] }

        assertEquals(
            TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                slotNumber.toString()
            },
            exportedSlots,
        )
    }

    @Test
    fun standingsRankStartsAtOneAndIncrements() {
        val exportedRanks = exportSuccess(validInput())
            .dataRows()
            .map { row -> row[STANDINGS_RANK_COLUMN] }

        assertEquals(
            TeamSlot.SLOT_NUMBERS.map { rank -> rank.toString() },
            exportedRanks,
        )
    }

    @Test
    fun cumulativeScoringColumnsUseFinalizedMatchData() {
        val input = validInput(
            matches = listOf(
                validMatch(
                    id = "match-1",
                    matchNumber = 1,
                    placements = validPlacements(),
                    kills = validKills(killsPerTeam = 1),
                ),
                validMatch(
                    id = "match-2",
                    matchNumber = 2,
                    placements = reversedPlacements(),
                    kills = validKills(killsPerTeam = 2),
                ),
            ),
        )

        val row = exportSuccess(input).rowForTeamSlot(1)
        val expectedPositionPoints =
            PositionPointsEngine()(1) +
                PositionPointsEngine()(12)
        val expectedKills = 3
        val expectedKillPoints =
            KillPointsEngine()(1) +
                KillPointsEngine()(2)

        assertEquals("2", row[EXPORTED_MATCH_COUNT_COLUMN])
        assertEquals("2", row[MATCHES_PLAYED_COLUMN])
        assertEquals(
            expectedPositionPoints.toString(),
            row[TOTAL_POSITION_POINTS_COLUMN],
        )
        assertEquals(
            expectedKills.toString(),
            row[TOTAL_KILLS_COLUMN],
        )
        assertEquals(
            expectedKillPoints.toString(),
            row[TOTAL_KILL_POINTS_COLUMN],
        )
        assertEquals(
            (expectedPositionPoints + expectedKillPoints).toString(),
            row[TOTAL_POINTS_COLUMN],
        )
    }

    @Test
    fun bestPlacementAndFirstPlaceCountAreExported() {
        val input = validInput(
            matches = listOf(
                validMatch(
                    id = "match-1",
                    matchNumber = 1,
                    placements = validPlacements(),
                ),
                validMatch(
                    id = "match-2",
                    matchNumber = 2,
                    placements = reversedPlacements(),
                ),
            ),
        )

        val teamOneRow = exportSuccess(input).rowForTeamSlot(1)
        val teamTwelveRow = exportSuccess(input).rowForTeamSlot(12)

        assertEquals("1", teamOneRow[BEST_PLACEMENT_COLUMN])
        assertEquals("1", teamOneRow[FIRST_PLACE_COUNT_COLUMN])
        assertEquals("1", teamTwelveRow[BEST_PLACEMENT_COLUMN])
        assertEquals("1", teamTwelveRow[FIRST_PLACE_COUNT_COLUMN])
    }

    @Test
    fun onlyFirstFourRosterPlayersAreExported() {
        val rosterPlayers = validRosterPlayers() +
            RosterPlayer(
                tournamentId = TOURNAMENT_ID,
                slotNumber = 1,
                displayName = "Player 1.5",
            )

        val row = exportSuccess(
            validInput(rosterPlayers = rosterPlayers),
        ).rowForTeamSlot(1)

        assertEquals("Player 1.1", row[PLAYER_1_COLUMN])
        assertEquals("Player 1.2", row[PLAYER_2_COLUMN])
        assertEquals("Player 1.3", row[PLAYER_3_COLUMN])
        assertEquals("Player 1.4", row[PLAYER_4_COLUMN])
        assertFalse(row.contains("Player 1.5"))
    }

    @Test
    fun fieldsContainingCommasAreQuoted() {
        val csv = exportSuccess(
            validInput(
                tournament = validTournament(name = "Summer, Cup"),
                teamSlots = validTeamSlots()
                    .replaceTeamName(
                        slotNumber = 1,
                        teamName = "Team, One",
                    ),
            ),
        )

        assertTrue(csv.contains("\"Summer, Cup\""))
        assertTrue(csv.contains("\"Team, One\""))
    }

    @Test
    fun embeddedQuotesAreDoubled() {
        val csv = exportSuccess(
            validInput(
                teamSlots = validTeamSlots()
                    .replaceTeamName(
                        slotNumber = 1,
                        teamName = "Team \"One\"",
                    ),
            ),
        )

        assertTrue(csv.contains("\"Team \"\"One\"\"\""))
    }

    @Test
    fun fieldsContainingNewlinesAreQuoted() {
        val csv = exportSuccess(
            validInput(
                teamSlots = validTeamSlots()
                    .replaceTeamName(
                        slotNumber = 1,
                        teamName = "Team\r\nOne",
                    ),
            ),
        )

        assertTrue(csv.contains("\"Team\r\nOne\""))
    }

    @Test
    fun fieldsWithLeadingAndTrailingWhitespaceAreQuoted() {
        val csv = exportSuccess(
            validInput(
                teamSlots = validTeamSlots()
                    .replaceTeamName(
                        slotNumber = 1,
                        teamName = " Team One ",
                    ),
            ),
        )

        assertTrue(csv.contains("\" Team One \""))
    }

    @Test
    fun unicodeAndSpecialCharactersArePreserved() {
        val csv = exportSuccess(
            validInput(
                tournament = validTournament(
                    name = "Copa São Paulo 🔥",
                ),
                teamSlots = validTeamSlots()
                    .replaceTeamName(
                        slotNumber = 1,
                        teamName = "HØG Élite",
                    ),
                rosterPlayers = validRosterPlayers()
                    .replacePlayerName(
                        slotNumber = 1,
                        playerIndex = 0,
                        displayName = "Æon",
                    ),
            ),
        )

        assertTrue(csv.contains("Copa São Paulo 🔥"))
        assertTrue(csv.contains("HØG Élite"))
        assertTrue(csv.contains("Æon"))
    }

    @Test
    fun tieBreakStatusUsesExistingStandingsEvidence() {
        val csv = exportSuccess(validInput())
        val teamOneRow = csv.rowForTeamSlot(1)
        val teamElevenRow = csv.rowForTeamSlot(11)
        val teamTwelveRow = csv.rowForTeamSlot(12)

        assertEquals(
            "unique_order",
            teamOneRow[TIE_BREAK_STATUS_COLUMN],
        )
        assertEquals(
            "tie_break_applied",
            teamElevenRow[TIE_BREAK_STATUS_COLUMN],
        )
        assertEquals(
            "tie_break_applied",
            teamTwelveRow[TIE_BREAK_STATUS_COLUMN],
        )
    }

    @Test
    fun rawOcrAndPrivateEvidenceColumnsAreNotExported() {
        val csv = exportSuccess(validInput())

        listOf(
            "raw_ocr_text",
            "ocr_confidence",
            "screenshot_path",
            "screenshot_storage_path",
            "preserved_ocr_evidence",
            "private_correction_evidence",
            "owner_id",
            "sync_revision",
        ).forEach { excludedColumn ->
            assertFalse(csv.contains(excludedColumn))
        }
    }

    private fun exportSuccess(
        input: TournamentCsvExportInput,
    ): String {
        val result = exporter.export(input)
        return (result as TournamentCsvExportResult.Success).csv
    }

    private fun assertFailure(
        result: TournamentCsvExportResult,
        failure: TournamentCsvExportFailure,
    ) {
        val failed = result as TournamentCsvExportResult.Failure
        assertTrue(failure in failed.failures)
    }

    private fun validInput(
        tournament: Tournament = validTournament(),
        matches: List<Match> = listOf(validMatch()),
        teamSlots: List<TeamSlot> = validTeamSlots(),
        rosterPlayers: List<RosterPlayer> = validRosterPlayers(),
    ): TournamentCsvExportInput =
        TournamentCsvExportInput(
            tournament = tournament,
            matches = matches,
            teamSlots = teamSlots,
            rosterPlayers = rosterPlayers,
        )

    private fun validTournament(
        name: String = "Synthetic Cup",
    ): Tournament =
        Tournament(
            id = TOURNAMENT_ID,
            name = name,
            date = LocalDate.of(2026, 7, 31),
            organizerName = "Organizer",
            organizerContactNumber = "1234567890",
            status = TournamentStatus.CONFIRMED,
        )

    private fun validMatch(
        id: String = "match-id",
        tournamentId: String = TOURNAMENT_ID,
        matchNumber: Int = 1,
        status: MatchStatus = MatchStatus.FINALIZED,
        placements: List<MatchPlacement> = validPlacements(),
        kills: List<MatchKill> = validKills(),
    ): Match =
        Match(
            id = id,
            tournamentId = tournamentId,
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

    private fun validKills(
        killsPerTeam: Int = 0,
    ): List<MatchKill> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchKill(
                teamSlotNumber = slotNumber,
                kills = killsPerTeam,
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

    private fun List<MatchPlacement>.replacePlacement(
        slotNumber: Int,
        position: Int,
    ): List<MatchPlacement> =
        map { placement ->
            if (placement.teamSlotNumber == slotNumber) {
                placement.copy(position = position)
            } else {
                placement
            }
        }

    private fun List<MatchKill>.replaceKill(
        slotNumber: Int,
        kills: Int,
    ): List<MatchKill> =
        map { kill ->
            if (kill.teamSlotNumber == slotNumber) {
                kill.copy(kills = kills)
            } else {
                kill
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

    private fun List<RosterPlayer>.replacePlayerName(
        slotNumber: Int,
        playerIndex: Int,
        displayName: String,
    ): List<RosterPlayer> {
        var currentIndex = 0

        return map { player ->
            if (
                player.slotNumber == slotNumber &&
                currentIndex++ == playerIndex
            ) {
                player.copy(displayName = displayName)
            } else {
                player
            }
        }
    }

    private fun String.linesByRecord(): List<String> =
        split("\r\n")

    private fun String.dataRows(): List<List<String>> =
        linesByRecord()
            .drop(1)
            .map { record -> record.split(",") }

    private fun String.rowForTeamSlot(
        slotNumber: Int,
    ): List<String> =
        dataRows().single { row ->
            row[TEAM_SLOT_COLUMN] == slotNumber.toString()
        }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"

        const val EXPORTED_MATCH_COUNT_COLUMN = 4
        const val STANDINGS_RANK_COLUMN = 5
        const val TEAM_SLOT_COLUMN = 6
        const val PLAYER_1_COLUMN = 8
        const val PLAYER_2_COLUMN = 9
        const val PLAYER_3_COLUMN = 10
        const val PLAYER_4_COLUMN = 11
        const val MATCHES_PLAYED_COLUMN = 12
        const val TOTAL_POSITION_POINTS_COLUMN = 13
        const val TOTAL_KILLS_COLUMN = 14
        const val TOTAL_KILL_POINTS_COLUMN = 15
        const val TOTAL_POINTS_COLUMN = 16
        const val BEST_PLACEMENT_COLUMN = 17
        const val FIRST_PLACE_COUNT_COLUMN = 18
        const val TIE_BREAK_STATUS_COLUMN = 19

        const val EXPECTED_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,exported_match_count,standings_rank,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,matches_played,total_position_points,total_kills,total_kill_points,total_points,best_placement,first_place_count,tie_break_status"
    }
}
