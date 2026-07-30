package com.hoggamers.rankforge.domain.matching

import kotlin.math.max
import kotlin.math.min

object PlayerNameSimilarityMatcher {
    fun compare(
        detectedName: String?,
        rosterName: String?,
    ): PlayerNameSimilarityAssessment {
        val normalizedDetectedName = PlayerNameComparisonNormalizer.normalize(detectedName)
        val normalizedRosterName = PlayerNameComparisonNormalizer.normalize(rosterName)
        val maximumLength = maximumLength(normalizedDetectedName, normalizedRosterName)

        if (normalizedDetectedName == null || normalizedRosterName == null) {
            return PlayerNameSimilarityAssessment(
                normalizedDetectedName = normalizedDetectedName,
                normalizedRosterName = normalizedRosterName,
                distance = null,
                maximumLength = maximumLength,
                similarityScore = 0,
                comparisonType = PlayerNameComparisonType.INVALID_INPUT,
            )
        }

        if (detectedName != null && rosterName != null && detectedName == rosterName) {
            return exactAssessment(
                normalizedDetectedName = normalizedDetectedName,
                normalizedRosterName = normalizedRosterName,
                maximumLength = maximumLength,
                comparisonType = PlayerNameComparisonType.EXACT,
            )
        }

        if (normalizedDetectedName == normalizedRosterName) {
            return exactAssessment(
                normalizedDetectedName = normalizedDetectedName,
                normalizedRosterName = normalizedRosterName,
                maximumLength = maximumLength,
                comparisonType = PlayerNameComparisonType.NORMALIZED_EXACT,
            )
        }

        val distance = unrestrictedDamerauLevenshteinDistance(
            normalizedDetectedName.toCodePoints(),
            normalizedRosterName.toCodePoints(),
        )

        return PlayerNameSimilarityAssessment(
            normalizedDetectedName = normalizedDetectedName,
            normalizedRosterName = normalizedRosterName,
            distance = distance,
            maximumLength = maximumLength,
            similarityScore = similarityScore(maximumLength, distance),
            comparisonType = PlayerNameComparisonType.FUZZY,
        )
    }

    private fun exactAssessment(
        normalizedDetectedName: String,
        normalizedRosterName: String,
        maximumLength: Int,
        comparisonType: PlayerNameComparisonType,
    ): PlayerNameSimilarityAssessment = PlayerNameSimilarityAssessment(
        normalizedDetectedName = normalizedDetectedName,
        normalizedRosterName = normalizedRosterName,
        distance = 0,
        maximumLength = maximumLength,
        similarityScore = 100,
        comparisonType = comparisonType,
    )

    private fun maximumLength(
        normalizedDetectedName: String?,
        normalizedRosterName: String?,
    ): Int = max(
        normalizedDetectedName?.codePointCount(0, normalizedDetectedName.length) ?: 0,
        normalizedRosterName?.codePointCount(0, normalizedRosterName.length) ?: 0,
    )

    private fun similarityScore(maximumLength: Int, distance: Int): Int {
        if (maximumLength == 0) {
            return 0
        }

        return (((maximumLength - distance) * 100) / maximumLength).coerceIn(0, 100)
    }

    private fun unrestrictedDamerauLevenshteinDistance(
        detectedCodePoints: IntArray,
        rosterCodePoints: IntArray,
    ): Int {
        val detectedLength = detectedCodePoints.size
        val rosterLength = rosterCodePoints.size
        val maximumDistance = detectedLength + rosterLength
        val lastDetectedRowByCodePoint = mutableMapOf<Int, Int>()
        val distances = Array(detectedLength + 2) { IntArray(rosterLength + 2) }

        distances[0][0] = maximumDistance
        for (detectedIndex in 0..detectedLength) {
            distances[detectedIndex + 1][0] = maximumDistance
            distances[detectedIndex + 1][1] = detectedIndex
        }
        for (rosterIndex in 0..rosterLength) {
            distances[0][rosterIndex + 1] = maximumDistance
            distances[1][rosterIndex + 1] = rosterIndex
        }

        for (detectedIndex in 1..detectedLength) {
            var lastMatchingRosterColumn = 0

            for (rosterIndex in 1..rosterLength) {
                val lastMatchingDetectedRow = lastDetectedRowByCodePoint[rosterCodePoints[rosterIndex - 1]] ?: 0
                val transpositionSourceColumn = lastMatchingRosterColumn
                var substitutionCost = 1

                if (detectedCodePoints[detectedIndex - 1] == rosterCodePoints[rosterIndex - 1]) {
                    substitutionCost = 0
                    lastMatchingRosterColumn = rosterIndex
                }

                distances[detectedIndex + 1][rosterIndex + 1] = minOf(
                    distances[detectedIndex][rosterIndex] + substitutionCost,
                    distances[detectedIndex + 1][rosterIndex] + 1,
                    distances[detectedIndex][rosterIndex + 1] + 1,
                    distances[lastMatchingDetectedRow][transpositionSourceColumn] +
                        (detectedIndex - lastMatchingDetectedRow - 1) +
                        1 +
                        (rosterIndex - transpositionSourceColumn - 1),
                )
            }

            lastDetectedRowByCodePoint[detectedCodePoints[detectedIndex - 1]] = detectedIndex
        }

        return distances[detectedLength + 1][rosterLength + 1]
    }

    private fun String.toCodePoints(): IntArray = codePoints().toArray()
}
