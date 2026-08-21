package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatchParticipantResultTest {
    @Test
    fun participatedResultRequiresPositivePlacementAndPreservesScores() {
        val result = MatchParticipantResult(
            teamSlotNumber = 1,
            participationStatus = MatchParticipationStatus.PARTICIPATED,
            placement = 1,
            kills = 5,
        )

        assertEquals(1, result.placement)
        assertEquals(12, result.placementPoints)
        assertEquals(5, result.killPoints)
        assertEquals(17, result.totalPoints)
    }

    @Test
    fun noShowHasNullablePlacementZeroKillsAndZeroScores() {
        val result = MatchParticipantResult(
            teamSlotNumber = 7,
            participationStatus = MatchParticipationStatus.NO_SHOW,
            placement = null,
            kills = 0,
        )

        assertEquals(null, result.placement)
        assertEquals(0, result.kills)
        assertEquals(0, result.placementPoints)
        assertEquals(0, result.killPoints)
        assertEquals(0, result.totalPoints)
    }

    @Test
    fun noShowCannotHavePlacement() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchParticipantResult(
                teamSlotNumber = 7,
                participationStatus = MatchParticipationStatus.NO_SHOW,
                placement = 11,
                kills = 0,
            )
        }
    }

    @Test
    fun noShowCannotHaveKills() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchParticipantResult(
                teamSlotNumber = 7,
                participationStatus = MatchParticipationStatus.NO_SHOW,
                placement = null,
                kills = 1,
            )
        }
    }

    @Test
    fun participatedCannotHaveNullablePlacement() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchParticipantResult(
                teamSlotNumber = 1,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = null,
                kills = 0,
            )
        }
    }
}
