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
import androidx.compose.material3.OutlinedTextField
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

const val ROSTER_ENTRY_SCREEN_TEST_TAG = "roster_entry_screen"
const val ROSTER_PLAYER_INPUT_TEST_TAG_PREFIX = "roster_player_input_"
const val ROSTER_REMOVE_PLAYER_BUTTON_TEST_TAG_PREFIX = "roster_remove_player_"
const val ROSTER_ADD_PLAYER_BUTTON_TEST_TAG = "roster_add_player"
const val ROSTER_SAVE_BUTTON_TEST_TAG = "roster_save"

@Composable
fun RosterEntryRoute(
    tournamentId: String,
    slotNumber: Int,
    onBackToTeamEntry: () -> Unit,
    viewModel: RosterEntryViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, slotNumber) {
        viewModel.load(tournamentId, slotNumber)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RosterEntryScreen(
        uiState = uiState,
        onPlayerNameChanged = viewModel::onPlayerNameChanged,
        onAddPlayer = viewModel::addPlayer,
        onRemovePlayer = viewModel::removePlayer,
        onSave = viewModel::saveRoster,
        onBackToTeamEntry = onBackToTeamEntry,
    )
}

@Composable
fun RosterEntryScreen(
    uiState: RosterEntryUiState,
    onPlayerNameChanged: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onSave: () -> Unit,
    onBackToTeamEntry: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.roster_entry_loading),
        )

        uiState.isNotFound -> RosterEntryNotFoundState(onBackToTeamEntry)

        else -> RosterEntryContent(
            uiState = uiState,
            onPlayerNameChanged = onPlayerNameChanged,
            onAddPlayer = onAddPlayer,
            onRemovePlayer = onRemovePlayer,
            onSave = onSave,
            onBackToTeamEntry = onBackToTeamEntry,
        )
    }
}

@Composable
private fun RosterEntryContent(
    uiState: RosterEntryUiState,
    onPlayerNameChanged: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onSave: () -> Unit,
    onBackToTeamEntry: () -> Unit,
) {
    val slotNumber = uiState.slotNumber ?: return
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(ROSTER_ENTRY_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.roster_entry_title, slotNumber),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (uiState.teamName.isNotBlank()) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = uiState.teamName,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(
            text = stringResource(R.string.roster_player_count, uiState.playerCount),
            modifier = Modifier.testTag("roster_player_count"),
        )
        if (uiState.isIncomplete) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = stringResource(R.string.roster_incomplete_message),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("roster_incomplete_message"),
            )
        }
        if (uiState.isAtMaximum) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = stringResource(R.string.roster_maximum_message),
                modifier = Modifier.testTag("roster_maximum_message"),
            )
        }
        if (uiState.players.isEmpty()) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Text(text = stringResource(R.string.roster_empty_message))
        }
        uiState.players.forEachIndexed { playerIndex, player ->
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            OutlinedTextField(
                value = player.displayName,
                onValueChange = { displayName ->
                    onPlayerNameChanged(playerIndex, displayName)
                },
                label = {
                    Text(text = stringResource(R.string.roster_player_name_label, playerIndex + 1))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ROSTER_PLAYER_INPUT_TEST_TAG_PREFIX + playerIndex),
                singleLine = true,
            )
            Button(
                onClick = { onRemovePlayer(playerIndex) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ROSTER_REMOVE_PLAYER_BUTTON_TEST_TAG_PREFIX + playerIndex),
            ) {
                Text(text = stringResource(R.string.remove_player_action, playerIndex + 1))
            }
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onAddPlayer,
            enabled = uiState.canAddPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ROSTER_ADD_PLAYER_BUTTON_TEST_TAG),
        ) {
            Text(text = stringResource(R.string.add_player_action))
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        if (uiState.hasSaveError) {
            Text(
                text = stringResource(R.string.roster_save_error),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        Button(
            onClick = onSave,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ROSTER_SAVE_BUTTON_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState.isSaving) {
                        R.string.roster_saving_action
                    } else {
                        R.string.save_roster_action
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
private fun RosterEntryNotFoundState(onBackToTeamEntry: () -> Unit) {
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
