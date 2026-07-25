package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
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

const val TEAM_ENTRY_SCREEN_TEST_TAG = "team_entry_screen"
const val TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX = "team_entry_slot_input_"

@Composable
fun TeamEntryRoute(
    tournamentId: String,
    onBackToDetails: () -> Unit,
    viewModel: TeamEntryViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TeamEntryScreen(
        uiState = uiState,
        onTeamNameChanged = viewModel::onTeamNameChanged,
        onSave = viewModel::saveTeamNames,
        onBackToDetails = onBackToDetails,
    )
}

@Composable
fun TeamEntryScreen(
    uiState: TeamEntryUiState,
    onTeamNameChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackToDetails: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.team_entry_loading),
        )

        uiState.isNotFound -> TeamEntryNotFoundState(onBackToDetails)

        else -> TeamEntryContent(
            slots = uiState.slots,
            onTeamNameChanged = onTeamNameChanged,
            onSave = onSave,
            onBackToDetails = onBackToDetails,
        )
    }
}

@Composable
private fun TeamEntryContent(
    slots: List<TeamEntrySlotUiState>,
    onTeamNameChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackToDetails: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(TEAM_ENTRY_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.team_entry_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        slots.forEach { slot ->
            OutlinedTextField(
                value = slot.teamName,
                onValueChange = { teamName -> onTeamNameChanged(slot.slotNumber, teamName) },
                label = {
                    Text(text = stringResource(R.string.team_name_slot_label, slot.slotNumber))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + slot.slotNumber),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.save_team_names_action))
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Button(
            onClick = onBackToDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}

@Composable
private fun TeamEntryNotFoundState(onBackToDetails: () -> Unit) {
    RankForgeScreenContainer {
        Text(
            text = stringResource(R.string.tournament_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackToDetails) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}
