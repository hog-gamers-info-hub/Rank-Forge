package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchTotalEngine
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

class MatchCsvExporterTest {
    private lateinit var exporter: MatchCsvExporter

    @Before
    fun setUp() {
        exporter = MatchCsvExporter()
    }

    @Test
    fun finalizedMatchExportsCsvSuccessfully() {
        val result = exporter.export(validInput())

        val success = result as MatchCsvExportResult.Success
        assertTrue(success.csv.startsWith(EXPECTED_HEADER))
        assertTrue(success.csv.contains("phase_10_v1,match_result"))
    }

    @Test
    fun headerUsesExactApprovedColumnOrder() {
        val csv = exportSuccess(validInput())

        assertEquals(EXPECTED_HEADER, csv.linesByRecord().first())
    }

    @Test
    fun finalizedMatchExportContainsExactlyTwelveDataRows() {
        val csv = exportSuccess(validInput())

        assertEquals(13, csv.linesByRecord().size)
        assertEquals(12, csv.linesByRecord().drop(1).size)
    }

    @Test
    fun exportedRowsAreOrderedByPlacementAscending() {
        val input = validInput(
            match = validMatch(
                placements = TeamSlot.SLOT_NUMBERS.reversed().map { slotNumber ->
                    MatchPlacement(
                        teamSlotNumber = slotNumber,
                        position = slotNumber,
                    )
                },
            ),
        )

        val placementColumnValues = exportSuccess(input).dataRows().map { row -> row[PLACEMENT_COLUMN] }

        assertEquals(TeamSlot.SLOT_NUMBERS.map { it.toString() }, placementColumnValues)
    }

    @Test
    fun rowNumbersFollowExportedPlacementOrder() {
        val rowNumberColumnValues = exportSuccess(validInput()).dataRows().map { row -> row[ROW_NUMBER_COLUMN] }

        assertEquals(TeamSlot.SLOT_NUMBERS.map { it.toString() }, rowNumberColumnValues)
    }

    @Test
    fun draftMatchIsRejected() {
        val result = exporter.export(validInput(match = validMatch(status = MatchStatus.DRAFT)))

        assertFailure(result, MatchCsvExportFailure.MATCH_NOT_FINALIZED)
    }

    @Test
    fun missingPlacementIsRejected() {
        val result = exporter.export(
            validInput(
                match = validMatch(placements = validPlacements().dropLast(1)),
            ),
        )

        assertFailure(result, MatchCsvExportFailure.MISSING_PLACEMENT)
    }

    @Test
    fun duplicatePlacementIsRejected() {
        val result = exporter.export(
            validInput(
                match = validMatch(
                    placements = validPlacements().replacePlacement(slotNumber = 2, position = 1),
                ),
            ),
        )

        assertFailure(result, MatchCsvExportFailure.DUPLICATE_PLACEMENT)
    }

    @Test
    fun duplicateTeamSlotIsRejected() {
        val result = exporter.export(
            validInput(
                match = validMatch(
                    placements = validPlacements().dropLast(1) + MatchPlacement(teamSlotNumber = 1, position = 12),
                ),
            ),
        )

        assertFailure(result, MatchCsvExportFailure.DUPLICATE_TEAM_SLOT)
    }

    @Test
    fun missingKillValueIsRejected() {
        val result = exporter.export(validInput(match = validMatch(kills = validKills().dropLast(1))))

        assertFailure(result, MatchCsvExportFailure.MISSING_KILL_VALUE)
    }

    @Test
    fun negativeKillCountIsRejected() {
        val result = exporter.export(
            validInput(
                match = validMatch(kills = validKills().replaceKill(slotNumber = 1, kills = -1)),
            ),
        )

        assertFailure(result, MatchCsvExportFailure.INVALID_KILL_COUNT)
    }

    @Test
    fun blankTeamNameIsRejected() {
        val result = exporter.export(
            validInput(
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = " "),
            ),
        )

