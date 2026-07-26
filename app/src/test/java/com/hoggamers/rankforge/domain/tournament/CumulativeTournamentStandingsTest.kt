package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CumulativeTournamentStandingsTest {
    private lateinit var standings: CumulativeTournamentStandingsEngine

    @Before
    fun setUp() {
        standings = CumulativeTournamentStandingsEngine()
    }

    @Test
    fun singleFinalizedMatchProducesCorrectCumulativeTotals() {
        val result = standings(
            listOf(
                match(
                    id = "match-1",
                    matchNumber = 1,
                    killsByTeamSlot = mapOf(1 to 0, 2 to 5),
                ),
            ),
        )

        assertEquals(
            CumulativeTournamentStanding(
                teamSlotNumber = 1,
                totalPositionPoints = 12,
                totalKillPoints = 0,
                totalPoints = 12,
                firstPlaceFinishes = 1,
                latestMatchPlacement = 1,
                matchesIncluded = 1,
            ),
            result.first { it.teamSlotNumber == 1 },
        )
        assertEquals(14, result.first { it.teamSlotNumber == 2 }.totalPoints)
    }

    @Test
    fun multipleFinalizedMatchesAggregateAllScoringFields() {
        val result = standings(
            listOf(
                match(
                    id = "match-1",
                    matchNumber = 1,
                    killsByTeamSlot = mapOf(1 to 2, 2 to 1),
                ),
                match(
                    id = "match-2",
                    matchNumber = 2,
                    placementsByTeamSlot = placementsWithFirstTwoSwapped(),
                    killsByTeamSlot = mapOf(1 to 3, 2 to 4),
                ),
            ),
        )

        assertEquals(
            CumulativeTournamentStanding(
                teamSlotNumber = 1,
                totalPositionPoints = 21,
                totalKillPoints = 5,
                totalPoints = 26,
                firstPlaceFinishes = 1,
                latestMatchPlacement = 2,
                matchesIncluded = 2,
            ),
            result.first { it.teamSlotNumber == 1 },
        )
        assertEquals(
            CumulativeTournamentStanding(
                teamSlotNumber = 2,
                totalPositionPoints = 21,
                totalKillPoints = 5,
                totalPoints = 26,
                firstPlaceFinishes = 1,
                latestMatchPlacement = 1,
                matchesIncluded = 2,
            ),
            result.first { it.teamSlotNumber == 2 },
        )
    }

    @Test
    fun draftMatchesAreExcluded() {
        val result = standings(
            listOf(
                match(
                    id = "match-1",
                    matchNumber = 1,
                    killsByTeamSlot = mapOf(1 to 1),
                ),
                match(
                    id = "draft-2",
                    matchNumber = 2,
                    status = MatchStatus.DRAFT,
                    killsByTeamSlot = mapOf(1 to 100),
                ),
            ),
        )

        assertEquals(13, result.first { it.teamSlotNumber == 1 }.totalPoints)
        assertEquals(1, result.first { it.teamSlotNumber == 1 }.matchesIncluded)
    }

    @Test
    fun firstPlaceFinishCountIsAccumulated() {
        val result = standings(
            listOf(
                match(id = "match-1", matchNumber = 1),
                match(
                    id = "match-2",
                    matchNumber = 2,
                    placementsByTeamSlot = placementsWithFirstTwoSwapped(),
                ),
                match(id = "match-3", matchNumber = 3),
            ),
        )

        assertEquals(2, result.first { it.teamSlotNumber == 1 }.firstPlaceFinishes)
        assertEquals(1, result.first { it.teamSlotNumber == 2 }.firstPlaceFinishes)
    }

    @Test
    fun latestMatchPlacementComesFromLatestFinalizedMatch() {
        val result = standings(
            listOf(
                match(
                    id = "match-1",
                    matchNumber = 1,
                    placementsByTeamSlot = placementsWithTeamOneInFirst(),
                ),
                match(
                    id = "match-2",
                    matchNumber = 2,
                    placementsByTeamSlot = placementsWithTeamOneInThird(),
                ),
            ),
        )

        assertEquals(3, result.first { it.teamSlotNumber == 1 }.latestMatchPlacement)
    }

    @Test
    fun repeatedCalculationIsDeterministic() {
        val matches = listOf(
            match(id = "match-1", matchNumber = 1, killsByTeamSlot = mapOf(1 to 2)),
            match(
                id = "match-2",
                matchNumber = 2,
                placementsByTeamSlot = placementsWithFirstTwoSwapped(),
                killsByTeamSlot = mapOf(1 to 4),
            ),
        )

        assertEquals(standings(matches), standings(matches))
    }

    @Test
    fun aggregationSupportsTenFinalizedMatches() {
        val result = standings(
            (1..10).map { matchNumber ->
                match(
                    id = "match-$matchNumber",
                    matchNumber = matchNumber,
                    killsByTeamSlot = mapOf(1 to matchNumber),
                )
            },
        )

        assertEquals(
            CumulativeTournamentStanding(
                teamSlotNumber = 1,
                totalPositionPoints = 120,
                totalKillPoints = 55,
                totalPoints = 175,
                firstPlaceFinishes = 10,
                latestMatchPlacement = 1,
                matchesIncluded = 10,
            ),
            result.first { it.teamSlotNumber == 1 },
        )
    }

    @Test
    fun duplicateFinalizedMatchIsCountedOnce() {
        val match = match(
            id = "match-1",
            matchNumber = 1,
            killsByTeamSlot = mapOf(1 to 2),
        )

        val result = standings(listOf(match, match))

        assertEquals(14, result.first { it.teamSlotNumber == 1 }.totalPoints)
        assertEquals(1, result.first { it.teamSlotNumber == 1 }.matchesIncluded)
    }

    @Test
    fun teamsWithoutFinalizedResultsAreExcludedBecauseMatchesAreTheInputContract() {
        val result = standings(
            listOf(
                match(
                    id = "draft-1",
                    matchNumber = 1,
                    status = MatchStatus.DRAFT,
                    placementsByTeamSlot = mapOf(2 to 1),
                    killsByTeamSlot = mapOf(2 to 0),
                ),
                match(
                    id = "match-2",
                    matchNumber = 2,
                    placementsByTeamSlot = mapOf(1 to 1),
                    killsByTeamSlot = mapOf(1 to 0),
                ),
            ),
        )

        assertEquals(listOf(1), result.map { it.teamSlotNumber })
    }

    @Test
    fun standingsRemainInStableTeamSlotOrderWithoutTieBreakRanking() {
        val result = standings(
            listOf(
                match(
                    id = "match-1",
                    matchNumber = 1,
                    placementsByTeamSlot = mapOf(1 to 1, 2 to 2),
                    killsByTeamSlot = mapOf(1 to 0, 2 to 10),
                ),
            ),
        )

        assertFalse(result.first().totalPoints > result.last().totalPoints)
        assertEquals(listOf(1, 2), result.map { it.teamSlotNumber })
    }

    @Test
    fun moreThanTenFinalizedMatchesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            standings(
                (1..11).map { matchNumber ->
                    match(id = "match-$matchNumber", matchNumber = matchNumber)
                },
            )
        }
    }

    private fun match(
        id: String,
        matchNumber: Int,
        status: MatchStatus = MatchStatus.FINALIZED,
        placementsByTeamSlot: Map<Int, Int> = defaultPlacements(),
        killsByTeamSlot: Map<Int, Int> = emptyMap(),
    ): Match =
        Match(
            id = id,
            tournamentId = "tournament-1",
            matchNumber = matchNumber,
            date = LocalDate.of(2026, 7, 26),
            mapName = "Bermuda",
            status = status,
            placements = placementsByTeamSlot.map { (teamSlotNumber, position) ->
                MatchPlacement(teamSlotNumber, position)
            },
            kills = placementsByTeamSlot.keys.map { teamSlotNumber ->
                MatchKill(teamSlotNumber, killsByTeamSlot[teamSlotNumber] ?: 0)
            },
        )

    private fun defaultPlacements(): Map<Int, Int> =
        (1..12).associateWith { it }

    private fun placementsWithFirstTwoSwapped(): Map<Int, Int> =
        defaultPlacements() + mapOf(1 to 2, 2 to 1)

    private fun placementsWithTeamOneInFirst(): Map<Int, Int> =
        defaultPlacements()

    private fun placementsWithTeamOneInThird(): Map<Int, Int> =
        defaultPlacements() + mapOf(1 to 3, 3 to 1)
}
