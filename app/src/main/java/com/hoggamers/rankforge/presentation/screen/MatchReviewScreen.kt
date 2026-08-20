package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

const val MATCH_REVIEW_SCREEN_TEST_TAG = "match_review_screen"
const val MATCH_REVIEW_ROW_TEST_TAG_PREFIX = "match_review_row_"
const val MATCH_REVIEW_VALID_STATUS_TEST_TAG = "match_review_valid_status"
const val MATCH_REVIEW_ISSUES_STATUS_TEST_TAG = "match_review_issues_status"
const val MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG = "match_review_placements_action"
const val MATCH_REVIEW_KILLS_ACTION_TEST_TAG = "match_review_kills_action"
const val MATCH_REVIEW_DETAILS_ACTION_TEST_TAG = "match_review_details_action"
const val MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG = "match_review_ocr_review_action"
const val MATCH_REVIEW_OCR_READY_TEST_TAG = "match_review_ocr_ready"
const val MATCH_REVIEW_OCR_STALE_TEST_TAG = "match_review_ocr_stale"
const val MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG = "match_review_finalize_action"
const val MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG = "match_review_finalize_confirm_action"
const val MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG = "match_review_finalized_status"
const val MATCH_REVIEW_CSV_EXPORT_ACTION_TEST_TAG = "match_review_csv_export_action"
const val MATCH_REVIEW_CSV_EXPORT_STATUS_TEST_TAG = "match_review_csv_export_status"
const val MATCH_REVIEW_GOOGLE_SHEETS_EXPORT_ACTION_TEST_TAG = "match_review_google_sheets_export_action"
const val MATCH_REVIEW_GOOGLE_SHEETS_EXPORT_STATUS_TEST_TAG = "match_review_google_sheets_export_status"
const val MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG = "match_review_download_result_action"
const val MATCH_REVIEW_DOWNLOAD_SCOPE_DIALOG_TEST_TAG = "match_review_download_scope_dialog"
const val MATCH_REVIEW_DOWNLOAD_SCOPE_CURRENT_MATCH_TEST_TAG = "match_review_download_scope_current_match"
const val MATCH_REVIEW_DOWNLOAD_SCOPE_TOURNAMENT_TEST_TAG = "match_review_download_scope_tournament"
const val MATCH_REVIEW_DOWNLOAD_SCOPE_CONTINUE_TEST_TAG = "match_review_download_scope_continue"
const val MATCH_REVIEW_DOWNLOAD_SCOPE_CANCEL_TEST_TAG = "match_review_download_scope_cancel"
const val MATCH_REVIEW_DOWNLOAD_FORMAT_DIALOG_TEST_TAG = "match_review_download_format_dialog"
const val MATCH_REVIEW_DOWNLOAD_FORMAT_PDF_TEST_TAG = "match_review_download_format_pdf"
const val MATCH_REVIEW_DOWNLOAD_FORMAT_PNG_TEST_TAG = "match_review_download_format_png"
const val MATCH_REVIEW_DOWNLOAD_FORMAT_BACK_TEST_TAG = "match_review_download_format_back"
const val MATCH_REVIEW_DOWNLOAD_FORMAT_CONFIRM_TEST_TAG = "match_review_download_format_confirm"
const val MATCH_REVIEW_DOWNLOAD_STATUS_TEST_TAG = "match_review_download_status"
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
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_SECTION_TEST_TAG = "match_review_result_screenshot_1_section"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_SECTION_TEST_TAG = "match_review_result_screenshot_2_section"
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG = "match_review_result_screenshot_1_select"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_SELECT_TEST_TAG = "match_review_result_screenshot_2_select"
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG = "match_review_result_screenshot_1_replace"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG = "match_review_result_screenshot_2_replace"
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG = "match_review_result_screenshot_1_crop"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG = "match_review_result_screenshot_2_crop"
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG = "match_review_result_screenshot_1_remove"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG = "match_review_result_screenshot_2_remove"
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_READY_TEST_TAG = "match_review_result_screenshot_1_crop_ready"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_READY_TEST_TAG = "match_review_result_screenshot_2_crop_ready"
const val MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG = "match_review_result_screenshot_1_preview"
const val MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG = "match_review_result_screenshot_2_preview"
const val MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG = "match_review_result_screenshot_next_select"
const val MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG = "match_review_lobby_screenshots_section"
const val MATCH_REVIEW_LOBBY_PLAYER_DETAILS_SECTION_TEST_TAG = "match_review_lobby_player_details_section"
const val MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG = "match_review_lobby_players_pager"
const val MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG = "match_review_result_screenshots_pager"
const val MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG = "match_review_result_screenshots_section"
const val MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG = "match_review_result_ocr_details_section"
const val MATCH_REVIEW_RESULT_OCR_PREVIEW_PAGER_TEST_TAG = "match_review_result_ocr_preview_pager"
const val MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG = "match_review_result_ocr_rows_pager"
const val MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG = "match_review_ocr_preflight_dialog"
const val MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG = "match_review_ocr_preflight_calculate"
const val MATCH_REVIEW_OCR_PREFLIGHT_CANCEL_ACTION_TEST_TAG = "match_review_ocr_preflight_cancel"

fun matchReviewOcrPreflightItemTestTag(identity: OcrScreenshotPreflightIdentity): String = when (identity) {
    is OcrScreenshotPreflightIdentity.Lobby ->
        "match_review_ocr_preflight_lobby_${identity.index}"
    is OcrScreenshotPreflightIdentity.Result ->
        "match_review_ocr_preflight_result_${identity.role.numberForUi()}"
}

fun matchReviewOcrPreflightActionTestTag(
    identity: OcrScreenshotPreflightIdentity,
    issue: OcrScreenshotPreflightIssue,
): String = "${matchReviewOcrPreflightItemTestTag(identity)}_${issue.name.lowercase()}"

private fun MatchResultScreenshotRole.numberForUi(): Int = when (this) {
    MatchResultScreenshotRole.MATCH_RESULT_UPPER -> 1
    MatchResultScreenshotRole.MATCH_RESULT_LOWER -> 2
}

