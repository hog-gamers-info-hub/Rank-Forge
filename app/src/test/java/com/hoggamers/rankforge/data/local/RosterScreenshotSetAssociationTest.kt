package com.hoggamers.rankforge.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RosterScreenshotSetAssociationTest {
    @Test
    fun orderedRosterScreenshotPositionsMapToTheirIntendedSlotRanges() {
        assertEquals(RosterScreenshotSlotRange(1, 4), rosterScreenshotSlotRange(1))
        assertEquals(RosterScreenshotSlotRange(5, 8), rosterScreenshotSlotRange(2))
        assertEquals(RosterScreenshotSlotRange(9, 12), rosterScreenshotSlotRange(3))
    }

    @Test
    fun unsupportedRosterScreenshotPositionsHaveNoSlotRange() {
        assertNull(rosterScreenshotSlotRange(0))
        assertNull(rosterScreenshotSlotRange(4))
    }
}
