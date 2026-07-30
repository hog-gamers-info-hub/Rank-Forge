package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class TeamAssignmentSafetyEvaluatorTest {
    @Test
    fun evaluate_returnsZeroResultForEmptyRowList() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(emptyList())

        assertEquals(0, result.rowCount)
        assertEquals(0, result.safeAssignmentCount)
        assertEquals(emptyList<RowTeamAssignmentSafetyResult>(), result.rowResults)
    }

    @Test
    fun evaluate_returnsSafeForAutomaticRowWithThreeMatchesAndNoSecondSuggestion() {
        val assessment = automaticAssessment(
            teamSlot = 1,
            confidenceScore = 90,
            contributingMatchCount = 3,
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT,
            expectedProposedTeamSlot = 1,
            expectedReasons = emptySet(),
        )
        assertEquals(1, result.safeAssignmentCount)
    }

    @Test
    fun evaluate_returnsSafeForAutomaticRowWithLeadExactlyTen() {
        val assessment = automaticAssessment(
            teamSlot = 1,
            confidenceScore = 90,
            contributingMatchCount = 4,
            secondSuggestion = suggestion(rank = 2, teamSlot = 2, confidenceScore = 80),
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT,
            expectedProposedTeamSlot = 1,
            expectedReasons = emptySet(),
        )
    }

    @Test
    fun evaluate_returnsReviewRequiredForAutomaticRowWithLeadNine() {
        val assessment = automaticAssessment(
            teamSlot = 1,
            confidenceScore = 90,
            contributingMatchCount = 4,
            secondSuggestion = suggestion(rank = 2, teamSlot = 2, confidenceScore = 81),
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
            expectedProposedTeamSlot = 1,
            expectedReasons = setOf(TeamAssignmentSafetyReason.INSUFFICIENT_CANDIDATE_LEAD),
        )
        assertEquals(0, result.safeAssignmentCount)
    }

    @Test
    fun evaluate_returnsReviewRequiredForAutomaticRowWithOnlyTwoContributingMatches() {
        val assessment = automaticAssessment(
            teamSlot = 1,
            confidenceScore = 90,
            contributingMatchCount = 2,
            secondSuggestion = suggestion(rank = 2, teamSlot = 2, confidenceScore = 75),
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
            expectedProposedTeamSlot = 1,
            expectedReasons = setOf(TeamAssignmentSafetyReason.INSUFFICIENT_PLAYER_MATCH_COUNT),
        )
    }

    @Test
    fun evaluate_preservesAllAutomaticSafetyFailureReasons() {
        val assessment = automaticAssessment(
            teamSlot = 1,
            confidenceScore = 90,
            contributingMatchCount = 2,
            secondSuggestion = suggestion(rank = 2, teamSlot = 2, confidenceScore = 85),
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
            expectedProposedTeamSlot = 1,
            expectedReasons = setOf(
                TeamAssignmentSafetyReason.INSUFFICIENT_PLAYER_MATCH_COUNT,
                TeamAssignmentSafetyReason.INSUFFICIENT_CANDIDATE_LEAD,
            ),
        )
    }

    @Test
    fun evaluate_returnsReviewRequiredForConfirmationRequiredRow() {
        val assessment = assessment(
            tier = TeamMatchConfidenceTier.CONFIRMATION_REQUIRED,
            selectedSuggestion = suggestion(rank = 1, teamSlot = 3, confidenceScore = 75),
            reason = TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD,
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
            expectedProposedTeamSlot = 3,
            expectedReasons = setOf(TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER),
        )
    }

    @Test
    fun evaluate_returnsManualRequiredForManualRequiredRow() {
        val assessment = assessment(
            tier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
            selectedSuggestion = suggestion(rank = 1, teamSlot = 4, confidenceScore = 74),
            reason = TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD,
        )

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.MANUAL_REQUIRED,
            expectedProposedTeamSlot = 4,
            expectedReasons = setOf(TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER),
        )
    }

    @Test
    fun evaluate_returnsManualRequiredForNoSuggestionRow() {
        val assessment = noSuggestionAssessment()

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, assessment)))

        assertSingleRow(
            result = result,
            expectedStatus = TeamAssignmentSafetyStatus.MANUAL_REQUIRED,
            expectedProposedTeamSlot = null,
            expectedReasons = setOf(
                TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER,
                TeamAssignmentSafetyReason.NO_SUGGESTION,
            ),
        )
    }

    @Test
    fun evaluate_returnsReviewRequiredForDuplicateOtherwiseSafeTeamSlots() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                row(0, automaticAssessment(teamSlot = 1)),
                row(1, automaticAssessment(teamSlot = 1)),
            ),
        )

        assertEquals(2, result.rowCount)
        assertEquals(0, result.safeAssignmentCount)
        assertEquals(
            listOf(
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
            ),
            result.rowResults.map { it.safetyStatus },
        )
        assertEquals(
            listOf(
                setOf(TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE),
                setOf(TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE),
            ),
            result.rowResults.map { it.reasons },
        )
    }

    @Test
    fun evaluate_duplicateTeamSlotDoesNotAffectManualRequiredRow() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                row(0, automaticAssessment(teamSlot = 1)),
                row(
                    1,
                    assessment(
                        tier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
                        selectedSuggestion = suggestion(rank = 1, teamSlot = 1, confidenceScore = 74),
                        reason = TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD,
                    ),
                ),
            ),
        )

        assertEquals(1, result.safeAssignmentCount)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, result.rowResults[0].safetyStatus)
        assertEquals(TeamAssignmentSafetyStatus.MANUAL_REQUIRED, result.rowResults[1].safetyStatus)
        assertFalse(result.rowResults[1].reasons.contains(TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE))
    }

    @Test
    fun evaluate_duplicateTeamSlotDoesNotChooseWinner() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                row(2, automaticAssessment(teamSlot = 1, confidenceScore = 100)),
                row(0, automaticAssessment(teamSlot = 1, confidenceScore = 90)),
                row(1, automaticAssessment(teamSlot = 1, confidenceScore = 95)),
            ),
        )

        assertEquals(0, result.safeAssignmentCount)
        assertEquals(listOf(0, 1, 2), result.rowResults.map { it.rowIndex })
        assertEquals(
            listOf(
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
            ),
            result.rowResults.map { it.safetyStatus },
        )
        result.rowResults.forEach { rowResult ->
            assertEquals(setOf(TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE), rowResult.reasons)
        }
    }

    @Test
    fun evaluate_returnsRowResultsSortedByRowIndex() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                row(2, automaticAssessment(teamSlot = 3)),
                row(0, automaticAssessment(teamSlot = 1)),
                row(1, automaticAssessment(teamSlot = 2)),
            ),
        )

        assertEquals(listOf(0, 1, 2), result.rowResults.map { it.rowIndex })
    }

    @Test
    fun evaluate_safeAssignmentCountCountsOnlySafeRows() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                row(0, automaticAssessment(teamSlot = 1)),
                row(
                    1,
                    automaticAssessment(
                        teamSlot = 2,
                        secondSuggestion = suggestion(rank = 2, teamSlot = 3, confidenceScore = 81),
                    ),
                ),
                row(2, noSuggestionAssessment()),
            ),
        )

        assertEquals(1, result.safeAssignmentCount)
    }

    @Test
    fun evaluate_setsProposedTeamSlotForRowsWithSelectedSuggestion() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(
            listOf(
                row(0, automaticAssessment(teamSlot = 3)),
                row(
                    1,
                    assessment(
                        tier = TeamMatchConfidenceTier.CONFIRMATION_REQUIRED,
                        selectedSuggestion = suggestion(rank = 1, teamSlot = 4, confidenceScore = 75),
                        reason = TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD,
                    ),
                ),
                row(
                    2,
                    assessment(
                        tier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
                        selectedSuggestion = suggestion(rank = 1, teamSlot = 5, confidenceScore = 74),
                        reason = TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD,
                    ),
                ),
            ),
        )

        assertEquals(listOf(3, 4, 5), result.rowResults.map { it.proposedTeamSlot })
    }

    @Test
    fun evaluate_setsProposedTeamSlotNullForNoSuggestionRow() {
        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(row(0, noSuggestionAssessment())))

        assertNull(result.rowResults.single().proposedTeamSlot)
    }

    @Test
    fun evaluate_rejectsDuplicateRowIndexes() {
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(
                listOf(
                    row(0, automaticAssessment(teamSlot = 1)),
                    row(0, automaticAssessment(teamSlot = 2)),
                ),
            )
        }
    }

    @Test
    fun evaluate_rejectsMoreThanTwelveRows() {
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(
                (0..12).map { rowIndex ->
                    row(rowIndex = rowIndex.coerceAtMost(11), automaticAssessment(teamSlot = 1))
                },
            )
        }
    }

    @Test
    fun evaluate_rejectsOutOfRangeRowIndex() {
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(listOf(row(-1, automaticAssessment(teamSlot = 1))))
        }
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(listOf(row(12, automaticAssessment(teamSlot = 1))))
        }
    }

    @Test
    fun evaluate_rejectsAutomaticOrConfirmationAssessmentWithoutSelectedSuggestion() {
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(
                listOf(
                    row(
                        0,
                        TeamMatchConfidenceAssessment(
                            tier = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
                            selectedSuggestion = null,
                            suggestions = TopTeamCandidateSuggestions(
                                detectedPlayerCount = 4,
                                evaluatedCandidateCount = 0,
                                suggestions = emptyList(),
                            ),
                            reason = TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD,
                        ),
                    ),
                ),
            )
        }
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(
                listOf(
                    row(
                        0,
                        TeamMatchConfidenceAssessment(
                            tier = TeamMatchConfidenceTier.CONFIRMATION_REQUIRED,
                            selectedSuggestion = null,
                            suggestions = TopTeamCandidateSuggestions(
                                detectedPlayerCount = 4,
                                evaluatedCandidateCount = 0,
                                suggestions = emptyList(),
                            ),
                            reason = TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun evaluate_rejectsSelectedSuggestionInconsistencyWhenDirectlyDetectable() {
        val selectedSuggestion = suggestion(rank = 1, teamSlot = 1, confidenceScore = 90)
        val preservedRankOneSuggestion = suggestion(rank = 1, teamSlot = 2, confidenceScore = 89)

        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(
                listOf(
                    row(
                        0,
                        TeamMatchConfidenceAssessment(
                            tier = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
                            selectedSuggestion = selectedSuggestion,
                            suggestions = TopTeamCandidateSuggestions(
                                detectedPlayerCount = 4,
                                evaluatedCandidateCount = 1,
                                suggestions = listOf(preservedRankOneSuggestion),
                            ),
                            reason = TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun evaluate_rejectsRankTwoWithHigherConfidenceThanRankOne() {
        assertIllegalArgumentException {
            TeamAssignmentSafetyEvaluator.evaluate(
                listOf(
                    row(
                        0,
                        automaticAssessment(
                            teamSlot = 1,
                            confidenceScore = 90,
                            secondSuggestion = suggestion(rank = 2, teamSlot = 2, confidenceScore = 91),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun evaluate_preservesEvidenceObjects() {
        val assessment = automaticAssessment(teamSlot = 1)
        val rowAssessment = row(0, assessment)

        val result = TeamAssignmentSafetyEvaluator.evaluate(listOf(rowAssessment))

        assertSame(assessment, result.rowResults.single().confidenceAssessment)
        assertSame(assessment.selectedSuggestion, result.rowResults.single().confidenceAssessment.selectedSuggestion)
    }

    @Test
    fun evaluate_isDeterministicAcrossRepeatedCalls() {
        val rowAssessments = listOf(
            row(0, automaticAssessment(teamSlot = 1)),
            row(
                1,
                automaticAssessment(
                    teamSlot = 2,
                    secondSuggestion = suggestion(rank = 2, teamSlot = 3, confidenceScore = 81),
                ),
            ),
            row(2, noSuggestionAssessment()),
        )

        val firstResult = TeamAssignmentSafetyEvaluator.evaluate(rowAssessments)
        val secondResult = TeamAssignmentSafetyEvaluator.evaluate(rowAssessments)

        assertEquals(firstResult, secondResult)
    }

    @Test
    fun resultTypesDoNotExposeUiPersistenceFinalizationScoringCorrectionOrActualAssignmentFields() {
        val forbiddenNameParts = listOf(
            "ui",
            "persistence",
            "persist",
            "room",
            "supabase",
            "finalization",
            "finalized",
            "score",
            "placement",
            "kill",
            "correction",
            "corrected",
            "committed",
            "assignedteam",
            "assignedslot",
            "assignmentwrite",
        )
        val exposedFieldNames = listOf(
            RowTeamMatchConfidenceAssessment::class.java,
            TeamAssignmentSafetyResult::class.java,
            RowTeamAssignmentSafetyResult::class.java,
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

    private fun assertSingleRow(
        result: TeamAssignmentSafetyResult,
        expectedStatus: TeamAssignmentSafetyStatus,
        expectedProposedTeamSlot: Int?,
        expectedReasons: Set<TeamAssignmentSafetyReason>,
    ) {
        assertEquals(1, result.rowCount)
        val rowResult = result.rowResults.single()
        assertEquals(0, rowResult.rowIndex)
        assertEquals(expectedStatus, rowResult.safetyStatus)
        assertEquals(expectedProposedTeamSlot, rowResult.proposedTeamSlot)
        assertEquals(expectedReasons, rowResult.reasons)
    }

    private fun row(
        rowIndex: Int,
        confidenceAssessment: TeamMatchConfidenceAssessment,
    ): RowTeamMatchConfidenceAssessment =
        RowTeamMatchConfidenceAssessment(
            rowIndex = rowIndex,
            confidenceAssessment = confidenceAssessment,
        )

    private fun noSuggestionAssessment(): TeamMatchConfidenceAssessment =
        TeamMatchConfidenceAssessment(
            tier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
            selectedSuggestion = null,
            suggestions = TopTeamCandidateSuggestions(
                detectedPlayerCount = 4,
                evaluatedCandidateCount = 0,
                suggestions = emptyList(),
            ),
            reason = TeamMatchConfidenceReason.NO_SUGGESTIONS,
        )

    private fun automaticAssessment(
        teamSlot: Int,
        confidenceScore: Int = 90,
        contributingMatchCount: Int = 3,
        secondSuggestion: TopTeamCandidateSuggestion? = null,
    ): TeamMatchConfidenceAssessment =
        assessment(
            tier = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
            selectedSuggestion = suggestion(
                rank = 1,
                teamSlot = teamSlot,
                confidenceScore = confidenceScore,
                contributingMatchCount = contributingMatchCount,
            ),
            reason = TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD,
            additionalSuggestions = listOfNotNull(secondSuggestion),
        )

    private fun assessment(
        tier: TeamMatchConfidenceTier,
        selectedSuggestion: TopTeamCandidateSuggestion,
        reason: TeamMatchConfidenceReason,
        additionalSuggestions: List<TopTeamCandidateSuggestion> = emptyList(),
    ): TeamMatchConfidenceAssessment =
        TeamMatchConfidenceAssessment(
            tier = tier,
            selectedSuggestion = selectedSuggestion,
            suggestions = TopTeamCandidateSuggestions(
                detectedPlayerCount = 4,
                evaluatedCandidateCount = 1 + additionalSuggestions.size,
                suggestions = listOf(selectedSuggestion) + additionalSuggestions,
            ),
            reason = reason,
        )

    private fun suggestion(
        rank: Int,
        teamSlot: Int,
        confidenceScore: Int,
        contributingMatchCount: Int = 3,
    ): TopTeamCandidateSuggestion =
        TopTeamCandidateSuggestion(
            rank = rank,
            teamCandidateScore = TeamCandidateScore(
                candidateTeamSlot = teamSlot,
                confidenceScore = confidenceScore,
                detectedPlayerCount = 4,
                validDetectedPlayerCount = 4,
                rosterPlayerCount = 4,
                contributingMatchCount = contributingMatchCount,
                averageMatchedPlayerScore = confidenceScore,
                coverageScore = if (contributingMatchCount >= 4) 100 else 75,
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
