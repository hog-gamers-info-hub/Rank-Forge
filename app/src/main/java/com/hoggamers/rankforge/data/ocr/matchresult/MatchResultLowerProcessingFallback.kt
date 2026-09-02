package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

/** Opt-in recovery used only after semantic role resolution is unresolved. */
internal class MatchResultLowerProcessingFallback(
    private val calculator: MatchResultPositionCropCalculator = MatchResultPositionCropCalculator(),
) {
    fun recover(
        semanticResolution: MatchResultSemanticRoleResolution,
        requestedRole: MatchResultScreenshotRole,
        evidence: MatchResultAutoCropEvidence,
        activeTeamCount: Int?,
    ): MatchResultPositionCropCalculationResult.Available? {
        if (
            semanticResolution !is MatchResultSemanticRoleResolution.Unresolved ||
            requestedRole != MatchResultScreenshotRole.MATCH_RESULT_LOWER
        ) return null

        val expectedPositions = when (activeTeamCount) {
            12 -> listOf(11, 12)
            11 -> listOf(11)
            else -> return null
        }
        return calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            expectedLowerPositions = expectedPositions,
            minimumRightPlacementAnchorCount = MINIMUM_ANCHOR_COUNT,
        ) as? MatchResultPositionCropCalculationResult.Available
    }

    private companion object {
        const val MINIMUM_ANCHOR_COUNT = 3
    }
}
