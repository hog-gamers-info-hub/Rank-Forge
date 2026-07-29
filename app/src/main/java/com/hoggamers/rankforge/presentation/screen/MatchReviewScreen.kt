package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_REVIEW_SCREEN_TEST_TAG = "match_review_screen"
const val MATCH_REVIEW_ROW_TEST_TAG_PREFIX = "match_review_row_"
const val MATCH_REVIEW_VALID_STATUS_TEST_TAG = "match_review_valid_status"
const val MATCH_REVIEW_ISSUES_STATUS_TEST_TAG = "match_review_issues_status"
const val MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG = "match_review_placements_action"
const val MATCH_REVIEW_KILLS_ACTION_TEST_TAG = "match_review_kills_action"
const val MATCH_REVIEW_DETAILS_ACTION_TEST_TAG = "match_review_details_action"
const val MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG = "match_review_finalize_action"
const val MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG = "match_review_finalize_confirm_action"
const val MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG = "match_review_finalized_status"
const val MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG = "match_review_correction_action"
const val MATCH_REVIEW_CORRECTION_CONFIRM_ACTION_TEST_TAG = "match_review_correction_confirm_action"
const val MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG = "match_review_correction_history"
const val MATCH_REVIEW_PHOTO_PICKER_ACTION_TEST_TAG = "match_review_photo_picker_action"
const val MATCH_REVIEW_SELECTED_SCREENSHOT_TEST_TAG = "match_review_selected_screenshot"
const val MATCH_REVIEW_PHOTO_PICKER_ERROR_TEST_TAG = "match_review_photo_picker_error"
const val MATCH_REVIEW_SCREENSHOT_VALIDATION_IN_PROGRESS_TEST_TAG = "match_review_screenshot_validation_in_progress"
const val MATCH_REVIEW_LINK_SCREENSHOT_ACTION_TEST_TAG = "match_review_link_screenshot_action"
const val MATCH_REVIEW_REPLACE_SCREENSHOT_ACTION_TEST_TAG = "match_review_replace_screenshot_action"
const val MATCH_REVIEW_UNLINK_SCREENSHOT_ACTION_TEST_TAG = "match_review_unlink_screenshot_action"
const val MATCH_REVIEW_LINKED_SCREENSHOT_TEST_TAG = "match_review_linked_screenshot"
const val MATCH_REVIEW_SCREENSHOT_LINK_ERROR_TEST_TAG = "match_review_screenshot_link_error"
const val MATCH_REVIEW_SCREENSHOT_DUPLICATE_IN_PROGRESS_TEST_TAG = "match_review_screenshot_duplicate_in_progress"
const val MATCH_REVIEW_SCREENSHOT_DUPLICATE_ERROR_TEST_TAG = "match_review_screenshot_duplicate_error"
const val MATCH_REVIEW_SCREENSHOT_DUPLICATE_INFO_TEST_TAG = "match_review_screenshot_duplicate_info"
const val MATCH_REVIEW_SCREENSHOT_PRESERVATION_IN_PROGRESS_TEST_TAG = "match_review_screenshot_preservation_in_progress"
const val MATCH_REVIEW_SCREENSHOT_PRESERVED_TEST_TAG = "match_review_screenshot_preserved"
const val MATCH_REVIEW_SCREENSHOT_PRESERVATION_ERROR_TEST_TAG = "match_review_screenshot_preservation_error"
const val MATCH_REVIEW_SCREENSHOT_UPLOAD_IN_PROGRESS_TEST_TAG = "match_review_screenshot_upload_in_progress"
const val MATCH_REVIEW_SCREENSHOT_UPLOADED_TEST_TAG = "match_review_screenshot_uploaded"
const val MATCH_REVIEW_SCREENSHOT_UPLOAD_ERROR_TEST_TAG = "match_review_screenshot_upload_error"
const val MATCH_REVIEW_SCREENSHOT_UPLOAD_RETRY_ACTION_TEST_TAG = "match_review_screenshot_upload_retry_action"
const val MATCH_REVIEW_SCREENSHOT_METADATA_RESTORED_TEST_TAG = "match_review_screenshot_metadata_restored"
const val MATCH_REVIEW_SCREENSHOT_LOCAL_MISSING_TEST_TAG = "match_review_screenshot_local_missing"

