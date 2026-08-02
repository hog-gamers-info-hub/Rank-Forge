package com.hoggamers.rankforge.domain.matching

data class ScoreboardTeamIdentificationRowResult(
    val rowIndex: Int,
    val expectedPlacementId: Int,
    val detectedPlayerNames: List<String>,
    val suggestions: TopTeamCandidateSuggestions,
    val confidenceAssessment: TeamMatchConfidenceAssessment,
    val assignmentSafety: RowTeamAssignmentSafetyResult,
) {
    val suggestedTeamSlot: Int?
        get() = confidenceAssessment.selectedSuggestion?.teamCandidateScore?.candidateTeamSlot

    val identifiedTeamSlot: Int?
        get() = confidenceAssessment.selectedSuggestion
            ?.takeIf { it.teamCandidateScore.contributingMatchCount > 0 }
            ?.teamCandidateScore
            ?.candidateTeamSlot
}

data class ScoreboardTeamIdentificationEvaluation(
    val rows: List<ScoreboardTeamIdentificationRowResult>,
    val assignmentSafety: TeamAssignmentSafetyResult,
)

object ScoreboardTeamIdentificationEvaluator {
    fun evaluate(
        rowEvidence: List<ScoreboardRowPlayerEvidence>,
        candidateTeams: List<TeamCandidateRosterInput>,
    ): ScoreboardTeamIdentificationEvaluation {
        val orderedEvidence = rowEvidence.sortedBy { it.rowIndex }
        val assessments = orderedEvidence.map { evidence ->
            val suggestions = TopTeamCandidateSuggestionProvider.suggestTopThree(
                detectedPlayerNames = evidence.detectedPlayerNames,
                candidateTeams = candidateTeams,
            )
            RowEvaluationInput(
                evidence = evidence,
                suggestions = suggestions,
                confidenceAssessment = TeamMatchConfidenceTierClassifier.classify(suggestions),
            )
        }
        val assignmentSafety = TeamAssignmentSafetyEvaluator.evaluate(
            assessments.map { assessment ->
                RowTeamMatchConfidenceAssessment(
                    rowIndex = assessment.evidence.rowIndex,
                    confidenceAssessment = assessment.confidenceAssessment,
                )
            },
        )
        val safetyByRow = assignmentSafety.rowResults.associateBy { it.rowIndex }

        return ScoreboardTeamIdentificationEvaluation(
            rows = assessments.map { assessment ->
                ScoreboardTeamIdentificationRowResult(
                    rowIndex = assessment.evidence.rowIndex,
                    expectedPlacementId = assessment.evidence.expectedPlacementId,
                    detectedPlayerNames = assessment.evidence.detectedPlayerNames.toList(),
                    suggestions = assessment.suggestions,
                    confidenceAssessment = assessment.confidenceAssessment,
                    assignmentSafety = requireNotNull(safetyByRow[assessment.evidence.rowIndex]),
                )
            },
            assignmentSafety = assignmentSafety,
        )
    }

    private data class RowEvaluationInput(
        val evidence: ScoreboardRowPlayerEvidence,
        val suggestions: TopTeamCandidateSuggestions,
        val confidenceAssessment: TeamMatchConfidenceAssessment,
    )
}
