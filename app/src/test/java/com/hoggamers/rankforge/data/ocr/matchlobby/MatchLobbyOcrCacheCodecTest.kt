package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchLobbyOcrCacheCodecTest {
    private val codec = MatchLobbyOcrCacheCodec()

    @Test
    fun roundTripReconstructsExpectedSlotsAndPlayers() {
        val expected = slots(RosterScreenshotPosition.THREE)

        val decoded = codec.decode(codec.encode(expected), RosterScreenshotPosition.THREE)

        assertEquals(expected, decoded)
    }

    @Test
    fun malformedJsonIsRejectedAsCacheMiss() {
        assertNull(codec.decode("not-json", RosterScreenshotPosition.ONE))
    }

    @Test
    fun outOfRangeSlotIsRejectedAsCacheMiss() {
        val payload = codec.encode(slots(RosterScreenshotPosition.ONE).map { slot ->
            if (slot.slotNumber == 1) slot.copy(slotNumber = 9) else slot
        })

        assertNull(codec.decode(payload, RosterScreenshotPosition.ONE))
    }

    @Test
    fun duplicatePlayerNumberIsRejectedAsCacheMiss() {
        val payload = codec.encode(slots(RosterScreenshotPosition.ONE).map { slot ->
            if (slot.slotNumber == 1) {
                slot.copy(players = slot.players.map { player ->
                    if (player.playerNumber == 2) player.copy(playerNumber = 1) else player
                })
            } else {
                slot
            }
        })

        assertNull(codec.decode(payload, RosterScreenshotPosition.ONE))
    }

    @Test
    fun payloadForAnotherPositionIsRejectedAsCacheMiss() {
        val payload = codec.encode(slots(RosterScreenshotPosition.TWO))

        assertNull(codec.decode(payload, RosterScreenshotPosition.ONE))
    }

    private fun slots(position: RosterScreenshotPosition) = position.tournamentSlotRange.map { slotNumber ->
        MatchLobbyPlayersOcrSlot(
            slotNumber = slotNumber,
            players = (1..4).map { playerNumber ->
                MatchLobbyPlayersOcrPlayer(
                    playerNumber = playerNumber,
                    playerName = "slot-$slotNumber-player-$playerNumber",
                )
            },
        )
    }
}
