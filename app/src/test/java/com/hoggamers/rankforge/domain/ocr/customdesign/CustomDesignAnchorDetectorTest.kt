package com.hoggamers.rankforge.domain.ocr.customdesign

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignAnchorDetectorTest {
    private val detector = CustomDesignAnchorDetector()

    @Test
    fun headerNormalizationAllowsCaseWhitespaceAndTerminalPunctuationButNotTypos() {
        val blocks = block(
            line(element("win", 100, 100, 140, 120)),
            line(element("ELIM", 160, 100, 210, 120)),
            line(element(" POS ", 230, 100, 270, 120)),
            line(element("TEAM   NAME", 290, 100, 390, 120)),
            line(element("TEAM NANE", 410, 100, 510, 120)),
        )

        val anchors = detector.detect(1080, 1350, labels(), blocks)

        assertTrue(CustomDesignAnchorField.WIN in anchors.columnX)
        assertTrue(CustomDesignAnchorField.TOTAL_KILLS in anchors.columnX)
        assertTrue(CustomDesignAnchorField.POSITION_POINTS in anchors.columnX)
        assertTrue(CustomDesignAnchorField.TEAM_NAME in anchors.columnX)
        assertFalse(CustomDesignAnchorField.TOTAL_POINTS in anchors.columnX)
    }

    @Test
    fun contiguousElementsOnOneLineCanFormAHeader() {
        val anchors = detector.detect(
            1080,
            1350,
            labels(teamName = "TEAM NAME"),
            block(
                line(
                    element("TEAM", 100, 100, 180, 130),
                    element("NAME", 190, 100, 270, 130),
                ),
            ),
        )

        assertEquals(185f, anchors.columnX[CustomDesignAnchorField.TEAM_NAME])
    }

    @Test
    fun multipleCredibleHeaderMatchesOmitThatField() {
        val result = detector.detectDetailed(
            1080,
            1350,
            labels(win = "WIN"),
            block(
                line(element("WIN", 100, 100, 140, 120)),
                line(element("WIN", 300, 100, 340, 120)),
            ),
        )

        assertFalse(CustomDesignAnchorField.WIN in result.anchors.columnX)
        assertTrue(CustomDesignAnchorField.WIN in result.ambiguousFields)
    }

    @Test
    fun rankParserAcceptsOnlyOneThroughTwelveWithOptionalLeadingZero() {
        listOf("1" to 1, "01" to 1, "09" to 9, "10" to 10, "12" to 12).forEach { (text, expected) ->
            val anchors = detector.detect(
                1080,
                1350,
                labels(win = "WIN"),
                block(
                    line(element("WIN", 300, 100, 340, 120)),
                    line(element(text, 100, 200, 120, 230)),
                ),
            )
            assertEquals(setOf(expected), anchors.rowY.keys)
        }

        listOf("0", "13", "99", "1A", "A1", "1.5").forEach { text ->
            val anchors = detector.detect(
                1080,
                1350,
                labels(win = "WIN"),
                block(
                    line(element("WIN", 300, 100, 340, 120)),
                    line(element(text, 100, 200, 120, 230)),
                ),
            )
            assertTrue("Unexpected rank candidate: $text", anchors.rowY.isEmpty())
        }
    }

    @Test
    fun leadingZeroRanksResolveToTheSameRanksAsNormalFormatting() {
        val normal = detectRanks((1..12).map { it.toString() })
        val leadingZero = detectRanks((1..12).map { "%02d".format(it) })
        val mixed = detectRanks(
            listOf("01", "2", "03", "4", "05", "6", "07", "8", "09", "10", "11", "12"),
        )

        assertEquals((1..12).toSet(), normal)
        assertEquals(normal, leadingZero)
        assertEquals(normal, mixed)
    }

    @Test
    fun cornerGeometryUsesAveragePointCenter() {
        val anchors = detector.detect(
            1080,
            1350,
            labels(teamName = "TEAM NAME"),
            block(
                line(
                    element(
                        text = "TEAM NAME",
                        left = 100,
                        top = 200,
                        right = 200,
                        bottom = 240,
                        corners = listOf(
                            RawOcrPoint(100, 200),
                            RawOcrPoint(200, 200),
                            RawOcrPoint(200, 240),
                            RawOcrPoint(100, 240),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(150f, anchors.columnX[CustomDesignAnchorField.TEAM_NAME])
    }

    @Test
    fun partialHeadersAndRowsRemainValidWithoutInterpolatingMissingRank() {
        val result = detector.detectDetailed(
            1080,
            1350,
            labels(positionPoints = "POS."),
            block(
                line(element("TEAM NAME", 300, 100, 400, 120)),
                line(element("WIN", 450, 100, 490, 120)),
                line(element("ELIM.", 520, 100, 570, 120)),
                line(element("TOTAL", 600, 100, 650, 120)),
                line(element("1", 100, 200, 120, 230)),
                line(element("2", 100, 300, 120, 330)),
                line(element("3", 100, 400, 120, 430)),
                line(element("5", 100, 600, 120, 630)),
            ),
        )

        assertEquals(4, result.anchors.columnX.size)
        assertEquals(setOf(1, 2, 3, 5), result.anchors.rowY.keys)
        assertFalse(4 in result.anchors.rowY)
    }

    @Test
    fun semanticCoordinatesRemainInOriginalSourceSpace() {
        val anchors = detector.detect(
            1080,
            1350,
            labels(win = "WIN"),
            block(line(element("WIN", 732, 495, 752, 515))),
        )

        assertEquals(1080, anchors.sourceWidth)
        assertEquals(1350, anchors.sourceHeight)
        assertEquals(742f, anchors.columnX[CustomDesignAnchorField.WIN])
    }

    @Test
    fun duplicateRankCandidatesRemainAmbiguousInsteadOfBeingChosen() {
        val result = detector.detectDetailed(
            1080,
            1350,
            labels(win = "WIN"),
            block(
                line(element("WIN", 300, 100, 340, 120)),
                line(element("1", 100, 200, 120, 230)),
                line(element("2", 100, 300, 120, 330)),
                line(element("3", 100, 400, 120, 430)),
                line(element("4", 100, 500, 120, 530)),
                line(element("04", 100, 505, 120, 535)),
                line(element("5", 100, 600, 120, 630)),
            ),
        )

        assertFalse(4 in result.anchors.rowY)
        assertTrue(4 in result.ambiguousRanks)
    }

    private fun labels(
        teamName: String = "TEAM NAME",
        win: String = "WIN",
        totalKills: String = "ELIM.",
        positionPoints: String = "POS.",
        totalPoints: String = "TOTAL",
    ) = CustomDesignOcrLabels(teamName, win, totalKills, positionPoints, totalPoints)

    private fun detectRanks(rankTexts: List<String>): Set<Int> = detector.detect(
        1080,
        1350,
        labels(win = "WIN"),
        block(
            line(element("WIN", 300, 100, 340, 120)),
            *rankTexts.mapIndexed { index, text ->
                line(element(text, 100, 200 + index * 50, 120, 230 + index * 50))
            }.toTypedArray(),
        ),
    ).rowY.keys

    private fun block(vararg lines: RawOcrLine) = listOf(
        RawOcrBlock(
            text = lines.joinToString(" ") { it.text },
            geometry = null,
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            lines = lines.toList(),
        ),
    )

    private fun line(vararg elements: RawOcrElement) = RawOcrLine(
        text = elements.joinToString(" ") { it.text },
        geometry = null,
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        elements = elements.toList(),
    )

    private fun element(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        corners: List<RawOcrPoint>? = null,
    ) = RawOcrElement(
        text = text,
        geometry = RawOcrGeometry(
            boundingBox = RawOcrBoundingBox(left, top, right, bottom),
            cornerPoints = corners,
        ),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
    )
}
