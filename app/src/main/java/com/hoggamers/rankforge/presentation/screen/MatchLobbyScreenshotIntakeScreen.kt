package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidMatchLobbyTeamCropPreviewImage
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val PointIqMatchReviewNavy = Color(0xFF071B3E)
private val PointIqMatchReviewBody = Color(0xFF607393)
private val PointIqMatchReviewBlue = Color(0xFF176AF7)

const val MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG = "match_lobby_screenshot_intake_screen"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX = "match_lobby_screenshot_select_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG = "match_lobby_screenshot_next_select"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX = "match_lobby_screenshot_crop_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX = "match_lobby_screenshot_remove_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX = "match_lobby_screenshot_preview_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX = "match_lobby_screenshot_slot_"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG = "match_lobby_screenshot_intake_pager"
const val MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG = "match_lobby_screenshot_save_template"
const val MATCH_LOBBY_TEAM_CROP_PREVIEWS_TEST_TAG_PREFIX = "match_lobby_team_crop_previews_"
const val MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX = "match_lobby_team_crop_card_"
const val MATCH_LOBBY_TEAM_CROP_DATA_TEST_TAG_PREFIX = "match_lobby_team_crop_data_"
const val MATCH_LOBBY_TEAM_CROP_ROW_LABEL_TEST_TAG_PREFIX = "match_lobby_team_crop_row_label_"
const val MATCH_LOBBY_TEAM_CROP_ROW_IMAGE_TEST_TAG_PREFIX = "match_lobby_team_crop_row_image_"
const val MATCH_LOBBY_TEAM_CROP_ROW_EVIDENCE_TEST_TAG_PREFIX = "match_lobby_team_crop_row_evidence_"
const val MATCH_LOBBY_TEAM_CROP_TEAM_SLOT_LABEL_TEST_TAG_PREFIX = "match_lobby_team_crop_team_slot_label_"
const val MATCH_LOBBY_TEAM_CROP_ROW_PP_NAME_TEST_TAG_PREFIX = "match_lobby_team_crop_row_pp_name_"
const val MATCH_LOBBY_TEAM_CROP_PLAYER_NAME_TEST_TAG_PREFIX = "match_lobby_team_crop_player_name_"
const val MATCH_LOBBY_DETAILS_HEADER_TEST_TAG = "match_lobby_details_header"
const val MATCH_LOBBY_DETAILS_STEP_TEST_TAG = "match_lobby_details_step"

internal data class TeamCropBitmapDimensions(
    val width: Int,
    val height: Int,
)

private fun MatchLobbyScreenshotSlotUiState.lobbyScreenshotHeightRatio(): Float? {
    val dimensions = OcrImageDimensions.from(
        width = selectedScreenshotWidth ?: return null,
        height = selectedScreenshotHeight ?: return null,
    ) ?: return null
    val crop = confirmedCrop ?: return null
    val pixelCrop = crop.toPixelRectOrNull(dimensions) ?: return null
    return pixelCrop.height.toFloat() / pixelCrop.width.toFloat()
}

internal fun calculateMaxTeamCropHeightRatio(
    dimensions: List<TeamCropBitmapDimensions>,
): Float = dimensions.maxOf { dimensionsForCrop ->
    dimensionsForCrop.height.toFloat() / dimensionsForCrop.width.toFloat()
}

val LocalMatchLobbyTeamCropPreviews = staticCompositionLocalOf<Map<Int, MatchLobbyTeamCropPreviewResult>> {
    emptyMap()
}

val LocalMatchLobbyTeamNames = staticCompositionLocalOf<Map<Int, String>> {
    emptyMap()
}

val LocalMatchLobbySourceSectionVisible = staticCompositionLocalOf { true }

