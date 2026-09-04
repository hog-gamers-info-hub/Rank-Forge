package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.geometry.Offset

data class SourceToPreviewTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val displayedWidth: Float,
    val displayedHeight: Float,
) {
    fun mapX(sourceX: Float): Float = offsetX + sourceX * scale

    fun mapY(sourceY: Float): Float = offsetY + sourceY * scale

    fun mapPoint(sourceX: Float, sourceY: Float): Offset =
        Offset(x = mapX(sourceX), y = mapY(sourceY))

    companion object {
        fun fit(
            sourceWidth: Int,
            sourceHeight: Int,
            containerWidth: Float,
            containerHeight: Float,
        ): SourceToPreviewTransform? {
            if (
                sourceWidth <= 0 ||
                sourceHeight <= 0 ||
                !containerWidth.isFinite() ||
                !containerHeight.isFinite() ||
                containerWidth <= 0f ||
                containerHeight <= 0f
            ) {
                return null
            }

            val scale = minOf(
                containerWidth / sourceWidth.toFloat(),
                containerHeight / sourceHeight.toFloat(),
            )
            if (!scale.isFinite() || scale <= 0f) return null

            val displayedWidth = sourceWidth * scale
            val displayedHeight = sourceHeight * scale
            return SourceToPreviewTransform(
                scale = scale,
                offsetX = (containerWidth - displayedWidth) / 2f,
                offsetY = (containerHeight - displayedHeight) / 2f,
                displayedWidth = displayedWidth,
                displayedHeight = displayedHeight,
            )
        }
    }
}
