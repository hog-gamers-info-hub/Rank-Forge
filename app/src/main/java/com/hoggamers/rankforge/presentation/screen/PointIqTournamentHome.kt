package com.hoggamers.rankforge.presentation.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R
import kotlinx.coroutines.delay

private val PointIqHomeHeader = Color(0xFFF6F8FF)
private val PointIqHomeSubtitle = Color(0xFF91AFE0)
private val PointIqHomeInactiveBlue = Color(0xFF7D9DCE)
private val PointIqHomeBlue = Color(0xFF176AF7)
private val PointIqHomeCyan = Color(0xFF17C9F2)
private val PointIqHomeCard = Color(0xFF071B3E)
private val PointIqHomeBorder = PointIqHomeBlue.copy(alpha = 0.55f)
private val PointIqHomeDanger = Color(0xFFFF9A9A)
private val PointIqHomeUtilityBorder = PointIqHomeInactiveBlue.copy(alpha = 0.55f)

private const val FEEDBACK_LABEL_HOLD_MS = 5_000L
private const val FEEDBACK_LABEL_TRANSITION_MS = 500
private val FeedbackCollapsedWidth = 36.dp

internal const val POINTIQ_FEEDBACK_URL = "https://feedback.point-iq.com/"

const val POINTIQ_HOME_CREATE_TOURNAMENT_TEST_TAG = "pointiq_home_create_tournament"
const val POINTIQ_HOME_FEEDBACK_TEST_TAG = "pointiq_home_feedback"
const val POINTIQ_HOME_FEEDBACK_PILL_TEST_TAG = "pointiq_home_feedback_pill"

@Composable
internal fun PointIqTournamentHomeContent(
    uiState: TournamentListUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAllTournaments: () -> Unit,
    onDeleteTournament: ((TournamentListItemUiState) -> Unit)? = null,
    onOpenFeedback: (() -> Unit)? = null,
    isDeleting: Boolean = false,
    deletionErrorMessage: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val navigationBarBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val openFeedback = onOpenFeedback ?: {
        tryLaunchIntent(buildBrowserIntent(POINTIQ_FEEDBACK_URL)) { intent ->
            context.startActivity(intent)
        }
        Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TOURNAMENT_LIST_SCREEN_TEST_TAG),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = navigationBarBottomPadding + 64.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PointIqRecentHeader(onViewAll = onOpenAllTournaments)
                }
            }

            if (uiState.isEmpty) {
                item {
                    PointIqEmptyTournamentCard()
                }
            } else {
                items(
                    items = uiState.tournaments.takeLast(3),
                    key = { tournament -> tournament.id },
                ) { tournament ->
                    PointIqTournamentSummaryCard(
                        tournament = tournament,
                        onClick = { onOpenTournamentDetails(tournament.id) },
                        onDeleteClick = onDeleteTournament?.let { deleteTournament ->
                            { deleteTournament(tournament) }
                        },
                    )
                }
            }

            if (isDeleting) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = PointIqHomeCyan,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.tournament_delete_in_progress),
                            color = PointIqHomeSubtitle,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            if (deletionErrorMessage != null) {
                item {
                    Text(
                        text = deletionErrorMessage,
                        color = PointIqHomeDanger,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                PointIqCreateTournamentCta(
                    hasTournamentCards = !uiState.isEmpty,
                    onClick = onCreateTournament,
                )
            }

        }

        PointIqFeedbackQuickAction(
            onClick = openFeedback,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = navigationBarBottomPadding + 100.dp),
        )
    }
}

