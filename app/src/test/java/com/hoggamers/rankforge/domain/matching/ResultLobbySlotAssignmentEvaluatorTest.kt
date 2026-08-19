package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultLobbySlotAssignmentEvaluatorTest {
    @Test
    fun evaluate_exactFourPlayerMatchProducesSafeAutomaticLobbySlot() {
        val matchResult = rank(
            resultPosition = 4,
            resultPlayers = teamPlayers(4),
            lobbyOverrides = mapOf(4 to teamPlayers(4)),
        )

        val row = evaluate(matchResult).rows.single()

        assertEquals(4, row.resultPosition)
        assertEquals(TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE, row.confidenceAssessment.tier)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, row.assignmentSafety.safetyStatus)
        assertEquals(4, row.automaticAssignedTeamSlot)
    }

    @Test
    fun evaluate_resultPlayerOrderingDoesNotAffectSafeAssignment() {
        val row = evaluate(
            rank(
                resultPosition = 7,
                resultPlayers = listOf("Delta7", "Bravo7", "Alpha7", "Charlie7"),
                lobbyOverrides = mapOf(9 to teamPlayers(7)),
            ),
        ).rows.single()

        assertEquals(9, row.automaticAssignedTeamSlot)
        assertEquals(4, row.assignmentSafety.confidenceAssessment.selectedSuggestion
            ?.teamCandidateScore?.contributingMatchCount)
    }

    @Test
    fun evaluate_threeOfFourStrongMatchCanAutoAssignWhenExistingRulesPass() {
        val row = evaluate(
            rank(
                resultPosition = 5,
                resultPlayers = listOf("Alpha6", "Bravo6", "Charlie6", "New6"),
                lobbyOverrides = mapOf(6 to listOf("Alpha6", "Bravo6", "Charlie6", "Old6")),
            ),
        ).rows.single()

        assertEquals(92, topScore(row).confidenceScore)
        assertEquals(3, topScore(row).contributingMatchCount)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, row.assignmentSafety.safetyStatus)
        assertEquals(6, row.automaticAssignedTeamSlot)
    }

    @Test
    fun evaluate_twoPlayerEvidenceDoesNotAutoAssign() {
        val row = evaluate(
            rank(
                resultPosition = 3,
                resultPlayers = listOf("Alpha3", "Bravo3", "Missing3", "Unknown3"),
                lobbyOverrides = mapOf(3 to listOf("Alpha3", "Bravo3", "Other3", "Another3")),
            ),
        ).rows.single()

        assertEquals(2, topScore(row).contributingMatchCount)
        assertEquals(TeamMatchConfidenceTier.CONFIRMATION_REQUIRED, row.confidenceAssessment.tier)
        assertEquals(TeamAssignmentSafetyStatus.REVIEW_REQUIRED, row.assignmentSafety.safetyStatus)
        assertNull(row.automaticAssignedTeamSlot)
        assertTrue(row.assignmentSafety.reasons.contains(TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER))
    }

    @Test
    fun evaluateCandidateLeadBelowTenDoesNotAutoAssign() {
        val row = evaluate(
            rank(
                resultPosition = 1,
                resultPlayers = teamPlayers(1),
                lobbyOverrides = mapOf(
                    1 to teamPlayers(1),
                    2 to listOf("Alpha1", "Bravo1", "Charlie1", "Old1"),
                ),
            ),
        ).rows.single()

        assertEquals(100, topScore(row).confidenceScore)
        assertEquals(92, row.matchResult.rankedCandidates.suggestions[1]
            .teamCandidateScore.confidenceScore)
        assertEquals(TeamAssignmentSafetyStatus.REVIEW_REQUIRED, row.assignmentSafety.safetyStatus)
        assertNull(row.automaticAssignedTeamSlot)
        assertTrue(row.assignmentSafety.reasons.contains(TeamAssignmentSafetyReason.INSUFFICIENT_CANDIDATE_LEAD))
    }

    @Test
    fun evaluate_duplicateSafeLobbySlotBlocksBothRows() {
        val first = rank(
            resultPosition = 1,
            resultPlayers = teamPlayers(7),
            lobbyOverrides = mapOf(7 to teamPlayers(7)),
        )
        val second = rank(
            resultPosition = 2,
            resultPlayers = teamPlayers(7),
            lobbyOverrides = mapOf(7 to teamPlayers(7)),
        )

        val result = evaluate(listOf(second, first))

        assertEquals(listOf(1, 2), result.rows.map { it.resultPosition })
        result.rows.forEach { row ->
            assertEquals(TeamAssignmentSafetyStatus.REVIEW_REQUIRED, row.assignmentSafety.safetyStatus)
            assertTrue(row.assignmentSafety.reasons.contains(TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE))
            assertNull(row.automaticAssignedTeamSlot)
        }
    }

    @Test
    fun evaluateUnavailableLobbyEvidenceNeverGeneratesAutomaticAssignment() {
        val matchResult = ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = 10,
                resultPlayerNames = teamPlayers(10),
                lobbyCandidates = (1..12).map { teamSlot ->
                    LobbyTeamSlotMatchCandidate(
                        teamSlotNumber = teamSlot,
                        playerNames = listOf(null, null, null, null),
                    )
                },
            ),
        )

        val row = evaluate(matchResult).rows.single()

        assertEquals(TeamMatchConfidenceTier.MANUAL_REQUIRED, row.confidenceAssessment.tier)
        assertEquals(TeamAssignmentSafetyStatus.MANUAL_REQUIRED, row.assignmentSafety.safetyStatus)
        assertNull(row.automaticAssignedTeamSlot)
    }

    @Test
    fun evaluate_twelveUniqueStrongRowsProducesTwelveUniqueSafeAssignments() {
        val matchResults = (1..12).map { resultPosition ->
            rank(
                resultPosition = resultPosition,
                resultPlayers = teamPlayers(resultPosition),
                lobbyOverrides = (1..12).associateWith { teamSlot -> teamPlayers(teamSlot) },
            )
        }

        val result = evaluate(matchResults)

        assertEquals(12, result.rows.size)
        assertEquals(12, result.assignmentSafety.safeAssignmentCount)
        assertEquals((1..12).toList(), result.rows.map { it.automaticAssignedTeamSlot })
        assertTrue(result.rows.all {
            it.assignmentSafety.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
        })
    }

    @Test
    fun evaluate_inputOrderDoesNotChangeOutputOrderingOrDecisions() {
        val matchResults = listOf(
            rank(3, teamPlayers(3), mapOf(3 to teamPlayers(3))),
            rank(1, teamPlayers(1), mapOf(1 to teamPlayers(1))),
            rank(2, teamPlayers(2), mapOf(2 to teamPlayers(2))),
        )

        val ordered = evaluate(matchResults)
        val reversed = evaluate(matchResults.reversed())

        assertEquals(ordered, reversed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun evaluate_rejectsDuplicateResultPositions() {
        evaluate(
            listOf(
                rank(1, teamPlayers(1), emptyMap()),
                rank(1, teamPlayers(2), emptyMap()),
            ),
        )
    }

    @Test
    fun evaluate_preservesRankedCandidateEvidenceAndDoesNotMutateMatchResult() {
        val matchResult = rank(
            resultPosition = 6,
            resultPlayers = teamPlayers(6),
            lobbyOverrides = mapOf(6 to teamPlayers(6)),
        )
        val originalCandidates = matchResult.rankedCandidates
        val originalPlayers = matchResult.resultPlayerNames

        val row = evaluate(matchResult).rows.single()

        assertSame(matchResult, row.matchResult)
        assertSame(originalCandidates, row.matchResult.rankedCandidates)
        assertSame(originalPlayers, row.matchResult.resultPlayerNames)
        assertEquals(originalCandidates, matchResult.rankedCandidates)
        assertEquals(originalPlayers, matchResult.resultPlayerNames)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, row.assignmentSafety.safetyStatus)
    }

    private fun evaluate(
        matchResults: List<ResultLobbySlotMatchResult>,
    ): ResultLobbySlotAssignmentEvaluation =
        ResultLobbySlotAssignmentEvaluator.evaluate(matchResults)

    private fun evaluate(
        vararg matchResults: ResultLobbySlotMatchResult,
    ): ResultLobbySlotAssignmentEvaluation =
        evaluate(matchResults.toList())

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

    private fun topScore(row: ResultLobbySlotAssignmentRowResult): TeamCandidateScore =
        row.matchResult.rankedCandidates.suggestions.first().teamCandidateScore

    private fun teamPlayers(teamSlot: Int): List<String?> = listOf(
        "Alpha$teamSlot",
        "Bravo$teamSlot",
        "Charlie$teamSlot",
        "Delta$teamSlot",
    )

    private fun unrelatedPlayers(teamSlot: Int): List<String?> = listOf(
        "ZZ${teamSlot}Quartz",
        "YY${teamSlot}Vex",
        "XX${teamSlot}Mirth",
        "WW${teamSlot}Pond",
    )
}
