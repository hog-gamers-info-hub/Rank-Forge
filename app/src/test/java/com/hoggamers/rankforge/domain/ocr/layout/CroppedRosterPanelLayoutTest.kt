package com.hoggamers.rankforge.domain.ocr.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CroppedRosterPanelLayoutTest {
    private val layout = FreeFireMaxCroppedRosterPanelLayout.definition
    private val validator = CroppedRosterLayoutValidator()

    @Test
    fun fourVisibleSlotRegionsExistInTheApprovedReadingOrder() {
        assertEquals(
            listOf(
                RosterVisibleSlotPosition.TOP_LEFT,
                RosterVisibleSlotPosition.TOP_RIGHT,
                RosterVisibleSlotPosition.BOTTOM_LEFT,
                RosterVisibleSlotPosition.BOTTOM_RIGHT,
            ),
            layout.slots.map { it.visiblePosition },
        )
    }

    @Test
    fun screenshotPositionsMapToTheApprovedTournamentSlotRanges() {
        assertEquals(1..4, RosterScreenshotPosition.ONE.tournamentSlotRange)
        assertEquals(5..8, RosterScreenshotPosition.TWO.tournamentSlotRange)
        assertEquals(9..12, RosterScreenshotPosition.THREE.tournamentSlotRange)
        assertEquals(RosterScreenshotPosition.TWO, RosterScreenshotPosition.fromIndex(2))
    }

    @Test
    fun screenshotPositionAndVisiblePositionMapToEveryTournamentSlot() {
        val tournamentSlots = RosterScreenshotPosition.entries.flatMap { screenshotPosition ->
            RosterVisibleSlotPosition.entries.map { visiblePosition ->
                layout.tournamentSlotFor(screenshotPosition, visiblePosition)
            }
        }

        assertEquals((1..12).toList(), tournamentSlots)
    }

    @Test
    fun everyVisibleSlotDefinesOneSlotNumberAndFourPlayerRowRegions() {
        layout.slots.forEach { slot ->
            assertTrue(slot.slotNumberRect.isWithin(slot.contentRect))
            assertEquals((1..4).toList(), slot.playerRowRegions.map { it.rowIndex })
            assertEquals(REQUIRED_PLAYER_ROW_COUNT, slot.playerRowRegions.size)
            assertTrue(slot.playerRowRegions.all { it.rect.isWithin(slot.contentRect) })
        }
    }

    @Test
    fun layoutRegionsRemainWithinNormalizedCroppedPanelBounds() {
        val rectangles = layout.slots.flatMap { slot ->
            listOf(slot.contentRect, slot.slotNumberRect) + slot.playerRowRegions.map { it.rect }
        }

        rectangles.forEach { rect ->
            assertTrue(rect.x in 0.0..1.0)
            assertTrue(rect.y in 0.0..1.0)
            assertTrue(rect.x + rect.width <= 1.0)
            assertTrue(rect.y + rect.height <= 1.0)
        }
    }

    @Test
    fun compatiblePreparedCroppedPanelIsAccepted() {
        assertEquals(
            CroppedRosterLayoutValidationResult.Compatible,
            validator.validate(
                layout,
                CroppedRosterPanelInput(
                    screenshotPosition = RosterScreenshotPosition.ONE,
                    isPreparedRosterCrop = true,
                    imageWidth = 800,
                    imageHeight = 600,
                ),
            ),
        )
    }

    @Test
    fun invalidCroppedPanelDimensionsAreRejectedBeforePixelConversion() {
        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.INVALID_CROPPED_PANEL_DIMENSIONS,
            ),
            validator.validate(
                layout,
                CroppedRosterPanelInput(
                    screenshotPosition = RosterScreenshotPosition.ONE,
                    isPreparedRosterCrop = true,
                    imageWidth = 0,
                    imageHeight = 600,
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            layout.slots.first().contentRect.toPixelRect(imageWidth = 0, imageHeight = 600)
        }
    }

    @Test
    fun unpreparedFullScreenshotInputIsRejectedWithoutCropGuessing() {
        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.UNPREPARED_ROSTER_CROP,
            ),
            validator.validate(
                layout,
                CroppedRosterPanelInput(
                    screenshotPosition = RosterScreenshotPosition.ONE,
                    isPreparedRosterCrop = false,
                    imageWidth = 1_600,
                    imageHeight = 720,
                ),
            ),
        )
    }

    @Test
    fun fiveAndSixPlayerRowsAreUnsupported() {
        (5..6).forEach { rowCount ->
            val firstSlot = layout.slots.first()
            val unsupportedRows = (1..rowCount).map { rowIndex ->
                CroppedRosterPlayerRowRegion(
                    rowIndex = rowIndex,
                    rect = firstSlot.playerRowRegions.first().rect,
                )
            }
            val unsupportedLayout = layout.copy(
                slots = layout.slots.map { slot ->
                    if (slot.visiblePosition == RosterVisibleSlotPosition.TOP_LEFT) {
                        slot.copy(playerRowRegions = unsupportedRows)
                    } else {
                        slot
                    }
                },
            )

            assertEquals(
                CroppedRosterLayoutValidationResult.Incompatible(
                    CroppedRosterLayoutValidationError.UNSUPPORTED_PLAYER_ROW_COUNT,
                ),
                validator.validate(
                    unsupportedLayout,
                    CroppedRosterPanelInput(
                        screenshotPosition = RosterScreenshotPosition.ONE,
                        isPreparedRosterCrop = true,
                        imageWidth = 800,
                        imageHeight = 600,
                    ),
                ),
            )
        }
    }

    private fun NormalizedOcrRect.isWithin(container: NormalizedOcrRect): Boolean =
        x >= container.x &&
            y >= container.y &&
            x + width <= container.x + container.width &&
            y + height <= container.y + container.height
}
