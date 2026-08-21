package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSlotParticipationTest {
    @Test
    fun sparseNamesKeepEveryNonBlankStructuralSlotActive() {
        val participation = mapOf(
            1 to "Team 1",
            2 to "Team 2",
            3 to "Team 3",
            4 to "Team 4",
            5 to "Team 5",
            6 to "Team 6",
            7 to " ",
            8 to "Team 8",
            9 to "Team 9",
            10 to "",
            11 to "Team 11",
            12 to "Team 12",
        ).analyzeTeamSlotParticipation()

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 8, 9, 11, 12), participation.activeSlotNumbers)
        assertEquals(10, participation.activeCount)
        assertTrue(participation.hasGap)
        assertTrue(participation.isReadyForMatchCreation)
    }

    @Test
    fun whitespaceOnlyNamesAreInactiveAndLeadingGapsAreAllowed() {
        val participation = mapOf(
            2 to "Team 2",
            5 to "Team 5",
            12 to "Team 12",
        ).analyzeTeamSlotParticipation()

        assertEquals(listOf(2, 5, 12), participation.activeSlotNumbers)
        assertEquals(3, participation.activeCount)
        assertTrue(participation.hasGap)
        assertTrue(participation.isReadyForMatchCreation)
    }

    @Test
    fun contiguousAndFullParticipationRemainReadyWithoutGaps() {
        val contiguous = (1..10).associateWith { "Team $it" }.analyzeTeamSlotParticipation()
        val full = (1..12).associateWith { "Team $it" }.analyzeTeamSlotParticipation()

        assertEquals((1..10).toList(), contiguous.activeSlotNumbers)
        assertFalse(contiguous.hasGap)
        assertTrue(contiguous.isReadyForMatchCreation)
        assertEquals((1..12).toList(), full.activeSlotNumbers)
        assertFalse(full.hasGap)
        assertTrue(full.isReadyForMatchCreation)
    }
}
