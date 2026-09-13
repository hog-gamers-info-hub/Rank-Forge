package com.hoggamers.rankforge.presentation.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

private val detailsDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

private val PointIqDetailsNavy = Color(0xFF071B3E)
private val PointIqDetailsBody = Color(0xFF607393)
private val PointIqDetailsBlue = Color(0xFF176AF7)
private val PointIqDetailsBorder = Color(0xFFD6E3F4)
private val PointIqDetailsCard = Color(0xFFFFFFFF)
private val PointIqDetailsDanger = Color(0xFFD92D3A)
private val PointIqDetailsDangerContainer = Color(0xFFFFF5F5)
private val PointIqDetailsBackground = Color(0xFF031225)
private val PointIqDetailsAmbientBlue = Color(0xFF0B386F)
private val PointIqDetailsHeader = Color(0xFFF6F8FF)
private val PointIqDetailsSubtitle = Color(0xFF91AFE0)
private val PointIqDetailsInactiveBlue = Color(0xFF7D9DCE)
private val PointIqDetailsCyan = Color(0xFF17C9F2)
private val PointIqDetailsDarkSurface = PointIqDetailsAmbientBlue.copy(alpha = 0.42f)
private val PointIqDetailsFinalizedSurface = Color(0xFF0B3A32)
private val PointIqDetailsFinalized = Color(0xFF70F0AE)

const val TOURNAMENT_DETAILS_SCREEN_TEST_TAG = "tournament_details_screen"
const val TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG = "tournament_details_not_found"
const val TOURNAMENT_SLOT_LIST_TEST_TAG = "tournament_slot_list"
const val TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX = "tournament_slot_item_"
const val TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG = "tournament_cloud_upload_action"
const val TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG = "tournament_cloud_upload_status"
const val DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG = "draft_match_cloud_sync_action"
const val DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG = "draft_match_cloud_sync_status"
const val DRAFT_MATCH_CONFLICT_RESOLUTION_ACTION_TEST_TAG = "draft_match_conflict_resolution_action"
const val FINALIZED_MATCH_CLOUD_SYNC_ACTION_TEST_TAG = "finalized_match_cloud_sync_action"
const val FINALIZED_MATCH_CLOUD_SYNC_STATUS_TEST_TAG = "finalized_match_cloud_sync_status"
const val MATCH_CLOUD_RESTORE_ACTION_TEST_TAG = "match_cloud_restore_action"
const val MATCH_CLOUD_RESTORE_STATUS_TEST_TAG = "match_cloud_restore_status"
const val TOURNAMENT_STANDINGS_CSV_EXPORT_ACTION_TEST_TAG = "tournament_standings_csv_export_action"
const val TOURNAMENT_STANDINGS_CSV_EXPORT_STATUS_TEST_TAG = "tournament_standings_csv_export_status"
const val TOURNAMENT_DELETE_ACTION_TEST_TAG = "tournament_delete_action"
const val TOURNAMENT_DELETE_DIALOG_TEST_TAG = "tournament_delete_dialog"
const val TOURNAMENT_DELETE_CONFIRM_ACTION_TEST_TAG = "tournament_delete_confirm_action"
const val TOURNAMENT_DELETE_CANCEL_ACTION_TEST_TAG = "tournament_delete_cancel_action"
const val TOURNAMENT_DELETE_PROGRESS_TEST_TAG = "tournament_delete_progress"
const val TOURNAMENT_DELETE_ERROR_TEST_TAG = "tournament_delete_error"
const val TOURNAMENT_OVERFLOW_ACTION_TEST_TAG = "tournament_details_overflow_action"
const val EDIT_TEAMS_ACTION_TEST_TAG = "edit_teams_action"
const val MATCH_PROCESSING_SECTION_TEST_TAG = "match_processing_section"
const val MATCH_STATUS_TEST_TAG_PREFIX = "match_status_"
private const val SHOW_LEGACY_TOURNAMENT_DETAILS_CONTROLS = false

