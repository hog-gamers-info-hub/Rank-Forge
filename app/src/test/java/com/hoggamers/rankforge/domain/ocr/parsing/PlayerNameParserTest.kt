package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelId
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCrop
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNameParserTest {
    @Test
    fun extractsSyntheticNamesInFixedPanelRowOrder() {
        val result = parse((1..12).map { placement -> line("Synthetic^$placement", placement) })

        assertEquals((1..12).toList(), result.rows.map { it.expectedPlacementId })
        assertEquals((0..4).toList(), result.rows.take(5).map { it.rowIndex })
        assertEquals((0..6).toList(), result.rows.drop(5).map { it.rowIndex })
        assertTrue(result.rows.take(5).all { it.panelId == ScoreboardPanelId.LEFT })
        assertTrue(result.rows.drop(5).all { it.panelId == ScoreboardPanelId.RIGHT })
        assertEquals((1..12).map { "Synthetic^$it" }, result.rows.map { it.detectedName })
        assertTrue(result.rows.all { it.status == PlayerNameParseStatus.DETECTED })
    }

    @Test
    fun preservesObservedNameTextWithoutRosterOrTeamMatching() {
        val result = parse(listOf(line("  Unit-7^A  ", 1)))

        assertEquals("Unit-7^A", result.rows.first().detectedName)
        assertEquals(PlayerNameParseStatus.DETECTED, result.rows.first().status)
    }

    @Test
    fun emptyOutputAndMissingGeometryProduceMissingOutcomesWithoutGuessing() {
        assertTrue(parse(emptyList()).rows.all { it.status == PlayerNameParseStatus.MISSING })

        val result = parse(listOf(line("Synthetic^1", 1, null)))
        assertTrue(result.rows.all { it.status == PlayerNameParseStatus.MISSING })
        assertTrue(result.rows.all { it.detectedName == null })
    }

    @Test
    fun ambiguousAndInvalidNameEvidenceRemainTyped() {
        val ambiguous = parse(listOf(line("Synthetic^A", 1), line("Synthetic^B", 1)))
        assertEquals(PlayerNameParseStatus.AMBIGUOUS, ambiguous.rows.first().status)
        assertNull(ambiguous.rows.first().detectedName)

        val empty = parse(listOf(line("   ", 1)))
        assertEquals(PlayerNameParseStatus.INVALID, empty.rows.first().status)
        assertEquals(PlayerNameParseFailure.EMPTY_TEXT, empty.rows.first().failure)
    }

    @Test
    fun placementKillAndRepeatedLabelEvidenceAreNotParsedAsNames() {
        val placement = RawOcrLine("1", geometry(220, 170), null, RawOcrConfidence.Unavailable, emptyList())
        val kills = RawOcrLine("9", geometry(670, 170), null, RawOcrConfidence.Unavailable, emptyList())
        val label = RawOcrLine("Eliminations", geometry(770, 170), null, RawOcrConfidence.Unavailable, emptyList())

        val result = parse(listOf(placement, kills, label))

        assertTrue(result.rows.all { it.status == PlayerNameParseStatus.MISSING })
        assertTrue(result.rows.all { it.detectedName == null })
    }

    @Test
    fun numericTextInAPlayerNameZoneIsInvalidRatherThanAName() {
        val result = parse(listOf(line("12", 1)))

        assertEquals(PlayerNameParseStatus.INVALID, result.rows.first().status)
        assertEquals(PlayerNameParseFailure.NUMERIC_TEXT, result.rows.first().failure)
        assertNull(result.rows.first().detectedName)
    }

    private fun parse(lines: List<RawOcrLine>): PlayerNameParsingResult =
        FixedLayoutPlayerNameParser().parse(
            PlayerNameParsingInput(
                listOf(
                    RawOcrExtractionResult.Extracted(
                        candidate(),
                        "",
                        listOf(RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines)),
                    ),
                ),
            ),
        )

    private fun line(
        text: String,
        placement: Int,
        geometry: RawOcrGeometry? = geometryFor(placement),
    ): RawOcrLine = RawOcrLine(text, geometry, null, RawOcrConfidence.Unavailable, emptyList())

    private fun geometryFor(placement: Int): RawOcrGeometry {
        val isRightPanel = placement >= 6
        val rowIndex = if (isRightPanel) placement - 6 else placement - 1
        val x = if (isRightPanel) 1_050 else 400
        val y = 165 + if (isRightPanel) rowIndex * 66 else rowIndex * 93
        return geometry(x, y)
    }

    private fun geometry(x: Int, y: Int): RawOcrGeometry =
        RawOcrGeometry(RawOcrBoundingBox(x, y, x + 10, y + 10), null)

    private fun candidate(): OcrPreprocessingCandidate = OcrPreprocessingCandidate(
        order = 0,
        crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
        cropRect = OcrPixelRect(0, 0, 1, 1),
        image = object : OcrPreprocessingImage {
            override val width = 1
            override val height = 1
        },
        appliedSteps = listOf(OcrPreprocessingStep.CROP),
        scaleFactor = null,
    )
}