@Composable
fun MatchReviewRoute(
    tournamentId: String,
    matchId: String,
    onBackToDetails: () -> Unit,
    onEnterPlacements: (String, String) -> Unit,
    onEnterKills: (String, String) -> Unit,
    onOpenOcrReview: (String, String) -> Unit,
    onOpenResultScreenshotCrop: (String, String, MatchResultScreenshotRole) -> Unit,
    onStartCorrection: (String, String) -> Unit,
    matchLobbyScreenshotIntake: @Composable () -> Unit = {},
    lobbyScreenshotIntakeViewModel: MatchLobbyScreenshotIntakeViewModel? = null,
    showLegacyManualReviewContent: Boolean = false,
    viewModel: MatchReviewViewModel = hiltViewModel(),
    ocrReviewViewModel: MatchOcrReviewViewModel? = null,
) {
    LaunchedEffect(tournamentId, matchId) {
        viewModel.load(tournamentId, matchId)
    }
    LaunchedEffect(tournamentId, matchId, lobbyScreenshotIntakeViewModel) {
        lobbyScreenshotIntakeViewModel?.load(
            tournamentId = tournamentId,
            matchId = matchId,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lobbyUiState by (lobbyScreenshotIntakeViewModel?.uiState
        ?: flowOf(MatchLobbyScreenshotIntakeUiState(isLoading = false)))
        .collectAsStateWithLifecycle(MatchLobbyScreenshotIntakeUiState(isLoading = false))
    val resolvedOcrReviewViewModel = ocrReviewViewModel ?: hiltViewModel<MatchOcrReviewViewModel>()
    val ocrUiState by resolvedOcrReviewViewModel.uiState.collectAsStateWithLifecycle()
    val ocrCacheAvailability by resolvedOcrReviewViewModel.cacheAvailability.collectAsStateWithLifecycle()
    LaunchedEffect(tournamentId, matchId, uiState.isAvailable, uiState.status) {
        if (uiState.isAvailable) {
            if (uiState.status == MatchStatus.FINALIZED) {
                resolvedOcrReviewViewModel.loadHistoricalEvidence(tournamentId, matchId)
            } else {
                resolvedOcrReviewViewModel.loadCached(tournamentId, matchId)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tournamentId, matchId, uiState.isAvailable, uiState.status) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.isAvailable &&
                uiState.status != MatchStatus.FINALIZED
            ) {
                resolvedOcrReviewViewModel.loadCached(tournamentId, matchId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val legacyPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri -> viewModel.onPhotoPickerResult(selectedUri?.toString()) },
    )
    val resultScreenshot1PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            viewModel.onPhotoPickerResult(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                selectedUri?.toString(),
            )
        },
    )
    val resultScreenshot2PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            viewModel.onPhotoPickerResult(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                selectedUri?.toString(),
            )
        },
    )
    val pdfDestinationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = viewModel::onDestinationResult,
    )
    val pngDestinationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
        onResult = viewModel::onDestinationResult,
    )
    LaunchedEffect(uiState.isPhotoPickerLaunchPending) {
        if (uiState.isPhotoPickerLaunchPending) {
            viewModel.onPhotoPickerLaunchHandled()
            try {
                legacyPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } catch (_: Exception) {
                viewModel.onPhotoPickerLaunchFailed()
            }
        }
    }
    val screenshot1 = uiState.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
    LaunchedEffect(screenshot1.isPhotoPickerLaunchPending) {
        if (screenshot1.isPhotoPickerLaunchPending) {
            viewModel.onPhotoPickerLaunchHandled(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
            try {
                resultScreenshot1PickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } catch (_: Exception) {
                viewModel.onPhotoPickerLaunchFailed(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
            }
        }
    }
    val screenshot2 = uiState.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
    LaunchedEffect(screenshot2.isPhotoPickerLaunchPending) {
        if (screenshot2.isPhotoPickerLaunchPending) {
            viewModel.onPhotoPickerLaunchHandled(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
            try {
                resultScreenshot2PickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } catch (_: Exception) {
                viewModel.onPhotoPickerLaunchFailed(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
            }
        }
    }
    val destinationRequest = uiState.resultDownloadUiState as?
        ResultDownloadUiState.DestinationLaunchRequested
    LaunchedEffect(destinationRequest) {
        val request = destinationRequest ?: return@LaunchedEffect
        viewModel.onDestinationLaunchHandled()
        try {
            when (request.format) {
                ResultExportFileFormat.PDF -> pdfDestinationLauncher.launch(request.suggestedDisplayName)
                ResultExportFileFormat.PNG -> pngDestinationLauncher.launch(request.suggestedDisplayName)
            }
        } catch (_: Exception) {
            viewModel.onDestinationLaunchFailed()
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
            MatchReviewNavigation.OCR_REVIEW -> {
                viewModel.onNavigationHandled()
                onOpenOcrReview(tournamentId, matchId)
            }
            MatchReviewNavigation.CORRECTION -> {
                viewModel.onNavigationHandled()
                onStartCorrection(tournamentId, matchId)
            }
            MatchReviewNavigation.DETAILS -> {
                viewModel.onNavigationHandled()
                onBackToDetails()
            }
            MatchReviewNavigation.RESULT_SCREENSHOT_1_CROP -> {
                viewModel.onNavigationHandled()
                onOpenResultScreenshotCrop(
                    tournamentId,
                    matchId,
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                )
            }
            MatchReviewNavigation.RESULT_SCREENSHOT_2_CROP -> {
                viewModel.onNavigationHandled()
                onOpenResultScreenshotCrop(
                    tournamentId,
                    matchId,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                )
            }
            null -> Unit
        }
    }
    BackHandler(onBack = viewModel::onBackToDetails)

    MatchReviewScreen(
        uiState = uiState,
        lobbyUiState = lobbyUiState,
        onEnterPlacements = viewModel::openPlacements,
        onEnterKills = viewModel::openKills,
        onOpenOcrReview = {
            if (showLegacyManualReviewContent) {
                viewModel.openOcrReview()
            } else {
                resolvedOcrReviewViewModel.reprocess(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    allowIncompleteEvidence = false,
                )
            }
        },
        onCalculatePoints = {
            if (showLegacyManualReviewContent) {
                viewModel.openOcrReview()
            } else {
                resolvedOcrReviewViewModel.reprocess(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    allowIncompleteEvidence = true,
                )
            }
        },
        onStartCorrection = viewModel::openCorrection,
        onBackToDetails = viewModel::onBackToDetails,
        onPrepareCsvExport = viewModel::prepareCsvExport,
        onPrepareGoogleSheetsExport = viewModel::prepareGoogleSheetsExport,
        onRequestResultDownload = viewModel::requestResultDownload,
        onFinalize = viewModel::finalizeMatch,
        onSelectScreenshot = viewModel::requestPhotoPicker,
        onSelectResultScreenshot = viewModel::requestPhotoPicker,
        onOpenResultScreenshotCrop = { role ->
            onOpenResultScreenshotCrop(tournamentId, matchId, role)
        },
        onLinkScreenshot = viewModel::linkScreenshot,
        onUnlinkScreenshot = viewModel::unlinkScreenshot,
        onRetryScreenshotUpload = viewModel::retryScreenshotUpload,
        onRetryResultScreenshotUpload = viewModel::retryResultScreenshotUpload,
        onRemoveResultScreenshot = viewModel::removeResultScreenshot,
        matchLobbyScreenshotIntake = matchLobbyScreenshotIntake,
        onSelectLobbyScreenshot = lobbyScreenshotIntakeViewModel?.let { intakeViewModel ->
            { index -> intakeViewModel.requestPhotoPicker(index) }
        } ?: {},
        onOpenLobbyScreenshotCrop = lobbyScreenshotIntakeViewModel?.let { intakeViewModel ->
            { index -> intakeViewModel.requestCropEditor(index) }
        } ?: {},
        showLegacyManualReviewContent = showLegacyManualReviewContent,
        showInlineOcrDetails = ocrCacheAvailability == MatchOcrCacheAvailability.READY,
        ocrCacheAvailability = ocrCacheAvailability,
        ocrUiState = ocrUiState,
        onOcrPlacementChanged = resolvedOcrReviewViewModel::onPlacementChanged,
        onOcrKillsChanged = resolvedOcrReviewViewModel::onKillsChanged,
        onOcrAssignedTeamSlotChanged = resolvedOcrReviewViewModel::onAssignedTeamSlotChanged,
        onOcrResetRowCorrection = resolvedOcrReviewViewModel::onResetRowCorrection,
        onOcrResetAllCorrections = resolvedOcrReviewViewModel::onResetAllCorrections,
        onOcrFinalize = resolvedOcrReviewViewModel::onFinalizeOcrCorrection,
        onOcrConfirmFinalizeWarnings = resolvedOcrReviewViewModel::onConfirmFinalizeWarnings,
        onOcrDismissFinalizeWarnings = resolvedOcrReviewViewModel::onDismissFinalizeWarnings,
    )
}

@Composable
fun MatchReviewScreen(
    uiState: MatchReviewUiState,
    lobbyUiState: MatchLobbyScreenshotIntakeUiState = MatchLobbyScreenshotIntakeUiState(isLoading = false),
    onEnterPlacements: () -> Unit,
    onEnterKills: () -> Unit,
    onOpenOcrReview: () -> Unit = {},
    onCalculatePoints: () -> Unit = {},
    onStartCorrection: () -> Unit = {},
    onBackToDetails: () -> Unit,
    onPrepareCsvExport: () -> Unit = {},
    onPrepareGoogleSheetsExport: () -> Unit = {},
    onRequestResultDownload: (ResultDownloadScope, ResultExportFileFormat) -> Unit = { _, _ -> },
    onFinalize: () -> Unit = {},
    onSelectScreenshot: () -> Unit = {},
    onSelectResultScreenshot: (MatchResultScreenshotRole) -> Unit = {},
    onOpenResultScreenshotCrop: (MatchResultScreenshotRole) -> Unit = {},
    onLinkScreenshot: () -> Unit = {},
    onUnlinkScreenshot: () -> Unit = {},
    onRetryScreenshotUpload: () -> Unit = {},
    onRetryResultScreenshotUpload: (MatchResultScreenshotRole) -> Unit = {},
    onRemoveResultScreenshot: (MatchResultScreenshotRole) -> Unit = {},
    onSelectLobbyScreenshot: (Int) -> Unit = {},
    onOpenLobbyScreenshotCrop: (Int) -> Unit = {},
    matchLobbyScreenshotIntake: @Composable () -> Unit = {},
    showLegacyManualReviewContent: Boolean = false,
    showInlineOcrDetails: Boolean = false,
    ocrCacheAvailability: MatchOcrCacheAvailability = MatchOcrCacheAvailability.UNKNOWN,
    ocrUiState: MatchOcrReviewUiState = MatchOcrReviewUiState.Loading,
    onOcrPlacementChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onOcrKillsChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onOcrAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onOcrResetRowCorrection: (rowIndex: Int) -> Unit = {},
    onOcrResetAllCorrections: () -> Unit = {},
    onOcrFinalize: () -> Unit = {},
    onOcrConfirmFinalizeWarnings: () -> Unit = {},
    onOcrDismissFinalizeWarnings: () -> Unit = {},
) {
    when {
        uiState.isLoading ||
            (uiState.isAvailable && !showLegacyManualReviewContent && lobbyUiState.isLoading) ->
            RankForgeLoadingState(
            message = stringResource(R.string.match_review_loading),
        )
        uiState.isNotFound -> MatchReviewNotFoundState(onBackToDetails)
        uiState.isAvailable -> MatchReviewContent(
            uiState = uiState,
            lobbyUiState = lobbyUiState,
            onEnterPlacements = onEnterPlacements,
            onEnterKills = onEnterKills,
            onOpenOcrReview = onOpenOcrReview,
            onCalculatePoints = onCalculatePoints,
            onStartCorrection = onStartCorrection,
            onBackToDetails = onBackToDetails,
            onPrepareCsvExport = onPrepareCsvExport,
            onPrepareGoogleSheetsExport = onPrepareGoogleSheetsExport,
            onRequestResultDownload = onRequestResultDownload,
            onFinalize = onFinalize,
            onSelectScreenshot = onSelectScreenshot,
            onSelectResultScreenshot = onSelectResultScreenshot,
            onOpenResultScreenshotCrop = onOpenResultScreenshotCrop,
            onLinkScreenshot = onLinkScreenshot,
            onUnlinkScreenshot = onUnlinkScreenshot,
            onRetryScreenshotUpload = onRetryScreenshotUpload,
            onRetryResultScreenshotUpload = onRetryResultScreenshotUpload,
            onRemoveResultScreenshot = onRemoveResultScreenshot,
            onSelectLobbyScreenshot = onSelectLobbyScreenshot,
            onOpenLobbyScreenshotCrop = onOpenLobbyScreenshotCrop,
            matchLobbyScreenshotIntake = matchLobbyScreenshotIntake,
            showLegacyManualReviewContent = showLegacyManualReviewContent,
            showInlineOcrDetails = showInlineOcrDetails,
            ocrCacheAvailability = ocrCacheAvailability,
            ocrUiState = ocrUiState,
            onOcrPlacementChanged = onOcrPlacementChanged,
            onOcrKillsChanged = onOcrKillsChanged,
            onOcrAssignedTeamSlotChanged = onOcrAssignedTeamSlotChanged,
            onOcrResetRowCorrection = onOcrResetRowCorrection,
            onOcrResetAllCorrections = onOcrResetAllCorrections,
            onOcrFinalize = onOcrFinalize,
            onOcrConfirmFinalizeWarnings = onOcrConfirmFinalizeWarnings,
            onOcrDismissFinalizeWarnings = onOcrDismissFinalizeWarnings,
        )
    }
}

@Composable
private fun MatchReviewContent(
    uiState: MatchReviewUiState,
    lobbyUiState: MatchLobbyScreenshotIntakeUiState,
    onEnterPlacements: () -> Unit,
    onEnterKills: () -> Unit,
    onOpenOcrReview: () -> Unit,
    onCalculatePoints: () -> Unit,
    onStartCorrection: () -> Unit,
    onBackToDetails: () -> Unit,
    onPrepareCsvExport: () -> Unit,
    onPrepareGoogleSheetsExport: () -> Unit,
    onRequestResultDownload: (ResultDownloadScope, ResultExportFileFormat) -> Unit,
    onFinalize: () -> Unit,
    onSelectScreenshot: () -> Unit,
    onSelectResultScreenshot: (MatchResultScreenshotRole) -> Unit,
    onOpenResultScreenshotCrop: (MatchResultScreenshotRole) -> Unit,
    onLinkScreenshot: () -> Unit,
    onUnlinkScreenshot: () -> Unit,
    onRetryScreenshotUpload: () -> Unit,
    onRetryResultScreenshotUpload: (MatchResultScreenshotRole) -> Unit,
    onRemoveResultScreenshot: (MatchResultScreenshotRole) -> Unit,
    onSelectLobbyScreenshot: (Int) -> Unit,
    onOpenLobbyScreenshotCrop: (Int) -> Unit,
    matchLobbyScreenshotIntake: @Composable () -> Unit,
    showLegacyManualReviewContent: Boolean,
    showInlineOcrDetails: Boolean,
    ocrCacheAvailability: MatchOcrCacheAvailability,
    ocrUiState: MatchOcrReviewUiState,
    onOcrPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onOcrKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onOcrAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onOcrResetRowCorrection: (rowIndex: Int) -> Unit,
    onOcrResetAllCorrections: () -> Unit,
    onOcrFinalize: () -> Unit,
    onOcrConfirmFinalizeWarnings: () -> Unit,
    onOcrDismissFinalizeWarnings: () -> Unit,
) {
    var showFinalizeConfirmation by remember { mutableStateOf(false) }
    var showCorrectionConfirmation by remember { mutableStateOf(false) }
    var showResultScopeDialog by remember { mutableStateOf(false) }
    var showResultFormatDialog by remember { mutableStateOf(false) }
    var selectedResultScope by remember { mutableStateOf<ResultDownloadScope?>(null) }
    var selectedResultFormat by remember { mutableStateOf<ResultExportFileFormat?>(null) }
    var showOcrPreflight by remember { mutableStateOf(false) }
    var ocrReviewOpened by rememberSaveable { mutableStateOf(false) }
    val ocrPreflightItems = classifyOcrScreenshotPreflight(
        lobbySlots = lobbyUiState.slots,
        resultSlots = uiState.resultScreenshots,
    )
    LaunchedEffect(ocrPreflightItems) {
        if (ocrPreflightItems.isNotEmpty()) {
            ocrReviewOpened = false
        }
    }
    val shouldShowInlineOcrDetails = showInlineOcrDetails ||
        ocrReviewOpened ||
        (uiState.status == MatchStatus.FINALIZED && ocrUiState.hasPreservedResultOcrEvidence())

    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_REVIEW_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(
                if (showLegacyManualReviewContent) {
                    R.string.match_review_title
                } else {
                    R.string.match_review_simplified_title
                },
                uiState.matchNumber ?: 0,
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (showLegacyManualReviewContent) {
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
                Button(
                    onClick = onPrepareCsvExport,
                    enabled = uiState.canPrepareMatchCsvExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MATCH_REVIEW_CSV_EXPORT_ACTION_TEST_TAG),
                ) {
                    Text(text = "Prepare CSV export")
                }
                when (uiState.csvExportResult) {
                    is AndroidExportResult.CsvReady -> Text(
                        text = "CSV export ready",
                        modifier = Modifier.testTag(MATCH_REVIEW_CSV_EXPORT_STATUS_TEST_TAG),
                    )
                    is AndroidExportResult.Blocked -> Text(
                        text = "CSV export blocked",
                        modifier = Modifier.testTag(MATCH_REVIEW_CSV_EXPORT_STATUS_TEST_TAG),
                    )
                    is AndroidExportResult.Unavailable -> Text(
                        text = "CSV export unavailable",
                        modifier = Modifier.testTag(MATCH_REVIEW_CSV_EXPORT_STATUS_TEST_TAG),
                    )
                    else -> Unit
                }
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 1.dp)
                    .testTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG),
            ) {
                matchLobbyScreenshotIntake()
            }
            if (shouldShowInlineOcrDetails && ocrUiState.hasLobbyPlayerEvidence()) {
                MatchReviewLobbyPlayerDetailsContent(ocrUiState)
            }
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Text(
                text = stringResource(R.string.match_review_result_screenshots_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG),
            )
        }
        ResultScreenshotSelector(
            resultScreenshots = uiState.resultScreenshots,
            isEditable = uiState.isEditable,
            onSelectScreenshot = onSelectResultScreenshot,
            onOpenCrop = onOpenResultScreenshotCrop,
            onRemoveScreenshot = onRemoveResultScreenshot,
        )
        if (showOcrPreflight) {
            MatchOcrScreenshotPreflightDialog(
                items = ocrPreflightItems,
                onCancel = { showOcrPreflight = false },
                onCalculatePoints = {
                    showOcrPreflight = false
                    ocrReviewOpened = true
                    onCalculatePoints()
                },
                onSelectLobbyScreenshot = { index ->
                    showOcrPreflight = false
                    ocrReviewOpened = false
                    onSelectLobbyScreenshot(index)
                },
                onOpenLobbyScreenshotCrop = { index ->
                    showOcrPreflight = false
                    ocrReviewOpened = false
                    onOpenLobbyScreenshotCrop(index)
                },
                onSelectResultScreenshot = { role ->
                    showOcrPreflight = false
                    ocrReviewOpened = false
                    onSelectResultScreenshot(role)
                },
                onOpenResultScreenshotCrop = { role ->
                    showOcrPreflight = false
                    ocrReviewOpened = false
                    onOpenResultScreenshotCrop(role)
                },
            )
        }
        if (shouldShowInlineOcrDetails) {
            MatchReviewResultOcrDetailsContent(
                uiState = ocrUiState,
                onPlacementChanged = onOcrPlacementChanged,
                onKillsChanged = onOcrKillsChanged,
                onAssignedTeamSlotChanged = onOcrAssignedTeamSlotChanged,
                onResetRowCorrection = onOcrResetRowCorrection,
                onResetAllCorrections = onOcrResetAllCorrections,
                onFinalizeOcrCorrection = onOcrFinalize,
                onConfirmFinalizeWarnings = onOcrConfirmFinalizeWarnings,
                onDismissFinalizeWarnings = onOcrDismissFinalizeWarnings,
            )
        }
        when (ocrCacheAvailability) {
            MatchOcrCacheAvailability.READY -> Text(
                text = "OCR data ready",
                modifier = Modifier.testTag(MATCH_REVIEW_OCR_READY_TEST_TAG),
            )
            MatchOcrCacheAvailability.STALE_OR_INCOMPLETE -> Text(
                text = "OCR data needs refresh",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MATCH_REVIEW_OCR_STALE_TEST_TAG),
            )
            MatchOcrCacheAvailability.UNKNOWN,
            MatchOcrCacheAvailability.NOT_AVAILABLE,
            -> Unit
        }
        if (!showLegacyManualReviewContent && shouldShowInlineOcrDetails) {
            val readyOcrUiState = ocrUiState as? MatchOcrReviewUiState.Ready
            readyOcrUiState?.correctionDraft?.let { correctionDraft ->
                MatchOcrReviewFinalizeAction(
                    correctionDraft = correctionDraft,
                    finalization = readyOcrUiState.finalization,
                    onFinalizeOcrCorrection = onOcrFinalize,
                )
            }
        }
        if (uiState.isEditable) {
            Button(
                onClick = {
                    if (showLegacyManualReviewContent || ocrPreflightItems.isEmpty()) {
                        ocrReviewOpened = true
                        onOpenOcrReview()
                    } else {
                        showOcrPreflight = true
                    }
                },
                enabled = uiState.isEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.match_ocr_review_title))
            }
        }
        if (showLegacyManualReviewContent && uiState.isEditable) {
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
                onClick = {
                    selectedResultScope = null
                    selectedResultFormat = null
                    showResultFormatDialog = false
                    showResultScopeDialog = true
                },
                enabled = uiState.canDownloadResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.match_review_download_result_action))
            }
            when (val downloadState = uiState.resultDownloadUiState) {
                is ResultDownloadUiState.Generating -> Text(
                    text = stringResource(R.string.match_review_download_generating),
                    modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_STATUS_TEST_TAG),
                )
                is ResultDownloadUiState.Saving -> Text(
                    text = stringResource(R.string.match_review_download_saving),
                    modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_STATUS_TEST_TAG),
                )
                is ResultDownloadUiState.Success -> Text(
                    text = stringResource(
                        if (downloadState.userSelectedDestination) {
                            R.string.match_review_download_saved_successfully
                        } else {
                            R.string.match_review_download_saved_to_downloads
                        },
                        downloadState.format.extension.uppercase(),
                    ),
                    modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_STATUS_TEST_TAG),
                )
                is ResultDownloadUiState.Failure -> Text(
                    text = stringResource(downloadState.reason.toMessageRes()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_STATUS_TEST_TAG),
                )
                ResultDownloadUiState.Idle,
                is ResultDownloadUiState.DestinationLaunchRequested,
                is ResultDownloadUiState.WaitingForDestination,
                -> Unit
            }
        }
        if (showLegacyManualReviewContent && uiState.status == MatchStatus.FINALIZED) {
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
            Text(
                stringResource(
                    if (showLegacyManualReviewContent) {
                        R.string.back_to_match_details_action
                    } else {
                        R.string.match_review_simplified_back_action
                    },
                ),
            )
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
    if (showResultScopeDialog) {
        ResultDownloadScopeDialog(
            selectedScope = selectedResultScope,
            onScopeSelected = { selectedResultScope = it },
            onDismiss = { showResultScopeDialog = false },
            onContinue = {
                if (selectedResultScope != null) {
                    showResultScopeDialog = false
                    showResultFormatDialog = true
                }
            },
        )
    }
    if (showResultFormatDialog) {
        ResultDownloadFormatDialog(
            selectedFormat = selectedResultFormat,
            onFormatSelected = { selectedResultFormat = it },
            onBack = {
                selectedResultFormat = null
                showResultFormatDialog = false
                showResultScopeDialog = true
            },
            onDismiss = { showResultFormatDialog = false },
            onDownload = {
                val scope = selectedResultScope
                val format = selectedResultFormat
                if (scope != null && format != null) {
                    showResultFormatDialog = false
                    onRequestResultDownload(scope, format)
                }
            },
        )
    }
}

