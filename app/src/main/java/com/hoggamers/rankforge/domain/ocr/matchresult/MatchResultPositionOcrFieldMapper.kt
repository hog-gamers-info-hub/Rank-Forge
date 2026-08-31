package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

data class MatchResultPositionOcrInput(
    val role: MatchResultScreenshotRole,
    val position: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val blocks: List<RawOcrBlock>,
    val rowCrops: List<MatchResultPositionRowCrop>,
    val placementVerification: MatchResultNumericVerification,
    val killVerifications: Map<Int, MatchResultNumericVerification>,
    val structuralIdentityValid: Boolean = true,
) {
    init {
        require(position in 1..12)
        require(cropWidth > 0 && cropHeight > 0)
    }
}

data class MatchResultPositionSemanticResult(
    val role: MatchResultScreenshotRole,
    val position: Int,
    val fields: List<MatchResultOcrField>,
    val row: MatchResultOcrRow?,
    val placementVerification: MatchResultNumericVerification,
    val killVerifications: Map<Int, MatchResultNumericVerification>,
    val structuralIdentityValid: Boolean,
    val isAutoAcceptable: Boolean,
    val basicKillEvidence: Map<Int, ParsedEliminationText?> = emptyMap(),
    val playerBoundaryEvidence: Map<Int, MatchResultPlayerBoundaryDecision> = emptyMap(),
)

data class MatchResultPositionSemanticBatchResult(
    val role: MatchResultScreenshotRole,
    val results: List<MatchResultPositionSemanticResult>,
    val sequenceValidation: MatchResultPositionSequenceValidation,
    val isAutoAcceptable: Boolean,
)

enum class MatchResultEliminationPrefixType {
    EXPLICIT_NUMERIC,
    O_NORMALIZED,
    EMPTY_PREFIX,
}

data class ParsedEliminationText(
    val kill: Int?,
    val playerSuffix: String?,
    val markerMatched: Boolean,
    val prefixType: MatchResultEliminationPrefixType = MatchResultEliminationPrefixType.EMPTY_PREFIX,
    val rawText: String = "",
    val markerType: String? = null,
)

enum class MatchResultPlayerBoundaryReason {
    STRONG_KILL_ANCHOR,
    WEAK_NO_PREFIX,
    NO_VALID_ANCHOR,
}

data class MatchResultPlayerBoundaryDecision(
    val anchorFound: Boolean,
    val anchorPrefixType: MatchResultEliminationPrefixType?,
    val markerType: String?,
    val anchorRegion: String,
    val boundaryAccepted: Boolean,
    val reason: MatchResultPlayerBoundaryReason,
)