@Composable
fun MatchLobbyScreenshotIntakeRoute(
    tournamentId: String,
    matchId: String,
    onOpenCropEditor: (Int) -> Unit,
    showTitle: Boolean = true,
    compactSelectors: Boolean = false,
    compactActions: Boolean = false,
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
    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3),
        onResult = { uris -> viewModel.onMultiPhotoPickerResult(uris.map { it.toString() }) },
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
    val multiPickerRequest = uiState.multiPhotoPickerRequest
    LaunchedEffect(multiPickerRequest?.requestId, multiPickerRequest?.isLaunchPending) {
        val request = multiPickerRequest?.takeIf { it.isLaunchPending } ?: return@LaunchedEffect
        viewModel.onMultiPhotoPickerLaunchHandled(request.requestId)
        try {
            multiPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        } catch (_: Exception) {
            viewModel.onMultiPhotoPickerLaunchFailed(request.requestId)
        }
    }
    MatchLobbyScreenshotIntakeScreen(
        uiState = uiState,
        onSelect = viewModel::requestPhotoPicker,
        onSelectBatch = viewModel::requestMultiPhotoPicker,
        onCrop = viewModel::requestCropEditor,
        onRemove = viewModel::removeScreenshot,
        onSaveLobbyForNextMatches = viewModel::saveLobbyForNextMatches,
        onUnsaveLobbyForNextMatches = viewModel::unsaveLobbyForNextMatches,
        showTitle = showTitle,
        compactSelectors = compactSelectors,
        compactActions = compactActions,
    )
}

