package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import kotlin.math.ceil

data class MatchResultPositionRowCropObservation(
    val text: String,
    val boundingBox: RawOcrBoundingBox,
)

data class MatchResultPositionRowCrop(
    val rowIndex: Int,
    val bounds: OcrPixelCropRect,
) {
    init {
        require(rowIndex in 1..2) { "Result position row must be 1 or 2." }
    }
}

sealed interface MatchResultPositionRowCropCalculationResult {
    data class Available(
        val crops: List<MatchResultPositionRowCrop>,
    ) : MatchResultPositionRowCropCalculationResult

    data object Unavailable : MatchResultPositionRowCropCalculationResult
}

class MatchResultPositionRowCropCalculator {
    fun calculate(
        position: Int,
        imageWidth: Int,
        imageHeight: Int,
        observations: List<MatchResultPositionRowCropObservation>,
    ): MatchResultPositionRowCropCalculationResult {
        val minBoxHeightPx = if (imageHeight > 0) {
            maxOf(2, ceil(imageHeight * MIN_BOX_HEIGHT_FRACTION).toInt())
        } else {
            0
        }
        val rowVerticalPaddingPx = if (imageHeight > 0) {
            maxOf(1, ceil(imageHeight * ROW_VERTICAL_PADDING_FRACTION).toInt())
        } else {
            0
        }
        if (position !in 1..12 || imageWidth <= 0 || imageHeight <= 0) {
            return MatchResultPositionRowCropCalculationResult.Unavailable
        }

        var placementCenterY: Double? = null
        val validBoxes = observations.mapIndexedNotNull { index, observation ->
            val clamped = observation.boundingBox.clampTo(imageWidth, imageHeight)
            if (clamped.right <= clamped.left || clamped.bottom <= clamped.top) {
                return@mapIndexedNotNull null
            }
            if (
                observation.text.trim() == position.toString() &&
                clamped.left < imageWidth * PLACEMENT_NUMBER_EXCLUSION_FRACTION
            ) {
                if (placementCenterY == null) placementCenterY = clamped.centerY()
                return@mapIndexedNotNull null
            }
            if (clamped.height() < minBoxHeightPx) {
                return@mapIndexedNotNull null
            }
            IndexedBox(index, clamped)
        }.sortedWith(compareBy<IndexedBox>({ it.box.centerY() }, { it.box.top }, { it.box.left }, { it.index }))
        if (validBoxes.isEmpty()) {
            return MatchResultPositionRowCropCalculationResult.Unavailable
        }
        val sortedCenterY = validBoxes.map { it.box.centerY() }
        val adjacentCenterGaps = sortedCenterY.zipWithNext { first, second -> second - first }
        val centerGapSplitThreshold = validBoxes.map { it.box.height().toDouble() }.medianOrNull()
            ?: return MatchResultPositionRowCropCalculationResult.Unavailable
        val largestGapIndex = adjacentCenterGaps.indices.maxWithOrNull(
            compareBy<Int>({ adjacentCenterGaps[it] }, { -it }),
        )
        val selectedSplitIndex = when {
            validBoxes.size == 1 -> null
            placementCenterY != null -> adjacentCenterGaps.indices
                .filter { index ->
                    val separator = requireNotNull(placementCenterY)
                    sortedCenterY[index] < separator &&
                        sortedCenterY[index + 1] > separator &&
                        adjacentCenterGaps[index] > centerGapSplitThreshold
                }
                .maxWithOrNull(compareBy<Int>({ adjacentCenterGaps[it] }, { -it }))
            else -> largestGapIndex?.takeIf { adjacentCenterGaps[it] > centerGapSplitThreshold }
        }
        val clusters = if (selectedSplitIndex == null) {
            listOf(validBoxes.toMutableList())
        } else {
            listOf(
                validBoxes.take(selectedSplitIndex + 1).toMutableList(),
                validBoxes.drop(selectedSplitIndex + 1).toMutableList(),
            )
        }
        val crops = clusters.mapIndexed { index, cluster ->
            MatchResultPositionRowCrop(
                rowIndex = index + 1,
                bounds = OcrPixelCropRect(
                    left = 0,
                    top = (cluster.minOf { it.box.top } - rowVerticalPaddingPx).coerceIn(0, imageHeight),
                    right = imageWidth,
                    bottom = (cluster.maxOf { it.box.bottom } + rowVerticalPaddingPx).coerceIn(0, imageHeight),
                ),
            )
        }
        val validCrops = crops.filter { crop -> crop.bounds.bottom > crop.bounds.top }
        return if (validCrops.isEmpty()) {
            MatchResultPositionRowCropCalculationResult.Unavailable
        } else {
            MatchResultPositionRowCropCalculationResult.Available(validCrops)
        }
    }

    private data class IndexedBox(val index: Int, val box: RawOcrBoundingBox)

    private companion object {
        const val PLACEMENT_NUMBER_EXCLUSION_FRACTION = 0.20
        const val MIN_BOX_HEIGHT_FRACTION = 0.01
        const val ROW_VERTICAL_PADDING_FRACTION = 0.02
    }
}

private fun RawOcrBoundingBox.clampTo(width: Int, height: Int): RawOcrBoundingBox = RawOcrBoundingBox(
    left = left.coerceIn(0, width),
    top = top.coerceIn(0, height),
    right = right.coerceIn(0, width),
    bottom = bottom.coerceIn(0, height),
)

private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0

private fun RawOcrBoundingBox.height(): Int = bottom - top

private fun List<Double>.medianOrNull(): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    }
}
