package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import kotlin.math.abs

/** The existing position-local kill-cell geometry shared by PP mapping and ML Kit recovery. */
object MatchResultKillFieldLayout {
    fun rowIndexForSlot(slot: Int): Int = if (slot == 1 || slot == 2) slot else slot - 2

    fun horizontalRange(position: Int, cropWidth: Int, firstPlayerColumn: Boolean): ClosedRange<Double> {
        val (leftFraction, rightFraction) = when {
            position <= 5 && firstPlayerColumn -> 0.34 to 0.56
            position <= 5 -> 0.80 to 1.0
            firstPlayerColumn -> 0.40 to 0.81
            else -> 0.81 to 1.0
        }
        return leftFraction * cropWidth..rightFraction * cropWidth
    }

    fun bounds(
        position: Int,
        cropWidth: Int,
        rowBounds: OcrPixelCropRect?,
        firstPlayerColumn: Boolean,
    ): MatchResultOcrRect {
        val horizontalRange = horizontalRange(position, cropWidth, firstPlayerColumn)
        return MatchResultOcrRect(
            left = horizontalRange.start,
            top = rowBounds?.top?.toDouble() ?: 0.0,
            right = horizontalRange.endInclusive,
            bottom = rowBounds?.bottom?.toDouble() ?: 1.0,
        )
    }

    fun isLocallyNearStandaloneKillAnchor(
        candidateBounds: RawOcrBoundingBox,
        anchorBounds: RawOcrBoundingBox,
    ): Boolean {
        val anchorHeight = (anchorBounds.bottom - anchorBounds.top).toDouble()
        val localRadiusPx = (1.5 * anchorHeight).coerceIn(12.0, 28.0)
        val candidateCenterX = (candidateBounds.left + candidateBounds.right) / 2.0
        return abs(candidateCenterX - anchorBounds.left) <= localRadiusPx
    }
}
