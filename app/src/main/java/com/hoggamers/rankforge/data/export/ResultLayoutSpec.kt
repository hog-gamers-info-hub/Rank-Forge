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
    const val TABLE_TOP = 140f

    const val TITLE_BASELINE = 48f
    const val TOURNAMENT_BASELINE = 78f
    const val SUBTITLE_BASELINE = 104f

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
}
