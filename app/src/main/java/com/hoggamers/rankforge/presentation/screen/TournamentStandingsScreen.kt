package com.hoggamers.rankforge.presentation.screen

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import kotlinx.coroutines.flow.Flow

private val PointIqStandingsBackground = Color(0xFF031225)
private val PointIqStandingsAmbientBlue = Color(0xFF0B386F)
private val PointIqStandingsHeader = Color(0xFFF6F8FF)
private val PointIqStandingsSubtitle = Color(0xFF91AFE0)
private val PointIqStandingsBlue = Color(0xFF176AF7)
private val PointIqStandingsCyan = Color(0xFF17C9F2)
private val PointIqStandingsDivider = PointIqStandingsBlue.copy(alpha = 0.24f)

private data class StandingRankStyle(
    val accent: Color,
    val badgeBackground: Color,
)

const val TOURNAMENT_STANDINGS_SCREEN_TEST_TAG = "tournament_standings_screen"
const val TOURNAMENT_STANDINGS_EMPTY_TEST_TAG = "tournament_standings_empty"
const val TOURNAMENT_STANDINGS_LIST_TEST_TAG = "tournament_standings_list"
const val TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX = "tournament_standing_row_"
const val TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX = "tournament_standing_complete_tie_"
const val TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG = "tournament_standings_share_action"
const val OPEN_STANDINGS_ACTION_TEST_TAG = "open_standings_action"

@Composable
fun TournamentStandingsRoute(
    tournamentId: String,
    onBackToTournamentDetails: () -> Unit,
    viewModel: TournamentStandingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    TournamentStandingsShareEventEffect(
        shareEvents = viewModel.shareEvents,
        shareTextTitle = stringResource(R.string.tournament_standings_share_text_title),
        chooserTitle = stringResource(R.string.tournament_standings_share_chooser_title),
        failureMessage = stringResource(R.string.tournament_standings_share_failed_message),
        startActivity = { intent -> context.startActivity(intent) },
        showFailure = { message -> snackbarHostState.showSnackbar(message) },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        TournamentStandingsScreen(
            uiState = uiState,
            onBackToTournamentDetails = onBackToTournamentDetails,
            onShareStandings = viewModel::shareStandings,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun TournamentStandingsShareEventEffect(
    shareEvents: Flow<TournamentStandingsShareEvent>,
    shareTextTitle: String,
    chooserTitle: String,
    failureMessage: String,
    startActivity: (Intent) -> Unit,
    showFailure: suspend (String) -> Unit,
) {
    LaunchedEffect(shareEvents) {
        shareEvents.collect { event ->
            when (event) {
                is TournamentStandingsShareEvent.ShareUrl -> {
                    startActivity(
                        createTournamentStandingsShareChooserIntent(
                            publicUrl = event.publicUrl,
                            shareTextTitle = shareTextTitle,
                            chooserTitle = chooserTitle,
                        ),
                    )
                }

                TournamentStandingsShareEvent.ShareFailed -> showFailure(failureMessage)
            }
        }
    }
}

@Composable
fun TournamentStandingsScreen(
    uiState: TournamentStandingsUiState,
    onBackToTournamentDetails: () -> Unit,
    onShareStandings: () -> Unit,
) {
    PointIqTournamentStandingsSystemBars()

    when {
        uiState.isLoading -> PointIqTournamentStandingsLoadingState(
            message = stringResource(R.string.tournament_standings_loading),
        )
        uiState.rows.isEmpty() -> TournamentStandingsEmptyState(onBackToTournamentDetails)
        else -> TournamentStandingsContent(
            rows = uiState.rows,
            isPublishing = uiState.isPublishing,
            onBackToTournamentDetails = onBackToTournamentDetails,
            onShareStandings = onShareStandings,
        )
    }
}

@Composable
private fun TournamentStandingsContent(
    rows: List<TournamentStandingRowUiState>,
    isPublishing: Boolean,
    onBackToTournamentDetails: () -> Unit,
    onShareStandings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointIqStandingsBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        TournamentStandingsHeaderEntrance(
            isPublishing = isPublishing,
            onShareStandings = onShareStandings,
            onBackToTournamentDetails = onBackToTournamentDetails,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TOURNAMENT_STANDINGS_LIST_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rows.forEach { row ->
                TournamentStandingRow(row)
            }
        }
    }
}

@Composable
private fun TournamentStandingsHeaderEntrance(
    isPublishing: Boolean = false,
    onShareStandings: (() -> Unit)? = null,
    onBackToTournamentDetails: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(220)) +
            slideInVertically(
                animationSpec = tween(220),
                initialOffsetY = { -4 },
            ),
    ) {
        TournamentStandingsHeader(
            isPublishing = isPublishing,
            onShareStandings = onShareStandings,
            onBackToTournamentDetails = onBackToTournamentDetails,
        )
    }
}

