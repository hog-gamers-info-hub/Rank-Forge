package com.hoggamers.rankforge.domain.ocr.layout

object FreeFireMaxScoreboardLayout {
    const val ID = "free-fire-max-match-result-two-panel"
    const val CALIBRATION_WIDTH = 1_600
    const val CALIBRATION_HEIGHT = 720
    const val MINIMUM_ASPECT_RATIO = 2.11
    const val MAXIMUM_ASPECT_RATIO = 2.33

    val definition = ScoreboardLayoutDefinition(
        id = ID,
        calibrationWidth = CALIBRATION_WIDTH,
        calibrationHeight = CALIBRATION_HEIGHT,
        minimumAspectRatio = MINIMUM_ASPECT_RATIO,
        maximumAspectRatio = MAXIMUM_ASPECT_RATIO,
        overallContentRect = NormalizedOcrRect(0.13, 0.22, 0.73, 0.65),
        panels = listOf(
            ScoreboardPanelDefinition(
                id = ScoreboardPanelId.LEFT,
                contentRect = NormalizedOcrRect(0.13, 0.22, 0.40, 0.65),
                rows = (1..5).map { placementId ->
                    row(
                        placementId = placementId,
                        rowIndex = placementId - 1,
                        visibility = ScoreboardRowVisibility.VISIBLE,
                    )
                },
            ),
            ScoreboardPanelDefinition(
                id = ScoreboardPanelId.RIGHT,
                contentRect = NormalizedOcrRect(0.54, 0.22, 0.32, 0.65),
                rows = (6..12).map { placementId ->
                    row(
                        placementId = placementId,
                        rowIndex = placementId - 6,
                        visibility = if (placementId == 12) {
                            ScoreboardRowVisibility.CONSTRAINED_REFERENCE
                        } else {
                            ScoreboardRowVisibility.VISIBLE
                        },
                    )
                },
            ),
        ),
        exclusionZones = listOf(
            ScoreboardExclusionZoneDefinition(
                ScoreboardExclusionZoneType.TOP_LOGO,
                NormalizedOcrRect(0.00, 0.00, 1.00, 0.20),
            ),
            ScoreboardExclusionZoneDefinition(
                ScoreboardExclusionZoneType.BOTTOM_CONTROLS_BACK_BUTTON,
                NormalizedOcrRect(0.00, 0.86, 1.00, 0.14),
            ),
            ScoreboardExclusionZoneDefinition(
                ScoreboardExclusionZoneType.BOTTOM_LEFT_NUMERIC_OVERLAY,
                NormalizedOcrRect(0.00, 0.94, 0.35, 0.06),
            ),
            ScoreboardExclusionZoneDefinition(
                ScoreboardExclusionZoneType.RIGHT_SIDE_BACKGROUND,
                NormalizedOcrRect(0.86, 0.00, 0.14, 1.00),
            ),
        ),
    )

    private fun row(
        placementId: Int,
        rowIndex: Int,
        visibility: ScoreboardRowVisibility,
    ): ScoreboardRowDefinition = ScoreboardRowDefinition(
        placementId = placementId,
        rowIndex = rowIndex,
        fieldZones = listOf(
            ScoreboardFieldZoneDefinition(
                ScoreboardFieldZoneType.PLACEMENT_NUMBER,
                NormalizedOcrRect(0.00, 0.00, 0.12, 1.00),
            ),
            ScoreboardFieldZoneDefinition(
                ScoreboardFieldZoneType.PLAYER_NAME,
                NormalizedOcrRect(0.12, 0.00, 0.58, 1.00),
            ),
            ScoreboardFieldZoneDefinition(
                ScoreboardFieldZoneType.ELIMINATION_VALUE,
                NormalizedOcrRect(0.70, 0.00, 0.16, 1.00),
            ),
        ),
        exclusionZones = listOf(
            ScoreboardExclusionZoneDefinition(
                ScoreboardExclusionZoneType.REPEATED_ELIMINATIONS_LABEL,
                NormalizedOcrRect(0.86, 0.00, 0.14, 1.00),
            ),
        ),
        visibility = visibility,
    )
}
