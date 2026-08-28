package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionLogicalRowClassifierTest {
    private val classifier = MatchResultPositionLogicalRowClassifier()

    @Test
    fun clearUpperAndLowerRowsMapToCanonicalRows() {
        val result = classify(line("Player A", 10, 10, 100, 20), line("Player B", 10, 70, 100, 80))
        assertEquals(listOf(1, 2), result.rowCrops.map { it.rowIndex })
        assertEquals(listOf(10, 70), result.rowCrops.map { it.bounds.top })
        assertEquals(1, result.diagnostics.upperCount)
        assertEquals(1, result.diagnostics.lowerCount)
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
    }

    @Test
    fun centerOnlyEvidenceMapsToFirstLogicalRow() {
        val result = classify(line("Player A", 10, 44, 100, 54))
        assertEquals(listOf(1), result.rowCrops.map { it.rowIndex })
        assertEquals(1, result.diagnostics.centerCount)
        assertEquals(MatchResultPositionLogicalRowClassificationKind.CENTERED_SINGLE_ROW, result.diagnostics.classification)
    }

    @Test
    fun clearUpperOnlyEvidenceMapsToFirstLogicalRow() {
        val result = classify(line("Player A", 10, 15, 100, 25))
        assertEquals(listOf(1), result.rowCrops.map { it.rowIndex })
    }

    @Test
    fun lowerOnlyEvidenceIsUnsafe() {
        val result = classifyResult(line("Player B", 10, 70, 100, 80))
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.LOWER_ONLY, result.diagnostics.reason)
    }

    @Test
    fun centerMixedWithIndependentRowIsUnsafe() {
        val result = classifyResult(
                line("Player A", 10, 10, 100, 20),
                line("Player B", 10, 44, 100, 54),
            )
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CENTER_WITH_UPPER, result.diagnostics.reason)
    }

    @Test
    fun placementNumberIsNotInterpretedAsPlayerRow() {
        val result = classify(
            line("7", 0, 44, 20, 54),
            line("Player A", 80, 10, 180, 20),
            line("Player B", 80, 70, 180, 80),
        )
        assertEquals(listOf(1, 2), result.rowCrops.map { it.rowIndex })
        assertEquals(80, result.rowCrops.first().bounds.left)
        assertEquals(2, result.blocks.single().lines.size)
    }

    @Test
    fun malformedOrMissingGeometryIsUnavailable() {
        val missing = classifyResult(RawOcrLine("broken", null, null, RawOcrConfidence.Unavailable, emptyList()))
        assertTrue(missing is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.NO_USABLE_GEOMETRY, missing.diagnostics.reason)
        val malformed = classifyResult(line("broken", 20, 20, 20, 30))
        assertTrue(malformed is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.NO_USABLE_GEOMETRY, malformed.diagnostics.reason)
    }

    private fun classify(vararg lines: RawOcrLine): MatchResultPositionLogicalRowClassification.Available =
        classifyResult(*lines) as MatchResultPositionLogicalRowClassification.Available

    private fun classifyResult(vararg lines: RawOcrLine): MatchResultPositionLogicalRowClassification =
        classifier.classify(
            position = 7,
            cropWidth = 200,
            cropHeight = 90,
            slotCenterYLocal = 47.0,
            blocks = listOf(RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines.toList())),
        )

    private fun classifyAt(
        position: Int,
        center: Double,
        boxes: List<RawOcrLine>,
    ): MatchResultPositionLogicalRowClassification.Available =
        classifier.classify(
            position = position,
            cropWidth = 300,
            cropHeight = 140,
            slotCenterYLocal = center,
            blocks = listOf(RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, boxes)),
        ) as MatchResultPositionLogicalRowClassification.Available

    private fun box(text: String, centerY: Double, height: Int): RawOcrLine {
        val top = (centerY - height / 2.0).toInt()
        return line(text, 80, top, 180, top + height)
    }

    @Test
    fun unavailableStructuralCenterIsReportedExplicitly() {
        val result = classifier.classify(
            position = 7,
            cropWidth = 200,
            cropHeight = 90,
            slotCenterYLocal = null,
            blocks = emptyList(),
        )
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(
            MatchResultPositionLogicalRowFallbackReason.STRUCTURAL_CENTER_UNAVAILABLE,
            result.diagnostics.reason,
        )
    }

    @Test
    fun placementRemovalIsCountedSeparatelyFromUsableLines() {
        val result = classify(
            line("7", 0, 44, 20, 54),
            line("Player A", 80, 10, 180, 20),
        )
        assertEquals(2, result.diagnostics.totalMappedLines)
        assertEquals(1, result.diagnostics.placementLinesRemoved)
        assertEquals(1, result.diagnostics.usableLines)
    }

    @Test
    fun p1TraceKeepsRowsSeparatedWithFortyPercentTolerance() {
        val result = classifyAt(1, 47.0, listOf(
            box("A1", 27.0, 20), box("A2", 26.5, 20), box("A3", 28.0, 20), box("A4", 29.0, 20),
            box("B1", 64.5, 20), box("B2", 65.0, 20), box("B3", 66.0, 20), box("B4", 67.0, 20),
        ))
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
        assertEquals(4, result.diagnostics.upperCount)
        assertEquals(4, result.diagnostics.lowerCount)
        assertEquals(8.0, result.diagnostics.derivedTolerance!!, 0.001)
    }

    @Test
    fun p2TraceIgnoresVerticalSpanningOutlierForClassification() {
        val result = classifyAt(2, 47.0, listOf(
            box("A1", 27.0, 20), box("A2", 26.5, 20), box("A3", 28.0, 20), box("A4", 29.0, 20),
            box("B1", 64.5, 20), box("B2", 65.0, 20), box("B3", 66.0, 20), box("B4", 67.0, 20),
            box("spanning", 45.5, 65),
        ))
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
        assertEquals(4, result.diagnostics.upperCount)
        assertEquals(4, result.diagnostics.lowerCount)
        assertEquals(1, result.diagnostics.spanningIgnored)
        assertTrue(result.blocks.flatMap { it.lines }.any { it.text == "spanning" })
    }

    @Test
    fun p3TraceKeepsRowsSeparated() {
        val result = classifyAt(3, 47.0, listOf(
            box("A1", 26.0, 21), box("A2", 25.5, 21), box("A3", 28.0, 21), box("A4", 29.0, 21),
            box("B1", 64.0, 21), box("B2", 65.0, 21), box("B3", 66.0, 21), box("B4", 67.0, 21),
        ))
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
    }

    @Test
    fun p11TraceDoesNotCollapseLowerRowAtLargeTextHeight() {
        val result = classifyAt(11, 47.0, listOf(
            box("A1", 27.0, 27), box("A2", 28.0, 27), box("A3", 29.0, 27),
            box("B1", 59.5, 27), box("B2", 59.5, 27), box("B3", 62.0, 27),
        ))
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
        assertEquals(10.8, result.diagnostics.derivedTolerance!!, 0.001)
    }

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = RawOcrLine(
        text = text,
        geometry = RawOcrGeometry(RawOcrBoundingBox(left, top, right, bottom), null),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        elements = emptyList(),
    )
}
