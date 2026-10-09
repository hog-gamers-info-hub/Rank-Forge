package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GroupRotationPairingLobbySlotTest {
    private val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)

    @Test
    fun acceptsLobbySlotBoundaries() {
        assertEquals(
            1,
            GroupRotationPairingLobbySlot(
                tournamentId = "tournament-1",
                pairing = pairing,
                lobbySlotNumber = 1,
                teamSlotNumber = 1,
            ).lobbySlotNumber,
        )
        assertEquals(
            12,
            GroupRotationPairingLobbySlot(
                tournamentId = "tournament-1",
                pairing = pairing,
                lobbySlotNumber = 12,
                teamSlotNumber = 24,
            ).lobbySlotNumber,
        )
    }

    @Test
    fun rejectsLobbySlotOutsideLocalRange() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupRotationPairingLobbySlot("tournament-1", pairing, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupRotationPairingLobbySlot("tournament-1", pairing, 13, 1)
        }
    }

    @Test
    fun acceptsPermanentTeamSlotBoundaries() {
        assertEquals(
            1,
            GroupRotationPairingLobbySlot("tournament-1", pairing, 1, 1).teamSlotNumber,
        )
        assertEquals(
            24,
            GroupRotationPairingLobbySlot("tournament-1", pairing, 12, 24).teamSlotNumber,
        )
    }

    @Test
    fun rejectsPermanentTeamSlotOutsideTournamentRange() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupRotationPairingLobbySlot("tournament-1", pairing, 1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupRotationPairingLobbySlot("tournament-1", pairing, 1, 25)
        }
    }

    @Test
    fun rejectsBlankTournamentId() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupRotationPairingLobbySlot(" ", pairing, 1, 1)
        }
    }
}
