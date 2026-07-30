package com.hoggamers.rankforge.domain.matching

data class TeamMatchConfidenceAssessment(
    val tier: TeamMatchConfidenceTier,
    val selectedSuggestion: TopTeamCandidateSuggestion?,
    val suggestions: TopTeamCandidateSuggestions,
    val reason: TeamMatchConfidenceReason,
)
