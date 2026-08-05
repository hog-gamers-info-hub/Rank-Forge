package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.NormalizedOcrRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelId
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardRowDefinition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import kotlin.math.roundToInt

enum class PlacementParseStatus {
    DETECTED,
    MISSING,
    AMBIGUOUS,
    DUPLICATE,
    INVALID,
}

data class PlacementOcrEvidence(
    val text: String,
    val geometry: RawOcrGeometry?,
    val source: RawOcrExtractionResult,
)

data class ParsedPlacementRow(
    val expectedPlacementId: Int,
    val panelId: ScoreboardPanelId,
    val rowIndex: Int,
    val status: PlacementParseStatus,
    val detectedValue: Int?,
    val evidence: List<PlacementOcrEvidence>,
)

data class PlacementParsingInput(
    val extractions: List<RawOcrExtractionResult>,
    val layout: ScoreboardLayoutDefinition =
        FreeFireMaxScoreboardLayout.definition,
)

data class PlacementParsingResult(
    val rows: List<ParsedPlacementRow>,
)

interface PlacementParser {
    fun parse(
        input: PlacementParsingInput,
    ): PlacementParsingResult
}

class FixedLayoutPlacementParser : PlacementParser {

    override fun parse(
        input: PlacementParsingInput,
    ): PlacementParsingResult {
        val rows =
            input.layout.panels.flatMap { panel ->
                panel.rows.map { row ->
                    panel to row
                }
            }

        val allEvidence =
            input.extractions
                .filterIsInstance<RawOcrExtractionResult.Extracted>()
                .flatMap { extraction ->
                    entities(extraction).map { entity ->
                        PlacementOcrEvidence(
                            text = entity.first,
                            geometry = entity.second,
                            source = extraction,
                        )
                    }
                }

        val parsed =
            rows.map { (panel, row) ->
                val evidence =
                    allEvidence.filter { item ->
                        val candidate =
                            item.source.sourceCandidate

                        val zone =
                            placementZoneRect(
                                panel = panel,
                                row = row,
                                layout = input.layout,
                                candidate = candidate,
                            )

                        item.geometry.intersects(zone)
                    }

                val values =
                    evidence
                        .mapNotNull {
                            token(it.text)
                        }
                        .distinct()

                val status =
                    when {
                        values.size > 1 ->
                            PlacementParseStatus.AMBIGUOUS

                        values.size == 1 ->
                            PlacementParseStatus.DETECTED

                        evidence.isEmpty() ->
                            PlacementParseStatus.MISSING

                        else ->
                            PlacementParseStatus.INVALID
                    }

                ParsedPlacementRow(
                    expectedPlacementId =
                        row.placementId,
                    panelId =
                        panel.id,
                    rowIndex =
                        row.rowIndex,
                    status =
                        status,
                    detectedValue =
                        values.singleOrNull(),
                    evidence =
                        evidence,
                )
            }

        val duplicates =
            parsed
                .filter {
                    it.status ==
                        PlacementParseStatus.DETECTED
                }
                .groupBy {
                    it.detectedValue
                }
                .filterValues {
                    it.size > 1
                }
                .keys

        return PlacementParsingResult(
            rows =
                parsed.map { row ->
                    if (
                        row.detectedValue in duplicates
                    ) {
                        row.copy(
                            status =
                                PlacementParseStatus.DUPLICATE,
                        )
                    } else {
                        row
                    }
                },
        )
    }

    private fun entities(
        extraction: RawOcrExtractionResult.Extracted,
    ): List<Pair<String, RawOcrGeometry?>> =
        extraction.blocks.flatMap { block ->
            block.lines.flatMap { line ->
                if (line.elements.isEmpty()) {
                    listOf(
                        line.text to line.geometry,
                    )
                } else {
                    line.elements.map { element ->
                        element.text to element.geometry
                    }
                }
            }
        }

    private fun placementZoneRect(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        layout: ScoreboardLayoutDefinition,
        candidate: OcrPreprocessingCandidate,
    ): OcrPixelRect {
        val rowHeight =
            panel.contentRect.height /
                panel.rows.size

        val rowY =
            panel.contentRect.y +
                row.rowIndex * rowHeight

        val layoutRect =
            NormalizedOcrRect(
                x = panel.contentRect.x,
                y = rowY,
                width =
                    panel.contentRect.width *
                        PLACEMENT_ZONE_WIDTH_RATIO,
                height = rowHeight,
            )

        return layoutRect.toCandidateLocalRect(
            layout = layout,
            candidate = candidate,
        )
    }

    private fun NormalizedOcrRect.toCandidateLocalRect(
        layout: ScoreboardLayoutDefinition,
        candidate: OcrPreprocessingCandidate,
    ): OcrPixelRect {
        val overall =
            layout.overallContentRect

        val scale =
            candidate.scaleFactor ?: 1.0

        return OcrPixelRect(
            x =
                (
                    (
                        (x - overall.x) /
                            overall.width
                        ) *
                        candidate.cropRect.width *
                        scale
                    ).roundToInt(),
            y =
                (
                    (
                        (y - overall.y) /
                            overall.height
                        ) *
                        candidate.cropRect.height *
                        scale
                    ).roundToInt(),
            width =
                (
                    (width / overall.width) *
                        candidate.cropRect.width *
                        scale
                    ).roundToInt(),
            height =
                (
                    (height / overall.height) *
                        candidate.cropRect.height *
                        scale
                    ).roundToInt(),
        )
    }

    private fun RawOcrGeometry?.intersects(
        zone: OcrPixelRect,
    ): Boolean =
        this?.boundingBox?.let { box ->
            box.left < zone.x + zone.width &&
                box.right > zone.x &&
                box.top < zone.y + zone.height &&
                box.bottom > zone.y
        } == true

    private fun token(
        text: String,
    ): Int? {
        val normalized =
            text
                .trim()
                .removeSuffix(".")
                .replace('O', '0')
                .replace('o', '0')

        return normalized
            .toIntOrNull()
            ?.takeIf {
                it in 1..12
            }
    }

    private companion object {
        const val PLACEMENT_ZONE_WIDTH_RATIO =
            0.12
    }
}