package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

enum class MatchReviewNavigation {
    PLACEMENTS,
    KILLS,
    OCR_REVIEW,
    CORRECTION,
    DETAILS,
    RESULT_SCREENSHOT_1_CROP,
    RESULT_SCREENSHOT_2_CROP,
}

enum class MatchDeletionUiError {
    TARGET_NOT_FOUND,
    AUTHENTICATION_REQUIRED,
    AUTHORIZATION_FAILURE,
    VALIDATION_FAILURE,
    STORAGE_FAILURE,
    REMOTE_FAILURE,
    LOCAL_CLEANUP_FAILURE,
    PREPARATION_FAILURE,
    UNKNOWN,
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

data class MatchResultScreenshotSlotUiState(
    val role: MatchResultScreenshotRole,
    val selectedScreenshotUri: String? = null,
    val selectedScreenshotMimeType: String? = null,
    val selectedScreenshotWidth: Int? = null,
    val selectedScreenshotHeight: Int? = null,
    val isPhotoPickerLaunchPending: Boolean = false,
    val isPhotoPickerRequestActive: Boolean = false,
    val photoPickerError: PhotoPickerError? = null,
    val isValidationInProgress: Boolean = false,
    val isSelectedScreenshotValidated: Boolean = false,
    val imageValidationError: ImageValidationError? = null,
    val hasLinkedAsset: Boolean = false,
    val linkedScreenshotUri: String? = null,
    val localRelativePath: String? = null,
    val localPreviewUri: String? = null,
    val fingerprint: String? = null,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,
    val metadata: ScreenshotMetadataUiState? = null,
    val localStatus: ScreenshotMetadataLocalUiStatus? = null,
    val uploadStatus: ScreenshotMetadataUploadUiStatus? = null,
    val uploadObjectPath: String? = null,
    val isLocalFileMissing: Boolean = false,
    val isDuplicateDetectionInProgress: Boolean = false,
    val duplicateError: ScreenshotDuplicateError? = null,
    val duplicateInfo: ScreenshotDuplicateInfo? = null,
    val isPreservationInProgress: Boolean = false,
    val preservationError: ScreenshotPreservationError? = null,
    val isUploadInProgress: Boolean = false,
    val uploadError: ScreenshotUploadError? = null,
    val confirmedCrop: OcrNormalizedCropRect? = null,
    val cropProfileId: String? = null,
) {
    val hasConfirmedCrop: Boolean
        get() = confirmedCrop != null && cropProfileId == OcrCropValidationProfiles.MatchResult.id

    val isBusy: Boolean
        get() = isPhotoPickerRequestActive ||
            isValidationInProgress ||
            isDuplicateDetectionInProgress ||
            isPreservationInProgress ||
            isUploadInProgress
}

data class MatchResultScreenshotCropBatch(
    val currentRole: MatchResultScreenshotRole,
    val remainingRoles: List<MatchResultScreenshotRole>,
)

data class MatchResultScreenshotMultiPhotoPickerRequest(
    val requestId: Long,
    val targetRoles: List<MatchResultScreenshotRole>,
    val isLaunchPending: Boolean = true,
)

data class MatchReviewUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val tournamentId: String? = null,
    val matchId: String? = null,
    val activeTeamCount: Int? = null,
    val finalizedParticipantSlotNumbers: Set<Int> = emptySet(),
    val matchNumber: Int? = null,
    val status: MatchStatus = MatchStatus.DRAFT,
    val rows: List<MatchReviewRowUiState> = emptyList(),
    val correctionHistory: List<MatchCorrectionRecord> = emptyList(),
    val validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
    val navigation: MatchReviewNavigation? = null,
    val isDeleting: Boolean = false,
    val deletionError: MatchDeletionUiError? = null,
    val isFinalizing: Boolean = false,
    val finalizationError: FinalizeMatchGlobalError? = null,
    val csvExportResult: AndroidExportResult? = null,
    val googleSheetsExportResult: AndroidExportResult? = null,
    val resultDownloadUiState: ResultDownloadUiState = ResultDownloadUiState.Idle,
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
    val resultScreenshots: List<MatchResultScreenshotSlotUiState> = defaultMatchResultScreenshotSlots(),
    val resultPositionCropPreviews: Map<MatchResultScreenshotRole, MatchResultPositionCropPreviewState> =
        defaultMatchResultPositionCropPreviewStates(),
    val calculatedEvidenceRestoreStatus: CalculatedEvidenceRestoreStatus =
        CalculatedEvidenceRestoreStatus.NOT_REQUESTED,
    val restoredCalculatedEvidence: MatchCalculatedEvidence? = null,
    val restoredLobbyTeamCropPreviews: Map<Int, MatchLobbyTeamCropPreviewResult> = emptyMap(),
    val restoredLobbyTeamNamesBySlot: Map<Int, String> = emptyMap(),
    val pendingResultScreenshotCropBatch: MatchResultScreenshotCropBatch? = null,
    val resultScreenshotMultiPhotoPickerRequest: MatchResultScreenshotMultiPhotoPickerRequest? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && !isAvailable

