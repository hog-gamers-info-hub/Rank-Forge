package com.hoggamers.rankforge.domain.ocr.layout

data class OcrPixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0) { "Pixel x must not be negative." }
        require(y >= 0) { "Pixel y must not be negative." }
        require(width >= 0) { "Pixel width must not be negative." }
        require(height >= 0) { "Pixel height must not be negative." }
    }
}
