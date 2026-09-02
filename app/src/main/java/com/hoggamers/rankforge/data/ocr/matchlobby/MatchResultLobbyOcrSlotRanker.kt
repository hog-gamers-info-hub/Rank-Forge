package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.matching.LobbyTeamSlotMatchCandidate
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchInput
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchResult
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatcher
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow

/**
 * Adapts the current match's completed Result OCR row and Lobby OCR semantic slots into the
 * pure Result-to-Lobby matcher. This is intentionally post-OCR and performs no I/O or mutation.
 */
object MatchResultLobbyOcrSlotRanker {
    fun rank(
        resultRow: MatchResultOcrRow,
        lobbyOcrResult: MatchLobbyPlayersOcrResult,
    ): ResultLobbySlotMatchResult = ResultLobbySlotMatcher.rank(
        ResultLobbySlotMatchInput(
            resultPosition = resultRow.position,
            resultPlayerNames = (1..4).map { logicalPlayerSlot ->
                resultRow.playerSlots
                    .firstOrNull { playerSlot -> playerSlot.slot == logicalPlayerSlot }
                    ?.player
                    ?.let { player ->
                        player.resolvedText
                            .ifBlank { player.ocrText }
                            .takeIf { playerName -> playerName.isNotBlank() }
                    }
            },
            lobbyCandidates = lobbyOcrResult.slots
                .sortedBy { lobbySlot -> lobbySlot.slotNumber }
                .map { lobbySlot ->
                    LobbyTeamSlotMatchCandidate(
                        teamSlotNumber = lobbySlot.slotNumber,
                        playerNames = lobbySlot.players
                            .sortedBy { player -> player.playerNumber }
                            .map { player -> player.playerName },
                    )
                },
        ),
    )
}
