package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GroupRotationPairingLobbySlotMappersTest {
    private val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)

    @Test
    fun mapsDomainToRoomAndBackWithoutChangingEitherIdentity() {
        val domain = GroupRotationPairingLobbySlot(
            tournamentId = "tournament-1",
            pairing = pairing,
            lobbySlotNumber = 7,
            teamSlotNumber = 17,
        )

        val entity = domain.toEntity()

        assertEquals("tournament-1", entity.tournamentId)
        assertEquals("A:C", entity.pairingKey)
        assertEquals(7, entity.lobbySlotNumber)
        assertEquals(17, entity.teamSlotNumber)
        assertEquals(domain, entity.toDomain(pairing))
    }

    @Test
    fun rejectsMismatchedPairingDuringRoomToDomainMapping() {
        val entity = GroupRotationPairingLobbySlot(
            tournamentId = "tournament-1",
            pairing = pairing,
            lobbySlotNumber = 1,
            teamSlotNumber = 13,
        ).toEntity()

        assertThrows(IllegalArgumentException::class.java) {
            entity.toDomain(GroupPairing(TournamentGroup.A, TournamentGroup.B))
        }
    }
}
