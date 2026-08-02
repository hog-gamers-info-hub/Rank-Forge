package com.hoggamers.rankforge.domain.matching

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrImagePreprocessor
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCrop
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardRowPlayerEvidenceCollectorTest {
    private val collector = ScoreboardRowPlayerEvidenceCollector()
    private val cropRect = OcrPixelRect(208, 158, 1_168, 468)

    @Test
    fun collectsMultipleRoughStringsFromOneFixedRow() {
        val result = collector.collect(extracted(
            candidate = candidate(),
            lines = listOf(
                line("  Alpha_1  ", box(100, 20, 220, 45)),
                line("Brav0?", box(230, 55, 340, 80)),
            ),
        ))

        assertEquals(listOf("Alpha_1", "Brav0?"), result.first().detectedPlayerNames)
        assertEquals(0, result.first().rowIndex)
        assertEquals(1, result.first().expectedPlacementId)
    }

    @Test
    fun joinsElementsFromOneOcrLineIntoOneVisuallyOrderedPlayerCandidate() {
        val result = collector.collect(extracted(
            candidate(),
            listOf(
                elementLine(
                    "GODZILLA" to box(230, 20, 340, 45),
                    "X10" to box(100, 20, 180, 45),
                ),
            ),
        ))

        assertEquals(listOf("X10 GODZILLA"), result.first().detectedPlayerNames)
    }

    @Test
    fun excludesNumericElementOutsidePlayerZoneWithoutAppendingItToTheName() {
        val result = collector.collect(extracted(
            candidate(),
            listOf(
                elementLine(
                    "X10" to box(100, 20, 180, 45),
                    "GODZILLA" to box(230, 20, 340, 45),
                    "4" to box(500, 20, 530, 45),
                ),
            ),
        ))

        assertEquals(listOf("X10 GODZILLA"), result.first().detectedPlayerNames)
    }

    @Test
    fun keepsTwoOcrLinesAsTwoPlayerCandidatesAndIgnoresBlankOrNumericTokens() {
        val result = collector.collect(extracted(
            candidate(),
            listOf(
                elementLine("   " to box(100, 20, 130, 45), "12" to box(150, 20, 180, 45), "K1ng?" to box(200, 20, 280, 45)),
                elementLine("Rough_Tag" to box(300, 55, 390, 80)),
            ),
        ))

        assertEquals(listOf("K1ng?", "Rough_Tag"), result.first().detectedPlayerNames)
    }

    @Test
    fun excludesBlankAndNumericOnlyEvidenceButPreservesImperfectText() {
        val result = collector.collect(extracted(
            candidate(),
            listOf(
                line("   ", box(100, 20, 160, 40)),
                line("12", box(170, 20, 210, 40)),
                line("K1ng?", box(220, 20, 320, 40)),
            ),
        ))

        assertEquals(listOf("K1ng?"), result.first().detectedPlayerNames)
    }

    @Test
    fun returnsAllTwelveRowsInPlacementOrder() {
        val result = collector.collect(extracted(
            candidate(),
            listOf(line("RightSix", box(730, 10, 840, 35))),
        ))

        assertEquals(12, result.size)
        assertEquals((0..11).toList(), result.map { it.rowIndex })
        assertEquals((1..12).toList(), result.map { it.expectedPlacementId })
        assertEquals(listOf("RightSix"), result[5].detectedPlayerNames)
    }

    @Test
    fun mapsLeftAndRightPanelRowsThroughTheFixedLayout() {
        val result = collector.collect(extracted(
            candidate(),
            listOf(
                line("LeftOne", box(100, 15, 180, 40)),
                line("RightSix", box(730, 15, 820, 40)),
            ),
        ))

        assertEquals(listOf("LeftOne"), result[0].detectedPlayerNames)
        assertEquals(listOf("RightSix"), result[5].detectedPlayerNames)
        assertTrue(result.drop(1).filterIndexed { index, _ -> index != 4 }.all { it.detectedPlayerNames.isEmpty() })
    }

    @Test
    fun mapsCroppedCandidateCoordinates() {
        val result = collector.collect(extracted(
            candidate = candidate(),
            lines = listOf(line("Cropped", box(120, 20, 200, 45))),
        ))

        assertEquals(listOf("Cropped"), result[0].detectedPlayerNames)
    }

    @Test
    fun mapsScaledCandidateCoordinatesUsingCandidateScale() {
        val scale = 1.5
        val result = collector.collect(extracted(
            candidate = candidate(scale),
            lines = listOf(line("Scaled", box(150, 30, 300, 68))),
        ))

        assertEquals(listOf("Scaled"), result[0].detectedPlayerNames)
    }

    @Test
    fun keepsCandidatesIsolated() {
        val baseline = collector.collect(extracted(
            candidate = candidate(order = 0),
            lines = listOf(line("BaselineOnly", box(100, 20, 220, 45))),
        ))
        val scaled = collector.collect(extracted(
            candidate = candidate(order = 1, scale = 1.5),
            lines = listOf(line("ScaledOnly", box(150, 30, 300, 68))),
        ))

        assertEquals(listOf("BaselineOnly"), baseline[0].detectedPlayerNames)
        assertEquals(listOf("ScaledOnly"), scaled[0].detectedPlayerNames)
        assertFalse(baseline[0].detectedPlayerNames.contains("ScaledOnly"))
    }

    @Test
    fun suppressesDuplicateRawEntitiesWithinOneCandidate() {
        val duplicate = RawOcrElement(
            text = "Repeat",
            geometry = geometry(box(100, 20, 220, 45)),
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
        )
        val result = collector.collect(extracted(
            candidate(),
            listOf(RawOcrLine("ignored", null, null, RawOcrConfidence.Unavailable, listOf(duplicate, duplicate))),
        ))

        assertEquals(listOf("Repeat"), result.first().detectedPlayerNames)
    }

    private fun extracted(
        candidate: OcrPreprocessingCandidate,
        lines: List<RawOcrLine>,
    ) = RawOcrExtractionResult.Extracted(
        sourceCandidate = candidate,
        fullText = lines.joinToString(" ") { it.text },
        blocks = listOf(RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines)),
    )

    private fun line(text: String, bounds: OcrPixelRect) =
        RawOcrLine(text, geometry(bounds), null, RawOcrConfidence.Unavailable, emptyList())

    private fun elementLine(vararg tokens: Pair<String, OcrPixelRect>) =
        RawOcrLine(
            text = "",
            geometry = null,
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            elements = tokens.map { (text, bounds) ->
                RawOcrElement(
                    text = text,
                    geometry = geometry(bounds),
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                )
            },
        )

    private fun box(x: Int, y: Int, right: Int, bottom: Int) = OcrPixelRect(x, y, right - x, bottom - y)

    private fun geometry(bounds: OcrPixelRect) = RawOcrGeometry(
        boundingBox = RawOcrBoundingBox(
            left = bounds.x,
            top = bounds.y,
            right = bounds.x + bounds.width,
            bottom = bounds.y + bounds.height,
        ),
        cornerPoints = null,
    )

    private fun candidate(scale: Double? = null, order: Int = 0): OcrPreprocessingCandidate {
        val factor = scale ?: 1.0
        return OcrPreprocessingCandidate(
            order = order,
            crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
            cropRect = cropRect,
            image = FakeImage(
                width = (cropRect.width * factor).roundToInt(),
                height = (cropRect.height * factor).roundToInt(),
            ),
            appliedSteps = if (scale == null) {
                listOf(OcrPreprocessingStep.CROP)
            } else {
                listOf(OcrPreprocessingStep.CROP, OcrPreprocessingStep.SCALE)
            },
            scaleFactor = scale,
        )
    }

    private data class FakeImage(
        override val width: Int,
        override val height: Int,
    ) : OcrPreprocessingImage
}
