package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect

data class MatchResultPositionRowCrop(
    val rowIndex: Int,
    val bounds: OcrPixelCropRect,
) {
    init {
        require(rowIndex in 1..2) { "Result position row must be 1 or 2." }
    }
}
