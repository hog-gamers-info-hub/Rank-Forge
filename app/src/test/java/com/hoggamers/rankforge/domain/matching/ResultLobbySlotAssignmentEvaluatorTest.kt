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
    fun evaluate_twoPlayerEvidenceAutoAssignsWithUniqueVoteWinner() {
        val row = evaluate(
            rank(
                resultPosition = 3,
                resultPlayers = listOf("Alpha3", "Bravo3", "Missing3", "Unknown3"),
                lobbyOverrides = mapOf(3 to listOf("Alpha3", "Bravo3", "Other3", "Another3")),
            ),
        ).rows.single()

        assertEquals(2, topScore(row).contributingMatchCount)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, row.assignmentSafety.safetyStatus)
        assertEquals(3, row.automaticAssignedTeamSlot)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, row.decisionStatus)
    }

    @Test
    fun evaluateCandidateLeadDoesNotOverrideVoteWinner() {
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
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, row.assignmentSafety.safetyStatus)
        assertEquals(1, row.automaticAssignedTeamSlot)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, row.decisionStatus)
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
    fun evaluate_uniqueHighestDuplicateVoteKeepsOnlyTheStrongestAutomaticAssignment() {
        val weaker = rank(
            resultPosition = 9,
            resultPlayers = listOf("Alpha5", "R1", "R2", "R3"),
            lobbyOverrides = mapOf(5 to listOf("Alpha5", "Other5", "Other6", "Other7")),
        )
        val stronger = rank(
            resultPosition = 11,
            resultPlayers = listOf("Alpha5", "Bravo5", "Charlie5", "R4"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )

        val result = evaluate(listOf(weaker, stronger))
        val weakerRow = result.rows.single { it.resultPosition == 9 }
        val strongerRow = result.rows.single { it.resultPosition == 11 }

        assertEquals(25, weaker.winningVotePercent)
        assertEquals(75, stronger.winningVotePercent)
        assertNull(weakerRow.automaticAssignedTeamSlot)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, weakerRow.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS, weakerRow.decisionReason)
        assertEquals(TeamAssignmentSafetyStatus.REVIEW_REQUIRED, weakerRow.assignmentSafety.safetyStatus)
        assertEquals(5, strongerRow.automaticAssignedTeamSlot)
        assertEquals(stronger.decisionStatus, strongerRow.decisionStatus)
        assertEquals(stronger.decisionReason, strongerRow.decisionReason)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, strongerRow.assignmentSafety.safetyStatus)
    }

    @Test
    fun evaluate_uniqueHighestFiftyPercentDuplicateKeepsItsAutomaticAssignment() {
        val weaker = rank(
            resultPosition = 1,
            resultPlayers = listOf("Alpha5", "R1", "R2", "R3"),
            lobbyOverrides = mapOf(5 to listOf("Alpha5", "Other5", "Other6", "Other7")),
        )
        val stronger = rank(
            resultPosition = 2,
            resultPlayers = listOf("Alpha5", "Bravo5", "R4", "R5"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )

        val result = evaluate(listOf(weaker, stronger))

        assertEquals(25, weaker.winningVotePercent)
        assertEquals(50, stronger.winningVotePercent)
        assertNull(result.rows.single { it.resultPosition == 1 }.automaticAssignedTeamSlot)
        assertEquals(5, result.rows.single { it.resultPosition == 2 }.automaticAssignedTeamSlot)
    }

    @Test
    fun evaluateEqualFiftyPercentDuplicateLeavesBothRowsManual() {
        val first = rank(
            resultPosition = 1,
            resultPlayers = listOf("Alpha5", "Bravo5", "R1", "R2"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val second = rank(
            resultPosition = 2,
            resultPlayers = listOf("Alpha5", "Bravo5", "R3", "R4"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )

        val result = evaluate(listOf(first, second))

        assertEquals(50, first.winningVotePercent)
        assertEquals(50, second.winningVotePercent)
        result.rows.forEach { row ->
            assertNull(row.automaticAssignedTeamSlot)
            assertEquals(ResultLobbySlotDecisionStatus.MANUAL, row.decisionStatus)
            assertEquals(ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS, row.decisionReason)
        }
    }

    @Test
    fun evaluateEqualTwentyFivePercentDuplicateLeavesBothRowsManual() {
        val first = rank(
            resultPosition = 1,
            resultPlayers = listOf("Alpha5", "R1", "R2", "R3"),
            lobbyOverrides = mapOf(5 to listOf("Alpha5", "Other5", "Other6", "Other7")),
        )
        val second = rank(
            resultPosition = 2,
            resultPlayers = listOf("Alpha5", "R4", "R5", "R6"),
            lobbyOverrides = mapOf(5 to listOf("Alpha5", "Other5", "Other6", "Other7")),
        )

        val result = evaluate(listOf(first, second))

        assertEquals(25, first.winningVotePercent)
        assertEquals(25, second.winningVotePercent)
        assertTrue(result.rows.all { it.automaticAssignedTeamSlot == null })
        assertTrue(result.rows.all {
            it.decisionReason == ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS &&
                it.assignmentSafety.safetyStatus == TeamAssignmentSafetyStatus.REVIEW_REQUIRED
        })
    }

    @Test
    fun evaluateThreeDuplicateClaimantsKeepsOnlyUniqueHighestVote() {
        val highest = rank(
            resultPosition = 1,
            resultPlayers = listOf("Alpha5", "Bravo5", "Charlie5", "R1"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val middle = rank(
            resultPosition = 2,
            resultPlayers = listOf("Alpha5", "Bravo5", "R2", "R3"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val lowest = rank(
            resultPosition = 3,
            resultPlayers = listOf("Alpha5", "R4", "R5", "R6"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )

        val result = evaluate(listOf(highest, middle, lowest))

        assertEquals(5, result.rows.single { it.resultPosition == 1 }.automaticAssignedTeamSlot)
        assertTrue(result.rows.filter { it.resultPosition != 1 }.all {
            it.automaticAssignedTeamSlot == null &&
                it.decisionReason == ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
        })
    }

    @Test
    fun evaluateThreeDuplicateClaimantsWithTopPercentageTieLeavesAllManual() {
        val first = rank(
            resultPosition = 1,
            resultPlayers = listOf("Alpha5", "Bravo5", "Charlie5", "R1"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val second = rank(
            resultPosition = 2,
            resultPlayers = listOf("Alpha5", "Bravo5", "Charlie5", "R2"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val third = rank(
            resultPosition = 3,
            resultPlayers = listOf("Alpha5", "R3", "R4", "R5"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )

        val result = evaluate(listOf(first, second, third))

        assertTrue(result.rows.all {
            it.automaticAssignedTeamSlot == null &&
                it.decisionReason == ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
        })
    }

    @Test
    fun evaluateResolvesIndependentDuplicateGroupsSeparately() {
        val slotFiveWinner = rank(
            resultPosition = 1,
            resultPlayers = listOf("Alpha5", "Bravo5", "Charlie5", "R1"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val slotFiveLoser = rank(
            resultPosition = 2,
            resultPlayers = listOf("Alpha5", "R2", "R3", "R4"),
            lobbyOverrides = mapOf(5 to teamPlayers(5)),
        )
        val slotEightWinner = rank(
            resultPosition = 3,
            resultPlayers = listOf("Alpha8", "Bravo8", "R5", "R6"),
            lobbyOverrides = mapOf(8 to teamPlayers(8)),
        )
        val slotEightLoser = rank(
            resultPosition = 4,
            resultPlayers = listOf("Alpha8", "R7", "R8", "R9"),
            lobbyOverrides = mapOf(8 to teamPlayers(8)),
        )

        val result = evaluate(listOf(slotEightLoser, slotFiveLoser, slotEightWinner, slotFiveWinner))

        assertEquals(5, result.rows.single { it.resultPosition == 1 }.automaticAssignedTeamSlot)
        assertNull(result.rows.single { it.resultPosition == 2 }.automaticAssignedTeamSlot)
        assertEquals(8, result.rows.single { it.resultPosition == 3 }.automaticAssignedTeamSlot)
        assertNull(result.rows.single { it.resultPosition == 4 }.automaticAssignedTeamSlot)
    }

    @Test
    fun evaluateObservedDeviceConflictKeepsPositionElevenAndDemotesPositionNine() {
        val lobbyPlayers = listOf(
            "FLX_RUSER!!",
            "APX MACHINE",
            "APX INFERNO",
            "APX ZENOX",
        )
        val positionNine = rank(
            resultPosition = 9,
            resultPlayers = listOf("MAFIABOSS", "ELX_RUSER!!", "HACKERBOSS", null),
            lobbyOverrides = mapOf(5 to lobbyPlayers),
        )
        val positionEleven = rank(
            resultPosition = 11,
            resultPlayers = listOf("APX ANGELIC", "ABX MACHINE", "APX INFERNO", "APX ZENOX"),
            lobbyOverrides = mapOf(5 to lobbyPlayers),
        )

        val result = evaluate(listOf(positionNine, positionEleven))
        val positionNineRow = result.rows.single { it.resultPosition == 9 }
        val positionElevenRow = result.rows.single { it.resultPosition == 11 }

        assertEquals(25, positionNine.winningVotePercent)
        assertTrue(positionEleven.winningVotePercent ?: 0 > positionNine.winningVotePercent ?: 0)
        assertNull(positionNineRow.automaticAssignedTeamSlot)
        assertEquals(ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS, positionNineRow.decisionReason)
        assertEquals(5, positionElevenRow.automaticAssignedTeamSlot)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, positionElevenRow.assignmentSafety.safetyStatus)
    }

    @Test
    fun evaluateNeverReturnsDuplicateAutomaticTeamSlots() {
        val matchResults = listOf(
            rank(1, listOf("Alpha5", "Bravo5", "Charlie5", "R1"), mapOf(5 to teamPlayers(5))),
            rank(2, listOf("Alpha5", "Bravo5", "R2", "R3"), mapOf(5 to teamPlayers(5))),
            rank(3, listOf("Alpha8", "Bravo8", "R4", "R5"), mapOf(8 to teamPlayers(8))),
            rank(4, listOf("Alpha8", "R6", "R7", "R8"), mapOf(8 to teamPlayers(8))),
        )

        val automaticSlots = evaluate(matchResults).rows.mapNotNull { it.automaticAssignedTeamSlot }

        assertEquals(automaticSlots.distinct(), automaticSlots)
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
                resultPlayers = isolatedTeamPlayers(resultPosition),
                lobbyOverrides = mapOf(resultPosition to isolatedTeamPlayers(resultPosition)),
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

    private fun isolatedTeamPlayers(teamSlot: Int): List<String?> = listOf(
        isolatedName(teamSlot, "QWER"),
        isolatedName(teamSlot, "ASDF"),
        isolatedName(teamSlot, "ZXCV"),
        isolatedName(teamSlot, "TYUI"),
    )

    private fun isolatedName(teamSlot: Int, suffix: String): String {
        val token = ('A'.code + teamSlot - 1).toChar()
        return "$token$token$token$token$suffix"
    }

    private fun unrelatedPlayers(teamSlot: Int): List<String?> = listOf(
        "ZZ${teamSlot}Quartz",
        "YY${teamSlot}Vex",
        "XX${teamSlot}Mirth",
        "WW${teamSlot}Pond",
    )
}
