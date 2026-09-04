package com.hoggamers.rankforge.domain.ocr.customdesign

data class CustomDesignEffectiveGridGeometry(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columnX: Map<CustomDesignAnchorField, Float>,
    val rowY: Map<Int, Float>,
)

fun resolveCustomDesignEffectiveGridGeometry(
    automatic: CustomDesignGridGeometry?,
    overrides: CustomDesignGridOverrides,
): CustomDesignEffectiveGridGeometry? = automatic?.let { geometry ->
    CustomDesignEffectiveGridGeometry(
        sourceWidth = geometry.sourceWidth,
        sourceHeight = geometry.sourceHeight,
        columnX = geometry.columnX.mapValues { (field, automaticX) ->
            overrides.columnX[field] ?: automaticX
        },
        rowY = geometry.rowY.mapValues { (rank, automaticRow) ->
            overrides.rowY[rank] ?: automaticRow.y
        },
    )
}
