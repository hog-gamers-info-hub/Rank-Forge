package com.hoggamers.rankforge.domain.ocr.matchlobby

enum class LobbyPlayerRow {
    ROW_1,
    ROW_2,
    ROW_3,
    ROW_4,
}

data class LobbyPlayerRowBand(
    val row: LobbyPlayerRow,
    val top: Double,
    val bottom: Double,
) {
    fun contains(centerY: Double): Boolean {
        if (!centerY.isFinite()) return false
        return if (row == LobbyPlayerRow.ROW_4) {
            centerY >= top && centerY <= bottom
        } else {
            centerY >= top && centerY < bottom
        }
    }
}

data class LobbyPlayerRowBands(
    val teamCropHeight: Double,
    val slotAnchorY: Double,
    val bands: List<LobbyPlayerRowBand>,
) {
    init {
        require(bands.map { it.row } == LobbyPlayerRow.entries.toList()) {
            "Player row bands must be ordered Row 1 through Row 4."
        }
    }

    fun bandFor(centerY: Double): LobbyPlayerRowBand? = bands.firstOrNull { it.contains(centerY) }

    fun bandFor(row: LobbyPlayerRow): LobbyPlayerRowBand = bands[row.ordinal]
}

object LobbyPlayerRowBandCalculator {
    fun calculate(
        teamCropHeight: Double,
        slotAnchorY: Double,
    ): LobbyPlayerRowBands? {
        if (!teamCropHeight.isFinite() || teamCropHeight <= 0.0) return null
        if (!slotAnchorY.isFinite() || slotAnchorY !in 0.0..teamCropHeight) return null

        val top = 0.0
        val bottom = teamCropHeight
        val upperSplit = top + (slotAnchorY - top) / 2.0
        val lowerSplit = slotAnchorY + (bottom - slotAnchorY) / 2.0
        val bands = listOf(
            LobbyPlayerRowBand(LobbyPlayerRow.ROW_1, top, upperSplit),
            LobbyPlayerRowBand(LobbyPlayerRow.ROW_2, upperSplit, slotAnchorY),
            LobbyPlayerRowBand(LobbyPlayerRow.ROW_3, slotAnchorY, lowerSplit),
            LobbyPlayerRowBand(LobbyPlayerRow.ROW_4, lowerSplit, bottom),
        )
        return LobbyPlayerRowBands(
            teamCropHeight = teamCropHeight,
            slotAnchorY = slotAnchorY,
            bands = bands,
        )
    }
}
