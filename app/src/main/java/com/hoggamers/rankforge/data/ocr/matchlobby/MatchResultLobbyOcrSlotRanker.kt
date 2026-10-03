package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.matching.LobbyTeamSlotMatchCandidate
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchInput
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatchResult
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotMatcher
import com.hoggamers.rankforge.domain.matching.RowTeamMatchConfidenceAssessment
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyEvaluator
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTierClassifier
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestionProvider
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
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
    ): ResultLobbySlotMatchResult {
        val orderedEligibleSlots = eligibleTeamSlots.distinct().sorted()
        if (permanentTeamCandidates == null) {
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

        val selectedCandidates = orderedEligibleSlots.map { slotNumber ->
            permanentTeamCandidates
                .firstOrNull { candidate -> candidate.teamSlot == slotNumber }
                ?: TeamCandidateRosterInput(slotNumber, emptyList())
        }
        val allCandidates = allRosterTeamCandidates
            .distinctBy { candidate -> candidate.teamSlot }
            .ifEmpty { selectedCandidates }

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

        val resolvedLobbyCandidates = orderedLobbySlots.mapNotNull { lobbySlot ->
            resolvePermanentTeamSlot(
                lobbySlot = lobbySlot,
                eligibleTeamSlots = orderedEligibleSlots.toSet(),
                selectedCandidates = selectedCandidates,
                allCandidates = allCandidates,
            )?.let { permanentTeamSlot ->
                LobbyTeamSlotMatchCandidate(
                    teamSlotNumber = permanentTeamSlot,
                    playerNames = lobbySlot.players
                        .sortedBy { player -> player.playerNumber }
                        .map { player -> player.playerName },
                )
            }
        }
        val duplicateResolvedSlots = resolvedLobbyCandidates
            .groupingBy { candidate -> candidate.teamSlotNumber }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        return ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = resultRow.position,
                resultPlayerNames = resultPlayerNames(resultRow),
                lobbyCandidates = resolvedLobbyCandidates.filterNot { candidate ->
                    candidate.teamSlotNumber in duplicateResolvedSlots
                },
            ),
        )
    }

    private fun resolvePermanentTeamSlot(
        lobbySlot: MatchLobbyPlayersOcrSlot,
        eligibleTeamSlots: Set<Int>,
        selectedCandidates: List<TeamCandidateRosterInput>,
        allCandidates: List<TeamCandidateRosterInput>,
    ): Int? {
        val lobbyPlayerNames = lobbySlot.players
            .sortedBy { player -> player.playerNumber }
            .mapNotNull { player -> player.playerName?.takeIf { it.isNotBlank() } }
        if (lobbyPlayerNames.isEmpty()) return null

        val selectedSuggestion = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = lobbyPlayerNames,
            candidateTeams = selectedCandidates,
        )
        val selectedConfidence = TeamMatchConfidenceTierClassifier.classify(selectedSuggestion)
        val selectedSafety = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                RowTeamMatchConfidenceAssessment(
                    rowIndex = 0,
                    confidenceAssessment = selectedConfidence,
                ),
            ),
        ).rowResults.single()
        val selectedPermanentSlot = selectedSafety.proposedTeamSlot
            ?.takeIf { it in eligibleTeamSlots }
            ?.takeIf { selectedSafety.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT }
            ?: return null

        val allTopCandidate = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = lobbyPlayerNames,
            candidateTeams = allCandidates,
        ).suggestions.firstOrNull()?.teamCandidateScore
        if (
            allTopCandidate != null &&
            allTopCandidate.contributingMatchCount > 0 &&
            allTopCandidate.candidateTeamSlot !in eligibleTeamSlots
        ) {
            return null
        }

        return selectedPermanentSlot
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
