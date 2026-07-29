package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionFailure
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutDefinition
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
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParsingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingFailure
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult

enum class OcrReviewFieldType {
    PLACEMENT,
    PLAYER_NAME,
    KILL,
}

enum class OcrReviewStatus {
    ACCEPTED,
    MISSING,
    INVALID,
    AMBIGUOUS,
    DUPLICATE,
    UNSUPPORTED,
    UNCERTAIN,
}

enum class OcrReviewSeverity {
    BLOCKING,
    WARNING,
    INFORMATIONAL,
}

sealed interface OcrReviewReason {
    data object Accepted : OcrReviewReason
    data object Missing : OcrReviewReason
    data object Ambiguous : OcrReviewReason
    data object Duplicate : OcrReviewReason
    data object ParserOutputUnavailable : OcrReviewReason
    data object RawOcrEmpty : OcrReviewReason
    data object PlacementInvalid : OcrReviewReason
    data class PlayerNameInvalid(val failure: PlayerNameParseFailure?) : OcrReviewReason
    data class KillInvalid(val failure: KillParseFailure?) : OcrReviewReason
    data class PreprocessingFailure(val failure: OcrPreprocessingFailure) : OcrReviewReason
    data class RawExtractionFailure(val failure: RawOcrExtractionFailure) : OcrReviewReason
}

data class OcrReviewEvidence(
    val text: String?,
    val geometry: RawOcrGeometry?,
    val source: RawOcrExtractionResult?,
)

data class OcrReviewField(
    val type: OcrReviewFieldType,
    val status: OcrReviewStatus,
    val severity: OcrReviewSeverity,
    val reason: OcrReviewReason,
    val manualReviewRequired: Boolean,
    val evidence: List<OcrReviewEvidence>,
)

data class OcrReviewRow(
    val expectedPlacementId: Int,
    val panelId: ScoreboardPanelId,
    val rowIndex: Int,
    val fields: List<OcrReviewField>,
) {
    val manualReviewRequired: Boolean = fields.any(OcrReviewField::manualReviewRequired)
}

data class OcrFailureAnalysisInput(
    val preprocessingResult: OcrPreprocessingResult? = null,
    val extractionResults: List<RawOcrExtractionResult> = emptyList(),
    val placementResult: PlacementParsingResult? = null,
    val playerNameResult: PlayerNameParsingResult? = null,
    val killResult: KillParsingResult? = null,
    val layout: ScoreboardLayoutDefinition = FreeFireMaxScoreboardLayout.definition,
)

data class OcrFailureAnalysisResult(val rows: List<OcrReviewRow>) {
    val manualReviewRequired: Boolean = rows.any(OcrReviewRow::manualReviewRequired)
}

interface OcrFailureAnalyzer {
    fun analyze(input: OcrFailureAnalysisInput): OcrFailureAnalysisResult
}

class FixedLayoutOcrFailureAnalyzer : OcrFailureAnalyzer {
    override fun analyze(input: OcrFailureAnalysisInput): OcrFailureAnalysisResult {
        val preprocessingFailure = input.preprocessingResult as? OcrPreprocessingResult.Failed
        if (preprocessingFailure != null) {
            return fallbackResult(
                input.layout,
                OcrReviewFallback(
                    status = OcrReviewStatus.UNSUPPORTED,
                    severity = OcrReviewSeverity.BLOCKING,
                    reason = OcrReviewReason.PreprocessingFailure(preprocessingFailure.failure),
                    evidence = emptyList(),
                ),
            )
        }

        val fallback = fallback(input)
        val placements = input.placementResult?.rows?.associateBy {
            OcrReviewRowKey(it.panelId, it.rowIndex)
        }.orEmpty()
        val playerNames = input.playerNameResult?.rows?.associateBy {
            OcrReviewRowKey(it.panelId, it.rowIndex)
        }.orEmpty()
        val kills = input.killResult?.rows?.associateBy {
            OcrReviewRowKey(it.panelId, it.rowIndex)
        }.orEmpty()

        return OcrFailureAnalysisResult(
            input.layout.panels.flatMap { panel ->
                panel.rows.map { row ->
                    val key = OcrReviewRowKey(panel.id, row.rowIndex)
                    OcrReviewRow(
                        expectedPlacementId = row.placementId,
                        panelId = panel.id,
                        rowIndex = row.rowIndex,
                        fields = listOf(
                            placements[key]?.toReviewField() ?: fallback.forType(OcrReviewFieldType.PLACEMENT),
                            playerNames[key]?.toReviewField() ?: fallback.forType(OcrReviewFieldType.PLAYER_NAME),
                            kills[key]?.toReviewField() ?: fallback.forType(OcrReviewFieldType.KILL),
                        ),
                    )
                }
            },
        )
    }

