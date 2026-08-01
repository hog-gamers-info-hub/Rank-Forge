package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionFailure
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneType
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelId
import com.hoggamers.rankforge.domain.ocr.parsing.KillOcrEvidence
import com.hoggamers.rankforge.domain.ocr.parsing.KillParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.KillParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.KillParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.ParsedKillRow
import com.hoggamers.rankforge.domain.ocr.parsing.ParsedPlacementRow
import com.hoggamers.rankforge.domain.ocr.parsing.ParsedPlayerNameRow
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementOcrEvidence
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameOcrEvidence
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParsingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCrop
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingFailure
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFailureAnalyzerTest {
    private val analyzer = FixedLayoutOcrFailureAnalyzer()

    @Test
    fun emptyRawOutputMarksEveryRequiredFieldMissingForManualReview() {
        val result = analyzer.analyze(
            OcrFailureAnalysisInput(extractionResults = listOf(RawOcrExtractionResult.Empty(candidate()))),
        )

        assertEquals((1..12).toList(), result.rows.map { it.expectedPlacementId })
        assertTrue(result.rows.flatMap { it.fields }.all { it.status == OcrReviewStatus.MISSING })
        assertTrue(result.rows.flatMap { it.fields }.all { it.manualReviewRequired })
    }

    @Test
    fun preprocessingFailureCreatesBlockingUnsupportedMarkers() {
        val result = analyzer.analyze(
            OcrFailureAnalysisInput(
                preprocessingResult = OcrPreprocessingResult.Failed(OcrPreprocessingFailure.UNSUPPORTED_LAYOUT),
            ),
        )

        assertTrue(result.rows.flatMap { it.fields }.all { it.status == OcrReviewStatus.UNSUPPORTED })
        assertTrue(result.rows.flatMap { it.fields }.all { it.severity == OcrReviewSeverity.BLOCKING })
    }

    @Test
    fun rawExtractionFailureCreatesBlockingUncertainMarkersWithEvidence() {
        val failed = RawOcrExtractionResult.Failed(candidate(), RawOcrExtractionFailure.ENGINE_FAILED)
        val result = analyzer.analyze(OcrFailureAnalysisInput(extractionResults = listOf(failed)))

        val fields = result.rows.flatMap { it.fields }
        assertTrue(fields.all { it.status == OcrReviewStatus.UNCERTAIN })
        assertTrue(fields.all { it.severity == OcrReviewSeverity.BLOCKING })
        assertTrue(fields.all { it.reason == OcrReviewReason.RawExtractionFailure(RawOcrExtractionFailure.ENGINE_FAILED) })
        assertTrue(fields.all { it.evidence.single().source == failed })
    }

    @Test
    fun extractedRawOutputWithoutParserResultsUsesParserOutputUnavailableFallback() {
        val result = analyzer.analyze(
            OcrFailureAnalysisInput(
                extractionResults = listOf(
                    RawOcrExtractionResult.Extracted(candidate(), "raw output", emptyList()),
                ),
            ),
        )

        val fields = result.rows.flatMap { it.fields }
        assertTrue(fields.all { it.status == OcrReviewStatus.UNCERTAIN })
        assertTrue(fields.all { it.severity == OcrReviewSeverity.WARNING })
        assertTrue(fields.all { it.reason == OcrReviewReason.ParserOutputUnavailable })
        assertTrue(fields.all { it.manualReviewRequired })
    }

    @Test
    fun partialSuccessPreservesValidFieldsAndMarksOnlyFailedFields() {
        val source = RawOcrExtractionResult.Empty(candidate())
        val result = analyzer.analyze(
            OcrFailureAnalysisInput(
                placementResult = PlacementParsingResult(
                    listOf(placementRow(PlacementParseStatus.DETECTED, source, "1")),
                ),
                playerNameResult = PlayerNameParsingResult(
                    listOf(playerNameRow(PlayerNameParseStatus.MISSING, source, "")),
                ),
                killResult = KillParsingResult(
                    listOf(killRow(KillParseStatus.DETECTED, source, "4")),
                ),
            ),
        )

        val fields = result.rows.first().fields.associateBy { it.type }
        assertEquals(OcrReviewStatus.ACCEPTED, fields.getValue(OcrReviewFieldType.PLACEMENT).status)
        assertFalse(fields.getValue(OcrReviewFieldType.PLACEMENT).manualReviewRequired)
        assertEquals(OcrReviewStatus.MISSING, fields.getValue(OcrReviewFieldType.PLAYER_NAME).status)
        assertTrue(fields.getValue(OcrReviewFieldType.PLAYER_NAME).manualReviewRequired)
        assertEquals(OcrReviewStatus.ACCEPTED, fields.getValue(OcrReviewFieldType.KILL).status)
        assertTrue(result.rows.first().manualReviewRequired)
    }

    @Test
    fun invalidAmbiguousAndDuplicateParserOutcomesRemainBlockingWithEvidence() {
        val source = RawOcrExtractionResult.Empty(candidate())
        val result = analyzer.analyze(
            OcrFailureAnalysisInput(
                placementResult = PlacementParsingResult(
                    listOf(placementRow(PlacementParseStatus.INVALID, source, "invalid")),
                ),
                playerNameResult = PlayerNameParsingResult(
                    listOf(playerNameRow(PlayerNameParseStatus.AMBIGUOUS, source, "Synthetic^A")),
                ),
                killResult = KillParsingResult(
                    listOf(killRow(KillParseStatus.DUPLICATE, source, "4")),
                ),
            ),
        )

        val fields = result.rows.first().fields.associateBy { it.type }
        assertEquals(OcrReviewStatus.INVALID, fields.getValue(OcrReviewFieldType.PLACEMENT).status)
        assertEquals(OcrReviewStatus.AMBIGUOUS, fields.getValue(OcrReviewFieldType.PLAYER_NAME).status)
        assertEquals(OcrReviewStatus.DUPLICATE, fields.getValue(OcrReviewFieldType.KILL).status)
        assertTrue(fields.values.all { it.severity == OcrReviewSeverity.BLOCKING })
        assertEquals("invalid", fields.getValue(OcrReviewFieldType.PLACEMENT).evidence.single().text)
        assertEquals(OcrReviewReason.PlacementInvalid, fields.getValue(OcrReviewFieldType.PLACEMENT).reason)
    }

    private fun placementRow(
        status: PlacementParseStatus,
        source: RawOcrExtractionResult,
        text: String,
    ) = ParsedPlacementRow(
        expectedPlacementId = 1,
        panelId = ScoreboardPanelId.LEFT,
        rowIndex = 0,
        status = status,
        detectedValue = if (status == PlacementParseStatus.DETECTED) 1 else null,
        evidence = listOf(PlacementOcrEvidence(text, null, source)),
    )

    private fun playerNameRow(
        status: PlayerNameParseStatus,
        source: RawOcrExtractionResult,
        text: String,
    ): ParsedPlayerNameRow {
        val row = FreeFireMaxScoreboardLayout.definition.panels.first().rows.first()
        return ParsedPlayerNameRow(
            expectedPlacementId = 1,
            panelId = ScoreboardPanelId.LEFT,
            rowIndex = 0,
            playerNameZone = row.fieldZones.first { it.type == ScoreboardFieldZoneType.PLAYER_NAME },
            playerNameZoneRect = OcrPixelRect(0, 0, 1, 1),
            status = status,
            detectedName = if (status == PlayerNameParseStatus.DETECTED) text else null,
            failure = null,
            evidence = listOf(PlayerNameOcrEvidence(text, null, source)),
        )
    }

    private fun killRow(
        status: KillParseStatus,
        source: RawOcrExtractionResult,
        text: String,
    ): ParsedKillRow {
        val row = FreeFireMaxScoreboardLayout.definition.panels.first().rows.first()
        return ParsedKillRow(
            expectedPlacementId = 1,
            panelId = ScoreboardPanelId.LEFT,
            rowIndex = 0,
            eliminationValueZone = row.fieldZones.first { it.type == ScoreboardFieldZoneType.ELIMINATION_VALUE },
            eliminationValueZoneRect = OcrPixelRect(0, 0, 1, 1),
            status = status,
            detectedValue = if (status == KillParseStatus.DETECTED) 4 else null,
            failure = if (status == KillParseStatus.INVALID) KillParseFailure.MALFORMED_TOKEN else null,
            evidence = listOf(KillOcrEvidence(text, null, source)),
        )
    }

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
