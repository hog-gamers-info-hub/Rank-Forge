package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PointIqHomeNavy = Color(0xFF071B3E)
private val PointIqHomeBody = Color(0xFF5D6F90)
private val PointIqHomeMuted = Color(0xFF7183A3)
private val PointIqHomeBlue = Color(0xFF176AF7)
private val PointIqHomeCyan = Color(0xFF17C9F2)
private val PointIqHomeBorder = Color(0xFFD9E6F7)
private val PointIqHomeCard = Color(0xFFFFFFFF)
private val PointIqHomeButtonStart = Color(0xFF0B49C8)
private val PointIqHomeButtonMiddle = Color(0xFF0C6EF2)
private val PointIqHomeButtonEnd = Color(0xFF18C7ED)
private val pointIqHomeDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

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
        PointIqHomeBackgroundDecoration()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.pointiq_home_title),
                        color = PointIqHomeNavy,
                        fontSize = 31.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pointiq_home_subtitle),
                        color = PointIqHomeBody,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp,
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                    PointIqCreateTournamentCard(onClick = onCreateTournament)
                    Spacer(modifier = Modifier.height(24.dp))
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
                    PointIqRecentTournamentCard(
                        tournament = tournament,
                        onClick = { onOpenTournamentDetails(tournament.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PointIqCreateTournamentCard(
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            PointIqHomeButtonStart,
            PointIqHomeButtonMiddle,
            PointIqHomeButtonEnd,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = PointIqHomeBlue.copy(alpha = 0.18f),
                spotColor = PointIqHomeBlue.copy(alpha = 0.24f),
            )
            .clip(shape)
            .background(gradient)
            .clickable(onClick = onClick),
    ) {
        PointIqCreateCardDecoration()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PointIqPlusIcon(
                    color = PointIqHomeBlue,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.size(18.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.pointiq_home_create_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pointiq_home_create_subtitle),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            PointIqChevronIcon(
                color = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
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
            color = PointIqHomeNavy,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        TextButton(onClick = onViewAll) {
            Text(
                text = stringResource(R.string.pointiq_home_view_all),
                color = PointIqHomeBlue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.size(5.dp))
            PointIqChevronIcon(
                color = PointIqHomeBlue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PointIqRecentTournamentCard(
    tournament: TournamentListItemUiState,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
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
    val lastUpdated = tournament.lastUpdatedEpochMillis
        ?.let(::formatPointIqHomeDate)
        ?: stringResource(R.string.pointiq_home_last_updated_unknown)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .shadow(
                elevation = 5.dp,
                shape = shape,
                ambientColor = PointIqHomeBlue.copy(alpha = 0.08f),
                spotColor = PointIqHomeBlue.copy(alpha = 0.10f),
            )
            .clip(shape)
            .background(PointIqHomeCard)
            .border(1.dp, PointIqHomeBorder, shape)
            .clickable(onClick = onClick)
            .testTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = tournament.name,
                color = PointIqHomeNavy,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = stringResource(
                    R.string.pointiq_home_summary_line,
                    teamsText,
                    matchesText,
                ),
                color = PointIqHomeBody,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pointiq_home_last_updated, lastUpdated),
                color = PointIqHomeMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.size(12.dp))
        PointIqChevronIcon(
            color = PointIqHomeNavy,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun PointIqEmptyTournamentCard() {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(shape)
            .background(PointIqHomeCard)
            .border(1.dp, PointIqHomeBorder, shape)
            .testTag(TOURNAMENT_LIST_EMPTY_TEST_TAG)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.tournament_list_empty_message),
            color = PointIqHomeBody,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun formatPointIqHomeDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(pointIqHomeDateFormatter)

@Composable
private fun PointIqHomeBackgroundDecoration() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 1.dp.toPx()
        val targetColor = PointIqHomeBlue.copy(alpha = 0.07f)
        val upperCenter = Offset(
            x = size.width + 2.dp.toPx(),
            y = 102.dp.toPx(),
        )

        listOf(42.dp, 76.dp, 112.dp).forEach { radius ->
            drawCircle(
                color = targetColor,
                radius = radius.toPx(),
                center = upperCenter,
                style = Stroke(width = strokeWidth),
            )
        }
        drawLine(
            color = targetColor,
            start = Offset(upperCenter.x - 128.dp.toPx(), upperCenter.y),
            end = Offset(upperCenter.x + 128.dp.toPx(), upperCenter.y),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = targetColor,
            start = Offset(upperCenter.x, upperCenter.y - 128.dp.toPx()),
            end = Offset(upperCenter.x, upperCenter.y + 128.dp.toPx()),
            strokeWidth = strokeWidth,
        )

        val lowerCenter = Offset(
            x = -14.dp.toPx(),
            y = size.height - 24.dp.toPx(),
        )
        listOf(34.dp, 66.dp, 96.dp).forEach { radius ->
            drawCircle(
                color = targetColor.copy(alpha = 0.7f),
                radius = radius.toPx(),
                center = lowerCenter,
                style = Stroke(width = strokeWidth),
            )
        }

        listOf(
            Offset(size.width * 0.72f, 46.dp.toPx()),
            Offset(size.width * 0.68f, 214.dp.toPx()),
            Offset(size.width * 0.86f, size.height * 0.86f),
            Offset(18.dp.toPx(), size.height * 0.78f),
        ).forEach { center ->
            drawCircle(
                color = PointIqHomeCyan.copy(alpha = 0.12f),
                radius = 8.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = 3.dp.toPx(),
                center = center,
            )
        }
    }
}

@Composable
private fun PointIqCreateCardDecoration() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.76f, size.height * 0.5f)
        val color = Color.White.copy(alpha = 0.10f)
        val strokeWidth = 1.dp.toPx()

        listOf(18.dp, 34.dp, 50.dp).forEach { radius ->
            drawCircle(
                color = color,
                radius = radius.toPx(),
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }
        drawLine(
            color = color,
            start = Offset(center.x - 64.dp.toPx(), center.y),
            end = Offset(center.x + 64.dp.toPx(), center.y),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(center.x, center.y - 58.dp.toPx()),
            end = Offset(center.x, center.y + 58.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun PointIqPlusIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 2.4.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.14f),
            end = Offset(size.width * 0.5f, size.height * 0.86f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.14f, size.height * 0.5f),
            end = Offset(size.width * 0.86f, size.height * 0.5f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun PointIqChevronIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, size.height * 0.18f),
            end = Offset(size.width * 0.68f, size.height * 0.5f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.68f, size.height * 0.5f),
            end = Offset(size.width * 0.34f, size.height * 0.82f),
            strokeWidth = stroke,
        )
    }
}
