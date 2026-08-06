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
const val ROSTER_SCREENSHOT_INTAKE_CROP_STATUS_TEST_TAG_PREFIX = "roster_screenshot_intake_crop_status_"
const val ROSTER_SCREENSHOT_INTAKE_CROP_INPUT_TEST_TAG_PREFIX = "roster_screenshot_intake_crop_input_"
const val ROSTER_SCREENSHOT_INTAKE_OPEN_CROP_BUTTON_TEST_TAG_PREFIX = "roster_screenshot_intake_open_crop_"
const val ROSTER_SCREENSHOT_INTAKE_SET_CROP_BUTTON_TEST_TAG_PREFIX = "roster_screenshot_intake_set_crop_"
const val ROSTER_SCREENSHOT_INTAKE_CLEAR_CROP_BUTTON_TEST_TAG_PREFIX = "roster_screenshot_intake_clear_crop_"

@Composable
fun RosterScreenshotIntakeRoute(
    tournamentId: String,
    onOpenCropEditor: (Int) -> Unit,
    viewModel: RosterScreenshotIntakeViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.pendingCropNavigationSlotIndex) {
        val slotIndex = uiState.pendingCropNavigationSlotIndex ?: return@LaunchedEffect
        viewModel.onCropNavigationHandled()
        onOpenCropEditor(slotIndex)
    }
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
        onOpenCropEditor = viewModel::requestCropEditor,
        onClearCrop = viewModel::clearCrop,
    )
}

@Composable
fun RosterScreenshotIntakeSection(
    uiState: RosterScreenshotIntakeUiState,
    onSelectImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onOpenCropEditor: (Int) -> Unit = {},
    onClearCrop: (Int) -> Unit = {},
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
                onClearCrop = onClearCrop,
                onOpenCropEditor = onOpenCropEditor,
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
    onClearCrop: (Int) -> Unit,
    onOpenCropEditor: (Int) -> Unit,
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
        if (slot.hasValidatedImage) {
            RosterScreenshotCropControls(
                slot = slot,
                onOpenCropEditor = onOpenCropEditor,
                onClearCrop = onClearCrop,
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

@Composable
private fun RosterScreenshotCropControls(
    slot: RosterScreenshotSlotUiState,
    onOpenCropEditor: (Int) -> Unit,
    onClearCrop: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.roster_screenshot_crop_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(slot.cropStatusStringRes()),
        modifier = Modifier.testTag(ROSTER_SCREENSHOT_INTAKE_CROP_STATUS_TEST_TAG_PREFIX + slot.index),
    )
    slot.cropError?.let { error ->
        Text(
            text = stringResource(error.toStringRes()),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(ROSTER_SCREENSHOT_INTAKE_ERROR_TEST_TAG_PREFIX + "crop_" + slot.index),
        )
    }
    Button(
        onClick = { onOpenCropEditor(slot.index) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ROSTER_SCREENSHOT_INTAKE_OPEN_CROP_BUTTON_TEST_TAG_PREFIX + slot.index),
    ) {
        Text(
            text = stringResource(
                if (slot.cropState is RosterScreenshotCropState.Set) {
                    R.string.roster_screenshot_crop_edit_action
                } else {
                    R.string.roster_screenshot_crop_open_action
                },
            ),
        )
    }
    Button(
        onClick = { onClearCrop(slot.index) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ROSTER_SCREENSHOT_INTAKE_CLEAR_CROP_BUTTON_TEST_TAG_PREFIX + slot.index),
    ) {
        Text(text = stringResource(R.string.roster_screenshot_crop_clear_action))
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

private fun RosterScreenshotSlotUiState.cropStatusStringRes(): Int = when {
    isCropReady -> R.string.roster_screenshot_crop_ready
    else -> R.string.roster_screenshot_crop_not_set
}

private fun RosterScreenshotCropError.toStringRes(): Int = when (this) {
    RosterScreenshotCropError.MISSING_SELECTED_IMAGE -> R.string.roster_screenshot_crop_missing_image
    RosterScreenshotCropError.INVALID_NUMBER -> R.string.roster_screenshot_crop_invalid_number
    RosterScreenshotCropError.NON_FINITE_VALUE -> R.string.roster_screenshot_crop_non_finite
    RosterScreenshotCropError.OUT_OF_BOUNDS -> R.string.roster_screenshot_crop_out_of_bounds
    RosterScreenshotCropError.INVALID_EDGES -> R.string.roster_screenshot_crop_invalid_edges
    RosterScreenshotCropError.TOO_SMALL -> R.string.roster_screenshot_crop_too_small
}
