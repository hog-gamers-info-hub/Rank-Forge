package com.hoggamers.rankforge.domain.ocr.layout

data class CroppedRosterPanelInput(
    val screenshotPosition: RosterScreenshotPosition?,
    val isPreparedRosterCrop: Boolean,
    val imageWidth: Int,
    val imageHeight: Int,
)

sealed interface CroppedRosterLayoutValidationResult {
    data object Compatible : CroppedRosterLayoutValidationResult

    data class Incompatible(
        val error: CroppedRosterLayoutValidationError,
    ) : CroppedRosterLayoutValidationResult
}

enum class CroppedRosterLayoutValidationError {
    INVALID_CROPPED_PANEL_DIMENSIONS,
    UNPREPARED_ROSTER_CROP,
    UNSUPPORTED_SCREENSHOT_POSITION,
    INVALID_VISIBLE_SLOT_STRUCTURE,
    OVERLAPPING_SLOT_CONTENT_REGIONS,
    UNSUPPORTED_PLAYER_ROW_COUNT,
    INVALID_PLAYER_ROW_STRUCTURE,
    PLAYER_ROW_REGION_OUTSIDE_SLOT,
}

class CroppedRosterLayoutValidator {
    fun validate(
        layout: CroppedRosterPanelLayout,
        input: CroppedRosterPanelInput,
    ): CroppedRosterLayoutValidationResult {
        if (input.imageWidth <= 0 || input.imageHeight <= 0) {
            return CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.INVALID_CROPPED_PANEL_DIMENSIONS,
            )
        }
        if (!input.isPreparedRosterCrop) {
            return CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.UNPREPARED_ROSTER_CROP,
            )
        }
        if (input.screenshotPosition == null) {
            return CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.UNSUPPORTED_SCREENSHOT_POSITION,
            )
        }

        val expectedPositions = RosterVisibleSlotPosition.entries.toSet()
        if (layout.slots.size != expectedPositions.size ||
            layout.slots.map { it.visiblePosition }.toSet() != expectedPositions
        ) {
            return CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.INVALID_VISIBLE_SLOT_STRUCTURE,
            )
        }
        if (layout.slots.any { slot ->
                layout.slots.any { other ->
                    slot !== other && slot.contentRect.overlaps(other.contentRect)
                }
            }
        ) {
            return CroppedRosterLayoutValidationResult.Incompatible(
                CroppedRosterLayoutValidationError.OVERLAPPING_SLOT_CONTENT_REGIONS,
            )
        }

        layout.slots.forEach { slot ->
            if (slot.playerRowRegions.size != REQUIRED_PLAYER_ROW_COUNT) {
                return CroppedRosterLayoutValidationResult.Incompatible(
                    CroppedRosterLayoutValidationError.UNSUPPORTED_PLAYER_ROW_COUNT,
                )
            }
            if (slot.playerRowRegions.map { it.rowIndex } != (1..REQUIRED_PLAYER_ROW_COUNT).toList()) {
                return CroppedRosterLayoutValidationResult.Incompatible(
                    CroppedRosterLayoutValidationError.INVALID_PLAYER_ROW_STRUCTURE,
                )
            }
            if (slot.playerRowRegions.any { row -> !row.rect.isWithin(slot.contentRect) }) {
                return CroppedRosterLayoutValidationResult.Incompatible(
                    CroppedRosterLayoutValidationError.PLAYER_ROW_REGION_OUTSIDE_SLOT,
                )
            }
        }

        return CroppedRosterLayoutValidationResult.Compatible
    }

    private fun NormalizedOcrRect.isWithin(container: NormalizedOcrRect): Boolean =
        x >= container.x &&
            y >= container.y &&
            x + width <= container.x + container.width &&
            y + height <= container.y + container.height

    private fun NormalizedOcrRect.overlaps(other: NormalizedOcrRect): Boolean =
        x < other.x + other.width &&
            x + width > other.x &&
            y < other.y + other.height &&
            y + height > other.y
}