@Composable
private fun ResultDownloadScopeDialog(
    selectedScope: ResultDownloadScope?,
    onScopeSelected: (ResultDownloadScope) -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_SCOPE_DIALOG_TEST_TAG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_review_download_result_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall)) {
                ResultDownloadRadioRow(
                    label = stringResource(R.string.match_review_download_current_match),
                    selected = selectedScope == ResultDownloadScope.CURRENT_MATCH,
                    onClick = { onScopeSelected(ResultDownloadScope.CURRENT_MATCH) },
                    testTag = MATCH_REVIEW_DOWNLOAD_SCOPE_CURRENT_MATCH_TEST_TAG,
                )
                ResultDownloadRadioRow(
                    label = stringResource(R.string.match_review_download_whole_tournament),
                    selected = selectedScope == ResultDownloadScope.WHOLE_TOURNAMENT,
                    onClick = { onScopeSelected(ResultDownloadScope.WHOLE_TOURNAMENT) },
                    testTag = MATCH_REVIEW_DOWNLOAD_SCOPE_TOURNAMENT_TEST_TAG,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_SCOPE_CANCEL_TEST_TAG),
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onContinue,
                enabled = selectedScope != null,
                modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_SCOPE_CONTINUE_TEST_TAG),
            ) {
                Text(stringResource(R.string.continue_action))
            }
        },
    )
}

