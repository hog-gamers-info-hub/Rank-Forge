package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.KillGlobalError
import com.hoggamers.rankforge.domain.tournament.KillValidationError
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_KILL_SCREEN_TEST_TAG = "match_kill_screen"
const val MATCH_KILL_ROW_TEST_TAG_PREFIX = "match_kill_row_"
const val MATCH_KILL_FIELD_TEST_TAG_PREFIX = "match_kill_field_"
const val MATCH_KILL_SAVE_ACTION_TEST_TAG = "match_kill_save_action"
const val MATCH_KILL_BACK_ACTION_TEST_TAG = "match_kill_back_action"

@Composable
fun MatchKillRoute(
    tournamentId: String,
    matchId: String,
    onBackToDetails: () -> Unit,
    viewModel: MatchKillViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId) {
        viewModel.load(tournamentId, matchId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.navigation) {
        if (uiState.navigation != null) {
            viewModel.onNavigationHandled()
            onBackToDetails()
        }
    }
    BackHandler(onBack = viewModel::onBackPressed)

    MatchKillScreen(
        uiState = uiState,
        onKillsChanged = viewModel::onKillsChanged,
        onSave = viewModel::save,
        onBackPressed = viewModel::onBackPressed,
    )
}

@Composable
fun MatchKillScreen(
    uiState: MatchKillUiState,
    onKillsChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackPressed: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.match_kill_loading),
        )
        uiState.isNotFound -> MatchKillNotFoundState(onBackPressed)
        uiState.isAvailable -> MatchKillContent(
            uiState = uiState,
            onKillsChanged = onKillsChanged,
            onSave = onSave,
            onBackPressed = onBackPressed,
        )
    }
}

@Composable
private fun MatchKillContent(
    uiState: MatchKillUiState,
    onKillsChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackPressed: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_KILL_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.match_kill_title, uiState.matchNumber ?: 0),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_kill_instructions))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        uiState.rows.forEach { row ->
            MatchKillRow(
                row = row,
                error = uiState.validationErrors[row.teamSlotNumber],
                onKillsChanged = onKillsChanged,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        MatchKillGlobalError(uiState.globalError)
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_KILL_SAVE_ACTION_TEST_TAG),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.save_match_kills_action))
            }
        }
        TextButton(
            onClick = onBackPressed,
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_KILL_BACK_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.back_to_tournament_details_action))
        }
    }
}

@Composable
private fun MatchKillRow(
    row: MatchKillRowUiState,
    error: KillValidationError?,
    onKillsChanged: (Int, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_KILL_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
    ) {
        Text(
            text = stringResource(
                R.string.match_kill_team_label,
                row.teamSlotNumber,
                row.teamName.ifBlank { stringResource(R.string.empty_team_slot_subtitle) },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = row.killsInput,
            onValueChange = { value -> onKillsChanged(row.teamSlotNumber, value) },
            label = { Text(stringResource(R.string.match_kill_field_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + row.teamSlotNumber),
            isError = error != null,
            supportingText = {
                if (error == KillValidationError.INVALID) {
                    Text(stringResource(R.string.match_kill_invalid_error))
                }
            },
        )
    }
}

@Composable
private fun MatchKillGlobalError(error: KillGlobalError?) {
    val message = when (error) {
        KillGlobalError.MATCH_NOT_FOUND -> stringResource(R.string.match_kill_match_not_found_error)
        KillGlobalError.MATCH_NOT_DRAFT -> stringResource(R.string.match_kill_not_draft_error)
        KillGlobalError.INVALID_DATA -> stringResource(R.string.match_kill_invalid_data_error)
        null -> null
    }
    if (message != null) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MatchKillNotFoundState(onBackPressed: () -> Unit) {
    RankForgeScreenContainer {
        Text(
            text = stringResource(R.string.match_kill_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_kill_not_found_message))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackPressed) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}
