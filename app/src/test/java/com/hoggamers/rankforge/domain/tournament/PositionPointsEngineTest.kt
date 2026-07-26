package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class PositionPointsEngineTest {
    private lateinit var positionPoints: PositionPointsEngine

    @Before
    fun setUp() {
        positionPoints = PositionPointsEngine()
    }

    @Test
    fun everyValidPlacementUsesApprovedPositionPoints() {
        val expectedPoints = listOf(12, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0)

        val actualPoints = (1..12).map { placement ->
            positionPoints(placement)
        }

        assertEquals(expectedPoints, actualPoints)
    }

    @Test
    fun placementBelowValidRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            positionPoints(0)
        }
    }

    @Test
    fun placementAboveValidRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            positionPoints(13)
        }
    }

    @Test
    fun repeatedCalculationIsDeterministic() {
        val results = (1..10).map { positionPoints(5) }

        assertEquals(List(10) { 6 }, results)
    }

    @Test
    fun eleventhAndTwelfthPlacementsBothReturnZero() {
        assertEquals(0, positionPoints(11))
        assertEquals(0, positionPoints(12))
    }
}
