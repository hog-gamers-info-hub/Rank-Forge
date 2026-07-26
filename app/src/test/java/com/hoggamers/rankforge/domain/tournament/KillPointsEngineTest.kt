package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class KillPointsEngineTest {
    private lateinit var killPoints: KillPointsEngine

    @Before
    fun setUp() {
        killPoints = KillPointsEngine()
    }

    @Test
    fun zeroKillsReturnZeroPoints() {
        assertEquals(0, killPoints(0))
    }

    @Test
    fun positiveKillValuesReturnTheSamePointValue() {
        val confirmedKills = listOf(1, 2, 5, 12)

        assertEquals(
            confirmedKills,
            confirmedKills.map(killPoints::invoke),
        )
    }

    @Test
    fun highKillTotalsAreSupportedWithoutAnAssumedMaximum() {
        val confirmedKills = 1_000_000

        assertEquals(confirmedKills, killPoints(confirmedKills))
    }

    @Test
    fun negativeKillsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            killPoints(-1)
        }
    }

    @Test
    fun repeatedCalculationIsDeterministic() {
        val results = (1..10).map { killPoints(7) }

        assertEquals(List(10) { 7 }, results)
    }
}
