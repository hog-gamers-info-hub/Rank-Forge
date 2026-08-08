package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultOcrFieldExtractorTest {
    private val extractor = MatchResultOcrFieldExtractor()

    @Test
    fun upperExtractionNeverEmitsPosition11() {
        val result = extractor.extract(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = 1156,
            cropHeight = 456,
            blocks = emptyList(),
        )

        assertTrue(result.rows.none { it.position == 11 })
    }

    @Test
    fun upperExtractionEmitsOnlyPositionsOneThroughTen() {
        val result = extractor.extract(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = 1156,
            cropHeight = 456,
            blocks = emptyList(),
        )

        assertEquals((1..10).toList(), result.rows.map { it.position })
    }

    @Test
    fun lowerRowAPlacement11EmitsPosition11() {
        val result = extractLower(element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")))

        assertEquals(listOf(11), result.rows.map { it.position })
        assertEquals(MatchResultOcrRowSource.LOWER_ROW_A, result.rows.single().source)
    }

    @Test
    fun lowerRowBPlacement12EmitsPosition12() {
        val result = extractLower(element("12", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_B_PLACEMENT")))

        assertEquals(listOf(12), result.rows.map { it.position })
        assertEquals(MatchResultOcrRowSource.LOWER_ROW_B, result.rows.single().source)
    }

    @Test
    fun lowerRowAPlacement10IsIgnored() {
        val result = extractLower(element("10", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")))

        assertTrue(result.rows.isEmpty())
        assertEquals(
            MatchResultOcrIgnoredLowerVisualRowReason.UPPER_OWNS_POSITION,
            result.ignoredLowerRows.single().reason,
        )
    }

    @Test
    fun lowerRowBPlacement11EmitsPosition11() {
        val result = extractLower(element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_B_PLACEMENT")))

        assertEquals(listOf(11), result.rows.map { it.position })
        assertEquals(MatchResultOcrRowSource.LOWER_ROW_B, result.rows.single().source)
    }

    @Test
    fun lowerExtractionDoesNotForcePosition12WhenAbsent() {
        val result = extractLower(element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")))

        assertEquals(listOf(11), result.rows.map { it.position })
        assertEquals(1, result.manualReviewRows.size)
        assertEquals(MatchResultOcrVisualRow.B, result.manualReviewRows.single().visualRow)
    }

    @Test
    fun lowerExtractionNeverEmitsPositionsTenOrBelow() {
        val result = extractLower(
            element("10", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element("7", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_B_PLACEMENT")),
        )

        assertTrue(result.rows.none { it.position <= 10 })
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun blankKillWithNonblankPlayerResolvesToZero() {
        val playerRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_1")
        val placementRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")
        val result = extractLower(
            element("11", placementRect),
            element("APX ANGELIC", playerRect),
        )

        val slot = result.rows.single().playerSlots.single()
        assertEquals("0", slot.kill.resolvedText)
        assertEquals(MatchResultOcrFieldStatus.ZERO_INFERRED_FROM_PLAYER_PRESENT, slot.kill.status)
        assertEquals("", slot.kill.ocrText)
    }

    @Test
    fun blankKillWithBlankPlayerRemainsAbsent() {
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
        )

        assertTrue(result.rows.single().playerSlots.isEmpty())
    }

    @Test
    fun letterOInsideKillFieldResolvesToZero() {
        val killRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_KILL_1")
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element("O", killRect),
        )

        val kill = result.rows.single().playerSlots.single().kill
        assertEquals("O", kill.ocrText)
        assertEquals("0", kill.resolvedText)
        assertEquals(MatchResultOcrFieldStatus.O_NORMALIZED_TO_0, kill.status)
    }

    @Test
    fun repeatedSameInputProducesDeterministicResult() {
        val input = listOf(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element("APX ANGELIC", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_1")),
        )

        assertEquals(extractLower(*input.toTypedArray()), extractLower(*input.toTypedArray()))
    }

    private fun extractLower(vararg elements: RawOcrElement): MatchResultOcrExtractionResult =
        extractor.extract(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            cropWidth = 1156,
            cropHeight = 452,
            blocks = listOf(
                RawOcrBlock(
                    text = elements.joinToString(" ") { it.text },
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    lines = listOf(
                        RawOcrLine(
                            text = elements.joinToString(" ") { it.text },
                            geometry = null,
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                            elements = elements.toList(),
                        ),
                    ),
                ),
            ),
        )

    private fun element(text: String, rect: MatchResultOcrRect): RawOcrElement =
        RawOcrElement(
            text = text,
            geometry = RawOcrGeometry(
                boundingBox = RawOcrBoundingBox(
                    left = rect.left.toInt(),
                    top = rect.top.toInt(),
                    right = rect.right.toInt(),
                    bottom = rect.bottom.toInt(),
                ),
                cornerPoints = null,
            ),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
        )
}

private fun MatchResultOcrCanonicalLayout.rect(id: String): MatchResultOcrRect =
    fields.first { it.id == id }.rect
