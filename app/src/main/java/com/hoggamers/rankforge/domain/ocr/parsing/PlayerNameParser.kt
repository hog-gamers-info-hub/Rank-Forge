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

enum class PlayerNameParseStatus {
    DETECTED,
    MISSING,
    AMBIGUOUS,
    INVALID,
}

enum class PlayerNameParseFailure {
    EMPTY_TEXT,
    NUMERIC_TEXT,
}

data class PlayerNameOcrEvidence(
    val text: String,
    val geometry: RawOcrGeometry?,
    val source: RawOcrExtractionResult,
)

data class ParsedPlayerNameRow(
    val expectedPlacementId: Int,
    val panelId: ScoreboardPanelId,
    val rowIndex: Int,
    val playerNameZone: ScoreboardFieldZoneDefinition,
    val playerNameZoneRect: OcrPixelRect,
    val status: PlayerNameParseStatus,
    val detectedName: String?,
    val failure: PlayerNameParseFailure?,
    val evidence: List<PlayerNameOcrEvidence>,
)

data class PlayerNameParsingInput(
    val extractions: List<RawOcrExtractionResult>,
    val layout: ScoreboardLayoutDefinition =
        FreeFireMaxScoreboardLayout.definition,
)

data class PlayerNameParsingResult(
    val rows: List<ParsedPlayerNameRow>,
)

interface PlayerNameParser {
    fun parse(
        input: PlayerNameParsingInput,
    ): PlayerNameParsingResult
}

class FixedLayoutPlayerNameParser : PlayerNameParser {

    override fun parse(
        input: PlayerNameParsingInput,
    ): PlayerNameParsingResult {
        val extracted =
            input.extractions
                .filterIsInstance<RawOcrExtractionResult.Extracted>()

        val evidence =
            extracted.flatMap(::entities)

        val referenceCandidate =
            extracted.firstOrNull()?.sourceCandidate

        val rows =
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
            }

        return PlayerNameParsingResult(rows)
    }

    private fun parseRow(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        layout: ScoreboardLayoutDefinition,
        allEvidence: List<PlayerNameOcrEvidence>,
        referenceCandidate: OcrPreprocessingCandidate?,
    ): ParsedPlayerNameRow {
        val playerNameZone =
            requireNotNull(
                row.fieldZones.singleOrNull {
                    it.type ==
                        ScoreboardFieldZoneType.PLAYER_NAME
                },
            ) {
                "Each scoreboard row must define one player-name zone."
            }

        val playerNameZoneRect =
            playerNameZoneRect(
                panel = panel,
                row = row,
                fieldZone = playerNameZone,
                layout = layout,
                candidate = referenceCandidate,
            )

        val evidence =
            allEvidence.filter { item ->
                val candidate =
                    item.source.sourceCandidate

                val zone =
                    playerNameZoneRect(
                        panel = panel,
                        row = row,
                        fieldZone = playerNameZone,
                        layout = layout,
                        candidate = candidate,
                    )

                item.intersects(zone)
            }

        val candidates =
            evidence
                .map {
                    it.text.trim()
                }
                .filter {
                    it.isNotEmpty() &&
                        !it.isNumeric()
                }
                .distinct()

        val failure =
            evidence.firstNotNullOfOrNull {
                it.text.trim().failure()
            }

        val status =
            when {
                candidates.size == 1 ->
                    PlayerNameParseStatus.DETECTED

                candidates.size > 1 ->
                    PlayerNameParseStatus.AMBIGUOUS

                evidence.isEmpty() ->
                    PlayerNameParseStatus.MISSING

                else ->
                    PlayerNameParseStatus.INVALID
            }

        return ParsedPlayerNameRow(
            expectedPlacementId =
                row.placementId,
            panelId =
                panel.id,
            rowIndex =
                row.rowIndex,
            playerNameZone =
                playerNameZone,
            playerNameZoneRect =
                playerNameZoneRect,
            status =
                status,
            detectedName =
                candidates.singleOrNull(),
            failure =
                if (
                    status ==
                    PlayerNameParseStatus.INVALID
                ) {
                    failure
                } else {
                    null
                },
            evidence =
                evidence,
        )
    }

    private fun entities(
        extraction: RawOcrExtractionResult.Extracted,
    ): List<PlayerNameOcrEvidence> =
        extraction.blocks.flatMap { block ->
            block.lines.flatMap { line ->
                if (line.elements.isEmpty()) {
                    listOf(
                        PlayerNameOcrEvidence(
                            text = line.text,
                            geometry = line.geometry,
                            source = extraction,
                        ),
                    )
                } else {
                    line.elements.map { element ->
                        PlayerNameOcrEvidence(
                            text = element.text,
                            geometry = element.geometry,
                            source = extraction,
                        )
                    }
                }
            }
        }

    private fun playerNameZoneRect(
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

    private fun PlayerNameOcrEvidence.intersects(
        zone: OcrPixelRect,
    ): Boolean =
        geometry?.boundingBox?.let { box ->
            box.left < zone.x + zone.width &&
                box.right > zone.x &&
                box.top < zone.y + zone.height &&
                box.bottom > zone.y
        } == true

    private fun String.isNumeric():
        Boolean =
        matches(NUMERIC_TEXT)

    private fun String.failure():
        PlayerNameParseFailure? =
        when {
            isEmpty() ->
                PlayerNameParseFailure.EMPTY_TEXT

            isNumeric() ->
                PlayerNameParseFailure.NUMERIC_TEXT

            else ->
                null
        }

    private companion object {
        val NUMERIC_TEXT =
            Regex("[+-]?\\d+")
    }
}