@Composable
fun TournamentDetailsRoute(
    tournamentId: String,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    onCreateMatch: (String) -> Unit = {},
    onEnterMatchPlacements: (String, String) -> Unit = { _, _ -> },
    onEnterMatchKills: (String, String) -> Unit = { _, _ -> },
    onReviewMatch: (String, String) -> Unit = { _, _ -> },
    onOpenStandings: (String) -> Unit = {},
    onResolveDraftConflict: (com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext) -> Unit = {},
    viewModel: TournamentDetailsViewModel = hiltViewModel(),
    uploadViewModel: TournamentCloudUploadViewModel? = null,
    draftMatchSyncViewModel: DraftMatchCloudSyncViewModel? = null,
    finalizedMatchSyncViewModel: FinalizedMatchCloudSyncViewModel? = null,
    matchCloudRestorationViewModel: MatchCloudRestorationViewModel? = null,
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.matchReviewRequest) {
        uiState.matchReviewRequest?.let { request ->
            viewModel.onMatchReviewRequestHandled()
            onReviewMatch(request.tournamentId, request.matchId)
        }
    }
    LaunchedEffect(uiState.navigation) {
        if (uiState.navigation == TournamentDetailsNavigation.TOURNAMENT_LIST) {
            viewModel.onNavigationHandled()
            onBackToList()
        }
    }
    BackHandler(enabled = uiState.isDeleting) {}
    val uploadUiState = if (uploadViewModel == null) {
        TournamentCloudUploadUiState.Idle
    } else {
        val state by uploadViewModel.uiState.collectAsStateWithLifecycle()
        state
    }
    val draftMatchSyncUiState = if (draftMatchSyncViewModel == null) {
        DraftMatchCloudSyncUiState.Idle
    } else {
        val state by draftMatchSyncViewModel.uiState.collectAsStateWithLifecycle()
        state
    }
    val finalizedMatchSyncUiState = if (finalizedMatchSyncViewModel == null) {
        FinalizedMatchCloudSyncUiState.Idle
    } else {
        val state by finalizedMatchSyncViewModel.uiState.collectAsStateWithLifecycle()
        state
    }
    val matchCloudRestorationUiState = if (matchCloudRestorationViewModel == null) MatchCloudRestorationUiState.Idle else {
        val state by matchCloudRestorationViewModel.uiState.collectAsStateWithLifecycle(); state
    }

    TournamentDetailsScreen(
        uiState = uiState,
        onBackToList = onBackToList,
        onEnterTeams = onEnterTeams,
        onCreateMatch = onCreateMatch,
        onCalculatePointsRequested = { viewModel.onCalculatePointsRequested() },
        pendingTeamCountConfirmation = uiState.pendingTeamCountConfirmation,
        calculatePointsMessage = uiState.calculatePointsMessage,
        isCreatingMatch = uiState.isCreatingMatch,
        onCancelTeamCountConfirmation = viewModel::cancelTeamCountConfirmation,
        onUseEnteredTeams = viewModel::useEnteredTeams,
        onUseDefaults = viewModel::useDefaults,
        onEnterMatchPlacements = onEnterMatchPlacements,
        onEnterMatchKills = onEnterMatchKills,
        onReviewMatch = onReviewMatch,
        onOpenStandings = onOpenStandings,
        onPrepareStandingsCsvExport = { viewModel.prepareStandingsCsvExport() },
        onDeleteTournament = { viewModel.deleteTournament() },
        isDeleting = uiState.isDeleting,
        deletionError = uiState.deletionError,
        uploadUiState = uploadUiState,
        onUpload = { id -> uploadViewModel?.upload(id) },
        draftMatchSyncUiState = draftMatchSyncUiState,
        onSyncDraftMatches = { id -> draftMatchSyncViewModel?.sync(id) },
        onResolveDraftConflict = onResolveDraftConflict,
        finalizedMatchSyncUiState = finalizedMatchSyncUiState,
        onSyncFinalizedMatches = { id -> finalizedMatchSyncViewModel?.sync(id) },
        matchCloudRestorationUiState = matchCloudRestorationUiState,
        onRestoreMatches = { id -> matchCloudRestorationViewModel?.restore(id) },
    )
}

@Composable
fun TournamentDetailsScreen(
    uiState: TournamentDetailsUiState,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    onCreateMatch: (String) -> Unit = {},
    onCalculatePointsRequested: ((String) -> Unit)? = null,
    pendingTeamCountConfirmation: TeamCountConfirmationUiState? = null,
    calculatePointsMessage: CalculatePointsMessage? = null,
    isCreatingMatch: Boolean = false,
    onCancelTeamCountConfirmation: () -> Unit = {},
    onUseEnteredTeams: () -> Unit = {},
    onUseDefaults: () -> Unit = {},
    onEnterMatchPlacements: (String, String) -> Unit = { _, _ -> },
    onEnterMatchKills: (String, String) -> Unit = { _, _ -> },
    onReviewMatch: (String, String) -> Unit = { _, _ -> },
    onOpenStandings: (String) -> Unit = {},
    onPrepareStandingsCsvExport: (String) -> Unit = {},
    uploadUiState: TournamentCloudUploadUiState = TournamentCloudUploadUiState.Idle,
    onUpload: (String) -> Unit = {},
    draftMatchSyncUiState: DraftMatchCloudSyncUiState = DraftMatchCloudSyncUiState.Idle,
    onSyncDraftMatches: (String) -> Unit = {},
    onResolveDraftConflict: (com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext) -> Unit = {},
    finalizedMatchSyncUiState: FinalizedMatchCloudSyncUiState = FinalizedMatchCloudSyncUiState.Idle,
    onSyncFinalizedMatches: (String) -> Unit = {},
    matchCloudRestorationUiState: MatchCloudRestorationUiState = MatchCloudRestorationUiState.Idle,
    onRestoreMatches: (String) -> Unit = {},
    showLegacyControls: Boolean = SHOW_LEGACY_TOURNAMENT_DETAILS_CONTROLS,
    onDeleteTournament: (String) -> Unit = {},
    isDeleting: Boolean = false,
    deletionError: TournamentDeletionUiError? = null,
) {
    PointIqTournamentDetailsSystemBars()

    when {
        uiState.isLoading -> PointIqTournamentDetailsLoadingState()

        uiState.isNotFound -> TournamentDetailsNotFoundState(onBackToList)

        uiState.tournament != null -> TournamentDetailsContent(
            tournament = uiState.tournament,
            onBackToList = onBackToList,
            onEnterTeams = onEnterTeams,
            onCreateMatch = onCreateMatch,
            onCalculatePointsRequested = onCalculatePointsRequested ?: onCreateMatch,
            pendingTeamCountConfirmation = pendingTeamCountConfirmation,
            calculatePointsMessage = calculatePointsMessage,
            isCreatingMatch = isCreatingMatch,
            onCancelTeamCountConfirmation = onCancelTeamCountConfirmation,
            onUseEnteredTeams = onUseEnteredTeams,
            onUseDefaults = onUseDefaults,
            onEnterMatchPlacements = onEnterMatchPlacements,
            onEnterMatchKills = onEnterMatchKills,
            onReviewMatch = onReviewMatch,
            onOpenStandings = onOpenStandings,
            onPrepareStandingsCsvExport = onPrepareStandingsCsvExport,
            csvExportResult = uiState.csvExportResult,
            uploadUiState = uploadUiState,
            onUpload = onUpload,
            draftMatchSyncUiState = draftMatchSyncUiState,
            onSyncDraftMatches = onSyncDraftMatches,
            onResolveDraftConflict = onResolveDraftConflict,
            finalizedMatchSyncUiState = finalizedMatchSyncUiState,
            onSyncFinalizedMatches = onSyncFinalizedMatches,
            matchCloudRestorationUiState = matchCloudRestorationUiState,
            onRestoreMatches = onRestoreMatches,
            showLegacyControls = showLegacyControls,
            onDeleteTournament = onDeleteTournament,
            isDeleting = isDeleting,
            deletionError = deletionError,
        )
    }
}

