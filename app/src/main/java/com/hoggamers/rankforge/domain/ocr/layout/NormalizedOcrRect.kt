package com.hoggamers.rankforge.domain.ocr.layout

import kotlin.math.roundToInt

data class NormalizedOcrRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    init {
        require(x in 0.0..1.0) { "Normalized x must be within 0.0..1.0." }
        require(y in 0.0..1.0) { "Normalized y must be within 0.0..1.0." }
        require(width > 0.0 && width <= 1.0) { "Normalized width must be within (0.0..1.0]." }
        require(height > 0.0 && height <= 1.0) { "Normalized height must be within (0.0..1.0]." }
        require(x + width <= 1.0) { "Normalized rectangle must not extend beyond the right edge." }
        require(y + height <= 1.0) { "Normalized rectangle must not extend beyond the bottom edge." }
    }

    fun toPixelRect(imageWidth: Int, imageHeight: Int): OcrPixelRect {
        require(imageWidth > 0) { "Image width must be positive." }
        require(imageHeight > 0) { "Image height must be positive." }

        val pixelX = (x * imageWidth).roundToInt().coerceIn(0, imageWidth)
        val pixelY = (y * imageHeight).roundToInt().coerceIn(0, imageHeight)
        val pixelWidth = (width * imageWidth).roundToInt().coerceIn(0, imageWidth - pixelX)
        val pixelHeight = (height * imageHeight).roundToInt().coerceIn(0, imageHeight - pixelY)

        return OcrPixelRect(
            x = pixelX,
            y = pixelY,
            width = pixelWidth,
            height = pixelHeight,
        )
    }
}
