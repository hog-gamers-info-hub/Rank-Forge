package com.hoggamers.rankforge.domain.matching

data class TeamCandidateScore(
    val candidateTeamSlot: Int,
    val confidenceScore: Int,
    val detectedPlayerCount: Int,
    val validDetectedPlayerCount: Int,
    val rosterPlayerCount: Int,
    val contributingMatchCount: Int,
    val averageMatchedPlayerScore: Int,
    val coverageScore: Int,
    val playerMatches: List<TeamCandidatePlayerMatch>,
)
