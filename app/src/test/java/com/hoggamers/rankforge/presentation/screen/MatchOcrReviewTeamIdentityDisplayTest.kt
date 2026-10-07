package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MatchOcrReviewTeamIdentityDisplayTest {
    @Test
    fun groupRotationDisplaysPairRelativeLobbyAndTeamName() {
        val label = displayTeamIdentityLabel(
            teamSlot = 18,
            teamNamesBySlot = mapOf(18 to "Phoenix"),
            lobbySlotByTeamSlot = mapOf(18 to 3),
            usesPairRelativeIdentity = true,
        )

        assertEquals("Lobby 03 \u00B7 Phoenix", label)
        assertFalse(label.contains("Slot 18"))
        assertFalse(label.contains("\u00C2\u00B7"))
    }

    @Test
    fun standardIdentityDisplayRemainsCanonicalSlotBased() {
        assertEquals(
            "Phoenix",
            displayTeamIdentityLabel(
                teamSlot = 18,
                teamNamesBySlot = mapOf(18 to "Phoenix"),
                lobbySlotByTeamSlot = mapOf(18 to 3),
                usesPairRelativeIdentity = false,
            ),
        )
        assertEquals(
            "Slot 18",
            displayTeamIdentityLabel(
                teamSlot = 18,
                teamNamesBySlot = emptyMap(),
                lobbySlotByTeamSlot = emptyMap(),
                usesPairRelativeIdentity = false,
            ),
        )
    }
}
