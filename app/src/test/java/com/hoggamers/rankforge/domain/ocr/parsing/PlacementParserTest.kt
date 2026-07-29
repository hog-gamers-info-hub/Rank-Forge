package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.*
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.preprocessing.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementParserTest {
    @Test fun detectsOneThroughTwelveInFixedPanelRowOrder() {
        val rows = (1..12).map { placement -> line("$placement", placement) }
        val result = parse(rows)
        assertEquals((1..12).toList(), result.rows.map { it.expectedPlacementId })
        assertEquals((1..5).toList(), result.rows.take(5).map { it.detectedValue })
        assertEquals((6..12).toList(), result.rows.drop(5).map { it.detectedValue })
        assertTrue(result.rows.all { it.status == PlacementParseStatus.DETECTED })
    }

    @Test fun emptyAndMissingGeometryEvidenceAreMissingWithoutGuessing() {
        assertTrue(parse(emptyList()).rows.all { it.status == PlacementParseStatus.MISSING })
        assertEquals(PlacementParseStatus.MISSING, parse(listOf(line("1", 1, null))).rows.first().status)
    }

    @Test fun duplicateOutOfRangeAndMalformedTokensAreTyped() {
        val duplicate = parse(listOf(line("1", 1), line("1", 2)))
        assertEquals(PlacementParseStatus.DUPLICATE, duplicate.rows[0].status)
        assertEquals(PlacementParseStatus.DUPLICATE, duplicate.rows[1].status)
        assertEquals(PlacementParseStatus.INVALID, parse(listOf(line("13", 1))).rows.first().status)
        assertEquals(PlacementParseStatus.INVALID, parse(listOf(line("x1", 1))).rows.first().status)
    }

    @Test fun nonPlacementZoneTextIsIgnored() {
        val nameZone = RawOcrLine("8", geometry(400, 170), null, RawOcrConfidence.Unavailable, emptyList())
        val killZone = RawOcrLine("9", geometry(500, 170), null, RawOcrConfidence.Unavailable, emptyList())
        assertTrue(parse(listOf(nameZone, killZone)).rows.all { it.status == PlacementParseStatus.MISSING })
    }

    private fun parse(lines: List<RawOcrLine>) = FixedLayoutPlacementParser().parse(
        PlacementParsingInput(listOf(RawOcrExtractionResult.Extracted(candidate(), "", listOf(RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines))))),
    )

    private fun line(text: String, placement: Int, geometry: RawOcrGeometry? = geometryFor(placement)) =
        RawOcrLine(text, geometry, null, RawOcrConfidence.Unavailable, emptyList())

    private fun geometryFor(placement: Int): RawOcrGeometry {
        val right = placement >= 6; val index = if (right) placement - 6 else placement - 1
        return geometry(if (right) 870 else 215, 165 + if (right) index * 66 else index * 93)
    }
    private fun geometry(x: Int, y: Int) = RawOcrGeometry(RawOcrBoundingBox(x, y, x + 10, y + 10), null)
    private fun candidate() = OcrPreprocessingCandidate(0, OcrPreprocessingCrop.OVERALL_SCOREBOARD, OcrPixelRect(0, 0, 1, 1), object : OcrPreprocessingImage { override val width = 1; override val height = 1 }, listOf(OcrPreprocessingStep.CROP), null)
}
