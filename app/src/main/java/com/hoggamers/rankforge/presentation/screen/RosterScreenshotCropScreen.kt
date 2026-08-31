package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.presentation.theme.RankForgePageBackground
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG = "roster_screenshot_crop_screen"
const val ROSTER_SCREENSHOT_CROP_EDITOR_TEST_TAG = "roster_screenshot_crop_editor"
const val ROSTER_SCREENSHOT_CROP_CANCEL_TEST_TAG = "roster_screenshot_crop_cancel"

@Composable
fun RosterScreenshotCropRoute(
    tournamentId: String,
    screenshotIndex: Int,
    onCancel: () -> Unit,
    onConfirmed: () -> Unit,
    viewModel: RosterScreenshotIntakeViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val slot = uiState.slots.firstOrNull {
        it.index == screenshotIndex &&
            screenshotIndex in 1..RosterScreenshotIntakeUiState.REQUIRED_SCREENSHOT_COUNT
    }

    BackHandler(onBack = onCancel)

    RosterScreenshotCropScreen(
        slot = slot,
        onCropChanged = { crop ->
            viewModel.onVisualCropChanged(screenshotIndex, crop)
        },
        onCancel = onCancel,
        onConfirm = {
            if (viewModel.confirmCrop(screenshotIndex)) {
                onConfirmed()
            }
        },
    )
}

@Composable
fun RosterScreenshotCropScreen(
    slot: RosterScreenshotSlotUiState?,
    onCropChanged: (OcrNormalizedCropRect) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RankForgePageBackground)
            .padding(RankForgeSpacing.Medium)
            .testTag(ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
    ) {
        Text(
            text = stringResource(R.string.roster_screenshot_crop_page_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = stringResource(R.string.roster_screenshot_crop_page_subtitle))

        if (slot == null || !slot.hasValidatedImage) {
            Text(
                text = stringResource(R.string.roster_screenshot_crop_missing_image),
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ROSTER_SCREENSHOT_CROP_CANCEL_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.roster_screenshot_crop_cancel_action))
            }
            return@Column
        }

        slot.cropError?.let { error ->
            Text(
                text = stringResource(error.toStringRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }

        OcrVisualCropEditor(
            imageUri = slot.selectedImageUri,
            crop = slot.visualCrop(),
            defaultCrop = OcrVisualCropDefaults.FullImageCrop,
            profile = OcrCropValidationProfiles.Roster,
            onCropChanged = onCropChanged,
            onConfirmCrop = onConfirm,
            confirmButtonText = stringResource(
                if (slot.cropState is RosterScreenshotCropState.Set) {
                    R.string.roster_screenshot_crop_update_action
                } else {
                    R.string.roster_screenshot_crop_confirm_action
                },
            ),
            previewContentDescription = stringResource(
                R.string.roster_screenshot_crop_preview_description,
                slot.index,
            ),
            sourceImageWidth = slot.selectedImageWidth,
            sourceImageHeight = slot.selectedImageHeight,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ROSTER_SCREENSHOT_CROP_EDITOR_TEST_TAG),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ROSTER_SCREENSHOT_CROP_CANCEL_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.roster_screenshot_crop_cancel_action))
            }
        }
    }
}

private fun RosterScreenshotSlotUiState.visualCrop(): OcrNormalizedCropRect =
    cropDraft.toNormalizedCropRectOrNull()
        ?: (cropState as? RosterScreenshotCropState.Set)?.crop
        ?: OcrVisualCropDefaults.FullImageCrop

private fun RosterScreenshotCropError.toStringRes(): Int = when (this) {
    RosterScreenshotCropError.MISSING_SELECTED_IMAGE -> R.string.roster_screenshot_crop_missing_image
    RosterScreenshotCropError.INVALID_NUMBER -> R.string.roster_screenshot_crop_invalid_number
    RosterScreenshotCropError.NON_FINITE_VALUE -> R.string.roster_screenshot_crop_non_finite
    RosterScreenshotCropError.OUT_OF_BOUNDS -> R.string.roster_screenshot_crop_out_of_bounds
    RosterScreenshotCropError.INVALID_EDGES -> R.string.roster_screenshot_crop_invalid_edges
    RosterScreenshotCropError.TOO_SMALL -> R.string.roster_screenshot_crop_too_small
}
