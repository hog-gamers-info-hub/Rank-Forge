package com.hoggamers.rankforge.domain.ocr.customdesign

enum class CustomDesignEditableCoordinateSource {
    AUTOMATIC,
    ESTIMATED,
    FALLBACK,
}

data class CustomDesignEditableColumnCoordinate(
    val x: Float,
    val source: CustomDesignEditableCoordinateSource,
)

data class CustomDesignEditableRowCoordinate(
    val y: Float,
    val source: CustomDesignEditableCoordinateSource,
)

data class CustomDesignEditableGridGeometry(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columnX: Map<CustomDesignAnchorField, CustomDesignEditableColumnCoordinate>,
    val rowY: Map<Int, CustomDesignEditableRowCoordinate>,
)
