package com.hoggamers.rankforge.domain.ocr.matchlobby

import kotlin.math.abs

enum class LobbySlotGridRole {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,

    ;

    companion object {
        fun fromSlotNumber(slotNumber: Int): LobbySlotGridRole? =
            slotNumber.takeIf { it in 1..12 }?.let { entries[(it - 1) % 4] }
    }
}

enum class LobbyGridPointSource {
    OBSERVED,
    INFERRED,
}

data class LobbyObservedSlotAnchor(
    val slotNumber: Int,
    val centerX: Double,
    val centerY: Double,
)

data class LobbyGridPoint(
    val slotNumber: Int,
    val role: LobbySlotGridRole,
    val centerX: Double,
    val centerY: Double,
    val source: LobbyGridPointSource,
)

data class LobbySlotGrid(
    val screenshotIndex: Int,
    val points: List<LobbyGridPoint>,
    val topRowCenterY: Double,
    val bottomRowCenterY: Double,
    val leftColumnCenterX: Double,
    val rightColumnCenterX: Double,
    val rowPitch: Double,
    val columnPitch: Double,
    val topRowAlignmentError: Double,
    val bottomRowAlignmentError: Double,
    val leftColumnAlignmentError: Double,
    val rightColumnAlignmentError: Double,
) {
    init {
        require(points.size == ROLES.size) { "A Lobby grid must contain four points." }
        require(points.map { it.role } == ROLES) {
            "Lobby grid points must be ordered by grid role."
        }
    }

    fun pointFor(role: LobbySlotGridRole): LobbyGridPoint = points[role.ordinal]

    private companion object {
        val ROLES = LobbySlotGridRole.entries.toList()
    }
}

/**
 * Geometry fallback used only when exactly two OCR anchors are on the same row
 * or the same column.
 *
 * columnToRowPitchRatio = horizontal column pitch / vertical row pitch.
 *
 * Directly observed geometry always has priority:
 * - 4 anchors: both pitches are measured directly.
 * - 3 anchors: both pitches are measured directly.
 * - 2 diagonal anchors: both pitches are measured directly.
 * - 2 same-row anchors: column pitch is measured; row pitch is inferred.
 * - 2 same-column anchors: row pitch is measured; column pitch is inferred.
 */
data class LobbyGridGeometryCalibration(
    val columnToRowPitchRatio: Double,
) {
    init {
        require(columnToRowPitchRatio.isFinite() && columnToRowPitchRatio > 0.0) {
            "Lobby grid column-to-row pitch ratio must be finite and positive."
        }
    }
}

object LobbyGridGeometryCalibrationProfiles {
    /**
     * Initial observed center-distance ratio from the verified lobby evidence:
     *
     * horizontal pitch = 491.0 px
     * vertical pitch   = 204.5 px
     * ratio            = 491.0 / 204.5 ~= 2.400978
     *
     * Keep this value isolated as calibration evidence so it can be replaced
     * with a median/multi-screenshot calibration without changing reconstruction logic.
     */
    val InitialObservedPitchRatio = LobbyGridGeometryCalibration(
        columnToRowPitchRatio = 491.0 / 204.5,
    )
}

sealed interface LobbyGridReconstructionResult {
    data class Reconstructed(
        val grid: LobbySlotGrid,
    ) : LobbyGridReconstructionResult

    data object InsufficientAnchors : LobbyGridReconstructionResult
    data object InvalidSlotGroup : LobbyGridReconstructionResult
    data object DuplicateSlot : LobbyGridReconstructionResult
    data object InvalidGeometry : LobbyGridReconstructionResult
}

