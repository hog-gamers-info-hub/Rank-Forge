package com.hoggamers.rankforge.domain.matching

data class PlayerNameSimilarityAssessment(
    val normalizedDetectedName: String?,
    val normalizedRosterName: String?,
    val distance: Int?,
    val maximumLength: Int,
    val similarityScore: Int,
    val comparisonType: PlayerNameComparisonType,
)
