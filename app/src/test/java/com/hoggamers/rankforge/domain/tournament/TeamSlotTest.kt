package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TeamSlotTest {
    @Test
    fun fixedSlotsContainExactlyTwelveSlotsNumberedOneThroughTwelve() {
        val slots = TeamSlot.fixedSlotsForTournament("tournament-id")

        assertEquals(12, slots.size)
        assertEquals((1..12).toList(), slots.map { it.slotNumber })
        assertEquals(List(12) { "tournament-id" }, slots.map { it.tournamentId })
    }

    @Test
    fun invalidSlotNumbersAreRejected() {
        assertInvalidSlotNumber(0)
        assertInvalidSlotNumber(13)
    }

    @Test
    fun copyCannotCreateInvalidSlotNumber() {
        val slot = TeamSlot.create(
            tournamentId = "tournament-id",
            slotNumber = 1,
        )

        try {
            slot.copy(slotNumber = 13)
            fail("Expected copied invalid slot number to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun assertInvalidSlotNumber(slotNumber: Int) {
        try {
            TeamSlot.create(
                tournamentId = "tournament-id",
                slotNumber = slotNumber,
            )
            fail("Expected slot number $slotNumber to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
