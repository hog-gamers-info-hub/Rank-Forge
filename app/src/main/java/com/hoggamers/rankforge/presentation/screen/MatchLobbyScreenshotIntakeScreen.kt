package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG = "match_lobby_screenshot_intake_screen"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX = "match_lobby_screenshot_select_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX = "match_lobby_screenshot_crop_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX = "match_lobby_screenshot_remove_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX = "match_lobby_screenshot_preview_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG = "match_lobby_screenshot_pager"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_INDICATOR_TEST_TAG_PREFIX = "match_lobby_screenshot_indicator_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIOUS_PAGE_TEST_TAG = "match_lobby_screenshot_previous_page"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_PAGE_TEST_TAG = "match_lobby_screenshot_next_page"

@Composable
fun MatchLobbyScreenshotIntakeRoute(
    tournamentId: String,
    matchId: String,
    onOpenCropEditor: (Int) -> Unit,
    showTitle: Boolean = true,
    viewModel: MatchLobbyScreenshotIntakeViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId) { viewModel.load(tournamentId, matchId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.pendingCropNavigationSlotIndex) {
        val index = uiState.pendingCropNavigationSlotIndex ?: return@LaunchedEffect
        viewModel.onCropNavigationHandled()
        onOpenCropEditor(index)
    }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { viewModel.onPhotoPickerResult(it?.toString()) },
    )
    uiState.slots.firstOrNull { it.isPhotoPickerLaunchPending }?.let { slot ->
        LaunchedEffect(slot.index, slot.isPhotoPickerLaunchPending) {
            viewModel.onPhotoPickerLaunchHandled(slot.index)
            try {
                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: Exception) {
                viewModel.onPhotoPickerLaunchFailed(slot.index)
            }
        }
    }
    MatchLobbyScreenshotIntakeScreen(
        uiState = uiState,
        onSelect = viewModel::requestPhotoPicker,
        onCrop = viewModel::requestCropEditor,
        onRemove = viewModel::removeScreenshot,
        showTitle = showTitle,
    )
}