@Composable
private fun ResultDownloadFormatDialog(
    selectedFormat: ResultExportFileFormat?,
    onFormatSelected: (ResultExportFileFormat) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_FORMAT_DIALOG_TEST_TAG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_review_download_choose_format)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall)) {
                ResultDownloadRadioRow(
                    label = stringResource(R.string.match_review_download_pdf),
                    selected = selectedFormat == ResultExportFileFormat.PDF,
                    onClick = { onFormatSelected(ResultExportFileFormat.PDF) },
                    testTag = MATCH_REVIEW_DOWNLOAD_FORMAT_PDF_TEST_TAG,
                )
                ResultDownloadRadioRow(
                    label = stringResource(R.string.match_review_download_png),
                    selected = selectedFormat == ResultExportFileFormat.PNG,
                    onClick = { onFormatSelected(ResultExportFileFormat.PNG) },
                    testTag = MATCH_REVIEW_DOWNLOAD_FORMAT_PNG_TEST_TAG,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_FORMAT_BACK_TEST_TAG),
            ) {
                Text(stringResource(R.string.back_action))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDownload,
                enabled = selectedFormat != null,
                modifier = Modifier.testTag(MATCH_REVIEW_DOWNLOAD_FORMAT_CONFIRM_TEST_TAG),
            ) {
                Text(stringResource(R.string.download_action))
            }
        },
    )
}

