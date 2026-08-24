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

sealed interface LobbyGridReconstructionResult {
    data class Reconstructed(
        val grid: LobbySlotGrid,
    ) : LobbyGridReconstructionResult

    data object InsufficientAnchors : LobbyGridReconstructionResult
    data object InvalidSlotGroup : LobbyGridReconstructionResult
    data object DuplicateSlot : LobbyGridReconstructionResult
    data object InvalidGeometry : LobbyGridReconstructionResult
}

class LobbySlotGridReconstructor {
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

        /*
         * The lobby slot grid is an axis-aligned rectangle by contract:
         *
         *   TOP_LEFT  --------  TOP_RIGHT
         *      |                    |
         *      |                    |
         *   BOTTOM_LEFT ------- BOTTOM_RIGHT
         *
         * Canonical grid axes are reconstructed from OCR center coordinates:
         *
         * leftColumnCenterX  = average X of observed TOP_LEFT/BOTTOM_LEFT
         * rightColumnCenterX = average X of observed TOP_RIGHT/BOTTOM_RIGHT
         * topRowCenterY      = average Y of observed TOP_LEFT/TOP_RIGHT
         * bottomRowCenterY   = average Y of observed BOTTOM_LEFT/BOTTOM_RIGHT
         *
         * A complete grid therefore requires evidence for both columns and both rows.
         * This is available from:
         * - all four anchors,
         * - any three anchors,
         * - either diagonal two-anchor pair.
         *
         * Same-row and same-column two-anchor pairs remain mathematically insufficient
         * because one complete axis is still unknown.
         */
        val axes = resolveAxes(observedByRole)
            ?: return LobbyGridReconstructionResult.InsufficientAnchors

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

    private fun resolveAxes(
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
        if (!rowPitch.isFinite() || !columnPitch.isFinite() || rowPitch <= 0.0 || columnPitch <= 0.0) {
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
    }
}
