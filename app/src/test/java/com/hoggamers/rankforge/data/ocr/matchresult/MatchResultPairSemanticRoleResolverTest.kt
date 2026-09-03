package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultPairSemanticRoleResolverTest {
    private val resolver = MatchResultPairSemanticRoleResolver()

    @Test
    fun exactTwelveStillIdentifiesLower() {
        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(lowerEvidence("12" to 420.0), upperEvidence()).firstRoleOrNull(),
        )
    }

    @Test
    fun plausibleMisreadTwelveStillIdentifiesLower() {
        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(lowerEvidence("1Z" to 415.0), upperEvidence()).firstRoleOrNull(),
        )
    }

    @Test
    fun commonAnchorsResolveVisuallyHigherScreenshotAsLower() {
        val first = evidence(7 to 124.0, 9 to 283.5, 10 to 365.5, 11 to 446.0)
        val second = evidence(7 to 98.5, 9 to 260.0, 10 to 341.0, 11 to 421.0)

        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(first, second).secondRoleOrNull(),
        )
    }

    @Test
    fun majorityOfCommonAnchorsWinsThroughMixedNoise() {
        val first = evidence(6 to 40.0, 7 to 120.0, 8 to 200.0, 9 to 280.0, 10 to 360.0)
        val second = evidence(6 to 55.0, 7 to 105.0, 8 to 215.0, 9 to 265.0, 10 to 380.0)

        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(first, second).firstRoleOrNull(),
        )
    }

    @Test
    fun onlyPositionsPresentInBothScreenshotsAreCompared() {
        val first = evidence(6 to 40.0, 8 to 200.0, 10 to 360.0)
        val second = evidence(7 to 55.0, 8 to 215.0, 10 to 380.0, 11 to 460.0)

        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(first, second).firstRoleOrNull(),
        )
    }

    @Test
    fun positionSixIsNotRequired() {
        val first = evidence(7 to 120.0, 8 to 200.0, 9 to 280.0)
        val second = evidence(7 to 100.0, 8 to 220.0, 9 to 300.0)

        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(first, second).firstRoleOrNull(),
        )
    }

    @Test
    fun tiedVotesRemainUnresolved() {
        val first = evidence(7 to 124.0, 9 to 260.0)
        val second = evidence(7 to 98.5, 9 to 283.5)

        assertEquals(
            MatchResultPairSemanticRoleResolution.Unresolved,
            resolver.resolve(first, second),
        )
    }

    @Test
    fun noCommonTrustworthyAnchorsRemainUnresolved() {
        val first = evidence(6 to 40.0, 7 to 120.0)
        val second = evidence(8 to 220.0, 9 to 300.0)

        assertEquals(
            MatchResultPairSemanticRoleResolution.Unresolved,
            resolver.resolve(first, second),
        )
    }

    @Test
    fun unrelatedFourAndFiveCannotOverridePairRelativeResult() {
        val first = evidence(
            7 to 124.0,
            9 to 283.5,
            10 to 365.5,
            11 to 446.0,
        )
        val second = evidence(
            4 to 40.0,
            5 to 120.0,
            7 to 98.5,
            9 to 260.0,
            10 to 341.0,
            11 to 421.0,
        )

        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            resolver.resolve(first, second).secondRoleOrNull(),
        )
    }

    @Test
    fun singleScreenshotIsAssignedUpperWithoutSemanticEvidence() {
        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            MatchResultScreenshotRoleAssignment.forSingleScreenshot(),
        )
    }

    private fun MatchResultPairSemanticRoleResolution.firstRoleOrNull(): MatchResultScreenshotRole? =
        (this as? MatchResultPairSemanticRoleResolution.Resolved)?.firstRole

    private fun MatchResultPairSemanticRoleResolution.secondRoleOrNull(): MatchResultScreenshotRole? =
        (this as? MatchResultPairSemanticRoleResolution.Resolved)?.secondRole

    private fun upperEvidence() = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + listOf(
            observation("4", 320.0, 50),
            observation("5", 420.0, 50),
            observation("6", 40.0),
            observation("7", 120.0),
        ),
        imageDimensions = OcrImageDimensions(1_200, 500),
    )

    private fun lowerEvidence(twelve: Pair<String, Double>): MatchResultAutoCropEvidence =
        MatchResultAutoCropEvidence(
            observations = eliminationObservations() + listOf(
                observation("8", 80.0),
                observation("9", 160.0),
                observation("10", 240.0),
                observation(twelve.first, twelve.second),
            ),
            imageDimensions = OcrImageDimensions(1_200, 500),
        )

    private fun evidence(vararg anchors: Pair<Int, Double>): MatchResultAutoCropEvidence =
        MatchResultAutoCropEvidence(
            observations = eliminationObservations() + anchors.map { (position, centerY) ->
                observation(position.toString(), centerY)
            },
            imageDimensions = OcrImageDimensions(1_200, 500),
        )

    private fun eliminationObservations() = listOf(
        observation("Eliminations", 40.0, 250),
        observation("Eliminations", 220.0, 252),
        observation("Eliminations", 40.0, 520),
        observation("Eliminations", 220.0, 522),
        observation("Eliminations", 40.0, 820),
        observation("Eliminations", 220.0, 822),
        observation("Eliminations", 40.0, 1_070),
        observation("Eliminations", 220.0, 1_072),
    )

    private fun observation(text: String, centerY: Double, left: Int = 646) =
        MatchResultAutoCropObservation(
            text = text,
            boundingBox = RawOcrBoundingBox(left, (centerY - 15).toInt(), left + 38, (centerY + 15).toInt()),
        )
}
