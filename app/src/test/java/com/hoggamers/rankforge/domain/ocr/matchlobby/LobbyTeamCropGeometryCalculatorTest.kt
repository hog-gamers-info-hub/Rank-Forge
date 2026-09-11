package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyTeamCropGeometryCalculatorTest {
    @Test
    fun horizontalDistanceIsTheAverageAdjacentSlotCenterSpacing() {
        val slots = validSlots(topRightCenterX = 500.0, bottomRightCenterX = 520.0)
        val crops = available(validSlots()).crops

        assertEquals(477.2, available(slots).crops[0].bounds.right, 0.0)
        assertEquals(868.0, crops[1].bounds.right, 0.0)
    }

    @Test
    fun cropRightUsesNinetyTwoPercentOfHorizontalDistanceFromTheSlotCenter() {
        val crops = available(validSlots()).crops

        assertEquals(100.0 + 400.0 * 0.92, crops[0].bounds.right, 0.0)
        assertEquals(500.0 + 400.0 * 0.92, crops[1].bounds.right, 0.0)
    }

    @Test
    fun horizontalPercentageStartsAtTheSlotNumberCenterNotTheCropLeft() {
        val crop = available(validSlots()).crops[0]

        assertEquals(468.0, crop.bounds.right, 0.0)
        assertEquals(0.0, crop.bounds.left, 0.0)
        assertEquals(468.0, crop.bounds.right - crop.bounds.left, 0.0)
    }

    @Test
    fun cropLeftStillUsesTheStableSlotLeftInset() {
        val crops = available(validSlots()).crops

        assertEquals(0.0, crops[0].bounds.left, 0.0)
        assertEquals(400.0, crops[1].bounds.left, 0.0)
    }

    @Test
    fun slotNumberRemainsVerticallyCenteredInEveryCrop() {
        val crops = available(validSlots()).crops

        assertEquals(LobbyTeamCropBounds(0.0, 0.0, 468.0, 200.0), crops[0].bounds)
        assertEquals(LobbyTeamCropBounds(0.0, 200.0, 468.0, 400.0), crops[2].bounds)
    }

    @Test
    fun allVisiblePositionsUseTheSameHorizontalDistanceRule() {
        val slots = validSlots()
        val crops = available(slots).crops

        crops.zip(slots).forEach { (crop, slot) ->
            assertEquals(368.0, crop.bounds.right - slot.slotNumberBounds.centerX, 0.0)
        }
    }

    @Test
    fun rightColumnCropSafelyClampsToThePanelRight() {
        val crops = available(validSlots(), panelWidth = 830).crops

        assertEquals(830.0, crops[1].bounds.right, 0.0)
        assertEquals(400.0, crops[1].bounds.left, 0.0)
        assertEquals(0.0, crops[1].bounds.top, 0.0)
        assertEquals(200.0, crops[1].bounds.bottom, 0.0)
    }

    @Test
    fun numberBoundingBoxRightEdgeDoesNotAffectCropWidth() {
        val baseline = available(validSlots()).crops[0].bounds.right
        val slots = validSlots().toMutableList().also {
            it[0] = it[0].copy(
                slotNumberBounds = LobbyTeamCropBounds(99.0, 95.0, 101.0, 105.0),
            )
        }

        assertEquals(baseline, available(slots).crops[0].bounds.right, 0.0)
    }

    @Test
    fun missingOneVisiblePositionIsTypedUnavailable() {
        val result = calculate(validSlots().dropLast(1))

        assertEquals(
            LobbyTeamCropUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
            (result as LobbyTeamCropGeometryResult.Unavailable).reason,
        )
    }

    @Test
    fun nonpositiveSlotNumberBoundsAreTypedUnavailable() {
        val slots = validSlots().toMutableList().also {
            it[0] = it[0].copy(slotNumberBounds = LobbyTeamCropBounds(1.0, 1.0, 1.0, 10.0))
        }

        assertEquals(
            LobbyTeamCropUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE,
            (calculate(slots) as LobbyTeamCropGeometryResult.Unavailable).reason,
        )
    }

    @Test
    fun materiallyInconsistentColumnSpacingIsTypedUnavailable() {
        val slots = validSlots().toMutableList().also {
            it[3] = it[3].copy(slotNumberBounds = bounds(750.0, 300.0))
        }

        assertEquals(
            LobbyTeamCropUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
            (calculate(slots) as LobbyTeamCropGeometryResult.Unavailable).reason,
        )
    }

    @Test
    fun smallBoundaryRoundingIsClampedButMaterialOverflowIsRejected() {
        val safelyRounded = validSlots(topCenterY = 99.0)
        assertEquals(0.0, available(safelyRounded).crops[0].bounds.top, 0.0)

        val overflow = validSlots(topCenterY = 50.0)
        val result = calculate(overflow)
        assertTrue(result is LobbyTeamCropGeometryResult.Unavailable)
        assertEquals(
            LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS,
            (result as LobbyTeamCropGeometryResult.Unavailable).reason,
        )
    }

    @Test
    fun gridGeometryWithFourValidCropsReturnsEveryCrop() {
        val result = availableGrid(validGrid())

        assertEquals(RosterVisibleSlotPosition.entries, result.crops.map { it.visibleSlotPosition })
        assertTrue(result.unavailable.isEmpty())
    }

    @Test
    fun firstInvalidGridCropDoesNotDiscardLaterValidCrops() {
        val result = availableGrid(validGrid(topLeftCenterY = 0.0))

        assertEquals(
            listOf(
                RosterVisibleSlotPosition.TOP_RIGHT,
                RosterVisibleSlotPosition.BOTTOM_LEFT,
                RosterVisibleSlotPosition.BOTTOM_RIGHT,
            ),
            result.crops.map { it.visibleSlotPosition },
        )
        assertEquals(
            listOf(
                LobbyTeamCropUnavailable(
                    visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                    detectedSlotNumber = 1,
                    reason = LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS,
                ),
            ),
            result.unavailable,
        )
    }

    @Test
    fun gridGeometryWithAllIndividualCropBoundsInvalidReturnsOnlyUnavailableOutcomes() {
        val result = availableGrid(
            allBoundsInvalidGrid(),
            panelHeight = 100,
        )

        assertTrue(result.crops.isEmpty())
        assertEquals(RosterVisibleSlotPosition.entries, result.unavailable.map { it.visibleSlotPosition })
        assertTrue(result.unavailable.all { it.reason == LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS })
    }

    private fun available(
        slots: List<LobbyTeamCropSlotGeometry>,
        panelWidth: Int = 1_000,
    ): LobbyTeamCropGeometryResult.Available = calculate(slots, panelWidth) as LobbyTeamCropGeometryResult.Available

    private fun calculate(
        slots: List<LobbyTeamCropSlotGeometry>,
        panelWidth: Int = 1_000,
    ): LobbyTeamCropGeometryResult =
        LobbyTeamCropGeometryCalculator.calculate(
            panelWidth = panelWidth,
            panelHeight = 800,
            slots = slots,
        )

    private fun availableGrid(
        grid: LobbySlotGrid,
        panelHeight: Int = 900,
    ): LobbyTeamCropGeometryResult.Available =
        LobbyTeamCropGeometryCalculator.calculate(
            panelWidth = 1_000,
            panelHeight = panelHeight,
            grid = grid,
            observedSlotLeftInsets = listOf(100.0),
        ) as LobbyTeamCropGeometryResult.Available

    private fun validGrid(
        topLeftCenterY: Double = 200.0,
        topRightCenterY: Double = 200.0,
        bottomLeftCenterY: Double = 600.0,
        bottomRightCenterY: Double = 600.0,
    ) = LobbySlotGrid(
        screenshotIndex = 1,
        points = listOf(
            gridPoint(1, LobbySlotGridRole.TOP_LEFT, 100.0, topLeftCenterY),
            gridPoint(2, LobbySlotGridRole.TOP_RIGHT, 500.0, topRightCenterY),
            gridPoint(3, LobbySlotGridRole.BOTTOM_LEFT, 100.0, bottomLeftCenterY),
            gridPoint(4, LobbySlotGridRole.BOTTOM_RIGHT, 500.0, bottomRightCenterY),
        ),
        topRowCenterY = 200.0,
        bottomRowCenterY = 600.0,
        leftColumnCenterX = 100.0,
        rightColumnCenterX = 500.0,
        rowPitch = 400.0,
        columnPitch = 400.0,
        topRowAlignmentError = 0.0,
        bottomRowAlignmentError = 0.0,
        leftColumnAlignmentError = 0.0,
        rightColumnAlignmentError = 0.0,
    )

    private fun allBoundsInvalidGrid() = LobbySlotGrid(
        screenshotIndex = 1,
        points = listOf(
            gridPoint(1, LobbySlotGridRole.TOP_LEFT, 100.0, 0.0),
            gridPoint(2, LobbySlotGridRole.TOP_RIGHT, 500.0, 0.0),
            gridPoint(3, LobbySlotGridRole.BOTTOM_LEFT, 100.0, 100.0),
            gridPoint(4, LobbySlotGridRole.BOTTOM_RIGHT, 500.0, 100.0),
        ),
        topRowCenterY = 0.0,
        bottomRowCenterY = 100.0,
        leftColumnCenterX = 100.0,
        rightColumnCenterX = 500.0,
        rowPitch = 100.0,
        columnPitch = 400.0,
        topRowAlignmentError = 0.0,
        bottomRowAlignmentError = 0.0,
        leftColumnAlignmentError = 0.0,
        rightColumnAlignmentError = 0.0,
    )

    private fun gridPoint(
        slotNumber: Int,
        role: LobbySlotGridRole,
        centerX: Double,
        centerY: Double,
    ) = LobbyGridPoint(
        slotNumber = slotNumber,
        role = role,
        centerX = centerX,
        centerY = centerY,
        source = LobbyGridPointSource.OBSERVED,
    )

    private fun validSlots(
        topCenterY: Double = 100.0,
        topRightCenterX: Double = 500.0,
        bottomRightCenterX: Double = 500.0,
    ): List<LobbyTeamCropSlotGeometry> = listOf(
        slot(RosterVisibleSlotPosition.TOP_LEFT, 4, 100.0, topCenterY),
        slot(RosterVisibleSlotPosition.TOP_RIGHT, 10, topRightCenterX, topCenterY),
        slot(RosterVisibleSlotPosition.BOTTOM_LEFT, 3, 100.0, topCenterY + 200.0),
        slot(RosterVisibleSlotPosition.BOTTOM_RIGHT, 2, bottomRightCenterX, topCenterY + 200.0),
    )

    private fun slot(
        position: RosterVisibleSlotPosition,
        number: Int,
        centerX: Double,
        centerY: Double,
    ) = LobbyTeamCropSlotGeometry(
        visibleSlotPosition = position,
        detectedSlotNumber = number,
        slotNumberBounds = bounds(centerX, centerY),
    )

    private fun bounds(centerX: Double, centerY: Double) = LobbyTeamCropBounds(
        left = centerX - 5.0,
        top = centerY - 5.0,
        right = centerX + 5.0,
        bottom = centerY + 5.0,
    )
}
