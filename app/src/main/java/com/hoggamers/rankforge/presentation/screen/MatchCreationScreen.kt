package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.MatchField
import com.hoggamers.rankforge.domain.tournament.MatchValidationError
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

private val matchDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

const val MATCH_CREATION_SCREEN_TEST_TAG = "match_creation_screen"
const val MATCH_NUMBER_FIELD_TEST_TAG = "match_number_field"
const val MATCH_DATE_FIELD_TEST_TAG = "match_date_field"
const val MATCH_DATE_TRAILING_ACTION_TEST_TAG = "match_date_trailing_action"
const val MATCH_DATE_CONFIRM_ACTION_TEST_TAG = "match_date_confirm_action"
const val MATCH_MAP_FIELD_TEST_TAG = "match_map_field"
const val MATCH_CREATE_ACTION_TEST_TAG = "match_create_action"
const val MATCH_BACK_ACTION_TEST_TAG = "match_back_action"

@Composable
fun MatchCreationRoute(
    tournamentId: String,
    onBackToDetails: () -> Unit,
    viewModel: MatchCreationViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.navigation) {
        if (uiState.navigation != null) {
            viewModel.onNavigationHandled()
            onBackToDetails()
        }
    }
    BackHandler(onBack = viewModel::onBackPressed)

    MatchCreationScreen(
        uiState = uiState,
        onMatchNumberChanged = viewModel::onMatchNumberChanged,
        onMatchDateChanged = viewModel::onMatchDateChanged,
        onMapNameChanged = viewModel::onMapNameChanged,
        onSubmit = viewModel::submit,
        onBackPressed = viewModel::onBackPressed,
    )
}

@Composable
fun MatchCreationScreen(
    uiState: MatchCreationUiState,
    onMatchNumberChanged: (String) -> Unit,
    onMatchDateChanged: (LocalDate) -> Unit,
    onMapNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackPressed: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (uiState.matchDate ?: LocalDate.now()).toUtcMillis(),
    )

    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MATCH_CREATION_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.match_creation_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        OutlinedTextField(
            value = uiState.matchNumber,
            onValueChange = onMatchNumberChanged,
            label = { Text(stringResource(R.string.match_number_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag(MATCH_NUMBER_FIELD_TEST_TAG),
            isError = uiState.validationErrors.containsKey(MatchField.MATCH_NUMBER),
            supportingText = {
                MatchValidationText(uiState.validationErrors[MatchField.MATCH_NUMBER])
            },
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        OutlinedTextField(
            value = uiState.matchDate?.format(matchDateFormatter).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.match_date_label)) },
            trailingIcon = {
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.testTag(MATCH_DATE_TRAILING_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.select_date_action))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MATCH_DATE_FIELD_TEST_TAG)
                .clickable { showDatePicker = true },
            isError = uiState.validationErrors.containsKey(MatchField.DATE),
            supportingText = {
                MatchValidationText(uiState.validationErrors[MatchField.DATE])
            },
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        OutlinedTextField(
            value = uiState.mapName,
            onValueChange = onMapNameChanged,
            label = { Text(stringResource(R.string.match_map_label)) },
            modifier = Modifier.fillMaxWidth().testTag(MATCH_MAP_FIELD_TEST_TAG),
            isError = uiState.validationErrors.containsKey(MatchField.MAP),
            supportingText = {
                MatchValidationText(uiState.validationErrors[MatchField.MAP])
            },
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        if (uiState.submissionError != null) {
            Text(
                text = stringResource(R.string.match_creation_error),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }
        Button(
            onClick = onSubmit,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag(MATCH_CREATE_ACTION_TEST_TAG),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.create_match_action))
            }
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        TextButton(
            onClick = onBackPressed,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag(MATCH_BACK_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.back_to_tournament_details_action))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onMatchDateChanged(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag(MATCH_DATE_CONFIRM_ACTION_TEST_TAG),
                ) {
                    Text(stringResource(R.string.select_date_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.back_action))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun MatchValidationText(error: MatchValidationError?) {
    val message = when (error) {
        MatchValidationError.REQUIRED -> stringResource(R.string.required_field_error)
        MatchValidationError.INVALID -> stringResource(R.string.match_number_invalid_error)
        MatchValidationError.DUPLICATE -> stringResource(R.string.duplicate_match_number_error)
        MatchValidationError.TOURNAMENT_NOT_CONFIRMED -> stringResource(R.string.match_tournament_not_confirmed_error)
        MatchValidationError.LIMIT_REACHED -> stringResource(R.string.match_limit_reached_error)
        MatchValidationError.TOURNAMENT_NOT_FOUND -> stringResource(R.string.match_tournament_not_found_error)
        null -> null
    }
    if (message != null) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