@Composable
private fun PointIqTournamentDetailsSystemBars() {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    DisposableEffect(window, view) {
        if (window == null) {
            onDispose { }
        } else {
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = windowInsetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = windowInsetsController.isAppearanceLightNavigationBars

            window.statusBarColor = PointIqDetailsBackground.toArgb()
            window.navigationBarColor = PointIqDetailsBackground.toArgb()
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false

            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
                windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                windowInsetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
}

@Composable
private fun PointIqTournamentDetailsLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PointIqDetailsBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PointIqDetailsCyan)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.tournament_details_loading),
                color = PointIqDetailsSubtitle,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun PointIqTournamentHero(
    tournament: TournamentDetailsItemUiState,
    showOverflowMenu: Boolean,
    onOverflowMenuChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEditTeams: () -> Unit,
    onDeleteTournament: () -> Unit,
    isDeleting: Boolean,
) {
    val heroShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.5f)
            .clip(heroShape),
    ) {
        Image(
            painter = painterResource(R.drawable.pointiq_tournament_details_hero),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Transparent,
                            PointIqDetailsBackground,
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back_action),
                    tint = PointIqDetailsHeader,
                    modifier = Modifier.size(32.dp),
                )
            }
            Box {
                IconButton(
                    onClick = { onOverflowMenuChange(true) },
                    enabled = !showOverflowMenu && !isDeleting,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag(TOURNAMENT_OVERFLOW_ACTION_TEST_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.tournament_details_overflow_content_description),
                        tint = PointIqDetailsHeader,
                    )
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { onOverflowMenuChange(false) },
                    containerColor = PointIqDetailsBackground,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.tournament_details_edit_teams_action),
                                color = PointIqDetailsHeader,
                            )
                        },
                        onClick = {
                            onOverflowMenuChange(false)
                            onEditTeams()
                        },
                        modifier = Modifier.testTag(EDIT_TEAMS_ACTION_TEST_TAG),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.tournament_delete_action),
                                color = PointIqDetailsDanger,
                            )
                        },
                        onClick = {
                            onOverflowMenuChange(false)
                            onDeleteTournament()
                        },
                        enabled = !isDeleting,
                        modifier = Modifier.testTag(TOURNAMENT_DELETE_ACTION_TEST_TAG),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = tournament.name,
                color = PointIqDetailsHeader,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.tournament_details_game_mode_presentation),
                color = PointIqDetailsSubtitle,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = stringResource(R.string.tournament_date_label),
                    tint = PointIqDetailsSubtitle,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.tournament_details_hero_date,
                        tournament.date.format(detailsDateFormatter),
                    ),
                    color = PointIqDetailsSubtitle,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun PointIqMatchProcessingHeader(
    nextMatchNumber: Int,
    canCreateMatch: Boolean,
    isCreatingMatch: Boolean,
    onCreateMatch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_PROCESSING_SECTION_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_match_processing),
            contentDescription = null,
            tint = PointIqDetailsCyan,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tournament_details_match_processing_title),
                color = PointIqDetailsHeader,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.tournament_details_match_processing_subtitle),
                color = PointIqDetailsSubtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (canCreateMatch) {
            OutlinedButton(
                onClick = onCreateMatch,
                enabled = !isCreatingMatch,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PointIqDetailsDarkSurface,
                    contentColor = PointIqDetailsSubtitle,
                    disabledContainerColor = PointIqDetailsDarkSurface.copy(alpha = 0.55f),
                    disabledContentColor = PointIqDetailsSubtitle.copy(alpha = 0.55f),
                ),
                border = BorderStroke(1.dp, PointIqDetailsBlue.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(40.dp).testTag(CREATE_MATCH_ACTION_TEST_TAG),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = PointIqDetailsCyan,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.tournament_details_match_number_action, nextMatchNumber),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.match_limit_reached_message),
                color = PointIqDetailsInactiveBlue,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun PointIqMatchRow(
    match: MatchUiState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag(MATCH_ITEM_TEST_TAG_PREFIX + match.matchNumber),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tournament_details_match_number_label, match.matchNumber),
            color = PointIqDetailsHeader,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        PointIqMatchStatusChip(match)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_tournament_details_chevron_right),
            contentDescription = null,
            tint = PointIqDetailsInactiveBlue,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun PointIqMatchStatusChip(match: MatchUiState) {
    val finalized = match.status == MatchStatus.FINALIZED
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (finalized) PointIqDetailsFinalizedSurface else PointIqDetailsDarkSurface,
            )
            .testTag(MATCH_STATUS_TEST_TAG_PREFIX + match.matchNumber)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = if (finalized) {
                androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.CheckCircle)
            } else {
                painterResource(R.drawable.ic_tournament_details_description)
            },
            contentDescription = null,
            tint = if (finalized) PointIqDetailsFinalized else PointIqDetailsSubtitle,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = stringResource(
                if (finalized) {
                    R.string.tournament_details_match_status_finalized
                } else {
                    R.string.tournament_details_match_status_ready
                },
            ),
            color = if (finalized) PointIqDetailsFinalized else PointIqDetailsSubtitle,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PointIqStandingsAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PointIqDetailsDarkSurface)
            .border(BorderStroke(1.dp, PointIqDetailsCyan.copy(alpha = 0.70f)), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(OPEN_STANDINGS_ACTION_TEST_TAG)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_calculated_standings),
                contentDescription = null,
                tint = PointIqDetailsCyan,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tournament_details_view_calculated_standings_title),
                color = PointIqDetailsHeader,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tournament_details_view_calculated_standings_subtitle),
                color = PointIqDetailsSubtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_tournament_details_chevron_right),
            contentDescription = null,
            tint = PointIqDetailsCyan,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun TournamentDetailsContent(
    tournament: TournamentDetailsItemUiState,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    onCreateMatch: (String) -> Unit,
    onCalculatePointsRequested: (String) -> Unit,
    pendingTeamCountConfirmation: TeamCountConfirmationUiState?,
    calculatePointsMessage: CalculatePointsMessage?,
    isCreatingMatch: Boolean,
    onCancelTeamCountConfirmation: () -> Unit,
    onUseEnteredTeams: () -> Unit,
    onUseDefaults: () -> Unit,
    onEnterMatchPlacements: (String, String) -> Unit,
    onEnterMatchKills: (String, String) -> Unit,
    onReviewMatch: (String, String) -> Unit,
    onOpenStandings: (String) -> Unit,
    onPrepareStandingsCsvExport: (String) -> Unit,
    csvExportResult: AndroidExportResult?,
    uploadUiState: TournamentCloudUploadUiState,
    onUpload: (String) -> Unit,
    draftMatchSyncUiState: DraftMatchCloudSyncUiState,
    onSyncDraftMatches: (String) -> Unit,
    onResolveDraftConflict: (com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext) -> Unit,
    finalizedMatchSyncUiState: FinalizedMatchCloudSyncUiState,
    onSyncFinalizedMatches: (String) -> Unit,
    matchCloudRestorationUiState: MatchCloudRestorationUiState,
    onRestoreMatches: (String) -> Unit,
    showLegacyControls: Boolean,
    onDeleteTournament: (String) -> Unit,
    isDeleting: Boolean,
    deletionError: TournamentDeletionUiError?,
) {
    var showDeleteConfirmation by remember(tournament.id) { mutableStateOf(false) }
    var showOverflowMenu by remember(tournament.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PointIqDetailsBackground)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PointIqDetailsAmbientBlue.copy(alpha = 0.42f),
                            PointIqDetailsAmbientBlue.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.width * 1.15f,
                    ),
                )
            }
            .testTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        PointIqTournamentHero(
            tournament = tournament,
            showOverflowMenu = showOverflowMenu,
            onOverflowMenuChange = { showOverflowMenu = it },
            onBack = onBackToList,
            onEditTeams = { onEnterTeams(tournament.id) },
            onDeleteTournament = { showDeleteConfirmation = true },
            isDeleting = isDeleting,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            PointIqMatchProcessingHeader(
                nextMatchNumber = tournament.nextMatchNumber,
                canCreateMatch = tournament.canCreateMatch(),
                isCreatingMatch = isCreatingMatch || isDeleting,
                onCreateMatch = { onCalculatePointsRequested(tournament.id) },
            )

            if (calculatePointsMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        when (calculatePointsMessage) {
                            CalculatePointsMessage.NO_TEAMS_SAVED ->
                                R.string.enter_and_save_teams_before_calculating_message
                            CalculatePointsMessage.INVALID_TEAM_SLOTS ->
                                R.string.team_entry_gap_message
                            CalculatePointsMessage.VALIDATION_FAILED ->
                                R.string.calculate_points_validation_error
                            CalculatePointsMessage.MATCH_CREATION_FAILED ->
                                R.string.match_creation_error
                        },
                    ),
                    color = Color(0xFFFF9A9A),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            pendingTeamCountConfirmation?.let { confirmation ->
                TeamCountConfirmationDialog(
                    confirmation = confirmation,
                    onCancel = onCancelTeamCountConfirmation,
                    onUseEnteredTeams = onUseEnteredTeams,
                    onUseDefaults = onUseDefaults,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            if (tournament.matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.tournament_details_no_matches_message),
                    color = PointIqDetailsSubtitle,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TOURNAMENT_MATCH_LIST_TEST_TAG),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TOURNAMENT_MATCH_LIST_TEST_TAG),
                ) {
                    tournament.matches.asReversed().forEachIndexed { index, match ->
                        PointIqMatchRow(
                            match = match,
                            onClick = {
                                if (!isDeleting) {
                                    onReviewMatch(tournament.id, match.id)
                                }
                            },
                        )
                        if (index < tournament.matches.lastIndex) {
                            HorizontalDivider(color = PointIqDetailsInactiveBlue.copy(alpha = 0.28f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PointIqStandingsAction(
                enabled = !isDeleting,
                onClick = { onOpenStandings(tournament.id) },
            )

            if (deletionError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(deletionError.toMessageRes()),
                    color = Color(0xFFFF9A9A),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.testTag(TOURNAMENT_DELETE_ERROR_TEST_TAG),
                )
            }
            if (isDeleting) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TOURNAMENT_DELETE_PROGRESS_TEST_TAG),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PointIqDetailsCyan,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.tournament_delete_in_progress),
                        color = PointIqDetailsSubtitle,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (showLegacyControls) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            TournamentCloudUploadSection(
                tournamentId = tournament.id,
                uiState = uploadUiState,
                onUpload = onUpload,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            DraftMatchCloudSyncSection(
                tournamentId = tournament.id,
                uiState = draftMatchSyncUiState,
                onSync = onSyncDraftMatches,
                onResolveConflict = onResolveDraftConflict,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            FinalizedMatchCloudSyncSection(
                tournamentId = tournament.id,
                uiState = finalizedMatchSyncUiState,
                onSync = onSyncFinalizedMatches,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            LegacyTeamSlotList(slots = tournament.slots)
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            MatchList(
                tournament = tournament,
                onCreateMatch = { if (!isDeleting) onCreateMatch(it) },
                onEnterMatchPlacements = { tournamentId, matchId ->
                    if (!isDeleting) onEnterMatchPlacements(tournamentId, matchId)
                },
                onEnterMatchKills = { tournamentId, matchId ->
                    if (!isDeleting) onEnterMatchKills(tournamentId, matchId)
                },
                onReviewMatch = { tournamentId, matchId ->
                    if (!isDeleting) onReviewMatch(tournamentId, matchId)
                },
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            MatchCloudRestorationSection(tournament.id, matchCloudRestorationUiState, onRestoreMatches)
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            }
        }

        if (showDeleteConfirmation && !isDeleting) {
            AlertDialog(
                modifier = Modifier.testTag(TOURNAMENT_DELETE_DIALOG_TEST_TAG),
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text(stringResource(R.string.tournament_delete_title)) },
                text = {
                    Text(stringResource(R.string.tournament_delete_message, tournament.name))
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmation = false },
                        modifier = Modifier.testTag(TOURNAMENT_DELETE_CANCEL_ACTION_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            onDeleteTournament(tournament.id)
                        },
                        modifier = Modifier.testTag(TOURNAMENT_DELETE_CONFIRM_ACTION_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.tournament_delete_confirm_action))
                    }
                },
            )
        }
        if (showLegacyControls && tournament.canPrepareStandingsCsvExport) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Button(
                onClick = { onPrepareStandingsCsvExport(tournament.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TOURNAMENT_STANDINGS_CSV_EXPORT_ACTION_TEST_TAG),
            ) {
                Text(text = "Prepare CSV export")
            }
        }
        if (showLegacyControls) when (csvExportResult) {
            is AndroidExportResult.CsvReady -> Text(
                text = "CSV export ready",
                modifier = Modifier.testTag(TOURNAMENT_STANDINGS_CSV_EXPORT_STATUS_TEST_TAG),
            )
            is AndroidExportResult.Blocked -> Text(
                text = "CSV export blocked",
                modifier = Modifier.testTag(TOURNAMENT_STANDINGS_CSV_EXPORT_STATUS_TEST_TAG),
            )
            else -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun TournamentDeletionUiError.toMessageRes(): Int = when (this) {
    TournamentDeletionUiError.TARGET_NOT_FOUND -> R.string.tournament_delete_target_not_found_error
    TournamentDeletionUiError.AUTHENTICATION_REQUIRED -> R.string.tournament_delete_authentication_error
    TournamentDeletionUiError.AUTHORIZATION_FAILURE -> R.string.tournament_delete_authorization_error
    TournamentDeletionUiError.VALIDATION_FAILURE -> R.string.tournament_delete_validation_error
    TournamentDeletionUiError.STORAGE_FAILURE -> R.string.tournament_delete_storage_error
    TournamentDeletionUiError.REMOTE_FAILURE -> R.string.tournament_delete_remote_error
    TournamentDeletionUiError.LOCAL_CLEANUP_FAILURE -> R.string.tournament_delete_local_cleanup_error
    TournamentDeletionUiError.PREPARATION_FAILURE,
    TournamentDeletionUiError.UNKNOWN,
    -> R.string.tournament_delete_generic_error
}

@Composable
private fun MatchCloudRestorationSection(tournamentId: String, uiState: MatchCloudRestorationUiState, onRestore: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
        Button(onClick = { onRestore(tournamentId) }, enabled = uiState !is MatchCloudRestorationUiState.Loading, modifier = Modifier.fillMaxWidth().testTag(MATCH_CLOUD_RESTORE_ACTION_TEST_TAG)) {
            Text(text = stringResource(if (uiState is MatchCloudRestorationUiState.Loading) R.string.restore_matches_loading else R.string.restore_matches_action))
        }
        Text(text = when (uiState) {
            MatchCloudRestorationUiState.Idle -> stringResource(R.string.restore_matches_ready)
            MatchCloudRestorationUiState.Loading -> stringResource(R.string.restore_matches_loading)
            MatchCloudRestorationUiState.Success -> stringResource(R.string.restore_matches_success)
            MatchCloudRestorationUiState.NoCloudMatches -> stringResource(R.string.restore_matches_none)
            MatchCloudRestorationUiState.AuthenticationRequired -> stringResource(R.string.restore_matches_authentication_required)
            MatchCloudRestorationUiState.AuthorizationFailure -> stringResource(R.string.restore_matches_authorization_failure)
            MatchCloudRestorationUiState.ValidationFailure -> stringResource(R.string.restore_matches_validation_failure)
            MatchCloudRestorationUiState.NetworkFailure -> stringResource(R.string.restore_matches_network_failure)
            MatchCloudRestorationUiState.LocalTransactionFailure -> stringResource(R.string.restore_matches_local_failure)
            MatchCloudRestorationUiState.Queued -> stringResource(R.string.restore_matches_queued)
            MatchCloudRestorationUiState.QueuePersistenceFailure -> stringResource(R.string.restore_matches_queue_persistence_failed)
        }, modifier = Modifier.testTag(MATCH_CLOUD_RESTORE_STATUS_TEST_TAG))
    }
}