        assertFailure(result, MatchCsvExportFailure.MISSING_TEAM_IDENTITY)
    }

    @Test
    fun commaContainingFieldsAreQuoted() {
        val csv = exportSuccess(
            validInput(
                tournament = validTournament(name = "Summer, Cup"),
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = "Team, One"),
                rosterPlayers = validRosterPlayers().replacePlayer(slotNumber = 1, playerIndex = 0, name = "Alpha, One"),
            ),
        )

        assertTrue(csv.contains("\"Summer, Cup\""))
        assertTrue(csv.contains("\"Team, One\""))
        assertTrue(csv.contains("\"Alpha, One\""))
    }

    @Test
    fun embeddedQuotesAreDoubled() {
        val csv = exportSuccess(
            validInput(
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = "Team \"One\""),
            ),
        )

        assertTrue(csv.contains("\"Team \"\"One\"\"\""))
    }

    @Test
    fun carriageReturnAndLineFeedFieldsAreQuoted() {
        val csv = exportSuccess(
            validInput(
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = "Team\r\nOne"),
            ),
        )

        assertTrue(csv.contains("\"Team\r\nOne\""))
    }

    @Test
    fun leadingAndTrailingWhitespaceFieldsAreQuoted() {
        val csv = exportSuccess(
            validInput(
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = " Team One "),
            ),
        )

        assertTrue(csv.contains("\" Team One \""))
    }

    @Test
    fun unicodeAndSpecialCharactersArePreserved() {
        val csv = exportSuccess(
            validInput(
                tournament = validTournament(name = "Copa São Paulo 🔥"),
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = "HØG Élite"),
                rosterPlayers = validRosterPlayers().replacePlayer(slotNumber = 1, playerIndex = 0, name = "Æon"),
            ),
        )

        assertTrue(csv.contains("Copa São Paulo 🔥"))
        assertTrue(csv.contains("HØG Élite"))
        assertTrue(csv.contains("Æon"))
    }

    @Test
    fun escapedUnicodeCharactersArePreserved() {
        val csv = exportSuccess(
            validInput(
                tournament = validTournament(name = "Copa S\u00E3o Paulo \uD83D\uDD25"),
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = "H\u00D8G \u00C9lite"),
                rosterPlayers = validRosterPlayers().replacePlayer(slotNumber = 1, playerIndex = 0, name = "\u00C6on"),
            ),
        )

        assertTrue(csv.contains("Copa S\u00E3o Paulo \uD83D\uDD25"))
        assertTrue(csv.contains("H\u00D8G \u00C9lite"))
        assertTrue(csv.contains("\u00C6on"))
    }

    @Test
    fun scoringColumnsComeFromExistingScoringEngines() {
        val firstDataRow = exportSuccess(validInput()).dataRows().first()
        val placement = firstDataRow[PLACEMENT_COLUMN].toInt()
        val kills = firstDataRow[KILLS_COLUMN].toInt()

        assertEquals(PositionPointsEngine()(placement).toString(), firstDataRow[PLACEMENT_POINTS_COLUMN])
        assertEquals(KillPointsEngine()(kills).toString(), firstDataRow[KILL_POINTS_COLUMN])
        assertEquals(MatchTotalEngine()(placement, kills).toString(), firstDataRow[TOTAL_POINTS_COLUMN])
    }

    @Test
    fun originalFinalizedStatusIsExportedWhenCorrectionHistoryIsEmpty() {
        val correctionStatusColumnValues = exportSuccess(validInput()).dataRows()
            .map { row -> row[CORRECTION_STATUS_COLUMN] }
            .toSet()

        assertEquals(setOf("original_finalized"), correctionStatusColumnValues)
    }

    @Test
    fun correctedFinalizedStatusIsExportedWhenCorrectionHistoryExists() {
        val correctionStatusColumnValues = exportSuccess(
            validInput(match = validMatch(correctionHistory = listOf(correctionRecord()))),
        ).dataRows()
            .map { row -> row[CORRECTION_STATUS_COLUMN] }
            .toSet()

        assertEquals(setOf("corrected_finalized"), correctionStatusColumnValues)
    }

    @Test
    fun rawOcrAndPrivateEvidenceColumnNamesAreNotExported() {
        val csv = exportSuccess(validInput())

        listOf(
            "raw_ocr_text",
            "ocr_confidence",
            "screenshot_path",
            "screenshot_storage_path",
            "preserved_ocr_evidence",
            "private_correction_evidence",
        ).forEach { excludedColumn ->
            assertFalse(csv.contains(excludedColumn))
        }
    }

    private fun exportSuccess(input: MatchCsvExportInput): String {
        val result = exporter.export(input)
        return (result as MatchCsvExportResult.Success).csv
    }

    private fun assertFailure(
        result: MatchCsvExportResult,
        failure: MatchCsvExportFailure,
    ) {
        val failed = result as MatchCsvExportResult.Failure
        assertTrue(failure in failed.failures)
    }

    private fun validInput(
        tournament: Tournament = validTournament(),
        match: Match = validMatch(),
        teamSlots: List<TeamSlot> = validTeamSlots(),
        rosterPlayers: List<RosterPlayer> = validRosterPlayers(),
    ): MatchCsvExportInput =
        MatchCsvExportInput(
            tournament = tournament,
            match = match,
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
        status: MatchStatus = MatchStatus.FINALIZED,
        placements: List<MatchPlacement> = validPlacements(),
        kills: List<MatchKill> = validKills(),
        correctionHistory: List<MatchCorrectionRecord> = emptyList(),
    ): Match =
        Match(
            id = MATCH_ID,
            tournamentId = TOURNAMENT_ID,
            matchNumber = 3,
            date = LocalDate.of(2026, 7, 31),
            mapName = "Bermuda",
            status = status,
            placements = placements,
            kills = kills,
            correctionHistory = correctionHistory,
        )

    private fun validPlacements(): List<MatchPlacement> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchPlacement(
                teamSlotNumber = slotNumber,
                position = slotNumber,
            )
        }

    private fun validKills(): List<MatchKill> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchKill(
                teamSlotNumber = slotNumber,
                kills = slotNumber - 1,
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

    private fun correctionRecord(): MatchCorrectionRecord =
        MatchCorrectionRecord(
            previousPlacements = validPlacements(),
            previousKills = validKills(),
            correctedPlacements = validPlacements(),
            correctedKills = validKills(),
        )

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

    private fun List<RosterPlayer>.replacePlayer(
        slotNumber: Int,
        playerIndex: Int,
        name: String,
    ): List<RosterPlayer> {
        var indexForSlot = 0
        return map { player ->
            if (player.slotNumber == slotNumber && indexForSlot++ == playerIndex) {
                player.copy(displayName = name)
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
            .map { line -> line.split(",") }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
        const val MATCH_ID = "match-id"
        const val ROW_NUMBER_COLUMN = 7
        const val PLACEMENT_COLUMN = 8
        const val PLACEMENT_POINTS_COLUMN = 15
        const val KILLS_COLUMN = 16
        const val KILL_POINTS_COLUMN = 17
        const val TOTAL_POINTS_COLUMN = 18
        const val CORRECTION_STATUS_COLUMN = 19
        const val EXPECTED_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,match_id,match_label,match_finalized_at,row_number,placement,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,placement_points,kills,kill_points,total_points,correction_status"
    }
}
