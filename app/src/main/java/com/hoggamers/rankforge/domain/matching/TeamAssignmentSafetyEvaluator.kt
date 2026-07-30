package com.hoggamers.rankforge.domain.matching

object TeamAssignmentSafetyEvaluator {
    fun evaluate(
        rowAssessments: List<RowTeamMatchConfidenceAssessment>,
    ): TeamAssignmentSafetyResult {
        validateRowStructure(rowAssessments)

        val rowEvaluations = rowAssessments
            .sortedBy { it.rowIndex }
            .map { rowAssessment ->
                validateConfidenceAssessment(rowAssessment.confidenceAssessment)
                evaluateRow(rowAssessment)
            }
        val duplicatedOtherwiseSafeTeamSlots = rowEvaluations
            .filter { it.otherwiseSafeAutomaticCandidate }
            .mapNotNull { it.proposedTeamSlot }
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        val rowResults = rowEvaluations.map { rowEvaluation ->
            rowEvaluation.toResult(duplicatedOtherwiseSafeTeamSlots)
        }

        return TeamAssignmentSafetyResult(
            rowCount = rowAssessments.size,
            safeAssignmentCount = rowResults.count {
                it.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
            },
            rowResults = rowResults,
        )
    }

    private fun validateRowStructure(rowAssessments: List<RowTeamMatchConfidenceAssessment>) {
        require(rowAssessments.size <= MAX_ROW_COUNT) {
            "Assignment safety evaluation supports at most 12 OCR rows."
        }

        val rowIndexes = rowAssessments.map { it.rowIndex }
        require(rowIndexes.all { it in ROW_INDEX_RANGE }) {
            "OCR row indexes must be in the zero-based range 0..11."
        }
        require(rowIndexes.toSet().size == rowIndexes.size) {
            "OCR row indexes must be unique."
        }
    }

    private fun validateConfidenceAssessment(confidenceAssessment: TeamMatchConfidenceAssessment) {
        val selectedSuggestion = confidenceAssessment.selectedSuggestion
        val preservedRankOneSuggestions = confidenceAssessment.suggestions.suggestions.filter { it.rank == 1 }

        if (selectedSuggestion == null) {
            require(confidenceAssessment.tier == TeamMatchConfidenceTier.MANUAL_REQUIRED) {
                "Only manual-required assessments may omit a selected suggestion."
            }
            require(preservedRankOneSuggestions.isEmpty()) {
                "Selected suggestion must match preserved rank-1 suggestion evidence."
            }
            return
        }

        require(selectedSuggestion.rank == 1) {
            "Selected suggestion must be rank 1."
        }
        require(selectedSuggestion in confidenceAssessment.suggestions.suggestions) {
            "Selected suggestion must be preserved in suggestion evidence."
        }
        require(preservedRankOneSuggestions.singleOrNull() == selectedSuggestion) {
            "Selected suggestion must match preserved rank-1 suggestion evidence."
        }

        val rankTwoSuggestion = confidenceAssessment.suggestions.suggestions.singleOrNull { it.rank == 2 }
        require(
            rankTwoSuggestion == null ||
                selectedSuggestion.teamCandidateScore.confidenceScore >=
                rankTwoSuggestion.teamCandidateScore.confidenceScore,
        ) {
            "Rank-2 suggestion confidence must not exceed rank-1 suggestion confidence."
        }
    }

