package com.hoggamers.rankforge.domain.matching

import com.hoggamers.rankforge.domain.tournament.TeamSlot

data class LobbyTeamSlotMatchCandidate(
    val teamSlotNumber: Int,
    val playerNames: List<String?>,
)

data class ResultLobbySlotMatchInput(
    val resultPosition: Int,
    val resultPlayerNames: List<String?>,
    val lobbyCandidates: List<LobbyTeamSlotMatchCandidate>,
)

data class ResultLobbyPlayerSlotVoteEvidence(
    val resultPlayerSlot: Int,
    val teamSlot: Int,
    val bestSimilarityScore: Int,
)

data class ResultLobbySlotVoteScore(
    val teamSlot: Int,
    val supportingResultPlayerSlots: List<Int>,
    val voteCount: Int,
    val votePercent: Int,
)

enum class ResultLobbySlotDecisionStatus {
    AUTOMATIC,
    MANUAL,
}

enum class ResultLobbySlotDecisionReason {
    UNIQUE_VOTE_WINNER,
    SINGLE_STRONG_VOTE,
    NO_PLAUSIBLE_MATCH,
    TOP_VOTE_TIE,
    SINGLE_VOTE_BELOW_STRONG_THRESHOLD,
    DUPLICATE_SLOT_ACROSS_RESULT_ROWS,
}

data class ResultLobbySlotMatchResult(
    val resultPosition: Int,
    val resultPlayerNames: List<String?>,
    val playerSlotVoteEvidence: List<ResultLobbyPlayerSlotVoteEvidence>,
    val slotVoteScores: List<ResultLobbySlotVoteScore>,
    val proposedTeamSlot: Int?,
    val winningVotePercent: Int?,
    val decisionStatus: ResultLobbySlotDecisionStatus,
    val decisionReason: ResultLobbySlotDecisionReason,
    val automaticAssignedTeamSlot: Int?,
    // Compatibility projection retained for the existing OCR review integration.
    val rankedCandidates: TopTeamCandidateSuggestions,
)

/**
 * Pure Result-to-Lobby vote aggregation for one Result placement row.
 *
 * Each non-blank Result player contributes at most one vote to each Lobby slot,
 * based on that slot's best player-name similarity. The vote model is the
 * authoritative decision output; rankedCandidates remains only a compatibility
 * projection for the existing review presentation.
 */
