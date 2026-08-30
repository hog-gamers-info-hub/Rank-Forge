package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

enum class MatchResultPpInputMode {
    FULL_PANEL,
    LOWER_POSITION_ROI,
}

data class MatchResultPpInputPlan(
    val mode: MatchResultPpInputMode,
    val bounds: OcrPixelCropRect,
    val crops: List<MatchResultPositionCrop>,
)

/** Plans the PP input bounds without owning or creating Android bitmaps. */
object MatchResultPpInputPlanner {
    fun plan(
        role: MatchResultScreenshotRole,
        sourceWidth: Int,
        sourceHeight: Int,
        crops: List<MatchResultPositionCrop>,
    ): MatchResultPpInputPlan? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val orderedCrops = crops.sortedWith(
            compareBy<MatchResultPositionCrop> { it.position }.thenBy { it.column },
        )
        if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
            val fullBounds = OcrPixelCropRect(0, 0, sourceWidth, sourceHeight)
            return MatchResultPpInputPlan(
                mode = MatchResultPpInputMode.FULL_PANEL,
                bounds = fullBounds,
                crops = orderedCrops,
            )
        }

        if (orderedCrops.isEmpty() || orderedCrops.any { !it.bounds.isWithin(sourceWidth, sourceHeight) }) {
            return null
        }
        val unpaddedBounds = OcrPixelCropRect(
            left = orderedCrops.minOf { it.bounds.left },
            top = orderedCrops.minOf { it.bounds.top },
            right = orderedCrops.maxOf { it.bounds.right },
            bottom = orderedCrops.maxOf { it.bounds.bottom },
        )
        val paddingPx = maxOf(1, orderedCrops.minOf { it.bounds.height })
        val roi = OcrPixelCropRect(
            left = maxOf(0, unpaddedBounds.left - paddingPx),
            top = maxOf(0, unpaddedBounds.top - paddingPx),
            right = minOf(sourceWidth, unpaddedBounds.right + paddingPx),
            bottom = minOf(sourceHeight, unpaddedBounds.bottom + paddingPx),
        )
        val localCrops = orderedCrops.map { crop ->
            crop.copy(
                bounds = OcrPixelCropRect(
                    left = crop.bounds.left - roi.left,
                    top = crop.bounds.top - roi.top,
                    right = crop.bounds.right - roi.left,
                    bottom = crop.bounds.bottom - roi.top,
                ),
                structuralCenterYInSource = crop.structuralCenterYInSource?.minus(roi.top),
            )
        }
        if (localCrops.any { !it.bounds.isWithin(roi.width, roi.height) }) return null
        return MatchResultPpInputPlan(
            mode = MatchResultPpInputMode.LOWER_POSITION_ROI,
            bounds = roi,
            crops = localCrops,
        )
    }

    private fun OcrPixelCropRect.isWithin(width: Int, height: Int): Boolean =
        right > left && bottom > top &&
            left >= 0 && top >= 0 && right <= width && bottom <= height
}