    private fun fallbackResult(
        layout: ScoreboardLayoutDefinition,
        fallback: OcrReviewFallback,
    ): OcrFailureAnalysisResult = OcrFailureAnalysisResult(
        layout.panels.flatMap { panel ->
            panel.rows.map { row ->
                OcrReviewRow(
                    expectedPlacementId = row.placementId,
                    panelId = panel.id,
                    rowIndex = row.rowIndex,
                    fields = OcrReviewFieldType.entries.map(fallback::forType),
                )
            }
        },
    )

    private fun fallback(input: OcrFailureAnalysisInput): OcrReviewFallback = when {
            input.extractionResults.any { it is RawOcrExtractionResult.Failed } -> {
                val failure = input.extractionResults.filterIsInstance<RawOcrExtractionResult.Failed>().first()
                OcrReviewFallback(
                    status = OcrReviewStatus.UNCERTAIN,
                    severity = OcrReviewSeverity.BLOCKING,
                    reason = OcrReviewReason.RawExtractionFailure(failure.failure),
                    evidence = listOf(OcrReviewEvidence(null, null, failure)),
                )
            }

            input.extractionResults.isEmpty() ||
                input.extractionResults.all { it is RawOcrExtractionResult.Empty } -> OcrReviewFallback(
                    status = OcrReviewStatus.MISSING,
                    severity = OcrReviewSeverity.BLOCKING,
                    reason = OcrReviewReason.RawOcrEmpty,
                    evidence = input.extractionResults.map { OcrReviewEvidence(null, null, it) },
                )

            else -> OcrReviewFallback(
                status = OcrReviewStatus.UNCERTAIN,
                severity = OcrReviewSeverity.WARNING,
                reason = OcrReviewReason.ParserOutputUnavailable,
                evidence = emptyList(),
            )
    }

    private fun ParsedPlacementRow.toReviewField(): OcrReviewField = when (status) {
        PlacementParseStatus.DETECTED -> accepted(OcrReviewFieldType.PLACEMENT, evidence.placementEvidenceToReviewEvidence())
        PlacementParseStatus.MISSING -> missing(OcrReviewFieldType.PLACEMENT, evidence.placementEvidenceToReviewEvidence())
        PlacementParseStatus.AMBIGUOUS -> ambiguous(OcrReviewFieldType.PLACEMENT, evidence.placementEvidenceToReviewEvidence())
        PlacementParseStatus.DUPLICATE -> duplicate(OcrReviewFieldType.PLACEMENT, evidence.placementEvidenceToReviewEvidence())
        PlacementParseStatus.INVALID -> invalid(
            OcrReviewFieldType.PLACEMENT,
            OcrReviewReason.PlacementInvalid,
            evidence.placementEvidenceToReviewEvidence(),
        )
    }

    private fun ParsedPlayerNameRow.toReviewField(): OcrReviewField = when (status) {
        PlayerNameParseStatus.DETECTED -> accepted(OcrReviewFieldType.PLAYER_NAME, evidence.playerNameEvidenceToReviewEvidence())
        PlayerNameParseStatus.MISSING -> missing(OcrReviewFieldType.PLAYER_NAME, evidence.playerNameEvidenceToReviewEvidence())
        PlayerNameParseStatus.AMBIGUOUS -> ambiguous(OcrReviewFieldType.PLAYER_NAME, evidence.playerNameEvidenceToReviewEvidence())
        PlayerNameParseStatus.INVALID -> invalid(
            OcrReviewFieldType.PLAYER_NAME,
            OcrReviewReason.PlayerNameInvalid(failure),
            evidence.playerNameEvidenceToReviewEvidence(),
        )
    }