@Composable
fun MatchReviewRoute(
    tournamentId: String,
    matchId: String,
    onBackToDetails: () -> Unit,
    onEnterPlacements: (String, String) -> Unit,
    onEnterKills: (String, String) -> Unit,
    onStartCorrection: (String, String) -> Unit,
    viewModel: MatchReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId) {
        viewModel.load(tournamentId, matchId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri -> viewModel.onPhotoPickerResult(selectedUri?.toString()) },
    )
    LaunchedEffect(uiState.isPhotoPickerLaunchPending) {
        if (uiState.isPhotoPickerLaunchPending) {
            viewModel.onPhotoPickerLaunchHandled()
            try {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } catch (_: Exception) {
                viewModel.onPhotoPickerLaunchFailed()
            }
        }
    }
    LaunchedEffect(uiState.navigation) {
        when (uiState.navigation) {
            MatchReviewNavigation.PLACEMENTS -> {
                viewModel.onNavigationHandled()
                onEnterPlacements(tournamentId, matchId)
            }
            MatchReviewNavigation.KILLS -> {
                viewModel.onNavigationHandled()
                onEnterKills(tournamentId, matchId)
            }
            MatchReviewNavigation.CORRECTION -> {
                viewModel.onNavigationHandled()
                onStartCorrection(tournamentId, matchId)
            }
            MatchReviewNavigation.DETAILS -> {
                viewModel.onNavigationHandled()
                onBackToDetails()
            }
            null -> Unit
        }
    }
    BackHandler(onBack = viewModel::onBackToDetails)

    MatchReviewScreen(
        uiState = uiState,
        onEnterPlacements = viewModel::openPlacements,
        onEnterKills = viewModel::openKills,
        onStartCorrection = viewModel::openCorrection,
        onBackToDetails = viewModel::onBackToDetails,
        onFinalize = viewModel::finalize,
        onSelectScreenshot = viewModel::requestPhotoPicker,
        onLinkScreenshot = viewModel::linkScreenshot,
        onUnlinkScreenshot = viewModel::unlinkScreenshot,
        onRetryScreenshotUpload = viewModel::retryScreenshotUpload,
    )
}

@Composable
fun MatchReviewScreen(
    uiState: MatchReviewUiState,
    onEnterPlacements: () -> Unit,
    onEnterKills: () -> Unit,
    onStartCorrection: () -> Unit = {},
    onBackToDetails: () -> Unit,
    onFinalize: () -> Unit = {},
    onSelectScreenshot: () -> Unit = {},
    onLinkScreenshot: () -> Unit = {},
    onUnlinkScreenshot: () -> Unit = {},
    onRetryScreenshotUpload: () -> Unit = {},
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.match_review_loading),
        )
        uiState.isNotFound -> MatchReviewNotFoundState(onBackToDetails)
        uiState.isAvailable -> MatchReviewContent(
            uiState = uiState,
            onEnterPlacements = onEnterPlacements,
            onEnterKills = onEnterKills,
            onStartCorrection = onStartCorrection,
            onBackToDetails = onBackToDetails,
            onFinalize = onFinalize,
            onSelectScreenshot = onSelectScreenshot,
            onLinkScreenshot = onLinkScreenshot,
            onUnlinkScreenshot = onUnlinkScreenshot,
            onRetryScreenshotUpload = onRetryScreenshotUpload,
        )
    }
}

