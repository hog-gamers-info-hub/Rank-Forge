package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrAnchors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridOverrides
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEditableGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.activeCustomDesignFields
import com.hoggamers.rankforge.domain.ocr.customdesign.resolveCustomDesignEffectiveGridGeometry

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
    val matchesPlayedLabel: String = "",
)

data class CustomDesignSetupUiState(
    val teamNameLabel: String = "",
    val winLabel: String = "",
    val matchesPlayedLabel: String = "",
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
    val isInitialSavedDesignDiscoveryComplete: Boolean = false,
    val ocrStatus: CustomDesignOcrStatus = CustomDesignOcrStatus.IDLE,
    val ocrAnchors: CustomDesignOcrAnchors? = null,
    val averageRankingBoundingBoxHeightPx: Float? = null,
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

    val allRequiredLabelsFilled: Boolean
        get() = teamNameLabel.isNotBlank() &&
            winLabel.isNotBlank() &&
            totalKillsLabel.isNotBlank() &&
            positionPointsLabel.isNotBlank() &&
            totalPointsLabel.isNotBlank()

    val activeFields: List<CustomDesignAnchorField>
        get() = activeCustomDesignFields(matchesPlayedLabel)

    val isFinalGridReady: Boolean
        get() = selectedImageReference != null &&
            sourceImageWidth != null &&
            sourceImageHeight != null &&
            allRequiredLabelsFilled &&
            ocrStatus in setOf(CustomDesignOcrStatus.COMPLETED, CustomDesignOcrStatus.FAILED) &&
            editableGridGeometry?.let { geometry ->
                geometry.columnX.keys == activeFields.toSet() &&
                    geometry.rowY.keys.containsAll((1..12).toSet())
            } == true
}

internal fun CustomDesignSetupUiState.previewGridGeometry(): CustomDesignEffectiveGridGeometry? {
    val sourceWidth = sourceImageWidth ?: return null
    val sourceHeight = sourceImageHeight ?: return null
    if (isFinalGridReady) {
        return resolveCustomDesignEffectiveGridGeometry(editableGridGeometry, manualGridOverrides)
    }

    val labelsByField = mapOf(
        CustomDesignAnchorField.TEAM_NAME to teamNameLabel,
        CustomDesignAnchorField.WIN to winLabel,
        CustomDesignAnchorField.MATCHES_PLAYED to matchesPlayedLabel,
        CustomDesignAnchorField.TOTAL_KILLS to totalKillsLabel,
        CustomDesignAnchorField.POSITION_POINTS to positionPointsLabel,
        CustomDesignAnchorField.TOTAL_POINTS to totalPointsLabel,
    )
    val detectedColumns = ocrAnchors?.columnX
        ?.filter { (field, x) ->
            labelsByField[field]?.isNotBlank() == true &&
                x.isFinite() &&
                x in 0f..sourceWidth.toFloat()
        }
        ?.mapValues { (field, x) -> manualGridOverrides.columnX[field] ?: x }
        .orEmpty()
    if (detectedColumns.isEmpty()) return null

    return CustomDesignEffectiveGridGeometry(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        columnX = detectedColumns,
        rowY = emptyMap(),
    )
}
