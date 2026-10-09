package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchCalculatedEvidenceCodecTest {
    private val codec = MatchCalculatedEvidenceCodec()

    @Test
    fun roundTripPreservesLobbyAndResultEvidenceExactly() {
        val lobby = LobbyCalculatedEvidence(
            teams = listOf(
                LobbyTeamCalculatedEvidence(
                    slotNumber = 11,
                    teamName = "Team 11",
                    sourceScreenshotIndex = 2,
                    cropLeft = 11.25,
                    cropTop = 22.5,
                    cropRight = 333.75,
                    cropBottom = 444.125,
                    playerNames = listOf("P1", "P2", null, "P4"),
                    teamSlotNumber = 17,
                ),
            ),
        )
        val result = ResultCalculatedEvidence(
            positions = listOf(
                ResultPositionCalculatedEvidence(
                    position = 12,
                    sourceScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    cropLeft = 11,
                    cropTop = 22,
                    cropRight = 333,
                    cropBottom = 444,
                    slotNumber = 11,
                    teamName = "Team 11",
                    playerNames = listOf("P1", null, "P3", "P4"),
                    playerKills = listOf(1, null, 3, 4),
                    totalKills = 8,
                    playerKillApplicable = listOf(true, false, true, true),
                ),
            ),
        )

        assertEquals(lobby, codec.decodeLobby(codec.encodeLobby(lobby)))
        assertEquals(result, codec.decodeResult(codec.encodeResult(result)))
    }

    @Test
    fun roundTripPreservesEmptyTwelveRowResultWorkingSet() {
        val result = ResultCalculatedEvidence(
            positions = (1..12).map { position ->
                ResultPositionCalculatedEvidence(
                    position = position,
                    playerNames = List(4) { "Not detected" },
                    placement = null,
                )
            },
        )

        assertEquals(result, codec.decodeResult(codec.encodeResult(result)))
    }

    @Test
    fun roundTripPreservesManuallyAddedPositions() {
        val result = ResultCalculatedEvidence(manuallyAddedPositions = listOf(11, 12))

        assertEquals(
            listOf(11, 12),
            codec.decodeResult(codec.encodeResult(result))?.manuallyAddedPositions,
        )
    }

    @Test
    fun legacyResultPayloadDefaultsToNoExcludedSourcePositions() {
        val payload = """
            {
              "positions": []
            }
        """.trimIndent()

        assertEquals(emptyList<Int>(), codec.decodeResult(payload)?.excludedSourcePositions)
        assertEquals(emptyList<Int>(), codec.decodeResult(payload)?.manuallyAddedPositions)
        assertEquals(
            MatchCalculatedEvidenceOrigin.AUTOMATIC,
            codec.decodeResult(payload)?.calculationOrigin,
        )
    }

    @Test
    fun legacyLobbyPayloadDefaultsCanonicalTeamIdentityToNull() {
        val payload = """
            {
              "teams": [{
                "slotNumber": 4,
                "teamName": "Legacy Team",
                "sourceScreenshotIndex": 1,
                "cropLeft": 1.0,
                "cropTop": 2.0,
                "cropRight": 3.0,
                "cropBottom": 4.0,
                "playerNames": ["P1", null, "P3", null]
              }]
            }
        """.trimIndent()

        assertEquals(null, codec.decodeLobby(payload)?.teams?.single()?.teamSlotNumber)
    }

    @Test
    fun roundTripPreservesManualCalculationOrigin() {
        val result = ResultCalculatedEvidence(
            calculationOrigin = MatchCalculatedEvidenceOrigin.MANUAL,
        )

        assertEquals(
            MatchCalculatedEvidenceOrigin.MANUAL,
            codec.decodeResult(codec.encodeResult(result))?.calculationOrigin,
        )
    }

    @Test
    fun roundTripPreservesExcludedSourcePositions() {
        val result = ResultCalculatedEvidence(
            positions = emptyList(),
            excludedSourcePositions = listOf(4, 9),
        )

        assertEquals(result, codec.decodeResult(codec.encodeResult(result)))
    }
}
