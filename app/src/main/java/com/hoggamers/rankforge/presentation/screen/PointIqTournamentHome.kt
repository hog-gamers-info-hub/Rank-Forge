package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R

private val PointIqHomeHeader = Color(0xFFF6F8FF)
private val PointIqHomeSubtitle = Color(0xFF91AFE0)
private val PointIqHomeInactiveBlue = Color(0xFF7D9DCE)
private val PointIqHomeBlue = Color(0xFF176AF7)
private val PointIqHomeCyan = Color(0xFF17C9F2)
private val PointIqHomeCard = Color(0xFF071B3E)
private val PointIqHomeBorder = PointIqHomeBlue.copy(alpha = 0.55f)

const val POINTIQ_HOME_CREATE_TOURNAMENT_TEST_TAG = "pointiq_home_create_tournament"

@Composable
internal fun PointIqTournamentHomeContent(
    uiState: TournamentListUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAllTournaments: () -> Unit,
) {
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
                bottom = 16.dp,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tournament.name,
                color = PointIqHomeHeader,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Free Fire MAX  •  Squad",
                color = PointIqHomeSubtitle,
                fontSize = 14.sp,
                lineHeight = 19.sp,
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
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.size(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PointIqHomeInactiveBlue,
            modifier = Modifier.size(24.dp),
        )
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
