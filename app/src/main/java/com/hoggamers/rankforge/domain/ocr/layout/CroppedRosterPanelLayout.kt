package com.hoggamers.rankforge.domain.ocr.layout

enum class RosterScreenshotPosition(
    val index: Int,
    val tournamentSlotRange: IntRange,
) {
    ONE(index = 1, tournamentSlotRange = 1..4),
    TWO(index = 2, tournamentSlotRange = 5..8),
    THREE(index = 3, tournamentSlotRange = 9..12),
    ;

    fun tournamentSlotFor(visibleSlotPosition: RosterVisibleSlotPosition): Int =
        tournamentSlotRange.first + visibleSlotPosition.offset - 1

    companion object {
        fun fromIndex(index: Int): RosterScreenshotPosition? = entries.singleOrNull {
            it.index == index
        }
    }
}

enum class RosterVisibleSlotPosition(
    val offset: Int,
) {
    TOP_LEFT(offset = 1),
    TOP_RIGHT(offset = 2),
    BOTTOM_LEFT(offset = 3),
    BOTTOM_RIGHT(offset = 4),
}

data class CroppedRosterPlayerRowRegion(
    val rowIndex: Int,
    val rect: NormalizedOcrRect,
)

data class CroppedRosterSlotRegion(
    val visiblePosition: RosterVisibleSlotPosition,
    val contentRect: NormalizedOcrRect,
    val playerRowRegions: List<CroppedRosterPlayerRowRegion>,
)

data class CroppedRosterPanelLayout(
    val id: String,
    val slots: List<CroppedRosterSlotRegion>,
) {
    fun tournamentSlotFor(
        screenshotPosition: RosterScreenshotPosition,
        visibleSlotPosition: RosterVisibleSlotPosition,
    ): Int = screenshotPosition.tournamentSlotFor(visibleSlotPosition)
}

/**
 * Defines the supported roster layout relative to the already prepared roster-panel crop.
 * These rectangles never identify or crop a region from the full screenshot.
 */
object FreeFireMaxCroppedRosterPanelLayout {
    const val ID = "free-fire-max-cropped-roster-panel-four-slots"
    const val PLAYER_CONTENT_START_FRACTION = 0.15

    val definition = CroppedRosterPanelLayout(
        id = ID,
        slots = listOf(
            slot(RosterVisibleSlotPosition.TOP_LEFT, x = 0.0, y = 0.0),
            slot(RosterVisibleSlotPosition.TOP_RIGHT, x = 0.5, y = 0.0),
            slot(RosterVisibleSlotPosition.BOTTOM_LEFT, x = 0.0, y = 0.5),
            slot(RosterVisibleSlotPosition.BOTTOM_RIGHT, x = 0.5, y = 0.5),
        ),
    )

    private fun slot(
        position: RosterVisibleSlotPosition,
        x: Double,
        y: Double,
    ): CroppedRosterSlotRegion {
        val contentRect = NormalizedOcrRect(x = x, y = y, width = 0.5, height = 0.5)
        return CroppedRosterSlotRegion(
            visiblePosition = position,
            contentRect = contentRect,
            playerRowRegions = (1..REQUIRED_PLAYER_ROW_COUNT).map { rowIndex ->
                CroppedRosterPlayerRowRegion(
                    rowIndex = rowIndex,
                    rect = relativeToSlot(
                        contentRect = contentRect,
                        x = 0.15,
                        y = (rowIndex - 1) * 0.25,
                        width = 0.85,
                        height = 0.25,
                    ),
                )
            },
        )
    }

    private fun relativeToSlot(
        contentRect: NormalizedOcrRect,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ): NormalizedOcrRect = NormalizedOcrRect(
        x = contentRect.x + contentRect.width * x,
        y = contentRect.y + contentRect.height * y,
        width = contentRect.width * width,
        height = contentRect.height * height,
    )
}

const val REQUIRED_PLAYER_ROW_COUNT = 4