@Composable
private fun TournamentCloudUploadSection(
    tournamentId: String,
    uiState: TournamentCloudUploadUiState,
    onUpload: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Button(
            onClick = { onUpload(tournamentId) },
            enabled = uiState !is TournamentCloudUploadUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState is TournamentCloudUploadUiState.Loading) {
                        R.string.upload_tournament_loading
                    } else {
                        R.string.upload_tournament_action
                    },
                ),
            )
        }
        Text(
            text = when (uiState) {
                TournamentCloudUploadUiState.Idle -> stringResource(R.string.upload_tournament_ready_message)
                TournamentCloudUploadUiState.Loading -> stringResource(R.string.upload_tournament_loading)
                TournamentCloudUploadUiState.Success -> stringResource(R.string.upload_tournament_success)
                TournamentCloudUploadUiState.AuthenticationRequired ->
                    stringResource(R.string.upload_tournament_authentication_required)
                TournamentCloudUploadUiState.AuthorizationFailure ->
                    stringResource(R.string.upload_tournament_authorization_failure)
                TournamentCloudUploadUiState.ValidationFailure ->
                    stringResource(R.string.upload_tournament_validation_failure)
                TournamentCloudUploadUiState.NetworkFailure ->
                    stringResource(R.string.upload_tournament_network_failure)
                TournamentCloudUploadUiState.Queued ->
                    stringResource(R.string.upload_tournament_queued)
                TournamentCloudUploadUiState.QueuePersistenceFailure ->
                    stringResource(R.string.upload_tournament_queue_persistence_failed)
                is TournamentCloudUploadUiState.PartialFailure ->
                    stringResource(R.string.upload_tournament_partial_failure)
            },
            modifier = Modifier.testTag(TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG),
        )
    }
}

