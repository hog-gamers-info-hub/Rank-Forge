package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource

enum class LobbyPlayerTextSource {
    PP_PANEL,
    EMPTY,
}

data class LobbyPlayerRowCropPreview(
    val row: LobbyPlayerRow,
    val boundsInTeamCrop: LobbyPlayerRowCropBounds,
    val slotAnchorSource: LobbySlotAnchorSource,
    val slotAnchorY: Double,
    val structuralEvidence: String?,
    val playerName: String? = null,
    val playerNameConfidence: Float? = null,
    val playerNameSource: LobbyPlayerTextSource = LobbyPlayerTextSource.EMPTY,
)
