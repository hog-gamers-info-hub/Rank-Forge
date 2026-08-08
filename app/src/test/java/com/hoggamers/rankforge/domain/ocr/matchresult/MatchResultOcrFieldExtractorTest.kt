package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrSymbol
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
    fun lowerPlayerFieldDoesNotIncludeLeadingEliminationContamination() {
        val playerRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_3")
        val prefixAndName = element(
            text = "EliminationARX",
            rect = MatchResultOcrRect(920.0, playerRect.top, 1074.0, playerRect.bottom),
            symbols = symbolChars("Elimination", 920.0, playerRect.top) +
                symbolChars("ARX", 960.0, playerRect.top),
        )
        val surname = element(
            text = "MACHINE",
            rect = MatchResultOcrRect(1000.0, playerRect.top, 1074.0, playerRect.bottom),
            symbols = symbolChars("MACHINE", 1000.0, playerRect.top),
        )
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            prefixAndName,
            surname,
        )

        assertEquals("ARX MACHINE", result.rows.single().playerSlots.single().player.resolvedText)
    }

    @Test
    fun lowerRightPlayerPreservesNoisyLeadingCharacterWhenFieldLocal() {
        val playerRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_4")
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element(
                text = "nPX",
                rect = MatchResultOcrRect(playerRect.left, playerRect.top, 1000.0, playerRect.bottom),
                symbols = symbolChars("nPX", playerRect.left, playerRect.top),
            ),
            element(
                text = "ZENOX",
                rect = MatchResultOcrRect(1000.0, playerRect.top, playerRect.right, playerRect.bottom),
                symbols = symbolChars("ZENOX", 1000.0, playerRect.top),
            ),
        )

        assertEquals("nPX ZENOX", result.rows.single().playerSlots.single().player.resolvedText)
    }

    @Test
    fun killDigitTouchingRoiEdgeIsAccepted() {
        val killRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_KILL_1")
        val edgeDigit = element(
            text = "1",
            rect = MatchResultOcrRect(killRect.left - 2.0, killRect.top, killRect.left, killRect.bottom),
            symbols = listOf(
                RawOcrSymbol(
                    text = "1",
                    geometry = RawOcrGeometry(
                        boundingBox = RawOcrBoundingBox(
                            killRect.left.toInt() - 2,
                            killRect.top.toInt(),
                            killRect.left.toInt(),
                            killRect.bottom.toInt(),
                        ),
                        cornerPoints = null,
                    ),
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                ),
            ),
        )
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            edgeDigit,
        )

        assertEquals("1", result.rows.single().playerSlots.single().kill.resolvedText)
    }

    @Test
    fun repeatedSameInputProducesDeterministicResult() {
        val input = listOf(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element("APX ANGELIC", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_1")),
        )

        assertEquals(extractLower(*input.toTypedArray()), extractLower(*input.toTypedArray()))
    }

    @Test
    fun playerFieldPrefersElementReadingOrderWhenSymbolsExist() {
        val playerRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_4")
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element(
                text = "nPX",
                rect = MatchResultOcrRect(playerRect.left, playerRect.top, 1000.0, playerRect.bottom),
                symbols = symbolChars("nPX", 1040.0, playerRect.top),
            ),
            element(
                text = "ZENOX",
                rect = MatchResultOcrRect(1000.0, playerRect.top, playerRect.right, playerRect.bottom),
                symbols = symbolChars("ZENOX", playerRect.left, playerRect.top),
            ),
        )

        assertEquals("nPX ZENOX", result.rows.single().playerSlots.single().player.resolvedText)
    }
    @Test
    fun playerFieldStripsTruncatedEliminatioPrefix() {
        val playerRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_4")
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element(
                text = "Eliminatiok30 CRIMINAL",
                rect = playerRect,
            ),
        )

        assertEquals("k30 CRIMINAL", result.rows.single().playerSlots.single().player.resolvedText)
    }

    @Test
    fun playerFieldIgnoresWeakEdgeIntersectionOutsideRoi() {
        val playerRect = MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLAYER_1")
        val result = extractLower(
            element("11", MatchResultOcrCanonicalLayouts.lower.rect("LOWER_ROW_A_PLACEMENT")),
            element(
                text = "MAFI-Boss",
                rect = MatchResultOcrRect(
                    left = playerRect.left,
                    top = playerRect.top,
                    right = playerRect.right - 10.0,
                    bottom = playerRect.bottom,
                ),
            ),
            element(
                text = "HACKERBoss",
                rect = MatchResultOcrRect(
                    left = playerRect.right - 1.0,
                    top = playerRect.top,
                    right = playerRect.right + 100.0,
                    bottom = playerRect.bottom,
                ),
            ),
        )

        assertEquals("MAFI-Boss", result.rows.single().playerSlots.single().player.resolvedText)
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

    private fun element(
        text: String,
        rect: MatchResultOcrRect,
        symbols: List<RawOcrSymbol> = emptyList(),
    ): RawOcrElement =
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
            symbols = symbols,
        )

    private fun symbolChars(text: String, startX: Double, top: Double): List<RawOcrSymbol> =
        text.mapIndexed { index, char ->
            val left = startX + index * 3.0
            RawOcrSymbol(
                text = char.toString(),
                geometry = RawOcrGeometry(
                    boundingBox = RawOcrBoundingBox(
                        left = left.toInt(),
                        top = top.toInt(),
                        right = left.toInt() + 2,
                        bottom = top.toInt() + 20,
                    ),
                    cornerPoints = null,
                ),
                recognizedLanguage = null,
                confidence = RawOcrConfidence.Unavailable,
            )
        }
}

private fun MatchResultOcrCanonicalLayout.rect(id: String): MatchResultOcrRect =
    fields.first { it.id == id }.rect

