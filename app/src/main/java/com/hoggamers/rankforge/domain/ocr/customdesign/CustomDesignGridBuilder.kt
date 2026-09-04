package com.hoggamers.rankforge.domain.ocr.customdesign

import javax.inject.Inject
import kotlin.math.abs

class CustomDesignGridBuilder @Inject constructor() {
    fun build(
        detection: CustomDesignAnchorDetectionResult,
    ): CustomDesignGridGeometry {
        val anchors = detection.anchors
        val detectedRows = anchors.rowY.entries
            .asSequence()
            .filter { (rank, y) ->
                rank in RANK_RANGE &&
                    rank !in detection.ambiguousRanks &&
                    y.isFinite() &&
                    y >= 0f &&
                    y <= anchors.sourceHeight.toFloat()
            }
            .sortedBy { it.key }
            .toList()

        val rowY = linkedMapOf<Int, CustomDesignRowCoordinate>()
        detectedRows.forEach { (rank, y) ->
            rowY[rank] = CustomDesignRowCoordinate(
                y = y,
                source = CustomDesignRowCoordinateSource.OCR,
            )
        }

        val lattice = estimateLattice(detectedRows)
        if (lattice != null) {
            addInternalInterpolations(
                rowY = rowY,
                detectedRows = detectedRows,
                ambiguousRanks = detection.ambiguousRanks,
                lattice = lattice,
                sourceHeight = anchors.sourceHeight,
            )
            addEdgeExtrapolations(
                rowY = rowY,
                detectedRows = detectedRows,
                ambiguousRanks = detection.ambiguousRanks,
                lattice = lattice,
                sourceHeight = anchors.sourceHeight,
            )
        }

        return CustomDesignGridGeometry(
            sourceWidth = anchors.sourceWidth,
            sourceHeight = anchors.sourceHeight,
            columnX = anchors.columnX.toMap(),
            rowY = rowY,
            estimatedRowStep = lattice?.step,
        )
    }

    private fun estimateLattice(
        detectedRows: List<Map.Entry<Int, Float>>,
    ): RowLattice? {
        if (detectedRows.size < MIN_DETECTED_ROWS) return null
        if (detectedRows.zipWithNext().any { (left, right) -> right.value <= left.value }) {
            return null
        }

        val stepCandidates = buildList {
            for (leftIndex in detectedRows.indices) {
                for (rightIndex in leftIndex + 1 until detectedRows.size) {
                    val left = detectedRows[leftIndex]
                    val right = detectedRows[rightIndex]
                    val step = (right.value - left.value) / (right.key - left.key).toFloat()
                    if (step.isFinite() && step > 0f) add(step)
                }
            }
        }
        val step = stepCandidates.median()
        if (!step.isFinite() || step <= 0f) return null

        val baseCandidates = detectedRows.map { (rank, y) ->
            y - step * (rank - FIRST_RANK).toFloat()
        }
        val baseY = baseCandidates.median()
        if (!baseY.isFinite()) return null

        val maxResidual = maxOf(MIN_FIT_RESIDUAL_PX, step * MAX_FIT_RESIDUAL_RATIO)
        val reliable = detectedRows.all { (rank, actualY) ->
            val predictedY = baseY + step * (rank - FIRST_RANK).toFloat()
            predictedY.isFinite() && abs(actualY - predictedY) <= maxResidual
        }
        return RowLattice(step = step, baseY = baseY).takeIf { reliable }
    }

    private fun addInternalInterpolations(
        rowY: MutableMap<Int, CustomDesignRowCoordinate>,
        detectedRows: List<Map.Entry<Int, Float>>,
        ambiguousRanks: Set<Int>,
        lattice: RowLattice,
        sourceHeight: Int,
    ) {
        detectedRows.zipWithNext().forEach { (left, right) ->
            val missingRanks = (left.key + 1 until right.key).toList()
            if (
                missingRanks.isEmpty() ||
                missingRanks.size > MAX_INTERNAL_MISSING_RANKS ||
                missingRanks.any { it in ambiguousRanks }
            ) {
                return@forEach
            }
            missingRanks.forEach { rank ->
                val predictedY = lattice.predict(rank)
                if (predictedY.isWithinSource(sourceHeight)) {
                    rowY.putIfAbsent(
                        rank,
                        CustomDesignRowCoordinate(
                            y = predictedY,
                            source = CustomDesignRowCoordinateSource.INTERPOLATED,
                        ),
                    )
                }
            }
        }
    }

    private fun addEdgeExtrapolations(
        rowY: MutableMap<Int, CustomDesignRowCoordinate>,
        detectedRows: List<Map.Entry<Int, Float>>,
        ambiguousRanks: Set<Int>,
        lattice: RowLattice,
        sourceHeight: Int,
    ) {
        val firstRank = detectedRows.firstOrNull()?.key
        if (firstRank == FIRST_RANK + 1 && FIRST_RANK !in ambiguousRanks) {
            addPredictedRow(
                rowY = rowY,
                rank = FIRST_RANK,
                source = CustomDesignRowCoordinateSource.EXTRAPOLATED,
                lattice = lattice,
                sourceHeight = sourceHeight,
            )
        }

        val lastRank = detectedRows.lastOrNull()?.key
        if (lastRank == LAST_RANK - 1 && LAST_RANK !in ambiguousRanks) {
            addPredictedRow(
                rowY = rowY,
                rank = LAST_RANK,
                source = CustomDesignRowCoordinateSource.EXTRAPOLATED,
                lattice = lattice,
                sourceHeight = sourceHeight,
            )
        }
    }

    private fun addPredictedRow(
        rowY: MutableMap<Int, CustomDesignRowCoordinate>,
        rank: Int,
        source: CustomDesignRowCoordinateSource,
        lattice: RowLattice,
        sourceHeight: Int,
    ) {
        val predictedY = lattice.predict(rank)
        if (predictedY.isWithinSource(sourceHeight)) {
            rowY.putIfAbsent(
                rank,
                CustomDesignRowCoordinate(y = predictedY, source = source),
            )
        }
    }

    private data class RowLattice(
        val step: Float,
        val baseY: Float,
    ) {
        fun predict(rank: Int): Float = baseY + step * (rank - FIRST_RANK).toFloat()
    }

    private companion object {
        const val FIRST_RANK = 1
        const val LAST_RANK = 12
        const val MIN_DETECTED_ROWS = 3
        const val MAX_INTERNAL_MISSING_RANKS = 2
        const val MIN_FIT_RESIDUAL_PX = 3f
        const val MAX_FIT_RESIDUAL_RATIO = 0.20f
        val RANK_RANGE = FIRST_RANK..LAST_RANK
    }
}

private fun Float.isWithinSource(sourceHeight: Int): Boolean =
    isFinite() && this >= 0f && this <= sourceHeight.toFloat()

private fun List<Float>.median(): Float {
    if (isEmpty()) return Float.NaN
    val sorted = sorted()
    return if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
    }
}
