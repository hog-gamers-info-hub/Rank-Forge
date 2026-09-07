package com.hoggamers.rankforge.domain.ocr.customdesign

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomDesignRankingBoundingBoxMetricsTest {
    @Test
    fun averageUsesAcceptedSourceImageBoundingBoxHeights() {
        val average = averageRankingBoundingBoxHeightPx(
            listOf(
                box(30),
                box(32),
                box(31),
                box(29),
            ),
        )

        assertEquals(30.5f, average!!, 0f)
    }

    @Test
    fun singleAcceptedBoxUsesItsHeight() {
        assertEquals(42f, averageRankingBoundingBoxHeightPx(listOf(box(42)))!!, 0f)
    }

    @Test
    fun noAcceptedBoxesHaveNoAverage() {
        assertNull(averageRankingBoundingBoxHeightPx(emptyList()))
    }

    private fun box(height: Int) = RawOcrBoundingBox(100, 200, 120, 200 + height)
}
