package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import kotlin.math.abs

enum class MatchResultPositionLogicalRow {
    UPPER,
    LOWER,
    CENTER,
}

enum class MatchResultPositionLogicalRowClassificationKind {
    ROW1_ONLY,
    ROW2_ONLY,
    ROW1_AND_ROW2,
    CENTERED_SINGLE_ROW,
    UNAVAILABLE,
}

enum class MatchResultPositionLogicalRowFallbackReason {
    STRUCTURAL_CENTER_UNAVAILABLE,
    INVALID_STRUCTURAL_CENTER,
    NO_USABLE_GEOMETRY,
    PLACEMENT_FILTER_REMOVED_ALL,
    LOWER_ONLY,
    CENTER_ONLY_NOT_SINGLE_PLAYER,
    CENTER_WITH_UPPER,
    CENTER_WITH_LOWER,
    CENTER_WITH_BOTH,
    CONFLICTING_CLUSTERS,
    NO_LOGICAL_ROWS,
}

enum class MatchResultPositionLogicalRowBand {
    UPPER,
    CENTER,
    LOWER,
    PLACEMENT_FILTERED,
    SPANNING_IGNORED,
}

data class MatchResultPositionLogicalRowDiagnostics(
    val position: Int,
    val positionHeight: Int,
    val slotCenterYLocal: Double?,
    val medianTextHeight: Double?,
    val derivedTolerance: Double?,
    val totalMappedLines: Int,
    val placementLinesRemoved: Int,
    val spanningIgnored: Int,
    val usableLines: Int,
    val upperCount: Int,
    val centerCount: Int,
    val lowerCount: Int,
    val classification: MatchResultPositionLogicalRowClassificationKind,
    val reason: MatchResultPositionLogicalRowFallbackReason? = null,
    val reasonText: String? = null,
)

sealed interface MatchResultPositionLogicalRowClassification {
    val diagnostics: MatchResultPositionLogicalRowDiagnostics

    data class Available(
        val rowCrops: List<MatchResultPositionRowCrop>,
        val blocks: List<RawOcrBlock>,
        override val diagnostics: MatchResultPositionLogicalRowDiagnostics,
    ) : MatchResultPositionLogicalRowClassification

    data class Unavailable(
        override val diagnostics: MatchResultPositionLogicalRowDiagnostics,
    ) : MatchResultPositionLogicalRowClassification
}

/** Classifies whole-position OCR lines without introducing fixed pixel thresholds. */
class MatchResultPositionLogicalRowClassifier {
    fun classify(
        position: Int,
        cropWidth: Int,
        cropHeight: Int,
        slotCenterYLocal: Double?,
        blocks: List<RawOcrBlock>,
        allowSingleRowFallback: Boolean = false,
    ): MatchResultPositionLogicalRowClassification {
        val totalMappedLines = blocks.sumOf { it.lines.size }
        if (slotCenterYLocal == null) return unavailable(
            position, cropHeight, null, totalMappedLines,
            reason = MatchResultPositionLogicalRowFallbackReason.STRUCTURAL_CENTER_UNAVAILABLE,
        )
        if (position !in 1..12 || cropWidth <= 0 || cropHeight <= 0 ||
            !slotCenterYLocal.isFinite() || slotCenterYLocal !in 0.0..cropHeight.toDouble()
        ) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            reason = MatchResultPositionLogicalRowFallbackReason.INVALID_STRUCTURAL_CENTER,
        )

