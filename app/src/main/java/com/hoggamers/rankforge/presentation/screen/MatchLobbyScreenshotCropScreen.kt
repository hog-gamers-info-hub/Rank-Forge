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
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_LOBBY_SCREENSHOT_CROP_SCREEN_TEST_TAG = "match_lobby_screenshot_crop_screen"
const val MATCH_LOBBY_SCREENSHOT_CROP_EDITOR_TEST_TAG = "match_lobby_screenshot_crop_editor"
const val MATCH_LOBBY_SCREENSHOT_CROP_CANCEL_TEST_TAG = "match_lobby_screenshot_crop_cancel"
const val MATCH_LOBBY_SCREENSHOT_CROP_LOADING_TEST_TAG = "match_lobby_screenshot_crop_loading"

@Composable
fun MatchLobbyScreenshotCropRoute(
    tournamentId: String,
    matchId: String,
    lobbyScreenshotIndex: Int,
    onCancel: () -> Unit,
    onConfirmed: () -> Unit,
    viewModel: MatchLobbyScreenshotCropViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId, lobbyScreenshotIndex) {
        viewModel.load(tournamentId, matchId, lobbyScreenshotIndex)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onCancel)
    MatchLobbyScreenshotCropScreen(
        uiState = uiState,
        onCropChanged = viewModel::onCropChanged,
        onCancel = onCancel,
        onConfirm = { viewModel.confirmCrop(onConfirmed) },
    )
}

@Composable
fun MatchLobbyScreenshotCropScreen(
    uiState: MatchLobbyScreenshotCropUiState,
    onCropChanged: (OcrNormalizedCropRect) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag(MATCH_LOBBY_SCREENSHOT_CROP_SCREEN_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(MATCH_LOBBY_SCREENSHOT_CROP_LOADING_TEST_TAG),
                )
                Text(text = stringResource(R.string.match_lobby_screenshot_loading))
            }
        }
        return
    }
    val index = uiState.lobbyScreenshotIndex ?: 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(RankForgeSpacing.Medium)
            .testTag(MATCH_LOBBY_SCREENSHOT_CROP_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
    ) {
        Text(
            text = stringResource(R.string.match_lobby_screenshot_crop_title, index),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(text = stringResource(R.string.match_lobby_screenshot_crop_guidance))
        uiState.error?.let { Text(text = stringResource(it.toStringRes()), color = MaterialTheme.colorScheme.error) }
        if (uiState.isFinalized) {
            Text(text = stringResource(R.string.match_lobby_screenshot_finalized_read_only), color = MaterialTheme.colorScheme.error)
        }
        if (uiState.imageUri != null && uiState.originalWidth != null && uiState.originalHeight != null) {
            OcrVisualCropEditor(
                imageUri = uiState.imageUri,
                crop = uiState.draftCrop,
                defaultCrop = OcrVisualCropDefaults.FullImageCrop,
                profile = OcrCropValidationProfiles.Lobby,
                onCropChanged = onCropChanged,
                onConfirmCrop = onConfirm,
                sourceImageWidth = uiState.originalWidth,
                sourceImageHeight = uiState.originalHeight,
                confirmButtonText = stringResource(
                    if (uiState.confirmedCrop != null) {
                        R.string.match_lobby_screenshot_update_crop_action
                    } else {
                        R.string.match_lobby_screenshot_confirm_crop_action
                    },
                ),
                previewContentDescription = stringResource(R.string.match_lobby_screenshot_crop_preview_description, index),
                modifier = Modifier.fillMaxWidth().testTag(MATCH_LOBBY_SCREENSHOT_CROP_EDITOR_TEST_TAG),
            )
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag(MATCH_LOBBY_SCREENSHOT_CROP_CANCEL_TEST_TAG),
        ) { Text(text = stringResource(R.string.match_lobby_screenshot_cancel_action)) }
        if (uiState.isSaving) Text(text = stringResource(R.string.match_lobby_screenshot_saving))
    }
}

private fun MatchLobbyScreenshotCropError.toStringRes(): Int = when (this) {
    MatchLobbyScreenshotCropError.INVALID_INDEX -> R.string.match_lobby_screenshot_invalid_index
    MatchLobbyScreenshotCropError.MISSING_ASSET -> R.string.match_lobby_screenshot_missing_asset
    MatchLobbyScreenshotCropError.MISSING_LOCAL_FILE -> R.string.match_lobby_screenshot_missing_local_file
    MatchLobbyScreenshotCropError.FINALIZED_MATCH -> R.string.match_lobby_screenshot_finalized_read_only
    MatchLobbyScreenshotCropError.INVALID_CROP -> R.string.match_lobby_screenshot_invalid_crop
    MatchLobbyScreenshotCropError.SAVE_FAILED -> R.string.match_lobby_screenshot_save_failed
}