@Composable
private fun PointIqFeedbackQuickAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val feedbackLabel = stringResource(R.string.pointiq_home_feedback_label_feedback)
    val suggestionLabel = stringResource(R.string.pointiq_home_feedback_label_suggestion)
    val reportLabel = stringResource(R.string.pointiq_home_feedback_label_report)
    val labels = remember(feedbackLabel, suggestionLabel, reportLabel) {
        listOf(feedbackLabel, suggestionLabel, reportLabel)
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelTextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )
    val expandedWidths = remember(labels, density) {
        val arrowWidthPx = textMeasurer.measure(
            text = "↗",
            style = labelTextStyle,
            maxLines = 1,
        ).size.width
        labels.map { label ->
            val labelWidthPx = textMeasurer.measure(
                text = label,
                style = labelTextStyle,
                maxLines = 1,
            ).size.width
            with(density) {
                labelWidthPx.toDp() + arrowWidthPx.toDp() + 4.dp + 20.dp
            }
        }
    }
    val labelIndex = remember { mutableIntStateOf(0) }
    val animatedWidth = remember(expandedWidths.first()) {
        Animatable(expandedWidths.first().value)
    }

    LaunchedEffect(expandedWidths) {
        while (true) {
            delay(FEEDBACK_LABEL_HOLD_MS)
            animatedWidth.animateTo(
                targetValue = FeedbackCollapsedWidth.value,
                animationSpec = tween(
                    durationMillis = FEEDBACK_LABEL_TRANSITION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            labelIndex.intValue = (labelIndex.intValue + 1) % labels.size
            animatedWidth.animateTo(
                targetValue = expandedWidths[labelIndex.intValue].value,
                animationSpec = tween(
                    durationMillis = FEEDBACK_LABEL_TRANSITION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .then(modifier)
            .padding(start = 12.dp)
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .testTag(POINTIQ_HOME_FEEDBACK_TEST_TAG),
        contentAlignment = Alignment.BottomStart,
    ) {
        Row(
            modifier = Modifier
                .width(animatedWidth.value.dp)
                .height(36.dp)
                .shadow(1.dp, shape)
                .clip(shape)
                .border(0.5.dp, PointIqHomeUtilityBorder, shape)
                .testTag(POINTIQ_HOME_FEEDBACK_PILL_TEST_TAG)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labels[labelIndex.intValue],
                color = PointIqHomeInactiveBlue,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = "↗",
                color = PointIqHomeInactiveBlue,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PointIqCreateTournamentCta(
    hasTournamentCards: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (hasTournamentCards) 14.dp else 0.dp,
                bottom = 12.dp,
            ),
    ) {
        ReviewMatchActionButton(
            label = stringResource(R.string.pointiq_home_create_title),
            enabled = true,
            onClick = onClick,
            modifier = Modifier.testTag(POINTIQ_HOME_CREATE_TOURNAMENT_TEST_TAG),
        )
    }
}

@Composable
private fun PointIqRecentHeader(
    onViewAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.pointiq_home_recent_title),
            color = PointIqHomeHeader,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        TextButton(onClick = onViewAll) {
            Text(
                text = stringResource(R.string.pointiq_home_view_all),
                color = PointIqHomeCyan,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PointIqHomeCyan,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun PointIqTournamentSummaryCard(
    tournament: TournamentListItemUiState,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val teamsText = stringResource(
        if (tournament.totalTeams == 1) {
            R.string.pointiq_home_team_count_one
        } else {
            R.string.pointiq_home_team_count_many
        },
        tournament.totalTeams,
    )
    val matchesText = stringResource(
        if (tournament.totalMatches == 1) {
            R.string.pointiq_home_match_count_one
        } else {
            R.string.pointiq_home_match_count_many
        },
        tournament.totalMatches,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PointIqHomeCard)
            .border(1.dp, PointIqHomeBorder, shape)
            .clickable(onClick = onClick)
            .testTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
            .padding(
                horizontal = if (onDeleteClick != null) 12.dp else 16.dp,
                vertical = if (onDeleteClick != null) 8.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tournament.name,
                color = PointIqHomeHeader,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Free Fire MAX  •  Squad",
                color = PointIqHomeSubtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.pointiq_home_summary_line,
                    teamsText,
                    matchesText,
                ),
                color = PointIqHomeSubtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.size(12.dp))
        if (onDeleteClick == null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PointIqHomeInactiveBlue,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onDeleteClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.tournament_delete_action),
                        tint = PointIqHomeInactiveBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PointIqEmptyTournamentCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_LIST_EMPTY_TEST_TAG)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.tournament_list_empty_message),
            color = PointIqHomeSubtitle,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
