package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect

enum class MatchLobbyScreenshotCropError {
    INVALID_INDEX,
    MISSING_ASSET,
    MISSING_LOCAL_FILE,
    FINALIZED_MATCH,
    INVALID_CROP,
    SAVE_FAILED,
}

data class MatchLobbyScreenshotCropUiState(
    val isLoading: Boolean = true,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val lobbyScreenshotIndex: Int? = null,
    val imageUri: String? = null,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,
    val confirmedCrop: OcrNormalizedCropRect? = null,
    val draftCrop: OcrNormalizedCropRect = OcrVisualCropDefaults.FullImageCrop,
    val isFinalized: Boolean = false,
    val isSaving: Boolean = false,
    val error: MatchLobbyScreenshotCropError? = null,
)
