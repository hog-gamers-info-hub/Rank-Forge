package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

enum class MatchResultScreenshotCropError {
    INVALID_ROLE,
    MISSING_ASSET,
    MISSING_LOCAL_FILE,
    FINALIZED_MATCH,
    INVALID_CROP,
    CONTENT_INVALID,
    CONTENT_VALIDATION_FAILED,
    SAVE_FAILED,
}

data class MatchResultScreenshotCropUiState(
    val isLoading: Boolean = true,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val role: MatchResultScreenshotRole? = null,
    val imageUri: String? = null,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,
    val confirmedCrop: OcrNormalizedCropRect? = null,
    val draftCrop: OcrNormalizedCropRect = OcrVisualCropDefaults.FullImageCrop,
    val isFinalized: Boolean = false,
    val isValidating: Boolean = false,
    val isSaving: Boolean = false,
    val error: MatchResultScreenshotCropError? = null,
)
