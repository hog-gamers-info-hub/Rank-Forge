package com.hoggamers.rankforge.presentation.screen

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.domain.matching.ScoreboardRowPlayerEvidence
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluator
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import kotlin.math.abs
import kotlin.math.hypot

private const val LOWER_ROI_DIAGNOSTIC_TAG = "RF_MLKIT_LOWER_ROI"
private const val LOWER_MATCH_DIAGNOSTIC_TAG = "RF_MLKIT_LOWER_MATCH"

private enum class LowerVisualRow(val label: String) {
    A("A"),
    B("B"),
}

internal data class LowerVisualRowResolution(
    val row: String,
    val detectedPlacement: Int?,
    val decision: String,
    val emittedPosition: Int?,
)

internal fun resolveLowerVisualRow(
    row: String,
    detectedPlacementText: String,
): LowerVisualRowResolution {
    val detectedPlacement = detectedPlacementText.trim().toIntOrNull()
    val (decision, emittedPosition) = when {
        detectedPlacement == 11 -> "EMIT_POSITION_11" to 11
        detectedPlacement == 12 -> "EMIT_POSITION_12" to 12
        detectedPlacement != null && detectedPlacement <= 10 ->
            "IGNORED_UPPER_OWNS_POSITION" to null
        else -> "MANUAL_REVIEW_REQUIRED" to null
    }
    return LowerVisualRowResolution(
        row = row,
        detectedPlacement = detectedPlacement,
        decision = decision,
        emittedPosition = emittedPosition,
    )
}

/**
 * Temporary, diagnostic-only extraction for Screenshot 2 / MATCH_RESULT_LOWER.
 * It intentionally does not feed the production OCR, match, or persistence paths.
 */
object MlKitScreenshot2LowerRoiDiagnostic {
    fun dump(
        text: Text,
        cropWidth: Int,
        cropHeight: Int,
        candidateTeams: List<TeamCandidateRosterInput>,
    ) {
        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(LOWER_ROI_DIAGNOSTIC_TAG, "ERROR invalid crop size ${cropWidth}x${cropHeight}")
            return
        }

        val elements = collectElements(text)
        val symbols = collectSymbols(text)
        val anchors = detectPlacementAnchors(elements, cropWidth, cropHeight)
        val transform = fitLayoutTransform(anchors, cropWidth, cropHeight)

        Log.i(LOWER_ROI_DIAGNOSTIC_TAG, "===== SCREENSHOT_2_ROI_BEGIN =====")
        Log.i(
            LOWER_ROI_DIAGNOSTIC_TAG,
            "CROP size=${cropWidth}x${cropHeight} canonical=${CANONICAL_WIDTH.toInt()}x${CANONICAL_HEIGHT.toInt()}",
        )
        Log.i(
            LOWER_ROI_DIAGNOSTIC_TAG,
            "ANCHORS count=${anchors.size} values=" +
                anchors.sortedBy { it.placement }.joinToString {
                    "${it.visualRow.label}:${it.placement}@(${fmt(it.centerX)},${fmt(it.centerY)})"
                },
        )
        Log.i(
            LOWER_ROI_DIAGNOSTIC_TAG,
            "TRANSFORM xScale=${fmt(transform.x.scale)} xOffset=${fmt(transform.x.offset)} " +
                "yScale=${fmt(transform.y.scale)} yOffset=${fmt(transform.y.offset)} " +
                "maxAnchorResidualPx=${fmt(maxAnchorResidual(anchors, transform))}",
        )

        val results = applyZeroKillFallback(canonicalFields().map { field ->
            mapField(field, transform, symbols)
        })
        check(results.size == 18) {
            "Screenshot 2 lower ROI diagnostic must emit exactly 18 logical fields."
        }

        val visualRows = LowerVisualRow.entries.map { visualRow ->
            val row = results.filter { it.visualRow == visualRow }
            val placement = row.first { it.type == FieldType.PLACEMENT }
            val resolution = resolveLowerVisualRow(
                row = visualRow.label,
                detectedPlacementText = placement.resolvedText,
            )
            Log.i(
                LOWER_ROI_DIAGNOSTIC_TAG,
                "VISUAL_ROW|row=${resolution.row}|detectedPlacement=${resolution.detectedPlacement ?: ""}" +
                    "|decision=${resolution.decision}",
            )
            row.forEach { result ->
                Log.i(LOWER_ROI_DIAGNOSTIC_TAG, result.toLogLine())
            }
            EmittedLowerRow(resolution.emittedPosition, resolution.decision, row)
        }