class LobbySlotGridReconstructor(
    private val geometryCalibration: LobbyGridGeometryCalibration =
        LobbyGridGeometryCalibrationProfiles.InitialObservedPitchRatio,
) {
    fun reconstruct(
        screenshotIndex: Int,
        observedAnchors: List<LobbyObservedSlotAnchor>,
    ): LobbyGridReconstructionResult {
        val expectedSlots = expectedSlotsFor(screenshotIndex)
            ?: return LobbyGridReconstructionResult.InvalidSlotGroup
        if (observedAnchors.any { it.slotNumber !in expectedSlots }) {
            return LobbyGridReconstructionResult.InvalidSlotGroup
        }
        if (observedAnchors.map { it.slotNumber }.distinct().size != observedAnchors.size) {
            return LobbyGridReconstructionResult.DuplicateSlot
        }
        if (observedAnchors.any { !it.centerX.isFinite() || !it.centerY.isFinite() }) {
            return LobbyGridReconstructionResult.InvalidGeometry
        }
        if (observedAnchors.size < MINIMUM_ANCHOR_COUNT) {
            return LobbyGridReconstructionResult.InsufficientAnchors
        }

        val observedByRole = observedAnchors
            .sortedWith(
                compareBy<LobbyObservedSlotAnchor> { roleFor(it.slotNumber)!!.ordinal }
                    .thenBy { it.slotNumber },
            )
            .associate { anchor ->
                roleFor(anchor.slotNumber)!! to anchor
            }
        if (observedByRole.size != observedAnchors.size) {
            return LobbyGridReconstructionResult.DuplicateSlot
        }
        if (!observedByRole.hasValidObservedOrdering()) {
            return LobbyGridReconstructionResult.InvalidGeometry
        }

        /*
         * Fixed axis-aligned lobby-grid contract:
         *
         *   TOP_LEFT  --------  TOP_RIGHT
         *      |                    |
         *      |                    |
         *   BOTTOM_LEFT ------- BOTTOM_RIGHT
         *
         * For 3 or 4 anchors, all four canonical axes are derived directly from
         * observed center coordinates. No pitch-ratio calibration is used.
         *
         * For exactly 2 anchors:
         *
         *   TOP_LEFT + TOP_RIGHT:
         *     H is observed, V = H / R, bottom row is inferred.
         *
         *   BOTTOM_LEFT + BOTTOM_RIGHT:
         *     H is observed, V = H / R, top row is inferred.
         *
         *   TOP_LEFT + BOTTOM_LEFT:
         *     V is observed, H = V * R, right column is inferred.
         *
         *   TOP_RIGHT + BOTTOM_RIGHT:
         *     V is observed, H = V * R, left column is inferred.
         *
         *   TOP_LEFT + BOTTOM_RIGHT:
         *     H and V are both observed from the diagonal; other corners are inferred.
         *
         *   TOP_RIGHT + BOTTOM_LEFT:
         *     H and V are both observed from the diagonal; other corners are inferred.
         *
         * R = columnToRowPitchRatio.
         */
        val axes = when {
            observedByRole.size >= DIRECT_GEOMETRY_MINIMUM_ANCHORS ->
                resolveDirectAxes(observedByRole)

            observedByRole.size == MINIMUM_ANCHOR_COUNT ->
                resolveTwoAnchorAxes(observedByRole)

            else -> null
        } ?: return LobbyGridReconstructionResult.InsufficientAnchors

        if (!axes.hasValidOrdering()) {
            return LobbyGridReconstructionResult.InvalidGeometry
        }

        val points = LobbySlotGridRole.entries.map { role ->
            observedByRole[role]?.let { observed ->
                LobbyGridPoint(
                    slotNumber = observed.slotNumber,
                    role = role,
                    centerX = observed.centerX,
                    centerY = observed.centerY,
                    source = LobbyGridPointSource.OBSERVED,
                )
            } ?: inferredPoint(
                screenshotIndex = screenshotIndex,
                role = role,
                centerX = axes.centerXFor(role),
                centerY = axes.centerYFor(role),
            )
        }

        return validateCompleteGrid(screenshotIndex, points)
    }

    /**
     * Three/four-anchor path.
     *
     * Every row and column has at least one observed center, so all four canonical
     * axes are obtained from direct OCR evidence. Repeated evidence on one axis is
     * averaged to smooth sub-pixel/bounding-box center variation.
     */
    private fun resolveDirectAxes(
        observedByRole: Map<LobbySlotGridRole, LobbyObservedSlotAnchor>,
    ): AxisCenters? {
        val leftColumnCenterX = averageOrNull(
            observedByRole[LobbySlotGridRole.TOP_LEFT]?.centerX,
            observedByRole[LobbySlotGridRole.BOTTOM_LEFT]?.centerX,
        ) ?: return null
        val rightColumnCenterX = averageOrNull(
            observedByRole[LobbySlotGridRole.TOP_RIGHT]?.centerX,
            observedByRole[LobbySlotGridRole.BOTTOM_RIGHT]?.centerX,
        ) ?: return null
        val topRowCenterY = averageOrNull(
            observedByRole[LobbySlotGridRole.TOP_LEFT]?.centerY,
            observedByRole[LobbySlotGridRole.TOP_RIGHT]?.centerY,
        ) ?: return null
        val bottomRowCenterY = averageOrNull(
            observedByRole[LobbySlotGridRole.BOTTOM_LEFT]?.centerY,
            observedByRole[LobbySlotGridRole.BOTTOM_RIGHT]?.centerY,
        ) ?: return null

        return AxisCenters(
            leftColumnCenterX = leftColumnCenterX,
            rightColumnCenterX = rightColumnCenterX,
            topRowCenterY = topRowCenterY,
            bottomRowCenterY = bottomRowCenterY,
        )
    }

    /**
     * Exactly-two-anchor path.
     *
     * All six possible role relationships are handled explicitly. Diagonals use
     * only direct center geometry. Same-row/same-column pairs use one calibrated
     * pitch ratio to recover the single missing dimension.
     */
    private fun resolveTwoAnchorAxes(
        observedByRole: Map<LobbySlotGridRole, LobbyObservedSlotAnchor>,
    ): AxisCenters? {
        if (observedByRole.size != MINIMUM_ANCHOR_COUNT) return null

        val topLeft = observedByRole[LobbySlotGridRole.TOP_LEFT]
        val topRight = observedByRole[LobbySlotGridRole.TOP_RIGHT]
        val bottomLeft = observedByRole[LobbySlotGridRole.BOTTOM_LEFT]
        val bottomRight = observedByRole[LobbySlotGridRole.BOTTOM_RIGHT]

        return when {
            topLeft != null && topRight != null ->
                axesFromTopRow(topLeft, topRight)

            bottomLeft != null && bottomRight != null ->
                axesFromBottomRow(bottomLeft, bottomRight)

            topLeft != null && bottomLeft != null ->
                axesFromLeftColumn(topLeft, bottomLeft)

            topRight != null && bottomRight != null ->
                axesFromRightColumn(topRight, bottomRight)

            topLeft != null && bottomRight != null ->
                axesFromTopLeftBottomRightDiagonal(topLeft, bottomRight)

            topRight != null && bottomLeft != null ->
                axesFromTopRightBottomLeftDiagonal(topRight, bottomLeft)

            else -> null
        }
    }

    private fun axesFromTopRow(
        topLeft: LobbyObservedSlotAnchor,
        topRight: LobbyObservedSlotAnchor,
    ): AxisCenters? {
        val topRowCenterY = averageOrNull(topLeft.centerY, topRight.centerY) ?: return null
        val columnPitch = topRight.centerX - topLeft.centerX
        if (!columnPitch.isPositiveFinite()) return null

        val rowPitch = columnPitch / geometryCalibration.columnToRowPitchRatio
        if (!rowPitch.isPositiveFinite()) return null

        return AxisCenters(
            leftColumnCenterX = topLeft.centerX,
            rightColumnCenterX = topRight.centerX,
            topRowCenterY = topRowCenterY,
            bottomRowCenterY = topRowCenterY + rowPitch,
        )
    }

    private fun axesFromBottomRow(
        bottomLeft: LobbyObservedSlotAnchor,
        bottomRight: LobbyObservedSlotAnchor,
    ): AxisCenters? {
        val bottomRowCenterY =
            averageOrNull(bottomLeft.centerY, bottomRight.centerY) ?: return null
        val columnPitch = bottomRight.centerX - bottomLeft.centerX
        if (!columnPitch.isPositiveFinite()) return null

        val rowPitch = columnPitch / geometryCalibration.columnToRowPitchRatio
        if (!rowPitch.isPositiveFinite()) return null

        return AxisCenters(
            leftColumnCenterX = bottomLeft.centerX,
            rightColumnCenterX = bottomRight.centerX,
            topRowCenterY = bottomRowCenterY - rowPitch,
            bottomRowCenterY = bottomRowCenterY,
        )
    }

    private fun axesFromLeftColumn(
        topLeft: LobbyObservedSlotAnchor,
        bottomLeft: LobbyObservedSlotAnchor,
    ): AxisCenters? {
        val leftColumnCenterX =
            averageOrNull(topLeft.centerX, bottomLeft.centerX) ?: return null
        val rowPitch = bottomLeft.centerY - topLeft.centerY
        if (!rowPitch.isPositiveFinite()) return null

        val columnPitch = rowPitch * geometryCalibration.columnToRowPitchRatio
        if (!columnPitch.isPositiveFinite()) return null

        return AxisCenters(
            leftColumnCenterX = leftColumnCenterX,
            rightColumnCenterX = leftColumnCenterX + columnPitch,
            topRowCenterY = topLeft.centerY,
            bottomRowCenterY = bottomLeft.centerY,
        )
    }

    private fun axesFromRightColumn(
        topRight: LobbyObservedSlotAnchor,
        bottomRight: LobbyObservedSlotAnchor,
    ): AxisCenters? {
        val rightColumnCenterX =
            averageOrNull(topRight.centerX, bottomRight.centerX) ?: return null
        val rowPitch = bottomRight.centerY - topRight.centerY
        if (!rowPitch.isPositiveFinite()) return null

        val columnPitch = rowPitch * geometryCalibration.columnToRowPitchRatio
        if (!columnPitch.isPositiveFinite()) return null

        return AxisCenters(
            leftColumnCenterX = rightColumnCenterX - columnPitch,
            rightColumnCenterX = rightColumnCenterX,
            topRowCenterY = topRight.centerY,
            bottomRowCenterY = bottomRight.centerY,
        )
    }

    private fun axesFromTopLeftBottomRightDiagonal(
        topLeft: LobbyObservedSlotAnchor,
        bottomRight: LobbyObservedSlotAnchor,
    ): AxisCenters? {
        val axes = AxisCenters(
            leftColumnCenterX = topLeft.centerX,
            rightColumnCenterX = bottomRight.centerX,
            topRowCenterY = topLeft.centerY,
            bottomRowCenterY = bottomRight.centerY,
        )
        return axes.takeIf { it.hasValidOrdering() }
    }

    private fun axesFromTopRightBottomLeftDiagonal(
        topRight: LobbyObservedSlotAnchor,
        bottomLeft: LobbyObservedSlotAnchor,
    ): AxisCenters? {
        val axes = AxisCenters(
            leftColumnCenterX = bottomLeft.centerX,
            rightColumnCenterX = topRight.centerX,
            topRowCenterY = topRight.centerY,
            bottomRowCenterY = bottomLeft.centerY,
        )
        return axes.takeIf { it.hasValidOrdering() }
    }

    /**
     * Validate every ordering relationship that can be checked directly from the
     * currently observed centers. Missing roles do not fail the check.
     */
    private fun Map<LobbySlotGridRole, LobbyObservedSlotAnchor>.hasValidObservedOrdering(): Boolean {
        val topLeft = this[LobbySlotGridRole.TOP_LEFT]
        val topRight = this[LobbySlotGridRole.TOP_RIGHT]
        val bottomLeft = this[LobbySlotGridRole.BOTTOM_LEFT]
        val bottomRight = this[LobbySlotGridRole.BOTTOM_RIGHT]

        if (topLeft != null && topRight != null && topLeft.centerX >= topRight.centerX) {
            return false
        }
        if (bottomLeft != null && bottomRight != null && bottomLeft.centerX >= bottomRight.centerX) {
            return false
        }
        if (topLeft != null && bottomLeft != null && topLeft.centerY >= bottomLeft.centerY) {
            return false
        }
        if (topRight != null && bottomRight != null && topRight.centerY >= bottomRight.centerY) {
            return false
        }
        if (topLeft != null && bottomRight != null &&
            (topLeft.centerX >= bottomRight.centerX || topLeft.centerY >= bottomRight.centerY)
        ) {
            return false
        }
        if (topRight != null && bottomLeft != null &&
            (bottomLeft.centerX >= topRight.centerX || topRight.centerY >= bottomLeft.centerY)
        ) {
            return false
        }
        return true
    }

    private fun inferredPoint(
        screenshotIndex: Int,
        role: LobbySlotGridRole,
        centerX: Double,
        centerY: Double,
    ) = LobbyGridPoint(
        slotNumber = slotNumberFor(screenshotIndex, role),
        role = role,
        centerX = centerX,
        centerY = centerY,
        source = LobbyGridPointSource.INFERRED,
    )

    private fun validateCompleteGrid(
        screenshotIndex: Int,
        points: List<LobbyGridPoint>,
    ): LobbyGridReconstructionResult {
        val topLeft = points[ROLE_INDEX_TOP_LEFT]
        val topRight = points[ROLE_INDEX_TOP_RIGHT]
        val bottomLeft = points[ROLE_INDEX_BOTTOM_LEFT]
        val bottomRight = points[ROLE_INDEX_BOTTOM_RIGHT]

        if (topLeft.centerX >= topRight.centerX || bottomLeft.centerX >= bottomRight.centerX ||
            topLeft.centerY >= bottomLeft.centerY || topRight.centerY >= bottomRight.centerY
        ) {
            return LobbyGridReconstructionResult.InvalidGeometry
        }

        val topRowCenterY = (topLeft.centerY + topRight.centerY) / 2.0
        val bottomRowCenterY = (bottomLeft.centerY + bottomRight.centerY) / 2.0
        val leftColumnCenterX = (topLeft.centerX + bottomLeft.centerX) / 2.0
        val rightColumnCenterX = (topRight.centerX + bottomRight.centerX) / 2.0
        val rowPitch = bottomRowCenterY - topRowCenterY
        val columnPitch = rightColumnCenterX - leftColumnCenterX
        if (!rowPitch.isPositiveFinite() || !columnPitch.isPositiveFinite()) {
            return LobbyGridReconstructionResult.InvalidGeometry
        }

        return LobbyGridReconstructionResult.Reconstructed(
            LobbySlotGrid(
                screenshotIndex = screenshotIndex,
                points = points,
                topRowCenterY = topRowCenterY,
                bottomRowCenterY = bottomRowCenterY,
                leftColumnCenterX = leftColumnCenterX,
                rightColumnCenterX = rightColumnCenterX,
                rowPitch = rowPitch,
                columnPitch = columnPitch,
                topRowAlignmentError = abs(topLeft.centerY - topRight.centerY),
                bottomRowAlignmentError = abs(bottomLeft.centerY - bottomRight.centerY),
                leftColumnAlignmentError = abs(topLeft.centerX - bottomLeft.centerX),
                rightColumnAlignmentError = abs(topRight.centerX - bottomRight.centerX),
            ),
        )
    }

    private data class AxisCenters(
        val leftColumnCenterX: Double,
        val rightColumnCenterX: Double,
        val topRowCenterY: Double,
        val bottomRowCenterY: Double,
    ) {
        fun hasValidOrdering(): Boolean =
            leftColumnCenterX.isFinite() &&
                rightColumnCenterX.isFinite() &&
                topRowCenterY.isFinite() &&
                bottomRowCenterY.isFinite() &&
                leftColumnCenterX < rightColumnCenterX &&
                topRowCenterY < bottomRowCenterY

        fun centerXFor(role: LobbySlotGridRole): Double = when (role) {
            LobbySlotGridRole.TOP_LEFT,
            LobbySlotGridRole.BOTTOM_LEFT,
            -> leftColumnCenterX

            LobbySlotGridRole.TOP_RIGHT,
            LobbySlotGridRole.BOTTOM_RIGHT,
            -> rightColumnCenterX
        }

        fun centerYFor(role: LobbySlotGridRole): Double = when (role) {
            LobbySlotGridRole.TOP_LEFT,
            LobbySlotGridRole.TOP_RIGHT,
            -> topRowCenterY

            LobbySlotGridRole.BOTTOM_LEFT,
            LobbySlotGridRole.BOTTOM_RIGHT,
            -> bottomRowCenterY
        }
    }

    private companion object {
        const val MINIMUM_ANCHOR_COUNT = 2
        const val DIRECT_GEOMETRY_MINIMUM_ANCHORS = 3
        const val ROLE_INDEX_TOP_LEFT = 0
        const val ROLE_INDEX_TOP_RIGHT = 1
        const val ROLE_INDEX_BOTTOM_LEFT = 2
        const val ROLE_INDEX_BOTTOM_RIGHT = 3

        fun expectedSlotsFor(screenshotIndex: Int): IntRange? = when (screenshotIndex) {
            1 -> 1..4
            2 -> 5..8
            3 -> 9..12
            else -> null
        }

        fun roleFor(slotNumber: Int): LobbySlotGridRole? =
            LobbySlotGridRole.fromSlotNumber(slotNumber)

        fun slotNumberFor(
            screenshotIndex: Int,
            role: LobbySlotGridRole,
        ): Int = (screenshotIndex - 1) * 4 + role.ordinal + 1

        fun averageOrNull(vararg values: Double?): Double? {
            val present = values.filterNotNull()
            return present.takeIf { it.isNotEmpty() }?.average()
        }

        fun Double.isPositiveFinite(): Boolean = isFinite() && this > 0.0
    }
}