@Composable
private fun ResultDownloadRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun MatchOcrScreenshotPreflightDialog(
    items: List<OcrScreenshotPreflightItem>,
    onCancel: () -> Unit,
    onCalculatePoints: () -> Unit,
    onSelectLobbyScreenshot: (Int) -> Unit,
    onOpenLobbyScreenshotCrop: (Int) -> Unit,
    onSelectResultScreenshot: (MatchResultScreenshotRole) -> Unit,
    onOpenResultScreenshotCrop: (MatchResultScreenshotRole) -> Unit,
) {
    val hasProcessing = items.any { it.issue == OcrScreenshotPreflightIssue.PROCESSING }
    AlertDialog(
        modifier = Modifier.testTag(MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG),
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.match_ocr_preflight_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
            ) {
                Text(stringResource(R.string.match_ocr_preflight_intro))
                items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(matchReviewOcrPreflightItemTestTag(item.identity)),
                        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
                    ) {
                        Text(item.issue.message(item.identity))
                        item.actionLabel()?.let { action ->
                            OutlinedButton(
                                onClick = {
                                    when (val identity = item.identity) {
                                        is OcrScreenshotPreflightIdentity.Lobby -> when (item.issue) {
                                            OcrScreenshotPreflightIssue.CROP_REQUIRED ->
                                                onOpenLobbyScreenshotCrop(identity.index)
                                            OcrScreenshotPreflightIssue.MISSING,
                                            OcrScreenshotPreflightIssue.LOCAL_FILE_MISSING,
                                            OcrScreenshotPreflightIssue.PROCESSING,
                                            -> onSelectLobbyScreenshot(identity.index)
                                        }
                                        is OcrScreenshotPreflightIdentity.Result -> when (item.issue) {
                                            OcrScreenshotPreflightIssue.CROP_REQUIRED ->
                                                onOpenResultScreenshotCrop(identity.role)
                                            OcrScreenshotPreflightIssue.MISSING,
                                            OcrScreenshotPreflightIssue.LOCAL_FILE_MISSING,
                                            OcrScreenshotPreflightIssue.PROCESSING,
                                            -> onSelectResultScreenshot(identity.role)
                                        }
                                    }
                                },
                                modifier = Modifier.testTag(
                                    matchReviewOcrPreflightActionTestTag(item.identity, item.issue),
                                ),
                            ) {
                                Text(action)
                            }
                        }
                    }
                }
                if (hasProcessing) {
                    Text(
                        text = stringResource(R.string.match_ocr_preflight_processing_message),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(stringResource(R.string.match_ocr_preflight_incomplete_message))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(MATCH_REVIEW_OCR_PREFLIGHT_CANCEL_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        },
        confirmButton = {
            Button(
                onClick = onCalculatePoints,
                enabled = !hasProcessing,
                modifier = Modifier.testTag(MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG),
            ) {
                Text(stringResource(R.string.calculate_points_action))
            }
        },
    )
}

@Composable
private fun OcrScreenshotPreflightIssue.message(
    identity: OcrScreenshotPreflightIdentity,
): String {
    val number = when (identity) {
        is OcrScreenshotPreflightIdentity.Lobby -> identity.index
        is OcrScreenshotPreflightIdentity.Result -> identity.role.numberForUi()
    }
    return when (this) {
        OcrScreenshotPreflightIssue.MISSING -> stringResourceForPreflight(
            identity,
            R.string.match_ocr_preflight_missing_lobby,
            R.string.match_ocr_preflight_missing_result,
            number,
        )
        OcrScreenshotPreflightIssue.LOCAL_FILE_MISSING -> stringResourceForPreflight(
            identity,
            R.string.match_ocr_preflight_local_missing_lobby,
            R.string.match_ocr_preflight_local_missing_result,
            number,
        )
        OcrScreenshotPreflightIssue.CROP_REQUIRED -> stringResourceForPreflight(
            identity,
            R.string.match_ocr_preflight_crop_lobby,
            R.string.match_ocr_preflight_crop_result,
            number,
        )
        OcrScreenshotPreflightIssue.PROCESSING -> stringResourceForPreflight(
            identity,
            R.string.match_ocr_preflight_processing_lobby,
            R.string.match_ocr_preflight_processing_result,
            number,
        )
    }
}

