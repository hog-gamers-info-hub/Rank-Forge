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
        val result = parse(
            (1..12).map { placement ->
                line(
                    "Synthetic^$placement",
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
            (1..12).map { "Synthetic^$it" },
            result.rows.map { it.detectedName },
        )
        assertTrue(
            result.rows.all {
                it.status == PlayerNameParseStatus.DETECTED
            },
        )
    }

    @Test
    fun preservesObservedNameTextWithoutRosterOrTeamMatching() {
        val result = parse(
            listOf(
                line(
                    "  Unit-7^A  ",
                    1,
                ),
            ),
        )

        assertEquals(
            "Unit-7^A",
            result.rows.first().detectedName,
        )
        assertEquals(
            PlayerNameParseStatus.DETECTED,
            result.rows.first().status,
        )
    }

    @Test
    fun emptyOutputAndMissingGeometryProduceMissingOutcomesWithoutGuessing() {
        assertTrue(
            parse(emptyList()).rows.all {
                it.status == PlayerNameParseStatus.MISSING
            },
        )

        val result = parse(
            listOf(
                line(
                    "Synthetic^1",
                    1,
                    null,
                ),
            ),
        )

        assertTrue(
            result.rows.all {
                it.status == PlayerNameParseStatus.MISSING
            },
        )
        assertTrue(
            result.rows.all {
                it.detectedName == null
            },
        )
    }

    @Test
    fun ambiguousAndInvalidNameEvidenceRemainTyped() {
        val ambiguous = parse(
            listOf(
                line(
                    "Synthetic^A",
                    1,
                ),
                line(
                    "Synthetic^B",
                    1,
                ),
            ),
        )

        assertEquals(
            PlayerNameParseStatus.AMBIGUOUS,
            ambiguous.rows.first().status,
        )
        assertNull(
            ambiguous.rows.first().detectedName,
        )

        val empty = parse(
            listOf(
                line(
                    "   ",
                    1,
                ),
            ),
        )

        assertEquals(
            PlayerNameParseStatus.INVALID,
            empty.rows.first().status,
        )
        assertEquals(
            PlayerNameParseFailure.EMPTY_TEXT,
            empty.rows.first().failure,
        )
    }

    @Test
    fun placementKillAndRepeatedLabelEvidenceAreNotParsedAsNames() {
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

        val kills = RawOcrLine(
            text = "9",
            geometry = geometry(
                x = 462,
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
                kills,
                label,
            ),
        )

        assertTrue(
            result.rows.all {
                it.status == PlayerNameParseStatus.MISSING
            },
        )
        assertTrue(
            result.rows.all {
                it.detectedName == null
            },
        )
    }

    @Test
    fun numericTextInAPlayerNameZoneIsInvalidRatherThanAName() {
        val result = parse(
            listOf(
                line(
                    "12",
                    1,
                ),
            ),
        )

        assertEquals(
            PlayerNameParseStatus.INVALID,
            result.rows.first().status,
        )
        assertEquals(
            PlayerNameParseFailure.NUMERIC_TEXT,
            result.rows.first().failure,
        )
        assertNull(
            result.rows.first().detectedName,
        )
    }

    @Test
    fun croppedCandidateLocalGeometryMapsPlayerNameIntoCorrectRow() {
        val extraction = RawOcrExtractionResult.Extracted(
            sourceCandidate = candidate(),
            fullText = "RB-Speed",
            blocks = listOf(
                RawOcrBlock(
                    text = "RB-Speed",
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    lines = listOf(
                        RawOcrLine(
                            text = "RB-Speed",
                            geometry = geometry(
                                x = 192,
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

        val result = FixedLayoutPlayerNameParser().parse(
            PlayerNameParsingInput(
                extractions = listOf(extraction),
            ),
        )

        val placementOne = result.rows.single {
            it.expectedPlacementId == 1
        }

        assertEquals(
            PlayerNameParseStatus.DETECTED,
            placementOne.status,
        )
        assertEquals(
            "RB-Speed",
            placementOne.detectedName,
        )

        assertTrue(
            result.rows
                .filter {
                    it.expectedPlacementId != 1
                }
                .all {
                    it.status == PlayerNameParseStatus.MISSING
                },
        )
    }

    private fun parse(
        lines: List<RawOcrLine>,
    ): PlayerNameParsingResult =
        FixedLayoutPlayerNameParser().parse(
            PlayerNameParsingInput(
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
                    820
                } else {
                    192
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