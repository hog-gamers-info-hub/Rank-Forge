package com.hoggamers.rankforge.domain.matching

data class TopTeamCandidateSuggestions(
    val detectedPlayerCount: Int,
    val evaluatedCandidateCount: Int,
    val suggestions: List<TopTeamCandidateSuggestion>,
)
