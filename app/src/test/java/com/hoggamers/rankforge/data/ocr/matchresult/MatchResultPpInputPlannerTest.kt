package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPpInputPlannerTest {
    @Test
    fun lowerPaddingUsesMinimumCropHeightAndClampsToSource() {
        val plan = lowerPlan()

        assertNotNull(plan)
        assertEquals(MatchResultPpInputMode.LOWER_POSITION_ROI, plan!!.mode)
        assertEquals(OcrPixelCropRect(300, 0, 700, 400), plan.bounds)
    }

    @Test
    fun lowerCropBoundsAreTranslatedToRoiLocalCoordinates() {
        val plan = lowerPlan()!!

        assertEquals(OcrPixelCropRect(100, 100, 300, 200), plan.crops[0].bounds)
        assertEquals(OcrPixelCropRect(100, 200, 300, 300), plan.crops[1].bounds)
    }

    @Test
    fun lowerStructuralCenterIsTranslatedByRoiTop() {
        val plan = lowerPlan()!!

        assertEquals(150.0, plan.crops[0].structuralCenterYInSource!!, 0.001)
        assertEquals(250.0, plan.crops[1].structuralCenterYInSource!!, 0.001)
    }

    @Test
    fun lowerPreservesPositionAndColumn() {
        val plan = lowerPlan()!!

        assertEquals(listOf(11, 12), plan.crops.map { it.position })
        assertTrue(plan.crops.all { it.column == MatchResultPositionColumn.RIGHT })
    }

    @Test
    fun lowerLocalCropsAreContainedInsideRoiDimensions() {
        val plan = lowerPlan()!!

        assertTrue(plan.crops.all { crop ->
            crop.bounds.left >= 0 &&
                crop.bounds.top >= 0 &&
                crop.bounds.right <= plan.bounds.width &&
                crop.bounds.bottom <= plan.bounds.height
        })
    }

    @Test
    fun lowerPaddingClampsAtSourceLeftAndTop() {
        val plan = MatchResultPpInputPlanner.plan(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            sourceWidth = 500,
            sourceHeight = 400,
            crops = listOf(
                crop(11, 0, 0, 100, 100, 50.0),
                crop(12, 0, 100, 100, 200, 150.0),
            ),
        )!!

        assertEquals(OcrPixelCropRect(0, 0, 200, 300), plan.bounds)
    }

    @Test
    fun lowerPaddingClampsAtSourceRightAndBottom() {
        val plan = MatchResultPpInputPlanner.plan(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            sourceWidth = 500,
            sourceHeight = 400,
            crops = listOf(
                crop(11, 400, 300, 500, 350, 325.0),
                crop(12, 400, 350, 500, 400, 375.0),
            ),
        )!!

        assertEquals(OcrPixelCropRect(350, 250, 500, 400), plan.bounds)
    }

    @Test
    fun outOfSourceLowerUnionIsRejected() {
        val plan = MatchResultPpInputPlanner.plan(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            sourceWidth = 1000,
            sourceHeight = 500,
            crops = listOf(crop(11, 400, 100, 1100, 200, 150.0)),
        )

        assertNull(plan)
    }

    @Test
    fun emptyLowerCropsAreRejected() {
        val plan = MatchResultPpInputPlanner.plan(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            sourceWidth = 1000,
            sourceHeight = 500,
            crops = emptyList(),
        )

        assertNull(plan)
    }

    @Test
    fun upperUsesFullPanelWithoutShiftingCropGeometry() {
        val crops = listOf(
            crop(2, 400, 200, 600, 300, 250.0),
            crop(1, 100, 0, 300, 100, 50.0),
        )
        val plan = MatchResultPpInputPlanner.plan(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            sourceWidth = 1000,
            sourceHeight = 500,
            crops = crops,
        )!!

        assertEquals(MatchResultPpInputMode.FULL_PANEL, plan.mode)
        assertEquals(OcrPixelCropRect(0, 0, 1000, 500), plan.bounds)
        assertEquals(crops.sortedBy { it.position }, plan.crops)
    }

    @Test
    fun cropOrderingIsDeterministic() {
        val plan = MatchResultPpInputPlanner.plan(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            sourceWidth = 1000,
            sourceHeight = 500,
            crops = listOf(
                crop(12, 400, 200, 600, 300, 250.0),
                crop(11, 400, 100, 600, 200, 150.0),
            ),
        )!!

        assertEquals(listOf(11, 12), plan.crops.map { it.position })
    }

    private fun lowerPlan(): MatchResultPpInputPlan? = MatchResultPpInputPlanner.plan(
        role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        sourceWidth = 1000,
        sourceHeight = 500,
        crops = listOf(
            crop(12, 400, 200, 600, 300, 250.0),
            crop(11, 400, 100, 600, 200, 150.0),
        ),
    )

    private fun crop(
        position: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        centerY: Double,
    ) = MatchResultPositionCrop(
        position = position,
        column = MatchResultPositionColumn.RIGHT,
        bounds = OcrPixelCropRect(left, top, right, bottom),
        structuralCenterYInSource = centerY,
    )
}
