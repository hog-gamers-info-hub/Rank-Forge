package com.hoggamers.rankforge.presentation.screen

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val ROSTER_REVIEW_SCREEN_TEST_TAG = "roster_review_screen"
const val ROSTER_REVIEW_SLOT_ITEM_TEST_TAG_PREFIX = "roster_review_slot_item_"
const val ROSTER_REVIEW_PLAYER_TEST_TAG_PREFIX = "roster_review_player_"
const val ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG = "roster_review_confirm"
const val ROSTER_REVIEW_STATUS_TEST_TAG = "roster_review_status"

@Composable
fun RosterReviewRoute(
    tournamentId: String,
    onEditTeam: (Int) -> Unit,
    onEditRoster: (Int) -> Unit,
    onBackToTeamEntry: () -> Unit,
    viewModel: RosterReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RosterReviewScreen(
        uiState = uiState,
        onEditTeam = onEditTeam,
        onEditRoster = onEditRoster,
        onConfirm = viewModel::confirmRoster,
        onBackToTeamEntry = onBackToTeamEntry,
        rosterScreenshotIntake = {
            RosterScreenshotIntakeRoute(tournamentId = tournamentId)
        },
    )
}

@Composable
fun RosterReviewScreen(
    uiState: RosterReviewUiState,
    onEditTeam: (Int) -> Unit,
    onEditRoster: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBackToTeamEntry: () -> Unit,
    rosterScreenshotIntake: @Composable () -> Unit = {},
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.roster_review_loading),
        )
        uiState.isNotFound -> RosterReviewNotFoundState(onBackToTeamEntry)
        else -> RosterReviewContent(
            uiState = uiState,
            onEditTeam = onEditTeam,
            onEditRoster = onEditRoster,
            onConfirm = onConfirm,
            onBackToTeamEntry = onBackToTeamEntry,
            rosterScreenshotIntake = rosterScreenshotIntake,
        )
    }
}

@Composable
private fun RosterReviewContent(
    uiState: RosterReviewUiState,
    onEditTeam: (Int) -> Unit,
    onEditRoster: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBackToTeamEntry: () -> Unit,
    rosterScreenshotIntake: @Composable () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(ROSTER_REVIEW_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.roster_review_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(
            text = stringResource(
                if (uiState.isConfirmed) {
                    R.string.roster_review_confirmed_status
                } else {
                    R.string.roster_review_draft_status
                },
            ),
            modifier = Modifier.testTag(ROSTER_REVIEW_STATUS_TEST_TAG),
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        rosterScreenshotIntake()
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        RosterValidationIssues(issues = uiState.validationIssues)
        if (uiState.validationIssues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        }
        uiState.teams.sortedBy { it.slotNumber }.forEach { team ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ROSTER_REVIEW_SLOT_ITEM_TEST_TAG_PREFIX + team.slotNumber),
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
            ) {
                Text(
                    text = stringResource(R.string.team_slot_label, team.slotNumber),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = team.teamName.ifBlank {
                        stringResource(R.string.empty_team_slot_subtitle)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (team.players.isEmpty()) {
                    Text(text = stringResource(R.string.roster_empty_message))
                } else {
                    team.players.sortedBy { it.playerIndex }.forEach { player ->
                        Text(
                            text = stringResource(
                                R.string.roster_review_player_value,
                                player.playerIndex + 1,
                                player.displayName,
                            ),
                            modifier = Modifier.testTag(
                                ROSTER_REVIEW_PLAYER_TEST_TAG_PREFIX +
                                    "${team.slotNumber}_${player.playerIndex}",
                            ),
                        )
                    }
                }
                Button(
                    onClick = { onEditTeam(team.slotNumber) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.edit_team_action, team.slotNumber))
                }
                Button(
                    onClick = { onEditRoster(team.slotNumber) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.edit_roster_action, team.slotNumber))
                }
            }
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        }
        if (uiState.hasConfirmError) {
            Text(
                text = stringResource(R.string.roster_confirmation_error),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        Button(
            onClick = onConfirm,
            enabled = uiState.canConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState.isConfirming) {
                        R.string.confirming_roster_action
                    } else {
                        R.string.confirm_roster_action
                    },
                ),
            )
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Button(
            onClick = onBackToTeamEntry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.back_to_team_entry_action))
        }
    }
}

@Composable
private fun RosterReviewNotFoundState(onBackToTeamEntry: () -> Unit) {
    RankForgeScreenContainer {
        Text(
            text = stringResource(R.string.tournament_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackToTeamEntry) {
            Text(text = stringResource(R.string.back_to_team_entry_action))
        }
    }
}
