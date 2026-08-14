package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentValidationError
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

private val tournamentDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

const val TOURNAMENT_CREATION_SCREEN_TEST_TAG = "tournament_creation_screen"
const val TOURNAMENT_DATE_FIELD_TEST_TAG = "tournament_date_field"
const val TOURNAMENT_DATE_TRAILING_ACTION_TEST_TAG = "tournament_date_trailing_action"
const val TOURNAMENT_DATE_CONFIRM_ACTION_TEST_TAG = "tournament_date_confirm_action"

@Composable
fun TournamentCreationRoute(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: TournamentCreationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigation) {
        when (val navigation = uiState.navigation) {
            TournamentCreationNavigation.Back -> {
                viewModel.onNavigationHandled()
                onBack()
            }

            is TournamentCreationNavigation.Created -> {
                viewModel.onNavigationHandled()
                onCreated(navigation.tournamentId)
            }

            null -> Unit
        }
    }

    TournamentCreationScreen(
        uiState = uiState,
        onTournamentNameChanged = viewModel::onTournamentNameChanged,
        onTournamentDateChanged = viewModel::onTournamentDateChanged,
        onOrganizerNameChanged = viewModel::onOrganizerNameChanged,
        onOrganizerContactNumberChanged = viewModel::onOrganizerContactNumberChanged,
        onSubmit = viewModel::submit,
        onBackPressed = viewModel::onBackPressed,
        onKeepEditing = viewModel::keepEditing,
        onDiscardChanges = viewModel::discardChanges,
    )
}

@Composable
fun TournamentCreationScreen(
    uiState: TournamentCreationUiState,
    onTournamentNameChanged: (String) -> Unit,
    onTournamentDateChanged: (LocalDate) -> Unit,
    onOrganizerNameChanged: (String) -> Unit,
    onOrganizerContactNumberChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackPressed: () -> Unit,
    onKeepEditing: () -> Unit,
    onDiscardChanges: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val tournamentDateLabel = stringResource(R.string.tournament_date_label)
    val openDatePicker = { showDatePicker = true }

    BackHandler(enabled = uiState.navigation == null, onBack = onBackPressed)

    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG)
            .verticalScroll(scrollState),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.tournament_creation_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        TournamentTextField(
            value = uiState.tournamentName,
            label = stringResource(R.string.tournament_name_label),
            error = uiState.validationErrors[TournamentField.NAME],
            onValueChange = onTournamentNameChanged,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TOURNAMENT_DATE_FIELD_TEST_TAG)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null) {
                            openDatePicker()
                        }
                    }
                }
                .clickable(
                    role = Role.Button,
                    onClick = openDatePicker,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = tournamentDateLabel
                },
        ) {
            OutlinedTextField(
                value = uiState.tournamentDate?.format(tournamentDateFormatter).orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(text = stringResource(R.string.tournament_date_label)) },
                isError = uiState.validationErrors.containsKey(TournamentField.DATE),
                supportingText = {
                    ValidationText(uiState.validationErrors[TournamentField.DATE])
                },
                trailingIcon = {
                    TextButton(
                        onClick = openDatePicker,
                        modifier = Modifier.testTag(TOURNAMENT_DATE_TRAILING_ACTION_TEST_TAG),
                    ) {
                        Text(text = stringResource(R.string.select_date_action))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))

        TournamentTextField(
            value = uiState.organizerName,
            label = stringResource(R.string.organizer_name_label),
            error = uiState.validationErrors[TournamentField.ORGANIZER_NAME],
            onValueChange = onOrganizerNameChanged,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))

        TournamentTextField(
            value = uiState.organizerContactNumber,
            label = stringResource(R.string.organizer_contact_number_label),
            error = uiState.validationErrors[TournamentField.ORGANIZER_CONTACT_NUMBER],
            keyboardType = KeyboardType.Phone,
            onValueChange = onOrganizerContactNumberChanged,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        uiState.submissionError?.let { error ->
            val message = when (error) {
                TournamentCreationSubmissionError.CLOUD_SYNC_PENDING -> R.string.upload_tournament_queued
                TournamentCreationSubmissionError.UNKNOWN -> R.string.tournament_creation_error
            }
            Text(
                text = stringResource(message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        }

        Button(
            onClick = onSubmit,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.padding(horizontal = RankForgeSpacing.Small))
                Text(text = stringResource(R.string.tournament_creation_submitting))
            } else {
                Text(
                    text = stringResource(
                        if (uiState.cloudConfirmationPending) {
                            R.string.upload_tournament_action
                        } else {
                            R.string.create_tournament_action
                        },
                    ),
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (uiState.tournamentDate ?: LocalDate.now()).toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            onTournamentDateChanged(selectedDateMillis.toLocalDate())
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag(TOURNAMENT_DATE_CONFIRM_ACTION_TEST_TAG),
                ) {
                    Text(text = stringResource(R.string.select_date_action))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text(text = stringResource(R.string.discard_tournament_changes_title)) },
            text = { Text(text = stringResource(R.string.discard_tournament_changes_message)) },
            confirmButton = {
                TextButton(onClick = onDiscardChanges) {
                    Text(text = stringResource(R.string.discard_changes_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepEditing) {
                    Text(text = stringResource(R.string.keep_editing_action))
                }
            },
        )
    }
}

@Composable
private fun TournamentTextField(
    value: String,
    label: String,
    error: TournamentValidationError?,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        isError = error != null,
        supportingText = { ValidationText(error) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ValidationText(error: TournamentValidationError?) {
    if (error == null) return

    val message = when (error) {
        TournamentValidationError.REQUIRED -> stringResource(R.string.required_field_error)
        TournamentValidationError.PAST_DATE -> stringResource(R.string.past_date_error)
        TournamentValidationError.UNSUPPORTED_STATUS -> stringResource(R.string.unsupported_status_error)
    }
    Text(text = message)
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant
    .ofEpochMilli(this)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()