@Composable
private fun stringResourceForPreflight(
    identity: OcrScreenshotPreflightIdentity,
    lobbyRes: Int,
    resultRes: Int,
    number: Int,
): String = stringResource(
    if (identity is OcrScreenshotPreflightIdentity.Lobby) lobbyRes else resultRes,
    number,
)

@Composable
private fun OcrScreenshotPreflightItem.actionLabel(): String? {
    val number = userFacingNumber
    return when (issue) {
        OcrScreenshotPreflightIssue.MISSING -> when (identity) {
            is OcrScreenshotPreflightIdentity.Lobby -> stringResource(
                R.string.match_ocr_preflight_select_lobby,
                number,
            )
            is OcrScreenshotPreflightIdentity.Result -> stringResource(
                R.string.match_ocr_preflight_select_result,
                number,
            )
        }
        OcrScreenshotPreflightIssue.LOCAL_FILE_MISSING -> when (identity) {
            is OcrScreenshotPreflightIdentity.Lobby -> stringResource(
                R.string.match_ocr_preflight_replace_lobby,
                number,
            )
            is OcrScreenshotPreflightIdentity.Result -> stringResource(
                R.string.match_ocr_preflight_replace_result,
                number,
            )
        }
        OcrScreenshotPreflightIssue.CROP_REQUIRED -> when (identity) {
            is OcrScreenshotPreflightIdentity.Lobby -> stringResource(
                R.string.match_ocr_preflight_crop_action_lobby,
                number,
            )
            is OcrScreenshotPreflightIdentity.Result -> stringResource(
                R.string.match_ocr_preflight_crop_action_result,
                number,
            )
        }
        OcrScreenshotPreflightIssue.PROCESSING -> null
    }
}

@Composable
private fun MatchReviewLobbyPlayerDetailsContent(
    ocrUiState: MatchOcrReviewUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_REVIEW_LOBBY_PLAYER_DETAILS_SECTION_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        when (ocrUiState) {
            MatchOcrReviewUiState.Loading -> Text(
                text = stringResource(R.string.match_ocr_review_loading),
            )
            is MatchOcrReviewUiState.Empty -> {
                if (ocrUiState.lobbyPlayers.isNotEmpty()) {
                    MatchReviewLobbyPlayersPager(
                        lobbyPlayers = ocrUiState.lobbyPlayers,
                        teamNamesBySlot = ocrUiState.teamNamesBySlot,
                    )
                } else {
                    Text(text = stringResource(R.string.match_ocr_review_empty_message))
                }
            }
            is MatchOcrReviewUiState.Error -> Text(
                text = ocrUiState.message,
                color = MaterialTheme.colorScheme.error,
            )
            is MatchOcrReviewUiState.Ready -> {
                if (ocrUiState.lobbyPlayers.isNotEmpty()) {
                    MatchReviewLobbyPlayersPager(
                        lobbyPlayers = ocrUiState.lobbyPlayers,
                        teamNamesBySlot = ocrUiState.teamNamesBySlot,
                    )
                } else {
                    Text(text = stringResource(R.string.match_ocr_review_empty_message))
                }
            }
        }
    }
}

@Composable
private fun MatchReviewLobbyPlayersPager(
    lobbyPlayers: List<MatchOcrReviewLobbySlotUiState>,
    teamNamesBySlot: Map<Int, String>,
) {
    val orderedSlots = lobbyPlayers.sortedBy { it.slotNumber }
    val pagerState = rememberPagerState(pageCount = { orderedSlots.size })

    LaunchedEffect(pagerState, orderedSlots.size) {
        val lastPage = orderedSlots.lastIndex
        if (lastPage >= 0 && pagerState.currentPage > lastPage) {
            pagerState.scrollToPage(lastPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.LOBBY_PLAYERS),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        if (orderedSlots.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                .testTag(MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG),
            ) { page ->
                orderedSlots.getOrNull(page)?.let { slot ->
                    MatchReviewOcrPagerItem {
                        MatchOcrReviewLobbySlotContent(
                            slot = slot,
                            teamNamesBySlot = teamNamesBySlot,
                        )
                    }
                }
            }
        }
    }
}

private fun MatchOcrReviewUiState.hasLobbyPlayerEvidence(): Boolean = when (this) {
    is MatchOcrReviewUiState.Empty -> lobbyPlayers.any { it.players.isNotEmpty() }
    is MatchOcrReviewUiState.Ready -> lobbyPlayers.any { it.players.isNotEmpty() }
    MatchOcrReviewUiState.Loading,
    is MatchOcrReviewUiState.Error,
    -> false
}

private fun MatchOcrReviewUiState.hasPreservedResultOcrEvidence(): Boolean =
    this is MatchOcrReviewUiState.Ready && rows.isNotEmpty()

@Composable
private fun MatchReviewResultOcrDetailsContent(
    uiState: MatchOcrReviewUiState,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onResetRowCorrection: (rowIndex: Int) -> Unit,
    onResetAllCorrections: () -> Unit,
    onFinalizeOcrCorrection: () -> Unit,
    onConfirmFinalizeWarnings: () -> Unit,
    onDismissFinalizeWarnings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        when (uiState) {
            MatchOcrReviewUiState.Loading -> Text(
                text = stringResource(R.string.match_ocr_review_loading),
            )
            is MatchOcrReviewUiState.Empty -> {
                val preview = uiState.matchResultOcrPreview
                if (preview is MatchResultOcrPreviewUiState.Ready && preview.rows.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.match_ocr_review_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    MatchReviewResultPreviewPager(
                        preview = preview,
                        reviewRowsByPosition = emptyMap(),
                        teamNamesBySlot = emptyMap(),
                    )
                } else {
                    MatchResultOcrPreviewSection(preview)
                    if (preview == MatchResultOcrPreviewUiState.NotRequested) {
                        Text(text = stringResource(R.string.match_ocr_review_empty_message))
                    }
                }
            }
            is MatchOcrReviewUiState.Error -> Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
            )
            is MatchOcrReviewUiState.Ready -> MatchReviewResultRowsPagerContent(
                uiState = uiState,
                onPlacementChanged = onPlacementChanged,
                onKillsChanged = onKillsChanged,
                onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                onResetRowCorrection = onResetRowCorrection,
                onResetAllCorrections = onResetAllCorrections,
                onFinalizeOcrCorrection = onFinalizeOcrCorrection,
                onConfirmFinalizeWarnings = onConfirmFinalizeWarnings,
                onDismissFinalizeWarnings = onDismissFinalizeWarnings,
            )
        }
    }
}

