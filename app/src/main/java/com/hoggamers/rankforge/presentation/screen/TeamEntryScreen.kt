package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
const val TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX = "team_entry_roster_button_"
private const val SHOW_TEAM_ENTRY_VALIDATION_ISSUES = false

@Composable
fun TeamEntryRoute(
    tournamentId: String,
    onBackToDetails: () -> Unit,
    onEditRoster: (Int) -> Unit = {},
    onReviewRoster: () -> Unit = {},
    focusSlotNumber: Int? = null,
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
        onEditRoster = onEditRoster,
        onReviewRoster = onReviewRoster,
        focusSlotNumber = focusSlotNumber,
    )
}

@Composable
fun TeamEntryScreen(
    uiState: TeamEntryUiState,
    onTeamNameChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackToDetails: () -> Unit,
    onEditRoster: (Int) -> Unit = {},
    onReviewRoster: () -> Unit = {},
    focusSlotNumber: Int? = null,
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
            onEditRoster = onEditRoster,
            onReviewRoster = onReviewRoster,
            focusSlotNumber = focusSlotNumber,
            isSaving = uiState.isSaving,
            hasSaveError = uiState.hasSaveError,
            validationIssues = uiState.validationIssues,
        )
    }
}

@Composable
private fun TeamEntryContent(
    slots: List<TeamEntrySlotUiState>,
    onTeamNameChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackToDetails: () -> Unit,
    onEditRoster: (Int) -> Unit,
    onReviewRoster: () -> Unit,
    focusSlotNumber: Int?,
    isSaving: Boolean,
    hasSaveError: Boolean,
    validationIssues: List<RosterValidationIssueUiState>,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(TEAM_ENTRY_SCREEN_TEST_TAG)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        val focusRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(focusSlotNumber) {
            if (focusSlotNumber != null) {
                focusRequester.bringIntoView()
            }
        }
        Text(
            text = stringResource(R.string.team_entry_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        if (SHOW_TEAM_ENTRY_VALIDATION_ISSUES) {
            RosterValidationIssues(issues = validationIssues)
            if (validationIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            }
        }
        slots.forEach { slot ->
            val isFocusedSlot = slot.slotNumber == focusSlotNumber
            OutlinedTextField(
                value = slot.teamName,
                onValueChange = { teamName -> onTeamNameChanged(slot.slotNumber, teamName) },
                label = {
                    Text(text = stringResource(R.string.team_name_slot_label, slot.slotNumber))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFocusedSlot) {
                            Modifier.bringIntoViewRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .testTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + slot.slotNumber),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Button(
                onClick = { onEditRoster(slot.slotNumber) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX + slot.slotNumber),
            ) {
                Text(text = stringResource(R.string.enter_players_name_action))
            }
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        }
        Button(
            onClick = onReviewRoster,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.overview_team_details_action))
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (isSaving) {
                        R.string.saving_team_names_action
                    } else {
                        R.string.save_team_names_action
                    },
                ),
            )
        }
        if (hasSaveError) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = stringResource(R.string.team_names_save_error),
                color = MaterialTheme.colorScheme.error,
            )
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
