package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MatchTotalCalculationTest {
    private lateinit var matchTotal: MatchTotalEngine

    @Before
    fun setUp() {
        matchTotal = MatchTotalEngine()
    }

    @Test
    fun firstPlaceWithZeroKillsReturnsTwelveTotalPoints() {
        assertEquals(12, matchTotal(confirmedPlacement = 1, confirmedKills = 0))
    }

    @Test
    fun secondPlaceWithFiveKillsReturnsFourteenTotalPoints() {
        assertEquals(14, matchTotal(confirmedPlacement = 2, confirmedKills = 5))
    }

    @Test
    fun twelfthPlaceWithEightKillsReturnsEightTotalPoints() {
        assertEquals(8, matchTotal(confirmedPlacement = 12, confirmedKills = 8))
    }

    @Test
    fun invalidPlacementIsRejectedByThePositionPointsEngine() {
        assertThrows(IllegalArgumentException::class.java) {
            matchTotal(confirmedPlacement = 13, confirmedKills = 0)
        }
    }

    @Test
    fun negativeKillsAreRejectedByTheKillPointsEngine() {
        assertThrows(IllegalArgumentException::class.java) {
            matchTotal(confirmedPlacement = 1, confirmedKills = -1)
        }
    }

    @Test
    fun repeatedCalculationIsDeterministic() {
        val results = (1..10).map {
            matchTotal(confirmedPlacement = 5, confirmedKills = 7)
        }

        assertEquals(List(10) { 13 }, results)
    }
}
