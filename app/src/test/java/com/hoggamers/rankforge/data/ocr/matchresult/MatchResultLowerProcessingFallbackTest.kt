package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultLowerProcessingFallbackTest {
    private val fallback = MatchResultLowerProcessingFallback()
    private val calculator = MatchResultPositionCropCalculator()

    @Test
    fun unresolvedLowerWithFiveTerminalAnchorsRecoversBothRowsForTwelveTeams() {
        val result = recover(evidence(*anchors(6, 7, 8, 9, 10)), activeTeamCount = 12)

        assertEquals(listOf(11, 12), result?.crops?.map { it.position })
    }

    @Test
    fun unresolvedLowerWithNonConsecutiveAnchorsRecoversBothRows() {
        val result = recover(evidence(*anchors(6, 8, 10)), activeTeamCount = 12)

        assertEquals(listOf(11, 12), result?.crops?.map { it.position })
    }

    @Test
    fun unresolvedLowerWithOnlyTwoAnchorsIsRejected() {
        assertNull(recover(evidence(*anchors(9, 10)), activeTeamCount = 12))
    }

    @Test
    fun unresolvedLowerWithInconsistentAnchorsIsRejected() {
        assertNull(
            recover(
                evidence(
                    observation("6", 646, 25, 684, 55),
                    observation("7", 646, 105, 684, 135),
                    observation("8", 646, 225, 684, 255),
                    observation("9", 646, 265, 684, 295),
                    observation("10", 646, 345, 684, 375),
                ),
                activeTeamCount = 12,
            ),
        )
    }

    @Test
    fun unresolvedLowerWithElevenTeamsRecoversOnlyPositionEleven() {
        val result = recover(evidence(*anchors(6, 7, 8, 9, 10)), activeTeamCount = 11)

        assertEquals(listOf(11), result?.crops?.map { it.position })
    }

    @Test
    fun unresolvedLowerWithTenOrFewerTeamsIsRejected() {
        assertNull(recover(evidence(*anchors(6, 7, 8, 9, 10)), activeTeamCount = 10))
    }

    @Test
    fun unresolvedLowerWithUnknownTeamCountIsRejected() {
        assertNull(
            fallback.recover(
                semanticResolution = MatchResultSemanticRoleResolution.Unresolved,
                requestedRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                evidence = evidence(*anchors(6, 7, 8, 9, 10)),
                activeTeamCount = null,
            ),
        )
    }

    @Test
    fun resolvedRoleDoesNotInvokeFallback() {
        val evidence = evidence(*anchors(6, 7, 8, 9, 10))
        val geometry = calculator.calculate(
            evidence = evidence,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        )

        val result = fallback.recover(
            semanticResolution = MatchResultSemanticRoleResolution.Resolved(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                geometry = geometry as com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult.Available,
            ),
            requestedRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            evidence = evidence,
            activeTeamCount = 12,
        )

        assertNull(result)
    }

    @Test
    fun ambiguousRoleDoesNotInvokeFallback() {
        assertNull(
            fallback.recover(
                semanticResolution = MatchResultSemanticRoleResolution.Ambiguous,
                requestedRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                evidence = evidence(*anchors(6, 7, 8, 9, 10)),
                activeTeamCount = 12,
            ),
        )
    }

    @Test
    fun unresolvedUpperRequestDoesNotInvokeFallback() {
        assertNull(
            fallback.recover(
                semanticResolution = MatchResultSemanticRoleResolution.Unresolved,
                requestedRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                evidence = evidence(*anchors(6, 7, 8, 9, 10)),
                activeTeamCount = 12,
            ),
        )
    }

    private fun recover(
        evidence: MatchResultAutoCropEvidence,
        activeTeamCount: Int,
    ) = fallback.recover(
        semanticResolution = MatchResultSemanticRoleResolution.Unresolved,
        requestedRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        evidence = evidence,
        activeTeamCount = activeTeamCount,
    )

    private fun evidence(vararg placementObservations: MatchResultAutoCropObservation) =
        MatchResultAutoCropEvidence(
            observations = eliminationObservations() + placementObservations,
            imageDimensions = OcrImageDimensions(width = 1_200, height = 720),
        )

    private fun anchors(vararg positions: Int): Array<out MatchResultAutoCropObservation> =
        positions.map { position ->
            val top = 25 + (position - 6) * 80
            observation(position.toString(), 646, top, 684, top + 30)
        }.toTypedArray()

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