class MatchResultPositionOcrFieldMapper {
    fun map(input: MatchResultPositionOcrInput): MatchResultPositionSemanticResult {
        val visualRow = when (input.role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> null
            MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                if (input.position == 11) MatchResultOcrVisualRow.A else MatchResultOcrVisualRow.B
        }
        val lines = input.blocks.flatMap { it.lines }.sortedWith(RAW_LINE_ORDER)
        val linesByRow = input.rowCrops.associate { row ->
            row.rowIndex to lines.filter { line -> row.containsCenter(line.centerY()) }
        }
        val slotSemantics = (1..4).associateWith { slot ->
            parseSlot(input, linesByRow[slotRow(slot)].orEmpty(), slot)
        }
        val fields = buildList {
            val placementRaw = input.placementVerification.candidates
                .map { it.rawText.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
            // Phase 1 crop identity is authoritative for placement. Focused PP placement
            // verification remains diagnostic metadata only and never changes this field.
            val placementStatus = MatchResultOcrFieldStatus.TEMPLATE_ONLY
            add(field(
                id = placementId(input),
                type = MatchResultOcrFieldType.PLACEMENT,
                position = input.position,
                visualRow = visualRow,
                slot = null,
                rect = placementRect(input.cropWidth, input.cropHeight),
                ocrText = placementRaw,
                resolvedText = if (input.structuralIdentityValid) input.position.toString() else "",
                status = placementStatus,
            ))

            (1..4).forEach { slot ->
                val rowIndex = slotRow(slot)
                val semantics = slotSemantics.getValue(slot)
                val isFirstPlayer = isFirstPlayer(slot)
                val playerText = semantics.playerText
                val playerRect = playerRect(input, rowIndex, isFirstPlayer)
                add(field(
                    id = "PLAYER_${input.position}_$slot",
                    type = MatchResultOcrFieldType.PLAYER,
                    position = input.position,
                    visualRow = visualRow,
                    slot = slot,
                    rect = playerRect,
                    ocrText = playerText,
                    resolvedText = playerText,
                    status = if (playerText.isBlank()) MatchResultOcrFieldStatus.EMPTY else MatchResultOcrFieldStatus.DIRECT_TEXT,
                ))

                add(killField(input, visualRow, slot, rowIndex, isFirstPlayer, semantics.elimination))
            }
        }

        val source = when (input.role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultOcrRowSource.UPPER_TEMPLATE
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> if (input.position == 11) {
                MatchResultOcrRowSource.LOWER_ROW_A
            } else {
                MatchResultOcrRowSource.LOWER_ROW_B
            }
        }
        val row = runCatching {
            MatchResultOcrRowAssembler.assemble(
                position = input.position,
                source = source,
                fields = fields,
                visualRow = visualRow,
            )
        }.getOrNull()
        val playerFields = fields.filter { it.type == MatchResultOcrFieldType.PLAYER && it.resolvedText.isNotBlank() }
        val allPresentPlayersHaveKills = playerFields.all { player ->
            fields.firstOrNull {
                it.type == MatchResultOcrFieldType.KILL && it.slot == player.slot
            }?.resolvedText?.isNotBlank() == true
        }
        val noKillConflict = input.killVerifications.values.none { it is MatchResultNumericVerification.Conflict }
        val placementNotConflict = input.placementVerification !is MatchResultNumericVerification.Conflict
        return MatchResultPositionSemanticResult(
            role = input.role,
            position = input.position,
            fields = fields,
            row = row,
            placementVerification = input.placementVerification,
            killVerifications = input.killVerifications,
            structuralIdentityValid = input.structuralIdentityValid,
            isAutoAcceptable = input.structuralIdentityValid && placementNotConflict &&
                allPresentPlayersHaveKills && noKillConflict,
            basicKillEvidence = slotSemantics.mapValues { it.value.elimination },
            playerBoundaryEvidence = slotSemantics.mapNotNull { (slot, semantics) ->
                semantics.playerBoundary?.let { slot to it }
            }.toMap(),
        )
    }

    fun mapBatch(
        role: MatchResultScreenshotRole,
        inputs: List<MatchResultPositionOcrInput>,
        allowUpperPositionElevenFallback: Boolean = false,
    ): MatchResultPositionSemanticBatchResult {
        val sequence = MatchResultPositionSequenceValidator.validate(
            role = role,
            positions = inputs.map { it.position },
            allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
        )
        val results = inputs.map(::map)
        return MatchResultPositionSemanticBatchResult(
            role = role,
            results = results,
            sequenceValidation = sequence,
            isAutoAcceptable = sequence.isValid && results.all { it.isAutoAcceptable },
        )
    }

    private fun parseSlot(
        input: MatchResultPositionOcrInput,
        rowLines: List<RawOcrLine>,
        slot: Int,
    ): SlotSemantic {
        val first = isFirstPlayer(slot)
        if (input.position <= 5) {
            val playerLines = rowLines.filter {
                it.centerX() in playerRange(input.cropWidth, first) &&
                    !it.text.parseElimination().markerMatched
            }
            val elimination = rowLines.firstOrNull {
                it.centerX() in killRange(input.cropWidth, first) &&
                    it.text.parseElimination().markerMatched
            }?.text?.let(MatchResultPositionSemanticTextParser::parse)
            return SlotSemantic(
                playerText = playerLines.joinToString(" ") { it.text.trim() }.trim()
                    .ifBlank { if (!first) elimination?.playerSuffix.orEmpty() else "" },
                elimination = elimination,
            )
        }

        val middleLines = rowLines.filter {
            it.centerX() in scaledRange(input.cropWidth, RIGHT_MERGED_RANGE)
        }
        val middleElimination = middleLines.firstOrNull {
            it.text.parseElimination().markerMatched
        }?.text?.let(MatchResultPositionSemanticTextParser::parse)
        if (first) {
            val playerLines = rowLines.filter {
                it.centerX() in scaledRange(input.cropWidth, RIGHT_LEFT_PLAYER_RANGE) &&
                    !it.text.parseElimination().markerMatched
            }
            return SlotSemantic(
                playerText = playerLines.joinToString(" ") { it.text.trim() }.trim(),
                elimination = middleElimination,
            )
        }
        val playerBoundary = findStrongPlayerBoundary(middleLines)
        val rawPlayerText = playerBoundary.anchor?.let { anchor ->
            buildList {
                anchor.parsed.playerSuffix?.takeIf { it.isNotBlank() }?.let(::add)
                middleLines
                    .filterNot { it === anchor.line }
                    .map { it.text.trim() }
                    .filter { it.isNotBlank() }
                    .forEach(::add)
            }.joinToString(" ").trim()
        } ?: middleLines.joinToString(" ") { it.text.trim() }.trim()
        val playerText = if (
            input.position in 6..12 && !playerBoundary.decision.boundaryAccepted
        ) {
            stripLeadingMergedEliminationPrefix(rawPlayerText)
        } else {
            rawPlayerText
        }
        val rightElimination = rowLines
            .filter { it.centerX() in scaledRange(input.cropWidth, RIGHT_KILL_RANGE) }
            .firstOrNull { it.text.parseElimination().markerMatched }
            ?.text?.let(MatchResultPositionSemanticTextParser::parse)
        return SlotSemantic(
            playerText = playerText,
            elimination = rightElimination,
            playerBoundary = playerBoundary.decision,
        )
    }

    private fun stripLeadingMergedEliminationPrefix(text: String): String {
        for (startIndex in 0..2) {
            for (marker in MERGED_ELIMINATION_MARKERS) {
                if (
                    startIndex + marker.length <= text.length &&
                    text.regionMatches(
                        startIndex,
                        marker,
                        0,
                        marker.length,
                        ignoreCase = true,
                    )
                ) {
                    return text.substring(startIndex + marker.length).trim()
                }
            }
        }
        return text
    }

    private fun killField(
        input: MatchResultPositionOcrInput,
        visualRow: MatchResultOcrVisualRow?,
        slot: Int,
        rowIndex: Int,
        first: Boolean,
        parsed: ParsedEliminationText?,
    ) = field(
        id = "KILL_${input.position}_$slot",
        type = MatchResultOcrFieldType.KILL,
        position = input.position,
        visualRow = visualRow,
        slot = slot,
        rect = killRect(input, rowIndex, first),
        ocrText = parsed?.rawText.orEmpty(),
        resolvedText = if (parsed?.markerMatched == true) (parsed.kill ?: 0).toString() else "",
        status = when {
            parsed?.markerMatched != true -> MatchResultOcrFieldStatus.EMPTY
            parsed.prefixType == MatchResultEliminationPrefixType.O_NORMALIZED -> MatchResultOcrFieldStatus.O_NORMALIZED_TO_0
            else -> MatchResultOcrFieldStatus.DIRECT_NUMERIC
        },
    )

    private data class SlotSemantic(
        val playerText: String,
        val elimination: ParsedEliminationText?,
        val playerBoundary: MatchResultPlayerBoundaryDecision? = null,
    )

    private data class StrongPlayerBoundaryAnchor(
        val line: RawOcrLine,
        val parsed: ParsedEliminationText,
    )

    private data class PlayerBoundaryResolution(
        val anchor: StrongPlayerBoundaryAnchor?,
        val decision: MatchResultPlayerBoundaryDecision,
    )

    private fun findStrongPlayerBoundary(
        middleLines: List<RawOcrLine>,
    ): PlayerBoundaryResolution {
        val parsedLines = middleLines.map { line -> line to line.text.parseElimination() }
        val strong = parsedLines.firstOrNull { (_, parsed) ->
            parsed.markerMatched &&
                (parsed.prefixType == MatchResultEliminationPrefixType.O_NORMALIZED ||
                    (parsed.prefixType == MatchResultEliminationPrefixType.EXPLICIT_NUMERIC && parsed.kill != null))
        }
        if (strong != null) {
            return PlayerBoundaryResolution(
                anchor = StrongPlayerBoundaryAnchor(strong.first, strong.second),
                decision = MatchResultPlayerBoundaryDecision(
                    anchorFound = true,
                    anchorPrefixType = strong.second.prefixType,
                    markerType = strong.second.markerType,
                    anchorRegion = "MIDDLE",
                    boundaryAccepted = true,
                    reason = MatchResultPlayerBoundaryReason.STRONG_KILL_ANCHOR,
                ),
            )
        }
        val weak = parsedLines.firstOrNull { (_, parsed) -> parsed.markerMatched }
        return PlayerBoundaryResolution(
            anchor = null,
            decision = MatchResultPlayerBoundaryDecision(
                anchorFound = weak != null,
                anchorPrefixType = weak?.second?.prefixType,
                markerType = weak?.second?.markerType,
                anchorRegion = "MIDDLE",
                boundaryAccepted = false,
                reason = if (weak == null) {
                    MatchResultPlayerBoundaryReason.NO_VALID_ANCHOR
                } else {
                    MatchResultPlayerBoundaryReason.WEAK_NO_PREFIX
                },
            ),
        )
    }

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        visualRow: MatchResultOcrVisualRow?,
        slot: Int?,
        rect: MatchResultOcrRect,
        ocrText: String,
        resolvedText: String,
        status: MatchResultOcrFieldStatus,
    ) = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = visualRow,
        slot = slot,
        canonicalRect = rect,
        mappedRect = rect,
        ocrText = ocrText,
        resolvedText = resolvedText,
        status = status,
    )

    private fun placementId(input: MatchResultPositionOcrInput): String = when (input.role) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER -> "PLACEMENT_${input.position}"
        MatchResultScreenshotRole.MATCH_RESULT_LOWER -> "LOWER_ROW_${if (input.position == 11) "A" else "B"}_PLACEMENT"
    }

    private fun placementRect(width: Int, height: Int) = MatchResultOcrRect(0.0, 0.0, width * 0.10, height.toDouble())

    private fun playerRect(input: MatchResultPositionOcrInput, rowIndex: Int, first: Boolean): MatchResultOcrRect {
        val range = if (input.position <= 5) playerRange(input.cropWidth, first) else if (first) {
            RIGHT_LEFT_PLAYER_RANGE
        } else {
            RIGHT_MERGED_RANGE
        }
        val row = input.rowCrops.firstOrNull { it.rowIndex == rowIndex }?.bounds
        return localRect(input.cropWidth, range.start, range.endInclusive, row)
    }

    private fun killRect(input: MatchResultPositionOcrInput, rowIndex: Int, first: Boolean): MatchResultOcrRect {
        val range = if (input.position <= 5) killRange(input.cropWidth, first) else if (first) {
            RIGHT_MERGED_RANGE
        } else {
            RIGHT_KILL_RANGE
        }
        val row = input.rowCrops.firstOrNull { it.rowIndex == rowIndex }?.bounds
        return localRect(input.cropWidth, range.start, range.endInclusive, row)
    }

    private fun localRect(width: Int, left: Double, right: Double, row: OcrPixelCropRect?): MatchResultOcrRect {
        val top = row?.top?.toDouble() ?: 0.0
        val bottom = row?.bottom?.toDouble() ?: 1.0
        return MatchResultOcrRect(left * width, top, right * width, bottom)
    }

    private fun scaledRange(width: Int, range: ClosedRange<Double>): ClosedRange<Double> =
        width * range.start..width * range.endInclusive

    private fun playerRange(width: Int, first: Boolean): ClosedRange<Double> = if (first) {
        width * 0.08..width * 0.36
    } else {
        width * 0.58..width * 0.82
    }

    private fun killRange(width: Int, first: Boolean): ClosedRange<Double> = if (first) {
        width * 0.34..width * 0.56
    } else {
        width * 0.80..width.toDouble()
    }

    private companion object {
        val MERGED_ELIMINATION_MARKERS = listOf(
            "Eliminations",
            "Elimination",
            "Eliminatio",
            "Eliminati",
        )
        val RIGHT_LEFT_PLAYER_RANGE = 0.05..0.40
        val RIGHT_MERGED_RANGE = 0.40..0.81
        val RIGHT_KILL_RANGE = 0.81..1.0
        val RAW_LINE_ORDER = compareBy<RawOcrLine>({ it.geometry?.boundingBox?.top ?: Int.MAX_VALUE })
            .thenBy { it.geometry?.boundingBox?.left ?: Int.MAX_VALUE }
    }
}

