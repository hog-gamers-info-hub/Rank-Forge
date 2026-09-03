package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

/** Recovery used after a screenshot has already been assigned the lower role. */
internal class MatchResultLowerProcessingFallback(
    private val calculator: MatchResultPositionCropCalculator = MatchResultPositionCropCalculator(),
    private val lowerEvidenceResolver: MatchResultLowerEvidenceResolver = MatchResultLowerEvidenceResolver(),
) {
    fun recover(
        evidence: MatchResultAutoCropEvidence,
    ): MatchResultPositionCropCalculationResult.Available? {
        val hasExistingPositionTwelveEvidence = lowerEvidenceResolver.hasExistingLowerEvidence(evidence)
        val minimumAnchorCount = if (hasExistingPositionTwelveEvidence) {
            0
        } else {
            MINIMUM_ANCHOR_COUNT
        }
        val twelvePositionCalculation = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            expectedLowerPositions = listOf(11, 12),
            minimumRightPlacementAnchorCount = minimumAnchorCount,
        )
        when (twelvePositionCalculation) {
            is MatchResultPositionCropCalculationResult.Available -> {
                return twelvePositionCalculation
            }

            is MatchResultPositionCropCalculationResult.Unavailable -> Unit
        }
        if (hasExistingPositionTwelveEvidence) return null

        val elevenPositionCalculation = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            expectedLowerPositions = listOf(11),
            minimumRightPlacementAnchorCount = MINIMUM_ANCHOR_COUNT,
        )
        return elevenPositionCalculation as? MatchResultPositionCropCalculationResult.Available
    }

    private companion object {
        const val MINIMUM_ANCHOR_COUNT = 3
    }
}
