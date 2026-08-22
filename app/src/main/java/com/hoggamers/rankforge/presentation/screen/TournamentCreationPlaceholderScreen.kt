package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentValidationError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tournamentDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

private val PointIqCreateNavy = Color(0xFF071B3E)
private val PointIqCreateBody = Color(0xFF607393)
private val PointIqCreateBlue = Color(0xFF176AF7)
private val PointIqCreateCyan = Color(0xFF17C9F2)
private val PointIqCreateBorder = Color(0xFFD6E3F4)
private val PointIqCreateBackgroundTop = Color(0xFFFDFEFF)
private val PointIqCreateBackgroundBottom = Color(0xFFF4FAFF)

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PointIqCreateBackgroundTop,
                        PointIqCreateBackgroundBottom,
                    ),
                ),
            )
            .testTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(
                start = 24.dp,
                top = 28.dp,
                end = 24.dp,
                bottom = 32.dp,
            ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.pointiq_tournament_creation_title),
            color = PointIqCreateNavy,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.pointiq_tournament_creation_description),
            color = PointIqCreateBody,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(28.dp))

        PointIqTournamentTextField(
            value = uiState.tournamentName,
            label = stringResource(R.string.tournament_name_label),
            error = uiState.validationErrors[TournamentField.NAME],
            onValueChange = onTournamentNameChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))

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
                singleLine = true,
                label = {
                    Text(
                        text = stringResource(R.string.tournament_date_label),
                    )
                },
                isError = uiState.validationErrors.containsKey(TournamentField.DATE),
                supportingText = uiState.validationErrors[TournamentField.DATE]?.let { error ->
                    {
                        ValidationText(error)
                    }
                },
                trailingIcon = {
                    TextButton(
                        onClick = openDatePicker,
                        modifier = Modifier.testTag(TOURNAMENT_DATE_TRAILING_ACTION_TEST_TAG),
                    ) {
                        Text(
                            text = stringResource(R.string.select_date_action),
                            color = PointIqCreateBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = pointIqTournamentFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PointIqTournamentTextField(
            value = uiState.organizerName,
            label = stringResource(R.string.organizer_name_label),
            error = uiState.validationErrors[TournamentField.ORGANIZER_NAME],
            onValueChange = onOrganizerNameChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))

        PointIqTournamentTextField(
            value = uiState.organizerContactNumber,
            label = stringResource(R.string.pointiq_contact_number_label),
            error = uiState.validationErrors[TournamentField.ORGANIZER_CONTACT_NUMBER],
            keyboardType = KeyboardType.Phone,
            onValueChange = onOrganizerContactNumberChanged,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.submissionError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(
                        when (uiState.submissionError) {
                            TournamentCreationSubmissionError.TOURNAMENT_LIMIT_REACHED ->
                                R.string.tournament_creation_limit_reached_error
                            TournamentCreationSubmissionError.QUOTA_CHECK_FAILED ->
                                R.string.tournament_creation_quota_check_error
                            TournamentCreationSubmissionError.AUTHENTICATION_REQUIRED ->
                                R.string.tournament_creation_authentication_required_error
                            TournamentCreationSubmissionError.UNKNOWN ->
                                R.string.tournament_creation_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        PointIqCreateTournamentButton(
            isSubmitting = uiState.isSubmitting,
            onClick = onSubmit,
        )
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
private fun PointIqTournamentTextField(
    value: String,
    label: String,
    error: TournamentValidationError?,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(text = label) },
        isError = error != null,
        supportingText = error?.let {
            {
                ValidationText(it)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = pointIqTournamentFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PointIqCreateTournamentButton(
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val gradientColors = if (isSubmitting) {
        listOf(
            PointIqCreateBlue.copy(alpha = 0.55f),
            PointIqCreateCyan.copy(alpha = 0.55f),
        )
    } else {
        listOf(PointIqCreateBlue, PointIqCreateCyan)
    }

    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.85f),
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = PointIqCreateBlue.copy(alpha = 0.12f),
                spotColor = PointIqCreateBlue.copy(alpha = 0.18f),
            )
            .background(
                brush = Brush.horizontalGradient(gradientColors),
                shape = shape,
            ),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.tournament_creation_submitting),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(
                text = stringResource(R.string.pointiq_create_tournament_action),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun pointIqTournamentFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PointIqCreateNavy,
    unfocusedTextColor = PointIqCreateNavy,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = PointIqCreateBlue,
    unfocusedBorderColor = PointIqCreateBorder,
    focusedLabelColor = PointIqCreateBlue,
    unfocusedLabelColor = PointIqCreateBody,
    cursorColor = PointIqCreateBlue,
)

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
