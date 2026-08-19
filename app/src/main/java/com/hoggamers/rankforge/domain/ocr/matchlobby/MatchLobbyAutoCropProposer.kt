package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import java.io.File

fun interface MatchLobbyAutoCropProposer {
    suspend fun propose(localFile: File): MatchLobbyAutoCropResult
}

sealed interface MatchLobbyAutoCropResult {
    data class Proposed(
        val crop: OcrNormalizedCropRect,
    ) : MatchLobbyAutoCropResult

    data object NoProposal : MatchLobbyAutoCropResult
}