@Composable
private fun DraftMatchCloudSyncSection(
    tournamentId: String,
    uiState: DraftMatchCloudSyncUiState,
    onSync: (String) -> Unit,
    onResolveConflict: (com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Button(
            onClick = { onSync(tournamentId) },
            enabled = uiState !is DraftMatchCloudSyncUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState is DraftMatchCloudSyncUiState.Loading) {
                        R.string.sync_draft_matches_loading
                    } else {
                        R.string.sync_draft_matches_action
                    },
                ),
            )
        }
        Text(
            text = when (uiState) {
                DraftMatchCloudSyncUiState.Idle -> stringResource(R.string.sync_draft_matches_ready_message)
                DraftMatchCloudSyncUiState.Loading -> stringResource(R.string.sync_draft_matches_loading)
                DraftMatchCloudSyncUiState.Success -> stringResource(R.string.sync_draft_matches_success)
                DraftMatchCloudSyncUiState.AuthenticationRequired ->
                    stringResource(R.string.sync_draft_matches_authentication_required)
                DraftMatchCloudSyncUiState.AuthorizationFailure ->
                    stringResource(R.string.sync_draft_matches_authorization_failure)
                DraftMatchCloudSyncUiState.ValidationFailure ->
                    stringResource(R.string.sync_draft_matches_validation_failure)
                DraftMatchCloudSyncUiState.NetworkFailure ->
                    stringResource(R.string.sync_draft_matches_network_failure)
                DraftMatchCloudSyncUiState.Queued ->
                    stringResource(R.string.sync_draft_matches_queued)
                DraftMatchCloudSyncUiState.QueuePersistenceFailure ->
                    stringResource(R.string.sync_draft_matches_queue_persistence_failed)
                is DraftMatchCloudSyncUiState.Conflict -> stringResource(R.string.draft_conflict_detected)
                is DraftMatchCloudSyncUiState.PartialFailure ->
                    stringResource(R.string.sync_draft_matches_partial_failure)
            },
            modifier = Modifier.testTag(DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG),
        )
        val conflict = (uiState as? DraftMatchCloudSyncUiState.Conflict)?.context
        if (conflict != null) {
            TextButton(
                onClick = { onResolveConflict(conflict) },
                modifier = Modifier.testTag(DRAFT_MATCH_CONFLICT_RESOLUTION_ACTION_TEST_TAG),
            ) { Text(stringResource(R.string.resolve_draft_conflict_action)) }
        }
    }
}

