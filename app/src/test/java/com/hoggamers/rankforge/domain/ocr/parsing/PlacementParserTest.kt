package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCrop
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementParserTest {

    @Test
    fun detectsOneThroughTwelveInFixedPanelRowOrder() {
        val rows = (1..12).map { placement ->
            line("$placement", placement)
        }

        val result = parse(rows)

        assertEquals(
            (1..12).toList(),
            result.rows.map { it.expectedPlacementId },
        )
        assertEquals(
            (1..5).toList(),
            result.rows.take(5).map { it.detectedValue },
        )
        assertEquals(
            (6..12).toList(),
            result.rows.drop(5).map { it.detectedValue },
        )
        assertTrue(
            result.rows.all {
                it.status == PlacementParseStatus.DETECTED
            },
        )
    }

    @Test
    fun emptyAndMissingGeometryEvidenceAreMissingWithoutGuessing() {
        assertTrue(
            parse(emptyList()).rows.all {
                it.status == PlacementParseStatus.MISSING
            },
        )

        assertEquals(
            PlacementParseStatus.MISSING,
            parse(
                listOf(
                    line("1", 1, null),
                ),
            ).rows.first().status,
        )
    }

    @Test
    fun duplicateOutOfRangeAndMalformedTokensAreTyped() {
        val duplicate = parse(
            listOf(
                line("1", 1),
                line("1", 2),
            ),
        )

        assertEquals(
            PlacementParseStatus.DUPLICATE,
            duplicate.rows[0].status,
        )
        assertEquals(
            PlacementParseStatus.DUPLICATE,
            duplicate.rows[1].status,
        )

        assertEquals(
            PlacementParseStatus.INVALID,
            parse(
                listOf(
                    line("13", 1),
                ),
            ).rows.first().status,
        )

        assertEquals(
            PlacementParseStatus.INVALID,
            parse(
                listOf(
                    line("x1", 1),
                ),
            ).rows.first().status,
        )
    }

    @Test
    fun nonPlacementZoneTextIsIgnored() {
        val nameZone = RawOcrLine(
            text = "8",
            geometry = geometry(192, 12),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = emptyList(),
        )

        val killZone = RawOcrLine(
            text = "9",
            geometry = geometry(462, 12),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = emptyList(),
        )

        assertTrue(
            parse(
                listOf(
                    nameZone,
                    killZone,
                ),
            ).rows.all {
                it.status == PlacementParseStatus.MISSING
            },
        )
    }

    @Test
    fun croppedCandidateLocalGeometryMapsPlacementIntoCorrectRow() {
        val extraction = RawOcrExtractionResult.Extracted(
            sourceCandidate = candidate(),
            fullText = "1",
            blocks = listOf(
                RawOcrBlock(
                    text = "1",
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    lines = listOf(
                        RawOcrLine(
                            text = "1",
                            geometry = geometry(
                                x = 10,
                                y = 10,
                            ),
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                            elements = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val result = FixedLayoutPlacementParser().parse(
            PlacementParsingInput(
                extractions = listOf(extraction),
            ),
        )

        val placementOne = result.rows.single {
            it.expectedPlacementId == 1
        }

        assertEquals(
            PlacementParseStatus.DETECTED,
            placementOne.status,
        )
        assertEquals(
            1,
            placementOne.detectedValue,
        )

        assertTrue(
            result.rows
                .filter {
                    it.expectedPlacementId != 1
                }
                .all {
                    it.status == PlacementParseStatus.MISSING
                },
        )
    }

    private fun parse(
        lines: List<RawOcrLine>,
    ): PlacementParsingResult =
        FixedLayoutPlacementParser().parse(
            PlacementParsingInput(
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
            x = if (isRightPanel) {
                662
            } else {
                7
            },
            y = 7 +
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