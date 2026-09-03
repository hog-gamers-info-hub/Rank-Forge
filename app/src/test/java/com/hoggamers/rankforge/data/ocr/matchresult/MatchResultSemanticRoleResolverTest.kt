package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultLowerEvidenceResolverTest {
    private val resolver = MatchResultLowerEvidenceResolver()

    @Test
    fun exactPositionTwelveEvidenceIdentifiesLower() {
        assertEquals(true, resolver.hasExistingLowerEvidence(lowerEvidence()))
    }

    @Test
    fun exactTwelveInPlacementColumnIdentifiesLowerWhenElevenIsMissing() {
        assertEquals(
            true,
            resolver.hasExistingLowerEvidence(
                lowerAnchoredEvidence(observation("12", 646, 400, 684, 430)),
            ),
        )
    }

    @Test
    fun exactTwelveOutsidePlacementColumnDoesNotResolveLower() {
        assertEquals(
            false,
            resolver.hasExistingLowerEvidence(lowerAnchoredEvidence(observation("12", 200, 400, 238, 430))),
        )
    }

    @Test
    fun shortMisreadAtExpectedPositionTwelveIdentifiesLower() {
        assertEquals(
            true,
            resolver.hasExistingLowerEvidence(
                lowerAnchoredEvidence(observation("1Z", 646, 400, 684, 430)),
            ),
        )
    }

    @Test
    fun shortTextInPlacementColumnAtWrongVerticalPositionDoesNotResolveLower() {
        assertEquals(
            false,
            resolver.hasExistingLowerEvidence(lowerAnchoredEvidence(observation("AB", 646, 320, 684, 350))),
        )
    }

    @Test
    fun shortTextNearExpectedPositionTwelveInWrongColumnDoesNotResolveLower() {
        assertEquals(
            false,
            resolver.hasExistingLowerEvidence(lowerAnchoredEvidence(observation("AB", 800, 400, 838, 430))),
        )
    }

    @Test
    fun missingPositionTwelveElementDoesNotResolveLowerFromEmptySpace() {
        assertEquals(
            false,
            resolver.hasExistingLowerEvidence(
                lowerAnchoredEvidence(observation("11", 646, 320, 684, 350)),
            ),
        )
    }

    @Test
    fun nonConsecutiveAnchorsCanValidateMisreadPositionTwelve() {
        val evidence = MatchResultAutoCropEvidence(
            observations = eliminationObservations() + listOf(
                observation("8", 646, 80, 684, 110),
                observation("10", 646, 240, 684, 270),
                observation("1Z", 646, 400, 684, 430),
            ),
            imageDimensions = OcrImageDimensions(1_200, 500),
        )

        assertEquals(true, resolver.hasExistingLowerEvidence(evidence))
    }

    @Test
    fun noValidStructuralEvidenceHasNoLowerEvidence() {
        assertEquals(
            false,
            resolver.hasExistingLowerEvidence(
                MatchResultAutoCropEvidence(emptyList(), OcrImageDimensions(1_200, 500)),
            ),
        )
    }

    private fun lowerEvidence(): MatchResultAutoCropEvidence = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + listOf(
            observation("11", 646, 320, 684, 350),
            observation("12", 646, 400, 684, 430),
        ),
        imageDimensions = OcrImageDimensions(1_200, 500),
    )

    private fun lowerAnchoredEvidence(
        vararg observations: MatchResultAutoCropObservation,
    ): MatchResultAutoCropEvidence = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + listOf(
            observation("8", 646, 80, 684, 110),
            observation("9", 646, 160, 684, 190),
            observation("10", 646, 240, 684, 270),
        ) + observations,
        imageDimensions = OcrImageDimensions(1_200, 500),
    )

    private fun eliminationObservations() = listOf(
        observation("Eliminations", 250, 40, 340, 70),
        observation("Eliminations", 252, 220, 342, 250),
        observation("Eliminations", 520, 40, 620, 70),
        observation("Eliminations", 522, 220, 618, 250),
        observation("Eliminations", 820, 40, 930, 70),
        observation("Eliminations", 822, 220, 928, 250),
        observation("Eliminations", 1_070, 40, 1_190, 70),
        observation("Eliminations", 1_072, 220, 1_188, 250),
    )

    private fun observation(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MatchResultAutoCropObservation(text, RawOcrBoundingBox(left, top, right, bottom))
}
