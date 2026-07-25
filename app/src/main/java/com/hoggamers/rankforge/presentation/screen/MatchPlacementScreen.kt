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
import com.hoggamers.rankforge.domain.tournament.PlacementGlobalError
import com.hoggamers.rankforge.domain.tournament.PlacementValidationError
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_PLACEMENT_SCREEN_TEST_TAG = "match_placement_screen"
const val MATCH_PLACEMENT_ROW_TEST_TAG_PREFIX = "match_placement_row_"
const val MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX = "match_placement_field_"
const val MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG = "match_placement_save_action"
const val MATCH_PLACEMENT_BACK_ACTION_TEST_TAG = "match_placement_back_action"

@Composable
fun MatchPlacementRoute(
    tournamentId: String,
    matchId: String,
    onBackToDetails: () -> Unit,
    viewModel: MatchPlacementViewModel = hiltViewModel(),
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

    MatchPlacementScreen(
        uiState = uiState,
        onPlacementChanged = viewModel::onPlacementChanged,
        onSave = viewModel::save,
        onBackPressed = viewModel::onBackPressed,
    )
}

@Composable
fun MatchPlacementScreen(
    uiState: MatchPlacementUiState,
    onPlacementChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackPressed: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.match_placement_loading),
        )
        uiState.isNotFound -> MatchPlacementNotFoundState(onBackPressed)
        uiState.isAvailable -> MatchPlacementContent(
            uiState = uiState,
            onPlacementChanged = onPlacementChanged,
            onSave = onSave,
            onBackPressed = onBackPressed,
        )
    }
}

@Composable
private fun MatchPlacementContent(
    uiState: MatchPlacementUiState,
    onPlacementChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onBackPressed: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_PLACEMENT_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.match_placement_title, uiState.matchNumber ?: 0),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_placement_instructions))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        uiState.rows.forEach { row ->
            MatchPlacementRow(
                row = row,
                error = uiState.validationErrors[row.teamSlotNumber],
                onPlacementChanged = onPlacementChanged,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        MatchPlacementGlobalError(uiState.globalError)
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.save_match_placements_action))
            }
        }
        TextButton(
            onClick = onBackPressed,
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_PLACEMENT_BACK_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.back_to_tournament_details_action))
        }
    }
}

@Composable
private fun MatchPlacementRow(
    row: MatchPlacementRowUiState,
    error: PlacementValidationError?,
    onPlacementChanged: (Int, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_PLACEMENT_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
    ) {
        Text(
            text = stringResource(
                R.string.match_placement_team_label,
                row.teamSlotNumber,
                row.teamName.ifBlank { stringResource(R.string.empty_team_slot_subtitle) },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = row.placementInput,
            onValueChange = { value -> onPlacementChanged(row.teamSlotNumber, value) },
            label = { Text(stringResource(R.string.match_placement_field_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + row.teamSlotNumber),
            isError = error != null,
            supportingText = {
                when (error) {
                    PlacementValidationError.INVALID -> Text(stringResource(R.string.match_placement_invalid_error))
                    PlacementValidationError.DUPLICATE -> Text(stringResource(R.string.match_placement_duplicate_error))
                    null -> Unit
                }
            },
        )
    }
}

@Composable
private fun MatchPlacementGlobalError(error: PlacementGlobalError?) {
    val message = when (error) {
        PlacementGlobalError.MATCH_NOT_FOUND -> stringResource(R.string.match_placement_match_not_found_error)
        PlacementGlobalError.MATCH_NOT_DRAFT -> stringResource(R.string.match_placement_not_draft_error)
        PlacementGlobalError.INVALID_DATA -> stringResource(R.string.match_placement_invalid_data_error)
        null -> null
    }
    if (message != null) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MatchPlacementNotFoundState(onBackPressed: () -> Unit) {
    RankForgeScreenContainer {
        Text(
            text = stringResource(R.string.match_placement_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_placement_not_found_message))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackPressed) {
            Text(text = stringResource(R.string.back_to_tournament_details_action))
        }
    }
}