@Composable
private fun MatchReviewResultPreviewPager(
    preview: MatchResultOcrPreviewUiState.Ready,
    reviewRowsByPosition: Map<Int, MatchOcrReviewRowUiState>,
    teamNamesBySlot: Map<Int, String>,
) {
    val rows = preview.rows
    val pagerState = rememberPagerState(pageCount = { rows.size })

    LaunchedEffect(pagerState, rows.size) {
        val lastPage = rows.lastIndex
        if (lastPage >= 0 && pagerState.currentPage > lastPage) {
            pagerState.scrollToPage(lastPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.COMPACT_LIST),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        if (rows.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_RESULT_OCR_PREVIEW_PAGER_TEST_TAG),
            ) { page ->
                rows.getOrNull(page)?.let { previewRow ->
                    MatchReviewOcrPagerItem {
                        MatchOcrReviewCompactRow(
                            previewRow = previewRow,
                            reviewRow = reviewRowsByPosition[previewRow.position],
                            teamNamesBySlot = teamNamesBySlot,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchReviewResultRowsPagerContent(
    uiState: MatchOcrReviewUiState.Ready,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onResetRowCorrection: (rowIndex: Int) -> Unit,
    onResetAllCorrections: () -> Unit,
    onFinalizeOcrCorrection: () -> Unit,
    onConfirmFinalizeWarnings: () -> Unit,
    onDismissFinalizeWarnings: () -> Unit,
) {
    val previewRowsByPosition = (uiState.matchResultOcrPreview as? MatchResultOcrPreviewUiState.Ready)
        ?.rows
        .orEmpty()
        .associateBy { it.position }
    val correctionRowsByIndex = uiState.correctionDraft?.rows.orEmpty().associateBy { it.rowIndex }
    val rows = uiState.rows
    val pagerState = rememberPagerState(pageCount = { rows.size })
    val teamSlotAssistant = MatchOcrReviewTeamSlotAssistant.deriveForUiState(uiState)

    LaunchedEffect(pagerState, rows.size) {
        val lastPage = rows.lastIndex
        if (lastPage >= 0 && pagerState.currentPage > lastPage) {
            pagerState.scrollToPage(lastPage)
        }
    }

    uiState.correctionDraft?.let { correctionDraft ->
        MatchOcrReviewCorrectionSummary(
            correctionDraft = correctionDraft,
            finalization = uiState.finalization,
            onResetAllCorrections = onResetAllCorrections,
            onFinalizeOcrCorrection = onFinalizeOcrCorrection,
            showCorrectionSummaryDetails = false,
            showResetAllCorrectionsAction = false,
            showFinalizeAction = false,
        )
    }
    if (teamSlotAssistant != null &&
        teamSlotAssistant.unresolvedRowIndexes.isNotEmpty() &&
        !uiState.finalization.isFinalized
    ) {
        MatchOcrReviewRemainingTeamSlotsSection(teamSlotAssistant)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.ROW_LIST),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
    ) {
        if (rows.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG),
            ) { page ->
                rows.getOrNull(page)?.let { row ->
                    MatchReviewOcrPagerItem {
                        MatchOcrReviewRow(
                            row = row,
                            previewRow = previewRowsByPosition[row.rowIndex + 1],
                            teamNamesBySlot = uiState.teamNamesBySlot,
                            correctionDraft = correctionRowsByIndex[row.rowIndex],
                            onPlacementChanged = onPlacementChanged,
                            onKillsChanged = onKillsChanged,
                            onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                            onResetRowCorrection = onResetRowCorrection,
                            correctionEnabled = !uiState.finalization.isFinalized,
                            availableTeamSlotOptions = if (uiState.finalization.isFinalized) {
                                emptyList()
                            } else {
                                teamSlotAssistant
                                    ?.availableOptionsByRow
                                    ?.get(row.rowIndex)
                                    .orEmpty()
                            },
                            showWarningDetails = false,
                            compactFieldRow = true,
                            showBlockerDetails = false,
                            compactResetAction = true,
                        )
                    }
                }
            }
        }
    }
    if (uiState.finalization.showWarningConfirmation) {
        MatchOcrReviewFinalizeWarningDialog(
            warningCount = uiState.correctionDraft?.warningCount ?: 0,
            onConfirmFinalizeWarnings = onConfirmFinalizeWarnings,
            onDismissFinalizeWarnings = onDismissFinalizeWarnings,
        )
    }
}

@Composable
private fun MatchReviewOcrPagerItem(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RankForgeSpacing.Small),
        ) {
            content()
        }
    }
}

@Composable
private fun ResultScreenshotSelector(
    resultScreenshots: List<MatchResultScreenshotSlotUiState>,
    isEditable: Boolean,
    onSelectScreenshot: (MatchResultScreenshotRole) -> Unit,
    onOpenCrop: (MatchResultScreenshotRole) -> Unit,
    onRemoveScreenshot: (MatchResultScreenshotRole) -> Unit,
) {
    val roles = listOf(
        MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        MatchResultScreenshotRole.MATCH_RESULT_LOWER,
    )
    val selectedPages = roles.mapNotNull { role ->
        resultScreenshots.slot(role)
            .takeIf { it.hasSelection() }
            ?.let { slot -> role to slot }
    }
    val selectedRoles = selectedPages.map { it.first }
    val nextEmptyRole = roles.firstOrNull { role ->
        !resultScreenshots.slot(role).hasSelection()
    }
    var activeRoleName by rememberSaveable { mutableStateOf<String?>(null) }
    val activeRole = when (activeRoleName) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER.name -> MatchResultScreenshotRole.MATCH_RESULT_UPPER
        MatchResultScreenshotRole.MATCH_RESULT_LOWER.name -> MatchResultScreenshotRole.MATCH_RESULT_LOWER
        else -> null
    }
    val pagerState = rememberPagerState(pageCount = { selectedPages.size })

    LaunchedEffect(selectedRoles) {
        if (selectedRoles.isEmpty()) {
            activeRoleName = null
            return@LaunchedEffect
        }
        val activePage = activeRole?.let(selectedRoles::indexOf) ?: -1
        val targetPage = if (activePage >= 0) activePage else 0
        activeRoleName = selectedRoles[targetPage].name
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState, selectedRoles) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selectedRoles.getOrNull(page)?.let { activeRoleName = it.name }
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        if (selectedPages.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG),
            ) { page ->
                selectedPages.getOrNull(page)?.let { (role, slot) ->
                    ResultScreenshotPage(
                        screenshotNumber = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 2,
                        slot = slot,
                        isEditable = isEditable,
                        onSelectScreenshot = onSelectScreenshot,
                        onOpenCrop = onOpenCrop,
                        onRemoveScreenshot = onRemoveScreenshot,
                    )
                }
            }
        }
        nextEmptyRole?.let { role ->
            val screenshotNumber = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 2
            val slot = resultScreenshots.slot(role)
            Button(
                onClick = { onSelectScreenshot(role) },
                enabled = isEditable && !slot.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG),
            ) {
                Text(
                    stringResource(
                        R.string.match_review_result_screenshot_select_action,
                        screenshotNumber,
                    ),
                )
            }
        }
    }
}

private fun MatchResultScreenshotSlotUiState.hasSelection(): Boolean =
    hasLinkedAsset || !selectedScreenshotUri.isNullOrBlank()

