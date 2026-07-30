package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class TeamMatchConfidenceTierClassifierTest {
    @Test
    fun classify_returnsManualRequiredForNoSuggestions() {
        val suggestions = TopTeamCandidateSuggestions(
            detectedPlayerCount = 4,
            evaluatedCandidateCount = 0,
            suggestions = emptyList(),
        )

        val assessment = TeamMatchConfidenceTierClassifier.classify(suggestions)

        assertEquals(TeamMatchConfidenceTier.MANUAL_REQUIRED, assessment.tier)
        assertEquals(TeamMatchConfidenceReason.NO_SUGGESTIONS, assessment.reason)
        assertNull(assessment.selectedSuggestion)
        assertSame(suggestions, assessment.suggestions)
    }

    @Test
    fun classify_returnsManualRequiredBelowConfirmationThreshold() {
        assertAssessmentForConfidence(
            confidenceScore = 0,
            expectedTier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
            expectedReason = TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD,
        )
        assertAssessmentForConfidence(
            confidenceScore = 74,
            expectedTier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
            expectedReason = TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD,
        )
    }

    @Test
    fun classify_returnsConfirmationRequiredAtInclusiveConfirmationThreshold() {
        assertAssessmentForConfidence(
            confidenceScore = 75,
            expectedTier = TeamMatchConfidenceTier.CONFIRMATION_REQUIRED,
            expectedReason = TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD,
        )
        assertAssessmentForConfidence(
            confidenceScore = 89,
            expectedTier = TeamMatchConfidenceTier.CONFIRMATION_REQUIRED,
            expectedReason = TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD,
        )
    }

    @Test
    fun classify_returnsAutomaticCandidateAtInclusiveAutomaticThreshold() {
        assertAssessmentForConfidence(
            confidenceScore = 90,
            expectedTier = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
            expectedReason = TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD,
        )
        assertAssessmentForConfidence(
            confidenceScore = 100,
            expectedTier = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
            expectedReason = TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD,
        )
    }

    @Test
    fun classify_selectsRankOneSuggestionWithoutUsingListOrder() {
        val rankOneSuggestion = suggestion(rank = 1, teamSlot = 1, confidenceScore = 90)
        val rankTwoSuggestion = suggestion(rank = 2, teamSlot = 2, confidenceScore = 75)
        val suggestions = TopTeamCandidateSuggestions(
            detectedPlayerCount = 4,
            evaluatedCandidateCount = 2,
            suggestions = listOf(rankTwoSuggestion, rankOneSuggestion),
        )

        val assessment = TeamMatchConfidenceTierClassifier.classify(suggestions)

        assertSame(rankOneSuggestion, assessment.selectedSuggestion)
        assertEquals(TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE, assessment.tier)
        assertEquals(TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD, assessment.reason)
    }

    @Test
    fun classify_rejectsLowerRankedHigherConfidenceInsteadOfResorting() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                suggestions(
                    suggestion(rank = 1, teamSlot = 1, confidenceScore = 75),
                    suggestion(rank = 2, teamSlot = 2, confidenceScore = 90),
                ),
            )
        }
    }

    @Test
    fun classify_rejectsDuplicateRankOneSuggestions() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                suggestions(
                    suggestion(rank = 1, teamSlot = 1, confidenceScore = 90),
                    suggestion(rank = 1, teamSlot = 2, confidenceScore = 89),
                ),
            )
        }
    }

    @Test
    fun classify_rejectsMissingRankOneSuggestion() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                suggestions(
                    suggestion(rank = 2, teamSlot = 1, confidenceScore = 90),
                ),
            )
        }
    }

    @Test
    fun classify_rejectsDuplicateRankValues() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                suggestions(
                    suggestion(rank = 1, teamSlot = 1, confidenceScore = 90),
                    suggestion(rank = 2, teamSlot = 2, confidenceScore = 89),
                    suggestion(rank = 2, teamSlot = 3, confidenceScore = 88),
                ),
            )
        }
    }

    @Test
    fun classify_rejectsNonSequentialRanks() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                suggestions(
                    suggestion(rank = 1, teamSlot = 1, confidenceScore = 90),
                    suggestion(rank = 3, teamSlot = 2, confidenceScore = 89),
                ),
            )
        }
    }

    @Test
    fun classify_rejectsMoreThanThreeSuggestions() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                suggestions(
                    suggestion(rank = 1, teamSlot = 1, confidenceScore = 100),
                    suggestion(rank = 2, teamSlot = 2, confidenceScore = 90),
                    suggestion(rank = 3, teamSlot = 3, confidenceScore = 89),
                    suggestion(rank = 4, teamSlot = 4, confidenceScore = 75),
                ),
            )
        }
    }

    @Test
    fun classify_rejectsEvaluatedCandidateCountBelowSuggestionCount() {
        assertIllegalArgumentException {
            TeamMatchConfidenceTierClassifier.classify(
                TopTeamCandidateSuggestions(
                    detectedPlayerCount = 4,
                    evaluatedCandidateCount = 1,
                    suggestions = listOf(
                        suggestion(rank = 1, teamSlot = 1, confidenceScore = 90),
                        suggestion(rank = 2, teamSlot = 2, confidenceScore = 89),
                    ),
                ),
            )
        }
    }

    @Test
    fun classify_preservesSuggestionEvidenceObjectAndNestedScoreEvidence() {
        val rankOneSuggestion = suggestion(rank = 1, teamSlot = 1, confidenceScore = 90)
        val suggestions = suggestions(rankOneSuggestion)

        val assessment = TeamMatchConfidenceTierClassifier.classify(suggestions)

        assertSame(suggestions, assessment.suggestions)
        assertSame(rankOneSuggestion, assessment.selectedSuggestion)
        assertEquals(1, assessment.selectedSuggestion?.teamCandidateScore?.candidateTeamSlot)
        assertEquals(90, assessment.selectedSuggestion?.teamCandidateScore?.confidenceScore)
    }

    @Test
    fun classify_isDeterministicAcrossRepeatedCalls() {
        val suggestions = suggestions(
            suggestion(rank = 1, teamSlot = 1, confidenceScore = 90),
            suggestion(rank = 2, teamSlot = 2, confidenceScore = 75),
        )

        val firstAssessment = TeamMatchConfidenceTierClassifier.classify(suggestions)
        val secondAssessment = TeamMatchConfidenceTierClassifier.classify(suggestions)

        assertEquals(firstAssessment, secondAssessment)
    }

    @Test
    fun confidenceResultTypesDoNotExposeAssignmentSafetyLeadUiPersistenceScoringOrFinalizationFields() {
        val forbiddenNameParts = listOf(
            "assignment",
            "allowed",
            "safe",
            "lead",
            "conflict",
            "ui",
            "persistence",
            "scoring",
            "score",
            "finalization",
            "finalized",
        )
        val exposedFieldNames = listOf(
            TeamMatchConfidenceAssessment::class.java,
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

    private fun assertAssessmentForConfidence(
        confidenceScore: Int,
        expectedTier: TeamMatchConfidenceTier,
        expectedReason: TeamMatchConfidenceReason,
    ) {
        val suggestions = suggestions(
            suggestion(rank = 1, teamSlot = 1, confidenceScore = confidenceScore),
        )

        val assessment = TeamMatchConfidenceTierClassifier.classify(suggestions)

        assertEquals(expectedTier, assessment.tier)
        assertEquals(expectedReason, assessment.reason)
        assertEquals(1, assessment.selectedSuggestion?.rank)
        assertSame(suggestions, assessment.suggestions)
    }

    private fun suggestions(
        vararg suggestions: TopTeamCandidateSuggestion,
    ): TopTeamCandidateSuggestions =
        TopTeamCandidateSuggestions(
            detectedPlayerCount = 4,
            evaluatedCandidateCount = suggestions.size,
            suggestions = suggestions.toList(),
        )

    private fun suggestion(
        rank: Int,
        teamSlot: Int,
        confidenceScore: Int,
    ): TopTeamCandidateSuggestion =
        TopTeamCandidateSuggestion(
            rank = rank,
            teamCandidateScore = TeamCandidateScore(
                candidateTeamSlot = teamSlot,
                confidenceScore = confidenceScore,
                detectedPlayerCount = 4,
                validDetectedPlayerCount = 4,
                rosterPlayerCount = 4,
                contributingMatchCount = if (confidenceScore == 0) 0 else 4,
                averageMatchedPlayerScore = confidenceScore,
                coverageScore = if (confidenceScore == 0) 0 else 100,
                playerMatches = emptyList(),
            ),
        )

    private fun assertIllegalArgumentException(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
        }
    }
}