    private fun ParsedKillRow.toReviewField(): OcrReviewField = when (status) {
        KillParseStatus.DETECTED -> accepted(OcrReviewFieldType.KILL, evidence.killEvidenceToReviewEvidence())
        KillParseStatus.MISSING -> missing(OcrReviewFieldType.KILL, evidence.killEvidenceToReviewEvidence())
        KillParseStatus.AMBIGUOUS -> ambiguous(OcrReviewFieldType.KILL, evidence.killEvidenceToReviewEvidence())
        KillParseStatus.DUPLICATE -> duplicate(OcrReviewFieldType.KILL, evidence.killEvidenceToReviewEvidence())
        KillParseStatus.INVALID -> invalid(
            OcrReviewFieldType.KILL,
            OcrReviewReason.KillInvalid(failure),
            evidence.killEvidenceToReviewEvidence(),
        )
    }

    private fun accepted(type: OcrReviewFieldType, evidence: List<OcrReviewEvidence>) = OcrReviewField(
        type,
        OcrReviewStatus.ACCEPTED,
        OcrReviewSeverity.INFORMATIONAL,
        OcrReviewReason.Accepted,
        false,
        evidence,
    )

    private fun missing(type: OcrReviewFieldType, evidence: List<OcrReviewEvidence>) = OcrReviewField(
        type,
        OcrReviewStatus.MISSING,
        OcrReviewSeverity.BLOCKING,
        OcrReviewReason.Missing,
        true,
        evidence,
    )

    private fun ambiguous(type: OcrReviewFieldType, evidence: List<OcrReviewEvidence>) = OcrReviewField(
        type,
        OcrReviewStatus.AMBIGUOUS,
        OcrReviewSeverity.BLOCKING,
        OcrReviewReason.Ambiguous,
        true,
        evidence,
    )

    private fun duplicate(type: OcrReviewFieldType, evidence: List<OcrReviewEvidence>) = OcrReviewField(
        type,
        OcrReviewStatus.DUPLICATE,
        OcrReviewSeverity.BLOCKING,
        OcrReviewReason.Duplicate,
        true,
        evidence,
    )

    private fun invalid(
        type: OcrReviewFieldType,
        reason: OcrReviewReason,
        evidence: List<OcrReviewEvidence>,
    ) = OcrReviewField(
        type,
        OcrReviewStatus.INVALID,
        OcrReviewSeverity.BLOCKING,
        reason,
        true,
        evidence,
    )

    private fun List<PlacementOcrEvidence>.placementEvidenceToReviewEvidence(): List<OcrReviewEvidence> = map {
        OcrReviewEvidence(it.text, it.geometry, it.source)
    }

    private fun List<PlayerNameOcrEvidence>.playerNameEvidenceToReviewEvidence(): List<OcrReviewEvidence> = map {
        OcrReviewEvidence(it.text, it.geometry, it.source)
    }

    private fun List<KillOcrEvidence>.killEvidenceToReviewEvidence(): List<OcrReviewEvidence> = map {
        OcrReviewEvidence(it.text, it.geometry, it.source)
    }
}

private data class OcrReviewRowKey(val panelId: ScoreboardPanelId, val rowIndex: Int)

private data class OcrReviewFallback(
    val status: OcrReviewStatus,
    val severity: OcrReviewSeverity,
    val reason: OcrReviewReason,
    val evidence: List<OcrReviewEvidence>,
) {
    fun forType(type: OcrReviewFieldType): OcrReviewField = OcrReviewField(
        type = type,
        status = status,
        severity = severity,
        reason = reason,
        manualReviewRequired = status != OcrReviewStatus.ACCEPTED,
        evidence = evidence,
    )
}
