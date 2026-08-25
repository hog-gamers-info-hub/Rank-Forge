package com.hoggamers.rankforge.domain.ocr.matchlobby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyPlayerRowCropGeometryTest {
    @Test
    fun centerAnchorProducesExactlyFourCompleteBands() {
        val geometry = LobbyPlayerRowCropGeometryCalculator.calculate(
            teamCropWidth = 200,
            teamCropHeight = 100,
            slotAnchorY = 50.0,
        )

        assertNotNull(geometry)
        assertEquals(
            listOf(
                LobbyPlayerRowCropBounds(30, 0, 200, 25),
                LobbyPlayerRowCropBounds(30, 25, 200, 50),
                LobbyPlayerRowCropBounds(30, 50, 200, 75),
                LobbyPlayerRowCropBounds(30, 75, 200, 100),
            ),
            geometry!!.rows,
        )
    }

    @Test
    fun oddDimensionsRoundBandEdgesOutwardWithoutLeavingSource() {
        val geometry = LobbyPlayerRowCropGeometryCalculator.calculate(
            teamCropWidth = 101,
            teamCropHeight = 101,
            slotAnchorY = 51.0,
        )

        assertEquals(
            listOf(0, 26, 51, 76, 101),
            geometry!!.rows.flatMap { listOf(it.top, it.bottom) }.distinct(),
        )
        geometry.rows.forEach { bounds ->
            assertEquals(15, bounds.left)
            assertEquals(101, bounds.right)
            assertEquals(true, bounds.top >= 0 && bounds.bottom <= 101)
        }
    }

    @Test
    fun nonCenterAnchorUsesPhase2ABandsWithoutUsingOcrTextBounds() {
        val geometry = LobbyPlayerRowCropGeometryCalculator.calculate(
            teamCropWidth = 300,
            teamCropHeight = 200,
            slotAnchorY = 80.0,
        )

        assertEquals(listOf(0, 40, 80, 140, 200), geometry!!.rows.flatMap { listOf(it.top, it.bottom) }.distinct())
        assertEquals(45, geometry.playerAreaLeft)
    }

    @Test
    fun invalidDimensionsOrDegenerateAnchorDoNotCreateRows() {
        assertNull(LobbyPlayerRowCropGeometryCalculator.calculate(0, 100, 50.0))
        assertNull(LobbyPlayerRowCropGeometryCalculator.calculate(100, 100, 0.0))
        assertNull(LobbyPlayerRowCropGeometryCalculator.calculate(100, 100, 100.0))
        assertNull(LobbyPlayerRowCropGeometryCalculator.calculate(100, 100, 50.0, 1.1))
    }
}
