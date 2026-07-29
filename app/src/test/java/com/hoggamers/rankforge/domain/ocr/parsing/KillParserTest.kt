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

class KillParserTest {
    @Test
    fun extractsKillValuesInFixedPanelRowOrder() {
        val result = parse((1..12).map { placement -> line("${placement + 2}", placement) })

        assertEquals((1..12).toList(), result.rows.map { it.expectedPlacementId })
        assertEquals((0..4).toList(), result.rows.take(5).map { it.rowIndex })
        assertEquals((0..6).toList(), result.rows.drop(5).map { it.rowIndex })
        assertTrue(result.rows.take(5).all { it.panelId == ScoreboardPanelId.LEFT })
        assertTrue(result.rows.drop(5).all { it.panelId == ScoreboardPanelId.RIGHT })
        assertEquals((3..14).toList(), result.rows.map { it.detectedValue })
        assertTrue(result.rows.all { it.status == KillParseStatus.DETECTED })
        assertEquals("3", result.rows.first().evidence.single().text)
    }

    @Test
    fun emptyOutputAndMissingGeometryProduceMissingOutcomesWithoutGuessing() {
        assertTrue(parse(emptyList()).rows.all { it.status == KillParseStatus.MISSING })

        val result = parse(listOf(line("3", 1, null)))
        assertTrue(result.rows.all { it.status == KillParseStatus.MISSING })
        assertTrue(result.rows.all { it.detectedValue == null })
    }

    @Test
    fun conflictingAndDuplicateEvidenceRemainTypedPerRow() {
        val ambiguous = parse(listOf(line("3", 1), line("4", 1)))
        assertEquals(KillParseStatus.AMBIGUOUS, ambiguous.rows.first().status)
        assertNull(ambiguous.rows.first().detectedValue)

        val duplicate = parse(listOf(line("3", 1), line("3", 1)))
        assertEquals(KillParseStatus.DUPLICATE, duplicate.rows.first().status)
        assertEquals(3, duplicate.rows.first().detectedValue)
    }

    @Test
    fun invalidNumericAndMalformedEvidenceIsTypedWithoutCrashing() {
        assertInvalid("-1", KillParseFailure.NEGATIVE_VALUE)
        assertInvalid("3.5", KillParseFailure.DECIMAL_VALUE)
        assertInvalid("not-a-kill", KillParseFailure.MALFORMED_TOKEN)
        assertInvalid("2147483648", KillParseFailure.INTEGER_OVERFLOW)
    }

    @Test
    fun placementPlayerAndRepeatedLabelEvidenceAreNotParsedAsKills() {
        val placement = RawOcrLine("1", geometry(220, 170), null, RawOcrConfidence.Unavailable, emptyList())
        val playerName = RawOcrLine("Synthetic^Unit", geometry(400, 170), null, RawOcrConfidence.Unavailable, emptyList())
        val label = RawOcrLine("Eliminations", geometry(770, 170), null, RawOcrConfidence.Unavailable, emptyList())

        val result = parse(listOf(placement, playerName, label))

        assertTrue(result.rows.all { it.status == KillParseStatus.MISSING })
        assertTrue(result.rows.all { it.detectedValue == null })
    }

    @Test
    fun repeatedKillValuesAcrossDifferentRowsAreAllowed() {
        val result = parse(listOf(line("7", 1), line("7", 2)))

        assertEquals(KillParseStatus.DETECTED, result.rows[0].status)
        assertEquals(KillParseStatus.DETECTED, result.rows[1].status)
        assertEquals(listOf(7, 7), result.rows.take(2).map { it.detectedValue })
    }

    private fun assertInvalid(text: String, expectedFailure: KillParseFailure) {
        val row = parse(listOf(line(text, 1))).rows.first()
        assertEquals(KillParseStatus.INVALID, row.status)
        assertEquals(expectedFailure, row.failure)
        assertNull(row.detectedValue)
    }

    private fun parse(lines: List<RawOcrLine>): KillParsingResult = FixedLayoutKillParser().parse(
        KillParsingInput(
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
        val x = if (isRightPanel) 1_240 else 670
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
