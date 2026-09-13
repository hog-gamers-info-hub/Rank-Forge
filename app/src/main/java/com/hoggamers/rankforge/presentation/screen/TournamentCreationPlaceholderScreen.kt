package com.hoggamers.rankforge.presentation.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
private const val SHOW_ORGANIZER_CONTACT_NUMBER = false

private val PointIqCreateBackground = Color(0xFF031225)
private val PointIqCreateAmbientBlue = Color(0xFF0B386F)
private val PointIqCreateHeader = Color(0xFFF6F8FF)
private val PointIqCreateSubtitle = Color(0xFF91AFE0)
private val PointIqCreateFieldText = Color(0xFFF6F8FF)
private val PointIqCreateFieldInactive = Color(0xFF7D9DCE)
private val PointIqCreateBlue = Color(0xFF176AF7)
private val PointIqCreateCyan = Color(0xFF17C9F2)
private val PointIqCreateCtaDeepBlue = Color(0xFF0D4DBA)
private val PointIqCreateCtaShadowLightBlue = Color(0xFF8EE7FF)
private val PointIqCreateFieldHorizontalInset = 24.dp

const val TOURNAMENT_CREATION_SCREEN_TEST_TAG = "tournament_creation_screen"
const val TOURNAMENT_DATE_FIELD_TEST_TAG = "tournament_date_field"
const val TOURNAMENT_DATE_TRAILING_ACTION_TEST_TAG = "tournament_date_trailing_action"
const val TOURNAMENT_DATE_CONFIRM_ACTION_TEST_TAG = "tournament_date_confirm_action"
const val TOURNAMENT_GAME_DROPDOWN_TEST_TAG = "tournament_game_dropdown"
const val TOURNAMENT_GAME_OPTION_FREE_FIRE_MAX_TEST_TAG = "tournament_game_option_free_fire_max"
const val TOURNAMENT_MODE_DROPDOWN_TEST_TAG = "tournament_mode_dropdown"
const val TOURNAMENT_MODE_OPTION_SOLO_TEST_TAG = "tournament_mode_option_solo"
const val TOURNAMENT_MODE_OPTION_DUO_TEST_TAG = "tournament_mode_option_duo"
const val TOURNAMENT_MODE_OPTION_SQUAD_TEST_TAG = "tournament_mode_option_squad"

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
    val freeFireMax = stringResource(R.string.tournament_game_free_fire_max)
    val solo = stringResource(R.string.tournament_mode_solo)
    val duo = stringResource(R.string.tournament_mode_duo)
    val squad = stringResource(R.string.tournament_mode_squad)
    var selectedGame by rememberSaveable { mutableStateOf(freeFireMax) }
    var selectedMode by rememberSaveable { mutableStateOf(squad) }
    val scrollState = rememberScrollState()
    val tournamentDateLabel = stringResource(R.string.tournament_date_label)
    val backDescription = stringResource(R.string.back_action)
    val openDatePicker = { showDatePicker = true }
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    DisposableEffect(window, view) {
        if (window == null) {
            onDispose { }
        } else {
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = windowInsetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = windowInsetsController.isAppearanceLightNavigationBars

            window.statusBarColor = PointIqCreateBackground.toArgb()
            window.navigationBarColor = PointIqCreateBackground.toArgb()
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false

            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
                windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                windowInsetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    BackHandler(enabled = uiState.navigation == null, onBack = onBackPressed)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PointIqCreateBackground)
            .testTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG)
    ) {
        PointIqCreateBackgroundDecoration(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    top = 28.dp,
                    end = 16.dp,
                    bottom = 32.dp,
                ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(
                    onClick = onBackPressed,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = backDescription
                        },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = backDescription,
                        tint = PointIqCreateHeader,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Column(
                    modifier = Modifier.padding(start = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pointiq_tournament_creation_title),
                        color = PointIqCreateHeader,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = stringResource(R.string.pointiq_tournament_creation_description)
                            .replace("set up", "create"),
                        color = PointIqCreateSubtitle,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(44.dp))

            PointIqTournamentField(
                value = uiState.tournamentName,
                label = stringResource(R.string.tournament_name_label),
                error = uiState.validationErrors[TournamentField.NAME],
                onValueChange = onTournamentNameChanged,
                leadingIcon = { tint ->
                    Icon(
                        painter = painterResource(R.drawable.ic_tournament_trophy),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))

            PointIqTournamentSelectionField(
                value = selectedGame,
                label = stringResource(R.string.tournament_game_label),
                options = listOf(freeFireMax),
                isOptionEnabled = { true },
                onOptionSelected = { selectedGame = it },
                enabled = false,
                fieldTestTag = TOURNAMENT_GAME_DROPDOWN_TEST_TAG,
                optionTestTag = { TOURNAMENT_GAME_OPTION_FREE_FIRE_MAX_TEST_TAG },
                leadingIcon = { tint ->
                    Icon(
                        painter = painterResource(R.drawable.ic_tournament_game),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))

            PointIqTournamentSelectionField(
                value = selectedMode,
                label = stringResource(R.string.tournament_mode_label),
                options = listOf(solo, duo, squad),
                isOptionEnabled = { it == squad },
                onOptionSelected = { selectedMode = it },
                enabled = false,
                fieldTestTag = TOURNAMENT_MODE_DROPDOWN_TEST_TAG,
                optionTestTag = { option ->
                    when (option) {
                        solo -> TOURNAMENT_MODE_OPTION_SOLO_TEST_TAG
                        duo -> TOURNAMENT_MODE_OPTION_DUO_TEST_TAG
                        else -> TOURNAMENT_MODE_OPTION_SQUAD_TEST_TAG
                    }
                },
                leadingIcon = { tint ->
                    Icon(
                        painter = painterResource(R.drawable.ic_tournament_groups),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))

            PointIqTournamentDateField(
                value = uiState.tournamentDate?.format(tournamentDateFormatter).orEmpty(),
                error = uiState.validationErrors[TournamentField.DATE],
                onOpenDatePicker = openDatePicker,
                fieldDescription = tournamentDateLabel,
            )
            Spacer(modifier = Modifier.height(16.dp))

            PointIqTournamentField(
                value = uiState.organizerName,
                label = stringResource(R.string.organizer_name_label),
                error = uiState.validationErrors[TournamentField.ORGANIZER_NAME],
                onValueChange = onOrganizerNameChanged,
                leadingIcon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            if (SHOW_ORGANIZER_CONTACT_NUMBER) {
                Spacer(modifier = Modifier.height(16.dp))
                PointIqTournamentField(
                    value = uiState.organizerContactNumber,
                    label = stringResource(R.string.pointiq_contact_number_label),
                    error = uiState.validationErrors[TournamentField.ORGANIZER_CONTACT_NUMBER],
                    keyboardType = KeyboardType.Phone,
                    onValueChange = onOrganizerContactNumberChanged,
                    leadingIcon = { tint ->
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
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
private fun PointIqCreateBackgroundDecoration(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    PointIqCreateAmbientBlue.copy(alpha = 0.42f),
                    PointIqCreateAmbientBlue.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                center = Offset(-size.width * 0.04f, size.height * 0.34f),
                radius = size.width * 0.78f,
            ),
        )
    }
}

@Composable
private fun PointIqTournamentField(
    value: String,
    label: String,
    error: TournamentValidationError?,
    onValueChange: (String) -> Unit,
    leadingIcon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isFocusedOverride: Boolean? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val visualFocused = isFocusedOverride ?: isFocused
    val fieldHeight = 48.dp
    val contentHorizontalPadding = 16.dp
    val contentLeadingPadding = contentHorizontalPadding / 2
    val iconSize = 24.dp
    val iconTextGap = 12.dp
    val floatingLabelFontSize = 12.sp
    val floatingLabelLineHeight = 16.sp
    val valueFontSize = 18.sp
    val valueLineHeight = 22.sp
    val bottomBorderInset = 9.dp
    val focusedStrokeWidth = 1.5.dp
    val inactiveStrokeWidth = 1.dp
    val textStyle = TextStyle(
        color = PointIqCreateFieldText,
        fontSize = valueFontSize,
        lineHeight = valueLineHeight,
    )
    val labelStyle = TextStyle(
        color = if (visualFocused) PointIqCreateCyan else PointIqCreateFieldInactive,
        fontSize = floatingLabelFontSize,
        lineHeight = floatingLabelLineHeight,
        fontWeight = FontWeight.Medium,
    )
    val placeholderStyle = TextStyle(
        color = PointIqCreateFieldInactive,
        fontSize = valueFontSize,
        lineHeight = valueLineHeight,
        fontWeight = FontWeight.Normal,
    )
    val textMeasurer = rememberTextMeasurer()
    val labelLayout = textMeasurer.measure(label, labelStyle)
    val labelGlyphCenterPx = if (label.isEmpty()) {
        labelLayout.size.height / 2f
    } else {
        var glyphTop = Float.POSITIVE_INFINITY
        var glyphBottom = Float.NEGATIVE_INFINITY
        label.indices.forEach { index ->
            val glyphBounds = labelLayout.getBoundingBox(index)
            glyphTop = minOf(glyphTop, glyphBounds.top)
            glyphBottom = maxOf(glyphBottom, glyphBounds.bottom)
        }
        (glyphTop + glyphBottom) / 2f
    }
    val labelBorderY = (if (visualFocused) focusedStrokeWidth else inactiveStrokeWidth) / 2f
    val labelOffsetY = with(LocalDensity.current) {
        (labelBorderY.toPx() - labelGlyphCenterPx).toDp()
    }
    val validationMessage = error?.let { validationErrorMessage(it) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = readOnly,
            enabled = enabled,
            textStyle = textStyle,
            cursorBrush = SolidColor(PointIqCreateCyan),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = PointIqCreateFieldHorizontalInset)
                .align(Alignment.CenterHorizontally)
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .semantics(mergeDescendants = true) {},
            decorationBox = { innerTextField ->
                val floating = visualFocused || value.isNotEmpty()
                val lineColor = when {
                    error != null -> MaterialTheme.colorScheme.error
                    visualFocused -> PointIqCreateCyan
                    else -> PointIqCreateFieldInactive
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .drawBehind {
                            val borderWidthDp = if (visualFocused) focusedStrokeWidth else inactiveStrokeWidth
                            val borderWidth = borderWidthDp.toPx()
                            val contentRowBottom = fieldHeight / 2 + iconSize / 2
                            val currentBottomSpace = contentRowBottom.let { rowBottom ->
                                fieldHeight - bottomBorderInset - borderWidthDp - rowBottom
                            }
                            val bottomY = contentRowBottom.toPx() +
                                currentBottomSpace.toPx() * 1.5f +
                                borderWidth / 2f
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, bottomY),
                                end = Offset(size.width, bottomY),
                                strokeWidth = borderWidth,
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(start = contentLeadingPadding, end = contentHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leadingIcon(lineColor)
                        Spacer(modifier = Modifier.width(iconTextGap))
                        Box(modifier = Modifier.weight(1f)) {
                            if (!floating) {
                                Text(
                                    text = label,
                                    style = placeholderStyle,
                                )
                            }
                            innerTextField()
                        }
                        trailingContent?.invoke()
                    }

                    if (floating) {
                        Text(
                                text = label,
                                style = labelStyle,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = contentLeadingPadding)
                                    .offset(y = labelOffsetY)
                                    .background(Color.Transparent),
                        )
                    }
                }
            },
        )

        if (validationMessage != null) {
            Text(
                text = validationMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PointIqCreateFieldHorizontalInset)
                    .align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun validationErrorMessage(error: TournamentValidationError): String = when (error) {
    TournamentValidationError.REQUIRED -> stringResource(R.string.required_field_error)
    TournamentValidationError.PAST_DATE -> stringResource(R.string.past_date_error)
    TournamentValidationError.UNSUPPORTED_STATUS -> stringResource(R.string.unsupported_status_error)
}

@Composable
private fun PointIqTournamentDateField(
    value: String,
    error: TournamentValidationError?,
    onOpenDatePicker: () -> Unit,
    fieldDescription: String,
) {
    val dateLabel = stringResource(R.string.tournament_date_label)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_DATE_TRAILING_ACTION_TEST_TAG)
            .clickable(
                role = Role.Button,
                onClick = onOpenDatePicker,
            )
            .semantics {
                contentDescription = fieldDescription
            },
    ) {
        PointIqTournamentField(
            value = value,
            onValueChange = {},
            label = dateLabel,
            error = error,
            leadingIcon = { tint ->
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            },
            readOnly = true,
            enabled = false,
            isFocusedOverride = false,
            modifier = Modifier.testTag(TOURNAMENT_DATE_FIELD_TEST_TAG),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointIqTournamentSelectionField(
    value: String,
    label: String,
    options: List<String>,
    isOptionEnabled: (String) -> Boolean,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    fieldTestTag: String,
    optionTestTag: (String) -> String,
    leadingIcon: @Composable (Color) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = enabled && expanded

    LaunchedEffect(enabled) {
        if (!enabled) {
            expanded = false
        }
    }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { shouldExpand ->
            expanded = enabled && shouldExpand
        },
    ) {
        PointIqTournamentField(
            value = value,
            onValueChange = {},
            label = label,
            error = null,
            leadingIcon = leadingIcon,
            readOnly = true,
            enabled = enabled,
            isFocusedOverride = isExpanded,
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isExpanded) PointIqCreateCyan else PointIqCreateFieldInactive,
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier = Modifier
                .menuAnchor()
                .testTag(fieldTestTag),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        if (enabled) {
                            onOptionSelected(option)
                        }
                        expanded = false
                    },
                    enabled = enabled && isOptionEnabled(option),
                    modifier = Modifier.testTag(optionTestTag(option)),
                )
            }
        }
    }
}

@Composable
private fun PointIqCreateTournamentButton(
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val enabledAlpha = if (isSubmitting) 0.55f else 1f
    val gradientColors = if (isSubmitting) {
        listOf(
            PointIqCreateCyan.copy(alpha = enabledAlpha),
            PointIqCreateBlue.copy(alpha = enabledAlpha),
            PointIqCreateCtaDeepBlue.copy(alpha = enabledAlpha),
        )
    } else {
        listOf(PointIqCreateCyan, PointIqCreateBlue, PointIqCreateCtaDeepBlue)
    }
    val edgeColor = PointIqCreateCyan.copy(alpha = if (isSubmitting) 0.55f else 0.72f)
    val glowAlpha = if (isSubmitting) 0.14f else 0.16f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PointIqCreateFieldHorizontalInset)
            .height(48.dp)
    ) {
        PointIqCreateCtaGlowLayer(
            alpha = glowAlpha,
            offsetY = 2.dp,
        )

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
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to gradientColors[0],
                            0.52f to gradientColors[1],
                            1f to gradientColors[2],
                        ),
                    ),
                    shape = shape,
                )
                .border(width = 1.dp, color = edgeColor, shape = shape),
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
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun PointIqCreateCtaGlowLayer(
    alpha: Float,
    offsetY: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = offsetY)
            .blur(
                radius = 7.dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            )
            .background(
                color = PointIqCreateCtaShadowLightBlue.copy(alpha = alpha),
                shape = RoundedCornerShape(18.dp),
            ),
    )
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant
    .ofEpochMilli(this)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()