@Composable
fun MatchLobbyScreenshotIntakeScreen(
    uiState: MatchLobbyScreenshotIntakeUiState,
    onSelect: (Int) -> Unit,
    onCrop: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    showTitle: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        if (showTitle) {
            Text(text = stringResource(R.string.match_lobby_screenshot_intake_title), style = MaterialTheme.typography.titleLarge)
        }
        if (uiState.isLoading) Text(text = stringResource(R.string.match_lobby_screenshot_loading))
        uiState.intakeError?.let { Text(text = stringResource(it.toStringRes()), color = MaterialTheme.colorScheme.error) }
        if (uiState.isFinalized) {
            Text(text = stringResource(R.string.match_lobby_screenshot_finalized_read_only), color = MaterialTheme.colorScheme.error)
        }
        if (uiState.slots.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { uiState.slots.size })
            val currentPage = pagerState.currentPage.coerceIn(0, uiState.slots.lastIndex)
            val activeSlot = uiState.slots.getOrNull(currentPage)
            Text(
                text = stringResource(
                    R.string.match_lobby_screenshot_slot_label,
                    uiState.slots[currentPage].index,
                    uiState.slots.size,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG),
            ) { page ->
                LobbyScreenshotPage(
                    slot = uiState.slots[page],
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (currentPage > 0) {
                    Text(
                        text = stringResource(R.string.match_lobby_screenshot_previous_page_affordance),
                        modifier = Modifier.testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIOUS_PAGE_TEST_TAG),
                    )
                }
                uiState.slots.indices.forEach { page ->
                    Text(
                        text = stringResource(
                            if (page == currentPage) {
                                R.string.match_lobby_screenshot_selected_page_indicator
                            } else {
                                R.string.match_lobby_screenshot_unselected_page_indicator
                            },
                        ),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_INDICATOR_TEST_TAG_PREFIX + (page + 1))
                            .semantics { selected = page == currentPage },
                    )
                }
                if (currentPage < uiState.slots.lastIndex) {
                    Text(
                        text = stringResource(R.string.match_lobby_screenshot_next_page_affordance),
                        modifier = Modifier.testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_PAGE_TEST_TAG),
                    )
                }
            }
            activeSlot?.let { slot ->
                if (slot.hasLinkedAsset && !slot.isLocalFileMissing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onSelect(slot.index) },
                            enabled = uiState.isAvailable && !uiState.isFinalized && !slot.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + slot.index),
                        ) { Text(text = stringResource(R.string.match_lobby_screenshot_replace_action)) }
                        Button(
                            onClick = { onCrop(slot.index) },
                            enabled = !uiState.isFinalized && !slot.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + slot.index),
                        ) { Text(text = stringResource(R.string.match_lobby_screenshot_crop_action)) }
                        Button(
                            onClick = { onRemove(slot.index) },
                            enabled = !uiState.isFinalized && !slot.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + slot.index),
                        ) { Text(text = stringResource(R.string.match_lobby_screenshot_remove_action)) }
                    }
                } else {
                    Button(
                        onClick = { onSelect(slot.index) },
                        enabled = uiState.isAvailable && !uiState.isFinalized && !slot.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + slot.index),
                    ) {
                        Text(
                            text = stringResource(
                                if (slot.hasLinkedAsset) {
                                    R.string.match_lobby_screenshot_replace_action
                                } else {
                                    R.string.match_lobby_screenshot_select_action
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyScreenshotPage(
    slot: MatchLobbyScreenshotSlotUiState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        if (slot.hasLinkedAsset && !slot.isLocalFileMissing) {
            Text(text = stringResource(R.string.match_lobby_screenshot_selected))
            if (slot.hasConfirmedCrop) {
                slot.selectedScreenshotUri?.takeIf { it.isNotBlank() }?.let { imageUri ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentAlignment = Alignment.Center,
                    ) {
                        LocalScreenshotPreview(
                            imageUri = imageUri,
                            crop = slot.confirmedCrop,
                            contentDescription = stringResource(
                                R.string.match_lobby_screenshot_preview_description,
                                slot.index,
                            ),
                            sourceImageWidth = slot.selectedScreenshotWidth,
                            sourceImageHeight = slot.selectedScreenshotHeight,
                            modifier = Modifier.fillMaxSize(),
                            testTag = MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + slot.index,
                        )
                    }
                }
            }
        } else if (slot.isLocalFileMissing) {
            Text(
                text = stringResource(R.string.match_lobby_screenshot_missing_local_file),
                color = MaterialTheme.colorScheme.error,
            )
        }
        slot.imageValidationError?.let { error ->
            Text(text = stringResource(error.toStringRes()), color = MaterialTheme.colorScheme.error)
        }
        slot.duplicateError?.let { error ->
            Text(text = stringResource(error.toStringRes()), color = MaterialTheme.colorScheme.error)
        }
        slot.preservationError?.let { error ->
            Text(text = stringResource(error.toStringRes()), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun MatchLobbyScreenshotIntakeError.toStringRes(): Int = when (this) {
    MatchLobbyScreenshotIntakeError.INVALID_CONTEXT -> R.string.match_lobby_screenshot_invalid_context
    MatchLobbyScreenshotIntakeError.MATCH_NOT_FOUND -> R.string.match_lobby_screenshot_match_not_found
    MatchLobbyScreenshotIntakeError.FINALIZED_MATCH -> R.string.match_lobby_screenshot_finalized_read_only
    MatchLobbyScreenshotIntakeError.PHOTO_PICKER_LAUNCH_FAILED -> R.string.match_lobby_screenshot_picker_failed
    MatchLobbyScreenshotIntakeError.INVALID_INDEX -> R.string.match_lobby_screenshot_invalid_index
    MatchLobbyScreenshotIntakeError.REMOVE_FAILED -> R.string.match_lobby_screenshot_remove_failed
}

private fun ImageValidationError.toStringRes(): Int = when (this) {
    ImageValidationError.EMPTY_URI -> R.string.match_lobby_screenshot_empty_uri
    ImageValidationError.NON_IMAGE_CONTENT -> R.string.match_lobby_screenshot_non_image
    ImageValidationError.UNSUPPORTED_FORMAT -> R.string.match_lobby_screenshot_unsupported_format
    ImageValidationError.UNREADABLE_URI -> R.string.match_lobby_screenshot_unreadable
    ImageValidationError.DECODE_FAILED -> R.string.match_lobby_screenshot_decode_failed
    ImageValidationError.INVALID_DIMENSIONS -> R.string.match_lobby_screenshot_invalid_dimensions
    ImageValidationError.IMAGE_TOO_LARGE -> R.string.match_lobby_screenshot_too_large
}

private fun MatchLobbyScreenshotDuplicateError.toStringRes(): Int = when (this) {
    MatchLobbyScreenshotDuplicateError.USED_BY_ANOTHER_LOBBY_SCREENSHOT -> R.string.match_lobby_screenshot_duplicate
    MatchLobbyScreenshotDuplicateError.STATE_CONFLICT -> R.string.match_lobby_screenshot_state_conflict
}

private fun MatchLobbyScreenshotPreservationError.toStringRes(): Int = when (this) {
    MatchLobbyScreenshotPreservationError.OWNER_MISSING -> R.string.match_lobby_screenshot_owner_missing
    MatchLobbyScreenshotPreservationError.PRESERVATION_FAILED -> R.string.match_lobby_screenshot_preservation_failed
    MatchLobbyScreenshotPreservationError.SAVE_FAILED -> R.string.match_lobby_screenshot_save_failed
    MatchLobbyScreenshotPreservationError.CLEANUP_FAILED -> R.string.match_lobby_screenshot_cleanup_failed
}
