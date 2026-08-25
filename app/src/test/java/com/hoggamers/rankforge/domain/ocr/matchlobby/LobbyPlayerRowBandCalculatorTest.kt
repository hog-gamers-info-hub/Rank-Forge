package com.hoggamers.rankforge.domain.ocr.matchlobby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyPlayerRowBandCalculatorTest {
    @Test
    fun exactCenterProducesTwoRowsAboveAndTwoBelow() {
        val bands = requireNotNull(LobbyPlayerRowBandCalculator.calculate(360.0, 180.0))

        assertEquals(
            listOf(
                LobbyPlayerRowBand(LobbyPlayerRow.ROW_1, 0.0, 90.0),
                LobbyPlayerRowBand(LobbyPlayerRow.ROW_2, 90.0, 180.0),
                LobbyPlayerRowBand(LobbyPlayerRow.ROW_3, 180.0, 270.0),
                LobbyPlayerRowBand(LobbyPlayerRow.ROW_4, 270.0, 360.0),
            ),
            bands.bands,
        )
    }

    @Test
    fun oddHeightAndFractionalAnchorRemainDeterministic() {
        val bands = requireNotNull(LobbyPlayerRowBandCalculator.calculate(361.0, 180.5))

        assertEquals(90.25, bands.bandFor(LobbyPlayerRow.ROW_1).bottom, 0.0)
        assertEquals(180.5, bands.bandFor(LobbyPlayerRow.ROW_2).bottom, 0.0)
        assertEquals(270.75, bands.bandFor(LobbyPlayerRow.ROW_3).bottom, 0.0)
        assertEquals(361.0, bands.bandFor(LobbyPlayerRow.ROW_4).bottom, 0.0)
    }

    @Test
    fun invalidHeightOrAnchorIsRejected() {
        assertNull(LobbyPlayerRowBandCalculator.calculate(0.0, 0.0))
        assertNull(LobbyPlayerRowBandCalculator.calculate(360.0, -1.0))
        assertNull(LobbyPlayerRowBandCalculator.calculate(360.0, 361.0))
    }

    @Test
    fun rowBoundariesUseUpperExclusiveAndFinalBottomInclusiveRules() {
        val bands = requireNotNull(LobbyPlayerRowBandCalculator.calculate(360.0, 180.0))

        assertEquals(LobbyPlayerRow.ROW_2, bands.bandFor(90.0)?.row)
        assertEquals(LobbyPlayerRow.ROW_3, bands.bandFor(180.0)?.row)
        assertEquals(LobbyPlayerRow.ROW_4, bands.bandFor(360.0)?.row)
        assertNull(bands.bandFor(-0.001))
        assertNull(bands.bandFor(360.001))
    }
}