@Composable
private fun ResultScreenshotPage(
    screenshotNumber: Int,
    slot: MatchResultScreenshotSlotUiState,
    isEditable: Boolean,
    onSelectScreenshot: (MatchResultScreenshotRole) -> Unit,
    onOpenCrop: (MatchResultScreenshotRole) -> Unit,
    onRemoveScreenshot: (MatchResultScreenshotRole) -> Unit,
) {
    val role = if (screenshotNumber == 1) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER
    } else {
        MatchResultScreenshotRole.MATCH_RESULT_LOWER
    }
    val previewImageUri = if (
        slot.hasLinkedAsset && !slot.isLocalFileMissing && slot.hasConfirmedCrop
    ) {
        slot.localPreviewUri?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (screenshotNumber == 1) {
                    MATCH_REVIEW_RESULT_SCREENSHOT_1_SECTION_TEST_TAG
                } else {
                    MATCH_REVIEW_RESULT_SCREENSHOT_2_SECTION_TEST_TAG
                },
            ),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        previewImageUri?.let { imageUri ->
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
                        R.string.match_review_result_screenshot_preview_description,
                        screenshotNumber,
                    ),
                    sourceImageWidth = slot.originalWidth ?: slot.selectedScreenshotWidth,
                    sourceImageHeight = slot.originalHeight ?: slot.selectedScreenshotHeight,
                    modifier = Modifier.fillMaxSize(),
                    testTag = if (screenshotNumber == 1) {
                        MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG
                    } else {
                        MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG
                    },
                )
                if (isEditable) {
                    ResultScreenshotActionOverlay(
                        role = role,
                        slot = slot,
                        onSelectScreenshot = onSelectScreenshot,
                        onOpenCrop = onOpenCrop,
                        onRemoveScreenshot = onRemoveScreenshot,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
        if (slot.isValidationInProgress) {
            Text(text = stringResource(R.string.match_review_screenshot_validating))
        }
        if (slot.isSelectedScreenshotValidated) {
            Text(text = stringResource(R.string.match_review_screenshot_selected_and_validated))
        }
        if (slot.hasLinkedAsset && !slot.hasConfirmedCrop) {
            Text(text = stringResource(R.string.match_review_result_screenshot_crop_required))
        }
        slot.photoPickerError?.let { error ->
            Text(
                text = stringResource(error.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        slot.imageValidationError?.let { error ->
            Text(
                text = stringResource(error.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (slot.isDuplicateDetectionInProgress) {
            Text(text = stringResource(R.string.match_review_screenshot_duplicate_checking))
        }
        slot.duplicateInfo?.let { info ->
            Text(
                text = stringResource(info.toMessageRes()),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        slot.duplicateError?.let { error ->
            Text(
                text = stringResource(error.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (slot.isPreservationInProgress) {
            Text(text = stringResource(R.string.match_review_screenshot_preservation_checking))
        }
        slot.preservationError?.let { error ->
            Text(
                text = stringResource(error.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (slot.isUploadInProgress) {
            Text(text = stringResource(R.string.match_review_screenshot_uploading))
        }
        slot.uploadError?.let { error ->
            Text(
                text = stringResource(error.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (isEditable && previewImageUri == null) {
            ResultScreenshotActionRow(
                role = role,
                slot = slot,
                onSelectScreenshot = onSelectScreenshot,
                onOpenCrop = onOpenCrop,
                onRemoveScreenshot = onRemoveScreenshot,
            )
        }
    }
}

@Composable
private fun ResultScreenshotActionOverlay(
    role: MatchResultScreenshotRole,
    slot: MatchResultScreenshotSlotUiState,
    onSelectScreenshot: (MatchResultScreenshotRole) -> Unit,
    onOpenCrop: (MatchResultScreenshotRole) -> Unit,
    onRemoveScreenshot: (MatchResultScreenshotRole) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = Modifier
            .then(modifier)
            .padding(end = 8.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultScreenshotActionIconButton(
            symbol = "✎",
            contentDescription = stringResource(R.string.match_review_screenshot_replace_content_description),
            enabled = !slot.isBusy,
            onClick = { onSelectScreenshot(role) },
            testTag = role.replaceActionTestTag(),
            iconModifier = Modifier.offset(y = (-2).dp),
        )
        ResultScreenshotActionIconButton(
            symbol = "✂",
            contentDescription = stringResource(R.string.match_review_screenshot_crop_content_description),
            enabled = slot.hasLinkedAsset && !slot.isLocalFileMissing && !slot.isBusy,
            onClick = { onOpenCrop(role) },
            testTag = role.cropActionTestTag(),
            iconModifier = Modifier.offset(y = (-2).dp),
        )
        ResultScreenshotActionIconButton(
            symbol = "×",
            contentDescription = stringResource(R.string.match_review_screenshot_remove_content_description),
            enabled = slot.hasLinkedAsset && !slot.isBusy,
            onClick = { onRemoveScreenshot(role) },
            testTag = role.removeActionTestTag(),
        )
    }
}

private fun MatchResultScreenshotRole.replaceActionTestTag(): String =
    if (this == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
        MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG
    } else {
        MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG
    }

private fun MatchResultScreenshotRole.cropActionTestTag(): String =
    if (this == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
        MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG
    } else {
        MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG
    }

private fun MatchResultScreenshotRole.removeActionTestTag(): String =
    if (this == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
        MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG
    } else {
        MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG
    }

@Composable
private fun ResultScreenshotActionIconButton(
    symbol: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    iconModifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier.size(36.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = CircleShape,
        tonalElevation = RankForgeSpacing.ExtraSmall,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .testTag(testTag)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    modifier = iconModifier,
                    text = symbol,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp),
                )
            }
        }
    }
}

@Composable
private fun ResultScreenshotActionRow(
    role: MatchResultScreenshotRole,
    slot: MatchResultScreenshotSlotUiState,
    onSelectScreenshot: (MatchResultScreenshotRole) -> Unit,
    onOpenCrop: (MatchResultScreenshotRole) -> Unit,
    onRemoveScreenshot: (MatchResultScreenshotRole) -> Unit,
) {
    val isUpper = role == MatchResultScreenshotRole.MATCH_RESULT_UPPER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = { onSelectScreenshot(role) },
            enabled = !slot.isBusy,
            contentPadding = PaddingValues(
                horizontal = RankForgeSpacing.Small,
                vertical = RankForgeSpacing.ExtraSmall,
            ),
            modifier = Modifier
                .testTag(
                    if (slot.hasLinkedAsset) {
                        if (isUpper) MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG
                        else MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG
                    } else {
                        if (isUpper) MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG
                        else MATCH_REVIEW_RESULT_SCREENSHOT_2_SELECT_TEST_TAG
                    },
                ),
        ) { Text(stringResource(R.string.match_review_result_screenshot_replace_short_action)) }
        TextButton(
            onClick = { onOpenCrop(role) },
            enabled = slot.hasLinkedAsset && !slot.isLocalFileMissing && !slot.isBusy,
            contentPadding = PaddingValues(
                horizontal = RankForgeSpacing.Small,
                vertical = RankForgeSpacing.ExtraSmall,
            ),
            modifier = Modifier
                .testTag(if (isUpper) MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG else MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG),
        ) { Text(stringResource(R.string.match_review_result_screenshot_crop_short_action)) }
        TextButton(
            onClick = { onRemoveScreenshot(role) },
            enabled = slot.hasLinkedAsset && !slot.isBusy,
            contentPadding = PaddingValues(
                horizontal = RankForgeSpacing.Small,
                vertical = RankForgeSpacing.ExtraSmall,
            ),
            modifier = Modifier
                .testTag(if (isUpper) MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG else MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG),
        ) { Text(stringResource(R.string.match_review_result_screenshot_remove_short_action)) }
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

private fun ResultDownloadFailure.toMessageRes(): Int = when (this) {
    ResultDownloadFailure.INVALID_CONTEXT -> R.string.match_review_download_invalid_context
    ResultDownloadFailure.INVALID_MATCH -> R.string.match_review_download_invalid_match
    ResultDownloadFailure.GENERATION_FAILED -> R.string.match_review_download_generation_failed
    ResultDownloadFailure.SAVE_FAILED -> R.string.match_review_download_save_failed
    ResultDownloadFailure.DESTINATION_WRITE_FAILED ->
        R.string.match_review_download_destination_failed
    ResultDownloadFailure.DESTINATION_LAUNCH_FAILED ->
        R.string.match_review_download_destination_launch_failed
}
