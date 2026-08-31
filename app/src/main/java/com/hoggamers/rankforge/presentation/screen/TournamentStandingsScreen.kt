package com.hoggamers.rankforge.presentation.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.theme.RankForgePageBackground
import kotlinx.coroutines.flow.Flow

private val PointIqStandingsNavy = Color(0xFF071B3E)
private val PointIqStandingsBody = Color(0xFF607393)
private val PointIqStandingsBlue = Color(0xFF176AF7)
private val PointIqStandingsBorder = Color(0xFFD9E6F7)
private val PointIqStandingsBanner = Color(0xFFF5F8FF)
private val PointIqStandingsBannerBorder = Color(0xFFCFE0FF)
private val PointIqStandingsDivider = Color(0xFFE2EAF4)

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
        shareTextTitle = context.getString(R.string.tournament_standings_share_text_title),
        chooserTitle = context.getString(R.string.tournament_standings_share_chooser_title),
        failureMessage = context.getString(R.string.tournament_standings_share_failed_message),
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
    when {
        uiState.isLoading -> RankForgeLoadingState(
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
            .background(RankForgePageBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        TournamentStandingsHeader(
            isPublishing = isPublishing,
            onShareStandings = onShareStandings,
            onBackToTournamentDetails = onBackToTournamentDetails,
        )
        Spacer(modifier = Modifier.height(18.dp))
        TournamentStandingsInfoBanner()
        Spacer(modifier = Modifier.height(20.dp))
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
private fun TournamentStandingsHeader(
    isPublishing: Boolean = false,
    onShareStandings: (() -> Unit)? = null,
    onBackToTournamentDetails: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tournament_standings_title),
            color = PointIqStandingsNavy,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        onShareStandings?.let { share ->
            TextButton(
                onClick = share,
                enabled = !isPublishing,
                modifier = Modifier.testTag(TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG),
            ) {
                Text(
                    text = stringResource(R.string.share_action),
                    color = PointIqStandingsBlue,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(onClick = onBackToTournamentDetails) {
            Text(
                text = stringResource(R.string.back_action),
                color = PointIqStandingsBlue,
                fontWeight = FontWeight.SemiBold,
            )
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
private fun TournamentStandingsInfoBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PointIqStandingsBanner,
        border = BorderStroke(1.dp, PointIqStandingsBannerBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PointIqStandingsBlue,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.tournament_standings_finalized_only_message),
                color = PointIqStandingsNavy,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TournamentStandingRow(row: TournamentStandingRowUiState) {
    val rankStyle = standingRankStyle(row.displayOrder)
    val teamLabel = row.teamName
        ?.takeIf { it.isNotBlank() }
        ?.let { teamName ->
            stringResource(R.string.tournament_standing_team_name_inline, teamName)
        }
        ?: stringResource(
            R.string.tournament_standing_team_slot_inline,
            row.teamSlotNumber,
        )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PointIqStandingsBorder),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StandingRankBadge(row.displayOrder, rankStyle)
                    Text(
                        text = teamLabel,
                        color = PointIqStandingsBody,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = PointIqStandingsDivider,
                    thickness = 1.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    StandingMetric(
                        label = stringResource(R.string.tournament_standing_kill_points_label),
                        value = row.totalKillPoints.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StandingMetric(
                        label = stringResource(R.string.tournament_standing_position_points_label),
                        value = row.totalPositionPoints.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StandingMetric(
                        label = stringResource(R.string.tournament_standing_total_points_label),
                        value = row.totalPoints.toString(),
                        valueColor = rankStyle.accent,
                        valueFontSize = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    StandingMetric(
                        label = stringResource(
                            R.string.tournament_standing_first_place_finishes_label,
                        ),
                        value = row.firstPlaceFinishes.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StandingMetric(
                        label = stringResource(R.string.tournament_standing_latest_placement_label),
                        value = row.latestMatchPlacement?.toString()
                            ?: stringResource(
                                R.string.tournament_standing_latest_placement_none_value,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    StandingMetric(
                        label = stringResource(R.string.tournament_standing_matches_included_label),
                        value = row.matchesIncluded.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.isCompleteTie) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.tournament_standing_complete_tie_message),
                        color = PointIqStandingsBody,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(
                            TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + row.teamSlotNumber,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StandingRankBadge(
    displayOrder: Int,
    rankStyle: StandingRankStyle,
) {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 38.dp)
            .background(rankStyle.badgeBackground, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayOrder.toString(),
            color = rankStyle.accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StandingMetric(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = PointIqStandingsNavy,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = PointIqStandingsBody,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = valueFontSize,
            lineHeight = (valueFontSize.value + 3).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun standingRankStyle(displayOrder: Int): StandingRankStyle = when (displayOrder) {
    1 -> StandingRankStyle(
        accent = Color(0xFFC28A00),
        badgeBackground = Color(0xFFFFF2C7),
    )
    2 -> StandingRankStyle(
        accent = Color(0xFF176AF7),
        badgeBackground = Color(0xFFE8F0FF),
    )
    3 -> StandingRankStyle(
        accent = Color(0xFFD46B2C),
        badgeBackground = Color(0xFFFFEBDD),
    )
    else -> StandingRankStyle(
        accent = PointIqStandingsNavy,
        badgeBackground = Color(0xFFEFF4FA),
    )
}

@Composable
private fun TournamentStandingsEmptyState(onBackToTournamentDetails: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RankForgePageBackground)
            .padding(24.dp)
            .testTag(TOURNAMENT_STANDINGS_EMPTY_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        TournamentStandingsHeader(onBackToTournamentDetails = onBackToTournamentDetails)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.tournament_standings_empty_title),
            color = PointIqStandingsNavy,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tournament_standings_empty_message),
            color = PointIqStandingsBody,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBackToTournamentDetails) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}
