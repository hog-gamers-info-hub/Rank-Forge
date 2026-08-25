package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition

/** Panel-relative geometry only; bitmap creation remains in the Android runner. */
data class LobbyTeamCropBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(right >= left) { "Crop bounds must not have a negative width." }
        require(bottom >= top) { "Crop bounds must not have a negative height." }
    }

    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

data class LobbyTeamCropSlotGeometry(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val detectedSlotNumber: Int,
    val slotNumberBounds: LobbyTeamCropBounds,
)

data class LobbyTeamCrop(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val detectedSlotNumber: Int,
    val bounds: LobbyTeamCropBounds,
)

enum class LobbyTeamCropUnavailableReason {
    REQUIRED_SLOT_NUMBER_UNAVAILABLE,
    SLOT_NUMBER_GEOMETRY_UNAVAILABLE,
    INVALID_TEAM_GRID_GEOMETRY,
    INVALID_CROP_BOUNDS,
}

sealed interface LobbyTeamCropGeometryResult {
    data class Available(
        val crops: List<LobbyTeamCrop>,
    ) : LobbyTeamCropGeometryResult {
        init {
            require(crops.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries) {
                "Team crops must contain every visible slot position exactly once."
            }
        }
    }

    data class Unavailable(
        val reason: LobbyTeamCropUnavailableReason,
    ) : LobbyTeamCropGeometryResult
}

object LobbyTeamCropGeometryCalculator {
    fun calculate(
        panelWidth: Int,
        panelHeight: Int,
        slots: List<LobbyTeamCropSlotGeometry>,
    ): LobbyTeamCropGeometryResult {
        if (slots.map { it.visibleSlotPosition } != RosterVisibleSlotPosition.entries) {
            return LobbyTeamCropGeometryResult.Unavailable(
                LobbyTeamCropUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
            )
        }
        if (panelWidth <= 0 || panelHeight <= 0 ||
            slots.any { it.detectedSlotNumber !in 1..12 }
        ) {
            return LobbyTeamCropGeometryResult.Unavailable(
                LobbyTeamCropUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
            )
        }
        if (slots.any { !it.slotNumberBounds.isPositive() }) {
            return LobbyTeamCropGeometryResult.Unavailable(
                LobbyTeamCropUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE,
            )
        }
        val byPosition = slots.associateBy { it.visibleSlotPosition }
        val topLeft = requireNotNull(byPosition[RosterVisibleSlotPosition.TOP_LEFT])
        val topRight = requireNotNull(byPosition[RosterVisibleSlotPosition.TOP_RIGHT])
        val bottomLeft = requireNotNull(byPosition[RosterVisibleSlotPosition.BOTTOM_LEFT])
        val bottomRight = requireNotNull(byPosition[RosterVisibleSlotPosition.BOTTOM_RIGHT])
        val topWidth = topRight.slotNumberBounds.centerX - topLeft.slotNumberBounds.centerX
        val bottomWidth = bottomRight.slotNumberBounds.centerX - bottomLeft.slotNumberBounds.centerX
        val leftHeight = bottomLeft.slotNumberBounds.centerY - topLeft.slotNumberBounds.centerY
        val rightHeight = bottomRight.slotNumberBounds.centerY - topRight.slotNumberBounds.centerY
        if (!topWidth.isPositive() || !bottomWidth.isPositive() ||
            !leftHeight.isPositive() || !rightHeight.isPositive() ||
            !isConsistent(topWidth, bottomWidth) || !isConsistent(leftHeight, rightHeight)
        ) {
            return LobbyTeamCropGeometryResult.Unavailable(
                LobbyTeamCropUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
            )
        }
        val teamWidth = (topWidth + bottomWidth) / 2.0
        val teamHeight = (leftHeight + rightHeight) / 2.0
        val slotLeftInsets = listOf(
            topLeft.slotNumberBounds.centerX,
            bottomLeft.slotNumberBounds.centerX,
            topRight.slotNumberBounds.centerX - teamWidth,
            bottomRight.slotNumberBounds.centerX - teamWidth,
        )
        if (slotLeftInsets.any { !it.isFinite() } ||
            slotLeftInsets.max() - slotLeftInsets.min() > teamWidth * MAX_GRID_DEVIATION_FRACTION
        ) {
            return LobbyTeamCropGeometryResult.Unavailable(
                LobbyTeamCropUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
            )
        }
        val slotLeftInset = slotLeftInsets.average()
        val boundaryTolerance = maxOf(teamWidth, teamHeight) * MAX_BOUNDARY_CLAMP_FRACTION
        val panelRight = panelWidth.toDouble()
        val panelBottom = panelHeight.toDouble()

        val crops = slots.map { slot ->
            val cropLeft = slot.slotNumberBounds.centerX - slotLeftInset
            val cropTop = slot.slotNumberBounds.centerY - teamHeight / 2.0
            val cropBottom = slot.slotNumberBounds.centerY + teamHeight / 2.0
            val cropRight = minOf(
                slot.slotNumberBounds.centerX + teamWidth * HORIZONTAL_CROP_DISTANCE_FRACTION,
                panelRight,
            )
            val rawBounds = LobbyTeamCropBounds(cropLeft, cropTop, cropRight, cropBottom)
            if (!rawBounds.isSafelyWithin(panelRight, panelBottom, boundaryTolerance)) {
                return LobbyTeamCropGeometryResult.Unavailable(
                    LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS,
                )
            }
            val bounded = LobbyTeamCropBounds(
                left = rawBounds.left.coerceIn(0.0, panelRight),
                top = rawBounds.top.coerceIn(0.0, panelBottom),
                right = rawBounds.right.coerceIn(0.0, panelRight),
                bottom = rawBounds.bottom.coerceIn(0.0, panelBottom),
            )
            if (!bounded.isPositive()) {
                return LobbyTeamCropGeometryResult.Unavailable(
                    LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS,
                )
            }
            LobbyTeamCrop(slot.visibleSlotPosition, slot.detectedSlotNumber, bounded)
        }
        return LobbyTeamCropGeometryResult.Available(crops)
    }

    private fun Double.isPositive(): Boolean = isFinite() && this > 0.0

    private fun LobbyTeamCropBounds.isPositive(): Boolean =
        right.isFinite() && bottom.isFinite() && left.isFinite() && top.isFinite() &&
            right > left && bottom > top

    private fun LobbyTeamCropBounds.isSafelyWithin(
        panelRight: Double,
        panelBottom: Double,
        tolerance: Double,
    ): Boolean = left >= -tolerance && top >= -tolerance &&
        right <= panelRight + tolerance && bottom <= panelBottom + tolerance

    private fun isConsistent(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) <= maxOf(first, second) * MAX_GRID_DEVIATION_FRACTION

    private const val MAX_GRID_DEVIATION_FRACTION = 0.20
    private const val MAX_BOUNDARY_CLAMP_FRACTION = 0.05
    private const val HORIZONTAL_CROP_DISTANCE_FRACTION = 0.92
}
