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
        val result = parse(
            (1..12).map { placement ->
                line(
                    "${placement + 2}",
                    placement,
                )
            },
        )

        assertEquals(
            (1..12).toList(),
            result.rows.map { it.expectedPlacementId },
        )
        assertEquals(
            (0..4).toList(),
            result.rows.take(5).map { it.rowIndex },
        )
        assertEquals(
            (0..6).toList(),
            result.rows.drop(5).map { it.rowIndex },
        )
        assertTrue(
            result.rows.take(5).all {
                it.panelId == ScoreboardPanelId.LEFT
            },
        )
        assertTrue(
            result.rows.drop(5).all {
                it.panelId == ScoreboardPanelId.RIGHT
            },
        )
        assertEquals(
            (3..14).toList(),
            result.rows.map { it.detectedValue },
        )
        assertTrue(
            result.rows.all {
                it.status == KillParseStatus.DETECTED
            },
        )
        assertEquals(
            "3",
            result.rows.first().evidence.single().text,
        )
    }

    @Test
    fun emptyOutputAndMissingGeometryProduceMissingOutcomesWithoutGuessing() {
        assertTrue(
            parse(emptyList()).rows.all {
                it.status == KillParseStatus.MISSING
            },
        )

        val result = parse(
            listOf(
                line(
                    "3",
                    1,
                    null,
                ),
            ),
        )

        assertTrue(
            result.rows.all {
                it.status == KillParseStatus.MISSING
            },
        )
        assertTrue(
            result.rows.all {
                it.detectedValue == null
            },
        )
    }

    @Test
    fun conflictingAndDuplicateEvidenceRemainTypedPerRow() {
        val ambiguous = parse(
            listOf(
                line("3", 1),
                line("4", 1),
            ),
        )

        assertEquals(
            KillParseStatus.AMBIGUOUS,
            ambiguous.rows.first().status,
        )
        assertNull(
            ambiguous.rows.first().detectedValue,
        )

        val duplicate = parse(
            listOf(
                line("3", 1),
                line("3", 1),
            ),
        )

        assertEquals(
            KillParseStatus.DUPLICATE,
            duplicate.rows.first().status,
        )
        assertEquals(
            3,
            duplicate.rows.first().detectedValue,
        )
    }

    @Test
    fun invalidNumericAndMalformedEvidenceIsTypedWithoutCrashing() {
        assertInvalid(
            "-1",
            KillParseFailure.NEGATIVE_VALUE,
        )
        assertInvalid(
            "3.5",
            KillParseFailure.DECIMAL_VALUE,
        )
        assertInvalid(
            "not-a-kill",
            KillParseFailure.MALFORMED_TOKEN,
        )
        assertInvalid(
            "2147483648",
            KillParseFailure.INTEGER_OVERFLOW,
        )
    }

    @Test
    fun placementPlayerAndRepeatedLabelEvidenceAreNotParsedAsKills() {
        val placement = RawOcrLine(
            text = "1",
            geometry = geometry(
                x = 10,
                y = 12,
            ),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = emptyList(),
        )

        val playerName = RawOcrLine(
            text = "Synthetic^Unit",
            geometry = geometry(
                x = 192,
                y = 12,
            ),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = emptyList(),
        )

        val label = RawOcrLine(
            text = "Eliminations",
            geometry = geometry(
                x = 570,
                y = 12,
            ),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = emptyList(),
        )

        val result = parse(
            listOf(
                placement,
                playerName,
                label,
            ),
        )

        assertTrue(
            result.rows.all {
                it.status == KillParseStatus.MISSING
            },
        )
        assertTrue(
            result.rows.all {
                it.detectedValue == null
            },
        )
    }

    @Test
    fun repeatedKillValuesAcrossDifferentRowsAreAllowed() {
        val result = parse(
            listOf(
                line("7", 1),
                line("7", 2),
            ),
        )

        assertEquals(
            KillParseStatus.DETECTED,
            result.rows[0].status,
        )
        assertEquals(
            KillParseStatus.DETECTED,
            result.rows[1].status,
        )
        assertEquals(
            listOf(7, 7),
            result.rows.take(2).map { it.detectedValue },
        )
    }

    @Test
    fun croppedCandidateLocalGeometryMapsKillIntoCorrectRow() {
        val extraction = RawOcrExtractionResult.Extracted(
            sourceCandidate = candidate(),
            fullText = "2",
            blocks = listOf(
                RawOcrBlock(
                    text = "2",
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    lines = listOf(
                        RawOcrLine(
                            text = "2",
                            geometry = geometry(
                                x = 462,
                                y = 12,
                            ),
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                            elements = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val result = FixedLayoutKillParser().parse(
            KillParsingInput(
                extractions = listOf(extraction),
            ),
        )

        val placementOne = result.rows.single {
            it.expectedPlacementId == 1
        }

        assertEquals(
            KillParseStatus.DETECTED,
            placementOne.status,
        )
        assertEquals(
            2,
            placementOne.detectedValue,
        )

        assertTrue(
            result.rows
                .filter {
                    it.expectedPlacementId != 1
                }
                .all {
                    it.status == KillParseStatus.MISSING
                },
        )
    }

    private fun assertInvalid(
        text: String,
        expectedFailure: KillParseFailure,
    ) {
        val row = parse(
            listOf(
                line(
                    text,
                    1,
                ),
            ),
        ).rows.first()

        assertEquals(
            KillParseStatus.INVALID,
            row.status,
        )
        assertEquals(
            expectedFailure,
            row.failure,
        )
        assertNull(
            row.detectedValue,
        )
    }

    private fun parse(
        lines: List<RawOcrLine>,
    ): KillParsingResult =
        FixedLayoutKillParser().parse(
            KillParsingInput(
                extractions = listOf(
                    RawOcrExtractionResult.Extracted(
                        sourceCandidate = candidate(),
                        fullText = "",
                        blocks = listOf(
                            RawOcrBlock(
                                text = "",
                                geometry = null,
                                recognizedLanguage = null,
                                confidence = RawOcrConfidence.Unavailable,
                                lines = lines,
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun line(
        text: String,
        placement: Int,
        geometry: RawOcrGeometry? = geometryFor(placement),
    ): RawOcrLine =
        RawOcrLine(
            text = text,
            geometry = geometry,
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = emptyList(),
        )

    private fun geometryFor(
        placement: Int,
    ): RawOcrGeometry {
        val isRightPanel = placement >= 6

        val rowIndex =
            if (isRightPanel) {
                placement - 6
            } else {
                placement - 1
            }

        return geometry(
            x =
                if (isRightPanel) {
                    1_015
                } else {
                    462
                },
            y =
                12 +
                    if (isRightPanel) {
                        rowIndex * 66
                    } else {
                        rowIndex * 93
                    },
        )
    }

    private fun geometry(
        x: Int,
        y: Int,
    ): RawOcrGeometry =
        RawOcrGeometry(
            boundingBox = RawOcrBoundingBox(
                left = x,
                top = y,
                right = x + 10,
                bottom = y + 10,
            ),
            cornerPoints = null,
        )

    private fun candidate():
        OcrPreprocessingCandidate =
        OcrPreprocessingCandidate(
            order = 0,
            crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
            cropRect = OcrPixelRect(
                x = 208,
                y = 158,
                width = 1168,
                height = 468,
            ),
            image = object : OcrPreprocessingImage {
                override val width = 1168
                override val height = 468
            },
            appliedSteps = listOf(
                OcrPreprocessingStep.CROP,
            ),
            scaleFactor = null,
        )
}