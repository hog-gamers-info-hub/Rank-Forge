package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
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
import org.junit.Assert.assertNull
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
        assertTrue(success.csv.contains("phase_10_v2,match_result"))
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
    fun tenTeamFinalizedMatchExportsOnlyTenRowsSortedByPlacement() {
        val csv = exportSuccess(
            validInput(
                match = validMatch(activeCount = 10),
                teamSlots = validTeamSlots(activeCount = 10),
            ),
        )

        assertEquals(11, csv.linesByRecord().size)
        assertEquals((1..10).map { it.toString() }, csv.dataRows().map { it[ROW_NUMBER_COLUMN] })
        assertEquals((1..10).map { it.toString() }, csv.dataRows().map { it[PLACEMENT_COLUMN] })
        assertEquals((1..10).map { it.toString() }, csv.dataRows().map { it[TEAM_SLOT_COLUMN] })
    }

    @Test
    fun tenTeamCorrectedMatchRetainsCorrectedFinalizedStatus() {
        val rows = (exporter.buildMatchRows(
            validInput(
                match = validMatch(
                    activeCount = 10,
                    correctionHistory = listOf(correctionRecord(activeCount = 10)),
                ),
                teamSlots = validTeamSlots(activeCount = 10),
            ),
        ) as MatchExportRowsResult.Success).rows

        assertEquals(setOf("corrected_finalized"), rows.map { it.correctionStatus }.toSet())
    }

    @Test
    fun tenTeamMatchWithNineOrElevenRowsIsRejected() {
        listOf(
            validMatch(activeCount = 10, placements = validPlacements(10).dropLast(1)),
            validMatch(
                activeCount = 10,
                placements = validPlacements(10) + MatchPlacement(10, 11),
            ),
        ).forEach { match ->
            assertFailure(
                exporter.export(
                    validInput(match = match, teamSlots = validTeamSlots(activeCount = 10)),
                ),
                MatchCsvExportFailure.INVALID_ROW_COUNT,
            )
        }
    }

    @Test
    fun tenTeamMatchRejectsParticipantAndValueShapeFailures() {
        val tenTeamSlots = validTeamSlots(activeCount = 10)
        assertFailure(
            exporter.export(
                validInput(
                    match = validMatch(
                        activeCount = 10,
                        placements = validPlacements(10).dropLast(1) + MatchPlacement(11, 10),
                        kills = validKills(10).dropLast(1) + MatchKill(11, 9),
                    ),
                    teamSlots = tenTeamSlots,
                ),
            ),
            MatchCsvExportFailure.MISSING_KILL_VALUE,
        )
        assertFailure(
            exporter.export(
                validInput(
                    match = validMatch(
                        activeCount = 10,
                        placements = validPlacements(10).replacePlacement(10, 11),
                    ),
                    teamSlots = tenTeamSlots,
                ),
            ),
            MatchCsvExportFailure.MISSING_PLACEMENT,
        )
        assertFailure(
            exporter.export(
                validInput(
                    match = validMatch(
                        activeCount = 10,
                        placements = validPlacements(10).dropLast(1) + MatchPlacement(1, 10),
                    ),
                    teamSlots = tenTeamSlots,
                ),
            ),
            MatchCsvExportFailure.DUPLICATE_TEAM_SLOT,
        )
        assertFailure(
            exporter.export(
                validInput(
                    match = validMatch(
                        activeCount = 10,
                        kills = validKills(10).replaceKill(1, -1),
                    ),
                    teamSlots = tenTeamSlots,
                ),
            ),
            MatchCsvExportFailure.INVALID_KILL_COUNT,
        )
    }

    @Test
    fun inactiveStructuralSlotsDoNotRequireNamesOrResultRows() {
        val result = exporter.export(
            validInput(
                match = validMatch(activeCount = 10),
                teamSlots = validTeamSlots(activeCount = 10),
            ),
        )

        assertTrue(result is MatchCsvExportResult.Success)
    }

    @Test
    fun sparseParticipationIsExportedAndZeroParticipantsAreRejected() {
        val gapSlots = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            TeamSlot(TOURNAMENT_ID, slotNumber, if (slotNumber <= 5 || slotNumber == 7) "Team $slotNumber" else "")
        }
        val sparseSlots = listOf(1, 2, 3, 4, 5, 7)
        val sparseMatch = validMatch(
            placements = sparseSlots.mapIndexed { index, slotNumber ->
                MatchPlacement(teamSlotNumber = slotNumber, position = index + 1)
            },
            kills = sparseSlots.mapIndexed { index, slotNumber ->
                MatchKill(teamSlotNumber = slotNumber, kills = index)
            },
        )
        assertTrue(exporter.export(validInput(teamSlots = gapSlots, match = sparseMatch)) is MatchCsvExportResult.Success)

        assertFailure(
            exporter.export(
                validInput(
                    teamSlots = validTeamSlots(activeCount = 0),
                    match = validMatch(activeCount = 0),
                ),
            ),
            MatchCsvExportFailure.MISSING_TEAM_IDENTITY,
        )
    }

    @Test
    fun noShowRowsAreExportedWithBlankPlacementAndZeroScores() {
        val result = exporter.buildMatchRows(
            validInput(
                match = validMatch().copy(
                    participantResults = listOf(
                        MatchParticipantResult(1, MatchParticipationStatus.NO_SHOW, null, 0),
                        MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 1, 2),
                        MatchParticipantResult(3, MatchParticipationStatus.NO_SHOW, null, 0),
                    ),
                ),
            ),
        ) as MatchExportRowsResult.Success

        assertEquals(3, result.rows.size)
        assertEquals(MatchParticipationStatus.PARTICIPATED.name, result.rows.first().participationStatus)
        assertEquals(1, result.rows.first().placement)
        result.rows.drop(1).forEach { row ->
            assertEquals(MatchParticipationStatus.NO_SHOW.name, row.participationStatus)
            assertNull(row.placement)
            assertEquals(0, row.kills)
            assertEquals(0, row.placementPoints)
            assertEquals(0, row.killPoints)
            assertEquals(0, row.totalPoints)
        }
    }

    @Test
    fun noShowRowsFollowAllParticipatedRowsInTeamSlotOrder() {
        val noShowSlots = setOf(3, 12)
        val participatedSlots = TeamSlot.SLOT_NUMBERS.filterNot { it in noShowSlots }
        val result = exporter.buildMatchRows(
            validInput(
                match = validMatch().copy(
                    participantResults = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                        if (slotNumber in noShowSlots) {
                            MatchParticipantResult(slotNumber, MatchParticipationStatus.NO_SHOW, null, 0)
                        } else {
                            MatchParticipantResult(
                                slotNumber,
                                MatchParticipationStatus.PARTICIPATED,
                                participatedSlots.indexOf(slotNumber) + 1,
                                0,
                            )
                        }
                    },
                ),
            ),
        ) as MatchExportRowsResult.Success

        assertEquals(
            participatedSlots + noShowSlots.sorted(),
            result.rows.map { row -> row.teamSlot },
        )
        result.rows.take(participatedSlots.size).forEach { row ->
            assertEquals(MatchParticipationStatus.PARTICIPATED.name, row.participationStatus)
        }
        result.rows.takeLast(noShowSlots.size).forEach { row ->
            assertEquals(MatchParticipationStatus.NO_SHOW.name, row.participationStatus)
            assertEquals(0, row.kills)
            assertEquals(0, row.placementPoints)
            assertEquals(0, row.killPoints)
            assertEquals(0, row.totalPoints)
        }
    }

    @Test
    fun participatedZeroPointRowIsNotClassifiedAsNoShow() {
        val result = exporter.buildMatchRows(
            validInput(
                match = validMatch().copy(
                    participantResults = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                        MatchParticipantResult(
                            teamSlotNumber = slotNumber,
                            participationStatus = MatchParticipationStatus.PARTICIPATED,
                            placement = slotNumber,
                            kills = 0,
                        )
                    },
                ),
            ),
        ) as MatchExportRowsResult.Success

        val lastRow = result.rows.last()
        assertEquals(12, lastRow.teamSlot)
        assertEquals(MatchParticipationStatus.PARTICIPATED.name, lastRow.participationStatus)
        assertEquals(12, lastRow.placement)
        assertEquals(0, lastRow.totalPoints)
    }

    @Test
    fun repeatedTenTeamExportIsDeterministic() {
        val input = validInput(
            match = validMatch(activeCount = 10),
            teamSlots = validTeamSlots(activeCount = 10),
        )

        assertEquals(exporter.export(input), exporter.export(input))
    }

    @Test
    fun buildMatchRowsUsesTheExactTypedPhase10Contract() {
        val result = exporter.buildMatchRows(validInput()) as MatchExportRowsResult.Success

        assertEquals(12, result.rows.size)
        assertEquals((1..12).toList(), result.rows.map { it.rowNumber })
        assertEquals(setOf(TOURNAMENT_ID), result.rows.map { it.tournamentId }.toSet())
        assertEquals(setOf(MATCH_ID), result.rows.map { it.matchId }.toSet())
        assertTrue(result.rows.all { it.matchFinalizedAt.isEmpty() })
        assertEquals(setOf("original_finalized"), result.rows.map { it.correctionStatus }.toSet())
        assertEquals(
            PositionPointsEngine()(1),
            result.rows.first().placementPoints,
        )
        assertEquals(
            KillPointsEngine()(0),
            result.rows.first().killPoints,
        )
        assertEquals(
            MatchTotalEngine()(1, 0),
            result.rows.first().totalPoints,
        )
    }

    @Test
    fun correctedTypedRowsAndCsvShareTheSameValidationFailureSet() {
        val input = validInput(
            match = validMatch(
                status = MatchStatus.DRAFT,
                correctionHistory = listOf(correctionRecord()),
            ),
        )

        val rowsResult = exporter.buildMatchRows(input) as MatchExportRowsResult.Failure
        val csvResult = exporter.export(input) as MatchCsvExportResult.Failure

        assertEquals(csvResult.failures, rowsResult.failures)
    }

    @Test
    fun exportedRowsAreOrderedByTotalPointsWithPlacementTieBreak() {
        val input = validInput(
            match = validMatch().copy(
                participantResults = listOf(
                    MatchParticipantResult(10, MatchParticipationStatus.PARTICIPATED, 4, 80),
                    MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 3, 32),
                    MatchParticipantResult(7, MatchParticipationStatus.PARTICIPATED, 2, 49),
                    MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 1, 55),
                ),
            ),
        )

        val rows = (exporter.buildMatchRows(input) as MatchExportRowsResult.Success).rows

        assertEquals(listOf(10, 2, 7, 1), rows.map { row -> row.teamSlot })
        assertEquals(listOf(1, 2, 3, 4), rows.map { row -> row.rowNumber })
        assertEquals(listOf(4, 1, 2, 3), rows.map { row -> row.placement })
    }

    @Test
    fun rowNumbersFollowExportedRankingOrder() {
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
    fun blankInactiveTeamNameDoesNotRequireAResultRow() {
        val activeSlots = (2..12).toList()
        val result = exporter.export(
            validInput(
                teamSlots = validTeamSlots().replaceTeamName(slotNumber = 1, teamName = " "),
                match = validMatch(
                    placements = activeSlots.mapIndexed { index, slot -> MatchPlacement(slot, index + 1) },
                    kills = activeSlots.mapIndexed { index, slot -> MatchKill(slot, index) },
                ),
            ),
        )

        assertTrue(result is MatchCsvExportResult.Success)
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
        activeCount: Int = 12,
        placements: List<MatchPlacement> = validPlacements(activeCount),
        kills: List<MatchKill> = validKills(activeCount),
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

    private fun validPlacements(activeCount: Int = 12): List<MatchPlacement> =
        (1..activeCount).map { slotNumber ->
            MatchPlacement(
                teamSlotNumber = slotNumber,
                position = slotNumber,
            )
        }

    private fun validKills(activeCount: Int = 12): List<MatchKill> =
        (1..activeCount).map { slotNumber ->
            MatchKill(
                teamSlotNumber = slotNumber,
                kills = slotNumber - 1,
            )
        }

    private fun validTeamSlots(activeCount: Int = 12): List<TeamSlot> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            TeamSlot(
                tournamentId = TOURNAMENT_ID,
                slotNumber = slotNumber,
                teamName = if (slotNumber <= activeCount) "Team $slotNumber" else "",
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

    private fun correctionRecord(activeCount: Int = 12): MatchCorrectionRecord =
        MatchCorrectionRecord(
            previousPlacements = validPlacements(activeCount),
            previousKills = validKills(activeCount),
            correctedPlacements = validPlacements(activeCount),
            correctedKills = validKills(activeCount),
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
        const val TEAM_SLOT_COLUMN = 10
        const val PLACEMENT_POINTS_COLUMN = 16
        const val KILLS_COLUMN = 17
        const val KILL_POINTS_COLUMN = 18
        const val TOTAL_POINTS_COLUMN = 19
        const val CORRECTION_STATUS_COLUMN = 20
        const val EXPECTED_HEADER =
            "export_schema_version,export_type,tournament_id,tournament_name,match_id,match_label,match_finalized_at,row_number,placement,participation_status,team_slot,team_name,player_1_name,player_2_name,player_3_name,player_4_name,placement_points,kills,kill_points,total_points,correction_status"
    }
}
