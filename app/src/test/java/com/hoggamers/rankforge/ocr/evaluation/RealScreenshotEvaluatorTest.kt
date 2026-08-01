package com.hoggamers.rankforge.ocr.evaluation

import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneType
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelId
import com.hoggamers.rankforge.domain.ocr.parsing.KillParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.KillParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.ParsedKillRow
import com.hoggamers.rankforge.domain.ocr.parsing.ParsedPlacementRow
import com.hoggamers.rankforge.domain.ocr.parsing.ParsedPlayerNameRow
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParsingResult
import com.hoggamers.rankforge.domain.ocr.review.OcrFailureAnalysisResult
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewField
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewFieldType
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewReason
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewRow
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewSeverity
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealScreenshotEvaluatorTest {
    private val evaluator = RealScreenshotEvaluator()

    @Test
    fun reportsPlacementPlayerNameAndKillMismatches() {
        val result = evaluator.evaluate(
            caseFor(
                expectedRows = listOf(visibleRow(1, "Alpha One", 3)),
                placement = PlacementParsingResult(listOf(placementRow(1, 2))),
                playerName = PlayerNameParsingResult(listOf(playerNameRow(1, "Bravo Two"))),
                kill = KillParsingResult(listOf(killRow(1, 4))),
                review = reviewResult(1, acceptedFields()),
            ),
        )

        assertEquals(
            setOf(OcrReviewFieldType.PLACEMENT, OcrReviewFieldType.PLAYER_NAME, OcrReviewFieldType.KILL),
            result.mismatches.filter { it.reason == OcrEvaluationMismatchReason.VALUE_MISMATCH }.map { it.fieldType }.toSet(),
        )
    }

    @Test
    fun constrainedRowTwelveRequiresReviewMarkersAndReportsFalseAccepts() {
        val result = evaluator.evaluate(
            caseFor(
                expectedRows = listOf(constrainedRow(12)),
                review = reviewResult(12, acceptedFields()),
            ),
        )

        assertEquals(3, result.falseAcceptCount)
        assertTrue(result.mismatches.all { it.reason == OcrEvaluationMismatchReason.FALSE_ACCEPT })
    }

    @Test
    fun constrainedFieldsAreCorrectWhenManualReviewMarkersExist() {
        val result = evaluator.evaluate(
            caseFor(
                expectedRows = listOf(constrainedRow(12)),
                review = reviewResult(12, reviewFields()),
            ),
        )

        assertEquals(0, result.falseAcceptCount)
        assertTrue(result.mismatches.isEmpty())
    }

    @Test
    fun evaluationOrderAndMetricsAreDeterministicForSanitizedFixtures() {
        val evaluationCase = caseFor(
            expectedRows = listOf(visibleRow(2, "Bravo Two", 2), visibleRow(1, "Alpha One", 1)),
            placement = PlacementParsingResult(listOf(placementRow(1, 1), placementRow(2, 2))),
            playerName = PlayerNameParsingResult(listOf(playerNameRow(1, "Alpha One"), playerNameRow(2, "Bravo Two"))),
            kill = KillParsingResult(listOf(killRow(1, 1), killRow(2, 2))),
            review = reviewResult(1, acceptedFields()) + reviewResult(2, acceptedFields()),
        )

        val first = evaluator.evaluate(evaluationCase)
        val second = evaluator.evaluate(evaluationCase)

        assertEquals(listOf(1, 2), first.evaluatedPlacementIds)
        assertEquals(first, second)
        assertEquals(OcrEvaluationFixturePolicy.NO_REAL_SCREENSHOT_SYNTHETIC, evaluationCase.fixturePolicy)
        assertTrue(first.mismatches.isEmpty())
    }

    @Test
    fun missingParserResultsProduceVisibleFieldMismatchesWithoutFalseAccepts() {
        val result = evaluator.evaluate(
            caseFor(expectedRows = listOf(visibleRow(1, "Alpha One", 3))),
        )

        assertEquals(
            OcrEvaluationMetric(OcrEvaluationMetricType.PLACEMENT_CORRECTNESS, 0, 1),
            result.metrics.single { it.type == OcrEvaluationMetricType.PLACEMENT_CORRECTNESS },
        )
        assertEquals(
            OcrEvaluationMetric(OcrEvaluationMetricType.REVIEW_MARKER_CORRECTNESS, 0, 3),
            result.metrics.single { it.type == OcrEvaluationMetricType.REVIEW_MARKER_CORRECTNESS },
        )
        assertEquals(0, result.falseAcceptCount)
        assertEquals(6, result.mismatches.size)
    }

    @Test
    fun duplicateExpectedIdsRemainDeterministicAndCountOnceForCoverage() {
        val result = evaluator.evaluate(
            caseFor(
                expectedRows = listOf(
                    visibleRow(1, "Alpha One", 1),
                    visibleRow(1, "Alpha One", 1),
                ),
            ),
        )

        assertEquals(listOf(1, 1), result.evaluatedPlacementIds)
        assertEquals(
            OcrEvaluationMetric(OcrEvaluationMetricType.ROW_COVERAGE, 1, 12),
            result.metrics.single { it.type == OcrEvaluationMetricType.ROW_COVERAGE },
        )
    }

    @Test
    fun outOfRangeExpectedIdsDoNotContributeToCoverage() {
        val result = evaluator.evaluate(
            caseFor(
                expectedRows = listOf(
                    visibleRow(0, "Outside Zero", 0),
                    visibleRow(13, "Outside Thirteen", 0),
                ),
            ),
        )

        assertEquals(listOf(0, 13), result.evaluatedPlacementIds)
        assertEquals(
            OcrEvaluationMetric(OcrEvaluationMetricType.ROW_COVERAGE, 0, 12),
            result.metrics.single { it.type == OcrEvaluationMetricType.ROW_COVERAGE },
        )
    }

    @Test
    fun emptyExpectedRowsPreserveMetricDenominatorBoundaries() {
        val result = evaluator.evaluate(caseFor(expectedRows = emptyList()))

        assertEquals(
            OcrEvaluationMetric(OcrEvaluationMetricType.ROW_COVERAGE, 0, 12),
            result.metrics.single { it.type == OcrEvaluationMetricType.ROW_COVERAGE },
        )
        assertTrue(
            result.metrics
                .filter { it.type != OcrEvaluationMetricType.ROW_COVERAGE }
                .all { it.numerator == 0 && it.denominator == 0 },
        )
    }

    @Test
    fun coverageMetricReachesFullDenominatorForAllUniqueRows() {
        val result = evaluator.evaluate(
            caseFor(expectedRows = (1..12).map { visibleRow(it, "Player $it", it) }),
        )

        assertEquals(
            OcrEvaluationMetric(OcrEvaluationMetricType.ROW_COVERAGE, 12, 12),
            result.metrics.single { it.type == OcrEvaluationMetricType.ROW_COVERAGE },
        )
    }

    private fun caseFor(
        expectedRows: List<ExpectedScoreboardRow>,
        placement: PlacementParsingResult? = null,
        playerName: PlayerNameParsingResult? = null,
        kill: KillParsingResult? = null,
        review: OcrFailureAnalysisResult? = null,
    ) = RealScreenshotEvaluationCase(
        caseId = "sanitized-case",
        expectedRows = expectedRows,
        placementResult = placement,
        playerNameResult = playerName,
        killResult = kill,
        reviewResult = review,
        fixturePolicy = OcrEvaluationFixturePolicy.NO_REAL_SCREENSHOT_SYNTHETIC,
    )

    private fun visibleRow(placement: Int, name: String, kills: Int) = ExpectedScoreboardRow(
        expectedPlacementId = placement,
        placement = ExpectedOcrField(ExpectedFieldVisibility.VISIBLE, placement),
        playerName = ExpectedOcrField(ExpectedFieldVisibility.VISIBLE, name),
        kill = ExpectedOcrField(ExpectedFieldVisibility.VISIBLE, kills),
    )

    private fun constrainedRow(placement: Int) = ExpectedScoreboardRow(
        expectedPlacementId = placement,
        placement = ExpectedOcrField<Int>(ExpectedFieldVisibility.CONSTRAINED, null),
        playerName = ExpectedOcrField<String>(ExpectedFieldVisibility.CONSTRAINED, null),
        kill = ExpectedOcrField<Int>(ExpectedFieldVisibility.CONSTRAINED, null),
    )

    private fun placementRow(expectedPlacementId: Int, value: Int) = ParsedPlacementRow(
        expectedPlacementId = expectedPlacementId,
        panelId = panelId(expectedPlacementId),
        rowIndex = rowIndex(expectedPlacementId),
        status = PlacementParseStatus.DETECTED,
        detectedValue = value,
        evidence = emptyList(),
    )

    private fun playerNameRow(expectedPlacementId: Int, value: String): ParsedPlayerNameRow {
        val row = layoutRow(expectedPlacementId)
        return ParsedPlayerNameRow(
            expectedPlacementId = expectedPlacementId,
            panelId = panelId(expectedPlacementId),
            rowIndex = rowIndex(expectedPlacementId),
            playerNameZone = row.fieldZones.first { it.type == ScoreboardFieldZoneType.PLAYER_NAME },
            playerNameZoneRect = OcrPixelRect(0, 0, 1, 1),
            status = PlayerNameParseStatus.DETECTED,
            detectedName = value,
            failure = null,
            evidence = emptyList(),
        )
    }

    private fun killRow(expectedPlacementId: Int, value: Int): ParsedKillRow {
        val row = layoutRow(expectedPlacementId)
        return ParsedKillRow(
            expectedPlacementId = expectedPlacementId,
            panelId = panelId(expectedPlacementId),
            rowIndex = rowIndex(expectedPlacementId),
            eliminationValueZone = row.fieldZones.first { it.type == ScoreboardFieldZoneType.ELIMINATION_VALUE },
            eliminationValueZoneRect = OcrPixelRect(0, 0, 1, 1),
            status = KillParseStatus.DETECTED,
            detectedValue = value,
            failure = null,
            evidence = emptyList(),
        )
    }

    private fun reviewResult(placement: Int, fields: List<OcrReviewField>) = OcrFailureAnalysisResult(
        listOf(OcrReviewRow(placement, panelId(placement), rowIndex(placement), fields)),
    )

    private operator fun OcrFailureAnalysisResult.plus(other: OcrFailureAnalysisResult) =
        OcrFailureAnalysisResult(rows + other.rows)

    private fun acceptedFields() = OcrReviewFieldType.entries.map {
        OcrReviewField(
            it,
            OcrReviewStatus.ACCEPTED,
            OcrReviewSeverity.INFORMATIONAL,
            OcrReviewReason.Accepted,
            false,
            emptyList(),
        )
    }

    private fun reviewFields() = OcrReviewFieldType.entries.map {
        OcrReviewField(
            it,
            OcrReviewStatus.MISSING,
            OcrReviewSeverity.BLOCKING,
            OcrReviewReason.Missing,
            true,
            emptyList(),
        )
    }

    private fun layoutRow(placement: Int) = FreeFireMaxScoreboardLayout.definition.panels
        .first { it.id == panelId(placement) }
        .rows
        .first { it.placementId == placement }

    private fun panelId(placement: Int) = if (placement <= 5) ScoreboardPanelId.LEFT else ScoreboardPanelId.RIGHT

    private fun rowIndex(placement: Int) = if (placement <= 5) placement - 1 else placement - 6
}