@Composable
private fun FinalizedMatchCloudSyncSection(
    tournamentId: String,
    uiState: FinalizedMatchCloudSyncUiState,
    onSync: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Button(
            onClick = { onSync(tournamentId) },
            enabled = uiState !is FinalizedMatchCloudSyncUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FINALIZED_MATCH_CLOUD_SYNC_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState is FinalizedMatchCloudSyncUiState.Loading) {
                        R.string.sync_finalized_matches_loading
                    } else {
                        R.string.sync_finalized_matches_action
                    },
                ),
            )
        }
        Text(
            text = when (uiState) {
                FinalizedMatchCloudSyncUiState.Idle -> stringResource(R.string.sync_finalized_matches_ready_message)
                FinalizedMatchCloudSyncUiState.Loading -> stringResource(R.string.sync_finalized_matches_loading)
                FinalizedMatchCloudSyncUiState.Success -> stringResource(R.string.sync_finalized_matches_success)
                FinalizedMatchCloudSyncUiState.AuthenticationRequired ->
                    stringResource(R.string.sync_finalized_matches_authentication_required)
                FinalizedMatchCloudSyncUiState.AuthorizationFailure ->
                    stringResource(R.string.sync_finalized_matches_authorization_failure)
                FinalizedMatchCloudSyncUiState.ValidationFailure ->
                    stringResource(R.string.sync_finalized_matches_validation_failure)
                FinalizedMatchCloudSyncUiState.NetworkFailure ->
                    stringResource(R.string.sync_finalized_matches_network_failure)
                FinalizedMatchCloudSyncUiState.Queued ->
                    stringResource(R.string.sync_finalized_matches_queued)
                FinalizedMatchCloudSyncUiState.QueuePersistenceFailure ->
                    stringResource(R.string.sync_finalized_matches_queue_persistence_failed)
                is FinalizedMatchCloudSyncUiState.PartialFailure ->
                    stringResource(R.string.sync_finalized_matches_partial_failure)
            },
            modifier = Modifier.testTag(FINALIZED_MATCH_CLOUD_SYNC_STATUS_TEST_TAG),
        )
    }
}

