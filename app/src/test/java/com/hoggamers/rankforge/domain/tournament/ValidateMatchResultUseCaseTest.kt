package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateMatchResultUseCaseTest {
    private lateinit var validate: ValidateMatchResultUseCase

    @Before
    fun setUp() {
        validate = ValidateMatchResultUseCase()
    }

    @Test
    fun completeTwelveTeamRowsAreValid() {
        val result = validate(
            (1..12).map { teamSlotNumber ->
                MatchResultRowInput(
                    teamSlotNumber = teamSlotNumber,
                    placement = teamSlotNumber.toString(),
                    kills = "0",
                )
            },
        )

        assertTrue(result.isValid)
    }

    @Test
    fun missingRowsAndFieldsAreDetected() {
        val result = validate(
            listOf(
                MatchResultRowInput(teamSlotNumber = 1, placement = "", kills = null),
            ),
        )

        assertTrue(
            MatchResultValidationError.MISSING_TEAM_RESULT_ROW in result.errorsByTeamSlot.getValue(12),
        )
        assertTrue(
            MatchResultValidationError.MISSING_PLACEMENT in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.MISSING_KILLS in result.errorsByTeamSlot.getValue(1),
        )
    }

    @Test
    fun duplicateTeamsAndPlacementsAreDetected() {
        val rows = (1..12).map { teamSlotNumber ->
            MatchResultRowInput(
                teamSlotNumber = teamSlotNumber,
                placement = if (teamSlotNumber == 2) "1" else teamSlotNumber.toString(),
                kills = "0",
            )
        } + MatchResultRowInput(teamSlotNumber = 1, placement = "1", kills = "0")

        val result = validate(rows)

        assertTrue(
            MatchResultValidationError.DUPLICATE_TEAM in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.DUPLICATE_PLACEMENT in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.DUPLICATE_PLACEMENT in result.errorsByTeamSlot.getValue(2),
        )
    }

    @Test
    fun invalidPlacementAndKillValuesAreDetected() {
        val result = validate(
            listOf(
                MatchResultRowInput(teamSlotNumber = 1, placement = "13", kills = "-1"),
                MatchResultRowInput(teamSlotNumber = 2, placement = "abc", kills = "two"),
            ),
        )

        assertTrue(
            MatchResultValidationError.INVALID_PLACEMENT in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.INVALID_KILLS in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.INVALID_PLACEMENT in result.errorsByTeamSlot.getValue(2),
        )
        assertTrue(
            MatchResultValidationError.INVALID_KILLS in result.errorsByTeamSlot.getValue(2),
        )
    }

    @Test
    fun storedDraftDetectsDuplicateAndInvalidTypedValues() {
        val result = validate(
            Match(
                id = "match-id",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
                placements = listOf(MatchPlacement(1, 1), MatchPlacement(1, 1)),
                kills = listOf(MatchKill(1, -1)),
            ),
        )

        assertTrue(
            MatchResultValidationError.DUPLICATE_TEAM in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.DUPLICATE_PLACEMENT in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.INVALID_KILLS in result.errorsByTeamSlot.getValue(1),
        )
    }

    @Test
    fun storedMatchMissingTypedResultDataIsDetected() {
        val result = validate(
            Match(
                id = "match-id",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )

        assertTrue(
            MatchResultValidationError.MISSING_PLACEMENT in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.MISSING_KILLS in result.errorsByTeamSlot.getValue(1),
        )
    }

    @Test
    fun storedMatchInvalidTypedResultDataIsDetected() {
        val result = validate(
            Match(
                id = "match-id",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
                placements = listOf(MatchPlacement(1, 13)),
                kills = listOf(MatchKill(1, -1)),
            ),
        )

        assertTrue(
            MatchResultValidationError.INVALID_PLACEMENT in result.errorsByTeamSlot.getValue(1),
        )
        assertTrue(
            MatchResultValidationError.INVALID_KILLS in result.errorsByTeamSlot.getValue(1),
        )
    }
}
