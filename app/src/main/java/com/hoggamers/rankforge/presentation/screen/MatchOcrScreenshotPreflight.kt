package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

enum class OcrScreenshotPreflightIssue {
    MISSING,
    LOCAL_FILE_MISSING,
    CROP_REQUIRED,
    PROCESSING,
}

sealed interface OcrScreenshotPreflightIdentity {
    data class Lobby(val index: Int) : OcrScreenshotPreflightIdentity
    data class Result(val role: MatchResultScreenshotRole) : OcrScreenshotPreflightIdentity
}

data class OcrScreenshotPreflightItem(
    val identity: OcrScreenshotPreflightIdentity,
    val issue: OcrScreenshotPreflightIssue,
) {
    val userFacingNumber: Int
        get() = when (val value = identity) {
            is OcrScreenshotPreflightIdentity.Lobby -> value.index
            is OcrScreenshotPreflightIdentity.Result -> when (value.role) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER -> 1
                MatchResultScreenshotRole.MATCH_RESULT_LOWER -> 2
            }
        }
}

fun classifyOcrScreenshotPreflight(
    lobbySlots: List<MatchLobbyScreenshotSlotUiState>,
    resultSlots: List<MatchResultScreenshotSlotUiState>,
): List<OcrScreenshotPreflightItem> = buildList {
    (1..3).forEach { index ->
        val slot = lobbySlots.firstOrNull { it.index == index }
        val issue = if (slot == null) {
            OcrScreenshotPreflightIssue.MISSING
        } else {
            classifyIssue(
                hasLinkedAsset = slot.hasLinkedAsset,
                hasConfirmedCrop = slot.hasConfirmedCrop,
                isLocalFileMissing = slot.isLocalFileMissing,
                isProcessing = slot.isOcrProcessing,
                requireConfirmedCrop = true,
            )
        }
        issue?.let {
            add(
                OcrScreenshotPreflightItem(
                    identity = OcrScreenshotPreflightIdentity.Lobby(index),
                    issue = it,
                ),
            )
        }
    }
    listOf(
        MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        MatchResultScreenshotRole.MATCH_RESULT_LOWER,
    ).forEach { role ->
        val slot = resultSlots.firstOrNull { it.role == role }
        val issue = if (slot == null) {
            OcrScreenshotPreflightIssue.MISSING
        } else {
            classifyIssue(
                hasLinkedAsset = slot.hasLinkedAsset,
                hasConfirmedCrop = slot.hasConfirmedCrop,
                isLocalFileMissing = slot.isLocalFileMissing,
                isProcessing = slot.isOcrProcessing,
                requireConfirmedCrop = false,
            )
        }
        issue?.let {
            add(
                OcrScreenshotPreflightItem(
                    identity = OcrScreenshotPreflightIdentity.Result(role),
                    issue = it,
                ),
            )
        }
    }
}

private fun classifyIssue(
    hasLinkedAsset: Boolean,
    hasConfirmedCrop: Boolean,
    isLocalFileMissing: Boolean,
    isProcessing: Boolean,
    requireConfirmedCrop: Boolean,
): OcrScreenshotPreflightIssue? = when {
    isProcessing -> OcrScreenshotPreflightIssue.PROCESSING
    !hasLinkedAsset -> OcrScreenshotPreflightIssue.MISSING
    isLocalFileMissing -> OcrScreenshotPreflightIssue.LOCAL_FILE_MISSING
    requireConfirmedCrop && !hasConfirmedCrop -> OcrScreenshotPreflightIssue.CROP_REQUIRED
    else -> null
}

private val MatchLobbyScreenshotSlotUiState.isOcrProcessing: Boolean
    get() = isPhotoPickerLaunchPending ||
        isPhotoPickerRequestActive ||
        isValidationInProgress ||
        isDuplicateDetectionInProgress ||
        isPreservationInProgress ||
        (isPreviewPreparationInProgress && !hasConfirmedCrop)

private val MatchResultScreenshotSlotUiState.isOcrProcessing: Boolean
    get() = isPhotoPickerLaunchPending ||
        isPhotoPickerRequestActive ||
        isValidationInProgress ||
        isDuplicateDetectionInProgress ||
        isPreservationInProgress
