package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RosterPlayerTest {
    @Test
    fun validConstructionPreservesTheSuppliedValues() {
        val player = RosterPlayer.create("tournament-id", 1, "Player One")

        assertEquals(RosterPlayer("tournament-id", 1, "Player One"), player)
    }

    @Test
    fun blankTournamentIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RosterPlayer.create(" ", 1, "Player One")
        }
    }

    @Test
    fun slotOneAndSlotTwelveAreAccepted() {
        assertEquals(1, RosterPlayer.create("tournament-id", 1, "Player One").slotNumber)
        assertEquals(12, RosterPlayer.create("tournament-id", 12, "Player Twelve").slotNumber)
    }

    @Test
    fun slotZeroAndSlotThirteenAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RosterPlayer.create("tournament-id", 0, "Player Zero")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RosterPlayer.create("tournament-id", 13, "Player Thirteen")
        }
    }
}
