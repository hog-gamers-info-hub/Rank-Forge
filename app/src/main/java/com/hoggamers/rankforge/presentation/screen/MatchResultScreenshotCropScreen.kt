package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_RESULT_SCREENSHOT_CROP_SCREEN_TEST_TAG = "match_result_screenshot_crop_screen"
const val MATCH_RESULT_SCREENSHOT_CROP_EDITOR_TEST_TAG = "match_result_screenshot_crop_editor"
const val MATCH_RESULT_SCREENSHOT_CROP_CANCEL_TEST_TAG = "match_result_screenshot_crop_cancel"
const val MATCH_RESULT_SCREENSHOT_CROP_LOADING_TEST_TAG = "match_result_screenshot_crop_loading"

@Composable
fun MatchResultScreenshotCropRoute(
    tournamentId: String,
    matchId: String,
    screenshotRole: String,
    onCancel: () -> Unit,
    onConfirmed: () -> Unit,
    viewModel: MatchResultScreenshotCropViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId, screenshotRole) {
        viewModel.load(tournamentId, matchId, screenshotRole)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onCancel)

    MatchResultScreenshotCropScreen(
        uiState = uiState,
        onCropChanged = viewModel::onCropChanged,
        onCancel = onCancel,
        onConfirm = {
            viewModel.confirmCrop(onConfirmed)
        },
    )
}

@Composable
fun MatchResultScreenshotCropScreen(
    uiState: MatchResultScreenshotCropUiState,
    onCropChanged: (OcrNormalizedCropRect) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag(MATCH_RESULT_SCREENSHOT_CROP_SCREEN_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(MATCH_RESULT_SCREENSHOT_CROP_LOADING_TEST_TAG),
                )
                Text(text = stringResource(R.string.match_result_screenshot_crop_loading))
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(RankForgeSpacing.Medium)
            .testTag(MATCH_RESULT_SCREENSHOT_CROP_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
    ) {
        val screenshotNumber = uiState.role.screenshotNumber()
        Text(
            text = stringResource(R.string.match_result_screenshot_crop_title, screenshotNumber),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(
                if (uiState.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                    R.string.match_result_screenshot_crop_1_guidance
                } else {
                    R.string.match_result_screenshot_crop_2_guidance
                },
            ),
        )
        uiState.error?.let { error ->
            Text(
                text = stringResource(error.toStringRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (uiState.isFinalized) {
            Text(
                text = stringResource(R.string.match_result_screenshot_crop_finalized_read_only),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (
            uiState.imageUri != null &&
            uiState.originalWidth != null &&
            uiState.originalHeight != null
        ) {
            OcrVisualCropEditor(
                imageUri = uiState.imageUri,
                crop = uiState.draftCrop,
                defaultCrop = OcrVisualCropDefaults.FullImageCrop,
                profile = OcrCropValidationProfiles.MatchResult,
                onCropChanged = onCropChanged,
                onConfirmCrop = onConfirm,
                sourceImageWidth = uiState.originalWidth,
                sourceImageHeight = uiState.originalHeight,
                confirmButtonText = stringResource(
                    if (uiState.confirmedCrop != null) {
                        R.string.match_result_screenshot_crop_update_action
                    } else {
                        R.string.match_result_screenshot_crop_confirm_action
                    },
                ),
                previewContentDescription = stringResource(
                    R.string.match_result_screenshot_crop_preview_description,
                    screenshotNumber,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_RESULT_SCREENSHOT_CROP_EDITOR_TEST_TAG),
            )
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_RESULT_SCREENSHOT_CROP_CANCEL_TEST_TAG),
        ) {
            Text(text = stringResource(R.string.match_result_screenshot_crop_cancel_action))
        }
        if (uiState.isSaving) {
            Text(text = stringResource(R.string.match_result_screenshot_crop_saving))
        }
        if (uiState.isValidating) {
            Text(text = stringResource(R.string.match_result_screenshot_crop_validating))
        }
    }
}

private fun MatchResultScreenshotRole?.screenshotNumber(): Int = when (this) {
    MatchResultScreenshotRole.MATCH_RESULT_UPPER -> 1
    MatchResultScreenshotRole.MATCH_RESULT_LOWER -> 2
    null -> 0
}

private fun MatchResultScreenshotCropError.toStringRes(): Int = when (this) {
    MatchResultScreenshotCropError.INVALID_ROLE -> R.string.match_result_screenshot_crop_invalid_role
    MatchResultScreenshotCropError.MISSING_ASSET -> R.string.match_result_screenshot_crop_missing_asset
    MatchResultScreenshotCropError.MISSING_LOCAL_FILE -> R.string.match_result_screenshot_crop_missing_local_file
    MatchResultScreenshotCropError.FINALIZED_MATCH -> R.string.match_result_screenshot_crop_finalized_read_only
    MatchResultScreenshotCropError.INVALID_CROP -> R.string.match_result_screenshot_crop_invalid_crop
    MatchResultScreenshotCropError.CONTENT_INVALID -> R.string.match_result_screenshot_crop_content_invalid
    MatchResultScreenshotCropError.CONTENT_VALIDATION_FAILED ->
        R.string.match_result_screenshot_crop_content_validation_failed
    MatchResultScreenshotCropError.SAVE_FAILED -> R.string.match_result_screenshot_crop_save_failed
}