    private fun evaluateRow(
        rowAssessment: RowTeamMatchConfidenceAssessment,
    ): RowSafetyEvaluation {
        val confidenceAssessment = rowAssessment.confidenceAssessment
        val selectedSuggestion = confidenceAssessment.selectedSuggestion
        val proposedTeamSlot = selectedSuggestion?.teamCandidateScore?.candidateTeamSlot

        if (selectedSuggestion == null) {
            return RowSafetyEvaluation(
                rowAssessment = rowAssessment,
                safetyStatus = TeamAssignmentSafetyStatus.MANUAL_REQUIRED,
                proposedTeamSlot = null,
                reasons = setOf(
                    TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER,
                    TeamAssignmentSafetyReason.NO_SUGGESTION,
                ),
                otherwiseSafeAutomaticCandidate = false,
            )
        }

        if (confidenceAssessment.tier != TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE) {
            return RowSafetyEvaluation(
                rowAssessment = rowAssessment,
                safetyStatus = when (confidenceAssessment.tier) {
                    TeamMatchConfidenceTier.CONFIRMATION_REQUIRED -> TeamAssignmentSafetyStatus.REVIEW_REQUIRED
                    TeamMatchConfidenceTier.MANUAL_REQUIRED -> TeamAssignmentSafetyStatus.MANUAL_REQUIRED
                    TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE -> error("Automatic tier handled separately.")
                },
                proposedTeamSlot = proposedTeamSlot,
                reasons = setOf(TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER),
                otherwiseSafeAutomaticCandidate = false,
            )
        }

        val reasons = linkedSetOf<TeamAssignmentSafetyReason>()
        if (selectedSuggestion.teamCandidateScore.contributingMatchCount < MINIMUM_SAFE_CONTRIBUTING_MATCH_COUNT) {
            reasons += TeamAssignmentSafetyReason.INSUFFICIENT_PLAYER_MATCH_COUNT
        }
        if (!hasSafeCandidateLead(confidenceAssessment)) {
            reasons += TeamAssignmentSafetyReason.INSUFFICIENT_CANDIDATE_LEAD
        }

        return RowSafetyEvaluation(
            rowAssessment = rowAssessment,
            safetyStatus = if (reasons.isEmpty()) {
                TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
            } else {
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED
            },
            proposedTeamSlot = proposedTeamSlot,
            reasons = reasons,
            otherwiseSafeAutomaticCandidate = reasons.isEmpty(),
        )
    }

    private fun hasSafeCandidateLead(confidenceAssessment: TeamMatchConfidenceAssessment): Boolean {
        val rankOneSuggestion = requireNotNull(confidenceAssessment.selectedSuggestion)
        val rankTwoSuggestion = confidenceAssessment.suggestions.suggestions.singleOrNull { it.rank == 2 }
            ?: return true
        val candidateLead =
            rankOneSuggestion.teamCandidateScore.confidenceScore -
                rankTwoSuggestion.teamCandidateScore.confidenceScore

        return candidateLead >= MINIMUM_SAFE_CANDIDATE_LEAD
    }

    private data class RowSafetyEvaluation(
        val rowAssessment: RowTeamMatchConfidenceAssessment,
        val safetyStatus: TeamAssignmentSafetyStatus,
        val proposedTeamSlot: Int?,
        val reasons: Set<TeamAssignmentSafetyReason>,
        val otherwiseSafeAutomaticCandidate: Boolean,
    ) {
        fun toResult(
            duplicatedOtherwiseSafeTeamSlots: Set<Int>,
        ): RowTeamAssignmentSafetyResult {
            val duplicateTeamCandidate = proposedTeamSlot in duplicatedOtherwiseSafeTeamSlots &&
                otherwiseSafeAutomaticCandidate
            val finalReasons = if (duplicateTeamCandidate) {
                reasons + TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE
            } else {
                reasons
            }

            return RowTeamAssignmentSafetyResult(
                rowIndex = rowAssessment.rowIndex,
                confidenceAssessment = rowAssessment.confidenceAssessment,
                safetyStatus = if (duplicateTeamCandidate) {
                    TeamAssignmentSafetyStatus.REVIEW_REQUIRED
                } else {
                    safetyStatus
                },
                proposedTeamSlot = proposedTeamSlot,
                reasons = finalReasons,
            )
        }
    }

    private val ROW_INDEX_RANGE = 0 until MAX_ROW_COUNT
    private const val MAX_ROW_COUNT = 12
    private const val MINIMUM_SAFE_CONTRIBUTING_MATCH_COUNT = 3
    private const val MINIMUM_SAFE_CANDIDATE_LEAD = 10
}
