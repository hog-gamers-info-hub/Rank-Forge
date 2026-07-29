package com.hoggamers.rankforge.ocr.evaluation

import com.hoggamers.rankforge.domain.ocr.parsing.KillParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.KillParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParsingResult
import com.hoggamers.rankforge.domain.ocr.review.OcrFailureAnalysisResult
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewField
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewFieldType
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewStatus

enum class OcrEvaluationFixturePolicy {
    NO_REAL_SCREENSHOT_SYNTHETIC,
    LOCAL_ONLY_GENUINE,
    APPROVED_COMMITTED_GENUINE,
}

enum class ExpectedFieldVisibility {
    VISIBLE,
    MISSING,
    CONSTRAINED,
}

data class ExpectedOcrField<T>(
    val visibility: ExpectedFieldVisibility,
    val value: T?,
) {
    init {
        require((visibility == ExpectedFieldVisibility.VISIBLE) == (value != null)) {
            "Visible fields require a value; missing and constrained fields do not."
        }
    }
}

data class ExpectedScoreboardRow(
    val expectedPlacementId: Int,
    val placement: ExpectedOcrField<Int>,
    val playerName: ExpectedOcrField<String>,
    val kill: ExpectedOcrField<Int>,
)

data class RealScreenshotEvaluationCase(
    val caseId: String,
    val expectedRows: List<ExpectedScoreboardRow>,
    val placementResult: PlacementParsingResult?,
    val playerNameResult: PlayerNameParsingResult?,
    val killResult: KillParsingResult?,
    val reviewResult: OcrFailureAnalysisResult?,
    val fixturePolicy: OcrEvaluationFixturePolicy,
)

enum class OcrEvaluationMetricType {
    ROW_COVERAGE,
    PLACEMENT_CORRECTNESS,
    PLAYER_NAME_CORRECTNESS,
    KILL_CORRECTNESS,
    REVIEW_MARKER_CORRECTNESS,
    FALSE_ACCEPT_COUNT,
}

data class OcrEvaluationMetric(
    val type: OcrEvaluationMetricType,
    val numerator: Int,
    val denominator: Int,
)

enum class OcrEvaluationMismatchReason {
    VALUE_MISMATCH,
    REVIEW_MARKER_MISMATCH,
    FALSE_ACCEPT,
}

data class OcrEvaluationMismatch(
    val expectedPlacementId: Int,
    val fieldType: OcrReviewFieldType,
    val expected: String,
    val observed: String,
    val reason: OcrEvaluationMismatchReason,
)

data class RealScreenshotEvaluationResult(
    val caseId: String,
    val evaluatedPlacementIds: List<Int>,
    val metrics: List<OcrEvaluationMetric>,
    val mismatches: List<OcrEvaluationMismatch>,
) {
    val falseAcceptCount: Int = metrics.first { it.type == OcrEvaluationMetricType.FALSE_ACCEPT_COUNT }.numerator
}

class RealScreenshotEvaluator {
    fun evaluate(case: RealScreenshotEvaluationCase): RealScreenshotEvaluationResult {
        val expectedRows = case.expectedRows.sortedBy(ExpectedScoreboardRow::expectedPlacementId)
        val placements = case.placementResult?.rows?.associateBy { it.expectedPlacementId }.orEmpty()
        val playerNames = case.playerNameResult?.rows?.associateBy { it.expectedPlacementId }.orEmpty()
        val kills = case.killResult?.rows?.associateBy { it.expectedPlacementId }.orEmpty()
        val reviewRows = case.reviewResult?.rows?.associateBy { it.expectedPlacementId }.orEmpty()
        val comparisons = expectedRows.flatMap { expected ->
            val reviewFields = reviewRows[expected.expectedPlacementId]?.fields?.associateBy(OcrReviewField::type).orEmpty()
            listOf(
                compareField(
                    expected.expectedPlacementId,
                    OcrReviewFieldType.PLACEMENT,
                    expected.placement,
                    placements[expected.expectedPlacementId]
                        ?.takeIf { it.status == PlacementParseStatus.DETECTED }
                        ?.detectedValue,
                    reviewFields[OcrReviewFieldType.PLACEMENT],
                ),
                compareField(
                    expected.expectedPlacementId,
                    OcrReviewFieldType.PLAYER_NAME,
                    expected.playerName,
                    playerNames[expected.expectedPlacementId]
                        ?.takeIf { it.status == PlayerNameParseStatus.DETECTED }
                        ?.detectedName,
                    reviewFields[OcrReviewFieldType.PLAYER_NAME],
                ),
                compareField(
                    expected.expectedPlacementId,
                    OcrReviewFieldType.KILL,
                    expected.kill,
                    kills[expected.expectedPlacementId]
                        ?.takeIf { it.status == KillParseStatus.DETECTED }
                        ?.detectedValue,
                    reviewFields[OcrReviewFieldType.KILL],
                ),
            )
        }

        return RealScreenshotEvaluationResult(
            caseId = case.caseId,
            evaluatedPlacementIds = expectedRows.map(ExpectedScoreboardRow::expectedPlacementId),
            metrics = metrics(expectedRows, comparisons),
            mismatches = comparisons.flatMap(FieldComparison::mismatches),
        )
    }