object ResultLobbySlotMatcher {
    fun rank(input: ResultLobbySlotMatchInput): ResultLobbySlotMatchResult {
        require(input.resultPosition in TeamSlot.SLOT_NUMBERS) {
            "Result position must be between 1 and 12."
        }
        require(input.resultPlayerNames.size == PLAYER_SLOTS_PER_ROW) {
            "Result row must contain exactly four player slots."
        }
        require(input.lobbyCandidates.map { it.teamSlotNumber }.distinct().size == input.lobbyCandidates.size) {
            "Lobby team slot numbers must be unique."
        }

        input.lobbyCandidates.forEach { candidate ->
            require(candidate.teamSlotNumber in TeamSlot.SLOT_NUMBERS) {
                "Lobby team slot number must be between 1 and 12."
            }
            require(candidate.playerNames.size == PLAYER_SLOTS_PER_ROW) {
                "Lobby team slot must contain exactly four player slots."
            }
        }

        val voteEvidence = input.resultPlayerNames.mapIndexedNotNull { resultPlayerIndex, resultPlayerName ->
            val detectedName = resultPlayerName?.trim().orEmpty()
            if (detectedName.isBlank()) {
                return@mapIndexedNotNull null
            }

            input.lobbyCandidates.mapNotNull { candidate ->
                val bestSimilarityScore = candidate.playerNames
                    .mapNotNull { lobbyPlayerName ->
                        val rosterName = lobbyPlayerName?.trim().orEmpty()
                        if (rosterName.isBlank()) {
                            null
                        } else {
                            PlayerNameSimilarityMatcher.compare(
                                detectedName = detectedName,
                                rosterName = rosterName,
                            ).similarityScore
                        }
                    }
                    .maxOrNull()

                bestSimilarityScore
                    ?.takeIf { it >= PLAUSIBLE_MATCH_THRESHOLD }
                    ?.let {
                        ResultLobbyPlayerSlotVoteEvidence(
                            resultPlayerSlot = resultPlayerIndex + RESULT_PLAYER_SLOT_OFFSET,
                            teamSlot = candidate.teamSlotNumber,
                            bestSimilarityScore = it,
                        )
                    }
            }
        }.flatten()

        val slotVoteScores = input.lobbyCandidates
            .map { candidate ->
                val supportingResultPlayerSlots = voteEvidence
                    .filter { it.teamSlot == candidate.teamSlotNumber }
                    .map { it.resultPlayerSlot }
                    .distinct()
                    .sorted()
                ResultLobbySlotVoteScore(
                    teamSlot = candidate.teamSlotNumber,
                    supportingResultPlayerSlots = supportingResultPlayerSlots,
                    voteCount = supportingResultPlayerSlots.size,
                    votePercent = supportingResultPlayerSlots.size * VOTE_PERCENT_PER_PLAYER,
                )
            }
            .sortedBy { it.teamSlot }

        val highestVoteCount = slotVoteScores.maxOfOrNull { it.voteCount } ?: 0
        val topSlots = slotVoteScores.filter { it.voteCount == highestVoteCount && highestVoteCount > 0 }
        val highestVotePercent = (highestVoteCount * VOTE_PERCENT_PER_PLAYER).takeIf { highestVoteCount > 0 }
        val winningSlot = topSlots.singleOrNull()
        val winningEvidence = winningSlot?.let { slot ->
            voteEvidence.filter { it.teamSlot == slot.teamSlot }
        }
        val decision = when {
            winningSlot == null && topSlots.isEmpty() -> Decision(
                proposedTeamSlot = null,
                winningVotePercent = null,
                decisionStatus = ResultLobbySlotDecisionStatus.MANUAL,
                decisionReason = ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH,
                automaticAssignedTeamSlot = null,
            )
            winningSlot == null -> Decision(
                proposedTeamSlot = null,
                winningVotePercent = highestVotePercent,
                decisionStatus = ResultLobbySlotDecisionStatus.MANUAL,
                decisionReason = ResultLobbySlotDecisionReason.TOP_VOTE_TIE,
                automaticAssignedTeamSlot = null,
            )
            winningSlot.voteCount >= 2 -> Decision(
                proposedTeamSlot = winningSlot.teamSlot,
                winningVotePercent = winningSlot.votePercent,
                decisionStatus = ResultLobbySlotDecisionStatus.AUTOMATIC,
                decisionReason = ResultLobbySlotDecisionReason.UNIQUE_VOTE_WINNER,
                automaticAssignedTeamSlot = winningSlot.teamSlot,
            )
            winningEvidence.orEmpty().maxOf { it.bestSimilarityScore } >= SINGLE_STRONG_MATCH_THRESHOLD -> Decision(
                proposedTeamSlot = winningSlot.teamSlot,
                winningVotePercent = winningSlot.votePercent,
                decisionStatus = ResultLobbySlotDecisionStatus.AUTOMATIC,
                decisionReason = ResultLobbySlotDecisionReason.SINGLE_STRONG_VOTE,
                automaticAssignedTeamSlot = winningSlot.teamSlot,
            )
            else -> Decision(
                proposedTeamSlot = winningSlot.teamSlot,
                winningVotePercent = winningSlot.votePercent,
                decisionStatus = ResultLobbySlotDecisionStatus.MANUAL,
                decisionReason = ResultLobbySlotDecisionReason.SINGLE_VOTE_BELOW_STRONG_THRESHOLD,
                automaticAssignedTeamSlot = null,
            )
        }

        val rankedCandidates = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = input.resultPlayerNames.toList(),
            candidateTeams = input.lobbyCandidates.map { candidate ->
                TeamCandidateRosterInput(
                    teamSlot = candidate.teamSlotNumber,
                    rosterPlayerNames = candidate.playerNames.toList(),
                )
            },
        )

        return ResultLobbySlotMatchResult(
            resultPosition = input.resultPosition,
            resultPlayerNames = input.resultPlayerNames.toList(),
            playerSlotVoteEvidence = voteEvidence,
            slotVoteScores = slotVoteScores,
            proposedTeamSlot = decision.proposedTeamSlot,
            winningVotePercent = decision.winningVotePercent,
            decisionStatus = decision.decisionStatus,
            decisionReason = decision.decisionReason,
            automaticAssignedTeamSlot = decision.automaticAssignedTeamSlot,
            rankedCandidates = rankedCandidates,
        )
    }

    private data class Decision(
        val proposedTeamSlot: Int?,
        val winningVotePercent: Int?,
        val decisionStatus: ResultLobbySlotDecisionStatus,
        val decisionReason: ResultLobbySlotDecisionReason,
        val automaticAssignedTeamSlot: Int?,
    )

    private const val PLAYER_SLOTS_PER_ROW = 4
    private const val RESULT_PLAYER_SLOT_OFFSET = 1
    private const val PLAUSIBLE_MATCH_THRESHOLD = 65
    private const val SINGLE_STRONG_MATCH_THRESHOLD = 75
    private const val VOTE_PERCENT_PER_PLAYER = 25
}
