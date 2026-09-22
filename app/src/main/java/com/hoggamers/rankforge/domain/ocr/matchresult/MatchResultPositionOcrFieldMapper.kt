package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
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

                add(
                    killField(
                        input = input,
                        visualRow = visualRow,
                        slot = slot,
                        rowIndex = rowIndex,
                        first = isFirstPlayer,
                        parsed = semantics.elimination,
                        verification = input.killVerifications[slot],
                    ),
                )
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
                it.centerX() in MatchResultKillFieldLayout.horizontalRange(input.position, input.cropWidth, first) &&
                    it.text.parseElimination().markerMatched
            }?.text?.let(MatchResultPositionSemanticTextParser::parse)
            return SlotSemantic(
                playerText = playerLines.sortedForPlayerText().joinToString(" ") { it.text.trim() }.trim()
                    .ifBlank { if (!first) elimination?.playerSuffix.orEmpty() else "" },
                elimination = elimination,
            )
        }

        val middleLines = rowLines.filter {
            it.centerX() in scaledRange(input.cropWidth, RIGHT_MERGED_RANGE)
        }
        val middleElimination = middleLines
            .mapNotNull(::parseSharedMiddleElimination)
            .firstOrNull()
        if (first) {
            val playerLines = rowLines.filter {
                it.centerX() in scaledRange(input.cropWidth, RIGHT_LEFT_PLAYER_RANGE) &&
                    !it.text.parseElimination().markerMatched
            }
            return SlotSemantic(
                playerText = playerLines.sortedForPlayerText().joinToString(" ") { it.text.trim() }.trim(),
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
        val exactPlayerText = if (
            input.position in 6..12 && !playerBoundary.decision.boundaryAccepted
        ) {
            stripLeadingMergedEliminationPrefix(rawPlayerText)
        } else {
            rawPlayerText
        }
        val playerText = if (
            input.position in 6..12 &&
            !playerBoundary.decision.boundaryAccepted &&
            exactPlayerText == rawPlayerText
        ) {
            val sameLinePlayerText = degradedMergedPlayerTextOrNull(middleLines) ?: exactPlayerText
            if (sameLinePlayerText != exactPlayerText) {
                sameLinePlayerText
            } else {
                recoverSplitDegradedRightPlayerOrNull(middleLines) ?: sameLinePlayerText
            }
        } else {
            exactPlayerText
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

    private fun parseSharedMiddleElimination(line: RawOcrLine): ParsedEliminationText? {
        val parsed = line.text.parseElimination()
        if (
            parsed.markerMatched &&
            (
                parsed.prefixType == MatchResultEliminationPrefixType.O_NORMALIZED && parsed.kill == 0 ||
                    parsed.prefixType == MatchResultEliminationPrefixType.EXPLICIT_NUMERIC && parsed.kill != null
                )
        ) {
            return parsed
        }

        val anchor = MatchResultEliminationAnchorText.find(line.text) ?: return null
        if (anchor.prefixType == MatchResultEliminationPrefixType.EMPTY_PREFIX || anchor.kill == null) {
            return null
        }
        return ParsedEliminationText(
            kill = anchor.kill,
            playerSuffix = null,
            markerMatched = true,
            prefixType = anchor.prefixType,
            rawText = anchor.rawText,
            markerType = "ELIMINAT",
        )
    }

    private fun degradedMergedPlayerTextOrNull(middleLines: List<RawOcrLine>): String? {
        val degradedBoundary = middleLines.firstNotNullOfOrNull { line ->
            if (line.text.parseElimination().markerMatched) {
                return@firstNotNullOfOrNull null
            }
            val playerSuffix = MatchResultEliminationAnchorText
                .playerSuffixAfterDegradedLeadingAnchorOrNull(line.text)
                ?: return@firstNotNullOfOrNull null
            line to playerSuffix
        } ?: return null

        return buildList {
            add(degradedBoundary.second)
            middleLines
                .filterNot { it === degradedBoundary.first }
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .forEach(::add)
        }.joinToString(" ").trim().takeIf { it.isNotEmpty() }
    }

    private fun recoverSplitDegradedRightPlayerOrNull(middleLines: List<RawOcrLine>): String? {
        val anchor = middleLines.firstNotNullOfOrNull { line ->
            val bounds = line.geometry?.boundingBox ?: return@firstNotNullOfOrNull null
            if (line.text.parseElimination().markerMatched) {
                return@firstNotNullOfOrNull null
            }
            if (MatchResultEliminationAnchorText.find(line.text) == null) {
                return@firstNotNullOfOrNull null
            }
            if (MatchResultEliminationAnchorText
                    .playerSuffixAfterDegradedLeadingAnchorOrNull(line.text) != null
            ) {
                return@firstNotNullOfOrNull null
            }
            line to bounds
        } ?: return null

        val (anchorLine, anchorBounds) = anchor
        val playerFragments = middleLines
            .asSequence()
            .filterNot { it === anchorLine }
            .mapNotNull { line ->
                line.geometry?.boundingBox?.let { bounds -> line to bounds }
            }
            .filter { (_, bounds) -> bounds.left >= anchorBounds.right }
            .filter { (line, _) -> !line.text.parseElimination().markerMatched }
            .sortedWith(compareBy<Pair<RawOcrLine, RawOcrBoundingBox>> { it.second.left }
                .thenBy { it.second.top })
            .map { (line, _) -> line.text.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        return playerFragments.joinToString(" ").trim().takeIf { it.isNotEmpty() }
    }

    private fun killField(
        input: MatchResultPositionOcrInput,
        visualRow: MatchResultOcrVisualRow?,
        slot: Int,
        rowIndex: Int,
        first: Boolean,
        parsed: ParsedEliminationText?,
        verification: MatchResultNumericVerification?,
    ): MatchResultOcrField {
        val ppResolved = parsed?.markerMatched == true
        val ppRawText = parsed?.rawText.orEmpty()
        val ppKill = parsed?.kill ?: 0
        val ppPrefixType = parsed?.prefixType
        val fallback = verification as? MatchResultNumericVerification.Verified
        return field(
            id = "KILL_${input.position}_$slot",
            type = MatchResultOcrFieldType.KILL,
            position = input.position,
            visualRow = visualRow,
            slot = slot,
            rect = killRect(input, rowIndex, first),
            ocrText = when {
                ppResolved -> ppRawText
                fallback != null -> fallback.candidates
                    .firstOrNull { it.value == fallback.value }
                    ?.rawText
                    .orEmpty()
                else -> ""
            },
            resolvedText = when {
                ppResolved -> ppKill.toString()
                fallback != null -> fallback.value.toString()
                else -> ""
            },
            status = when {
                ppResolved && ppPrefixType == MatchResultEliminationPrefixType.O_NORMALIZED ->
                    MatchResultOcrFieldStatus.O_NORMALIZED_TO_0
                ppResolved -> MatchResultOcrFieldStatus.DIRECT_NUMERIC
                fallback != null -> MatchResultOcrFieldStatus.MLKIT_FALLBACK
                else -> MatchResultOcrFieldStatus.EMPTY
            },
        )
    }

    private fun List<RawOcrLine>.sortedForPlayerText(): List<RawOcrLine> =
        sortedWith(
            compareBy<RawOcrLine> {
                it.geometry?.boundingBox?.left ?: Int.MAX_VALUE
            }.thenBy {
                it.geometry?.boundingBox?.top ?: Int.MAX_VALUE
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
        val row = input.rowCrops.firstOrNull { it.rowIndex == rowIndex }?.bounds
        return MatchResultKillFieldLayout.bounds(
            position = input.position,
            cropWidth = input.cropWidth,
            rowBounds = row,
            firstPlayerColumn = first,
        )
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
    private val truncatedStrongPrefixPattern = Regex(
        "^(\\d+|[Oo])\\s*Eliminat$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): ParsedEliminationText {
        val raw = text.trim()
        val match = markerPattern.matchEntire(raw)
        if (match == null) {
            val truncatedMatch = truncatedStrongPrefixPattern.matchEntire(raw)
            if (truncatedMatch != null) {
                val prefix = truncatedMatch.groupValues[1]
                val prefixType = if (prefix.equals("O", ignoreCase = true)) {
                    MatchResultEliminationPrefixType.O_NORMALIZED
                } else {
                    MatchResultEliminationPrefixType.EXPLICIT_NUMERIC
                }
                return ParsedEliminationText(
                    kill = if (prefixType == MatchResultEliminationPrefixType.O_NORMALIZED) {
                        0
                    } else {
                        prefix.toIntOrNull()
                    },
                    playerSuffix = null,
                    markerMatched = true,
                    prefixType = prefixType,
                    rawText = raw,
                    markerType = "ELIMINAT",
                )
            }
            return ParsedEliminationText(
                kill = null,
                playerSuffix = null,
                markerMatched = false,
                rawText = raw,
            )
        }
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
