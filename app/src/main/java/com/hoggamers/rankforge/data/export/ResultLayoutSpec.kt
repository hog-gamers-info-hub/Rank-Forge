package com.hoggamers.rankforge.data.export

object ResultLayoutSpec {
    const val LOGICAL_PAGE_WIDTH = 842
    const val LOGICAL_PAGE_HEIGHT = 595
    const val PNG_SCALE = 2f
    const val PNG_WIDTH = 1684
    const val PNG_HEIGHT = 1190

    const val OUTER_HORIZONTAL_MARGIN = 32f
    const val TABLE_WIDTH = 778f
    const val TABLE_HEADER_HEIGHT = 30f
    const val RESULT_ROW_HEIGHT = 30f
    const val RESULT_ROW_COUNT = 12
    const val TABLE_TOP = 122f
    const val OVERALL_IMAGE_GROUP_ROW_COUNT_3 = 18
    const val OVERALL_IMAGE_GROUP_ROW_COUNT_4 = 24

    const val TITLE_BASELINE = 48f
    const val STAGE_BASELINE = 78f
    const val SUBTITLE_BASELINE = 104f
    const val SUBTITLE_WITHOUT_STAGE_BASELINE = 104f
    const val FOOTER_BASELINE = 575f

    private const val FOOTER_AFTER_TABLE_GAP = 63f
    private const val FOOTER_BOTTOM_PADDING = 20f

    val COLUMN_WIDTHS = listOf(
        60f,
        298f,
        60f,
        110f,
        120f,
        130f,
    )

    val COLUMN_BOUNDARIES: List<Float>
        get() = buildList {
            var boundary = OUTER_HORIZONTAL_MARGIN
            add(boundary)
            COLUMN_WIDTHS.forEach { width ->
                boundary += width
                add(boundary)
            }
        }

    val TABLE_BOTTOM: Float
        get() = TABLE_TOP + TABLE_HEADER_HEIGHT + RESULT_ROW_COUNT * RESULT_ROW_HEIGHT

    fun legacyLayoutForRowCount(rowCount: Int): ResultRenderLayout? =
        rowCount.takeIf { it in 1..RESULT_ROW_COUNT }?.let { count ->
            ResultRenderLayout(
                logicalPageHeight = LOGICAL_PAGE_HEIGHT,
                pngHeight = PNG_HEIGHT,
                footerBaseline = FOOTER_BASELINE,
                rowCount = count,
            )
        }

    /** Supported overall PointIQ image layouts; arbitrary row counts fail closed. */
    fun overallImageLayoutForRowCount(rowCount: Int): ResultRenderLayout? = when (rowCount) {
        RESULT_ROW_COUNT -> legacyLayoutForRowCount(rowCount)
        OVERALL_IMAGE_GROUP_ROW_COUNT_3,
        OVERALL_IMAGE_GROUP_ROW_COUNT_4,
        -> {
            val tableBottom = TABLE_TOP + TABLE_HEADER_HEIGHT + rowCount * RESULT_ROW_HEIGHT
            val footerBaseline = tableBottom + FOOTER_AFTER_TABLE_GAP
            val logicalPageHeight = (footerBaseline + FOOTER_BOTTOM_PADDING).toInt()
            ResultRenderLayout(
                logicalPageHeight = logicalPageHeight,
                pngHeight = (logicalPageHeight * PNG_SCALE).toInt(),
                footerBaseline = footerBaseline,
                rowCount = rowCount,
            )
        }
        else -> null
    }
}

data class ResultRenderLayout(
    val logicalPageHeight: Int,
    val pngHeight: Int,
    val footerBaseline: Float,
    val rowCount: Int,
)