    private fun <T> compareField(
        placementId: Int,
        fieldType: OcrReviewFieldType,
        expected: ExpectedOcrField<T>,
        observedValue: T?,
        reviewField: OcrReviewField?,
    ): FieldComparison {
        val reviewAccepted = reviewField?.status == OcrReviewStatus.ACCEPTED &&
            reviewField.manualReviewRequired.not()
        return if (expected.visibility == ExpectedFieldVisibility.VISIBLE) {
            val valueCorrect = observedValue == expected.value
            val reviewCorrect = reviewAccepted
            FieldComparison(
                fieldType = fieldType,
                visible = true,
                valueCorrect = valueCorrect,
                reviewCorrect = reviewCorrect,
                falseAccept = false,
                mismatches = buildList {
                    if (!valueCorrect) {
                        add(mismatch(placementId, fieldType, expected.value, observedValue, OcrEvaluationMismatchReason.VALUE_MISMATCH))
                    }
                    if (!reviewCorrect) {
                        add(
                            mismatch(
                                placementId,
                                fieldType,
                                OcrReviewStatus.ACCEPTED,
                                reviewField?.status,
                                OcrEvaluationMismatchReason.REVIEW_MARKER_MISMATCH,
                            ),
                        )
                    }
                },
            )
        } else {
            val reviewCorrect = reviewField?.manualReviewRequired == true
            FieldComparison(
                fieldType = fieldType,
                visible = false,
                valueCorrect = null,
                reviewCorrect = reviewCorrect,
                falseAccept = !reviewCorrect,
                mismatches = if (reviewCorrect) {
                    emptyList()
                } else {
                    listOf(
                        mismatch(
                            placementId,
                            fieldType,
                            expected.visibility,
                            reviewField?.status,
                            OcrEvaluationMismatchReason.FALSE_ACCEPT,
                        ),
                    )
                },
            )
        }
    }

    private fun metrics(
        expectedRows: List<ExpectedScoreboardRow>,
        comparisons: List<FieldComparison>,
    ): List<OcrEvaluationMetric> = listOf(
        OcrEvaluationMetric(
            OcrEvaluationMetricType.ROW_COVERAGE,
            expectedRows.map(ExpectedScoreboardRow::expectedPlacementId).distinct().count { it in 1..12 },
            12,
        ),
        correctnessMetric(OcrEvaluationMetricType.PLACEMENT_CORRECTNESS, comparisons, OcrReviewFieldType.PLACEMENT),
        correctnessMetric(OcrEvaluationMetricType.PLAYER_NAME_CORRECTNESS, comparisons, OcrReviewFieldType.PLAYER_NAME),
        correctnessMetric(OcrEvaluationMetricType.KILL_CORRECTNESS, comparisons, OcrReviewFieldType.KILL),
        OcrEvaluationMetric(
            OcrEvaluationMetricType.REVIEW_MARKER_CORRECTNESS,
            comparisons.count { it.reviewCorrect },
            comparisons.size,
        ),
        OcrEvaluationMetric(
            OcrEvaluationMetricType.FALSE_ACCEPT_COUNT,
            comparisons.count { it.falseAccept },
            comparisons.size,
        ),
    )

    private fun correctnessMetric(
        type: OcrEvaluationMetricType,
        comparisons: List<FieldComparison>,
        fieldType: OcrReviewFieldType,
    ): OcrEvaluationMetric {
        val visibleComparisons = comparisons.filter { it.fieldType == fieldType && it.visible }
        return OcrEvaluationMetric(
            type,
            visibleComparisons.count { it.valueCorrect == true },
            visibleComparisons.size,
        )
    }

    private fun mismatch(
        placementId: Int,
        fieldType: OcrReviewFieldType,
        expected: Any?,
        observed: Any?,
        reason: OcrEvaluationMismatchReason,
    ) = OcrEvaluationMismatch(
        expectedPlacementId = placementId,
        fieldType = fieldType,
        expected = expected?.toString() ?: "NONE",
        observed = observed?.toString() ?: "NONE",
        reason = reason,
    )
}

private data class FieldComparison(
    val fieldType: OcrReviewFieldType,
    val visible: Boolean,
    val valueCorrect: Boolean?,
    val reviewCorrect: Boolean,
    val falseAccept: Boolean,
    val mismatches: List<OcrEvaluationMismatch>,
)
