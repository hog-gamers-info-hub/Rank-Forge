package com.hoggamers.rankforge.domain.ocr.layout

enum class ScoreboardPanelId {
    LEFT,
    RIGHT,
}

enum class ScoreboardFieldZoneType {
    PLACEMENT_NUMBER,
    PLAYER_NAME,
    ELIMINATION_VALUE,
}

enum class ScoreboardExclusionZoneType {
    TOP_LOGO,
    BOTTOM_CONTROLS_BACK_BUTTON,
    BOTTOM_LEFT_NUMERIC_OVERLAY,
    RIGHT_SIDE_BACKGROUND,
    REPEATED_ELIMINATIONS_LABEL,
}

enum class ScoreboardRowVisibility {
    VISIBLE,
    CONSTRAINED_REFERENCE,
}

data class ScoreboardFieldZoneDefinition(
    val type: ScoreboardFieldZoneType,
    val relativeRect: NormalizedOcrRect,
)

data class ScoreboardExclusionZoneDefinition(
    val type: ScoreboardExclusionZoneType,
    val rect: NormalizedOcrRect,
)

data class ScoreboardRowDefinition(
    val placementId: Int,
    val rowIndex: Int,
    val fieldZones: List<ScoreboardFieldZoneDefinition>,
    val exclusionZones: List<ScoreboardExclusionZoneDefinition>,
    val visibility: ScoreboardRowVisibility,
) {
    init {
        require(placementId in 1..12) { "Placement ID must be within 1..12." }
        require(rowIndex >= 0) { "Row index must not be negative." }
    }
}

data class ScoreboardPanelDefinition(
    val id: ScoreboardPanelId,
    val contentRect: NormalizedOcrRect,
    val rows: List<ScoreboardRowDefinition>,
)

data class PlacementToPanelRowMapping(
    val placementId: Int,
    val panelId: ScoreboardPanelId,
    val rowIndex: Int,
    val visibility: ScoreboardRowVisibility,
)

data class ScoreboardLayoutDefinition(
    val id: String,
    val calibrationWidth: Int,
    val calibrationHeight: Int,
    val minimumAspectRatio: Double,
    val maximumAspectRatio: Double,
    val overallContentRect: NormalizedOcrRect,
    val panels: List<ScoreboardPanelDefinition>,
    val exclusionZones: List<ScoreboardExclusionZoneDefinition>,
) {
    init {
        require(calibrationWidth > 0) { "Calibration width must be positive." }
        require(calibrationHeight > 0) { "Calibration height must be positive." }
        require(minimumAspectRatio > 0.0) { "Minimum aspect ratio must be positive." }
        require(maximumAspectRatio >= minimumAspectRatio) {
            "Maximum aspect ratio must be at least the minimum aspect ratio."
        }
    }

    val placementMappings: List<PlacementToPanelRowMapping> = panels.flatMap { panel ->
        panel.rows.map { row ->
            PlacementToPanelRowMapping(
                placementId = row.placementId,
                panelId = panel.id,
                rowIndex = row.rowIndex,
                visibility = row.visibility,
            )
        }
    }
}
