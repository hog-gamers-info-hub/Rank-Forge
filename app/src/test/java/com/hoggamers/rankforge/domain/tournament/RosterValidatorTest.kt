package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterValidatorTest {
    private val validator = RosterValidator()

    @Test
    fun validTwelveTeamRosterHasNoIssues() {
        val result = validator.validate(
            teams = (1..12).map { slotNumber ->
                team(
                    slotNumber = slotNumber,
                    teamName = "Team $slotNumber",
                    playerNames = listOf("One", "Two", "Three", "Four"),
                )
            },
        )

        assertTrue(result.issues.isEmpty())
        assertFalse(result.hasBlockingIssues)
    }

    @Test
    fun missingTeamNameIsNonBlockingIssue() {
        val result = validator.validate(listOf(team(1, "", listOf("One", "Two", "Three", "Four"))))

        assertEquals(listOf(RosterValidationIssue.MissingTeamName(1)), result.issues)
        assertFalse(result.hasBlockingIssues)
    }

    @Test
    fun playerCountsOutsideFourToSixAreNonBlockingIssues() {
        val result = validator.validate(
            listOf(
                team(1, "Alpha", emptyList()),
                team(2, "Bravo", List(7) { "Player $it" }),
            ),
        )

        assertEquals(
            listOf(
                RosterValidationIssue.InvalidPlayerCount(1, 0),
                RosterValidationIssue.InvalidPlayerCount(2, 7),
            ),
            result.issues,
        )
        assertFalse(result.hasBlockingIssues)
    }

    @Test
    fun exactlyFourAndSixPlayersAreAccepted() {
        val result = validator.validate(
            listOf(
                team(1, "Alpha", List(4) { "Four $it" }),
                team(2, "Bravo", List(6) { "Six $it" }),
            ),
        )

        assertTrue(result.issues.isEmpty())
        assertFalse(result.hasBlockingIssues)
    }

    @Test
    fun trimmedExactTeamAndPlayerDuplicatesAreBlocking() {
        val result = validator.validate(
            listOf(
                team(1, " Alpha ", listOf("One", "Two", "Three", "Four")),
                team(2, "Alpha", listOf("One", "Two", " Two ", "Four")),
            ),
        )

        assertEquals(
            listOf(
                RosterValidationIssue.DuplicateTeamName(2, 1, "Alpha"),
                RosterValidationIssue.DuplicatePlayerName(2, 2, 1, "Two"),
            ),
            result.issues,
        )
        assertTrue(result.hasBlockingIssues)
    }

    @Test
    fun caseSensitiveNamesRemainDistinct() {
        val result = validator.validate(
            listOf(
                team(1, "Alpha", listOf("Player")),
                team(2, "alpha", listOf("player")),
            ),
        )

        assertEquals(
            listOf(
                RosterValidationIssue.InvalidPlayerCount(1, 1),
                RosterValidationIssue.InvalidPlayerCount(2, 1),
            ),
            result.issues,
        )
    }

    @Test
    fun duplicatePlayersWithinOneTeamAreBlocking() {
        val result = validator.validate(
            listOf(team(1, "Alpha", listOf("One", "Two", "One", "Four"))),
        )

        assertEquals(
            listOf(RosterValidationIssue.DuplicatePlayerName(1, 2, 0, "One")),
            result.issues,
        )
    }

    @Test
    fun samePlayerNameAcrossTeamsIsAllowed() {
        val result = validator.validate(
            listOf(
                team(1, "Alpha", listOf("Player")),
                team(2, "Bravo", listOf("Player")),
            ),
        )

        assertTrue(result.issues.all { it is RosterValidationIssue.InvalidPlayerCount })
        assertFalse(result.hasBlockingIssues)
    }

    @Test
    fun multipleIssuesAreOrderedBySlotAndRule() {
        val result = validator.validate(
            listOf(
                team(3, " Alpha ", listOf("One", "One")),
                team(1, "Alpha", emptyList()),
                team(2, "", List(7) { "Player $it" }),
            ),
        )

        assertEquals(
            listOf(
                RosterValidationIssue.InvalidPlayerCount(1, 0),
                RosterValidationIssue.MissingTeamName(2),
                RosterValidationIssue.InvalidPlayerCount(2, 7),
                RosterValidationIssue.DuplicateTeamName(3, 1, "Alpha"),
                RosterValidationIssue.InvalidPlayerCount(3, 2),
                RosterValidationIssue.DuplicatePlayerName(3, 1, 0, "One"),
            ),
            result.issues,
        )
    }

    private fun team(
        slotNumber: Int,
        teamName: String,
        playerNames: List<String>,
    ) = RosterValidationTeam(
        slotNumber = slotNumber,
        teamName = teamName,
        players = playerNames.mapIndexed { index, name ->
            RosterValidationPlayer(index, name)
        },
    )
}
