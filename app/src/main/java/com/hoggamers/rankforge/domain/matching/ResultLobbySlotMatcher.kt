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

data class ResultLobbySlotMatchResult(
    val resultPosition: Int,
    val resultPlayerNames: List<String?>,
    val rankedCandidates: TopTeamCandidateSuggestions,
)

/**
 * Pure post-OCR adapter that ranks semantic Lobby Team Slots for one Result placement row.
 *
 * This component does not read OCR geometry, assign a final Team Slot, apply assignment safety,
 * or mutate OCR evidence. Lobby OCR player names are adapted into the existing team-candidate
 * matching engine so its normalization, fuzzy similarity, one-to-one player matching, and
 * candidate ordering remain authoritative.
 */
object ResultLobbySlotMatcher {
    fun rank(input: ResultLobbySlotMatchInput): ResultLobbySlotMatchResult {
        require(input.resultPosition in TeamSlot.SLOT_NUMBERS) {
            "Result position must be between 1 and 12."
        }
        require(input.resultPlayerNames.size == PLAYER_SLOTS_PER_ROW) {
            "Result row must contain exactly four player slots."
        }

        input.lobbyCandidates.forEach { candidate ->
            require(candidate.teamSlotNumber in TeamSlot.SLOT_NUMBERS) {
                "Lobby team slot number must be between 1 and 12."
            }
            require(candidate.playerNames.size == PLAYER_SLOTS_PER_ROW) {
                "Lobby team slot must contain exactly four player slots."
            }
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
            rankedCandidates = rankedCandidates,
        )
    }

    private const val PLAYER_SLOTS_PER_ROW = 4
}
