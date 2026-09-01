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

        val decoded = codec.decode(codec.encode(expected))

        assertEquals(expected, decoded)
    }

    @Test
    fun malformedJsonIsRejectedAsCacheMiss() {
        assertNull(codec.decode("not-json"))
    }

    @Test
    fun outOfRangeSlotIsRejectedAsCacheMiss() {
        val payload = codec.encode(slots(RosterScreenshotPosition.ONE).map { slot ->
            if (slot.slotNumber == 1) slot.copy(slotNumber = 9) else slot
        })

        assertNull(codec.decode(payload))
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

        assertNull(codec.decode(payload))
    }

    @Test
    fun swappedSemanticGroupIsAcceptedWithoutPhysicalPositionValidation() {
        val expected = slots(5..8)

        assertEquals(expected, codec.decode(codec.encode(expected)))
    }

    @Test
    fun versionOnePayloadIsRejectedAsCacheMiss() {
        val payload = codec.encode(slots(1..4)).replace("\"payloadVersion\":2", "\"payloadVersion\":1")

        assertNull(codec.decode(payload))
    }

    @Test
    fun partialSameGroupSlotsRoundTripAndMixedGroupsAreRejected() {
        val partial = slots(1..4).take(3)

        assertEquals(partial, codec.decode(codec.encode(partial)))
        assertNull(codec.decode(codec.encode(slots(1..4) + slots(5..5))))
    }

    @Test
    fun duplicateOrMixedSemanticSlotsAreRejectedAsCacheMiss() {
        val duplicate = codec.encode(slots(1..4).map { slot ->
            if (slot.slotNumber == 2) slot.copy(slotNumber = 1) else slot
        })
        val mixed = codec.encode(slots(1..4).map { slot ->
            if (slot.slotNumber == 4) slot.copy(slotNumber = 8) else slot
        })

        assertNull(codec.decode(duplicate))
        assertNull(codec.decode(mixed))
    }

    @Test
    fun slotZeroOrThirteenAreRejectedAsCacheMiss() {
        listOf(0, 13).forEach { invalidSlot ->
            val payload = codec.encode(slots(1..4).map { slot ->
                if (slot.slotNumber == 1) slot.copy(slotNumber = invalidSlot) else slot
            })

            assertNull(codec.decode(payload))
        }
    }

    @Test
    fun missingOrOutOfRangePlayerNumberIsRejectedAsCacheMiss() {
        val payload = codec.encode(slots(1..4).map { slot ->
            if (slot.slotNumber == 1) {
                slot.copy(players = slot.players.map { player ->
                    if (player.playerNumber == 4) player.copy(playerNumber = 5) else player
                })
            } else {
                slot
            }
        })

        assertNull(codec.decode(payload))
    }

    @Test
    fun decodedSlotsAndPlayersAreSortedDeterministically() {
        val expected = slots(5..8)
        val unsorted = expected.reversed().map { slot -> slot.copy(players = slot.players.reversed()) }

        assertEquals(expected, codec.decode(codec.encode(unsorted)))
    }

    private fun slots(position: RosterScreenshotPosition) = slots(position.tournamentSlotRange)

    private fun slots(slotNumberRange: IntRange) = slotNumberRange.map { slotNumber ->
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
