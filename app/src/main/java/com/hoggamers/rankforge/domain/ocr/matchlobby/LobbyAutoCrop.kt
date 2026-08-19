package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect

data class LobbyCropCalibration(
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
)

object LobbyCropCalibrationProfiles {
    /**
     * Initial safe LA-03B median from Shot 2 and Shot 3 exact variants only.
     * Shot 1 is excluded because its anchor recovery used provisional horizontal geometry;
     * independent-match validation remains pending.
     */
    val InitialSafeLa03bMedian = LobbyCropCalibration(
        top = 0.541262,
        bottom = 0.441069,
        left = 0.073980,
        right = 0.917857,
    )
}

sealed interface LobbyAutoCropCalculationResult {
    data class Proposal(
        val crop: OcrNormalizedCropRect,
    ) : LobbyAutoCropCalculationResult

    data object InvalidImageDimensions : LobbyAutoCropCalculationResult
    data object InvalidGridGeometry : LobbyAutoCropCalculationResult
    data object InvalidCalibration : LobbyAutoCropCalculationResult
}

class LobbyAutoCropCalculator {
    fun calculate(
        grid: LobbySlotGrid,
        imageWidth: Int,
        imageHeight: Int,
        calibration: LobbyCropCalibration,
    ): LobbyAutoCropCalculationResult {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return LobbyAutoCropCalculationResult.InvalidImageDimensions
        }
        if (!calibration.isValid()) {
            return LobbyAutoCropCalculationResult.InvalidCalibration
        }
        if (!grid.hasValidGeometry()) {
            return LobbyAutoCropCalculationResult.InvalidGridGeometry
        }

        val width = imageWidth.toDouble()
        val height = imageHeight.toDouble()
        val cropTopPx = grid.topRowCenterY - calibration.top * grid.rowPitch
        val cropBottomPx = grid.bottomRowCenterY + calibration.bottom * grid.rowPitch
        val cropLeftPx = grid.leftColumnCenterX - calibration.left * grid.columnPitch
        val cropRightPx = grid.rightColumnCenterX + calibration.right * grid.columnPitch

        val leftPx = cropLeftPx.coerceIn(0.0, width)
        val topPx = cropTopPx.coerceIn(0.0, height)
        val rightPx = cropRightPx.coerceIn(0.0, width)
        val bottomPx = cropBottomPx.coerceIn(0.0, height)
        if (!(leftPx < rightPx && topPx < bottomPx)) {
            return LobbyAutoCropCalculationResult.InvalidGridGeometry
        }

        return LobbyAutoCropCalculationResult.Proposal(
            crop = OcrNormalizedCropRect(
                left = leftPx / width,
                top = topPx / height,
                right = rightPx / width,
                bottom = bottomPx / height,
            ),
        )
    }

    private fun LobbyCropCalibration.isValid(): Boolean =
        listOf(top, bottom, left, right).all { it.isFinite() && it >= 0.0 }

    private fun LobbySlotGrid.hasValidGeometry(): Boolean =
        listOf(
            topRowCenterY,
            bottomRowCenterY,
            leftColumnCenterX,
            rightColumnCenterX,
            rowPitch,
            columnPitch,
        ).all { it.isFinite() } &&
            rowPitch > 0.0 &&
            columnPitch > 0.0 &&
            points.all { it.centerX.isFinite() && it.centerY.isFinite() }
}
