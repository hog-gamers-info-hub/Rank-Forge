package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.hoggamers.rankforge.data.local.PointTableLogoPlacement

/**
 * Render-only data owns no bitmap lifecycle. Never expose [bitmap] to Compose or UI state; the
 * caller must recycle it after the synchronous render operation finishes.
 */
data class PointTableLogoRenderData(
    val bitmap: Bitmap,
    val placement: PointTableLogoPlacement,
)

class PointTableLogoCanvasRenderer {
    fun draw(
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        renderData: PointTableLogoRenderData?,
    ): Boolean {
        if (targetWidth <= 0 || targetHeight <= 0 || renderData == null) return false

        val bitmap = renderData.bitmap
        val placement = renderData.placement
        if (
            bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0 ||
            !placement.isValid() ||
            !isFinite(placement.centerXRatio) ||
            !isFinite(placement.centerYRatio) ||
            !isFinite(placement.widthRatio)
        ) {
            return false
        }

        val logoAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val logoWidthPx = placement.widthRatio * targetWidth.toFloat()
        val logoHeightPx = logoWidthPx / logoAspectRatio
        val centerXPx = placement.centerXRatio * targetWidth.toFloat()
        val centerYPx = placement.centerYRatio * targetHeight.toFloat()
        val left = centerXPx - logoWidthPx / 2f
        val top = centerYPx - logoHeightPx / 2f
        val right = centerXPx + logoWidthPx / 2f
        val bottom = centerYPx + logoHeightPx / 2f
        if (
            !isFinite(logoAspectRatio) || logoAspectRatio <= 0f ||
            !isFinite(logoWidthPx) || logoWidthPx <= 0f ||
            !isFinite(logoHeightPx) || logoHeightPx <= 0f ||
            !isFinite(left) || !isFinite(top) || !isFinite(right) || !isFinite(bottom)
        ) {
            return false
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, right, bottom),
            paint,
        )
        return true
    }

    private fun isFinite(value: Float): Boolean =
        !java.lang.Float.isNaN(value) && !java.lang.Float.isInfinite(value)
}
