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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionGlobalError
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val MATCH_CORRECTION_SCREEN_TEST_TAG = "match_correction_screen"
const val MATCH_CORRECTION_ROW_TEST_TAG_PREFIX = "match_correction_row_"
const val MATCH_CORRECTION_PLACEMENT_FIELD_TEST_TAG_PREFIX = "match_correction_placement_field_"
const val MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX = "match_correction_kills_field_"
const val MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG = "match_correction_submit_action"
const val MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG = "match_correction_submit_confirm_action"
const val MATCH_CORRECTION_DISCARD_ACTION_TEST_TAG = "match_correction_discard_action"
const val MATCH_CORRECTION_DISCARD_CONFIRM_ACTION_TEST_TAG = "match_correction_discard_confirm_action"

@Composable
fun MatchCorrectionRoute(
    tournamentId: String,
    matchId: String,
    onBackToReview: () -> Unit,
    viewModel: MatchCorrectionViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId) {
        viewModel.load(tournamentId, matchId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.navigation) {
        when (uiState.navigation) {
            MatchCorrectionNavigation.REVIEW,
            MatchCorrectionNavigation.DETAILS,
            -> {
                viewModel.onNavigationHandled()
                onBackToReview()
            }
            null -> Unit
        }
    }
    BackHandler(onBack = viewModel::onBackPressed)

    MatchCorrectionScreen(
        uiState = uiState,
        onPlacementChanged = viewModel::onPlacementChanged,
        onKillsChanged = viewModel::onKillsChanged,
        onSubmit = viewModel::submit,
        onDiscard = viewModel::discard,
        onBackPressed = viewModel::onBackPressed,
    )
}

@Composable
fun MatchCorrectionScreen(
    uiState: MatchCorrectionUiState,
    onPlacementChanged: (Int, String) -> Unit,
    onKillsChanged: (Int, String) -> Unit,
    onSubmit: () -> Unit,
    onDiscard: () -> Unit,
    onBackPressed: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.match_correction_loading),
        )
        uiState.isNotFound -> MatchCorrectionNotFoundState(onBackPressed)
        uiState.isAvailable -> MatchCorrectionContent(
            uiState = uiState,
            onPlacementChanged = onPlacementChanged,
            onKillsChanged = onKillsChanged,
            onSubmit = onSubmit,
            onDiscard = onDiscard,
            onBackPressed = onBackPressed,
        )
    }
}

@Composable
private fun MatchCorrectionContent(
    uiState: MatchCorrectionUiState,
    onPlacementChanged: (Int, String) -> Unit,
    onKillsChanged: (Int, String) -> Unit,
    onSubmit: () -> Unit,
    onDiscard: () -> Unit,
    onBackPressed: () -> Unit,
) {
    var showSubmitConfirmation by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_CORRECTION_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.match_correction_title, uiState.matchNumber ?: 0),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_correction_instructions))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        uiState.rows.forEach { row ->
            MatchCorrectionRow(
                row = row,
                onPlacementChanged = onPlacementChanged,
                onKillsChanged = onKillsChanged,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        MatchCorrectionGlobalError(uiState.globalError)
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = { showSubmitConfirmation = true },
            enabled = uiState.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.submit_match_correction_action))
            }
        }
        TextButton(
            onClick = { showDiscardConfirmation = true },
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_CORRECTION_DISCARD_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.discard_match_correction_action))
        }
        TextButton(
            onClick = onBackPressed,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.back_to_match_review_action))
        }
    }

    if (showSubmitConfirmation) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmation = false },
            title = { Text(stringResource(R.string.submit_match_correction_title)) },
            text = { Text(stringResource(R.string.submit_match_correction_message)) },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmation = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubmitConfirmation = false
                        onSubmit()
                    },
                    modifier = Modifier.testTag(MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.confirm_submit_match_correction_action))
                }
            },
        )
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(R.string.discard_match_correction_title)) },
            text = { Text(stringResource(R.string.discard_match_correction_message)) },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onDiscard()
                    },
                    modifier = Modifier.testTag(MATCH_CORRECTION_DISCARD_CONFIRM_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.confirm_discard_match_correction_action))
                }
            },
        )
    }
}

@Composable
private fun MatchCorrectionRow(
    row: MatchCorrectionRowUiState,
    onPlacementChanged: (Int, String) -> Unit,
    onKillsChanged: (Int, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_CORRECTION_ROW_TEST_TAG_PREFIX + row.teamSlotNumber),
    ) {
        Text(
            text = stringResource(
                R.string.match_correction_team_label,
                row.teamSlotNumber,
                row.teamName.ifBlank { stringResource(R.string.empty_team_slot_subtitle) },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (row.playerNames.isNotEmpty()) {
            Text(stringResource(R.string.match_player_names_value, row.playerNames.joinToString()))
        }
        Text(
            stringResource(
                R.string.match_correction_previous_value,
                row.teamSlotNumber,
                row.previousPlacement,
                row.previousKills,
            ),
        )
        OutlinedTextField(
            value = row.placementInput,
            onValueChange = { onPlacementChanged(row.teamSlotNumber, it) },
            label = { Text(stringResource(R.string.match_correction_placement_field_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_CORRECTION_PLACEMENT_FIELD_TEST_TAG_PREFIX + row.teamSlotNumber),
            isError = row.validationErrors.any {
                it == MatchResultValidationError.MISSING_PLACEMENT ||
                    it == MatchResultValidationError.INVALID_PLACEMENT ||
                    it == MatchResultValidationError.DUPLICATE_PLACEMENT
            },
        )
        OutlinedTextField(
            value = row.killsInput,
            onValueChange = { onKillsChanged(row.teamSlotNumber, it) },
            label = { Text(stringResource(R.string.match_correction_kills_field_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX + row.teamSlotNumber),
            isError = row.validationErrors.any {
                it == MatchResultValidationError.MISSING_KILLS ||
                    it == MatchResultValidationError.INVALID_KILLS
            },
            supportingText = {
                row.validationErrors.sortedBy { it.ordinal }.forEach { error ->
                    Text(stringResource(error.toMessageRes()))
                }
            },
        )
    }
}

@Composable
private fun MatchCorrectionGlobalError(error: MatchCorrectionGlobalError?) {
    val message = when (error) {
        MatchCorrectionGlobalError.MATCH_NOT_FOUND -> stringResource(R.string.match_correction_match_not_found_error)
        MatchCorrectionGlobalError.MATCH_NOT_FINALIZED -> stringResource(R.string.match_correction_not_finalized_error)
        MatchCorrectionGlobalError.INVALID_DATA -> stringResource(R.string.match_correction_invalid_data_error)
        null -> null
    }
    if (message != null) Text(text = message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun MatchCorrectionNotFoundState(onBackPressed: () -> Unit) {
    RankForgeScreenContainer {
        Text(
            text = stringResource(R.string.match_correction_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_correction_not_found_message))
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackPressed) {
            Text(stringResource(R.string.back_to_match_review_action))
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