@Composable
private fun MatchList(
    tournament: TournamentDetailsItemUiState,
    onCreateMatch: (String) -> Unit,
    onEnterMatchPlacements: (String, String) -> Unit,
    onEnterMatchKills: (String, String) -> Unit,
    onReviewMatch: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TOURNAMENT_MATCH_LIST_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.matches_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (tournament.status != TournamentStatus.CONFIRMED) {
            Text(text = stringResource(R.string.matches_require_confirmed_roster_message))
        } else if (tournament.matches.size >= com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT) {
            Text(text = stringResource(R.string.match_limit_reached_message))
        } else {
            Button(
                onClick = { onCreateMatch(tournament.id) },
                modifier = Modifier.fillMaxWidth().testTag(CREATE_MATCH_ACTION_TEST_TAG),
            ) {
                Text(
                    text = stringResource(
                        R.string.create_match_number_action,
                        tournament.nextMatchNumber,
                    ),
                )
            }
        }
        if (tournament.matches.isEmpty()) {
            Text(text = stringResource(R.string.no_matches_message))
        } else {
            tournament.matches.forEach { match ->
                Column(
                    modifier = Modifier.testTag(MATCH_ITEM_TEST_TAG_PREFIX + match.matchNumber),
                ) {
                    Text(text = stringResource(R.string.match_number_value, match.matchNumber))
                    Text(text = stringResource(R.string.match_date_value, match.date.format(detailsDateFormatter)))
                    Text(text = stringResource(R.string.match_map_value, match.mapName))
                    Text(
                        text = stringResource(
                            R.string.match_status_value,
                            if (match.status == MatchStatus.DRAFT) {
                                stringResource(R.string.match_status_draft)
                            } else {
                                stringResource(R.string.match_status_finalized)
                            },
                        ),
                    )
                    if (match.placements.isEmpty()) {
                        Text(text = stringResource(R.string.no_match_placements_message))
                    } else {
                        match.placements.forEach { placement ->
                            Text(
                                text = stringResource(
                                    R.string.match_placement_value,
                                    placement.teamSlotNumber,
                                    placement.position,
                                ),
                            )
                        }
                    }
                    if (match.kills.isEmpty()) {
                        Text(text = stringResource(R.string.no_match_kills_message))
                    } else {
                        match.kills.forEach { kill ->
                            Text(
                                text = stringResource(
                                    R.string.match_kill_value,
                                    kill.teamSlotNumber,
                                    kill.kills,
                                ),
                            )
                        }
                    }
                    if (match.validationIssues.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.match_validation_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag(
                                MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX + match.matchNumber,
                            ),
                        )
                        match.validationIssues.forEach { issue ->
                            Text(
                                text = stringResource(
                                    R.string.match_validation_issue,
                                    issue.teamSlotNumber,
                                    stringResource(issue.error.toMessageRes()),
                                ),
                                modifier = Modifier.testTag(
                                    MATCH_VALIDATION_ISSUE_TEST_TAG_PREFIX +
                                        issue.teamSlotNumber + "_" + issue.error.name,
                                ),
                            )
                        }
                    }
                    if (match.status == MatchStatus.DRAFT) {
                        TextButton(
                            onClick = { onEnterMatchPlacements(tournament.id, match.id) },
                            modifier = Modifier.testTag(MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX + match.matchNumber),
                        ) {
                            Text(text = stringResource(R.string.enter_match_placements_action))
                        }
                        TextButton(
                            onClick = { onEnterMatchKills(tournament.id, match.id) },
                            modifier = Modifier.testTag(MATCH_KILLS_ACTION_TEST_TAG_PREFIX + match.matchNumber),
                        ) {
                            Text(text = stringResource(R.string.enter_match_kills_action))
                        }
                    }
                    TextButton(
                        onClick = { onReviewMatch(tournament.id, match.id) },
                        modifier = Modifier.testTag(MATCH_REVIEW_ACTION_TEST_TAG_PREFIX + match.matchNumber),
                    ) {
                        Text(text = stringResource(R.string.review_match_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSlotList(
    slots: List<TeamSlotUiState>,
    onEnterTeams: () -> Unit,
) {
    val activeSlots = slots.takeWhile { it.teamName.trim().isNotBlank() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_SLOT_LIST_TEST_TAG),
        shape = RoundedCornerShape(18.dp),
        color = PointIqDetailsCard,
        border = BorderStroke(1.dp, PointIqDetailsBorder),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tournament_details_slot_list_title),
                color = PointIqDetailsNavy,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (activeSlots.isEmpty()) {
                Text(
                    text = stringResource(R.string.tournament_details_no_teams_saved),
                    color = PointIqDetailsBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            } else {
                activeSlots.forEachIndexed { index, slot ->
                    Text(
                        text = stringResource(
                            R.string.tournament_details_slot_row,
                            slot.slotNumber,
                            slot.teamName,
                        ),
                        color = PointIqDetailsNavy,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp)
                            .testTag(TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX + slot.slotNumber),
                    )
                    if (index < activeSlots.lastIndex) {
                        HorizontalDivider(color = PointIqDetailsBorder.copy(alpha = 0.75f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onEnterTeams,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PointIqDetailsBlue,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.enter_teams_action),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LegacyTeamSlotList(slots: List<TeamSlotUiState>) {
    Column(
        modifier = Modifier.testTag(TOURNAMENT_SLOT_LIST_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.team_slots_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        slots.forEach { slot ->
            Column(
                modifier = Modifier.testTag(TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX + slot.slotNumber),
            ) {
                Text(
                    text = stringResource(R.string.team_slot_label, slot.slotNumber),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = slot.teamName.ifBlank {
                        stringResource(R.string.empty_team_slot_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SimplifiedMatchList(
    tournament: TournamentDetailsItemUiState,
    onCalculatePointsRequested: (String) -> Unit,
    calculatePointsMessage: CalculatePointsMessage?,
    isCreatingMatch: Boolean,
    onOpenMatchReview: (String, String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_MATCH_LIST_TEST_TAG),
        shape = RoundedCornerShape(18.dp),
        color = PointIqDetailsCard,
        border = BorderStroke(1.dp, PointIqDetailsBorder),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tournament_details_matches_title),
                color = PointIqDetailsNavy,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (tournament.matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.tournament_details_no_matches_message),
                    color = PointIqDetailsBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            } else {
                tournament.matches.forEachIndexed { index, match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMatchReview(tournament.id, match.id) }
                            .padding(vertical = 9.dp)
                            .testTag(MATCH_ITEM_TEST_TAG_PREFIX + match.matchNumber),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.tournament_details_match_row,
                                match.matchNumber,
                                stringResource(
                                    if (match.status == MatchStatus.DRAFT) {
                                        R.string.tournament_details_match_in_progress
                                    } else {
                                        R.string.tournament_details_match_completed
                                    },
                                ),
                            ),
                            color = PointIqDetailsNavy,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_tournament_details_chevron_right),
                            contentDescription = null,
                            tint = PointIqDetailsBlue,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag(MATCH_ITEM_CHEVRON_TEST_TAG_PREFIX + match.matchNumber),
                        )
                    }
                    if (index < tournament.matches.lastIndex) {
                        HorizontalDivider(color = PointIqDetailsBorder.copy(alpha = 0.75f))
                    }
                }
            }

            if (calculatePointsMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        when (calculatePointsMessage) {
                            CalculatePointsMessage.NO_TEAMS_SAVED ->
                                R.string.enter_and_save_teams_before_calculating_message
                            CalculatePointsMessage.INVALID_TEAM_SLOTS ->
                                R.string.team_entry_gap_message
                            CalculatePointsMessage.VALIDATION_FAILED ->
                                R.string.calculate_points_validation_error
                            CalculatePointsMessage.MATCH_CREATION_FAILED ->
                                R.string.match_creation_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (tournament.matches.size < com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT) {
                Button(
                    onClick = { onCalculatePointsRequested(tournament.id) },
                    enabled = !isCreatingMatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PointIqDetailsBlue,
                        disabledContainerColor = PointIqDetailsBlue.copy(alpha = 0.45f),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(CREATE_MATCH_ACTION_TEST_TAG),
                ) {
                    Text(
                        text = stringResource(
                            R.string.create_match_number_action,
                            tournament.nextMatchNumber,
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.match_limit_reached_message),
                    color = PointIqDetailsBody,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

const val CALCULATE_POINTS_CONFIRMATION_DIALOG_TEST_TAG = "calculate_points_confirmation_dialog"
const val CALCULATE_POINTS_USE_TEAMS_TEST_TAG = "calculate_points_use_teams"
const val CALCULATE_POINTS_USE_DEFAULTS_TEST_TAG = "calculate_points_use_defaults"
const val CALCULATE_POINTS_CANCEL_TEST_TAG = "calculate_points_cancel"

@Composable
internal fun TeamCountConfirmationDialog(
    confirmation: TeamCountConfirmationUiState,
    onCancel: () -> Unit,
    onUseEnteredTeams: () -> Unit,
    onUseDefaults: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(CALCULATE_POINTS_CONFIRMATION_DIALOG_TEST_TAG),
        onDismissRequest = onCancel,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.team_count_confirmation_entered,
                        confirmation.enteredCount,
                        confirmation.enteredCount,
                    ),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.team_count_confirmation_empty,
                        confirmation.emptyCount,
                        confirmation.emptyCount,
                    ),
                )
                Text(text = stringResource(R.string.team_count_confirmation_prompt))
                Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
                if (confirmation.enteredCount > 0) {
                    FilledTonalButton(
                        onClick = onUseEnteredTeams,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.team_count_confirmation_use_teams,
                                confirmation.enteredCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onUseDefaults,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.team_count_confirmation_use_defaults),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CALCULATE_POINTS_CANCEL_TEST_TAG),
                ) {
                    Text(text = stringResource(R.string.team_count_confirmation_cancel))
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun TournamentDetailsNotFoundState(
    onBackToList: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PointIqDetailsBackground)
            .padding(24.dp)
            .testTag(TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.tournament_not_found_title),
            color = PointIqDetailsHeader,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(
            text = stringResource(R.string.tournament_not_found_message),
            color = PointIqDetailsSubtitle,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onBackToList,
            colors = ButtonDefaults.buttonColors(containerColor = PointIqDetailsBlue),
        ) {
            Text(text = stringResource(R.string.back_to_tournament_list_action))
        }
    }
}

const val TOURNAMENT_MATCH_LIST_TEST_TAG = "tournament_match_list"
const val CREATE_MATCH_ACTION_TEST_TAG = "create_match_action"
const val MATCH_ITEM_TEST_TAG_PREFIX = "match_item_"
const val MATCH_ITEM_CHEVRON_TEST_TAG_PREFIX = "match_item_chevron_"
const val MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX = "match_placement_action_"
const val MATCH_KILLS_ACTION_TEST_TAG_PREFIX = "match_kills_action_"
const val MATCH_REVIEW_ACTION_TEST_TAG_PREFIX = "match_review_action_"
const val MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX = "match_validation_issues_"
const val MATCH_VALIDATION_ISSUE_TEST_TAG_PREFIX = "match_validation_issue_"

private fun MatchResultValidationError.toMessageRes(): Int = when (this) {
    MatchResultValidationError.MISSING_TEAM_RESULT_ROW -> R.string.match_validation_missing_team_result_row
    MatchResultValidationError.DUPLICATE_TEAM -> R.string.match_validation_duplicate_team
    MatchResultValidationError.MISSING_PLACEMENT -> R.string.match_validation_missing_placement
    MatchResultValidationError.DUPLICATE_PLACEMENT -> R.string.match_validation_duplicate_placement
    MatchResultValidationError.INVALID_PLACEMENT -> R.string.match_validation_invalid_placement
    MatchResultValidationError.MISSING_KILLS -> R.string.match_validation_missing_kills
    MatchResultValidationError.INVALID_KILLS -> R.string.match_validation_invalid_kills
}
