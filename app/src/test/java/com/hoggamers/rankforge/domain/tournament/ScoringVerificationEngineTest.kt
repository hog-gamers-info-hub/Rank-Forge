package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScoringVerificationEngineTest {
    private lateinit var verification: ScoringVerificationEngine

    @Before
    fun setUp() {
        verification = ScoringVerificationEngine()
    }

    @Test
    fun validFinalizedMatchProducesVerifiedDerivedScores() {
        val result = verification(
            listOf(match("match-1", killsByTeamSlot = mapOf(2 to 5))),
        )

        assertEquals(ScoringVerificationState.VALID, result.state)
        assertTrue(result.isValid)
        assertEquals(listOf("match-1"), result.finalizedMatchIds)
        assertEquals(12, result.matchVerifications.single().teamScores.size)
        val slotTwo = result.matchVerifications.single().teamScores.first { it.teamSlotNumber == 2 }
        assertEquals(9, slotTwo.positionPoints)
        assertEquals(5, slotTwo.killPoints)
        assertEquals(14, slotTwo.matchTotal)
        assertTrue(result.matchVerifications.single().matchTotalConsistent)
    }

    @Test
    fun draftMatchesAreExcludedFromVerificationAndStandings() {
        val result = verification(
            listOf(
                match("finalized-1"),
                match("draft-2", status = MatchStatus.DRAFT, killForSlotOne = 100),
            ),
        )

        assertEquals(ScoringVerificationState.VALID, result.state)
        assertEquals(listOf("finalized-1"), result.finalizedMatchIds)
        assertEquals(listOf("draft-2"), result.excludedDraftMatchIds)
        assertTrue(result.standings.all { it.matchesIncluded == 1 })
    }

    @Test
    fun cumulativeTotalsAreVerifiedFromFinalizedMatchesOnly() {
        val result = verification(
            listOf(
                match("match-1", killForSlotOne = 2),
                match(
                    "match-2",
                    placementsByTeamSlot = defaultPlacements() + mapOf(1 to 2, 2 to 1),
                    killForSlotOne = 3,
                ),
            ),
        )

        val slotOne = result.standings.first { it.teamSlotNumber == 1 }
        assertEquals(21, slotOne.totalPositionPoints)
        assertEquals(5, slotOne.totalKillPoints)
        assertEquals(26, slotOne.totalPoints)
        assertEquals(2, slotOne.matchesIncluded)
        assertTrue(result.cumulativeTotalsConsistent)
    }

    @Test
    fun approvedTieBreakOrderingUsesExistingRules() {
        val result = verification(
            listOf(
                match(
                    "match-1",
                    placementsByTeamSlot = defaultPlacements() + mapOf(1 to 1, 2 to 2),
                    killsByTeamSlot = mapOf(1 to 0, 2 to 10),
                ),
                match(
                    "match-2",
                    placementsByTeamSlot = defaultPlacements() + mapOf(1 to 2, 2 to 1),
                    killsByTeamSlot = mapOf(1 to 10, 2 to 0),
                ),
            ),
        )

        assertTrue(result.tieBreakOrderingVerified)
        assertEquals(listOf(2, 1), result.tieBreakStandings.take(2).map { it.standing.teamSlotNumber })
        assertFalse(result.tieBreakStandings.take(2).any { it.isCompleteTie })
    }

    @Test
    fun completeUnresolvedTieRemainsRepresentedByTieBreakRules() {
        val result = TieBreakRules()(
            listOf(
                standing(teamSlotNumber = 1, totalPoints = 100),
                standing(teamSlotNumber = 2, totalPoints = 100),
            ),
        )

        assertTrue(result.all { it.isCompleteTie })
    }

    private fun standing(
        teamSlotNumber: Int,
        totalPoints: Int,
    ) = CumulativeTournamentStanding(
        teamSlotNumber = teamSlotNumber,
        totalPositionPoints = totalPoints,
        totalKillPoints = 0,
        totalPoints = totalPoints,
        firstPlaceFinishes = 0,
        latestMatchPlacement = 12,
        matchesIncluded = 1,
    )

    @Test
    fun noFinalizedMatchesHaveExplicitEmptyState() {
        val result = verification(listOf(match("draft-1", status = MatchStatus.DRAFT)))

        assertEquals(ScoringVerificationState.NO_FINALIZED_MATCHES, result.state)
        assertFalse(result.isValid)
        assertTrue(result.finalizedMatchIds.isEmpty())
        assertTrue(result.standings.isEmpty())
    }

    @Test
    fun invalidPlacementAndNegativeKillsAreSurfaced() {
        val invalidPlacement = match("invalid-placement").copy(
            placements = listOf(MatchPlacement(1, 13)) + defaultPlacements()
                .filterKeys { it != 1 }
                .map { (slot, placement) -> MatchPlacement(slot, placement) },
        )
        val invalidKills = match("invalid-kills").copy(
            kills = defaultPlacements().keys.map { slot -> MatchKill(slot, if (slot == 1) -1 else 0) },
        )

        val result = verification(listOf(invalidPlacement, invalidKills))

        assertEquals(ScoringVerificationState.INVALID, result.state)
        assertFalse(result.isValid)
        assertEquals(2, result.issues.size)
        assertTrue(result.issues.all { it.code == ScoringVerificationIssueCode.INVALID_FINALIZED_MATCH })
    }

    @Test
    fun duplicateFinalizedMatchIdsAreHandledOnceDeterministically() {
        val duplicate = match("match-1", killForSlotOne = 4)

        val first = verification(listOf(duplicate, duplicate))
        val second = verification(listOf(duplicate, duplicate))

        assertEquals(first, second)
        assertEquals(listOf("match-1"), first.finalizedMatchIds)
        assertEquals(listOf("match-1"), first.duplicateFinalizedMatchIds)
        assertEquals(1, first.standings.first { it.teamSlotNumber == 1 }.matchesIncluded)
    }

    private fun match(
        id: String,
        status: MatchStatus = MatchStatus.FINALIZED,
        placementsByTeamSlot: Map<Int, Int> = defaultPlacements(),
        killsByTeamSlot: Map<Int, Int> = emptyMap(),
        killForSlotOne: Int = 0,
    ) = Match(
        id = id,
        tournamentId = "tournament-1",
        matchNumber = id.substringAfterLast('-').toIntOrNull() ?: 1,
        date = LocalDate.of(2026, 7, 26),
        mapName = "Bermuda",
        status = status,
        placements = placementsByTeamSlot.map { (slot, placement) -> MatchPlacement(slot, placement) },
        kills = placementsByTeamSlot.keys.map { slot ->
            MatchKill(slot, killsByTeamSlot[slot] ?: if (slot == 1) killForSlotOne else 0)
        },
    )

    private fun defaultPlacements(): Map<Int, Int> =
        (1..12).associateWith { it }
}

