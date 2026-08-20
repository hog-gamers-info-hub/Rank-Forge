package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultLobbySlotMatcherTest {
    @Test
    fun rank_exactFourPlayerMatchRanksSemanticLobbySlotFirst() {
        val result = rank(
            resultPosition = 4,
            resultPlayers = exactPlayers,
            lobbyOverrides = mapOf(4 to exactPlayers),
        )

        assertEquals(4, result.resultPosition)
        assertEquals(exactPlayers, result.resultPlayerNames)
        assertEquals(12, result.rankedCandidates.evaluatedCandidateCount)
        assertEquals(4, topScore(result).candidateTeamSlot)
        assertEquals(4, topScore(result).contributingMatchCount)
        assertEquals(100, topScore(result).confidenceScore)
    }

    @Test
    fun rank_resultPlayerOrderDoesNotChangeWinningLobbySlot() {
        val result = rank(
            resultPosition = 7,
            resultPlayers = listOf("DeltaKing", "BravoFox", "AlphaWolf", "CharlieRay"),
            lobbyOverrides = mapOf(4 to exactPlayers),
        )

        assertEquals(4, topScore(result).candidateTeamSlot)
        assertEquals(4, topScore(result).contributingMatchCount)
        assertEquals(100, topScore(result).confidenceScore)
    }

    @Test
    fun rank_reusesExistingOcrTolerantNameMatching() {
        val result = rank(
            resultPosition = 2,
            resultPlayers = listOf("PLAYER0NE", "NOVA", "RIN", "KAI"),
            lobbyOverrides = mapOf(
                9 to listOf("PLAYERONE", "NOVA", "RIN", "KAI"),
            ),
        )

        assertEquals(9, topScore(result).candidateTeamSlot)
        assertEquals(4, topScore(result).contributingMatchCount)
        assertEquals(100, topScore(result).confidenceScore)
    }

    @Test
    fun rank_oneChangedPlayerStillRanksThreeStrongLobbyMatchesFirst() {
        val result = rank(
            resultPosition = 5,
            resultPlayers = listOf("Unit7", "Nova", "Rin", "NewWolf"),
            lobbyOverrides = mapOf(
                6 to listOf("Unit7", "Nova", "Rin", "OldWolf"),
            ),
        )

        assertEquals(6, topScore(result).candidateTeamSlot)
        assertEquals(3, topScore(result).contributingMatchCount)
        assertTrue(topScore(result).confidenceScore > secondScore(result).confidenceScore)
    }

    @Test
    fun rank_missingResultPlayerUsesOnlyAvailableEvidence() {
        val result = rank(
            resultPosition = 8,
            resultPlayers = listOf("Unit7", "Nova", null, "Kai"),
            lobbyOverrides = mapOf(
                3 to listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
        )

        assertEquals(3, topScore(result).candidateTeamSlot)
        assertEquals(3, topScore(result).validDetectedPlayerCount)
        assertEquals(3, topScore(result).contributingMatchCount)
    }

    @Test
    fun rank_missingLobbyEvidenceReturnsNoCandidatesWithoutFailingResultEvidence() {
        val result = ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = 10,
                resultPlayerNames = exactPlayers,
                lobbyCandidates = emptyList(),
            ),
        )

        assertEquals(10, result.resultPosition)
        assertEquals(exactPlayers, result.resultPlayerNames)
        assertEquals(0, result.rankedCandidates.evaluatedCandidateCount)
        assertTrue(result.rankedCandidates.suggestions.isEmpty())
    }

    @Test
    fun rank_supportsSemanticLobbySlotTwelve() {
        val result = rank(
            resultPosition = 1,
            resultPlayers = exactPlayers,
            lobbyOverrides = mapOf(12 to exactPlayers),
        )

        assertEquals(12, topScore(result).candidateTeamSlot)
        assertEquals(4, topScore(result).contributingMatchCount)
        assertEquals(100, topScore(result).confidenceScore)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rank_rejectsResultRowsWithoutFourPlayerSlots() {
        ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = 1,
                resultPlayerNames = listOf("A", "B", "C"),
                lobbyCandidates = emptyList(),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rank_rejectsLobbyCandidatesWithoutFourPlayerSlots() {
        ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = 1,
                resultPlayerNames = exactPlayers,
                lobbyCandidates = listOf(
                    LobbyTeamSlotMatchCandidate(
                        teamSlotNumber = 1,
                        playerNames = listOf("A", "B", "C"),
                    ),
                ),
            ),
        )
    }

    private fun rank(
        resultPosition: Int,
        resultPlayers: List<String?>,
        lobbyOverrides: Map<Int, List<String?>>,
    ): ResultLobbySlotMatchResult = ResultLobbySlotMatcher.rank(
        ResultLobbySlotMatchInput(
            resultPosition = resultPosition,
            resultPlayerNames = resultPlayers,
            lobbyCandidates = (1..12).map { teamSlot ->
                LobbyTeamSlotMatchCandidate(
                    teamSlotNumber = teamSlot,
                    playerNames = lobbyOverrides[teamSlot] ?: unrelatedPlayers(teamSlot),
                )
            },
        ),
    )

    private fun topScore(result: ResultLobbySlotMatchResult): TeamCandidateScore =
        result.rankedCandidates.suggestions.first().teamCandidateScore

    private fun secondScore(result: ResultLobbySlotMatchResult): TeamCandidateScore =
        result.rankedCandidates.suggestions[1].teamCandidateScore

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
    }
}
