package com.hoggamers.rankforge.domain.ocr.customdesign

enum class CustomDesignRowCoordinateSource {
    OCR,
    INTERPOLATED,
    EXTRAPOLATED,
}

data class CustomDesignRowCoordinate(
    val y: Float,
    val source: CustomDesignRowCoordinateSource,
)

data class CustomDesignGridPoint(
    val x: Float,
    val y: Float,
)

data class CustomDesignGridGeometry(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columnX: Map<CustomDesignAnchorField, Float>,
    val rowY: Map<Int, CustomDesignRowCoordinate>,
    val estimatedRowStep: Float?,
) {
    val hasAllColumns: Boolean
        get() = columnX.keys.containsAll(CustomDesignAnchorField.entries)

    val hasAllRows: Boolean
        get() = rowY.keys.containsAll((1..12).toSet())

    fun cellCenter(
        field: CustomDesignAnchorField,
        rank: Int,
    ): CustomDesignGridPoint? {
        val x = columnX[field] ?: return null
        val y = rowY[rank]?.y ?: return null
        return CustomDesignGridPoint(x = x, y = y)
    }
}
