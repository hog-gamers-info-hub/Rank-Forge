package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrAnchors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridOverrides
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEditableGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors

enum class CustomDesignSaveStatus {
    IDLE,
    SAVING,
    SAVED,
    FAILED,
}

enum class CustomDesignRestoreStatus {
    IDLE,
    RESTORING,
    RESTORED,
    FAILED,
}

enum class CustomDesignDeleteStatus {
    IDLE,
    DELETING,
    DELETED,
    FAILED,
}

enum class CustomDesignLabelField {
    TEAM_NAME,
    WIN,
    TOTAL_KILLS,
    POSITION_POINTS,
    TOTAL_POINTS,
}

data class CustomDesignDraft(
    val imageReference: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val teamNameLabel: String,
    val winLabel: String,
    val totalKillsLabel: String,
    val positionPointsLabel: String,
    val totalPointsLabel: String,
)

data class CustomDesignSetupUiState(
    val teamNameLabel: String = "",
    val winLabel: String = "",
    val totalKillsLabel: String = "",
    val positionPointsLabel: String = "",
    val totalPointsLabel: String = "",
    val textColors: CustomDesignColumnTextColors = CustomDesignColumnTextColors.allBlack(),
    val selectedImageReference: String? = null,
    val sourceImageWidth: Int? = null,
    val sourceImageHeight: Int? = null,
    val draft: CustomDesignDraft? = null,
    val validationErrors: Set<CustomDesignLabelField> = emptySet(),
    val imageValidationError: ImageValidationError? = null,
    val photoPickerError: PhotoPickerError? = null,
    val isPhotoPickerLaunchPending: Boolean = false,
    val isImageValidationInProgress: Boolean = false,
    val ocrStatus: CustomDesignOcrStatus = CustomDesignOcrStatus.IDLE,
    val ocrAnchors: CustomDesignOcrAnchors? = null,
    val gridGeometry: CustomDesignGridGeometry? = null,
    val editableGridGeometry: CustomDesignEditableGridGeometry? = null,
    val manualGridOverrides: CustomDesignGridOverrides = CustomDesignGridOverrides(),
    val saveStatus: CustomDesignSaveStatus = CustomDesignSaveStatus.IDLE,
    val savedCustomDesignId: String? = null,
    val restoreStatus: CustomDesignRestoreStatus = CustomDesignRestoreStatus.IDLE,
    val deleteStatus: CustomDesignDeleteStatus = CustomDesignDeleteStatus.IDLE,
) {
    val hasUsableDraft: Boolean
        get() = draft != null
}
