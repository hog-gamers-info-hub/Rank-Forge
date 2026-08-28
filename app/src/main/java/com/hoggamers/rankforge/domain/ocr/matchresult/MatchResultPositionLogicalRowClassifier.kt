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

        // OCR line centers need a tighter band than the full text height; otherwise the
        // physical upper/lower rows collapse into CENTER for larger fonts.
        val tolerance = medianHeight * CENTER_TOLERANCE_FRACTION
        // The placement number is structural evidence, not a player row. Remove only an
        // exact position token in the left placement region near the structural center.
        val classifiedLines = candidates.map { candidate ->
            val spanning = (candidate.box.bottom - candidate.box.top).toDouble() > medianHeight * SPANNING_HEIGHT_FACTOR
            val placement = candidate.line.text.trim() == position.toString() &&
                ((candidate.box.left + candidate.box.right) / 2.0) <= cropWidth * PLACEMENT_REGION_FRACTION &&
                abs(candidate.centerY - slotCenterYLocal) <= medianHeight &&
                !spanning
            val band = when {
                placement -> MatchResultPositionLogicalRowBand.PLACEMENT_FILTERED
                spanning -> MatchResultPositionLogicalRowBand.SPANNING_IGNORED
                candidate.centerY < slotCenterYLocal - tolerance -> MatchResultPositionLogicalRowBand.UPPER
                candidate.centerY > slotCenterYLocal + tolerance -> MatchResultPositionLogicalRowBand.LOWER
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

        val grouped = lines.groupBy { it.band.toLogicalRow() }
        val upper = grouped[MatchResultPositionLogicalRow.UPPER].orEmpty()
        val lower = grouped[MatchResultPositionLogicalRow.LOWER].orEmpty()
        val center = grouped[MatchResultPositionLogicalRow.CENTER].orEmpty()
        val reason = when {
            lower.isNotEmpty() && upper.isEmpty() && center.isEmpty() -> MatchResultPositionLogicalRowFallbackReason.LOWER_ONLY
            center.isNotEmpty() && upper.isNotEmpty() && lower.isNotEmpty() -> MatchResultPositionLogicalRowFallbackReason.CENTER_WITH_BOTH
            center.isNotEmpty() && upper.isNotEmpty() -> MatchResultPositionLogicalRowFallbackReason.CENTER_WITH_UPPER
            center.isNotEmpty() && lower.isNotEmpty() -> MatchResultPositionLogicalRowFallbackReason.CENTER_WITH_LOWER
            else -> null
        }
        if (reason != null) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            medianHeight = medianHeight, tolerance = tolerance, placementRemoved = placementRemoved,
            spanningIgnored = spanningIgnored, usableLines = lines.size,
            upper = upper.size, center = center.size, lower = lower.size,
            reason = reason,
        )

        val rowCrops = when {
            upper.isNotEmpty() && lower.isNotEmpty() -> listOf(
                rowCrop(1, upper, cropWidth, cropHeight),
                rowCrop(2, lower, cropWidth, cropHeight),
            )
            center.isNotEmpty() -> listOf(
                rowCrop(1, center, cropWidth, cropHeight),
            )
            upper.isNotEmpty() -> listOf(
                rowCrop(1, upper, cropWidth, cropHeight),
            )
            else -> return unavailable(
                position, cropHeight, slotCenterYLocal, totalMappedLines,
                medianHeight = medianHeight, tolerance = tolerance, placementRemoved = placementRemoved,
                spanningIgnored = spanningIgnored, usableLines = lines.size,
                reason = MatchResultPositionLogicalRowFallbackReason.NO_LOGICAL_ROWS,
            )
        }
        if (rowCrops.any { it == null }) return unavailable(
            position, cropHeight, slotCenterYLocal, totalMappedLines,
            medianHeight = medianHeight, placementRemoved = placementRemoved, usableLines = lines.size,
            upper = upper.size, center = center.size, lower = lower.size,
            reason = MatchResultPositionLogicalRowFallbackReason.NO_LOGICAL_ROWS,
        )
        val classification = when {
            upper.isNotEmpty() && lower.isNotEmpty() -> MatchResultPositionLogicalRowClassificationKind.ROW1_AND_ROW2
            center.isNotEmpty() -> MatchResultPositionLogicalRowClassificationKind.CENTERED_SINGLE_ROW
            else -> MatchResultPositionLogicalRowClassificationKind.ROW1_ONLY
        }
        val diagnostics = MatchResultPositionLogicalRowDiagnostics(
            position = position, positionHeight = cropHeight, slotCenterYLocal = slotCenterYLocal,
            medianTextHeight = medianHeight, derivedTolerance = tolerance,
            totalMappedLines = totalMappedLines, placementLinesRemoved = placementRemoved,
            spanningIgnored = spanningIgnored,
            usableLines = lines.size, upperCount = upper.size, centerCount = center.size,
            lowerCount = lower.size, classification = classification,
        )
        return MatchResultPositionLogicalRowClassification.Available(
            rowCrops = rowCrops.filterNotNull(),
            diagnostics = diagnostics,
            blocks = blocks.mapNotNull { block ->
                block.copy(lines = block.lines.filter { line ->
                    classifiedLines.any {
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
        const val CENTER_TOLERANCE_FRACTION = 0.40
        const val SPANNING_HEIGHT_FACTOR = 2.0
    }
}