        val candidates = blocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.geometry?.boundingBox ?: return@mapNotNull null
                if (box.right <= box.left || box.bottom <= box.top) return@mapNotNull null
                val centerX = (box.left + box.right) / 2.0
                val centerY = (box.top + box.bottom) / 2.0
                if (!centerX.isFinite() || !centerY.isFinite() ||
                    centerX !in 0.0..cropWidth.toDouble() ||
                    centerY !in 0.0..cropHeight.toDouble()
                ) return@mapNotNull null
                Candidate(line, box, centerY)
            }
        }
        if (candidates.isEmpty()) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            reason = MatchResultPositionLogicalRowFallbackReason.NO_USABLE_GEOMETRY,
        )

        val medianHeight = median(candidates.map { (it.box.bottom - it.box.top).toDouble() })
            ?: return unavailable(
                position, cropHeight, slotCenterYLocal, totalMappedLines,
                usableLines = candidates.size,
                reason = MatchResultPositionLogicalRowFallbackReason.NO_USABLE_GEOMETRY,
            )
        if (!medianHeight.isFinite() || medianHeight <= 0.0) {
            return unavailable(
                position, cropHeight, slotCenterYLocal, totalMappedLines,
                usableLines = candidates.size,
                reason = MatchResultPositionLogicalRowFallbackReason.NO_USABLE_GEOMETRY,
            )
        }

        // Match Result position rectangles contain two physical text rows; OCR fragments
        // from one row share near-identical vertical centers. CENTER is uncertainty, not
        // a third row, so derive UPPER and LOWER from the strongest Y split first.
        val tolerance = medianHeight * CENTER_TOLERANCE_FRACTION

        // The placement number is structural evidence, not a player row. Remove only a
        // compact numeric-like token in the left placement region near the structural center.
        val classifiedLines = candidates.map { candidate ->
            val spanning = (candidate.box.bottom - candidate.box.top).toDouble() > medianHeight * SPANNING_HEIGHT_FACTOR
            val placement = isStructuralPlacementCandidate(
                candidate, cropWidth, slotCenterYLocal, medianHeight, spanning,
            )
            val band = when {
                placement -> MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED
                spanning -> MatchResultPositionLogicalRowBand.SPANNING_IGNORED
                else -> MatchResultPositionLogicalRowBand.CENTER
            }
            ClassifiedLine(candidate, band)
        }

        val placementRemoved = classifiedLines.count { it.band == MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED }
        val spanningIgnored = classifiedLines.count { it.band == MatchResultPositionLogicalRowBand.SPANNING_IGNORED }
        val lines = classifiedLines.filter {
            it.band != MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED &&
                it.band != MatchResultPositionLogicalRowBand.SPANNING_IGNORED
        }
        if (lines.isEmpty()) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            medianHeight = medianHeight, tolerance = tolerance, placementRemoved = placementRemoved,
            spanningIgnored = spanningIgnored,
            reason = if (placementRemoved > 0 && spanningIgnored == 0) {
                MatchResultPositionLogicalRowFallbackReason.PLACEMENT_FILTER_REMOVED_ALL
            } else {
                MatchResultPositionLogicalRowFallbackReason.NO_LOGICAL_ROWS
            },
        )

        val clusters = deriveTwoRowClusters(lines.map { it.candidate }, slotCenterYLocal, tolerance)
        if (clusters == null) {
            val singleRow = if (allowSingleRowFallback) {
                deriveSingleRow(lines.map { it.candidate }, slotCenterYLocal, tolerance)
            } else {
                null
            }
            if (singleRow != null) {
                val rowIndex = if (singleRow.centerY < slotCenterYLocal) 1 else 2
                val rowCrop = rowCrop(rowIndex, lines, cropWidth, cropHeight)
                if (rowCrop != null) {
                    val diagnostics = MatchResultPositionLogicalRowDiagnostics(
                        position = position, positionHeight = cropHeight,
                        slotCenterYLocal = slotCenterYLocal, medianTextHeight = medianHeight,
                        derivedTolerance = tolerance, totalMappedLines = totalMappedLines,
                        placementLinesRemoved = placementRemoved, spanningIgnored = spanningIgnored,
                        usableLines = lines.size,
                        upperCount = if (rowIndex == 1) lines.size else 0,
                        centerCount = 0,
                        lowerCount = if (rowIndex == 2) lines.size else 0,
                        classification = if (rowIndex == 1) {
                            MatchResultPositionLogicalRowClassificationKind.ROW1_ONLY
                        } else {
                            MatchResultPositionLogicalRowClassificationKind.ROW2_ONLY
                        },
                    )
                    return MatchResultPositionLogicalRowClassification.Available(
                        rowCrops = listOf(rowCrop),
                        diagnostics = diagnostics,
                        blocks = blocks.mapNotNull { block ->
                            block.copy(lines = block.lines.filter { line ->
                                lines.any { it.candidate.line === line }
                            }).takeIf { it.lines.isNotEmpty() }
                        },
                    )
                }
            }
            return unavailable(
                position, cropHeight, slotCenterYLocal, totalMappedLines,
                medianHeight = medianHeight, tolerance = tolerance, placementRemoved = placementRemoved,
                spanningIgnored = spanningIgnored, usableLines = lines.size,
                reason = MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS,
            )
        }

        val assignedLines = classifiedLines.map { line ->
            if (line.band == MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED ||
                line.band == MatchResultPositionLogicalRowBand.SPANNING_IGNORED
            ) return@map line
            val upperDistance = abs(line.candidate.centerY - clusters.upperCenter)
            val lowerDistance = abs(line.candidate.centerY - clusters.lowerCenter)
            if (upperDistance == lowerDistance) {
                return unavailable(
                    position, cropHeight, slotCenterYLocal, totalMappedLines,
                    medianHeight = medianHeight, tolerance = tolerance,
                    placementRemoved = placementRemoved, spanningIgnored = spanningIgnored,
                    usableLines = lines.size,
                    reason = MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS,
                )
            }
            ClassifiedLine(
                line.candidate,
                if (upperDistance < lowerDistance) {
                    MatchResultPositionLogicalRowBand.UPPER
                } else {
                    MatchResultPositionLogicalRowBand.LOWER
                },
            )
        }
        val upper = assignedLines.filter { it.band == MatchResultPositionLogicalRowBand.UPPER }
        val lower = assignedLines.filter { it.band == MatchResultPositionLogicalRowBand.LOWER }
        if (upper.isEmpty() || lower.isEmpty()) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            medianHeight = medianHeight, tolerance = tolerance,
            placementRemoved = placementRemoved, spanningIgnored = spanningIgnored,
            usableLines = lines.size, upper = upper.size, lower = lower.size,
            reason = MatchResultPositionLogicalRowFallbackReason.CONFLICTING_CLUSTERS,
        )

        val rowCrops = listOf(rowCrop(1, upper, cropWidth, cropHeight), rowCrop(2, lower, cropWidth, cropHeight))
        if (rowCrops.any { it == null }) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            medianHeight = medianHeight, tolerance = tolerance,
            placementRemoved = placementRemoved, spanningIgnored = spanningIgnored,
            usableLines = lines.size, upper = upper.size, lower = lower.size,
            reason = MatchResultPositionLogicalRowFallbackReason.NO_LOGICAL_ROWS,
        )
        val diagnostics = MatchResultPositionLogicalRowDiagnostics(
            position = position, positionHeight = cropHeight, slotCenterYLocal = slotCenterYLocal,
            medianTextHeight = medianHeight, derivedTolerance = tolerance,
            totalMappedLines = totalMappedLines, placementLinesRemoved = placementRemoved,
            spanningIgnored = spanningIgnored,
            usableLines = lines.size, upperCount = upper.size, centerCount = 0,
            lowerCount = lower.size,
            classification = MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2,
        )

        return MatchResultPositionLogicalRowClassification.Available(
            rowCrops = rowCrops.filterNotNull(),
            diagnostics = diagnostics,
            blocks = blocks.mapNotNull { block ->
                block.copy(lines = block.lines.filter { line ->
                    assignedLines.any {
                        it.candidate.line === line && it.band != MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED
                    }
                })
                    .takeIf { it.lines.isNotEmpty() }
            },
        )
    }

    private fun unavailable(
        position: Int,
        cropHeight: Int,
        slotCenterYLocal: Double?,
        totalMappedLines: Int,
        reason: MatchResultPositionLogicalRowFallbackReason,
        medianHeight: Double? = null,
        tolerance: Double? = medianHeight?.let { it * CENTER_TOLERANCE_FRACTION },
        placementRemoved: Int = 0,
        spanningIgnored: Int = 0,
        usableLines: Int = 0,
        upper: Int = 0,
        center: Int = 0,
        lower: Int = 0,
    ) = MatchResultPositionLogicalRowClassification.Unavailable(
        diagnostics = MatchResultPositionLogicalRowDiagnostics(
            position = position,
            positionHeight = cropHeight,
            slotCenterYLocal = slotCenterYLocal,
            medianTextHeight = medianHeight,
            derivedTolerance = tolerance,
            totalMappedLines = totalMappedLines,
            placementLinesRemoved = placementRemoved,
            spanningIgnored = spanningIgnored,
            usableLines = usableLines,
            upperCount = upper,
            centerCount = center,
            lowerCount = lower,
            classification = MatchResultPositionLogicalRowClassificationKind.UNAVAILABLE,
            reason = reason,
            reasonText = reason.name,
        ),
    )

    private fun isStructuralPlacementCandidate(
        candidate: Candidate,
        cropWidth: Int,
        slotCenterYLocal: Double,
        medianHeight: Double,
        spanning: Boolean,
    ): Boolean {
        if (spanning) return false
        val token = candidate.line.text.trim()
        if (token.isEmpty() || token.length > MAX_PLACEMENT_TOKEN_LENGTH ||
            token.any { it !in NUMERIC_LIKE_PLACEMENT_CHARACTERS }
        ) return false
        val centerX = (candidate.box.left + candidate.box.right) / 2.0
        return centerX <= cropWidth * PLACEMENT_REGION_FRACTION &&
            abs(candidate.centerY - slotCenterYLocal) <= medianHeight
    }

    private fun deriveTwoRowClusters(
        candidates: List<Candidate>,
        slotCenterYLocal: Double,
        tolerance: Double,
    ): RowClusters? {
        if (candidates.size < 2) return null
        val sorted = candidates.sortedBy { it.centerY }
        var largestGap = Double.NEGATIVE_INFINITY
        var splitIndex = -1
        for (index in 0 until sorted.lastIndex) {
            val gap = sorted[index + 1].centerY - sorted[index].centerY
            if (gap > largestGap) {
                largestGap = gap
                splitIndex = index
            }
        }
        if (splitIndex < 0 || largestGap <= tolerance) return null
        val upper = sorted.subList(0, splitIndex + 1)
        val lower = sorted.subList(splitIndex + 1, sorted.size)
        val upperCenter = median(upper.map { it.centerY }) ?: return null
        val lowerCenter = median(lower.map { it.centerY }) ?: return null
        if (upperCenter >= slotCenterYLocal || lowerCenter <= slotCenterYLocal) return null
        if (upper.zipWithNext().any { (first, second) -> second.centerY - first.centerY > tolerance } ||
            lower.zipWithNext().any { (first, second) -> second.centerY - first.centerY > tolerance }
        ) return null
        if (upper.any { abs(it.centerY - upperCenter) > tolerance } ||
            lower.any { abs(it.centerY - lowerCenter) > tolerance }
        ) return null
        return RowClusters(upper, lower, upperCenter, lowerCenter)
    }

    private fun deriveSingleRow(
        candidates: List<Candidate>,
        slotCenterYLocal: Double,
        tolerance: Double,
    ): SingleRow? {
        if (candidates.isEmpty()) return null
        val centerY = median(candidates.map { it.centerY }) ?: return null
        if (abs(centerY - slotCenterYLocal) <= tolerance) return null
        if (candidates.any { abs(it.centerY - centerY) > tolerance }) return null
        return SingleRow(centerY)
    }

    private fun rowCrop(
        rowIndex: Int,
        lines: List<ClassifiedLine>,
        width: Int,
        height: Int,
    ): MatchResultPositionRowCrop? {
        if (lines.isEmpty()) return null
        val left = lines.minOf { it.candidate.box.left }.coerceIn(0, width)
        val top = lines.minOf { it.candidate.box.top }.coerceIn(0, height)
        val right = lines.maxOf { it.candidate.box.right }.coerceIn(0, width)
        val bottom = lines.maxOf { it.candidate.box.bottom }.coerceIn(0, height)
        return if (right > left && bottom > top) {
            MatchResultPositionRowCrop(rowIndex, OcrPixelCropRect(left, top, right, bottom))
        } else null
    }

    private data class Candidate(
        val line: RawOcrLine,
        val box: RawOcrBoundingBox,
        val centerY: Double,
    )

    private data class ClassifiedLine(
        val candidate: Candidate,
        val band: MatchResultPositionLogicalRowBand,
    )

    private data class RowClusters(
        val upper: List<Candidate>,
        val lower: List<Candidate>,
        val upperCenter: Double,
        val lowerCenter: Double,
    )

    private data class SingleRow(val centerY: Double)

    private fun MatchResultPositionLogicalRowBand.toLogicalRow(): MatchResultPositionLogicalRow = when (this) {
        MatchResultPositionLogicalRowBand.UPPER -> MatchResultPositionLogicalRow.UPPER
        MatchResultPositionLogicalRowBand.CENTER -> MatchResultPositionLogicalRow.CENTER
        MatchResultPositionLogicalRowBand.LOWER -> MatchResultPositionLogicalRow.LOWER
        MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED -> error("Placement evidence must be filtered first.")
        MatchResultPositionLogicalRowBand.SPANNING_IGNORED -> error("Spanning evidence must be filtered first.")
    }

    private fun median(values: List<Double>): Double? {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private companion object {
        const val PLACEMENT_REGION_FRACTION = 0.10
        const val CENTER_TOLERANCE_FRACTION = 0.35
        const val SPANNING_HEIGHT_FACTOR = 2.0
        const val MAX_PLACEMENT_TOKEN_LENGTH = 3
        const val NUMERIC_LIKE_PLACEMENT_CHARACTERS = "0123456789IiLlOo|"
    }
}
