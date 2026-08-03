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
    fun playerRowsExactlyCoverEachSlotWithApprovedRelativeGeometry() {
        layout.slots.forEach { slot ->
            val rows = slot.playerRowRegions
            assertEquals((1..4).toList(), rows.map { it.rowIndex })

            rows.forEachIndexed { index, row ->
                val relativeX = (row.rect.x - slot.contentRect.x) / slot.contentRect.width
                val relativeY = (row.rect.y - slot.contentRect.y) / slot.contentRect.height
                val relativeWidth = row.rect.width / slot.contentRect.width
                val relativeHeight = row.rect.height / slot.contentRect.height

                assertEquals(0.15, relativeX, DOUBLE_EPSILON)
                assertEquals(index * 0.25, relativeY, DOUBLE_EPSILON)
                assertEquals(0.85, relativeWidth, DOUBLE_EPSILON)
                assertEquals(0.25, relativeHeight, DOUBLE_EPSILON)

                if (index > 0) {
                    val previous = rows[index - 1].rect
                    assertEquals(previous.y + previous.height, row.rect.y, DOUBLE_EPSILON)
                }
            }

            assertEquals(slot.contentRect.y, rows.first().rect.y, DOUBLE_EPSILON)
            assertEquals(
                slot.contentRect.y + slot.contentRect.height,
                rows.last().rect.y + rows.last().rect.height,
                DOUBLE_EPSILON,
            )
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
    fun missingScreenshotPositionIsRejected() {
        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.UNSUPPORTED_SCREENSHOT_POSITION,
            ),
            validator.validate(
                layout,
                CroppedRosterPanelInput(
                    screenshotPosition = null,
                    isPreparedRosterCrop = true,
                    imageWidth = 800,
                    imageHeight = 600,
                ),
            ),
        )
    }

    @Test
    fun invalidVisibleSlotStructureIsRejected() {
        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.INVALID_VISIBLE_SLOT_STRUCTURE,
            ),
            validator.validate(
                layout.copy(slots = layout.slots.drop(1)),
                CroppedRosterPanelInput(RosterScreenshotPosition.ONE, true, 800, 600),
            ),
        )
    }

    @Test
    fun overlappingSlotContentRegionsAreRejected() {
        val firstContentRect = layout.slots.first().contentRect
        val overlappingLayout = layout.copy(
            slots = layout.slots.mapIndexed { index, slot ->
                if (index == 1) slot.copy(contentRect = firstContentRect) else slot
            },
        )

        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.OVERLAPPING_SLOT_CONTENT_REGIONS,
            ),
            validator.validate(
                overlappingLayout,
                CroppedRosterPanelInput(RosterScreenshotPosition.ONE, true, 800, 600),
            ),
        )
    }

    @Test
    fun slotNumberRegionOutsideSlotIsRejected() {
        val invalidLayout = layout.copy(
            slots = layout.slots.mapIndexed { index, slot ->
                if (index == 0) {
                    slot.copy(slotNumberRect = NormalizedOcrRect(0.51, 0.01, 0.01, 0.01))
                } else {
                    slot
                }
            },
        )

        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.SLOT_NUMBER_REGION_OUTSIDE_SLOT,
            ),
            validator.validate(
                invalidLayout,
                CroppedRosterPanelInput(RosterScreenshotPosition.ONE, true, 800, 600),
            ),
        )
    }

    @Test
    fun invalidPlayerRowIndexesAreRejected() {
        val firstSlot = layout.slots.first()
        val invalidLayout = layout.copy(
            slots = layout.slots.mapIndexed { index, slot ->
                if (index == 0) {
                    slot.copy(
                        playerRowRegions = firstSlot.playerRowRegions.mapIndexed { rowIndex, row ->
                            if (rowIndex == 0) row.copy(rowIndex = 2) else row
                        },
                    )
                } else {
                    slot
                }
            },
        )

        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.INVALID_PLAYER_ROW_STRUCTURE,
            ),
            validator.validate(
                invalidLayout,
                CroppedRosterPanelInput(RosterScreenshotPosition.ONE, true, 800, 600),
            ),
        )
    }

    @Test
    fun playerRowOutsideSlotIsRejected() {
        val firstSlot = layout.slots.first()
        val invalidLayout = layout.copy(
            slots = layout.slots.mapIndexed { index, slot ->
                if (index == 0) {
                    slot.copy(
                        playerRowRegions = firstSlot.playerRowRegions.mapIndexed { rowIndex, row ->
                            if (rowIndex == 0) {
                                row.copy(rect = NormalizedOcrRect(0.51, 0.01, 0.01, 0.01))
                            } else {
                                row
                            }
                        },
                    )
                } else {
                    slot
                }
            },
        )

        assertEquals(
            CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.PLAYER_ROW_REGION_OUTSIDE_SLOT,
            ),
            validator.validate(
                invalidLayout,
                CroppedRosterPanelInput(RosterScreenshotPosition.ONE, true, 800, 600),
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

    private companion object {
        const val DOUBLE_EPSILON = 1.0e-9
    }
}
