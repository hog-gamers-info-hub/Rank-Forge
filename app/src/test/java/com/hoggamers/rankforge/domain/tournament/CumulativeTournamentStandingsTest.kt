package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun tenTeamFinalizedMatchProducesExactlyTenStandingsFromActiveResultRows() {
        val result = standings(
            listOf(
                match(
                    id = "ten-team-match",
                    matchNumber = 1,
                    activeCount = 10,
                    killsByTeamSlot = mapOf(2 to 5),
                ),
            ),
        )

        assertEquals(10, result.size)
        assertEquals((1..10).toList(), result.map { it.teamSlotNumber })
        assertEquals(12, result.first { it.teamSlotNumber == 1 }.totalPoints)
        assertEquals(14, result.first { it.teamSlotNumber == 2 }.totalPoints)
        assertTrue(result.all { it.matchesIncluded == 1 })
    }

    @Test
    fun noShowRowsRemainInStandingsWithoutPointsOrPlayedMatches() {
        val result = standings(
            listOf(
                match(
                    id = "mixed-match-1",
                    matchNumber = 1,
                    participantResults = listOf(
                        MatchParticipantResult(1, MatchParticipationStatus.NO_SHOW, null, 0),
                        MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 1, 2),
                        MatchParticipantResult(3, MatchParticipationStatus.NO_SHOW, null, 0),
                    ),
                ),
                match(
                    id = "mixed-match-2",
                    matchNumber = 2,
                    participantResults = listOf(
                        MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 0),
                        MatchParticipantResult(2, MatchParticipationStatus.NO_SHOW, null, 0),
                        MatchParticipantResult(3, MatchParticipationStatus.NO_SHOW, null, 0),
                    ),
                ),
            ),
        )

        assertEquals(listOf(1, 2, 3), result.map { it.teamSlotNumber })
        val noShowOnly = result.first { it.teamSlotNumber == 3 }
        assertEquals(0, noShowOnly.totalPoints)
        assertEquals(0, noShowOnly.totalKillPoints)
        assertEquals(0, noShowOnly.matchesPlayed)
        assertNull(noShowOnly.latestMatchPlacement)

        val mixed = result.first { it.teamSlotNumber == 1 }
        assertEquals(1, mixed.matchesPlayed)
        assertEquals(1, mixed.latestMatchPlacement)
    }

    @Test
    fun correctedTenTeamMatchUsesItsCurrentFinalizedValues() {
        val result = standings(
            listOf(
                match(
                    id = "corrected-ten-team-match",
                    matchNumber = 1,
                    activeCount = 10,
                    placementsByTeamSlot = placementsWithFirstTwoSwapped(activeCount = 10),
                    killsByTeamSlot = mapOf(1 to 7),
                ),
            ),
        )

        assertEquals(10, result.size)
        assertEquals(16, result.first { it.teamSlotNumber == 1 }.totalPoints)
        assertEquals(2, result.first { it.teamSlotNumber == 1 }.latestMatchPlacement)
        assertEquals(7, result.first { it.teamSlotNumber == 1 }.totalKillPoints)
    }

    @Test
    fun twelveTeamFinalizedMatchStillProducesTwelveStandingsRows() {
        val result = standings(listOf(match(id = "twelve-team-match", matchNumber = 1)))

        assertEquals(12, result.size)
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
    fun emptyFinalizedMatchInputProducesNoStandings() {
        assertTrue(standings(emptyList()).isEmpty())
    }

    @Test
    fun placementWithoutConfirmedKillDataIsRejectedByTheEngineContract() {
        val placementWithoutKill = match(id = "match-1", matchNumber = 1).copy(kills = emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            standings(listOf(placementWithoutKill))
        }
    }

    @Test
    fun equalMatchNumbersUseMatchIdForDeterministicLatestPlacement() {
        val first = standings(
            listOf(
                match(
                    id = "match-2",
                    matchNumber = 1,
                    placementsByTeamSlot = placementsWithTeamOneInFirst(),
                ),
                match(
                    id = "match-1",
                    matchNumber = 1,
                    placementsByTeamSlot = placementsWithTeamOneInThird(),
                ),
            ),
        )
        val second = standings(
            listOf(
                match(
                    id = "match-1",
                    matchNumber = 1,
                    placementsByTeamSlot = placementsWithTeamOneInThird(),
                ),
                match(
                    id = "match-2",
                    matchNumber = 1,
                    placementsByTeamSlot = placementsWithTeamOneInFirst(),
                ),
            ),
        )

        assertEquals(first, second)
        assertEquals(1, first.first { it.teamSlotNumber == 1 }.latestMatchPlacement)
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
        activeCount: Int = 12,
        placementsByTeamSlot: Map<Int, Int> = defaultPlacements(activeCount),
        killsByTeamSlot: Map<Int, Int> = emptyMap(),
        participantResults: List<MatchParticipantResult> = emptyList(),
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
            participantResults = participantResults,
        )

    private fun defaultPlacements(activeCount: Int = 12): Map<Int, Int> =
        (1..activeCount).associateWith { it }

    private fun placementsWithFirstTwoSwapped(activeCount: Int = 12): Map<Int, Int> =
        defaultPlacements(activeCount) + mapOf(1 to 2, 2 to 1)

    private fun placementsWithTeamOneInFirst(): Map<Int, Int> =
        defaultPlacements()

    private fun placementsWithTeamOneInThird(): Map<Int, Int> =
        defaultPlacements() + mapOf(1 to 3, 3 to 1)
}
