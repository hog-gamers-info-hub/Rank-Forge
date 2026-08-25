package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrLocalRelativePath
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity

internal fun MatchLobbyScreenshotAssetEntity.toRosterOcrScreenshotSource(
    position: RosterScreenshotPosition,
    identity: MatchLobbyScreenshotIdentity,
): RosterOcrScreenshotSource? {
    if (identity.tournamentId != tournamentId || identity.matchId != matchId ||
        identity.lobbyScreenshotIndex != lobbyScreenshotIndex ||
        cropProfileId != OcrCropValidationProfiles.Lobby.id ||
        cropLeft == null || cropTop == null || cropRight == null || cropBottom == null
    ) return null
    return RosterOcrScreenshotSource(
        tournamentId = tournamentId,
        rosterScreenshotIndex = lobbyScreenshotIndex,
        screenshotPosition = position,
        localRelativePath = RosterOcrLocalRelativePath(localRelativePath),
        sourceWidth = originalWidth,
        sourceHeight = originalHeight,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
    )
}