    val isValid: Boolean
        get() = isAvailable && validationErrors.isEmpty()

    val canPrepareMatchCsvExport: Boolean
        get() = status == MatchStatus.FINALIZED && isValid

    val participantTeamIdentitiesAreValid: Boolean
        get() = finalizedParticipantSlotNumbers.isNotEmpty() &&
            finalizedParticipantSlotNumbers.all { participantSlotNumber ->
                rows.firstOrNull { row -> row.teamSlotNumber == participantSlotNumber }
                    ?.teamName
                    ?.isNotBlank() == true
            }

    val canDownloadResult: Boolean
    get() = canPrepareMatchCsvExport &&
        participantTeamIdentitiesAreValid &&
        !resultDownloadUiState.isBusy

    val isEditable: Boolean
        get() = isAvailable && status == MatchStatus.DRAFT

    val hasLinkedScreenshot: Boolean
        get() = isScreenshotLinked || linkedScreenshotUri != null

    val canOpenOcrReview: Boolean
        get() = isEditable &&
            !tournamentId.isNullOrBlank() &&
            !matchId.isNullOrBlank() &&
            resultScreenshots.isOcrReady(MatchResultScreenshotRole.MATCH_RESULT_UPPER) &&
            resultScreenshots.isOcrReady(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
}

enum class CalculatedEvidenceRestoreStatus {
    NOT_REQUESTED,
    CHECKING,
    NOT_FOUND,
    RESTORED,
    CLEARED,
    FAILED,
}

sealed interface ResultDownloadUiState {
    data object Idle : ResultDownloadUiState

    data class Generating(
        val scope: ResultDownloadScope,
        val format: ResultExportFileFormat,
    ) : ResultDownloadUiState

    data class Saving(
        val format: ResultExportFileFormat,
    ) : ResultDownloadUiState

    data class DestinationLaunchRequested(
        val format: ResultExportFileFormat,
        val suggestedDisplayName: String,
    ) : ResultDownloadUiState

    data class WaitingForDestination(
        val format: ResultExportFileFormat,
        val suggestedDisplayName: String,
    ) : ResultDownloadUiState

    data class Success(
        val format: ResultExportFileFormat,
        val userSelectedDestination: Boolean,
    ) : ResultDownloadUiState

    data class Failure(
        val reason: ResultDownloadFailure,
    ) : ResultDownloadUiState

    val isBusy: Boolean
        get() = this is Generating ||
            this is Saving ||
            this is DestinationLaunchRequested ||
            this is WaitingForDestination
}

private fun List<MatchResultScreenshotSlotUiState>.isOcrReady(
    role: MatchResultScreenshotRole,
): Boolean = firstOrNull { it.role == role }?.let { slot ->
    slot.hasLinkedAsset &&
        slot.hasConfirmedCrop &&
        !slot.isBusy &&
        !slot.isLocalFileMissing
} == true

fun defaultMatchResultScreenshotSlots(): List<MatchResultScreenshotSlotUiState> = listOf(
    MatchResultScreenshotSlotUiState(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
    MatchResultScreenshotSlotUiState(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
)

fun defaultMatchResultPositionCropPreviewStates(): Map<
    MatchResultScreenshotRole,
    MatchResultPositionCropPreviewState,
> = MatchResultScreenshotRole.entries.associateWith {
    MatchResultPositionCropPreviewState.Unavailable(
        MatchResultPositionCropPreviewUnavailableReason.NOT_READY,
    )
}

fun List<MatchResultScreenshotSlotUiState>.slot(
    role: MatchResultScreenshotRole,
): MatchResultScreenshotSlotUiState = firstOrNull { it.role == role }
    ?: MatchResultScreenshotSlotUiState(role)

fun List<MatchResultScreenshotSlotUiState>.replaceSlot(
    role: MatchResultScreenshotRole,
    transform: (MatchResultScreenshotSlotUiState) -> MatchResultScreenshotSlotUiState,
): List<MatchResultScreenshotSlotUiState> = map { slot ->
    if (slot.role == role) transform(slot) else slot
}

data class MatchReviewRowUiState(
    val teamSlotNumber: Int,
    val teamName: String,
    val playerNames: List<String> = emptyList(),
    val placementInput: String = "",
    val killsInput: String = "",
    val validationErrors: Set<MatchResultValidationError> = emptySet(),
)
