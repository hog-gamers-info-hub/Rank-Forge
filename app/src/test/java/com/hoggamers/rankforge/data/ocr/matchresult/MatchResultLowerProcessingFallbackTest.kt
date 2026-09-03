package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultLowerProcessingFallbackTest {
    private val fallback = MatchResultLowerProcessingFallback()

    @Test
    fun lowerWithFiveTerminalAnchorsRecoversBothRowsForTwelveTeams() {
        val result = recover(evidence(*anchors(6, 7, 8, 9, 10)))

        assertEquals(listOf(11, 12), result?.crops?.map { it.position })
    }

    @Test
    fun lowerWithNonConsecutiveAnchorsRecoversBothRows() {
        val result = recover(evidence(*anchors(6, 8, 10)))

        assertEquals(listOf(11, 12), result?.crops?.map { it.position })
    }

    @Test
    fun lowerWithOnlyTwoAnchorsIsRejected() {
        assertNull(recover(evidence(*anchors(9, 10))))
    }

    @Test
    fun lowerWithInconsistentAnchorsIsRejected() {
        assertNull(
            recover(
                evidence(
                    observation("6", 646, 25, 684, 55),
                    observation("7", 646, 105, 684, 135),
                    observation("8", 646, 225, 684, 255),
                    observation("9", 646, 265, 684, 295),
                    observation("10", 646, 345, 684, 375),
                ),
            ),
        )
    }

    @Test
    fun lowerWhenPositionTwelveDoesNotFitRecoversOnlyPositionEleven() {
        val result = recover(
            evidence(*anchors(6, 7, 8, 9, 10), imageHeight = 500),
        )

        assertEquals(listOf(11), result?.crops?.map { it.position })
    }

    @Test
    fun lowerInferenceDoesNotNeedRegisteredTeamCount() {
        assertEquals(
            listOf(11, 12),
            fallback.recover(evidence(*anchors(6, 7, 8, 9, 10)))?.crops?.map { it.position },
        )
    }

    private fun recover(
        evidence: MatchResultAutoCropEvidence,
    ) = fallback.recover(
        evidence = evidence,
    )

    private fun evidence(
        vararg placementObservations: MatchResultAutoCropObservation,
        imageHeight: Int = 720,
    ) =
        MatchResultAutoCropEvidence(
            observations = eliminationObservations() + placementObservations,
            imageDimensions = OcrImageDimensions(width = 1_200, height = imageHeight),
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
