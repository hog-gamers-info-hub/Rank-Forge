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
    fun centerOnlyEvidenceRemainsUnavailable() {
        val result = classifyResult(line("Player A", 10, 44, 100, 54))
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    @Test
    fun clearUpperOnlyEvidenceRemainsUnavailable() {
        val result = classifyResult(line("Player A", 10, 15, 100, 25))
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    @Test
    fun lowerOnlyEvidenceIsUnsafe() {
        val result = classifyResult(line("Player B", 10, 70, 100, 80))
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    @Test
    fun twoSeparatedPhysicalRowsResolveFormerCenterEvidence() {
        val result = classifyResult(
                line("Player A", 10, 10, 100, 20),
                line("Player B", 10, 44, 100, 54),
            )
        assertTrue(result is MatchResultPositionLogicalRowClassification.Available)
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
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
        val result = classifyResult(
            line("7", 0, 44, 20, 54),
            line("Player A", 80, 10, 180, 20),
        )
        assertEquals(2, result.diagnostics.totalMappedLines)
        assertEquals(1, result.diagnostics.placementLinesRemoved)
        assertEquals(1, result.diagnostics.usableLines)
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
    }

    @Test
    fun p1TraceKeepsRowsSeparatedWithThirtyFivePercentTolerance() {
        val result = classifyAt(1, 47.0, listOf(
            box("A1", 27.0, 20), box("A2", 26.5, 20), box("A3", 28.0, 20), box("A4", 29.0, 20),
            box("B1", 64.5, 20), box("B2", 65.0, 20), box("B3", 66.0, 20), box("B4", 67.0, 20),
        ))
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
        assertEquals(4, result.diagnostics.upperCount)
        assertEquals(4, result.diagnostics.lowerCount)
        assertEquals(7.0, result.diagnostics.derivedTolerance!!, 0.001)
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
        assertEquals(9.45, result.diagnostics.derivedTolerance!!, 0.001)
    }

    @Test
    fun physicalPosition11TraceFiltersMisreadPlacementAndResolvesKtsNekoToLower() {
        val result = classifyCustom(
            position = 11,
            cropWidth = 493,
            cropHeight = 82,
            center = 41.0,
            lines = listOf(
                line("4 Eliminatiorsenyeager", 207, 7, 391, 36),
                line("KTS BE4STz", 80, 9, 176, 32),
                line("3 Eliminati", 419, 10, 493, 34),
                line("71", 11, 25, 31, 53),
                line("KTS NEKO", 83, 36, 171, 67),
                line("O EliminatioKES ASH!SH!!", 207, 38, 394, 67),
                line("3 Eliminati", 420, 43, 493, 67),
            ),
        ) as MatchResultPositionLogicalRowClassification.Available
        assertEquals(1, result.diagnostics.placementLinesRemoved)
        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
        assertEquals(3, result.diagnostics.upperCount)
        assertEquals(3, result.diagnostics.lowerCount)
        assertEquals(0, result.diagnostics.centerCount)
        assertEquals(listOf(1, 2), result.rowCrops.map { it.rowIndex })
        assertTrue(result.blocks.flatMap { it.lines }.any { it.text == "KTS NEKO" })
    }

    @Test
    fun misreadCompactPlacementTokenIsFilteredForEveryPosition() {
        for (position in 1..12) {
            val result = classifyCustom(
                position = position,
                cropWidth = 300,
                cropHeight = 100,
                center = 50.0,
                lines = listOf(
                    line("O", 8, 45, 18, 55),
                    line("upper", 80, 20, 160, 30),
                    line("lower", 80, 70, 160, 80),
                ),
            ) as MatchResultPositionLogicalRowClassification.Available
            assertEquals(1, result.diagnostics.placementLinesRemoved)
            assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
            assertEquals(0, result.diagnostics.centerCount)
        }
    }

    @Test
    fun twoRowClusteringIsUniformAcrossEveryPosition() {
        for (position in 1..12) {
            val result = classifyCustom(
                position = position,
                cropWidth = 300,
                cropHeight = 100,
                center = 50.0,
                lines = listOf(
                    box("u1", 24.0, 10), box("u2", 25.0, 10), box("u3", 26.0, 10),
                    box("l1", 74.0, 10), box("l2", 75.0, 10), box("l3", 76.0, 10),
                ),
            ) as MatchResultPositionLogicalRowClassification.Available
            assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2, result.diagnostics.classification)
            assertEquals(3, result.diagnostics.upperCount)
            assertEquals(3, result.diagnostics.lowerCount)
            assertEquals(0, result.diagnostics.centerCount)
            assertEquals(listOf(1, 2), result.rowCrops.map { it.rowIndex })
        }
    }

    @Test
    fun borderlineCenterEvidenceIsAssignedToNearestEstablishedCluster() {
        val result = classifyCustom(
            position = 7,
            cropWidth = 300,
            cropHeight = 120,
            center = 50.0,
            lines = listOf(
                box("upper", 30.0, 40),
                box("upper-strong", 32.0, 40),
                box("lower-near-center", 64.0, 40),
                box("lower", 70.0, 40),
                box("lower-strong", 72.0, 40),
            ),
        ) as MatchResultPositionLogicalRowClassification.Available
        assertEquals(0, result.diagnostics.centerCount)
        assertEquals(2, result.diagnostics.upperCount)
        assertEquals(3, result.diagnostics.lowerCount)
    }

    @Test
    fun alphabeticLeftRegionTextIsNotPlacementEvidence() {
        val result = classifyResult(
            line("Alpha", 0, 10, 20, 20),
            line("Player A", 80, 10, 180, 20),
            line("Player B", 80, 70, 180, 80),
        )
        assertTrue(result is MatchResultPositionLogicalRowClassification.Available)
        assertEquals(0, result.diagnostics.placementLinesRemoved)
        assertEquals(3, result.diagnostics.upperCount + result.diagnostics.lowerCount)
    }

    @Test
    fun numericLikeTokenOutsidePlacementGeometryIsNotFiltered() {
        val result = classifyResult(
            line("71", 80, 10, 100, 20),
            line("Player A", 120, 10, 200, 20),
            line("Player B", 120, 70, 200, 80),
        )
        assertTrue(result is MatchResultPositionLogicalRowClassification.Available)
        assertEquals(0, result.diagnostics.placementLinesRemoved)
    }

    @Test
    fun ambiguousGeometryRemainsUnavailable() {
        val result = classifyResult(
            box("one", 44.0, 10),
            box("two", 49.0, 10),
            box("three", 54.0, 10),
        )
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    @Test
    fun singleDetectedClusterRemainsUnavailable() {
        val result = classifyResult(box("one", 25.0, 10), box("two", 26.0, 10))
        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    @Test
    fun explicitlyEnabledSingleUpperRowMapsToRowOneOnly() {
        val result = classifyCustom(
            position = 11,
            cropWidth = 200,
            cropHeight = 90,
            center = 47.0,
            lines = listOf(box("upper-one", 25.0, 10), box("upper-two", 26.0, 10)),
            allowSingleRowFallback = true,
        ) as MatchResultPositionLogicalRowClassification.Available

        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW1_ONLY, result.diagnostics.classification)
        assertEquals(listOf(1), result.rowCrops.map { it.rowIndex })
        assertEquals(2, result.diagnostics.upperCount)
        assertEquals(0, result.diagnostics.lowerCount)
    }

    @Test
    fun explicitlyEnabledSingleLowerRowMapsToRowTwoOnly() {
        val result = classifyCustom(
            position = 11,
            cropWidth = 200,
            cropHeight = 90,
            center = 47.0,
            lines = listOf(box("lower-one", 68.0, 10), box("lower-two", 69.0, 10)),
            allowSingleRowFallback = true,
        ) as MatchResultPositionLogicalRowClassification.Available

        assertEquals(MatchResultPositionLogicalRowClassificationKind.ROW2_ONLY, result.diagnostics.classification)
        assertEquals(listOf(2), result.rowCrops.map { it.rowIndex })
        assertEquals(0, result.diagnostics.upperCount)
        assertEquals(2, result.diagnostics.lowerCount)
    }

    @Test
    fun enabledSingleRowFallbackStillRejectsCenterOnlyEvidence() {
        val result = classifyCustom(
            position = 11,
            cropWidth = 200,
            cropHeight = 90,
            center = 47.0,
            lines = listOf(box("center", 45.0, 10)),
            allowSingleRowFallback = true,
        )

        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    @Test
    fun enabledSingleRowFallbackKeepsConflictingClustersUnavailable() {
        val result = classifyCustom(
            position = 11,
            cropWidth = 200,
            cropHeight = 90,
            center = 47.0,
            lines = listOf(
                box("upper", 25.0, 10),
                box("lower", 68.0, 10),
                box("unrelated", 85.0, 10),
            ),
            allowSingleRowFallback = true,
        )

        assertTrue(result is MatchResultPositionLogicalRowClassification.Unavailable)
        assertEquals(MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS, result.diagnostics.reason)
    }

    private fun classifyCustom(
        position: Int,
        cropWidth: Int,
        cropHeight: Int,
        center: Double,
        lines: List<RawOcrLine>,
        allowSingleRowFallback: Boolean = false,
    ): MatchResultPositionLogicalRowClassification = classifier.classify(
        position = position,
        cropWidth = cropWidth,
        cropHeight = cropHeight,
        slotCenterYLocal = center,
        blocks = listOf(RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines)),
        allowSingleRowFallback = allowSingleRowFallback,
    )

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = RawOcrLine(
        text = text,
        geometry = RawOcrGeometry(RawOcrBoundingBox(left, top, right, bottom), null),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        elements = emptyList(),
    )
}
