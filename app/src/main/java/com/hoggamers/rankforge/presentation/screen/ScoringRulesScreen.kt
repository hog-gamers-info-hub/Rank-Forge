package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.PositionPointsEngine
import com.hoggamers.rankforge.presentation.component.PointIqHomeSystemBars
import com.hoggamers.rankforge.presentation.component.PointIqPageHeader
import com.hoggamers.rankforge.presentation.component.pointIqHomeBackground

private val ScoringRulesHeading = Color(0xFFF6F8FF)
private val ScoringRulesBody = Color(0xFFF6F8FF)
private val ScoringRulesSecondary = Color(0xFF91AFE0)

const val SCORING_RULES_SCREEN_TEST_TAG = "scoring_rules_screen"

private val placementPoints = (1..12).map { placement ->
    placement to PositionPointsEngine()(placement)
}

@Composable
fun ScoringRulesScreen(
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    PointIqHomeSystemBars()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointIqHomeBackground()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 24.dp),
    ) {
        PointIqPageHeader(
            title = stringResource(R.string.scoring_rules_title),
            onBack = onBack,
            backTestTag = SCORING_RULES_SCREEN_TEST_TAG + "_back",
        )

        Spacer(modifier = Modifier.height(24.dp))

        ScoringRulesSectionTitle(R.string.scoring_rules_placement_points_title)
        Spacer(modifier = Modifier.height(8.dp))
        PlacementPointsGrid()

        Spacer(modifier = Modifier.height(24.dp))
        ScoringRulesSectionTitle(R.string.scoring_rules_kill_points_title)
        Spacer(modifier = Modifier.height(6.dp))
        ScoringRulesBodyText(R.string.scoring_rules_kill_points_description)

        Spacer(modifier = Modifier.height(20.dp))
        ScoringRulesSectionTitle(R.string.scoring_rules_total_points_title)
        Spacer(modifier = Modifier.height(6.dp))
        ScoringRulesBodyText(R.string.scoring_rules_total_points_description)

        Spacer(modifier = Modifier.height(20.dp))
        ScoringRulesSectionTitle(R.string.scoring_rules_tie_break_title)
        Spacer(modifier = Modifier.height(6.dp))
        val tieBreakRules = listOf(
            R.string.scoring_rules_tie_break_first,
            R.string.scoring_rules_tie_break_kills,
            R.string.scoring_rules_tie_break_latest_placement,
        )
        tieBreakRules.forEachIndexed { index, ruleRes ->
            Text(
                text = stringResource(
                    R.string.scoring_rules_tie_break_item,
                    index + 1,
                    stringResource(ruleRes),
                ),
                color = ScoringRulesBody,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.scoring_rules_complete_tie),
            color = ScoringRulesSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ScoringRulesSectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = ScoringRulesHeading,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ScoringRulesBodyText(textRes: Int) {
    Text(
        text = stringResource(textRes),
        color = ScoringRulesBody,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun PlacementPointsGrid() {
    val labels = stringArrayResource(R.array.scoring_rules_placement_labels)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PlacementPointsColumn(
            entries = placementPoints.take(6),
            labels = labels,
            modifier = Modifier.weight(1f),
        )
        PlacementPointsColumn(
            entries = placementPoints.drop(6),
            labels = labels,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlacementPointsColumn(
    entries: List<Pair<Int, Int>>,
    labels: Array<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        entries.forEach { (placement, points) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = labels[placement - 1],
                    color = ScoringRulesBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.width(42.dp),
                )
                Text(
                    text = points.toString(),
                    color = ScoringRulesBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
