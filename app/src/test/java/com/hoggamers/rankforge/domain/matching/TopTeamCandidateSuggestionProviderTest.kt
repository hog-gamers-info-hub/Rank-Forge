package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class TopTeamCandidateSuggestionProviderTest {
    @Test
    fun suggestTopThree_returnsEmptySuggestionsForNoCandidateTeams() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = emptyList(),
        )

        assertEquals(4, result.detectedPlayerCount)
        assertEquals(0, result.evaluatedCandidateCount)
        assertEquals(emptyList<TopTeamCandidateSuggestion>(), result.suggestions)
    }

    @Test
    fun suggestTopThree_returnsOneSuggestionForOneCandidate() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(exactCandidate(teamSlot = 1)),
        )

        assertSuggestionSlots(result, listOf(1))
        assertEquals(listOf(1), result.suggestions.map { it.rank })
        assertEquals(100, result.suggestions.single().teamCandidateScore.confidenceScore)
        assertEquals(4, result.suggestions.single().teamCandidateScore.playerMatches.size)
    }

    @Test
    fun suggestTopThree_returnsTwoSuggestionsForTwoCandidates() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(
                threeExactCandidate(teamSlot = 2),
                exactCandidate(teamSlot = 1),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2))
        assertEquals(listOf(1, 2), result.suggestions.map { it.rank })
        assertEquals(2, result.evaluatedCandidateCount)
    }

    @Test
    fun suggestTopThree_returnsThreeSuggestionsForThreeCandidates() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(
                twoExactCandidate(teamSlot = 3),
                exactCandidate(teamSlot = 1),
                threeExactCandidate(teamSlot = 2),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), result.suggestions.map { it.rank })
        assertEquals(3, result.evaluatedCandidateCount)
    }

    @Test
    fun suggestTopThree_returnsExactlyThreeSuggestionsForMoreThanThreeCandidates() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(
                oneExactCandidate(teamSlot = 4),
                twoExactCandidate(teamSlot = 3),
                exactCandidate(teamSlot = 1),
                threeExactCandidate(teamSlot = 2),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), result.suggestions.map { it.rank })
        assertEquals(4, result.evaluatedCandidateCount)
    }

    @Test
    fun suggestTopThree_ordersCandidatesByHigherConfidence() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(
                threeExactCandidate(teamSlot = 2),
                exactCandidate(teamSlot = 1),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2))
        assertEquals(100, result.suggestions[0].teamCandidateScore.confidenceScore)
        assertEquals(92, result.suggestions[1].teamCandidateScore.confidenceScore)
    }

    @Test
    fun suggestTopThree_breaksConfidenceTieByHigherContributingMatchCount() {
        val detectedNames = listOf("Unit7", "abcdefghijklmn")
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = detectedNames,
            candidateTeams = listOf(
                TeamCandidateRosterInput(
                    teamSlot = 1,
                    rosterPlayerNames = listOf("Unit7"),
                ),
                TeamCandidateRosterInput(
                    teamSlot = 2,
                    rosterPlayerNames = listOf("Unit7", "abcxyzghijklmn"),
                ),
            ),
        )

        assertSuggestionSlots(result, listOf(2, 1))
        assertEquals(77, result.suggestions[0].teamCandidateScore.confidenceScore)
        assertEquals(77, result.suggestions[1].teamCandidateScore.confidenceScore)
        assertEquals(2, result.suggestions[0].teamCandidateScore.contributingMatchCount)
        assertEquals(1, result.suggestions[1].teamCandidateScore.contributingMatchCount)
    }

    @Test
    fun teamCandidateScoreOrdering_breaksTieByHigherAverageMatchedPlayerScore() {
        val lowerAverage = candidateScore(
            candidateTeamSlot = 1,
            confidenceScore = 60,
            contributingMatchCount = 1,
            averageMatchedPlayerScore = 75,
            coverageScore = 25,
        )
        val higherAverage = candidateScore(
            candidateTeamSlot = 2,
            confidenceScore = 60,
            contributingMatchCount = 1,
            averageMatchedPlayerScore = 76,
            coverageScore = 25,
        )

        val orderedSlots = listOf(lowerAverage, higherAverage)
            .sortedWith(TopTeamCandidateSuggestionProvider.teamCandidateScoreOrdering)
            .map { it.candidateTeamSlot }

        assertEquals(listOf(2, 1), orderedSlots)
    }

    @Test
    fun teamCandidateScoreOrdering_breaksTieByHigherCoverageScore() {
        val lowerCoverage = candidateScore(
            candidateTeamSlot = 1,
            confidenceScore = 80,
            contributingMatchCount = 2,
            averageMatchedPlayerScore = 80,
            coverageScore = 50,
        )
        val higherCoverage = candidateScore(
            candidateTeamSlot = 2,
            confidenceScore = 80,
            contributingMatchCount = 2,
            averageMatchedPlayerScore = 80,
            coverageScore = 75,
        )

        val orderedSlots = listOf(lowerCoverage, higherCoverage)
            .sortedWith(TopTeamCandidateSuggestionProvider.teamCandidateScoreOrdering)
            .map { it.candidateTeamSlot }

        assertEquals(listOf(2, 1), orderedSlots)
    }

    @Test
    fun suggestTopThree_breaksFinalTieByLowerTeamSlot() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(
                exactCandidate(teamSlot = 2),
                exactCandidate(teamSlot = 1),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2))
        assertEquals(100, result.suggestions[0].teamCandidateScore.confidenceScore)
        assertEquals(100, result.suggestions[1].teamCandidateScore.confidenceScore)
    }

    @Test
    fun suggestTopThree_assignsSequentialRanksAfterSorting() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(
                oneExactCandidate(teamSlot = 4),
                exactCandidate(teamSlot = 1),
                twoExactCandidate(teamSlot = 3),
                threeExactCandidate(teamSlot = 2),
            ),
        )

        assertEquals(listOf(1, 2, 3), result.suggestions.map { it.rank })
        assertSuggestionSlots(result, listOf(1, 2, 3))
    }

    @Test
    fun suggestTopThree_keepsLowConfidenceCandidatesWhenAmongStrongestAvailable() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = listOf("abcd"),
            candidateTeams = listOf(
                TeamCandidateRosterInput(
                    teamSlot = 2,
                    rosterPlayerNames = listOf("xy"),
                ),
                TeamCandidateRosterInput(
                    teamSlot = 1,
                    rosterPlayerNames = listOf("abxd"),
                ),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2))
        assertEquals(60, result.suggestions[0].teamCandidateScore.confidenceScore)
        assertEquals(0, result.suggestions[1].teamCandidateScore.confidenceScore)
    }

    @Test
    fun suggestTopThree_keepsZeroConfidenceCandidatesOnlyWhenNeededToFillSuggestions() {
        val resultWithNeededZeroConfidence = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = listOf("Unit7"),
            candidateTeams = listOf(
                TeamCandidateRosterInput(1, listOf("Unit7")),
                TeamCandidateRosterInput(2, emptyList()),
                TeamCandidateRosterInput(3, listOf("xy")),
            ),
        )
        val resultWithEnoughStrongerCandidates = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = listOf("Unit7"),
            candidateTeams = listOf(
                TeamCandidateRosterInput(1, listOf("Unit7")),
                TeamCandidateRosterInput(2, emptyList()),
                TeamCandidateRosterInput(3, listOf("xy")),
                TeamCandidateRosterInput(4, listOf("Unit7")),
                TeamCandidateRosterInput(5, listOf("Unit7")),
            ),
        )

        assertSuggestionSlots(resultWithNeededZeroConfidence, listOf(1, 2, 3))
        assertEquals(listOf(77, 0, 0), resultWithNeededZeroConfidence.suggestions.map {
            it.teamCandidateScore.confidenceScore
        })
        assertSuggestionSlots(resultWithEnoughStrongerCandidates, listOf(1, 4, 5))
        assertFalse(resultWithEnoughStrongerCandidates.suggestions.any {
            it.teamCandidateScore.confidenceScore == 0
        })
    }

    @Test
    fun suggestTopThree_rejectsDuplicateCandidateTeamSlots() {
        assertIllegalArgumentException {
            TopTeamCandidateSuggestionProvider.suggestTopThree(
                detectedPlayerNames = listOf("Unit7"),
                candidateTeams = listOf(
                    TeamCandidateRosterInput(1, listOf("Unit7")),
                    TeamCandidateRosterInput(1, listOf("Nova")),
                ),
            )
        }
    }

    @Test
    fun suggestTopThree_propagatesInvalidCandidateSlotException() {
        assertIllegalArgumentException {
            TopTeamCandidateSuggestionProvider.suggestTopThree(
                detectedPlayerNames = listOf("Unit7"),
                candidateTeams = listOf(TeamCandidateRosterInput(0, listOf("Unit7"))),
            )
        }
        assertIllegalArgumentException {
            TopTeamCandidateSuggestionProvider.suggestTopThree(
                detectedPlayerNames = listOf("Unit7"),
                candidateTeams = listOf(TeamCandidateRosterInput(13, listOf("Unit7"))),
            )
        }
    }

    @Test
    fun suggestTopThree_evaluatesCandidateWithEmptyRoster() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = listOf(TeamCandidateRosterInput(1, emptyList())),
        )

        assertSuggestionSlots(result, listOf(1))
        assertEquals(0, result.suggestions.single().teamCandidateScore.confidenceScore)
        assertEquals(0, result.suggestions.single().teamCandidateScore.rosterPlayerCount)
        assertEquals(1, result.evaluatedCandidateCount)
    }

    @Test
    fun suggestTopThree_preservesDetectedAndRosterInputs() {
        val detectedNames = mutableListOf<String?>("Unit7", "Nova", "Rin", "Kai")
        val rosterOne = mutableListOf<String?>("Unit7", "Nova", "Rin", "Kai")
        val rosterTwo = mutableListOf<String?>("Unit7", "Nova", "Rin", "Byte")
        val candidateTeams = listOf(
            TeamCandidateRosterInput(1, rosterOne),
            TeamCandidateRosterInput(2, rosterTwo),
        )

        TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = detectedNames,
            candidateTeams = candidateTeams,
        )

        assertEquals(listOf("Unit7", "Nova", "Rin", "Kai"), detectedNames)
        assertEquals(listOf("Unit7", "Nova", "Rin", "Kai"), rosterOne)
        assertEquals(listOf("Unit7", "Nova", "Rin", "Byte"), rosterTwo)
    }

    @Test
    fun suggestTopThree_isDeterministicAcrossRepeatedCalls() {
        val candidateTeams = listOf(
            twoExactCandidate(teamSlot = 3),
            exactCandidate(teamSlot = 1),
            threeExactCandidate(teamSlot = 2),
        )

        val firstResult = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = candidateTeams,
        )
        val secondResult = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = standardDetectedNames,
            candidateTeams = candidateTeams,
        )

        assertEquals(firstResult, secondResult)
    }

    @Test
    fun topThreeResultTypesDoNotExposeThresholdAssignmentOrCandidateLeadFields() {
        val forbiddenNameParts = listOf(
            "match",
            "automatic",
            "requiresconfirmation",
            "confidencetier",
            "lead",
            "assignment",
            "conflict",
            "selectedteam",
            "ui",
            "persistence",
            "finalized",
        )
        val exposedFieldNames = listOf(
            TopTeamCandidateSuggestions::class.java,
            TopTeamCandidateSuggestion::class.java,
            TeamCandidateRosterInput::class.java,
        ).flatMap { type ->
            type.declaredFields.map { it.name.lowercase() }
        }

        forbiddenNameParts.forEach { forbiddenNamePart ->
            assertFalse(
                "Unexpected out-of-scope field name containing $forbiddenNamePart.",
                exposedFieldNames.any { it.contains(forbiddenNamePart) },
            )
        }
    }

    @Test
    fun suggestTopThree_doesNotAddSpeculativeOcrConfusionMappings() {
        val result = TopTeamCandidateSuggestionProvider.suggestTopThree(
            detectedPlayerNames = listOf("5S", "Nova", "Rin", "Kai"),
            candidateTeams = listOf(
                TeamCandidateRosterInput(1, listOf("55", "Nova", "Rin", "Kai")),
                TeamCandidateRosterInput(2, listOf("55", "Byte", "Chord", "Delta")),
            ),
        )

        assertSuggestionSlots(result, listOf(1, 2))
        assertEquals(92, result.suggestions[0].teamCandidateScore.confidenceScore)
        assertEquals(3, result.suggestions[0].teamCandidateScore.contributingMatchCount)
        assertEquals(
            listOf(1 to 1, 2 to 2, 3 to 3),
            result.suggestions[0].teamCandidateScore.playerMatches.map {
                it.detectedPlayerIndex to it.rosterPlayerIndex
            },
        )
    }

    private fun assertSuggestionSlots(
        result: TopTeamCandidateSuggestions,
        expectedTeamSlots: List<Int>,
    ) {
        assertEquals(expectedTeamSlots, result.suggestions.map { it.teamCandidateScore.candidateTeamSlot })
        assertEquals((1..expectedTeamSlots.size).toList(), result.suggestions.map { it.rank })
        result.suggestions.forEach { suggestion ->
            assertEquals(
                suggestion.teamCandidateScore.detectedPlayerCount,
                result.detectedPlayerCount,
            )
        }
    }

    private fun exactCandidate(teamSlot: Int): TeamCandidateRosterInput =
        TeamCandidateRosterInput(
            teamSlot = teamSlot,
            rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
        )

    private fun threeExactCandidate(teamSlot: Int): TeamCandidateRosterInput =
        TeamCandidateRosterInput(
            teamSlot = teamSlot,
            rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Byte"),
        )

    private fun twoExactCandidate(teamSlot: Int): TeamCandidateRosterInput =
        TeamCandidateRosterInput(
            teamSlot = teamSlot,
            rosterPlayerNames = listOf("Unit7", "Nova", "Byte", "Chord"),
        )

    private fun oneExactCandidate(teamSlot: Int): TeamCandidateRosterInput =
        TeamCandidateRosterInput(
            teamSlot = teamSlot,
            rosterPlayerNames = listOf("Unit7", "Byte", "Chord", "Delta"),
        )

    private fun candidateScore(
        candidateTeamSlot: Int,
        confidenceScore: Int,
        contributingMatchCount: Int,
        averageMatchedPlayerScore: Int,
        coverageScore: Int,
    ): TeamCandidateScore =
        TeamCandidateScore(
            candidateTeamSlot = candidateTeamSlot,
            confidenceScore = confidenceScore,
            detectedPlayerCount = 4,
            validDetectedPlayerCount = 4,
            rosterPlayerCount = 4,
            contributingMatchCount = contributingMatchCount,
            averageMatchedPlayerScore = averageMatchedPlayerScore,
            coverageScore = coverageScore,
            playerMatches = emptyList(),
        )

    private fun assertIllegalArgumentException(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
        }
    }

    private companion object {
        val standardDetectedNames = listOf("Unit7", "Nova", "Rin", "Kai")
    }
}
