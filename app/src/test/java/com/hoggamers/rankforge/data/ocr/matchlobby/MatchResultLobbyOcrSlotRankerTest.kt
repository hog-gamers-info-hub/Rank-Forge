package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultLobbyOcrSlotRankerTest {
    @Test
    fun rank_usesCurrentLobbyOcrSemanticSlotTwelveAsCandidateIdentity() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 4,
                playerNames = exactPlayers,
            ),
            lobbyOcrResult = lobbyResult(
                overrides = mapOf(12 to exactPlayers),
            ),
        )

        val topScore = result.rankedCandidates.suggestions.first().teamCandidateScore
        assertEquals(4, result.resultPosition)
        assertEquals(12, result.rankedCandidates.evaluatedCandidateCount)
        assertEquals(12, topScore.candidateTeamSlot)
        assertEquals(4, topScore.contributingMatchCount)
        assertEquals(100, topScore.confidenceScore)
    }

    @Test
    fun rank_readsResultPlayerNamesFromExistingResultOcrFieldsOnly() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 7,
                playerNames = listOf("PLAYER0NE", "NOVA", "RIN", "KAI"),
            ),
            lobbyOcrResult = lobbyResult(
                overrides = mapOf(
                    5 to listOf("PLAYERONE", "NOVA", "RIN", "KAI"),
                ),
            ),
        )

        val topScore = result.rankedCandidates.suggestions.first().teamCandidateScore
        assertEquals(listOf("PLAYER0NE", "NOVA", "RIN", "KAI"), result.resultPlayerNames)
        assertEquals(5, topScore.candidateTeamSlot)
        assertEquals(4, topScore.contributingMatchCount)
        assertEquals(100, topScore.confidenceScore)
    }

    @Test
    fun rank_unavailableLobbyPlayersContributeNoFabricatedMatches() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 10,
                playerNames = exactPlayers,
            ),
            lobbyOcrResult = MatchLobbyPlayersOcrResult.unavailable(),
        )

        assertEquals(12, result.rankedCandidates.evaluatedCandidateCount)
        assertEquals(3, result.rankedCandidates.suggestions.size)
        assertTrue(result.rankedCandidates.suggestions.all { suggestion ->
            suggestion.teamCandidateScore.contributingMatchCount == 0 &&
                suggestion.teamCandidateScore.confidenceScore == 0
        })
    }

    private fun lobbyResult(
        overrides: Map<Int, List<String?>>,
    ): MatchLobbyPlayersOcrResult = MatchLobbyPlayersOcrResult(
        slots = (1..12).map { teamSlot ->
            val playerNames = overrides[teamSlot] ?: unrelatedPlayers(teamSlot)
            MatchLobbyPlayersOcrSlot(
                slotNumber = teamSlot,
                players = playerNames.mapIndexed { index, playerName ->
                    MatchLobbyPlayersOcrPlayer(
                        playerNumber = index + 1,
                        playerName = playerName,
                    )
                },
            )
        },
    )

    private fun resultRow(
        position: Int,
        playerNames: List<String?>,
    ): MatchResultOcrRow = MatchResultOcrRow(
        position = position,
        source = MatchResultOcrRowSource.UPPER_TEMPLATE,
        placement = field(
            id = "placement-$position",
            type = MatchResultOcrFieldType.PLACEMENT,
            position = position,
            slot = null,
            text = position.toString(),
            status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
        ),
        playerSlots = playerNames.mapIndexed { index, playerName ->
            val playerSlot = index + 1
            MatchResultOcrPlayerSlot(
                slot = playerSlot,
                player = field(
                    id = "player-$position-$playerSlot",
                    type = MatchResultOcrFieldType.PLAYER,
                    position = position,
                    slot = playerSlot,
                    text = playerName.orEmpty(),
                    status = if (playerName.isNullOrBlank()) {
                        MatchResultOcrFieldStatus.EMPTY
                    } else {
                        MatchResultOcrFieldStatus.DIRECT_TEXT
                    },
                ),
                kill = field(
                    id = "kill-$position-$playerSlot",
                    type = MatchResultOcrFieldType.KILL,
                    position = position,
                    slot = playerSlot,
                    text = "0",
                    status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
                ),
            )
        },
    )

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        slot: Int?,
        text: String,
        status: MatchResultOcrFieldStatus,
    ): MatchResultOcrField = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = null,
        slot = slot,
        canonicalRect = EMPTY_RECT,
        mappedRect = EMPTY_RECT,
        ocrText = text,
        resolvedText = text,
        status = status,
    )

    private fun unrelatedPlayers(teamSlot: Int): List<String?> = listOf(
        "ZZ${teamSlot}Quartz",
        "YY${teamSlot}Vex",
        "XX${teamSlot}Mirth",
        "WW${teamSlot}Pond",
    )

    private companion object {
        val exactPlayers = listOf<String?>(
            "AlphaWolf",
            "BravoFox",
            "CharlieRay",
            "DeltaKing",
        )
        val EMPTY_RECT = MatchResultOcrRect(
            left = 0.0,
            top = 0.0,
            right = 0.0,
            bottom = 0.0,
        )
    }
}
