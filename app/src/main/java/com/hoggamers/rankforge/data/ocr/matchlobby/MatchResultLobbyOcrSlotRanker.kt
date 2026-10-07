package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.matching.LobbyTeamSlotMatchCandidate
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchInput
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchResult
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatcher
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestionProvider
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContext
import com.hoggamers.rankforge.domain.tournament.TeamSlot

/**
 * Adapts the current match's completed Result OCR row and Lobby OCR semantic slots into the
 * pure Result-to-Lobby matcher. This is intentionally post-OCR and performs no I/O or mutation.
 */
object MatchResultLobbyOcrSlotRanker {
    fun rank(
        resultRow: MatchResultOcrRow,
        lobbyOcrResult: MatchLobbyPlayersOcrResult,
        eligibleTeamSlots: Collection<Int> = TeamSlot.SLOT_NUMBERS.toList(),
        permanentTeamCandidates: Collection<TeamCandidateRosterInput>? = null,
        allRosterTeamCandidates: Collection<TeamCandidateRosterInput> =
            permanentTeamCandidates.orEmpty(),
        matchIdentityContext: MatchTeamIdentityContext? = null,
    ): ResultLobbySlotMatchResult {
        val orderedEligibleSlots = eligibleTeamSlots.distinct().sorted()
        if (matchIdentityContext == null) {
            require(orderedEligibleSlots == TeamSlot.SLOT_NUMBERS.toList()) {
                "Standard lobby matching requires the twelve local team slots."
            }
            return ResultLobbySlotMatcher.rank(
                ResultLobbySlotMatchInput(
                    resultPosition = resultRow.position,
                    resultPlayerNames = resultPlayerNames(resultRow),
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
        require(orderedEligibleSlots.size == TeamSlot.SLOT_NUMBERS.count()) {
            "A match must expose exactly twelve eligible permanent team slots."
        }
        require(orderedEligibleSlots.all { it in TeamSlot.TOURNAMENT_SLOT_NUMBERS }) {
            "Eligible permanent team slots must be between 1 and 24."
        }
        val orderedLobbySlots = lobbyOcrResult.slots.sortedBy { it.slotNumber }
        require(orderedLobbySlots.size == TeamSlot.SLOT_NUMBERS.count()) {
            "Lobby OCR must expose exactly twelve local slots."
        }
        require(orderedLobbySlots.map { it.slotNumber }.distinct().size == orderedLobbySlots.size) {
            "Lobby OCR local slot numbers must be unique."
        }
        require(orderedLobbySlots.all { it.slotNumber in TeamSlot.SLOT_NUMBERS }) {
            "Lobby OCR local slot numbers must be between 1 and 12."
        }

        val identityContext = matchIdentityContext
        require(identityContext.eligibleTeamSlotNumbers == orderedEligibleSlots.toSet()) {
            "Lobby matching requires the persisted identity context for the selected team slots."
        }

        val rosterCandidates = allRosterTeamCandidates
            .distinctBy { candidate -> candidate.teamSlot }
        val resolvedLobbyCandidates = orderedLobbySlots.mapNotNull { lobbySlot ->
            val canonicalTeamSlot = requireNotNull(
                identityContext.canonicalTeamSlotForLobby(lobbySlot.slotNumber),
            ) {
                "Persisted identity context is missing lobby slot ${lobbySlot.slotNumber}."
            }
            val lobbyPlayerNames = lobbySlot.players
                .sortedBy { player -> player.playerNumber }
                .mapNotNull { player -> player.playerName?.takeIf { it.isNotBlank() } }
            val rosterTopCandidate = if (lobbyPlayerNames.isNotEmpty() && rosterCandidates.isNotEmpty()) {
                TopTeamCandidateSuggestionProvider.suggestTopThree(
                    detectedPlayerNames = lobbyPlayerNames,
                    candidateTeams = rosterCandidates,
                ).suggestions.firstOrNull()?.teamCandidateScore
            } else {
                null
            }
            if (
                rosterTopCandidate != null &&
                rosterTopCandidate.contributingMatchCount > 0 &&
                rosterTopCandidate.candidateTeamSlot != canonicalTeamSlot
            ) {
                // The persisted lineup is authoritative. A roster hit for another permanent
                // team is contradictory evidence, so leave this local slot out of matching
                // instead of silently replacing the out-of-group team with an eligible one.
                return@mapNotNull null
            }
            LobbyTeamSlotMatchCandidate(
                teamSlotNumber = canonicalTeamSlot,
                playerNames = lobbySlot.players
                    .sortedBy { player -> player.playerNumber }
                    .map { player -> player.playerName },
            )
        }

        return ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = resultRow.position,
                resultPlayerNames = resultPlayerNames(resultRow),
                lobbyCandidates = resolvedLobbyCandidates,
            ),
        )
    }

    private fun resultPlayerNames(resultRow: MatchResultOcrRow): List<String?> =
        (1..4).map { logicalPlayerSlot ->
            resultRow.playerSlots
                .firstOrNull { playerSlot -> playerSlot.slot == logicalPlayerSlot }
                ?.player
                ?.let { player ->
                    player.resolvedText
                        .ifBlank { player.ocrText }
                        .takeIf { playerName -> playerName.isNotBlank() }
                }
        }
}
