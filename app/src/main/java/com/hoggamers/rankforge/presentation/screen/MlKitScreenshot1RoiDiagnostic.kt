package com.hoggamers.rankforge.presentation.screen

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.domain.matching.ScoreboardRowPlayerEvidence
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluator
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import kotlin.math.abs

private const val ROI_DIAGNOSTIC_TAG = "RF_MLKIT_ROI"
private const val MATCH_DIAGNOSTIC_TAG = "RF_MLKIT_MATCH"

object MlKitScreenshot1RoiDiagnostic {
    fun dump(
        text: Text,
        cropWidth: Int,
        cropHeight: Int,
        candidateTeams: List<TeamCandidateRosterInput>,
    ) {
        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(ROI_DIAGNOSTIC_TAG, "ERROR invalid crop size ${cropWidth}x${cropHeight}")
            return
        }

        val elementObservations = collectElements(text)
        val symbolObservations = collectSymbols(text)
        val anchors = detectPlacementAnchors(
            elements = elementObservations,
            cropWidth = cropWidth,
        )

        val transform = fitLayoutTransform(
            anchors = anchors,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
        )
        if (transform == null) {
            Log.e(
                ROI_DIAGNOSTIC_TAG,
                "ERROR could not register canonical layout; anchors=" +
                    anchors.joinToString { "${it.placement}@(${it.centerX},${it.centerY})" },
            )
            return
        }

        Log.i(ROI_DIAGNOSTIC_TAG, "===== SCREENSHOT_1_ROI_BEGIN =====")
        Log.i(
            ROI_DIAGNOSTIC_TAG,
            "CROP size=${cropWidth}x${cropHeight} canonical=${CANONICAL_WIDTH}x${CANONICAL_HEIGHT}",
        )
        Log.i(
            ROI_DIAGNOSTIC_TAG,
            "ANCHORS count=${anchors.size} values=" +
                anchors.sortedBy { it.placement }.joinToString {
                    "${it.placement}@(${fmt(it.centerX)},${fmt(it.centerY)})"
                },
        )
        Log.i(
            ROI_DIAGNOSTIC_TAG,
            "TRANSFORM xScale=${fmt(transform.x.scale)} xOffset=${fmt(transform.x.offset)} " +
                "yScale=${fmt(transform.y.scale)} yOffset=${fmt(transform.y.offset)} " +
                "maxAnchorResidualPx=${fmt(maxAnchorResidual(anchors, transform))}",
        )

        val results = canonicalFields().map { field ->
            mapField(
                field = field,
                transform = transform,
                symbols = symbolObservations,
            )
        }

        check(results.size == 90) {
            "Screenshot 1 ROI diagnostic must emit exactly 90 logical fields."
        }

        results.forEach { result ->
            Log.i(ROI_DIAGNOSTIC_TAG, result.toLogLine())
        }

        (1..10).forEach { placement ->
            val row = results.filter { it.position == placement }
            val players = (1..4).joinToString(" | ") { slot ->
                val player = row.first { it.type == FieldType.PLAYER && it.slot == slot }
                val kill = row.first { it.type == FieldType.KILL && it.slot == slot }
                "P$slot=${quoted(player.resolvedText)} K$slot=${quoted(kill.resolvedText)}"
            }
            Log.i(ROI_DIAGNOSTIC_TAG, "ROW[$placement] $players")
        }

        dumpTeamMatching(
            results = results,
            candidateTeams = candidateTeams,
        )