@Composable
fun MatchLobbyScreenshotIntakeScreen(
    uiState: MatchLobbyScreenshotIntakeUiState,
    onSelect: (Int) -> Unit,
    onSelectBatch: (() -> Unit)? = null,
    onCrop: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSaveLobbyForNextMatches: () -> Unit = {},
    onUnsaveLobbyForNextMatches: () -> Unit = {},
    showTitle: Boolean = true,
    compactSelectors: Boolean = false,
    compactActions: Boolean = false,
) {
    val teamCropPreviewsByScreenshotIndex = LocalMatchLobbyTeamCropPreviews.current
    val sourceSectionVisible = LocalMatchLobbySourceSectionVisible.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(
            if (compactActions && !showTitle && uiState.slots.any { it.hasScreenshotSelection() }) {
                4.dp
            } else {
                RankForgeSpacing.Small
            },
        ),
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
            var activeSlotIndex by rememberSaveable { mutableStateOf<Int?>(null) }
            val selectedSlots = uiState.slots.filter { slot ->
                slot.hasScreenshotSelection()
            }
            val globallyOrderedTeamCropPreviews = teamCropPreviewsByScreenshotIndex.values
                .asSequence()
                .flatMap { result ->
                    (result as? MatchLobbyTeamCropPreviewResult.Available)
                        ?.previews
                        .orEmpty()
                        .asSequence()
                }
                .sortedBy { it.detectedSlotNumber }
                .toList()
            val selectedSlotIndices = selectedSlots.map { it.index }
            val pagerState = rememberPagerState(pageCount = { selectedSlots.size })
            val scope = rememberCoroutineScope()

            LaunchedEffect(selectedSlotIndices) {
                if (selectedSlotIndices.isEmpty()) {
                    activeSlotIndex = null
                    return@LaunchedEffect
                }
                val activePage = activeSlotIndex?.let(selectedSlotIndices::indexOf) ?: -1
                val targetPage = if (activePage >= 0) activePage else 0
                activeSlotIndex = selectedSlotIndices[targetPage]
                if (pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }
            LaunchedEffect(pagerState, selectedSlotIndices) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        selectedSlotIndices.getOrNull(page)?.let { activeSlotIndex = it }
                    }
            }

            if (compactActions && !showTitle) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            modifier = Modifier.testTag(MATCH_LOBBY_DETAILS_HEADER_TEST_TAG),
                            horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(PointIqMatchReviewNavy)
                                    .testTag(MATCH_LOBBY_DETAILS_STEP_TEST_TAG),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "1",
                                    color = Color.White,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false,
                                        ),
                                    ),
                                )
                            }
                            Text(
                                text = stringResource(R.string.match_review_lobby_screenshots_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedSlots.isEmpty()) {
                                    PointIqMatchReviewNavy
                                } else {
                                    Color.Unspecified
                                },
                            )
                        }
                        LobbyTemplateToggle(
                            uiState = uiState,
                            onSaveLobbyForNextMatches = onSaveLobbyForNextMatches,
                            onUnsaveLobbyForNextMatches = onUnsaveLobbyForNextMatches,
                            modifier = Modifier.align(Alignment.Top),
                        )
                    }
                    if (selectedSlots.isEmpty()) {
                        Text(
                            text = stringResource(R.string.pointiq_match_review_lobby_description),
                            color = PointIqMatchReviewBody,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }

            if ((sourceSectionVisible && !compactSelectors) || (compactActions && showTitle)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                if (sourceSectionVisible && !compactSelectors) {
                    uiState.slots.forEach { slot ->
                        val hasSelection = slot.hasScreenshotSelection()
                        val targetPage = selectedSlotIndices.indexOf(slot.index)
                        val onClick: () -> Unit = {
                            if (hasSelection && targetPage >= 0) {
                                activeSlotIndex = slot.index
                                scope.launch { pagerState.animateScrollToPage(targetPage) }
                            } else if (!hasSelection) {
                                (onSelectBatch ?: { onSelect(slot.index) })()
                            }
                            Unit
                        }
                        LobbyScreenshotSelectorButton(
                            slot = slot,
                            hasSelection = hasSelection,
                            isActive = hasSelection && activeSlotIndex == slot.index,
                            enabled = hasSelection || (uiState.isAvailable && !uiState.isFinalized && !slot.isBusy),
                            compactSelectors = compactSelectors,
                            onClick = onClick,
                        )
                    }
                }
                    if (compactActions && showTitle) {
                        Spacer(modifier = Modifier.weight(1f))
                        LobbyTemplateToggle(
                            uiState = uiState,
                            onSaveLobbyForNextMatches = onSaveLobbyForNextMatches,
                            onUnsaveLobbyForNextMatches = onUnsaveLobbyForNextMatches,
                        )
                    }
                }
            }

            if (sourceSectionVisible && selectedSlots.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val maxLobbyScreenshotHeight = maxWidth * (
                        selectedSlots
                            .mapNotNull(MatchLobbyScreenshotSlotUiState::lobbyScreenshotHeightRatio)
                            .maxOrNull()
                            ?: 1f
                    )
                    HorizontalPager(
                        state = pagerState,
                        pageSize = PageSize.Fill,
                        pageSpacing = RankForgeSpacing.ExtraSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG),
                    ) { page ->
                        selectedSlots.getOrNull(page)?.let { slot ->
                            LobbyScreenshotDetail(
                                slot = slot,
                                imageAreaHeight = maxLobbyScreenshotHeight,
                                compactActions = compactActions,
                                isFinalized = uiState.isFinalized,
                                isAvailable = uiState.isAvailable,
                                onSelect = onSelect,
                                onSelectBatch = onSelectBatch,
                                onCrop = onCrop,
                                onRemove = onRemove,
                            )
                        }
                    }
                }
            }

            if (sourceSectionVisible && compactSelectors) {
                uiState.slots
                    .filterNot { it.hasScreenshotSelection() }
                    .minByOrNull { it.index }
                    ?.let { nextEmptySlot ->
                        Button(
                            onClick = { (onSelectBatch ?: { onSelect(nextEmptySlot.index) })() },
                            enabled = uiState.isAvailable &&
                                !uiState.isFinalized &&
                                !nextEmptySlot.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PointIqMatchReviewBlue,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(
                                horizontal = RankForgeSpacing.Small,
                                vertical = RankForgeSpacing.ExtraSmall,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG),
                        ) {
                            Text(
                                text = stringResource(R.string.pointiq_match_review_upload_lobby_screenshots),
                            )
                        }
                    }
            }
            if (sourceSectionVisible && !compactActions) activeSlotIndex?.let { activeIndex ->
                selectedSlots.firstOrNull { it.index == activeIndex }?.let { slot ->
                    LobbyScreenshotActions(
                        slot = slot,
                        isFinalized = uiState.isFinalized,
                        isAvailable = uiState.isAvailable,
                        onSelect = onSelect,
                        onSelectBatch = onSelectBatch,
                        onCrop = onCrop,
                        onRemove = onRemove,
                        compactActions = compactActions,
                    )
                }
            }
            if (!compactActions) {
                LobbyTemplateToggle(
                    uiState = uiState,
                    onSaveLobbyForNextMatches = onSaveLobbyForNextMatches,
                    onUnsaveLobbyForNextMatches = onUnsaveLobbyForNextMatches,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (uiState.lobbyTemplateSaveStatus == MatchLobbyTemplateSaveStatus.FAILED) {
                Text(
                    text = stringResource(R.string.match_lobby_screenshot_template_mutation_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (globallyOrderedTeamCropPreviews.isNotEmpty()) {
                LobbyTeamCropPreviewPager(previews = globallyOrderedTeamCropPreviews)
            }
        }
    }
}

@Composable
private fun LobbyTemplateToggle(
    uiState: MatchLobbyScreenshotIntakeUiState,
    onSaveLobbyForNextMatches: () -> Unit,
    onUnsaveLobbyForNextMatches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSaved = uiState.isLobbySavedForNextMatches
    val enabled = if (isSaved) {
        uiState.canUnsaveLobbyForNextMatches
    } else {
        uiState.canSaveLobbyForNextMatches
    }
    val accessibilityDescription = stringResource(
        if (isSaved) {
            R.string.match_lobby_screenshot_saved_template_accessibility_description
        } else {
            R.string.match_lobby_screenshot_unsaved_template_accessibility_description
        },
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.match_lobby_screenshot_save_template_compact_action))
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 18.dp)
                .toggleable(
                    value = isSaved,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { checked ->
                        if (checked) {
                            onSaveLobbyForNextMatches()
                        } else {
                            onUnsaveLobbyForNextMatches()
                        }
                    },
                )
                .background(
                    color = if (isSaved) PointIqMatchReviewBlue else Color(0xFFD7DEE8),
                    shape = CircleShape,
                )
                .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
                .semantics { contentDescription = accessibilityDescription },
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .offset(x = if (isSaved) 12.dp else 0.dp)
                    .size(14.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

private fun MatchLobbyScreenshotSlotUiState.hasScreenshotSelection(): Boolean =
    hasLinkedAsset || !selectedScreenshotUri.isNullOrBlank()

@Composable
private fun RowScope.LobbyScreenshotSelectorButton(
    slot: MatchLobbyScreenshotSlotUiState,
    hasSelection: Boolean,
    isActive: Boolean,
    enabled: Boolean,
    compactSelectors: Boolean,
    onClick: () -> Unit,
) {
    val modifier = Modifier
        .then(if (compactSelectors) Modifier else Modifier.weight(1f))
        .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + slot.index)
        .semantics { selected = isActive }
    if (compactSelectors) {
        if (isActive) {
            FilledIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier,
            ) {
                Text(text = slot.index.toString())
            }
        } else {
            OutlinedIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier,
            ) {
                Text(text = slot.index.toString())
            }
        }
        return
    }
    val content: @Composable RowScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.match_lobby_screenshot_slot_short_label, slot.index))
            Text(
                text = stringResource(
                    if (hasSelection) {
                        R.string.screenshot_slot_selected_status
                    } else {
                        R.string.screenshot_slot_empty_status
                    },
                ),
            )
        }
    }
    if (isActive) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = modifier,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun LobbyScreenshotDetail(
    slot: MatchLobbyScreenshotSlotUiState,
    imageAreaHeight: Dp,
    compactActions: Boolean,
    isFinalized: Boolean,
    isAvailable: Boolean,
    onSelect: (Int) -> Unit,
    onSelectBatch: (() -> Unit)?,
    onCrop: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val previewImageUri = if (
        slot.hasLinkedAsset && !slot.isLocalFileMissing && slot.hasConfirmedCrop
    ) {
        slot.selectedScreenshotUri?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        if (!compactActions && slot.hasLinkedAsset && !slot.isLocalFileMissing) {
            Text(text = stringResource(R.string.match_lobby_screenshot_selected))
        } else if (slot.isLocalFileMissing) {
            Text(
                text = stringResource(R.string.match_lobby_screenshot_missing_local_file),
                color = MaterialTheme.colorScheme.error,
            )
        }
        previewImageUri?.let { imageUri ->
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageAreaHeight)
                        .clip(MaterialTheme.shapes.medium),
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
                        modifier = Modifier
                            .fillMaxWidth(),
                        testTag = MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + slot.index,
                    )
                }
                if (compactActions && !isFinalized) {
                    MatchReviewScreenshotActionRow(
                        replaceLabel = stringResource(R.string.match_lobby_screenshot_replace_action),
                        editLabel = stringResource(R.string.match_review_screenshot_edit_action),
                        removeLabel = stringResource(R.string.match_lobby_screenshot_remove_action),
                        replaceContentDescription = stringResource(
                            R.string.match_review_screenshot_replace_content_description,
                        ),
                        editContentDescription = stringResource(
                            R.string.match_review_screenshot_crop_content_description,
                        ),
                        removeContentDescription = stringResource(
                            R.string.match_review_screenshot_remove_content_description,
                        ),
                        replaceEnabled = isAvailable && !isFinalized && !slot.isBusy,
                        editEnabled = !isFinalized && !slot.isBusy,
                        removeEnabled = !isFinalized && !slot.isBusy,
                        replaceTestTag = MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + slot.index,
                        editTestTag = MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + slot.index,
                        removeTestTag = MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + slot.index,
                        onReplace = { onSelect(slot.index) },
                        onEdit = { onCrop(slot.index) },
                        onRemove = { onRemove(slot.index) },
                    )
                }
            }
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
        if (compactActions && previewImageUri == null) {
            LobbyScreenshotActions(
                slot = slot,
                isFinalized = isFinalized,
                isAvailable = isAvailable,
                onSelect = onSelect,
                onSelectBatch = onSelectBatch,
                onCrop = onCrop,
                onRemove = onRemove,
                compactActions = true,
            )
        }
    }
}

