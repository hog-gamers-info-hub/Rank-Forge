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
        if (observedAnchors.size < 3) {
            return LobbyGridReconstructionResult.InsufficientAnchors
        }

        val observedByRole = observedAnchors
            .sortedWith(compareBy<LobbyObservedSlotAnchor> { roleFor(it.slotNumber)!!.ordinal }
                .thenBy { it.slotNumber })
            .associate { anchor ->
                roleFor(anchor.slotNumber)!! to anchor
            }
        if (observedByRole.size != observedAnchors.size) {
            return LobbyGridReconstructionResult.DuplicateSlot
        }

        val points = LobbySlotGridRole.entries.map { role ->
            val observed = observedByRole[role]
            if (observed != null) {
                LobbyGridPoint(
                    slotNumber = observed.slotNumber,
                    role = role,
                    centerX = observed.centerX,
                    centerY = observed.centerY,
                    source = LobbyGridPointSource.OBSERVED,
                )
            } else {
                inferMissingPoint(role, observedByRole, screenshotIndex)
                    ?: return LobbyGridReconstructionResult.InsufficientAnchors
            }
        }

        return validateCompleteGrid(screenshotIndex, points)
    }

    private fun inferMissingPoint(
        missingRole: LobbySlotGridRole,
        observedByRole: Map<LobbySlotGridRole, LobbyObservedSlotAnchor>,
        screenshotIndex: Int,
    ): LobbyGridPoint? {
        return when (missingRole) {
            LobbySlotGridRole.TOP_LEFT -> {
                val bottomLeft = observedByRole[LobbySlotGridRole.BOTTOM_LEFT] ?: return null
                val topRight = observedByRole[LobbySlotGridRole.TOP_RIGHT] ?: return null
                inferredPoint(
                    screenshotIndex,
                    missingRole,
                    centerX = bottomLeft.centerX,
                    centerY = topRight.centerY,
                )
            }

            LobbySlotGridRole.TOP_RIGHT -> {
                val bottomRight = observedByRole[LobbySlotGridRole.BOTTOM_RIGHT] ?: return null
                val topLeft = observedByRole[LobbySlotGridRole.TOP_LEFT] ?: return null
                inferredPoint(
                    screenshotIndex,
                    missingRole,
                    centerX = bottomRight.centerX,
                    centerY = topLeft.centerY,
                )
            }

            LobbySlotGridRole.BOTTOM_LEFT -> {
                val topLeft = observedByRole[LobbySlotGridRole.TOP_LEFT] ?: return null
                val bottomRight = observedByRole[LobbySlotGridRole.BOTTOM_RIGHT] ?: return null
                inferredPoint(
                    screenshotIndex,
                    missingRole,
                    centerX = topLeft.centerX,
                    centerY = bottomRight.centerY,
                )
            }

            LobbySlotGridRole.BOTTOM_RIGHT -> {
                val topRight = observedByRole[LobbySlotGridRole.TOP_RIGHT] ?: return null
                val bottomLeft = observedByRole[LobbySlotGridRole.BOTTOM_LEFT] ?: return null
                inferredPoint(
                    screenshotIndex,
                    missingRole,
                    centerX = topRight.centerX,
                    centerY = bottomLeft.centerY,
                )
            }
        }
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

    private companion object {
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
    }
}
