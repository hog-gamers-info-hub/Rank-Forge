package com.hoggamers.rankforge.domain.ocr.customdesign

data class CustomDesignGridOverrides(
    val columnX: Map<CustomDesignAnchorField, Float> = emptyMap(),
    val rowY: Map<Int, Float> = emptyMap(),
)
