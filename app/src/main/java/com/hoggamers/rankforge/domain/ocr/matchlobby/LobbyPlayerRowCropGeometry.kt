package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxCroppedRosterPanelLayout
import kotlin.math.ceil

/** Integer pixel bounds in the canonical team-crop coordinate system. */
data class LobbyPlayerRowCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0 && top >= 0) { "Row crop origin must not be negative." }
        require(right > left && bottom > top) { "Row crop bounds must be positive." }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Deterministic bounds for all four player-row images. */
data class LobbyPlayerRowCropGeometry(
    val teamCropWidth: Int,
    val teamCropHeight: Int,
    val playerAreaLeft: Int,
    val bands: LobbyPlayerRowBands,
    val rows: List<LobbyPlayerRowCropBounds>,
) {
    init {
        require(rows.size == LobbyPlayerRow.entries.size) {
            "Exactly four player-row bounds are required."
        }
    }

    fun boundsFor(row: LobbyPlayerRow): LobbyPlayerRowCropBounds = rows[row.ordinal]
}

object LobbyPlayerRowCropGeometryCalculator {
    /**
     * Uses the established Free Fire roster player-content boundary (15% of a
     * compact team crop) and the Phase 2A row bands as the complete vertical
     * crop regions. No OCR text bounding box participates in final bounds.
     */
    fun calculate(
        teamCropWidth: Int,
        teamCropHeight: Int,
        slotAnchorY: Double,
        playerAreaStartFraction: Double = FreeFireMaxCroppedRosterPanelLayout.PLAYER_CONTENT_START_FRACTION,
    ): LobbyPlayerRowCropGeometry? {
        if (teamCropWidth <= 0 || teamCropHeight <= 0) return null
        if (!playerAreaStartFraction.isFinite() || playerAreaStartFraction !in 0.0..1.0) return null
        val bands = LobbyPlayerRowBandCalculator.calculate(teamCropHeight.toDouble(), slotAnchorY)
            ?: return null
        val left = (teamCropWidth * playerAreaStartFraction).toInt().coerceIn(0, teamCropWidth - 1)
        val rows = bands.bands.map { band ->
            val top = ceil(band.top).toInt().coerceIn(0, teamCropHeight)
            val bottom = if (band.row == LobbyPlayerRow.ROW_4) {
                teamCropHeight
            } else {
                ceil(band.bottom).toInt().coerceIn(0, teamCropHeight)
            }
            if (bottom <= top) return null
            LobbyPlayerRowCropBounds(left, top, teamCropWidth, bottom)
        }
        return LobbyPlayerRowCropGeometry(
            teamCropWidth = teamCropWidth,
            teamCropHeight = teamCropHeight,
            playerAreaLeft = left,
            bands = bands,
            rows = rows,
        )
    }
}