        val emittedRows = visualRows.filter { it.position != null }
        emittedRows.forEach { emittedRow ->
            val placement = requireNotNull(emittedRow.position)
            val row = emittedRow.fields
            val players = (1..4).joinToString(" | ") { slot ->
                val player = row.first { it.type == FieldType.PLAYER && it.slot == slot }
                val kill = row.first { it.type == FieldType.KILL && it.slot == slot }
                "P$slot=${quoted(player.resolvedText)} K$slot=${quoted(kill.resolvedText)}"
            }
            Log.i(LOWER_ROI_DIAGNOSTIC_TAG, "ROW[$placement] $players")
        }

        dumpTeamMatching(emittedRows, candidateTeams)

        Log.i(LOWER_ROI_DIAGNOSTIC_TAG, "FIELD_COUNT=${results.size}")
        Log.i(LOWER_ROI_DIAGNOSTIC_TAG, "EMITTED_ROW_COUNT=${emittedRows.size}")
        Log.i(
            LOWER_ROI_DIAGNOSTIC_TAG,
            "IGNORED_ROW_COUNT=${visualRows.count { it.decision == "IGNORED_UPPER_OWNS_POSITION" }}",
        )
        Log.i(LOWER_ROI_DIAGNOSTIC_TAG, "===== SCREENSHOT_2_ROI_END =====")
    }

    internal fun canonicalFieldSpecsForTest(): List<LowerRoiFieldSpec> = canonicalFields().map { field ->
        LowerRoiFieldSpec(
            id = field.id,
            type = field.type.name,
            visualRow = field.visualRow.label,
            slot = field.slot,
            left = field.rect.left.toInt(),
            top = field.rect.top.toInt(),
            right = field.rect.right.toInt(),
            bottom = field.rect.bottom.toInt(),
        )
    }

    private fun applyZeroKillFallback(results: List<FieldResult>): List<FieldResult> {
        val playersByKey = results
            .filter { it.type == FieldType.PLAYER }
            .associateBy { it.visualRow to it.slot }

        return results.map { field ->
            if (field.type != FieldType.KILL || field.resolvedText.isNotBlank()) {
                field
            } else {
                val player = playersByKey[field.visualRow to field.slot]
                val fallback = lowerKillFallback(
                    killResolvedText = field.resolvedText,
                    playerResolvedText = player?.resolvedText.orEmpty(),
                )
                if (fallback.status == null) {
                    field
                } else {
                    field.copy(
                        resolvedText = fallback.resolvedText,
                        status = fallback.status,
                    )
                }
            }
        }
    }

    private fun dumpTeamMatching(
        emittedRows: List<EmittedLowerRow>,
        candidateTeams: List<TeamCandidateRosterInput>,
    ) {
        Log.i(LOWER_MATCH_DIAGNOSTIC_TAG, "===== SCREENSHOT_2_MATCH_BEGIN =====")

        val usableTeams = candidateTeams
            .filter { team -> team.rosterPlayerNames.any { !it.isNullOrBlank() } }
            .sortedBy { it.teamSlot }
        Log.i(
            LOWER_MATCH_DIAGNOSTIC_TAG,
            "ROSTER_CANDIDATES count=${usableTeams.size} slots=" +
                usableTeams.joinToString(prefix = "[", postfix = "]") { it.teamSlot.toString() },
        )

        if (usableTeams.isEmpty()) {
            Log.e(LOWER_MATCH_DIAGNOSTIC_TAG, "ERROR no populated tournament roster candidates")
        }

        val rowEvidence = emittedRows.mapIndexed { rowIndex, emittedRow ->
            val placement = requireNotNull(emittedRow.position)
            ScoreboardRowPlayerEvidence(
                rowIndex = rowIndex,
                expectedPlacementId = placement,
                detectedPlayerNames = emittedRow.fields
                    .filter {
                        it.type == FieldType.PLAYER && it.resolvedText.isNotBlank()
                    }
                    .sortedBy { it.slot }
                    .map { it.resolvedText },
            )
        }
        val evaluation = ScoreboardTeamIdentificationEvaluator.evaluate(rowEvidence, usableTeams)

        evaluation.rows.sortedBy { it.expectedPlacementId }.forEach { row ->
            val ranked = row.suggestions.suggestions.joinToString(prefix = "[", postfix = "]") { suggestion ->
                val score = suggestion.teamCandidateScore
                "#${suggestion.rank}:slot=${score.candidateTeamSlot}," +
                    "score=${score.confidenceScore},matches=${score.contributingMatchCount}"
            }
            val selectedScore = row.confidenceAssessment.selectedSuggestion?.teamCandidateScore
            Log.i(
                LOWER_MATCH_DIAGNOSTIC_TAG,
                "MATCH|position=${row.expectedPlacementId}" +
                    "|detected=${row.detectedPlayerNames.joinToString(prefix = "[", postfix = "]") { quoted(it) }}" +
                    "|suggestedTeamSlot=${row.suggestedTeamSlot ?: ""}" +
                    "|identifiedTeamSlot=${row.identifiedTeamSlot ?: ""}" +
                    "|confidenceTier=${row.confidenceAssessment.tier}" +
                    "|confidenceScore=${selectedScore?.confidenceScore ?: ""}" +
                    "|contributingMatches=${selectedScore?.contributingMatchCount ?: 0}" +
                    "|safety=${row.assignmentSafety.safetyStatus}" +
                    "|top3=$ranked",
            )
        }

        val identified = evaluation.rows.count { it.identifiedTeamSlot != null }
        Log.i(
            LOWER_MATCH_DIAGNOSTIC_TAG,
            "MATCH_COUNT=${evaluation.rows.size} IDENTIFIED_COUNT=$identified",
        )
        Log.i(LOWER_MATCH_DIAGNOSTIC_TAG, "===== SCREENSHOT_2_MATCH_END =====")
    }

    private fun mapField(
        field: CanonicalField,
        transform: LayoutTransform,
        symbols: List<SymbolObservation>,
    ): FieldResult {
        val mapped = transform.map(field.rect)
        val padding = when (field.type) {
            FieldType.PLACEMENT -> Padding(horizontal = 2.0, vertical = 3.0)
            FieldType.PLAYER -> Padding(horizontal = 0.0, vertical = 3.0)
            FieldType.KILL -> Padding(horizontal = 0.0, vertical = 3.0)
        }
        val selected = symbols
            .filter { symbol ->
                symbol.centerX >= mapped.left - padding.horizontal &&
                    symbol.centerX < mapped.right + padding.horizontal &&
                    symbol.centerY >= mapped.top - padding.vertical &&
                    symbol.centerY < mapped.bottom + padding.vertical
            }
            .sortedWith(compareBy<SymbolObservation> { it.centerX }.thenBy { it.centerY })

        return when (field.type) {
            FieldType.PLACEMENT -> {
                val numeric = selected.filter { it.text.singleOrNull()?.isDigit() == true }
                val ocr = numeric.joinToString(separator = "") { it.text }
                FieldResult(
                    fieldId = field.id,
                    type = field.type,
                    visualRow = field.visualRow,
                    slot = null,
                    mappedRect = mapped,
                    ocrText = ocr,
                    resolvedText = ocr,
                    status = when {
                        ocr.isBlank() -> "TEMPLATE_ONLY"
                        else -> "DIRECT_NUMERIC"
                    },
                    first = numeric.firstOrNull(),
                )
            }
            FieldType.KILL -> {
                // This filter is deliberately restricted to numeric symbols. It prevents
                // the nearby "Eliminations" label from becoming a kill value.
                val numeric = selected.filter {
                    it.text.singleOrNull()?.isDigit() == true || it.text == "O" || it.text == "o"
                }
                val ocr = numeric.joinToString(separator = "") { it.text }
                val normalized = normalizeLowerKillOcr(ocr)
                FieldResult(
                    fieldId = field.id,
                    type = field.type,
                    visualRow = field.visualRow,
                    slot = field.slot,
                    mappedRect = mapped,
                    ocrText = ocr,
                    resolvedText = normalized.resolvedText,
                    status = normalized.status,
                    first = numeric.firstOrNull(),
                )
            }
            FieldType.PLAYER -> {
                val rendered = renderPlayer(selected)
                FieldResult(
                    fieldId = field.id,
                    type = field.type,
                    visualRow = field.visualRow,
                    slot = field.slot,
                    mappedRect = mapped,
                    ocrText = rendered,
                    resolvedText = rendered,
                    status = if (rendered.isBlank()) "EMPTY" else "DIRECT",
                    first = selected.firstOrNull(),
                )
            }
        }
    }

    private fun renderPlayer(symbols: List<SymbolObservation>): String {
        if (symbols.isEmpty()) return ""
        val ordered = symbols.sortedWith(compareBy<SymbolObservation> { it.centerX }.thenBy { it.centerY })
        return buildString {
            ordered.forEachIndexed { index, current ->
                if (index > 0) {
                    val previous = ordered[index - 1]
                    val sameElement = current.blockIndex == previous.blockIndex &&
                        current.lineIndex == previous.lineIndex &&
                        current.elementIndex == previous.elementIndex
                    if (!sameElement) append(' ')
                }
                append(current.text)
            }
        }.trim()
    }

    private fun collectElements(text: Text): List<ElementObservation> = buildList {
        text.textBlocks.forEachIndexed { blockIndex, block ->
            block.lines.forEachIndexed { lineIndex, line ->
                line.elements.forEachIndexed { elementIndex, element ->
                    val box = element.boundingBox ?: return@forEachIndexed
                    add(ElementObservation(element.text, box, blockIndex, lineIndex, elementIndex))
                }
            }
        }
    }

    private fun collectSymbols(text: Text): List<SymbolObservation> = buildList {
        text.textBlocks.forEachIndexed { blockIndex, block ->
            block.lines.forEachIndexed { lineIndex, line ->
                line.elements.forEachIndexed { elementIndex, element ->
                    val elementBox = element.boundingBox ?: return@forEachIndexed
                    element.symbols.forEachIndexed { symbolIndex, symbol ->
                        val box = symbol.boundingBox ?: return@forEachIndexed
                        add(
                            SymbolObservation(
                                text = symbol.text,
                                box = box,
                                blockIndex = blockIndex,
                                lineIndex = lineIndex,
                                elementIndex = elementIndex,
                                symbolIndex = symbolIndex,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun detectPlacementAnchors(
        elements: List<ElementObservation>,
        cropWidth: Int,
        cropHeight: Int,
    ): List<PlacementAnchor> = elements.mapNotNull { element ->
        val placement = element.text.trim().trim('.', ':').toIntOrNull()
            ?.takeIf { it in 1..12 }
            ?: return@mapNotNull null
        if (element.centerX < cropWidth * 0.45 || element.centerX > cropWidth * 0.80) {
            return@mapNotNull null
        }
        val visualRow = LowerVisualRow.entries.minByOrNull { row ->
            abs(element.centerY - expectedAnchorCenterY(row, cropHeight))
        } ?: return@mapNotNull null
        val residual = abs(element.centerY - expectedAnchorCenterY(visualRow, cropHeight))
        if (residual > cropHeight * 0.16) return@mapNotNull null
        PlacementAnchor(visualRow, placement, element.centerX, element.centerY)
    }.groupBy { it.visualRow }.mapNotNull { (_, candidates) ->
        candidates.minByOrNull { candidate ->
            abs(candidate.centerY - expectedAnchorCenterY(candidate.visualRow, cropHeight))
        }
    }

    private fun expectedAnchorCenterY(visualRow: LowerVisualRow, cropHeight: Int): Double =
        cropHeight.toDouble() / CANONICAL_HEIGHT * when (visualRow) {
            LowerVisualRow.A -> 330.0
            LowerVisualRow.B -> 411.0
        }

    private fun fitLayoutTransform(
        anchors: List<PlacementAnchor>,
        cropWidth: Int,
        cropHeight: Int,
    ): LayoutTransform {
        val fallbackX = cropWidth.toDouble() / CANONICAL_WIDTH
        val fallbackY = cropHeight.toDouble() / CANONICAL_HEIGHT
        val x = fitAxis(
            canonical = anchors.map { canonicalPlacement(it.visualRow).centerX },
            observed = anchors.map { it.centerX },
            fallbackScale = fallbackX,
        )
        val y = fitAxis(
            canonical = anchors.map { canonicalPlacement(it.visualRow).centerY },
            observed = anchors.map { it.centerY },
            fallbackScale = fallbackY,
        )
        return LayoutTransform(x, y)
    }

    private fun fitAxis(
        canonical: List<Double>,
        observed: List<Double>,
        fallbackScale: Double,
    ): AxisTransform {
        if (canonical.isEmpty()) return AxisTransform(fallbackScale, 0.0)
        val canonicalMean = canonical.average()
        val observedMean = observed.average()
        val denominator = canonical.sumOf { value ->
            val delta = value - canonicalMean
            delta * delta
        }
        if (denominator < 1e-6) {
            return AxisTransform(
                fallbackScale,
                observed.zip(canonical).map { (observedValue, canonicalValue) ->
                    observedValue - fallbackScale * canonicalValue
                }.average(),
            )
        }
        val numerator = canonical.zip(observed).sumOf { (canonicalValue, observedValue) ->
            (canonicalValue - canonicalMean) * (observedValue - observedMean)
        }
        val scale = numerator / denominator
        return AxisTransform(scale, observedMean - scale * canonicalMean)
    }

    private fun maxAnchorResidual(anchors: List<PlacementAnchor>, transform: LayoutTransform): Double =
        anchors.maxOfOrNull { anchor ->
            val canonical = canonicalPlacement(anchor.visualRow)
            val mapped = transform.mapPoint(canonical.centerX, canonical.centerY)
            hypot(mapped.first - anchor.centerX, mapped.second - anchor.centerY)
        } ?: 0.0

    private fun canonicalPlacement(visualRow: LowerVisualRow): CanonicalRect =
        canonicalFields().first { it.type == FieldType.PLACEMENT && it.visualRow == visualRow }.rect

    private fun canonicalFields(): List<CanonicalField> = buildList {
        val rows = mapOf(
            LowerVisualRow.A to listOf(297.0..326.0, 331.0..360.0, 297.0..326.0, 331.0..360.0),
            LowerVisualRow.B to listOf(378.0..407.0, 412.0..441.0, 378.0..407.0, 412.0..441.0),
        )
        val playerColumns = listOf(
            725.0..869.0,
            725.0..869.0,
            959.0..1074.0,
            959.0..1074.0,
        )
        val killColumns = listOf(
            865.0..884.0,
            865.0..884.0,
            1074.0..1090.0,
            1074.0..1090.0,
        )
        val placements = mapOf(
            LowerVisualRow.A to CanonicalRect(675.0, 297.0, 710.0, 363.0),
            LowerVisualRow.B to CanonicalRect(675.0, 377.0, 710.0, 445.0),
        )
        LowerVisualRow.entries.forEach { visualRow ->
            add(
                CanonicalField(
                    id = "LOWER_ROW_${visualRow.label}_PLACEMENT",
                    type = FieldType.PLACEMENT,
                    visualRow = visualRow,
                    slot = null,
                    rect = placements.getValue(visualRow),
                ),
            )
            (1..4).forEach { slot ->
                val player = playerColumns[slot - 1]
                val kill = killColumns[slot - 1]
                val vertical = rows.getValue(visualRow)[slot - 1]
                add(
                    CanonicalField(
                        id = "LOWER_ROW_${visualRow.label}_PLAYER_$slot",
                        type = FieldType.PLAYER,
                        visualRow = visualRow,
                        slot = slot,
                        rect = CanonicalRect(player.start, vertical.start, player.endInclusive, vertical.endInclusive),
                    ),
                )
                add(
                    CanonicalField(
                        id = "LOWER_ROW_${visualRow.label}_KILL_$slot",
                        type = FieldType.KILL,
                        visualRow = visualRow,
                        slot = slot,
                        rect = CanonicalRect(kill.start, vertical.start, kill.endInclusive, vertical.endInclusive),
                    ),
                )
            }
        }
    }

    private fun FieldResult.toLogLine(): String = buildString {
        append("FIELD|")
        append(fieldId)
        append("|type=").append(type)
        append("|row=").append(visualRow.label)
        slot?.let { append("|slot=").append(it) }
        append("|roi=(").append(mappedRect.left.toInt()).append(',').append(mappedRect.top.toInt())
        append(")-(").append(mappedRect.right.toInt()).append(',').append(mappedRect.bottom.toInt()).append(')')
        append("|ocr=").append(quoted(ocrText))
        append("|resolved=").append(quoted(resolvedText))
        append("|status=").append(status)
        append("|firstChar=").append(quoted(first?.text.orEmpty()))
        append("|firstCharBox=").append(
            first?.box?.let { box -> "(${box.left},${box.top})-(${box.right},${box.bottom})" } ?: "null",
        )
    }

    private fun quoted(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\""

    private fun fmt(value: Double): String = "%.4f".format(java.util.Locale.US, value)

    private const val CANONICAL_WIDTH = 1156.0
    private const val CANONICAL_HEIGHT = 452.0

    private enum class FieldType { PLACEMENT, PLAYER, KILL }

    private data class CanonicalField(
        val id: String,
        val type: FieldType,
        val visualRow: LowerVisualRow,
        val slot: Int?,
        val rect: CanonicalRect,
    )

    private data class CanonicalRect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    ) {
        val centerX: Double get() = (left + right) / 2.0
        val centerY: Double get() = (top + bottom) / 2.0
    }

    private data class MappedRect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    )

    private data class Padding(val horizontal: Double, val vertical: Double)

    private data class ElementObservation(
        val text: String,
        val box: Rect,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
    ) {
        val centerX: Double get() = (box.left + box.right) / 2.0
        val centerY: Double get() = (box.top + box.bottom) / 2.0
    }

    private data class SymbolObservation(
        val text: String,
        val box: Rect,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
        val symbolIndex: Int,
    ) {
        val centerX: Double get() = (box.left + box.right) / 2.0
        val centerY: Double get() = (box.top + box.bottom) / 2.0
    }

    private data class PlacementAnchor(
        val visualRow: LowerVisualRow,
        val placement: Int,
        val centerX: Double,
        val centerY: Double,
    )

    private data class AxisTransform(val scale: Double, val offset: Double) {
        fun map(value: Double): Double = scale * value + offset
    }

    private data class LayoutTransform(val x: AxisTransform, val y: AxisTransform) {
        fun map(rect: CanonicalRect): MappedRect = MappedRect(
            left = x.map(rect.left),
            top = y.map(rect.top),
            right = x.map(rect.right),
            bottom = y.map(rect.bottom),
        )

        fun mapPoint(xValue: Double, yValue: Double): Pair<Double, Double> =
            x.map(xValue) to y.map(yValue)
    }

    private data class FieldResult(
        val fieldId: String,
        val type: FieldType,
        val visualRow: LowerVisualRow,
        val slot: Int?,
        val mappedRect: MappedRect,
        val ocrText: String,
        val resolvedText: String,
        val status: String,
        val first: SymbolObservation?,
    )

    private data class EmittedLowerRow(
        val position: Int?,
        val decision: String,
        val fields: List<FieldResult>,
    )
}

internal data class LowerRoiFieldSpec(
    val id: String,
    val type: String,
    val visualRow: String,
    val slot: Int?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class LowerKillFallback(
    val resolvedText: String,
    val status: String?,
)

internal fun lowerKillFallback(
    killResolvedText: String,
    playerResolvedText: String,
): LowerKillFallback = if (
    killResolvedText.isBlank() && playerResolvedText.isNotBlank()
) {
    LowerKillFallback(
        resolvedText = "0",
        status = "ZERO_INFERRED_FROM_PLAYER_PRESENT",
    )
} else {
    LowerKillFallback(
        resolvedText = killResolvedText,
        status = null,
    )
}

internal data class LowerKillNormalization(
    val resolvedText: String,
    val status: String,
)

internal fun normalizeLowerKillOcr(ocrText: String): LowerKillNormalization {
    val normalized = ocrText.map { char -> if (char == 'O' || char == 'o') '0' else char }
        .joinToString(separator = "")
    return LowerKillNormalization(
        resolvedText = normalized,
        status = when {
            normalized.isBlank() -> "EMPTY"
            ocrText.any { it == 'O' || it == 'o' } -> "O_NORMALIZED_TO_0"
            else -> "DIRECT_NUMERIC"
        },
    )
}
