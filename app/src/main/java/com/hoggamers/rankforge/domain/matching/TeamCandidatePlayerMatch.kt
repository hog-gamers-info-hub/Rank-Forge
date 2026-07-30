package com.hoggamers.rankforge.domain.matching

data class TeamCandidatePlayerMatch(
    val detectedPlayerIndex: Int,
    val rosterPlayerIndex: Int,
    val detectedOriginalName: String?,
    val rosterOriginalName: String?,
    val similarityAssessment: PlayerNameSimilarityAssessment,
    val contributesToScore: Boolean,
)