object MatchResultPositionSemanticTextParser {
    // Longest supported marker first so "Eliminatiok..." consumes "Eliminatio".
    private val markerPattern = Regex(
        "^\\s*(?:(\\d+|[Oo])\\s*)?(Eliminations?|Eliminatio|Eliminati)(.*)$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): ParsedEliminationText {
        val raw = text.trim()
        val match = markerPattern.matchEntire(raw) ?: return ParsedEliminationText(
            kill = null,
            playerSuffix = null,
            markerMatched = false,
            rawText = raw,
        )
        val prefix = match.groupValues[1]
        val prefixType = when {
            prefix.equals("O", ignoreCase = true) -> MatchResultEliminationPrefixType.O_NORMALIZED
            prefix.isNotBlank() -> MatchResultEliminationPrefixType.EXPLICIT_NUMERIC
            else -> MatchResultEliminationPrefixType.EMPTY_PREFIX
        }
        return ParsedEliminationText(
            kill = when (prefixType) {
                MatchResultEliminationPrefixType.O_NORMALIZED -> 0
                MatchResultEliminationPrefixType.EXPLICIT_NUMERIC -> prefix.toIntOrNull()
                MatchResultEliminationPrefixType.EMPTY_PREFIX -> 0
            },
            playerSuffix = match.groupValues[3].trim().ifBlank { null },
            markerMatched = true,
            prefixType = prefixType,
            rawText = raw,
            markerType = match.groupValues[2].uppercase(),
        )
    }

    fun suffixAfterElimination(text: String): String = parse(text).playerSuffix.orEmpty()
}

private fun RawOcrLine.centerX(): Double = geometry?.boundingBox?.let { (it.left + it.right) / 2.0 } ?: -1.0
private fun RawOcrLine.centerY(): Double = geometry?.boundingBox?.let { (it.top + it.bottom) / 2.0 } ?: -1.0
private fun MatchResultPositionRowCrop.containsCenter(centerY: Double): Boolean = centerY >= bounds.top && centerY <= bounds.bottom
private fun isFirstPlayer(slot: Int): Boolean = slot == 1 || slot == 2
private fun slotRow(slot: Int): Int = if (isFirstPlayer(slot)) slot else slot - 2
private fun String.parseElimination(): ParsedEliminationText = MatchResultPositionSemanticTextParser.parse(this)
