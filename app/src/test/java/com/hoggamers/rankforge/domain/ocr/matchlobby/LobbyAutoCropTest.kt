package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyAutoCropTest {
    private val calculator = LobbyAutoCropCalculator()

    @Test
    fun calibratedIdealGridUsesSuppliedFormula() {
        val calibration = LobbyCropCalibration(
            top = 0.1,
            bottom = 0.2,
            left = 0.3,
            right = 0.4,
        )

        val crop = proposal(
            grid = grid(
                topRowCenterY = 100.0,
                bottomRowCenterY = 200.0,
                leftColumnCenterX = 100.0,
                rightColumnCenterX = 300.0,
                rowPitch = 100.0,
                columnPitch = 200.0,
            ),
            width = 400,
            height = 400,
            calibration = calibration,
        )

        assertPixels(crop, 40.0, 90.0, 380.0, 220.0, 1.0e-9, 400, 400)
    }

    @Test
    fun realShotOneRegressionUsesInitialSafeLa03bCalibration() {
        val crop = proposal(
            grid = reconstructed(
                screenshotIndex = 1,
                anchors = listOf(
                    anchor(1, 584.0, 231.0),
                    anchor(2, 1_075.5, 231.5),
                    anchor(4, 1_075.5, 436.5),
                ),
            ),
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )

        assertPixels(crop, 547.638830, 120.155974, 1_526.626716, 527.029412, 1.5)
    }

    @Test
    fun realShotTwoRegressionUsesInitialSafeLa03bCalibration() {
        val crop = proposal(
            grid = reconstructed(
                screenshotIndex = 2,
                anchors = listOf(
                    anchor(5, 585.0, 244.5),
                    anchor(7, 584.5, 450.5),
                    anchor(8, 1_075.5, 450.5),
                ),
            ),
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )

        assertPixels(crop, 548.444315, 133.000028, 1_525.938323, 541.360214, 1.5)
    }

    @Test
    fun realShotThreeRegressionUsesInitialSafeLa03bCalibration() {
        val crop = proposal(
            grid = gridFromAnchors(
                screenshotIndex = 3,
                listOf(
                    anchor(9, 584.5, 249.5),
                    anchor(10, 1_074.5, 249.5),
                    anchor(11, 584.0, 455.0),
                    anchor(12, 1_074.0, 455.5),
                ),
            ),
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )

        assertPixels(crop, 547.999800, 138.135343, 1_523.999930, 545.999947, 1.5)
    }

    @Test
    fun clampsAllCalculatedEdgesToImageBounds() {
        val crop = proposal(
            grid = grid(
                topRowCenterY = 10.0,
                bottomRowCenterY = 90.0,
                leftColumnCenterX = 10.0,
                rightColumnCenterX = 90.0,
                rowPitch = 80.0,
                columnPitch = 80.0,
            ),
            width = 100,
            height = 100,
            calibration = LobbyCropCalibration(
                top = 1.0,
                bottom = 1.0,
                left = 1.0,
                right = 1.0,
            ),
        )

        assertEquals(OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0), crop)
    }

    @Test
    fun invalidImageDimensionsReturnExplicitResult() {
        val grid = grid()
        val calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian

        assertEquals(
            LobbyAutoCropCalculationResult.InvalidImageDimensions,
            calculator.calculate(grid, 0, 720, calibration),
        )
        assertEquals(
            LobbyAutoCropCalculationResult.InvalidImageDimensions,
            calculator.calculate(grid, 1600, 0, calibration),
        )
        assertEquals(
            LobbyAutoCropCalculationResult.InvalidImageDimensions,
            calculator.calculate(grid, -1, 720, calibration),
        )
        assertEquals(
            LobbyAutoCropCalculationResult.InvalidImageDimensions,
            calculator.calculate(grid, 1600, -1, calibration),
        )
    }

    @Test
    fun invalidGridGeometryReturnsExplicitResult() {
        val base = grid()
        val calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian
        val invalidGrids = listOf(
            base.copy(rowPitch = 0.0),
            base.copy(columnPitch = -1.0),
            base.copy(topRowCenterY = Double.NaN),
            base.copy(rightColumnCenterX = Double.POSITIVE_INFINITY),
            base.copy(points = base.points.mapIndexed { index, point ->
                if (index == 0) point.copy(centerY = Double.NaN) else point
            }),
        )

        invalidGrids.forEach { invalidGrid ->
            assertEquals(
                LobbyAutoCropCalculationResult.InvalidGridGeometry,
                calculator.calculate(invalidGrid, 1600, 720, calibration),
            )
        }
    }

    @Test
    fun invalidCalibrationReturnsExplicitResult() {
        val base = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian
        val invalidCalibrations = listOf(
            base.copy(top = -0.1),
            base.copy(bottom = Double.NaN),
            base.copy(left = Double.POSITIVE_INFINITY),
            base.copy(right = Double.NEGATIVE_INFINITY),
        )

        invalidCalibrations.forEach { invalidCalibration ->
            assertEquals(
                LobbyAutoCropCalculationResult.InvalidCalibration,
                calculator.calculate(grid(), 1600, 720, invalidCalibration),
            )
        }
    }

    @Test
    fun sameInputProducesExactlyTheSameProposal() {
        val grid = grid()
        val calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian

        val first = calculator.calculate(grid, 1600, 720, calibration)
        val second = calculator.calculate(grid, 1600, 720, calibration)

        assertEquals(first, second)
    }

    @Test
    fun proposalIsNormalizedAndWithinImageBounds() {
        val crop = proposal(
            grid = grid(),
            width = 1600,
            height = 720,
            calibration = LobbyCropCalibrationProfiles.InitialSafeLa03bMedian,
        )

        assertTrue(crop.left >= 0.0 && crop.left < crop.right && crop.right <= 1.0)
        assertTrue(crop.top >= 0.0 && crop.top < crop.bottom && crop.bottom <= 1.0)
    }

    private fun proposal(
        grid: LobbySlotGrid,
        width: Int = 1600,
        height: Int = 720,
        calibration: LobbyCropCalibration,
    ): OcrNormalizedCropRect =
        (calculator.calculate(grid, width, height, calibration) as LobbyAutoCropCalculationResult.Proposal).crop

    private fun assertPixels(
        crop: OcrNormalizedCropRect,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        tolerance: Double,
        width: Int = 1600,
        height: Int = 720,
    ) {
        assertEquals(left, crop.left * width, tolerance)
        assertEquals(top, crop.top * height, tolerance)
        assertEquals(right, crop.right * width, tolerance)
        assertEquals(bottom, crop.bottom * height, tolerance)
    }

    private fun reconstructed(
        screenshotIndex: Int,
        anchors: List<LobbyObservedSlotAnchor>,
    ): LobbySlotGrid = gridFromAnchors(screenshotIndex, anchors)

    private fun gridFromAnchors(
        screenshotIndex: Int,
        anchors: List<LobbyObservedSlotAnchor>,
    ): LobbySlotGrid =
        (LobbySlotGridReconstructor().reconstruct(screenshotIndex, anchors)
            as LobbyGridReconstructionResult.Reconstructed).grid

    private fun grid(
        topRowCenterY: Double = 100.0,
        bottomRowCenterY: Double = 200.0,
        leftColumnCenterX: Double = 100.0,
        rightColumnCenterX: Double = 300.0,
        rowPitch: Double = 100.0,
        columnPitch: Double = 200.0,
        points: List<LobbyGridPoint> = listOf(
            point(LobbySlotGridRole.TOP_LEFT, 100.0, 100.0),
            point(LobbySlotGridRole.TOP_RIGHT, 300.0, 100.0),
            point(LobbySlotGridRole.BOTTOM_LEFT, 100.0, 200.0),
            point(LobbySlotGridRole.BOTTOM_RIGHT, 300.0, 200.0),
        ),
    ) = LobbySlotGrid(
        screenshotIndex = 1,
        points = points,
        topRowCenterY = topRowCenterY,
        bottomRowCenterY = bottomRowCenterY,
        leftColumnCenterX = leftColumnCenterX,
        rightColumnCenterX = rightColumnCenterX,
        rowPitch = rowPitch,
        columnPitch = columnPitch,
        topRowAlignmentError = 0.0,
        bottomRowAlignmentError = 0.0,
        leftColumnAlignmentError = 0.0,
        rightColumnAlignmentError = 0.0,
    )

    private fun point(role: LobbySlotGridRole, x: Double, y: Double) = LobbyGridPoint(
        slotNumber = role.ordinal + 1,
        role = role,
        centerX = x,
        centerY = y,
        source = LobbyGridPointSource.OBSERVED,
    )

    private fun anchor(slotNumber: Int, centerX: Double, centerY: Double) =
        LobbyObservedSlotAnchor(slotNumber, centerX, centerY)
}
