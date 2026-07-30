package com.hoggamers.rankforge.domain.matching

object TeamMatchConfidenceTierClassifier {
    fun classify(
        suggestions: TopTeamCandidateSuggestions,
    ): TeamMatchConfidenceAssessment {
        validateSuggestionStructure(suggestions)

        val selectedSuggestion = suggestions.suggestions.singleOrNull { it.rank == 1 }
            ?: return TeamMatchConfidenceAssessment(
                tier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
                selectedSuggestion = null,
                suggestions = suggestions,
                reason = TeamMatchConfidenceReason.NO_SUGGESTIONS,
            )

        val confidenceScore = selectedSuggestion.teamCandidateScore.confidenceScore
        val tier = when {
            confidenceScore >= AUTOMATIC_CANDIDATE_THRESHOLD -> TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE
            confidenceScore >= CONFIRMATION_REQUIRED_THRESHOLD -> TeamMatchConfidenceTier.CONFIRMATION_REQUIRED
            else -> TeamMatchConfidenceTier.MANUAL_REQUIRED
        }
        val reason = when (tier) {
            TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE ->
                TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD
            TeamMatchConfidenceTier.CONFIRMATION_REQUIRED ->
                TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD
            TeamMatchConfidenceTier.MANUAL_REQUIRED ->
                TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD
        }

        return TeamMatchConfidenceAssessment(
            tier = tier,
            selectedSuggestion = selectedSuggestion,
            suggestions = suggestions,
            reason = reason,
        )
    }

    private fun validateSuggestionStructure(suggestions: TopTeamCandidateSuggestions) {
        val suggestionItems = suggestions.suggestions

        require(suggestionItems.size <= MAX_SUGGESTION_COUNT) {
            "Top team suggestions must include at most three suggestions."
        }
        require(suggestions.evaluatedCandidateCount >= suggestionItems.size) {
            "Evaluated candidate count must be at least the suggestion count."
        }

        if (suggestionItems.isEmpty()) {
            return
        }

        val ranks = suggestionItems.map { it.rank }
        require(ranks.toSet().size == ranks.size) {
            "Suggestion ranks must be unique."
        }
        require(ranks.count { it == 1 } == 1) {
            "Exactly one rank-1 suggestion is required."
        }
        require(ranks.sorted() == (1..suggestionItems.size).toList()) {
            "Suggestion ranks must be sequential starting at 1."
        }

        val suggestionsByRank = suggestionItems.associateBy { it.rank }
        (1 until suggestionItems.size).forEach { rank ->
            val higherRankedSuggestion = requireNotNull(suggestionsByRank[rank])
            val lowerRankedSuggestion = requireNotNull(suggestionsByRank[rank + 1])

            require(teamCandidateScoreOrdering.compare(
                higherRankedSuggestion.teamCandidateScore,
                lowerRankedSuggestion.teamCandidateScore,
            ) <= 0) {
                "Suggestion rank order must match candidate score order."
            }
        }
    }

    private val teamCandidateScoreOrdering: Comparator<TeamCandidateScore> =
        compareByDescending<TeamCandidateScore> { it.confidenceScore }
            .thenByDescending { it.contributingMatchCount }
            .thenByDescending { it.averageMatchedPlayerScore }
            .thenByDescending { it.coverageScore }
            .thenBy { it.candidateTeamSlot }

    private const val AUTOMATIC_CANDIDATE_THRESHOLD = 90
    private const val CONFIRMATION_REQUIRED_THRESHOLD = 75
    private const val MAX_SUGGESTION_COUNT = 3
}