@Composable
private fun MatchReviewContent(
    uiState: MatchReviewUiState,
    onEnterPlacements: () -> Unit,
    onEnterKills: () -> Unit,
    onStartCorrection: () -> Unit,
    onBackToDetails: () -> Unit,
    onFinalize: () -> Unit,
    onSelectScreenshot: () -> Unit,
    onLinkScreenshot: () -> Unit,
    onUnlinkScreenshot: () -> Unit,
    onRetryScreenshotUpload: () -> Unit,
) {
    var showFinalizeConfirmation by remember { mutableStateOf(false) }
    var showCorrectionConfirmation by remember { mutableStateOf(false) }

    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_REVIEW_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.match_review_title, uiState.matchNumber ?: 0),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(
            text = stringResource(
                R.string.match_review_status_value,
                stringResource(
                    if (uiState.status == com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED) {
                        R.string.match_status_finalized
                    } else {
                        R.string.match_status_draft
                    },
                ),
            ),
            modifier = if (uiState.status == com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED) {
                Modifier.testTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG)
            } else {
                Modifier
            },
        )
        if (uiState.status == MatchStatus.FINALIZED) {
            Text(text = stringResource(R.string.match_review_finalized_read_only))
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        if (uiState.isValid) {
            Text(
                text = stringResource(R.string.match_review_valid_status),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(MATCH_REVIEW_VALID_STATUS_TEST_TAG),
            )
        } else {
            Text(
                text = stringResource(R.string.match_review_issues_status),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_ISSUES_STATUS_TEST_TAG),
            )
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        uiState.rows.forEach { row ->
            MatchReviewRow(row)
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        if (uiState.correctionHistory.isNotEmpty()) {
            MatchCorrectionHistory(uiState.correctionHistory)
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        Button(
            onClick = onSelectScreenshot,
            enabled = !uiState.isPhotoPickerRequestActive,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_REVIEW_PHOTO_PICKER_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.match_review_select_screenshot_action))
        }
        if (uiState.isScreenshotValidationInProgress) {
            Text(
                text = stringResource(R.string.match_review_screenshot_validating),
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_VALIDATION_IN_PROGRESS_TEST_TAG),
            )
        }
        if (uiState.isSelectedScreenshotValidated) {
            Text(
                text = stringResource(R.string.match_review_screenshot_selected_and_validated),
                modifier = Modifier.testTag(MATCH_REVIEW_SELECTED_SCREENSHOT_TEST_TAG),
            )
        }
        if (uiState.photoPickerError != null) {
            Text(
                text = stringResource(uiState.photoPickerError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_PHOTO_PICKER_ERROR_TEST_TAG),
            )
        }
        if (uiState.imageValidationError != null) {
            Text(
                text = stringResource(uiState.imageValidationError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_PHOTO_PICKER_ERROR_TEST_TAG),
            )
        }
        if (uiState.hasLinkedScreenshot) {
            Text(
                text = stringResource(R.string.match_review_screenshot_linked),
                modifier = Modifier.testTag(MATCH_REVIEW_LINKED_SCREENSHOT_TEST_TAG),
            )
            if (uiState.screenshotMetadata != null) {
                Text(
                    text = stringResource(R.string.match_review_screenshot_metadata_restored),
                    modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_METADATA_RESTORED_TEST_TAG),
                )
            }
            if (uiState.isEditable) {
                if (
                    uiState.isSelectedScreenshotValidated &&
                    uiState.selectedScreenshotUri != uiState.linkedScreenshotUri
                ) {
                    Button(
                        onClick = onLinkScreenshot,
                        enabled = !uiState.isScreenshotDuplicateDetectionInProgress &&
                            !uiState.isScreenshotPreservationInProgress &&
                            !uiState.isScreenshotUploadInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(MATCH_REVIEW_REPLACE_SCREENSHOT_ACTION_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.match_review_replace_screenshot_action))
                    }
                }
                TextButton(
                    onClick = onUnlinkScreenshot,
                    enabled = !uiState.isScreenshotDuplicateDetectionInProgress &&
                        !uiState.isScreenshotPreservationInProgress &&
                        !uiState.isScreenshotUploadInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MATCH_REVIEW_UNLINK_SCREENSHOT_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.match_review_unlink_screenshot_action))
                }
            }
        } else if (uiState.isEditable && uiState.isSelectedScreenshotValidated) {
            Button(
                onClick = onLinkScreenshot,
                enabled = !uiState.isScreenshotDuplicateDetectionInProgress &&
                    !uiState.isScreenshotPreservationInProgress &&
                    !uiState.isScreenshotUploadInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_LINK_SCREENSHOT_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.match_review_screenshot_link_action))
            }
        }
        if (uiState.status == MatchStatus.FINALIZED && uiState.isSelectedScreenshotValidated) {
            Text(text = stringResource(R.string.match_review_screenshot_link_finalized_protected))
        }
        if (uiState.screenshotLinkError != null) {
            Text(
                text = stringResource(uiState.screenshotLinkError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_LINK_ERROR_TEST_TAG),
            )
        }
        if (uiState.isScreenshotDuplicateDetectionInProgress) {
            Text(
                text = stringResource(R.string.match_review_screenshot_duplicate_checking),
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_DUPLICATE_IN_PROGRESS_TEST_TAG),
            )
        }
        if (uiState.isScreenshotPreservationInProgress) {
            Text(
                text = stringResource(R.string.match_review_screenshot_preservation_checking),
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_PRESERVATION_IN_PROGRESS_TEST_TAG),
            )
        }
        if (uiState.isScreenshotLocallyPreserved) {
            Text(
                text = stringResource(R.string.match_review_screenshot_preserved),
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_PRESERVED_TEST_TAG),
            )
        }
        if (uiState.isPreservedScreenshotMissing) {
            Text(
                text = stringResource(R.string.match_review_screenshot_local_missing),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_LOCAL_MISSING_TEST_TAG),
            )
        }
        if (uiState.screenshotPreservationError != null) {
            Text(
                text = stringResource(uiState.screenshotPreservationError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_PRESERVATION_ERROR_TEST_TAG),
            )
        }
        if (uiState.isScreenshotUploadInProgress) {
            Text(
                text = stringResource(R.string.match_review_screenshot_uploading),
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_UPLOAD_IN_PROGRESS_TEST_TAG),
            )
        }
        if (uiState.isScreenshotUploaded) {
            Text(
                text = stringResource(R.string.match_review_screenshot_uploaded),
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_UPLOADED_TEST_TAG),
            )
        }
        if (uiState.screenshotUploadError != null) {
            Text(
                text = stringResource(uiState.screenshotUploadError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_UPLOAD_ERROR_TEST_TAG),
            )
            if (uiState.isEditable && uiState.hasLinkedScreenshot) {
                TextButton(
                    onClick = onRetryScreenshotUpload,
                    enabled = !uiState.isScreenshotUploadInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MATCH_REVIEW_SCREENSHOT_UPLOAD_RETRY_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.match_review_screenshot_upload_retry_action))
                }
            }
        }
        if (uiState.screenshotDuplicateInfo != null) {
            Text(
                text = stringResource(uiState.screenshotDuplicateInfo.toMessageRes()),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_DUPLICATE_INFO_TEST_TAG),
            )
        }
        if (uiState.screenshotDuplicateError != null) {
            Text(
                text = stringResource(uiState.screenshotDuplicateError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_SCREENSHOT_DUPLICATE_ERROR_TEST_TAG),
            )
        }
        if (uiState.isEditable) {
            Button(
                onClick = onEnterPlacements,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.edit_match_placements_action))
            }
            TextButton(
                onClick = onEnterKills,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.edit_match_kills_action))
            }
            Button(
                onClick = { showFinalizeConfirmation = true },
                enabled = uiState.isValid && !uiState.isFinalizing &&
                    !uiState.isScreenshotPreservationInProgress &&
                    !uiState.isScreenshotUploadInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG),
            ) {
                Text(
                    stringResource(
                        if (uiState.isFinalizing) {
                            R.string.match_review_finalizing_action
                        } else {
                            R.string.match_review_finalize_action
                        },
                    ),
                )
            }
            if (!uiState.isValid) {
                Text(
                    text = stringResource(R.string.match_review_finalize_blocked_message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (uiState.status == MatchStatus.FINALIZED) {
            Button(
                onClick = { showCorrectionConfirmation = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.start_match_correction_action))
            }
        }
        if (uiState.finalizationError != null) {
            Text(
                text = stringResource(uiState.finalizationError.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(
            onClick = onBackToDetails,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.back_to_match_details_action))
        }
    }

    if (showFinalizeConfirmation) {
        AlertDialog(
            onDismissRequest = { showFinalizeConfirmation = false },
            title = { Text(stringResource(R.string.match_review_finalize_title)) },
            text = { Text(stringResource(R.string.match_review_finalize_message)) },
            dismissButton = {
                TextButton(onClick = { showFinalizeConfirmation = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinalizeConfirmation = false
                        onFinalize()
                    },
                    modifier = Modifier.testTag(MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.confirm_finalize_match_action))
                }
            },
        )
    }
    if (showCorrectionConfirmation) {
        AlertDialog(
            onDismissRequest = { showCorrectionConfirmation = false },
            title = { Text(stringResource(R.string.start_match_correction_title)) },
            text = { Text(stringResource(R.string.start_match_correction_message)) },
            dismissButton = {
                TextButton(onClick = { showCorrectionConfirmation = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCorrectionConfirmation = false
                        onStartCorrection()
                    },
                    modifier = Modifier.testTag(MATCH_REVIEW_CORRECTION_CONFIRM_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.confirm_start_match_correction_action))
                }
            },
        )
    }
}

