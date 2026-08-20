package com.hoggamers.rankforge.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultLayoutSpecTest {
    @Test
    fun logicalPageUsesApprovedLandscapeDimensions() {
        assertEquals(842, ResultLayoutSpec.LOGICAL_PAGE_WIDTH)
        assertEquals(595, ResultLayoutSpec.LOGICAL_PAGE_HEIGHT)
    }

    @Test
    fun pngDimensionsAreTwoTimesLogicalPage() {
        assertEquals(1684, ResultLayoutSpec.PNG_WIDTH)
        assertEquals(1190, ResultLayoutSpec.PNG_HEIGHT)
        assertEquals(2f, ResultLayoutSpec.PNG_SCALE)
        assertEquals(
            ResultLayoutSpec.LOGICAL_PAGE_WIDTH * ResultLayoutSpec.PNG_SCALE,
            ResultLayoutSpec.PNG_WIDTH.toFloat(),
        )
        assertEquals(
            ResultLayoutSpec.LOGICAL_PAGE_HEIGHT * ResultLayoutSpec.PNG_SCALE,
            ResultLayoutSpec.PNG_HEIGHT.toFloat(),
        )
    }

    @Test
    fun columnWidthsSumToAvailableTableWidth() {
        assertEquals(
            ResultLayoutSpec.TABLE_WIDTH,
            ResultLayoutSpec.COLUMN_WIDTHS.sum(),
            0f,
        )
    }

    @Test
    fun fixedRowsAndHeaderFitInsideLogicalPage() {
        assertEquals(12, ResultLayoutSpec.RESULT_ROW_COUNT)
        assertTrue(ResultLayoutSpec.TABLE_BOTTOM <= ResultLayoutSpec.LOGICAL_PAGE_HEIGHT)
        assertTrue(
            ResultLayoutSpec.TABLE_TOP + ResultLayoutSpec.TABLE_HEADER_HEIGHT +
                ResultLayoutSpec.RESULT_ROW_COUNT * ResultLayoutSpec.RESULT_ROW_HEIGHT <=
                ResultLayoutSpec.LOGICAL_PAGE_HEIGHT,
        )
    }

    @Test
    fun allColumnBoundariesRemainInsideLogicalPage() {
        val boundaries = ResultLayoutSpec.COLUMN_BOUNDARIES

        assertEquals(7, boundaries.size)
        assertEquals(ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN, boundaries.first(), 0f)
        assertEquals(
            ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN + ResultLayoutSpec.TABLE_WIDTH,
            boundaries.last(),
            0f,
        )
        assertTrue(boundaries.all { boundary -> boundary in 0f..ResultLayoutSpec.LOGICAL_PAGE_WIDTH.toFloat() })
    }
}
