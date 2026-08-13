package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.tournament.MatchStatus

enum class MatchLobbyScreenshotDuplicateError {
    USED_BY_ANOTHER_LOBBY_SCREENSHOT,
    STATE_CONFLICT,
}

enum class MatchLobbyScreenshotPreservationError {
    OWNER_MISSING,
    PRESERVATION_FAILED,
    SAVE_FAILED,
    CLEANUP_FAILED,
}

enum class MatchLobbyScreenshotIntakeError {
    INVALID_CONTEXT,
    MATCH_NOT_FOUND,
    FINALIZED_MATCH,
    PHOTO_PICKER_LAUNCH_FAILED,
    INVALID_INDEX,
    REMOVE_FAILED,
}

enum class MatchLobbyTemplateSaveStatus {
    SAVED,
    FAILED,
}

data class MatchLobbyScreenshotSlotUiState(
    val index: Int,
    val selectedScreenshotUri: String? = null,
    val selectedScreenshotMimeType: String? = null,
    val selectedScreenshotWidth: Int? = null,
    val selectedScreenshotHeight: Int? = null,
    val fingerprint: String? = null,
    val localRelativePath: String? = null,
    val hasLinkedAsset: Boolean = false,
    val isLocalFileMissing: Boolean = false,
    val confirmedCrop: OcrNormalizedCropRect? = null,
    val cropProfileId: String? = null,
    val isPhotoPickerLaunchPending: Boolean = false,
    val isPhotoPickerRequestActive: Boolean = false,
    val isValidationInProgress: Boolean = false,
    val isDuplicateDetectionInProgress: Boolean = false,
    val isPreservationInProgress: Boolean = false,
    val photoPickerError: MatchLobbyScreenshotIntakeError? = null,
    val imageValidationError: ImageValidationError? = null,
    val duplicateError: MatchLobbyScreenshotDuplicateError? = null,
    val preservationError: MatchLobbyScreenshotPreservationError? = null,
) {
    val hasConfirmedCrop: Boolean
        get() = confirmedCrop != null && cropProfileId == OcrCropValidationProfiles.Lobby.id

    val isBusy: Boolean
        get() = isPhotoPickerLaunchPending ||
            isPhotoPickerRequestActive ||
            isValidationInProgress ||
            isDuplicateDetectionInProgress ||
            isPreservationInProgress
}

data class MatchLobbyScreenshotIntakeUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val status: MatchStatus? = null,
    val slots: List<MatchLobbyScreenshotSlotUiState> = defaultMatchLobbyScreenshotSlots(),
    val pendingCropNavigationSlotIndex: Int? = null,
    val intakeError: MatchLobbyScreenshotIntakeError? = null,
    val isSavingLobbyTemplate: Boolean = false,
    val lobbyTemplateSaveStatus: MatchLobbyTemplateSaveStatus? = null,
) {
    val isFinalized: Boolean
        get() = status == MatchStatus.FINALIZED

    fun slot(index: Int): MatchLobbyScreenshotSlotUiState? = slots.firstOrNull { it.index == index }

    val canSaveLobbyForNextMatches: Boolean
        get() = isAvailable &&
            status == MatchStatus.DRAFT &&
            !isSavingLobbyTemplate &&
            slots.size == 3 &&
            slots.all { slot ->
                slot.index in 1..3 &&
                    slot.hasLinkedAsset &&
                    !slot.isLocalFileMissing &&
                    !slot.selectedScreenshotUri.isNullOrBlank() &&
                    slot.hasConfirmedCrop &&
                    !slot.isBusy
            }

    fun replaceSlot(
        index: Int,
        transform: (MatchLobbyScreenshotSlotUiState) -> MatchLobbyScreenshotSlotUiState,
    ): MatchLobbyScreenshotIntakeUiState = copy(
        slots = slots.map { slot -> if (slot.index == index) transform(slot) else slot },
    )
}

fun defaultMatchLobbyScreenshotSlots(): List<MatchLobbyScreenshotSlotUiState> =
    (1..3).map(::MatchLobbyScreenshotSlotUiState)
