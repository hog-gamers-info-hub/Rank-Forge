package com.hoggamers.rankforge.presentation.screen

enum class RosterScreenshotIntakeError {
    MISSING_TOURNAMENT_ID,
    PHOTO_PICKER_LAUNCH_FAILED,
}

enum class RosterScreenshotDuplicateSelectionState {
    SELECTED_FOR_ANOTHER_ROSTER_SCREENSHOT,
}

data class RosterScreenshotSlotUiState(
    val index: Int,
    val selectedImageUri: String? = null,
    val isSelectedImageValidated: Boolean = false,
    val selectedImageMimeType: String? = null,
    val selectedImageWidth: Int? = null,
    val selectedImageHeight: Int? = null,
    val selectedImageFingerprint: String? = null,
    val isValidationInProgress: Boolean = false,
    val lastValidationError: ImageValidationError? = null,
    val duplicateSelectionState: RosterScreenshotDuplicateSelectionState? = null,
) {
    val hasValidatedImage: Boolean
        get() = selectedImageUri != null && isSelectedImageValidated
}

data class RosterScreenshotIntakeUiState(
    val tournamentId: String? = null,
    val slots: List<RosterScreenshotSlotUiState> = defaultRosterScreenshotSlots(),
    val isPhotoPickerLaunchPending: Boolean = false,
    val activePhotoPickerSlotIndex: Int? = null,
    val intakeError: RosterScreenshotIntakeError? = null,
) {
    val selectedImageCount: Int
        get() = slots.count { it.hasValidatedImage }

    val isCompleteSet: Boolean
        get() = slots.size == REQUIRED_SCREENSHOT_COUNT && slots.all { it.hasValidatedImage }

    val isIncompleteDraftSet: Boolean
        get() = !isCompleteSet

    val canSelectImages: Boolean
        get() = !tournamentId.isNullOrBlank() && !isPhotoPickerLaunchPending

    companion object {
        const val REQUIRED_SCREENSHOT_COUNT = 3
    }
}

fun defaultRosterScreenshotSlots(): List<RosterScreenshotSlotUiState> =
    (1..RosterScreenshotIntakeUiState.REQUIRED_SCREENSHOT_COUNT).map(::RosterScreenshotSlotUiState)
