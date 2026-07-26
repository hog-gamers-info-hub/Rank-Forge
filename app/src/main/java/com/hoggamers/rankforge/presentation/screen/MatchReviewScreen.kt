package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_REVIEW_SCREEN_TEST_TAG = "match_review_screen"
const val MATCH_REVIEW_ROW_TEST_TAG_PREFIX = "match_review_row_"
const val MATCH_REVIEW_VALID_STATUS_TEST_TAG = "match_review_valid_status"
const val MATCH_REVIEW_ISSUES_STATUS_TEST_TAG = "match_review_issues_status"
const val MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG = "match_review_placements_action"
const val MATCH_REVIEW_KILLS_ACTION_TEST_TAG = "match_review_kills_action"
const val MATCH_REVIEW_DETAILS_ACTION_TEST_TAG = "match_review_details_action"

@Composable
fun MatchReviewRoute(
    tournamentId: String,
    matchId: String,
    onBackToDetails: () -> Unit,
    onEnterPlacements: (String, String) -> Unit,
    onEnterKills: (String, String) -> Unit,
    viewModel: MatchReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId) {
        viewModel.load(tournamentId, matchId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
            MatchReviewNavigation.DETAILS -> {
                viewModel.onNavigationHandled()
                onBackToDetails()
            }
            null -> Unit
        }
    }
    BackHandler(onBack = viewModel::onBackToDetails)

    MatchReviewScreen(
        uiState = uiState,
        onEnterPlacements = viewModel::openPlacements,
        onEnterKills = viewModel::openKills,
        onBackToDetails = viewModel::onBackToDetails,
    )
}

@Composable
fun MatchReviewScreen(
    uiState: MatchReviewUiState,
    onEnterPlacements: () -> Unit,
    onEnterKills: () -> Unit,
    onBackToDetails: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.match_review_loading),
        )
        uiState.isNotFound -> MatchReviewNotFoundState(onBackToDetails)
        uiState.isAvailable -> MatchReviewContent(
            uiState = uiState,
            onEnterPlacements = onEnterPlacements,
            onEnterKills = onEnterKills,
            onBackToDetails = onBackToDetails,
        )
    }
}

@Composable
private fun MatchReviewContent(
    uiState: MatchReviewUiState,
    onEnterPlacements: () -> Unit,
    onEnterKills: () -> Unit,
    onBackToDetails: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_REVIEW_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.match_review_title, uiState.matchNumber ?: 0),
            style = MaterialTheme.typography.headlineMedium,
        )
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
        TextButton(
            onClick = onBackToDetails,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.back_to_match_details_action))
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
