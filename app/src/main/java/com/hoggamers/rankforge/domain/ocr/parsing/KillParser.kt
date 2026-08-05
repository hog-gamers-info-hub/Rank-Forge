package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.NormalizedOcrRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneType
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelId
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardRowDefinition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import kotlin.math.roundToInt

enum class KillParseStatus {
    DETECTED,
    MISSING,
    AMBIGUOUS,
    DUPLICATE,
    INVALID,
}

enum class KillParseFailure {
    EMPTY_TEXT,
    NEGATIVE_VALUE,
    DECIMAL_VALUE,
    MALFORMED_TOKEN,
    INTEGER_OVERFLOW,
}

data class KillOcrEvidence(
    val text: String,
    val geometry: RawOcrGeometry?,
    val source: RawOcrExtractionResult,
)

data class ParsedKillRow(
    val expectedPlacementId: Int,
    val panelId: ScoreboardPanelId,
    val rowIndex: Int,
    val eliminationValueZone: ScoreboardFieldZoneDefinition,
    val eliminationValueZoneRect: OcrPixelRect,
    val status: KillParseStatus,
    val detectedValue: Int?,
    val failure: KillParseFailure?,
    val evidence: List<KillOcrEvidence>,
)

data class KillParsingInput(
    val extractions: List<RawOcrExtractionResult>,
    val layout: ScoreboardLayoutDefinition =
        FreeFireMaxScoreboardLayout.definition,
)

data class KillParsingResult(
    val rows: List<ParsedKillRow>,
)

interface KillParser {
    fun parse(
        input: KillParsingInput,
    ): KillParsingResult
}

class FixedLayoutKillParser : KillParser {

    override fun parse(
        input: KillParsingInput,
    ): KillParsingResult {
        val extracted =
            input.extractions
                .filterIsInstance<RawOcrExtractionResult.Extracted>()

        val evidence =
            extracted.flatMap(::entities)

        val referenceCandidate =
            extracted.firstOrNull()?.sourceCandidate

        return KillParsingResult(
            rows =
                input.layout.panels.flatMap { panel ->
                    panel.rows.map { row ->
                        parseRow(
                            panel = panel,
                            row = row,
                            layout = input.layout,
                            allEvidence = evidence,
                            referenceCandidate = referenceCandidate,
                        )
                    }
                },
        )
    }

    private fun parseRow(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        layout: ScoreboardLayoutDefinition,
        allEvidence: List<KillOcrEvidence>,
        referenceCandidate: OcrPreprocessingCandidate?,
    ): ParsedKillRow {
        val eliminationValueZone =
            requireNotNull(
                row.fieldZones.singleOrNull {
                    it.type ==
                        ScoreboardFieldZoneType.ELIMINATION_VALUE
                },
            ) {
                "Each scoreboard row must define one elimination-value zone."
            }

        val eliminationValueZoneRect =
            eliminationValueZoneRect(
                panel = panel,
                row = row,
                fieldZone = eliminationValueZone,
                layout = layout,
                candidate = referenceCandidate,
            )

        val evidence =
            allEvidence.filter { item ->
                val candidate =
                    item.source.sourceCandidate

                val zone =
                    eliminationValueZoneRect(
                        panel = panel,
                        row = row,
                        fieldZone = eliminationValueZone,
                        layout = layout,
                        candidate = candidate,
                    )

                item.intersects(zone)
            }

        val tokens =
            evidence.map {
                it.text.trim().toKillToken()
            }

        val validValues =
            tokens.mapNotNull {
                (it as? KillToken.Valid)?.value
            }

        val distinctValues =
            validValues.distinct()

        val status =
            when {
                evidence.isEmpty() ->
                    KillParseStatus.MISSING

                distinctValues.size > 1 ->
                    KillParseStatus.AMBIGUOUS

                validValues.size > 1 ->
                    KillParseStatus.DUPLICATE

                distinctValues.size == 1 ->
                    KillParseStatus.DETECTED

                else ->
                    KillParseStatus.INVALID
            }

        return ParsedKillRow(
            expectedPlacementId =
                row.placementId,
            panelId =
                panel.id,
            rowIndex =
                row.rowIndex,
            eliminationValueZone =
                eliminationValueZone,
            eliminationValueZoneRect =
                eliminationValueZoneRect,
            status =
                status,
            detectedValue =
                distinctValues.singleOrNull(),
            failure =
                if (
                    status ==
                    KillParseStatus.INVALID
                ) {
                    tokens
                        .filterIsInstance<KillToken.Invalid>()
                        .firstOrNull()
                        ?.failure
                } else {
                    null
                },
            evidence =
                evidence,
        )
    }

