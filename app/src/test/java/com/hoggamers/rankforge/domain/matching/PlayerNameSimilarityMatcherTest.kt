package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerNameSimilarityMatcherTest {
    @Test
    fun compare_classifiesIdenticalOriginalStringsAsExact() {
        assertAssessment(
            assessment = PlayerNameSimilarityMatcher.compare("Unit7", "Unit7"),
            expectedType = PlayerNameComparisonType.EXACT,
            expectedDetected = "un1t7",
            expectedRoster = "un1t7",
            expectedDistance = 0,
            expectedMaximumLength = 5,
            expectedScore = 100,
        )
    }

    @Test
    fun compare_classifiesNormalizationOnlyDifferencesAsNormalizedExact() {
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("Unit7", "unit7"),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "un1t7",
            "un1t7",
            0,
            5,
            100,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("Unit 7", "Unit   7"),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "un1t 7",
            "un1t 7",
            0,
            6,
            100,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("Unit-7", "Unit_7"),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "un1t 7",
            "un1t 7",
            0,
            6,
            100,
        )
    }

    @Test
    fun compare_usesApprovedConfusionMappingsOnlyThroughNormalization() {
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("O0o", "000"),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "000",
            "000",
            0,
            3,
            100,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("1Il", "111"),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "111",
            "111",
            0,
            3,
            100,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("5S", "55"),
            PlayerNameComparisonType.FUZZY,
            "5s",
            "55",
            1,
            2,
            50,
        )
    }

    @Test
    fun compare_calculatesBasicEditDistancesAndScores() {
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("abcd", "abxd"),
            PlayerNameComparisonType.FUZZY,
            "abcd",
            "abxd",
            1,
            4,
            75,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("abcd", "abcde"),
            PlayerNameComparisonType.FUZZY,
            "abcd",
            "abcde",
            1,
            5,
            80,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("abcde", "abcd"),
            PlayerNameComparisonType.FUZZY,
            "abcde",
            "abcd",
            1,
            5,
            80,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("abcd", "abxy"),
            PlayerNameComparisonType.FUZZY,
            "abcd",
            "abxy",
            2,
            4,
            50,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("abc", "abcdef"),
            PlayerNameComparisonType.FUZZY,
            "abc",
            "abcdef",
            3,
            6,
            50,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("a", "b"),
            PlayerNameComparisonType.FUZZY,
            "a",
            "b",
            1,
            1,
            0,
        )
    }

    @Test
    fun compare_supportsAdjacentTranspositionAndUnrestrictedDamerauLevenshtein() {
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("abcd", "acbd"),
            PlayerNameComparisonType.FUZZY,
            "abcd",
            "acbd",
            1,
            4,
            75,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("CA", "ABC"),
            PlayerNameComparisonType.FUZZY,
            "ca",
            "abc",
            2,
            3,
            33,
        )
    }

    @Test
    fun compare_usesNfcNormalizerAndUnicodeCodePointLengths() {
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("Caf\u00E9", "Cafe\u0301"),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "caf\u00E9",
            "caf\u00E9",
            0,
            4,
            100,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("a\uD801\uDC28b", "a\uD801\uDC28c"),
            PlayerNameComparisonType.FUZZY,
            "a\uD801\uDC28b",
            "a\uD801\uDC28c",
            1,
            3,
            66,
        )
    }

    @Test
    fun compare_reportsInvalidInputWithNullableDistanceAndAvailableMaximumLength() {
        assertAssessment(
            PlayerNameSimilarityMatcher.compare(null, "Alpha"),
            PlayerNameComparisonType.INVALID_INPUT,
            null,
            "a1pha",
            null,
            5,
            0,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("Alpha", null),
            PlayerNameComparisonType.INVALID_INPUT,
            "a1pha",
            null,
            null,
            5,
            0,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare(null, null),
            PlayerNameComparisonType.INVALID_INPUT,
            null,
            null,
            null,
            0,
            0,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("", "Alpha"),
            PlayerNameComparisonType.INVALID_INPUT,
            null,
            "a1pha",
            null,
            5,
            0,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("--__...", "Alpha"),
            PlayerNameComparisonType.INVALID_INPUT,
            null,
            "a1pha",
            null,
            5,
            0,
        )
        assertAssessment(
            PlayerNameSimilarityMatcher.compare("\u2605\u2605\u2606", "Alpha"),
            PlayerNameComparisonType.INVALID_INPUT,
            null,
            "a1pha",
            null,
            5,
            0,
        )
    }

    @Test
    fun compare_isSymmetricAndDeterministic() {
        val first = PlayerNameSimilarityMatcher.compare("abcd", "abxd")
        val reversed = PlayerNameSimilarityMatcher.compare("abxd", "abcd")
        val repeated = PlayerNameSimilarityMatcher.compare("abcd", "abxd")

        assertEquals(first.distance, reversed.distance)
        assertEquals(first.similarityScore, reversed.similarityScore)
        assertEquals(first, repeated)
    }

    @Test
    fun compare_preservesOriginalInputStrings() {
        val detected = " Alpha-Beta "
        val roster = "Alpha Beta"

        assertAssessment(
            PlayerNameSimilarityMatcher.compare(detected, roster),
            PlayerNameComparisonType.NORMALIZED_EXACT,
            "a1pha beta",
            "a1pha beta",
            0,
            10,
            100,
        )
        assertEquals(" Alpha-Beta ", detected)
        assertEquals("Alpha Beta", roster)
    }

    @Test
    fun compare_preservesEqualScoresWithoutTieBreaking() {
        val first = PlayerNameSimilarityMatcher.compare("abcd", "abxd")
        val second = PlayerNameSimilarityMatcher.compare("wxyz", "wxyq")

        assertEquals(PlayerNameComparisonType.FUZZY, first.comparisonType)
        assertEquals(PlayerNameComparisonType.FUZZY, second.comparisonType)
        assertEquals(1, first.distance)
        assertEquals(1, second.distance)
        assertEquals(4, first.maximumLength)
        assertEquals(4, second.maximumLength)
        assertEquals(75, first.similarityScore)
        assertEquals(75, second.similarityScore)
    }

    private fun assertAssessment(
        assessment: PlayerNameSimilarityAssessment,
        expectedType: PlayerNameComparisonType,
        expectedDetected: String?,
        expectedRoster: String?,
        expectedDistance: Int?,
        expectedMaximumLength: Int,
        expectedScore: Int,
    ) {
        assertEquals(expectedDetected, assessment.normalizedDetectedName)
        assertEquals(expectedRoster, assessment.normalizedRosterName)
        assertEquals(expectedDistance, assessment.distance)
        assertEquals(expectedMaximumLength, assessment.maximumLength)
        assertEquals(expectedScore, assessment.similarityScore)
        assertEquals(expectedType, assessment.comparisonType)
    }
}
