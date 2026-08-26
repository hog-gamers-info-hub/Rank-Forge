package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrFieldMapper
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrInput
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultRowOcrEnhancerTest {
    @Test
    fun selectionPrioritizesMarkersThenExplicitKillsThenNonEmptyThenConfidence() {
        val moreMarkers = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_3X,
            success("EliminationA", "2EliminationB"),
        )
        val fewerMarkers = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_4X,
            success("2EliminationB", "name"),
        )
        assertEquals(
            "MORE_MARKERS",
            MatchResultRowOcrCandidateSelector.select(moreMarkers, fewerMarkers)?.reason,
        )

        val explicitKills = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_3X,
            success("3EliminationA"),
        )
        val missingPrefix = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_4X,
            success("EliminationA"),
        )
        assertEquals(
            "MORE_EXPLICIT_KILL_EVIDENCE",
            MatchResultRowOcrCandidateSelector.select(explicitKills, missingPrefix)?.reason,
        )

        val moreResults = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_3X,
            success("name", "other"),
        )
        val fewerResults = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_4X,
            success("name"),
        )
        assertEquals(
            "MORE_NON_EMPTY_RESULTS",
            MatchResultRowOcrCandidateSelector.select(moreResults, fewerResults)?.reason,
        )
    }

    @Test
    fun selectionUsesConfidenceThenPrefersThreeXOnExactTie() {
        val higherConfidence = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_4X,
            success("name", confidence = 0.9f),
        )
        val lowerConfidence = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_3X,
            success("name", confidence = 0.8f),
        )
        assertEquals(
            "HIGHER_AVERAGE_CONFIDENCE",
            MatchResultRowOcrCandidateSelector.select(lowerConfidence, higherConfidence)?.reason,
        )

        val threeX = MatchResultRowOcrCandidateSelector.evaluate(MatchResultRowOcrCandidate.SCALE_3X, success("name"))
        val fourX = MatchResultRowOcrCandidateSelector.evaluate(MatchResultRowOcrCandidate.SCALE_4X, success("name"))
        val tie = MatchResultRowOcrCandidateSelector.select(threeX, fourX)
        assertEquals(MatchResultRowOcrCandidate.SCALE_3X, tie?.selected?.candidate)
        assertEquals("TIE_PREFER_3X", tie?.reason)
    }

    @Test
    fun selectionUsesTheOtherCandidateWhenOnlyOneRecognitionSucceeds() {
        val failedThreeX = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_3X,
            MatchResultPositionPaddleOcrResult.Failed(MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED),
        )
        val validFourX = MatchResultRowOcrCandidateSelector.evaluate(MatchResultRowOcrCandidate.SCALE_4X, success("name"))
        assertEquals(
            MatchResultRowOcrCandidate.SCALE_4X,
            MatchResultRowOcrCandidateSelector.select(failedThreeX, validFourX)?.selected?.candidate,
        )

        val validThreeX = MatchResultRowOcrCandidateSelector.evaluate(MatchResultRowOcrCandidate.SCALE_3X, success("name"))
        val failedFourX = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_4X,
            MatchResultPositionPaddleOcrResult.Failed(MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED),
        )
        assertEquals(
            MatchResultRowOcrCandidate.SCALE_3X,
            MatchResultRowOcrCandidateSelector.select(validThreeX, failedFourX)?.selected?.candidate,
        )
    }

    @Test
    fun geometryRestorationDividesByScaleAddsRowOffsetAndClamps() {
        val row = row(2, 40, 80)
        val restoredThreeX = MatchResultRowOcrGeometryMapper.mapBlocks(
            block(line("3Elimination", 300, 15, 600, 75)),
            MatchResultRowOcrCandidate.SCALE_3X,
            row,
            positionWidth = 491,
            positionHeight = 82,
        )
        val restoredFourX = MatchResultRowOcrGeometryMapper.mapBlocks(
            block(line("3Elimination", 400, 20, 800, 100)),
            MatchResultRowOcrCandidate.SCALE_4X,
            row,
            positionWidth = 491,
            positionHeight = 82,
        )
        val expected = RawOcrBoundingBox(100, 45, 200, 65)
        assertEquals(expected, restoredThreeX.single().lines.single().geometry?.boundingBox)
        assertEquals(expected, restoredFourX.single().lines.single().geometry?.boundingBox)

        val clamped = MatchResultRowOcrGeometryMapper.mapBlocks(
            block(line("edge", -40, -100, 4_000, 1_000)),
            MatchResultRowOcrCandidate.SCALE_4X,
            row,
            positionWidth = 491,
            positionHeight = 82,
        )
        assertEquals(RawOcrBoundingBox(0, 15, 491, 82), clamped.single().lines.single().geometry?.boundingBox)
    }

    @Test
    fun positionSevenPhysicalSemanticShapeRemainsCompatibleAfterMixedScaleRows() {
        val rowOne = MatchResultRowOcrGeometryMapper.mapBlocks(
            block(
                line("A", 210, 30, 480, 90),
                line("3EliminationB", 606, 30, 1_191, 90),
                line("Eliminati", 1_230, 30, 1_440, 90),
            ),
            MatchResultRowOcrCandidate.SCALE_3X,
            row(1, 0, 41),
            491,
            82,
        )
        val rowTwo = MatchResultRowOcrGeometryMapper.mapBlocks(
            block(
                line("C", 280, 16, 640, 116),
                line("EliminationD", 808, 16, 1_400, 116),
                line("1 Eliminati", 1_640, 16, 1_920, 116),
            ),
            MatchResultRowOcrCandidate.SCALE_4X,
            row(2, 41, 82),
            491,
            82,
        )
        val result = MatchResultPositionOcrFieldMapper().map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 7,
                cropWidth = 491,
                cropHeight = 82,
                blocks = rowOne + rowTwo,
                rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
                placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
                killVerifications = emptyMap(),
            ),
        )
        assertEquals("7", result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }.resolvedText)
        assertEquals("3", result.fields.single { it.id == "KILL_7_1" }.resolvedText)
        assertEquals("0", result.fields.single { it.id == "KILL_7_2" }.resolvedText)
        assertEquals("0", result.fields.single { it.id == "KILL_7_3" }.resolvedText)
        assertEquals("1", result.fields.single { it.id == "KILL_7_4" }.resolvedText)
    }

    @Test
    fun failedCandidatesAreRejectedWithoutSynthesizingRowEvidence() {
        val failedThreeX = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_3X,
            MatchResultPositionPaddleOcrResult.Failed(MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED),
        )
        val failedFourX = MatchResultRowOcrCandidateSelector.evaluate(
            MatchResultRowOcrCandidate.SCALE_4X,
            MatchResultPositionPaddleOcrResult.Failed(MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED),
        )
        assertTrue(MatchResultRowOcrCandidateSelector.select(failedThreeX, failedFourX) == null)
    }

    private fun success(vararg texts: String, confidence: Float = 0.8f): MatchResultPositionPaddleOcrResult.Success =
        MatchResultPositionPaddleOcrResult.Success(
            MatchResultPositionPaddleOcrEvidence(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 7,
                column = MatchResultPositionColumn.RIGHT,
                cropWidth = 491,
                cropHeight = 82,
                blocks = block(*texts.mapIndexed { index, text -> line(text, 20, index * 10, 120, index * 10 + 8, confidence) }.toTypedArray()),
            ),
        )

    private fun row(index: Int, top: Int, bottom: Int) = MatchResultPositionRowCrop(
        index,
        OcrPixelCropRect(0, top, 491, bottom),
    )

    private fun block(vararg lines: RawOcrLine) = listOf(
        RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines.toList()),
    )

    private fun line(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        confidence: Float = 0.8f,
    ) = RawOcrLine(
        text = text,
        geometry = RawOcrGeometry(RawOcrBoundingBox(left, top, right, bottom), null),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Available(confidence),
        elements = emptyList(),
    )
}