        Log.i(ROI_DIAGNOSTIC_TAG, "FIELD_COUNT=${results.size}")
        Log.i(ROI_DIAGNOSTIC_TAG, "===== SCREENSHOT_1_ROI_END =====")
    }

    private fun dumpTeamMatching(
        results: List<FieldResult>,
        candidateTeams: List<TeamCandidateRosterInput>,
    ) {
        Log.i(MATCH_DIAGNOSTIC_TAG, "===== SCREENSHOT_1_MATCH_BEGIN =====")

        val usableTeams = candidateTeams
            .filter { team ->
                team.rosterPlayerNames.any { !it.isNullOrBlank() }
            }
            .sortedBy { it.teamSlot }

        Log.i(
            MATCH_DIAGNOSTIC_TAG,
            "ROSTER_CANDIDATES count=${usableTeams.size} slots=" +
                usableTeams.joinToString(prefix = "[", postfix = "]") { it.teamSlot.toString() },
        )

        if (usableTeams.isEmpty()) {
            Log.e(MATCH_DIAGNOSTIC_TAG, "ERROR no populated tournament roster candidates")
            Log.i(MATCH_DIAGNOSTIC_TAG, "===== SCREENSHOT_1_MATCH_END =====")
            return
        }

        val rowEvidence = (1..10).map { placement ->
            val detectedNames = results
                .filter {
                    it.position == placement &&
                        it.type == FieldType.PLAYER &&
                        it.resolvedText.isNotBlank()
                }
                .sortedBy { it.slot }
                .map { it.resolvedText }

            ScoreboardRowPlayerEvidence(
                rowIndex = placement - 1,
                expectedPlacementId = placement,
                detectedPlayerNames = detectedNames,
            )
        }

        val evaluation = ScoreboardTeamIdentificationEvaluator.evaluate(
            rowEvidence = rowEvidence,
            candidateTeams = usableTeams,
        )

        evaluation.rows
            .sortedBy { it.expectedPlacementId }
            .forEach { row ->
                val ranked = row.suggestions.suggestions.joinToString(
                    prefix = "[",
                    postfix = "]",
                ) { suggestion ->
                    val score = suggestion.teamCandidateScore
                    "#${suggestion.rank}:slot=${score.candidateTeamSlot}," +
                        "score=${score.confidenceScore}," +
                        "matches=${score.contributingMatchCount}"
                }
                val selectedScore = row.confidenceAssessment.selectedSuggestion
                    ?.teamCandidateScore

                Log.i(
                    MATCH_DIAGNOSTIC_TAG,
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
        val safe = evaluation.rows.count {
            it.assignmentSafety.safetyStatus.name == "SAFE_AUTOMATIC_ASSIGNMENT"
        }
        Log.i(
            MATCH_DIAGNOSTIC_TAG,
            "MATCH_COUNT=${evaluation.rows.size} IDENTIFIED_COUNT=$identified SAFE_AUTOMATIC_COUNT=$safe",
        )
        Log.i(MATCH_DIAGNOSTIC_TAG, "===== SCREENSHOT_1_MATCH_END =====")
    }

    private fun mapField(
        field: CanonicalField,
        transform: LayoutTransform,
        symbols: List<SymbolObservation>,
    ): FieldResult {
        val mapped = transform.map(field.rect)
        val pad = when (field.type) {
            FieldType.PLACEMENT -> Padding(horizontal = 3.0, vertical = 3.0)
            FieldType.PLAYER -> Padding(horizontal = 0.0, vertical = 3.0)
            // Temporary diagnostic tolerance. The first canonical comparison showed
            // kill glyphs can sit several pixels outside the candidate tight digit ROI.
            FieldType.KILL -> Padding(horizontal = 12.0, vertical = 3.0)
        }

        val selected = symbols
            .filter { symbol ->
                symbol.centerX >= mapped.left - pad.horizontal &&
                    symbol.centerX < mapped.right + pad.horizontal &&
                    symbol.centerY >= mapped.top - pad.vertical &&
                    symbol.centerY < mapped.bottom + pad.vertical
            }
            .sortedWith(
                compareBy<SymbolObservation> { it.centerX }
                    .thenBy { it.centerY },
            )

        return when (field.type) {
            FieldType.PLACEMENT -> {
                val ocrSymbols = selected.filter { it.text.singleOrNull()?.isDigit() == true }
                val ocr = ocrSymbols.joinToString(separator = "") { it.text }
                val expected = field.position.toString()
                FieldResult(
                    fieldId = field.id,
                    type = field.type,
                    position = field.position,
                    slot = null,
                    mappedRect = mapped,
                    ocrText = ocr,
                    resolvedText = expected,
                    status = when {
                        ocr.isBlank() -> "TEMPLATE_ONLY"
                        ocr == expected -> "OCR_MATCH"
                        else -> "OCR_MISMATCH"
                    },
                    first = ocrSymbols.firstOrNull(),
                )
            }

            FieldType.KILL -> {
                val killSymbols = selected.filter { symbol ->
                    symbol.text == "O" ||
                        symbol.text == "o" ||
                        symbol.text.singleOrNull()?.isDigit() == true
                }
                val ocr = killSymbols.joinToString(separator = "") { it.text }
                val normalized = ocr.map { char ->
                    if (char == 'O' || char == 'o') '0' else char
                }.joinToString(separator = "")
                FieldResult(
                    fieldId = field.id,
                    type = field.type,
                    position = field.position,
                    slot = field.slot,
                    mappedRect = mapped,
                    ocrText = ocr,
                    resolvedText = normalized,
                    status = when {
                        normalized.isBlank() -> "EMPTY"
                        ocr.any { it == 'O' || it == 'o' } -> "O_NORMALIZED_TO_0"
                        else -> "DIRECT_NUMERIC"
                    },
                    first = killSymbols.firstOrNull(),
                )
            }

            FieldType.PLAYER -> {
                val rendered = renderPlayer(selected)
                val crossesBoundary = selected.any { symbol ->
                    symbol.elementBox.left < mapped.left ||
                        symbol.elementBox.right > mapped.right
                }
                FieldResult(
                    fieldId = field.id,
                    type = field.type,
                    position = field.position,
                    slot = field.slot,
                    mappedRect = mapped,
                    ocrText = rendered,
                    resolvedText = rendered,
                    status = when {
                        rendered.isBlank() -> "EMPTY"
                        crossesBoundary -> "EDGE_SPILL_REVIEW"
                        else -> "DIRECT"
                    },
                    first = selected.firstOrNull(),
                )
            }
        }
    }

    private fun renderPlayer(symbols: List<SymbolObservation>): String {
        if (symbols.isEmpty()) return ""

        val ordered = symbols.sortedWith(
            compareBy<SymbolObservation> { it.centerX }
                .thenBy { it.centerY },
        )
        val result = StringBuilder()

        ordered.forEachIndexed { index, current ->
            if (index > 0) {
                val previous = ordered[index - 1]
                val sameElement =
                    current.blockIndex == previous.blockIndex &&
                        current.lineIndex == previous.lineIndex &&
                        current.elementIndex == previous.elementIndex
                if (!sameElement) {
                    result.append(' ')
                }
            }
            result.append(current.text)
        }

        return result.toString().trim()
    }

    private fun collectElements(text: Text): List<ElementObservation> =
        buildList {
            text.textBlocks.forEachIndexed { blockIndex, block ->
                block.lines.forEachIndexed { lineIndex, line ->
                    line.elements.forEachIndexed { elementIndex, element ->
                        val box = element.boundingBox ?: return@forEachIndexed
                        add(
                            ElementObservation(
                                text = element.text,
                                box = box,
                                blockIndex = blockIndex,
                                lineIndex = lineIndex,
                                elementIndex = elementIndex,
                            ),
                        )
                    }
                }
            }
        }

    private fun collectSymbols(text: Text): List<SymbolObservation> =
        buildList {
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
                                    elementBox = elementBox,
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
    ): List<PlacementAnchor> {
        val grouped = elements.mapNotNull { element ->
            val placement = element.text.trim().toIntOrNull() ?: return@mapNotNull null
            if (placement !in 1..10) return@mapNotNull null

            val centerX = element.centerX
            val plausibleBand = if (placement <= 5) {
                centerX < cropWidth * 0.12
            } else {
                centerX >= cropWidth * 0.50 && centerX < cropWidth * 0.68
            }
            if (!plausibleBand) return@mapNotNull null

            PlacementAnchor(
                placement = placement,
                centerX = centerX,
                centerY = element.centerY,
            )
        }.groupBy { it.placement }

        return grouped.mapNotNull { (_, candidates) ->
            candidates.maxByOrNull { candidate ->
                canonicalPlacement(candidate.placement)?.let { expected ->
                    // Prefer the candidate whose vertical order is most compatible with
                    // the canonical row after simple crop-height scaling.
                    val roughExpectedY = expected.centerY * 1.0
                    -abs(candidate.centerY - roughExpectedY)
                } ?: Double.NEGATIVE_INFINITY
            }
        }
    }

    private fun fitLayoutTransform(
        anchors: List<PlacementAnchor>,
        cropWidth: Int,
        cropHeight: Int,
    ): LayoutTransform? {
        if (anchors.size < 2) return null

        val pairs = anchors.mapNotNull { observed ->
            canonicalPlacement(observed.placement)?.let { canonical ->
                AxisPair(
                    canonicalX = canonical.centerX,
                    observedX = observed.centerX,
                    canonicalY = canonical.centerY,
                    observedY = observed.centerY,
                )
            }
        }
        if (pairs.size < 2) return null

        val x = fitAxis(
            canonical = pairs.map { it.canonicalX },
            observed = pairs.map { it.observedX },
            fallbackScale = cropWidth.toDouble() / CANONICAL_WIDTH,
        )
        val y = fitAxis(
            canonical = pairs.map { it.canonicalY },
            observed = pairs.map { it.observedY },
            fallbackScale = cropHeight.toDouble() / CANONICAL_HEIGHT,
        )
        return LayoutTransform(x = x, y = y)
    }

    private fun fitAxis(
        canonical: List<Double>,
        observed: List<Double>,
        fallbackScale: Double,
    ): AxisTransform {
        val canonicalMean = canonical.average()
        val observedMean = observed.average()
        val denominator = canonical.sumOf { value ->
            val delta = value - canonicalMean
            delta * delta
        }

        if (denominator < 1e-6) {
            val offset = observed.zip(canonical).map { (o, c) ->
                o - fallbackScale * c
            }.average()
            return AxisTransform(scale = fallbackScale, offset = offset)
        }

        val numerator = canonical.zip(observed).sumOf { (c, o) ->
            (c - canonicalMean) * (o - observedMean)
        }
        val scale = numerator / denominator
        val offset = observedMean - scale * canonicalMean
        return AxisTransform(scale = scale, offset = offset)
    }

    private fun maxAnchorResidual(
        anchors: List<PlacementAnchor>,
        transform: LayoutTransform,
    ): Double =
        anchors.maxOfOrNull { anchor ->
            val canonical = canonicalPlacement(anchor.placement) ?: return@maxOfOrNull 0.0
            val mapped = transform.mapPoint(canonical.centerX, canonical.centerY)
            kotlin.math.hypot(
                mapped.first - anchor.centerX,
                mapped.second - anchor.centerY,
            )
        } ?: 0.0

    private fun canonicalPlacement(placement: Int): CanonicalRect? =
        canonicalFields()
            .firstOrNull {
                it.type == FieldType.PLACEMENT &&
                    it.position == placement
            }
            ?.rect

    private fun canonicalFields(): List<CanonicalField> {
        val playerColumns = mapOf(
            "L1" to HorizontalRange(96.0, 233.0),
            "L2" to HorizontalRange(380.0, 520.0),
            "R1" to HorizontalRange(720.0, 865.0),
            "R2" to HorizontalRange(956.0, 1076.0),
        )
        val killColumns = mapOf(
            "L1" to HorizontalRange(233.0, 252.0),
            "L2" to HorizontalRange(520.0, 541.0),
            "R1" to HorizontalRange(865.0, 885.0),
            "R2" to HorizontalRange(1076.0, 1096.0),
        )
        val rows = mapOf(
            1 to RowBands(VerticalRange(8.0, 38.0), VerticalRange(45.0, 76.0)),
            2 to RowBands(VerticalRange(99.0, 129.0), VerticalRange(140.0, 170.0)),
            3 to RowBands(VerticalRange(191.0, 221.0), VerticalRange(233.0, 263.0)),
            4 to RowBands(VerticalRange(290.0, 321.0), VerticalRange(331.0, 362.0)),
            5 to RowBands(VerticalRange(382.0, 412.0), VerticalRange(423.0, 453.0)),
            6 to RowBands(VerticalRange(7.0, 35.0), VerticalRange(42.0, 70.0)),
            7 to RowBands(VerticalRange(88.0, 116.0), VerticalRange(121.0, 149.0)),
            8 to RowBands(VerticalRange(167.0, 195.0), VerticalRange(201.0, 229.0)),
            9 to RowBands(VerticalRange(248.0, 276.0), VerticalRange(282.0, 310.0)),
            10 to RowBands(VerticalRange(329.0, 357.0), VerticalRange(363.0, 391.0)),
        )
        val placements = mapOf(
            1 to CanonicalRect(10.0, 8.0, 61.0, 73.0),
            2 to CanonicalRect(13.0, 99.0, 63.0, 164.0),
            3 to CanonicalRect(12.0, 191.0, 63.0, 258.0),
            4 to CanonicalRect(23.0, 293.0, 50.0, 349.0),
            5 to CanonicalRect(23.0, 383.0, 51.0, 442.0),
            6 to CanonicalRect(672.0, 21.0, 701.0, 57.0),
            7 to CanonicalRect(673.0, 99.0, 701.0, 137.0),
            8 to CanonicalRect(673.0, 178.0, 701.0, 216.0),
            9 to CanonicalRect(673.0, 258.0, 701.0, 296.0),
            10 to CanonicalRect(668.0, 338.0, 708.0, 376.0),
        )

        return buildList {
            (1..10).forEach { position ->
                val placementRect = requireNotNull(placements[position])
                add(
                    CanonicalField(
                        id = "PLACEMENT_$position",
                        type = FieldType.PLACEMENT,
                        position = position,
                        slot = null,
                        rect = placementRect,
                    ),
                )

                val columnKeys = if (position <= 5) {
                    listOf("L1", "L1", "L2", "L2")
                } else {
                    listOf("R1", "R1", "R2", "R2")
                }
                val row = requireNotNull(rows[position])
                val verticalRanges = listOf(
                    row.upper,
                    row.lower,
                    row.upper,
                    row.lower,
                )

                (1..4).forEach { slot ->
                    val column = columnKeys[slot - 1]
                    val vertical = verticalRanges[slot - 1]
                    val player = requireNotNull(playerColumns[column])
                    val kill = requireNotNull(killColumns[column])

                    add(
                        CanonicalField(
                            id = "PLAYER_${position}_$slot",
                            type = FieldType.PLAYER,
                            position = position,
                            slot = slot,
                            rect = CanonicalRect(
                                left = player.left,
                                top = vertical.top,
                                right = player.right,
                                bottom = vertical.bottom,
                            ),
                        ),
                    )
                    add(
                        CanonicalField(
                            id = "KILL_${position}_$slot",
                            type = FieldType.KILL,
                            position = position,
                            slot = slot,
                            rect = CanonicalRect(
                                left = kill.left,
                                top = vertical.top,
                                right = kill.right,
                                bottom = vertical.bottom,
                            ),
                        ),
                    )
                }
            }
        }.also { fields ->
            check(fields.size == 90)
        }
    }

    private fun FieldResult.toLogLine(): String =
        buildString {
            append("FIELD|")
            append(fieldId)
            append("|type=")
            append(type)
            append("|position=")
            append(position)
            slot?.let {
                append("|slot=")
                append(it)
            }
            append("|roi=(")
            append(mappedRect.left.toInt())
            append(',')
            append(mappedRect.top.toInt())
            append(")-(")
            append(mappedRect.right.toInt())
            append(',')
            append(mappedRect.bottom.toInt())
            append(')')
            append("|ocr=")
            append(quoted(ocrText))
            append("|resolved=")
            append(quoted(resolvedText))
            append("|status=")
            append(status)
            append("|firstChar=")
            append(quoted(first?.text.orEmpty()))
            append("|firstCharBox=")
            append(
                first?.box?.let { box ->
                    "(${box.left},${box.top})-(${box.right},${box.bottom})"
                } ?: "null",
            )
            append("|firstCharXY=")
            append(
                first?.let { symbol ->
                    "(${symbol.box.left},${symbol.box.top})"
                } ?: "null",
            )
        }

    private fun quoted(value: String): String =
        "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\\\"") +
            "\""

    private fun fmt(value: Double): String = "%.4f".format(java.util.Locale.US, value)

    private const val CANONICAL_WIDTH = 1156.0
    private const val CANONICAL_HEIGHT = 456.0

    private enum class FieldType {
        PLACEMENT,
        PLAYER,
        KILL,
    }

    private data class CanonicalField(
        val id: String,
        val type: FieldType,
        val position: Int,
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

    private data class HorizontalRange(
        val left: Double,
        val right: Double,
    )

    private data class VerticalRange(
        val top: Double,
        val bottom: Double,
    )

    private data class RowBands(
        val upper: VerticalRange,
        val lower: VerticalRange,
    )

    private data class Padding(
        val horizontal: Double,
        val vertical: Double,
    )

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
        val elementBox: Rect,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
        val symbolIndex: Int,
    ) {
        val centerX: Double get() = (box.left + box.right) / 2.0
        val centerY: Double get() = (box.top + box.bottom) / 2.0
    }

    private data class PlacementAnchor(
        val placement: Int,
        val centerX: Double,
        val centerY: Double,
    )

    private data class AxisPair(
        val canonicalX: Double,
        val observedX: Double,
        val canonicalY: Double,
        val observedY: Double,
    )

    private data class AxisTransform(
        val scale: Double,
        val offset: Double,
    ) {
        fun map(value: Double): Double = scale * value + offset
    }

    private data class LayoutTransform(
        val x: AxisTransform,
        val y: AxisTransform,
    ) {
        fun map(rect: CanonicalRect): MappedRect =
            MappedRect(
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
        val position: Int,
        val slot: Int?,
        val mappedRect: MappedRect,
        val ocrText: String,
        val resolvedText: String,
        val status: String,
        val first: SymbolObservation?,
    )
}