    private fun entities(
        extraction: RawOcrExtractionResult.Extracted,
    ): List<KillOcrEvidence> =
        extraction.blocks.flatMap { block ->
            block.lines.flatMap { line ->
                if (line.elements.isEmpty()) {
                    listOf(
                        KillOcrEvidence(
                            text = line.text,
                            geometry = line.geometry,
                            source = extraction,
                        ),
                    )
                } else {
                    line.elements.map { element ->
                        KillOcrEvidence(
                            text = element.text,
                            geometry = element.geometry,
                            source = extraction,
                        )
                    }
                }
            }
        }

    private fun eliminationValueZoneRect(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        fieldZone: ScoreboardFieldZoneDefinition,
        layout: ScoreboardLayoutDefinition,
        candidate: OcrPreprocessingCandidate?,
    ): OcrPixelRect {
        val rowHeight =
            panel.contentRect.height /
                panel.rows.size

        val rowY =
            panel.contentRect.y +
                row.rowIndex * rowHeight

        val relativeRect =
            fieldZone.relativeRect

        val layoutRect =
            NormalizedOcrRect(
                x =
                    panel.contentRect.x +
                        panel.contentRect.width *
                        relativeRect.x,
                y =
                    rowY +
                        rowHeight *
                        relativeRect.y,
                width =
                    panel.contentRect.width *
                        relativeRect.width,
                height =
                    rowHeight *
                        relativeRect.height,
            )

        return layoutRect.toCandidateLocalRect(
            layout = layout,
            candidate = candidate,
        )
    }

    private fun NormalizedOcrRect.toCandidateLocalRect(
        layout: ScoreboardLayoutDefinition,
        candidate: OcrPreprocessingCandidate?,
    ): OcrPixelRect {
        val overall =
            layout.overallContentRect

        val cropWidth =
            candidate?.cropRect?.width
                ?: (
                    overall.width *
                        layout.calibrationWidth
                    ).roundToInt()

        val cropHeight =
            candidate?.cropRect?.height
                ?: (
                    overall.height *
                        layout.calibrationHeight
                    ).roundToInt()

        val scale =
            candidate?.scaleFactor ?: 1.0

        return OcrPixelRect(
            x =
                (
                    (
                        (x - overall.x) /
                            overall.width
                        ) *
                        cropWidth *
                        scale
                    ).roundToInt(),
            y =
                (
                    (
                        (y - overall.y) /
                            overall.height
                        ) *
                        cropHeight *
                        scale
                    ).roundToInt(),
            width =
                (
                    (width / overall.width) *
                        cropWidth *
                        scale
                    ).roundToInt(),
            height =
                (
                    (height / overall.height) *
                        cropHeight *
                        scale
                    ).roundToInt(),
        )
    }

    private fun KillOcrEvidence.intersects(
        zone: OcrPixelRect,
    ): Boolean =
        geometry?.boundingBox?.let { box ->
            box.left < zone.x + zone.width &&
                box.right > zone.x &&
                box.top < zone.y + zone.height &&
                box.bottom > zone.y
        } == true

    private fun String.toKillToken():
        KillToken =
        when {
            isEmpty() ->
                KillToken.Invalid(
                    KillParseFailure.EMPTY_TEXT,
                )

            NEGATIVE_INTEGER.matches(this) ->
                KillToken.Invalid(
                    KillParseFailure.NEGATIVE_VALUE,
                )

            DECIMAL_NUMBER.matches(this) ->
                KillToken.Invalid(
                    KillParseFailure.DECIMAL_VALUE,
                )

            NON_NEGATIVE_INTEGER.matches(this) ->
                toIntOrNull()
                    ?.let(KillToken::Valid)
                    ?: KillToken.Invalid(
                        KillParseFailure.INTEGER_OVERFLOW,
                    )

            else ->
                KillToken.Invalid(
                    KillParseFailure.MALFORMED_TOKEN,
                )
        }

    private sealed interface KillToken {
        data class Valid(
            val value: Int,
        ) : KillToken

        data class Invalid(
            val failure: KillParseFailure,
        ) : KillToken
    }

    private companion object {
        val NEGATIVE_INTEGER =
            Regex("-\\d+")

        val DECIMAL_NUMBER =
            Regex("[+-]?(?:\\d+\\.\\d*|\\.\\d+)")

        val NON_NEGATIVE_INTEGER =
            Regex("\\d+")
    }
}