@Composable
private fun LobbyTeamCropPreviewPager(
    previews: List<MatchLobbyTeamCropPreview>,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_LOBBY_TEAM_CROP_PREVIEWS_TEST_TAG_PREFIX + "global"),
    ) {
        val displayWidth = maxWidth
        val maxDisplayHeight = displayWidth * calculateMaxTeamCropHeightRatio(
            previews.map { preview ->
                when (val image = preview.image) {
                    is AndroidMatchLobbyTeamCropPreviewImage -> TeamCropBitmapDimensions(
                        width = image.bitmap.width,
                        height = image.bitmap.height,
                    )
                    else -> TeamCropBitmapDimensions(width = 1, height = 1)
                }
            },
        )
        val pagerState = rememberPagerState(pageCount = { previews.size })
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
        ) {
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fill,
                pageSpacing = RankForgeSpacing.ExtraSmall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val preview = previews[it]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxDisplayHeight)
                            .testTag(
                                MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX +
                                    "slot_" + preview.detectedSlotNumber,
                            ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (val image = preview.image) {
                                is AndroidMatchLobbyTeamCropPreviewImage -> {
                                    val aspectRatio = image.bitmap.width.toFloat() / image.bitmap.height.toFloat()
                                    Image(
                                        bitmap = image.bitmap.asImageBitmap(),
                                        contentDescription = "Slot ${preview.detectedSlotNumber}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(aspectRatio),
                                    )
                                }
                                else -> Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                )
                            }
                        }
                    }
                    if (preview.playerRowPreviews.isNotEmpty()) {
                        OcrReviewContainer(
                            modifier = Modifier.testTag(
                                MATCH_LOBBY_TEAM_CROP_DATA_TEST_TAG_PREFIX +
                                    "slot_" + preview.detectedSlotNumber,
                            ),
                        ) {
                            LobbyPlayerRowPreviewColumn(preview = preview)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyPlayerRowPreviewColumn(
    preview: MatchLobbyTeamCropPreview,
) {
    if (preview.playerRowPreviews.isEmpty()) return
    val teamName = LocalMatchLobbyTeamNames.current[preview.detectedSlotNumber]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.match_ocr_review_compact_not_named)
    LobbyPlayerNamePresentation(
        slotNumber = preview.detectedSlotNumber,
        teamName = teamName,
        playerNames = preview.playerRowPreviews.associate { rowPreview ->
            rowPreview.row.ordinal + 1 to rowPreview.dualOcrResult?.finalText
        },
        slotTestTag = MATCH_LOBBY_TEAM_CROP_TEAM_SLOT_LABEL_TEST_TAG_PREFIX + preview.detectedSlotNumber,
        playerTestTag = { playerNumber ->
            MATCH_LOBBY_TEAM_CROP_PLAYER_NAME_TEST_TAG_PREFIX +
                "slot_${preview.detectedSlotNumber}_row_$playerNumber"
        },
    )
}

@Composable
private fun LobbyScreenshotActions(
    slot: MatchLobbyScreenshotSlotUiState,
    isFinalized: Boolean,
    isAvailable: Boolean,
    onSelect: (Int) -> Unit,
    onSelectBatch: (() -> Unit)?,
    onCrop: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    compactActions: Boolean,
) {
    if (!isFinalized && slot.hasLinkedAsset) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LobbyScreenshotActionButton(
                compactActions = compactActions,
                onClick = { onSelect(slot.index) },
                enabled = isAvailable && !isFinalized && !slot.isBusy,
                modifier = Modifier.testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + slot.index),
                label = stringResource(R.string.match_lobby_screenshot_replace_action),
            )
            LobbyScreenshotActionButton(
                compactActions = compactActions,
                onClick = { onCrop(slot.index) },
                enabled = !isFinalized && !slot.isBusy,
                modifier = Modifier.testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + slot.index),
                label = stringResource(R.string.match_lobby_screenshot_crop_action),
            )
            LobbyScreenshotActionButton(
                compactActions = compactActions,
                onClick = { onRemove(slot.index) },
                enabled = !isFinalized && !slot.isBusy,
                modifier = Modifier.testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + slot.index),
                label = stringResource(R.string.match_lobby_screenshot_remove_action),
            )
        }
    } else if (!isFinalized) {
        Button(
            onClick = { (onSelectBatch ?: { onSelect(slot.index) })() },
            enabled = isAvailable && !isFinalized && !slot.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + slot.index),
        ) {
            Text(text = stringResource(R.string.match_lobby_screenshot_replace_action))
        }
    }
}

@Composable
private fun RowScope.LobbyScreenshotActionButton(
    compactActions: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    label: String,
) {
    val buttonModifier = modifier.then(if (compactActions) Modifier else Modifier.weight(1f))
    if (compactActions) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(
                horizontal = RankForgeSpacing.Small,
                vertical = RankForgeSpacing.ExtraSmall,
            ),
            modifier = buttonModifier,
        ) { Text(text = label) }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
        ) { Text(text = label) }
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
