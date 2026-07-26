package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TieBreakRulesTest {
    private lateinit var tieBreakRules: TieBreakRules

    @Before
    fun setUp() {
        tieBreakRules = TieBreakRules()
    }

    @Test
    fun higherTotalPointsRankFirst() {
        val result = tieBreakRules(
            listOf(
                standing(teamSlotNumber = 1, totalPoints = 100),
                standing(teamSlotNumber = 2, totalPoints = 90),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
    }

    @Test
    fun equalTotalPointsAreResolvedByFirstPlaceFinishes() {
        val result = tieBreakRules(
            listOf(
                standing(teamSlotNumber = 1, totalPoints = 100, firstPlaceFinishes = 2),
                standing(teamSlotNumber = 2, totalPoints = 100, firstPlaceFinishes = 1),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
    }

    @Test
    fun equalTotalPointsAndFirstPlacesAreResolvedByTotalKills() {
        val result = tieBreakRules(
            listOf(
                standing(
                    teamSlotNumber = 1,
                    totalPoints = 100,
                    firstPlaceFinishes = 1,
                    totalKillPoints = 8,
                ),
                standing(
                    teamSlotNumber = 2,
                    totalPoints = 100,
                    firstPlaceFinishes = 1,
                    totalKillPoints = 5,
                ),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
    }

    @Test
    fun equalEarlierCriteriaAreResolvedByBetterLatestMatchPlacement() {
        val result = tieBreakRules(
            listOf(
                standing(
                    teamSlotNumber = 1,
                    totalPoints = 100,
                    firstPlaceFinishes = 1,
                    totalKillPoints = 8,
                    latestMatchPlacement = 2,
                ),
                standing(
                    teamSlotNumber = 2,
                    totalPoints = 100,
                    firstPlaceFinishes = 1,
                    totalKillPoints = 8,
                    latestMatchPlacement = 5,
                ),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
    }

    @Test
    fun laterCriteriaAreNotUsedWhenTotalPointsResolveTheOrder() {
        val result = tieBreakRules(
            listOf(
                standing(
                    teamSlotNumber = 1,
                    totalPoints = 100,
                    firstPlaceFinishes = 0,
                    totalKillPoints = 0,
                    latestMatchPlacement = 12,
                ),
                standing(
                    teamSlotNumber = 2,
                    totalPoints = 99,
                    firstPlaceFinishes = 10,
                    totalKillPoints = 100,
                    latestMatchPlacement = 1,
                ),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
    }

    @Test
    fun completeEqualityRemainsAnUnresolvedCompleteTie() {
        val result = tieBreakRules(
            listOf(
                standing(teamSlotNumber = 2, totalPoints = 100),
                standing(teamSlotNumber = 1, totalPoints = 100),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
        assertTrue(result.all { it.isCompleteTie })
    }

    @Test
    fun repeatedOrderingIsDeterministic() {
        val standings = listOf(
            standing(teamSlotNumber = 3, totalPoints = 90, totalKillPoints = 4),
            standing(teamSlotNumber = 1, totalPoints = 100),
            standing(teamSlotNumber = 2, totalPoints = 90, totalKillPoints = 4),
        )

        assertEquals(tieBreakRules(standings), tieBreakRules(standings))
    }

    @Test
    fun multipleRowsAreSortedByTheApprovedCriteria() {
        val result = tieBreakRules(
            listOf(
                standing(
                    teamSlotNumber = 1,
                    totalPoints = 100,
                    firstPlaceFinishes = 1,
                    totalKillPoints = 5,
                    latestMatchPlacement = 2,
                ),
                standing(
                    teamSlotNumber = 2,
                    totalPoints = 100,
                    firstPlaceFinishes = 2,
                    totalKillPoints = 1,
                    latestMatchPlacement = 12,
                ),
                standing(
                    teamSlotNumber = 3,
                    totalPoints = 100,
                    firstPlaceFinishes = 2,
                    totalKillPoints = 5,
                    latestMatchPlacement = 3,
                ),
                standing(teamSlotNumber = 4, totalPoints = 110),
                standing(teamSlotNumber = 5, totalPoints = 90),
            ),
        )

        assertEquals(listOf(4, 3, 2, 1, 5), teamSlots(result))
        assertFalse(result.any { it.isCompleteTie })
    }

    @Test
    fun teamSlotIsOnlyUsedForStableOutputWithinAnExplicitCompleteTie() {
        val result = tieBreakRules(
            listOf(
                standing(teamSlotNumber = 2, totalPoints = 100),
                standing(teamSlotNumber = 1, totalPoints = 100),
            ),
        )

        assertEquals(listOf(1, 2), teamSlots(result))
        assertTrue(result.all { it.isCompleteTie })
    }

    private fun teamSlots(result: List<TieBreakStanding>): List<Int> =
        result.map { it.standing.teamSlotNumber }

    private fun standing(
        teamSlotNumber: Int,
        totalPoints: Int = 0,
        totalPositionPoints: Int = totalPoints,
        totalKillPoints: Int = 0,
        firstPlaceFinishes: Int = 0,
        latestMatchPlacement: Int = 12,
        matchesIncluded: Int = 1,
    ): CumulativeTournamentStanding =
        CumulativeTournamentStanding(
            teamSlotNumber = teamSlotNumber,
            totalPositionPoints = totalPositionPoints,
            totalKillPoints = totalKillPoints,
            totalPoints = totalPoints,
            firstPlaceFinishes = firstPlaceFinishes,
            latestMatchPlacement = latestMatchPlacement,
            matchesIncluded = matchesIncluded,
        )
}

