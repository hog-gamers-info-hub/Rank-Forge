package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlin.math.abs

class MatchResultOcrFieldExtractor {
    fun extract(
        role: MatchResultScreenshotRole,
        cropWidth: Int,
        cropHeight: Int,
        blocks: List<RawOcrBlock>,
    ): MatchResultOcrExtractionResult {
        require(cropWidth > 0) { "Crop width must be positive." }
        require(cropHeight > 0) { "Crop height must be positive." }

        val layout = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultOcrCanonicalLayouts.upper
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> MatchResultOcrCanonicalLayouts.lower
        }
        val elements = flattenElements(blocks)
        val transform = fitLayoutTransform(role, layout, elements, cropWidth, cropHeight)
        val initiallyExtracted = layout.fields.map { field ->
            mapField(field, transform, elements, role)
        }
        val extractedFields = applyZeroKillFallback(initiallyExtracted)

        return when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> extractUpper(extractedFields)
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> extractLower(extractedFields)
        }
    }

    private fun extractUpper(fields: List<MatchResultOcrField>): MatchResultOcrExtractionResult {
        val rows = (1..10).map { position ->
            MatchResultOcrRowAssembler.assemble(
                position = position,
                source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                fields = fields,
            )
        }
        return MatchResultOcrExtractionResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            fields = fields,
            rows = rows,
        )
    }

    private fun extractLower(fields: List<MatchResultOcrField>): MatchResultOcrExtractionResult {
        val ignoredRows = mutableListOf<MatchResultOcrIgnoredLowerVisualRow>()
        val manualReviewRows = mutableListOf<MatchResultOcrManualReviewRow>()
        val emitted = mutableListOf<EmittedLowerRow>()

        MatchResultOcrVisualRow.entries.forEach { visualRow ->
            val rowFields = fields.filter { it.visualRow == visualRow }
            val placement = rowFields.first { it.type == MatchResultOcrFieldType.PLACEMENT }
            val detectedPlacement = placement.resolvedText.trim().toIntOrNull()
            when {
                detectedPlacement == 11 || detectedPlacement == 12 -> {
                    emitted += EmittedLowerRow(visualRow, detectedPlacement)
                }

                detectedPlacement != null && detectedPlacement <= 10 -> {
                    ignoredRows += MatchResultOcrIgnoredLowerVisualRow(
                        visualRow = visualRow,
                        detectedPlacement = detectedPlacement,
                        reason = MatchResultOcrIgnoredLowerVisualRowReason.UPPER_OWNS_POSITION,
                    )
                }

                detectedPlacement == null && placement.ocrText.isBlank() -> {
                    manualReviewRows += MatchResultOcrManualReviewRow(
                        visualRow = visualRow,
                        detectedPlacementText = placement.ocrText,
                        reason = MatchResultOcrManualReviewReason.MISSING_PLACEMENT,
                    )
                }

                detectedPlacement == null -> {
                    manualReviewRows += MatchResultOcrManualReviewRow(
                        visualRow = visualRow,
                        detectedPlacementText = placement.ocrText,
                        reason = MatchResultOcrManualReviewReason.INVALID_PLACEMENT,
                    )
                }

                else -> {
                    manualReviewRows += MatchResultOcrManualReviewRow(
                        visualRow = visualRow,
                        detectedPlacementText = placement.ocrText,
                        reason = MatchResultOcrManualReviewReason.UNSUPPORTED_PLACEMENT,
                    )
                }
            }
        }

        val semanticFields = fields.map { field ->
            val emittedPosition = emitted.firstOrNull { it.visualRow == field.visualRow }?.position
            field.copy(position = emittedPosition)
        }
        val rows = emitted.map { emittedRow ->
            MatchResultOcrRowAssembler.assemble(
                position = emittedRow.position,
                source = when (emittedRow.visualRow) {
                    MatchResultOcrVisualRow.A -> MatchResultOcrRowSource.LOWER_ROW_A
                    MatchResultOcrVisualRow.B -> MatchResultOcrRowSource.LOWER_ROW_B
                },
                fields = semanticFields,
                visualRow = emittedRow.visualRow,
            )
        }

        return MatchResultOcrExtractionResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            fields = semanticFields,
            rows = rows,
            ignoredLowerRows = ignoredRows,
            manualReviewRows = manualReviewRows,
        )
    }

    private fun mapField(
        field: MatchResultOcrCanonicalField,
        transform: LayoutTransform,
        elements: List<ElementObservation>,
        role: MatchResultScreenshotRole,
    ): MatchResultOcrField {
        val mapped = transform.map(field.rect)
        val padding = when (field.type) {
            MatchResultOcrFieldType.PLACEMENT -> 3.0 to 3.0
            MatchResultOcrFieldType.PLAYER -> 0.0 to 3.0
            MatchResultOcrFieldType.KILL -> {
                if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 12.0 to 3.0 else 0.0 to 3.0
            }
        }
        val selected = elements
            .filter { it.box.intersects(mapped.expanded(padding.first, padding.second)) }
            .sortedWith(compareBy<ElementObservation> { it.box.left }.thenBy { it.box.top })

        return when (field.type) {
            MatchResultOcrFieldType.PLACEMENT -> mapPlacementField(field, mapped, selected, role)
            MatchResultOcrFieldType.PLAYER -> {
                val rendered = renderPlayer(selected)
                MatchResultOcrField(
                    id = field.id,
                    type = field.type,
                    position = field.position,
                    visualRow = field.visualRow,
                    slot = field.slot,
                    canonicalRect = field.rect,
                    mappedRect = mapped,
                    ocrText = rendered,
                    resolvedText = rendered,
                    status = if (rendered.isBlank()) {
                        MatchResultOcrFieldStatus.EMPTY
                    } else {
                        MatchResultOcrFieldStatus.DIRECT_TEXT
                    },
                )
            }

            MatchResultOcrFieldType.KILL -> mapKillField(field, mapped, selected)
        }
    }

    private fun mapPlacementField(
        field: MatchResultOcrCanonicalField,
        mapped: MatchResultOcrRect,
        selected: List<ElementObservation>,
        role: MatchResultScreenshotRole,
    ): MatchResultOcrField {
        val rawText = renderRawText(selected)
        val numericText = selected
            .flatMap { it.text.filter(Char::isDigit).map(Char::toString) }
            .joinToString(separator = "")
        val expected = field.position
        val resolvedText = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
            expected?.toString().orEmpty()
        } else {
            numericText
        }
        val status = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
            when {
                rawText.isBlank() -> MatchResultOcrFieldStatus.TEMPLATE_ONLY
                numericText == expected?.toString() -> MatchResultOcrFieldStatus.OCR_MATCH
                else -> MatchResultOcrFieldStatus.OCR_MISMATCH
            }
        } else if (numericText.isBlank()) {
            MatchResultOcrFieldStatus.EMPTY
        } else {
            MatchResultOcrFieldStatus.DIRECT_NUMERIC
        }
        return MatchResultOcrField(
            id = field.id,
            type = field.type,
            position = field.position,
            visualRow = field.visualRow,
            slot = field.slot,
            canonicalRect = field.rect,
            mappedRect = mapped,
            ocrText = rawText,
            resolvedText = resolvedText,
            status = status,
        )
    }

    private fun mapKillField(
        field: MatchResultOcrCanonicalField,
        mapped: MatchResultOcrRect,
        selected: List<ElementObservation>,
    ): MatchResultOcrField {
        val killTokens = selected.flatMap { element ->
            val text = element.text.trim()
            if (text.isNotEmpty() && text.all { it.isDigit() || it == 'O' || it == 'o' }) {
                text.map { it.toString() }
            } else {
                emptyList()
            }
        }
        val rawText = killTokens.joinToString(separator = "")
        val normalizedText = rawText.map { char ->
            if (char == 'O' || char == 'o') '0' else char
        }.joinToString(separator = "")
        val status = when {
            normalizedText.isBlank() -> MatchResultOcrFieldStatus.EMPTY
            rawText.any { it == 'O' || it == 'o' } -> MatchResultOcrFieldStatus.O_NORMALIZED_TO_0
            else -> MatchResultOcrFieldStatus.DIRECT_NUMERIC
        }
        return MatchResultOcrField(
            id = field.id,
            type = field.type,
            position = field.position,
            visualRow = field.visualRow,
            slot = field.slot,
            canonicalRect = field.rect,
            mappedRect = mapped,
            ocrText = rawText,
            resolvedText = normalizedText,
            status = status,
        )
    }

    private fun renderPlayer(elements: List<ElementObservation>): String =
        elements.joinToString(separator = " ") { it.text.trim() }.trim()

    private fun renderRawText(elements: List<ElementObservation>): String =
        elements.joinToString(separator = " ") { it.text.trim() }.trim()

    private fun applyZeroKillFallback(fields: List<MatchResultOcrField>): List<MatchResultOcrField> {
        val players = fields
            .filter { it.type == MatchResultOcrFieldType.PLAYER }
            .associateBy { Triple(it.position, it.visualRow, it.slot) }
        return fields.map { field ->
            if (field.type != MatchResultOcrFieldType.KILL || field.resolvedText.isNotBlank()) {
                field
            } else {
                val player = players[Triple(field.position, field.visualRow, field.slot)]
                if (player?.resolvedText?.isNotBlank() == true) {
                    field.copy(
                        resolvedText = "0",
                        status = MatchResultOcrFieldStatus.ZERO_INFERRED_FROM_PLAYER_PRESENT,
                    )
                } else {
                    field
                }
            }
        }
    }

    private fun flattenElements(blocks: List<RawOcrBlock>): List<ElementObservation> = buildList {
        blocks.forEachIndexed { blockIndex, block ->
            block.lines.forEachIndexed { lineIndex, line ->
                line.elements.forEachIndexed { elementIndex, element ->
                    element.geometry.boundingBoxOrCorners()?.let { box ->
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
    }

    private fun fitLayoutTransform(
        role: MatchResultScreenshotRole,
        layout: MatchResultOcrCanonicalLayout,
        elements: List<ElementObservation>,
        cropWidth: Int,
        cropHeight: Int,
    ): LayoutTransform {
        val anchors = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> detectUpperAnchors(layout, elements, cropHeight)
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> detectLowerAnchors(layout, elements, cropWidth, cropHeight)
        }
        val fallbackX = cropWidth.toDouble() / layout.width
        val fallbackY = cropHeight.toDouble() / layout.height
        return LayoutTransform(
            x = fitAxis(anchors.map { it.canonical.centerX() }, anchors.map { it.observedX }, fallbackX),
            y = fitAxis(anchors.map { it.canonical.centerY() }, anchors.map { it.observedY }, fallbackY),
        )
    }

    private fun detectUpperAnchors(
        layout: MatchResultOcrCanonicalLayout,
        elements: List<ElementObservation>,
        cropHeight: Int,
    ): List<Anchor> {
        val byPosition = layout.fields
            .filter { it.type == MatchResultOcrFieldType.PLACEMENT }
            .associateBy { it.position }
        return elements.mapNotNull { element ->
            val placement = element.text.trim().toIntOrNull()?.takeIf { it in 1..10 } ?: return@mapNotNull null
            val canonical = byPosition[placement]?.rect ?: return@mapNotNull null
            val leftBand = placement <= 5 && element.box.centerX() < 0.20 * layout.width
            val rightBand = placement >= 6 && element.box.centerX() in 0.50 * layout.width..0.68 * layout.width
            if (!leftBand && !rightBand) return@mapNotNull null
            Anchor(canonical, element.box.centerX(), element.box.centerY(), placement, null)
        }.groupBy { it.position }.mapNotNull { (position, candidates) ->
            candidates.minByOrNull { candidate ->
                abs(candidate.observedY - expectedUpperAnchorY(position, layout, cropHeight))
            }
        }
    }

    private fun expectedUpperAnchorY(
        position: Int?,
        layout: MatchResultOcrCanonicalLayout,
        cropHeight: Int,
    ): Double {
        val canonical = layout.fields.firstOrNull {
            it.type == MatchResultOcrFieldType.PLACEMENT && it.position == position
        }?.rect ?: return 0.0
        return canonical.centerY() * cropHeight / layout.height
    }

    private fun detectLowerAnchors(
        layout: MatchResultOcrCanonicalLayout,
        elements: List<ElementObservation>,
        cropWidth: Int,
        cropHeight: Int,
    ): List<Anchor> {
        val placements = layout.fields
            .filter { it.type == MatchResultOcrFieldType.PLACEMENT }
            .associateBy { it.visualRow }
        return elements.mapNotNull { element ->
            val placement = element.text.trim().toIntOrNull()?.takeIf { it in 1..12 }
                ?: return@mapNotNull null
            if (element.box.centerX() !in cropWidth * 0.45..cropWidth * 0.80) {
                return@mapNotNull null
            }
            val visualRow = MatchResultOcrVisualRow.entries.minByOrNull { row ->
                abs(element.box.centerY() - expectedLowerAnchorY(row, cropHeight))
            } ?: return@mapNotNull null
            if (abs(element.box.centerY() - expectedLowerAnchorY(visualRow, cropHeight)) > cropHeight * 0.16) {
                return@mapNotNull null
            }
            val canonical = placements[visualRow]?.rect ?: return@mapNotNull null
            Anchor(canonical, element.box.centerX(), element.box.centerY(), null, visualRow)
        }.groupBy { it.visualRow }.mapNotNull { (_, candidates) -> candidates.minByOrNull {
            abs(it.observedY - expectedLowerAnchorY(it.visualRow!!, cropHeight))
        } }
    }

    private fun expectedLowerAnchorY(visualRow: MatchResultOcrVisualRow, cropHeight: Int): Double =
        cropHeight.toDouble() / MatchResultOcrCanonicalLayouts.lower.height * when (visualRow) {
            MatchResultOcrVisualRow.A -> 330.0
            MatchResultOcrVisualRow.B -> 411.0
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

    private data class EmittedLowerRow(
        val visualRow: MatchResultOcrVisualRow,
        val position: Int,
    )

    private data class Anchor(
        val canonical: MatchResultOcrRect,
        val observedX: Double,
        val observedY: Double,
        val position: Int?,
        val visualRow: MatchResultOcrVisualRow?,
    )

    private data class ElementObservation(
        val text: String,
        val box: MatchResultOcrRect,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
    )

    private data class AxisTransform(val scale: Double, val offset: Double) {
        fun map(value: Double): Double = scale * value + offset
    }

    private data class LayoutTransform(val x: AxisTransform, val y: AxisTransform) {
        fun map(rect: MatchResultOcrRect): MatchResultOcrRect = MatchResultOcrRect(
            left = x.map(rect.left),
            top = y.map(rect.top),
            right = x.map(rect.right),
            bottom = y.map(rect.bottom),
        )
    }
}

private fun RawOcrGeometry?.boundingBoxOrCorners(): MatchResultOcrRect? {
    val boundingBox = this?.boundingBox
    if (boundingBox != null) return boundingBox.toMatchResultRect()
    val points = this?.cornerPoints.orEmpty()
    if (points.isEmpty()) return null
    return MatchResultOcrRect(
        left = points.minOf { it.x }.toDouble(),
        top = points.minOf { it.y }.toDouble(),
        right = points.maxOf { it.x }.toDouble(),
        bottom = points.maxOf { it.y }.toDouble(),
    )
}

private fun RawOcrBoundingBox.toMatchResultRect(): MatchResultOcrRect = MatchResultOcrRect(
    left = left.toDouble(),
    top = top.toDouble(),
    right = right.toDouble(),
    bottom = bottom.toDouble(),
)

private fun MatchResultOcrRect.expanded(horizontal: Double, vertical: Double): MatchResultOcrRect =
    MatchResultOcrRect(
        left = left - horizontal,
        top = top - vertical,
        right = right + horizontal,
        bottom = bottom + vertical,
    )

private fun MatchResultOcrRect.intersects(other: MatchResultOcrRect): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top

private fun MatchResultOcrRect.centerX(): Double = (left + right) / 2.0

private fun MatchResultOcrRect.centerY(): Double = (top + bottom) / 2.0
