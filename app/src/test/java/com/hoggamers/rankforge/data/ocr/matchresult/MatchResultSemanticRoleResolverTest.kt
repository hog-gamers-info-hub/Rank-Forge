package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultSemanticRoleResolverTest {
    private val resolver = MatchResultSemanticRoleResolver()

    @Test
    fun upperEvidenceResolvesUpperWithoutStoredRoleInput() {
        val evidence = upperEvidence()

        repeat(2) {
            val result = resolver.resolve(evidence) as MatchResultSemanticRoleResolution.Resolved
            assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER, result.role)
            assertEquals((1..10).toList(), result.geometry.crops.map { crop -> crop.position })
        }
    }

    @Test
    fun lowerEvidenceResolvesLower() {
        val result = resolver.resolve(lowerEvidence()) as MatchResultSemanticRoleResolution.Resolved

        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_LOWER, result.role)
        assertEquals(listOf(11, 12), result.geometry.crops.map { crop -> crop.position })
    }

    @Test
    fun positionElevenFallbackCannotResolveAnUpperRole() {
        val upper = upperEvidence()
        val result = resolver.resolve(
            upper.copy(
                observations = upper.observations + observation("11", 650, 420, 680, 450),
            ),
        ) as MatchResultSemanticRoleResolution.Resolved

        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER, result.role)
        assertEquals((1..10).toList(), result.geometry.crops.map { crop -> crop.position })
    }

    @Test
    fun noValidStructuralEvidenceIsUnresolved() {
        assertEquals(
            MatchResultSemanticRoleResolution.Unresolved,
            resolver.resolve(MatchResultAutoCropEvidence(emptyList(), OcrImageDimensions(1_200, 500))),
        )
    }

    @Test
    fun evidenceSatisfyingBothStrictHypothesesIsAmbiguous() {
        val upper = MatchResultAutoCropEvidence(
            observations = eliminationObservations() + listOf(
                observation("4", 50, 320, 75, 350),
                observation("5", 50, 420, 75, 450),
                observation("6", 650, 20, 680, 50),
                observation("7", 650, 90, 680, 120),
                observation("11", 650, 370, 680, 400),
                observation("12", 650, 440, 680, 470),
            ),
            imageDimensions = OcrImageDimensions(1_200, 500),
        )
        assertEquals(
            MatchResultSemanticRoleResolution.Ambiguous,
            resolver.resolve(
                upper,
            ),
        )
    }

    private fun upperEvidence(): MatchResultAutoCropEvidence = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + listOf(
            observation("4", 50, 320, 75, 350),
            observation("5", 50, 420, 75, 450),
            observation("6", 650, 20, 680, 50),
            observation("7", 650, 100, 680, 130),
        ),
        imageDimensions = OcrImageDimensions(1_200, 500),
    )

    private fun lowerEvidence(): MatchResultAutoCropEvidence = MatchResultAutoCropEvidence(
        observations = eliminationObservations() + listOf(
            observation("11", 646, 320, 684, 350),
            observation("12", 646, 400, 684, 430),
        ),
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
