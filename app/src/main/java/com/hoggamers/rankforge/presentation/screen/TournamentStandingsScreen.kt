package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val TOURNAMENT_STANDINGS_SCREEN_TEST_TAG = "tournament_standings_screen"
const val TOURNAMENT_STANDINGS_EMPTY_TEST_TAG = "tournament_standings_empty"
const val TOURNAMENT_STANDINGS_LIST_TEST_TAG = "tournament_standings_list"
const val TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX = "tournament_standing_row_"
const val TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX = "tournament_standing_complete_tie_"
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

    TournamentStandingsScreen(
        uiState = uiState,
        onBackToTournamentDetails = onBackToTournamentDetails,
    )
}

@Composable
fun TournamentStandingsScreen(
    uiState: TournamentStandingsUiState,
    onBackToTournamentDetails: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.tournament_standings_loading),
        )
        uiState.rows.isEmpty() -> TournamentStandingsEmptyState(onBackToTournamentDetails)
        else -> TournamentStandingsContent(
            rows = uiState.rows,
            onBackToTournamentDetails = onBackToTournamentDetails,
        )
    }
}

@Composable
private fun TournamentStandingsContent(
    rows: List<TournamentStandingRowUiState>,
    onBackToTournamentDetails: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.tournament_standings_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.tournament_standings_finalized_only_message))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TOURNAMENT_STANDINGS_LIST_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            rows.forEach { row ->
                TournamentStandingRow(row)
            }
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onBackToTournamentDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}

@Composable
private fun TournamentStandingRow(row: TournamentStandingRowUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
    ) {
        Text(
            text = stringResource(R.string.tournament_standing_order_value, row.displayOrder),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = stringResource(R.string.tournament_standing_team_slot_value, row.teamSlotNumber))
        Text(text = stringResource(R.string.tournament_standing_total_points_value, row.totalPoints))
        Text(
            text = stringResource(
                R.string.tournament_standing_position_points_value,
                row.totalPositionPoints,
            ),
        )
        Text(
            text = stringResource(
                R.string.tournament_standing_kill_points_value,
                row.totalKillPoints,
            ),
        )
        Text(
            text = stringResource(
                R.string.tournament_standing_first_place_finishes_value,
                row.firstPlaceFinishes,
            ),
        )
        Text(
            text = row.latestMatchPlacement?.let { placement ->
                stringResource(
                    R.string.tournament_standing_latest_placement_value,
                    placement,
                )
            } ?: stringResource(R.string.tournament_standing_latest_placement_none),
        )
        Text(
            text = stringResource(
                R.string.tournament_standing_matches_included_value,
                row.matchesIncluded,
            ),
        )
        if (row.isCompleteTie) {
            Text(
                text = stringResource(R.string.tournament_standing_complete_tie_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(
                    TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + row.teamSlotNumber,
                ),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = RankForgeSpacing.Small))
    }
}

@Composable
private fun TournamentStandingsEmptyState(onBackToTournamentDetails: () -> Unit) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(TOURNAMENT_STANDINGS_EMPTY_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.tournament_standings_empty_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.tournament_standings_empty_message))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackToTournamentDetails) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}
