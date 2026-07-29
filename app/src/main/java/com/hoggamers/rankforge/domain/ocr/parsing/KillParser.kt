package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneType
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelId
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardRowDefinition
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
    val layout: ScoreboardLayoutDefinition = FreeFireMaxScoreboardLayout.definition,
)

data class KillParsingResult(val rows: List<ParsedKillRow>)

interface KillParser {
    fun parse(input: KillParsingInput): KillParsingResult
}

class FixedLayoutKillParser : KillParser {
    override fun parse(input: KillParsingInput): KillParsingResult {
        val evidence = input.extractions
            .filterIsInstance<RawOcrExtractionResult.Extracted>()
            .flatMap(::entities)

        return KillParsingResult(
            input.layout.panels.flatMap { panel ->
                panel.rows.map { row -> parseRow(panel, row, input.layout, evidence) }
            },
        )
    }

    private fun parseRow(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        layout: ScoreboardLayoutDefinition,
        allEvidence: List<KillOcrEvidence>,
    ): ParsedKillRow {
        val eliminationValueZone = requireNotNull(
            row.fieldZones.singleOrNull { it.type == ScoreboardFieldZoneType.ELIMINATION_VALUE },
        ) { "Each scoreboard row must define one elimination-value zone." }
        val eliminationValueZoneRect = eliminationValueZoneRect(panel, row, eliminationValueZone, layout)
        val evidence = allEvidence.filter { it.intersects(eliminationValueZoneRect) }
        val tokens = evidence.map { it.text.trim().toKillToken() }
        val validValues = tokens.mapNotNull { (it as? KillToken.Valid)?.value }
        val distinctValues = validValues.distinct()
        val status = when {
            evidence.isEmpty() -> KillParseStatus.MISSING
            distinctValues.size > 1 -> KillParseStatus.AMBIGUOUS
            validValues.size > 1 -> KillParseStatus.DUPLICATE
            distinctValues.size == 1 -> KillParseStatus.DETECTED
            else -> KillParseStatus.INVALID
        }

        return ParsedKillRow(
            expectedPlacementId = row.placementId,
            panelId = panel.id,
            rowIndex = row.rowIndex,
            eliminationValueZone = eliminationValueZone,
            eliminationValueZoneRect = eliminationValueZoneRect,
            status = status,
            detectedValue = distinctValues.singleOrNull(),
            failure = if (status == KillParseStatus.INVALID) {
                tokens.filterIsInstance<KillToken.Invalid>().firstOrNull()?.failure
            } else {
                null
            },
            evidence = evidence,
        )
    }

    private fun entities(extraction: RawOcrExtractionResult.Extracted): List<KillOcrEvidence> =
        extraction.blocks.flatMap { block ->
            block.lines.flatMap { line ->
                if (line.elements.isEmpty()) {
                    listOf(KillOcrEvidence(line.text, line.geometry, extraction))
                } else {
                    line.elements.map { element ->
                        KillOcrEvidence(element.text, element.geometry, extraction)
                    }
                }
            }
        }

    private fun eliminationValueZoneRect(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        fieldZone: ScoreboardFieldZoneDefinition,
        layout: ScoreboardLayoutDefinition,
    ): OcrPixelRect {
        val rowHeight = panel.contentRect.height / panel.rows.size
        val rowY = panel.contentRect.y + row.rowIndex * rowHeight
        val relativeRect = fieldZone.relativeRect
        return OcrPixelRect(
            x = ((panel.contentRect.x + panel.contentRect.width * relativeRect.x) * layout.calibrationWidth)
                .roundToInt(),
            y = ((rowY + rowHeight * relativeRect.y) * layout.calibrationHeight).roundToInt(),
            width = (panel.contentRect.width * relativeRect.width * layout.calibrationWidth).roundToInt(),
            height = (rowHeight * relativeRect.height * layout.calibrationHeight).roundToInt(),
        )
    }

    private fun KillOcrEvidence.intersects(zone: OcrPixelRect): Boolean = geometry?.boundingBox?.let {
        it.left < zone.x + zone.width &&
            it.right > zone.x &&
            it.top < zone.y + zone.height &&
            it.bottom > zone.y
    } == true

    private fun String.toKillToken(): KillToken = when {
        isEmpty() -> KillToken.Invalid(KillParseFailure.EMPTY_TEXT)
        NEGATIVE_INTEGER.matches(this) -> KillToken.Invalid(KillParseFailure.NEGATIVE_VALUE)
        DECIMAL_NUMBER.matches(this) -> KillToken.Invalid(KillParseFailure.DECIMAL_VALUE)
        NON_NEGATIVE_INTEGER.matches(this) -> toIntOrNull()?.let(KillToken::Valid)
            ?: KillToken.Invalid(KillParseFailure.INTEGER_OVERFLOW)
        else -> KillToken.Invalid(KillParseFailure.MALFORMED_TOKEN)
    }

    private sealed interface KillToken {
        data class Valid(val value: Int) : KillToken
        data class Invalid(val failure: KillParseFailure) : KillToken
    }

    private companion object {
        val NEGATIVE_INTEGER = Regex("-\\d+")
        val DECIMAL_NUMBER = Regex("[+-]?(?:\\d+\\.\\d*|\\.\\d+)")
        val NON_NEGATIVE_INTEGER = Regex("\\d+")
    }
}