@Composable
private fun MatchCorrectionHistory(history: List<MatchCorrectionRecord>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.match_correction_history_title),
            style = MaterialTheme.typography.titleMedium,
        )
        history.forEachIndexed { index, correction ->
            Text(stringResource(R.string.match_correction_revision_label, index + 1))
            val previousPlacements = correction.previousPlacements.associateBy { it.teamSlotNumber }
            val previousKills = correction.previousKills.associateBy { it.teamSlotNumber }
            val correctedPlacements = correction.correctedPlacements.associateBy { it.teamSlotNumber }
            val correctedKills = correction.correctedKills.associateBy { it.teamSlotNumber }
            com.hoggamers.rankforge.domain.tournament.TeamSlot.SLOT_NUMBERS.forEach { slotNumber ->
                Text(
                    stringResource(
                        R.string.match_correction_previous_value,
                        slotNumber,
                        previousPlacements[slotNumber]?.position ?: 0,
                        previousKills[slotNumber]?.kills ?: 0,
                    ),
                )
                Text(
                    stringResource(
                        R.string.match_correction_corrected_value,
                        slotNumber,
                        correctedPlacements[slotNumber]?.position ?: 0,
                        correctedKills[slotNumber]?.kills ?: 0,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MatchReviewRow(row: MatchReviewRowUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
    ) {
        Text(
            text = stringResource(
                R.string.match_review_team_label,
                row.teamSlotNumber,
                row.teamName.ifBlank { stringResource(R.string.empty_team_slot_subtitle) },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (row.playerNames.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.match_player_names_value,
                    row.playerNames.joinToString(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(
                R.string.match_review_placement_value,
                row.placementInput.ifBlank { stringResource(R.string.match_review_empty_value) },
            ),
        )
        Text(
            stringResource(
                R.string.match_review_kills_value,
                row.killsInput.ifBlank { stringResource(R.string.match_review_empty_value) },
            ),
        )
        if (row.validationErrors.isEmpty()) {
            Text(
                text = stringResource(R.string.match_review_row_valid),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            row.validationErrors
                .sortedBy { it.ordinal }
                .forEach { error ->
                    Text(
                        text = stringResource(
                            R.string.match_review_row_issue,
                            stringResource(error.toMessageRes()),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
        }
    }
}

@Composable
private fun MatchReviewNotFoundState(onBackToDetails: () -> Unit) {
    RankForgeScreenContainer {
        Text(
            text = stringResource(R.string.match_review_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_review_not_found_message))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackToDetails) {
            Text(text = stringResource(R.string.back_to_match_details_action))
        }
    }
}

private fun MatchResultValidationError.toMessageRes(): Int = when (this) {
    MatchResultValidationError.MISSING_TEAM_RESULT_ROW -> R.string.match_validation_missing_team_result_row
    MatchResultValidationError.DUPLICATE_TEAM -> R.string.match_validation_duplicate_team
    MatchResultValidationError.MISSING_PLACEMENT -> R.string.match_validation_missing_placement
    MatchResultValidationError.DUPLICATE_PLACEMENT -> R.string.match_validation_duplicate_placement
    MatchResultValidationError.INVALID_PLACEMENT -> R.string.match_validation_invalid_placement
    MatchResultValidationError.MISSING_KILLS -> R.string.match_validation_missing_kills
    MatchResultValidationError.INVALID_KILLS -> R.string.match_validation_invalid_kills
}

private fun com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError.toMessageRes(): Int = when (this) {
    com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError.MATCH_NOT_FOUND ->
        R.string.match_review_finalize_match_not_found_error
    com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError.MATCH_NOT_DRAFT ->
        R.string.match_review_finalize_not_draft_error
    com.hoggamers.rankforge.domain.tournament.FinalizeMatchGlobalError.INVALID_DATA ->
        R.string.match_review_finalize_invalid_data_error
}

private fun PhotoPickerError.toMessageRes(): Int = when (this) {
    PhotoPickerError.LAUNCH_FAILED -> R.string.match_review_photo_picker_launch_failed_error
}

private fun ImageValidationError.toMessageRes(): Int = when (this) {
    ImageValidationError.EMPTY_URI -> R.string.match_review_image_validation_empty_uri_error
    ImageValidationError.NON_IMAGE_CONTENT -> R.string.match_review_image_validation_non_image_error
    ImageValidationError.UNSUPPORTED_FORMAT -> R.string.match_review_image_validation_unsupported_format_error
    ImageValidationError.UNREADABLE_URI -> R.string.match_review_image_validation_unreadable_error
    ImageValidationError.DECODE_FAILED -> R.string.match_review_image_validation_decode_failed_error
    ImageValidationError.INVALID_DIMENSIONS -> R.string.match_review_image_validation_invalid_dimensions_error
    ImageValidationError.IMAGE_TOO_LARGE -> R.string.match_review_image_validation_too_large_error
}

private fun ScreenshotLinkError.toMessageRes(): Int = when (this) {
    ScreenshotLinkError.INVALID_IMAGE -> R.string.match_review_screenshot_link_invalid_image_error
    ScreenshotLinkError.MISSING_TOURNAMENT_ID -> R.string.match_review_screenshot_link_missing_tournament_error
    ScreenshotLinkError.MISSING_MATCH_ID -> R.string.match_review_screenshot_link_missing_match_error
    ScreenshotLinkError.FINALIZED_MATCH -> R.string.match_review_screenshot_link_finalized_error
}

private fun ScreenshotDuplicateInfo.toMessageRes(): Int = when (this) {
    ScreenshotDuplicateInfo.ALREADY_LINKED_TO_THIS_MATCH ->
        R.string.match_review_screenshot_duplicate_same_match
}

private fun ScreenshotDuplicateError.toMessageRes(): Int = when (this) {
    ScreenshotDuplicateError.FINGERPRINT_FAILED ->
        R.string.match_review_screenshot_duplicate_fingerprint_failed
    ScreenshotDuplicateError.LINKED_TO_OTHER_MATCH ->
        R.string.match_review_screenshot_duplicate_other_match
    ScreenshotDuplicateError.STATE_CONFLICT ->
        R.string.match_review_screenshot_duplicate_state_conflict
}

private fun ScreenshotPreservationError.toMessageRes(): Int = when (this) {
    ScreenshotPreservationError.SOURCE_READ_FAILED ->
        R.string.match_review_screenshot_preservation_source_read_failed
    ScreenshotPreservationError.COPY_FAILED ->
        R.string.match_review_screenshot_preservation_copy_failed
    ScreenshotPreservationError.ATOMIC_MOVE_FAILED ->
        R.string.match_review_screenshot_preservation_atomic_move_failed
    ScreenshotPreservationError.CLEANUP_FAILED ->
        R.string.match_review_screenshot_preservation_cleanup_failed
    ScreenshotPreservationError.ROOM_WRITE_FAILED ->
        R.string.match_review_screenshot_metadata_room_write_failed
    ScreenshotPreservationError.LOCAL_FILE_MISSING ->
        R.string.match_review_screenshot_local_missing
    ScreenshotPreservationError.INVALID_RELATIVE_PATH ->
        R.string.match_review_screenshot_metadata_invalid_relative_path
    ScreenshotPreservationError.MISSING_TOURNAMENT_ID ->
        R.string.match_review_screenshot_preservation_missing_tournament
    ScreenshotPreservationError.MISSING_MATCH_ID ->
        R.string.match_review_screenshot_preservation_missing_match
    ScreenshotPreservationError.FINALIZED_MATCH ->
        R.string.match_review_screenshot_preservation_finalized
}

private fun ScreenshotUploadError.toMessageRes(): Int = when (this) {
    ScreenshotUploadError.MISSING_AUTH_SESSION ->
        R.string.match_review_screenshot_upload_missing_auth
    ScreenshotUploadError.MISSING_LOCAL_FILE ->
        R.string.match_review_screenshot_upload_missing_local_file
    ScreenshotUploadError.MISSING_TOURNAMENT_ID ->
        R.string.match_review_screenshot_upload_missing_tournament
    ScreenshotUploadError.MISSING_MATCH_ID ->
        R.string.match_review_screenshot_upload_missing_match
    ScreenshotUploadError.UNSUPPORTED_FORMAT ->
        R.string.match_review_screenshot_upload_unsupported_format
    ScreenshotUploadError.LOCAL_FILE_READ_FAILED ->
        R.string.match_review_screenshot_upload_local_file_read_failed
    ScreenshotUploadError.NETWORK ->
        R.string.match_review_screenshot_upload_network_failed
    ScreenshotUploadError.AUTHORIZATION ->
        R.string.match_review_screenshot_upload_authorization_failed
    ScreenshotUploadError.UPLOAD_FAILED ->
        R.string.match_review_screenshot_upload_failed
    ScreenshotUploadError.CLOUD_METADATA_WRITE_FAILED ->
        R.string.match_review_screenshot_metadata_cloud_write_failed
    ScreenshotUploadError.RLS_DENIED ->
        R.string.match_review_screenshot_metadata_rls_denied
}