@Composable
private fun TournamentStandingsHeader(
    isPublishing: Boolean = false,
    onShareStandings: (() -> Unit)? = null,
    onBackToTournamentDetails: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onBackToTournamentDetails,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.back_action),
                tint = PointIqStandingsHeader,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tournament_standings_title),
                color = PointIqStandingsHeader,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = stringResource(R.string.tournament_standings_finalized_matches_only),
                color = PointIqStandingsSubtitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        onShareStandings?.let { share ->
            TextButton(
                onClick = share,
                enabled = !isPublishing,
                modifier = Modifier.testTag(TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = PointIqStandingsCyan,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.share_action),
                    color = PointIqStandingsCyan,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal fun createTournamentStandingsShareChooserIntent(
    publicUrl: String,
    shareTextTitle: String,
    chooserTitle: String,
): Intent {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$shareTextTitle\n$publicUrl")
    }
    return Intent.createChooser(shareIntent, chooserTitle)
}

@Composable
private fun TournamentStandingRow(row: TournamentStandingRowUiState) {
    val rankStyle = standingRankStyle(row.displayOrder)
    val teamName = row.teamName
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(
            R.string.tournament_standing_team_slot_inline,
            row.teamSlotNumber,
        )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
        shape = RoundedCornerShape(18.dp),
        color = PointIqStandingsAmbientBlue.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, rankStyle.accent.copy(alpha = 0.62f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StandingRankBadge(row.displayOrder, rankStyle)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = teamName,
                            color = PointIqStandingsHeader,
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = PointIqStandingsDivider,
                        thickness = 1.dp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    StandingMetricRow {
                        StandingMetric(
                            label = stringResource(R.string.tournament_standing_kill_points_label),
                            value = row.totalKillPoints.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StandingMetricColumnDivider()
                        StandingMetric(
                            label = stringResource(R.string.tournament_standing_position_points_label),
                            value = row.totalPositionPoints.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StandingMetricColumnDivider()
                        StandingMetric(
                            label = stringResource(R.string.tournament_standing_total_points_label),
                            value = row.totalPoints.toString(),
                            valueColor = rankStyle.accent,
                            valueFontSize = 20.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(
                        color = PointIqStandingsDivider,
                        thickness = 1.dp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    StandingMetricRow {
                        StandingMetric(
                            label = stringResource(
                                R.string.tournament_standing_first_place_finishes_label,
                            ),
                            value = row.firstPlaceFinishes.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StandingMetricColumnDivider()
                        StandingMetric(
                            label = stringResource(R.string.tournament_standing_latest_placement_label),
                            value = row.latestMatchPlacement?.let { placement -> "#$placement" }
                                ?: stringResource(
                                    R.string.tournament_standing_latest_placement_none_value,
                                ),
                            modifier = Modifier.weight(1f),
                        )
                        StandingMetricColumnDivider()
                        StandingMetric(
                            label = stringResource(R.string.tournament_standing_matches_included_label),
                            value = row.matchesIncluded.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.isCompleteTie) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tournament_standing_complete_tie_message),
                            color = PointIqStandingsSubtitle,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.testTag(
                                TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + row.teamSlotNumber,
                            ),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .border(
                        width = 1.dp,
                        color = rankStyle.accent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(17.dp),
                    ),
            )
        }
    }
}

@Composable
private fun StandingMetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun RowScope.StandingMetricColumnDivider() {
    Box(
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight()
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(PointIqStandingsDivider),
        )
    }
}

@Composable
private fun StandingRankBadge(
    displayOrder: Int,
    rankStyle: StandingRankStyle,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(rankStyle.badgeBackground, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = rankStyle.accent.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayOrder.toString(),
            color = rankStyle.accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StandingMetric(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = PointIqStandingsHeader,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = PointIqStandingsSubtitle,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = valueFontSize,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun standingRankStyle(displayOrder: Int): StandingRankStyle = when (displayOrder) {
    1 -> StandingRankStyle(
        accent = Color(0xFFF6C817),
        badgeBackground = Color(0xFFF6C817).copy(alpha = 0.18f),
    )
    2 -> StandingRankStyle(
        accent = Color(0xFFB9D8FF),
        badgeBackground = Color(0xFFB9D8FF).copy(alpha = 0.18f),
    )
    3 -> StandingRankStyle(
        accent = Color(0xFFFF9B42),
        badgeBackground = Color(0xFFFF9B42).copy(alpha = 0.18f),
    )
    else -> StandingRankStyle(
        accent = PointIqStandingsCyan,
        badgeBackground = PointIqStandingsCyan.copy(alpha = 0.16f),
    )
}

@Composable
private fun TournamentStandingsEmptyState(onBackToTournamentDetails: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointIqStandingsBackground()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag(TOURNAMENT_STANDINGS_EMPTY_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        TournamentStandingsHeaderEntrance(
            onBackToTournamentDetails = onBackToTournamentDetails,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.tournament_standings_empty_title),
            color = PointIqStandingsHeader,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tournament_standings_empty_message),
            color = PointIqStandingsSubtitle,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBackToTournamentDetails) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}

@Composable
private fun PointIqTournamentStandingsSystemBars() {
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

            window.statusBarColor = PointIqStandingsBackground.toArgb()
            window.navigationBarColor = PointIqStandingsBackground.toArgb()
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
private fun PointIqTournamentStandingsLoadingState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointIqStandingsBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(RankForgeSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = PointIqStandingsCyan)
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Text(
                text = message,
                color = PointIqStandingsSubtitle,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun Modifier.pointIqStandingsBackground(): Modifier =
    background(PointIqStandingsBackground).drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    PointIqStandingsAmbientBlue.copy(alpha = 0.42f),
                    PointIqStandingsAmbientBlue.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.22f),
                radius = size.width * 1.15f,
            ),
        )
    }
