package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val ROSTER_SCREENSHOT_INTAKE_SECTION_TEST_TAG = "roster_screenshot_intake_section"
const val ROSTER_SCREENSHOT_INTAKE_SET_STATUS_TEST_TAG = "roster_screenshot_intake_set_status"
const val ROSTER_SCREENSHOT_INTAKE_SELECT_BUTTON_TEST_TAG_PREFIX =
    "roster_screenshot_intake_select_"
const val ROSTER_SCREENSHOT_INTAKE_REMOVE_BUTTON_TEST_TAG_PREFIX =
    "roster_screenshot_intake_remove_"
const val ROSTER_SCREENSHOT_INTAKE_ERROR_TEST_TAG_PREFIX = "roster_screenshot_intake_error_"

@Composable
fun RosterScreenshotIntakeRoute(
    tournamentId: String,
    viewModel: RosterScreenshotIntakeViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
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

    RosterScreenshotIntakeSection(
        uiState = uiState,
        onSelectImage = viewModel::requestPhotoPicker,
        onRemoveImage = viewModel::removeSelectedImage,
    )
}

@Composable
fun RosterScreenshotIntakeSection(
    uiState: RosterScreenshotIntakeUiState,
    onSelectImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ROSTER_SCREENSHOT_INTAKE_SECTION_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.roster_screenshot_intake_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                if (uiState.isCompleteSet) {
                    R.string.roster_screenshot_intake_complete
                } else {
                    R.string.roster_screenshot_intake_incomplete
                },
                uiState.selectedImageCount,
                RosterScreenshotIntakeUiState.REQUIRED_SCREENSHOT_COUNT,
            ),
            modifier = Modifier.testTag(ROSTER_SCREENSHOT_INTAKE_SET_STATUS_TEST_TAG),
        )
        uiState.intakeError?.let { error ->
            Text(
                text = stringResource(error.toStringRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ROSTER_SCREENSHOT_INTAKE_ERROR_TEST_TAG_PREFIX + "intake"),
            )
        }
        uiState.slots.forEach { slot ->
            RosterScreenshotSlot(
                slot = slot,
                enabled = uiState.canSelectImages && !slot.isValidationInProgress,
                onSelectImage = onSelectImage,
                onRemoveImage = onRemoveImage,
            )
        }
    }
}

@Composable
private fun RosterScreenshotSlot(
    slot: RosterScreenshotSlotUiState,
    enabled: Boolean,
    onSelectImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
        Text(
            text = stringResource(R.string.roster_screenshot_intake_slot_label, slot.index),
            style = MaterialTheme.typography.titleMedium,
        )
        when {
            slot.isValidationInProgress -> Text(
                text = stringResource(R.string.roster_screenshot_intake_validating),
            )

            slot.hasValidatedImage -> Text(
                text = stringResource(R.string.roster_screenshot_intake_selected),
            )
        }
        slot.lastValidationError?.let { error ->
            Text(
                text = stringResource(error.toStringRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ROSTER_SCREENSHOT_INTAKE_ERROR_TEST_TAG_PREFIX + slot.index),
            )
        }
        slot.duplicateSelectionState?.let {
            Text(
                text = stringResource(R.string.roster_screenshot_intake_duplicate),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ROSTER_SCREENSHOT_INTAKE_ERROR_TEST_TAG_PREFIX + slot.index),
            )
        }
        Button(
            onClick = { onSelectImage(slot.index) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ROSTER_SCREENSHOT_INTAKE_SELECT_BUTTON_TEST_TAG_PREFIX + slot.index),
        ) {
            Text(
                text = stringResource(
                    if (slot.hasValidatedImage) {
                        R.string.roster_screenshot_intake_replace_action
                    } else {
                        R.string.roster_screenshot_intake_select_action
                    },
                    slot.index,
                ),
            )
        }
        if (slot.hasValidatedImage) {
            Button(
                onClick = { onRemoveImage(slot.index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ROSTER_SCREENSHOT_INTAKE_REMOVE_BUTTON_TEST_TAG_PREFIX + slot.index),
            ) {
                Text(text = stringResource(R.string.roster_screenshot_intake_remove_action, slot.index))
            }
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    }
}

private fun ImageValidationError.toStringRes(): Int = when (this) {
    ImageValidationError.EMPTY_URI -> R.string.roster_screenshot_intake_empty_uri
    ImageValidationError.NON_IMAGE_CONTENT -> R.string.roster_screenshot_intake_non_image
    ImageValidationError.UNSUPPORTED_FORMAT -> R.string.roster_screenshot_intake_unsupported_format
    ImageValidationError.UNREADABLE_URI -> R.string.roster_screenshot_intake_unreadable
    ImageValidationError.DECODE_FAILED -> R.string.roster_screenshot_intake_decode_failed
    ImageValidationError.INVALID_DIMENSIONS -> R.string.roster_screenshot_intake_invalid_dimensions
    ImageValidationError.IMAGE_TOO_LARGE -> R.string.roster_screenshot_intake_too_large
}

private fun RosterScreenshotIntakeError.toStringRes(): Int = when (this) {
    RosterScreenshotIntakeError.MISSING_TOURNAMENT_ID ->
        R.string.roster_screenshot_intake_missing_tournament
    RosterScreenshotIntakeError.PHOTO_PICKER_LAUNCH_FAILED ->
        R.string.roster_screenshot_intake_picker_failed
}
