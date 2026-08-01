package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord

enum class MatchReviewNavigation {
    PLACEMENTS,
    KILLS,
    OCR_REVIEW,
    CORRECTION,
    DETAILS,
}

enum class PhotoPickerError {
    LAUNCH_FAILED,
}

enum class ScreenshotLinkError {
    INVALID_IMAGE,
    MISSING_TOURNAMENT_ID,
    MISSING_MATCH_ID,
    FINALIZED_MATCH,
}

enum class ScreenshotDuplicateError {
    FINGERPRINT_FAILED,
    LINKED_TO_OTHER_MATCH,
    STATE_CONFLICT,
}

enum class ScreenshotDuplicateInfo {
    ALREADY_LINKED_TO_THIS_MATCH,
}

enum class ScreenshotPreservationError {
    SOURCE_READ_FAILED,
    COPY_FAILED,
    ATOMIC_MOVE_FAILED,
    CLEANUP_FAILED,
    ROOM_WRITE_FAILED,
    LOCAL_FILE_MISSING,
    INVALID_RELATIVE_PATH,
    MISSING_TOURNAMENT_ID,
    MISSING_MATCH_ID,
    FINALIZED_MATCH,
}

enum class ScreenshotUploadError {
    MISSING_AUTH_SESSION,
    MISSING_LOCAL_FILE,
    MISSING_TOURNAMENT_ID,
    MISSING_MATCH_ID,
    UNSUPPORTED_FORMAT,
    LOCAL_FILE_READ_FAILED,
    NETWORK,
    AUTHORIZATION,
    UPLOAD_FAILED,
    CLOUD_METADATA_WRITE_FAILED,
    RLS_DENIED,
}

enum class ScreenshotMetadataLocalUiStatus {
    PRESERVED,
    MISSING,
    CLEANUP_FAILED,
}

enum class ScreenshotMetadataUploadUiStatus {
    PENDING,
    UPLOADED,
    FAILED,
}

data class ScreenshotMetadataUiState(
    val localStatus: ScreenshotMetadataLocalUiStatus,
    val uploadStatus: ScreenshotMetadataUploadUiStatus,
    val revision: Long,
)

data class MatchReviewUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val matchNumber: Int? = null,
    val status: MatchStatus = MatchStatus.DRAFT,
    val rows: List<MatchReviewRowUiState> = emptyList(),
    val correctionHistory: List<MatchCorrectionRecord> = emptyList(),
    val validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
    val navigation: MatchReviewNavigation? = null,
    val isFinalizing: Boolean = false,
    val finalizationError: FinalizeMatchGlobalError? = null,
    val csvExportResult: AndroidExportResult? = null,
    val googleSheetsExportResult: AndroidExportResult? = null,
    val selectedScreenshotUri: String? = null,
    val isPhotoPickerLaunchPending: Boolean = false,
    val isPhotoPickerRequestActive: Boolean = false,
    val photoPickerError: PhotoPickerError? = null,
    val isScreenshotValidationInProgress: Boolean = false,
    val isSelectedScreenshotValidated: Boolean = false,
    val selectedScreenshotMimeType: String? = null,
    val selectedScreenshotWidth: Int? = null,
    val selectedScreenshotHeight: Int? = null,
    val imageValidationError: ImageValidationError? = null,
    val isScreenshotLinked: Boolean = false,
    val linkedScreenshotUri: String? = null,
    val linkedScreenshotFingerprint: String? = null,
    val screenshotLinkError: ScreenshotLinkError? = null,
    val isScreenshotDuplicateDetectionInProgress: Boolean = false,
    val screenshotDuplicateError: ScreenshotDuplicateError? = null,
    val screenshotDuplicateInfo: ScreenshotDuplicateInfo? = null,
    val isScreenshotPreservationInProgress: Boolean = false,
    val isScreenshotLocallyPreserved: Boolean = false,
    val preservedScreenshotRelativePath: String? = null,
    val screenshotMetadata: ScreenshotMetadataUiState? = null,
    val isPreservedScreenshotMissing: Boolean = false,
    val screenshotPreservationError: ScreenshotPreservationError? = null,
    val isScreenshotUploadInProgress: Boolean = false,
    val isScreenshotUploaded: Boolean = false,
    val screenshotUploadObjectPath: String? = null,
    val screenshotUploadError: ScreenshotUploadError? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val isValid: Boolean
        get() = isAvailable && validationErrors.isEmpty()

    val canPrepareMatchCsvExport: Boolean
        get() = status == MatchStatus.FINALIZED && isValid

    val isEditable: Boolean
        get() = isAvailable && status == MatchStatus.DRAFT

    val hasLinkedScreenshot: Boolean
        get() = isScreenshotLinked || linkedScreenshotUri != null

    val canOpenOcrReview: Boolean
        get() = isEditable &&
            !tournamentId.isNullOrBlank() &&
            !matchId.isNullOrBlank() &&
            hasLinkedScreenshot &&
            !isScreenshotDuplicateDetectionInProgress &&
            !isScreenshotPreservationInProgress &&
            !isScreenshotUploadInProgress &&
            !isPreservedScreenshotMissing
}

data class MatchReviewRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val playerNames: List<String> = emptyList(),
    val placementInput: String = "",
    val killsInput: String = "",
    val validationErrors: Set<MatchResultValidationError> = emptySet(),
)
