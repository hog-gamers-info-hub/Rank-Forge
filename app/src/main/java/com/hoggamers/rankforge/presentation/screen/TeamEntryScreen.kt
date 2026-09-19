package com.hoggamers.rankforge.presentation.screen

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

private val PointIqTeamsBackground = Color(0xFF031225)
private val PointIqTeamsAmbientBlue = Color(0xFF0B386F)
private val PointIqTeamsNavy = Color(0xFFF6F8FF)
private val PointIqTeamsBody = Color(0xFF91AFE0)
private val PointIqTeamsBlue = Color(0xFF176AF7)
private val PointIqTeamsFieldText = Color(0xFFF6F8FF)
private val PointIqTeamsFieldInactive = Color(0xFF7D9DCE)
private val PointIqTeamsFieldCyan = Color(0xFF17C9F2)
private val PointIqTeamsDialogSurface = Color(0xFF071B3E)
private val PointIqTeamsDialogBorder = Color(0xFF176AF7)
private val PointIqTeamsDialogError = Color(0xFFFF6B6B)
private val PointIqTeamsDarkSurface = PointIqTeamsAmbientBlue.copy(alpha = 0.42f)
private val PointIqTeamsCtaDeepBlue = Color(0xFF0D4DBA)
private val PointIqTeamsCtaShadowLightBlue = Color(0xFF8EE7FF)
private val PointIqTeamsFieldHorizontalInset = 16.dp

const val TEAM_ENTRY_SCREEN_TEST_TAG = "team_entry_screen"
const val TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX = "team_entry_slot_input_"
const val TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX = "team_entry_roster_button_"
const val TEAM_ENTRY_TEAM_NAME_GAP_TEST_TAG = "team_entry_team_name_gap"
private const val SHOW_TEAM_ENTRY_VALIDATION_ISSUES = false
private const val SHOW_TEAM_ENTRY_ROSTER_ACTIONS = false
private const val SHOW_TEAM_ENTRY_OVERVIEW = false

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
    LaunchedEffect(viewModel.navigationEvents) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                TeamEntryNavigationEvent.BackToTournamentDetails -> onBackToDetails()
            }
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TeamEntryScreen(
        uiState = uiState,
        onTeamNameChanged = viewModel::onTeamNameChanged,
        onBulkTeamNamesApplied = viewModel::onBulkTeamNamesApplied,
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
    onBulkTeamNamesApplied: (List<String>) -> Unit,
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

        else -> {
            TeamEntryContent(
                slots = uiState.slots,
                onTeamNameChanged = onTeamNameChanged,
                onBulkTeamNamesApplied = onBulkTeamNamesApplied,
                onSave = onSave,
                onBackToDetails = onBackToDetails,
                onEditRoster = onEditRoster,
                onReviewRoster = onReviewRoster,
                focusSlotNumber = focusSlotNumber,
                isSaving = uiState.isSaving,
                hasSaveError = uiState.hasSaveError,
                validationIssues = uiState.validationIssues,
                hasTeamNameGap = uiState.hasTeamNameGap,
            )
        }
    }
}

@Composable
private fun TeamEntryContent(
    slots: List<TeamEntrySlotUiState>,
    onTeamNameChanged: (Int, String) -> Unit,
    onBulkTeamNamesApplied: (List<String>) -> Unit,
    onSave: () -> Unit,
    onBackToDetails: () -> Unit,
    onEditRoster: (Int) -> Unit,
    onReviewRoster: () -> Unit,
    focusSlotNumber: Int?,
    isSaving: Boolean,
    hasSaveError: Boolean,
    validationIssues: List<RosterValidationIssueUiState>,
    hasTeamNameGap: Boolean,
) {
    val focusRequester = remember { BringIntoViewRequester() }
    var isPasteTeamListDialogVisible by remember { mutableStateOf(false) }
    val backDescription = stringResource(R.string.back_action)
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

            window.statusBarColor = PointIqTeamsBackground.toArgb()
            window.navigationBarColor = PointIqTeamsBackground.toArgb()
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

    LaunchedEffect(focusSlotNumber) {
        if (focusSlotNumber != null) {
            focusRequester.bringIntoView()
        }
    }

        Column(
            modifier = Modifier
                .fillMaxSize()
            .background(PointIqTeamsBackground)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PointIqTeamsAmbientBlue.copy(alpha = 0.42f),
                            PointIqTeamsAmbientBlue.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(-size.width * 0.04f, size.height * 0.34f),
                        radius = size.width * 0.78f,
                    ),
                )
            }
            .testTag(TEAM_ENTRY_SCREEN_TEST_TAG)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = 28.dp,
                end = 24.dp,
                bottom = 32.dp,
            ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-8).dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(
                onClick = onBackToDetails,
                modifier = Modifier
                    .size(40.dp)
                    .semantics {
                        contentDescription = backDescription
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = backDescription,
                    tint = PointIqTeamsNavy,
                    modifier = Modifier
                        .size(32.dp)
                        .offset(y = (-6).dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(R.string.team_entry_title),
                        color = PointIqTeamsNavy,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = { isPasteTeamListDialogVisible = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PointIqTeamsDarkSurface,
                            contentColor = PointIqTeamsFieldCyan,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_team_entry_paste),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.team_entry_paste_list_action),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = stringResource(R.string.pointiq_team_entry_description),
                    color = PointIqTeamsBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        if (SHOW_TEAM_ENTRY_VALIDATION_ISSUES) {
            RosterValidationIssues(issues = validationIssues)
            if (validationIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (hasTeamNameGap) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEAM_ENTRY_TEAM_NAME_GAP_TEST_TAG),
            ) {
                Text(
                    text = stringResource(R.string.team_entry_gap_message),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        slots.forEach { slot ->
            val isFocusedSlot = slot.slotNumber == focusSlotNumber

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFocusedSlot) {
                            Modifier.bringIntoViewRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PointIqTeamNameField(
                    slotNumber = slot.slotNumber,
                    value = slot.teamName,
                    placeholder = stringResource(R.string.team_entry_team_name_placeholder),
                    fieldDescription = stringResource(
                        R.string.team_name_slot_label,
                        slot.slotNumber,
                    ),
                    onValueChange = { teamName ->
                        onTeamNameChanged(slot.slotNumber, teamName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PointIqTeamsFieldHorizontalInset)
                        .testTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + slot.slotNumber),
                )

                if (SHOW_TEAM_ENTRY_ROSTER_ACTIONS) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onEditRoster(slot.slotNumber) },
                        modifier = Modifier
                            .height(44.dp)
                            .testTag(
                                TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX + slot.slotNumber,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.enter_players_name_action),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (SHOW_TEAM_ENTRY_OVERVIEW) {
            Button(
                onClick = onReviewRoster,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PointIqTeamsNavy,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = stringResource(R.string.overview_team_details_action))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))
        PointIqSaveTeamsButton(
            isSaving = isSaving,
            onClick = onSave,
            modifier = Modifier.padding(horizontal = PointIqTeamsFieldHorizontalInset),
        )

        if (hasSaveError) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.team_names_save_error),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

    }

    if (isPasteTeamListDialogVisible) {
        PasteTeamListDialog(
            onDismissRequest = { isPasteTeamListDialogVisible = false },
            onApply = { teamNames ->
                onBulkTeamNamesApplied(teamNames)
                isPasteTeamListDialogVisible = false
            },
        )
    }
}

@Composable
private fun PointIqTeamNameField(
    slotNumber: Int,
    value: String,
    placeholder: String,
    fieldDescription: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PointIqUnderlineTextField(
        value = value,
        placeholder = placeholder,
        fieldDescription = fieldDescription,
        onValueChange = onValueChange,
        modifier = modifier,
        leadingContent = {
            Text(
                text = slotNumber.toString().padStart(2, '0'),
                color = it,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}

@Composable
internal fun PointIqUnderlineTextField(
    value: String,
    placeholder: String,
    fieldDescription: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable (Color) -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val textStyle = TextStyle(
        color = PointIqTeamsFieldText,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    )
    val placeholderStyle = TextStyle(
        color = PointIqTeamsFieldInactive,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(PointIqTeamsFieldCyan),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .semantics {
                contentDescription = fieldDescription
            },
        decorationBox = { innerTextField ->
            val lineColor = if (isFocused) PointIqTeamsFieldCyan else PointIqTeamsFieldInactive
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .drawBehind {
                        val strokeWidth = if (isFocused) 1.5.dp else 1.dp
                        val strokeWidthPx = strokeWidth.toPx()
                        val bottomY = size.height - 9.dp.toPx() - strokeWidthPx / 2f
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, bottomY),
                            end = Offset(size.width, bottomY),
                            strokeWidth = strokeWidthPx,
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingContent?.let { content ->
                        content(lineColor)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && !isFocused) {
                            Text(text = placeholder, style = placeholderStyle)
                        }
                        innerTextField()
                    }
                }
            }
        },
    )
}

@Composable
private fun PointIqSaveTeamsButton(
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val alpha = if (isSaving) 0.55f else 1f
    val gradientColors = listOf(
        PointIqTeamsFieldCyan.copy(alpha = alpha),
        PointIqTeamsBlue.copy(alpha = alpha),
        PointIqTeamsCtaDeepBlue.copy(alpha = alpha),
    )
    val edgeColor = PointIqTeamsFieldCyan.copy(alpha = if (isSaving) 0.55f else 0.72f)
    val glowAlpha = if (isSaving) 0.14f else 0.16f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 2.dp)
                .blur(
                    radius = 7.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(
                    color = PointIqTeamsCtaShadowLightBlue.copy(alpha = glowAlpha),
                    shape = shape,
                ),
        )
        Button(
            onClick = onClick,
            enabled = !isSaving,
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
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = stringResource(
                    if (isSaving) {
                        R.string.saving_team_names_action
                    } else {
                        R.string.save_team_names_action
                    },
                ),
                fontSize = if (isSaving) 15.sp else 16.sp,
                fontWeight = if (isSaving) FontWeight.SemiBold else FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PasteTeamListDialog(
    onDismissRequest: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    var pastedText by remember { mutableStateOf("") }
    var hasOverflow by remember { mutableStateOf(false) }
    val dialogShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PointIqTeamsFieldText,
        unfocusedTextColor = PointIqTeamsFieldText,
        focusedContainerColor = PointIqTeamsBackground,
        unfocusedContainerColor = PointIqTeamsBackground,
        focusedBorderColor = PointIqTeamsFieldCyan,
        unfocusedBorderColor = PointIqTeamsFieldInactive,
        focusedLabelColor = PointIqTeamsFieldCyan,
        unfocusedLabelColor = PointIqTeamsFieldInactive,
        cursorColor = PointIqTeamsFieldCyan,
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.border(
            BorderStroke(1.dp, PointIqTeamsDialogBorder),
            dialogShape,
        ),
        shape = dialogShape,
        containerColor = PointIqTeamsDialogSurface,
        titleContentColor = PointIqTeamsNavy,
        textContentColor = PointIqTeamsBody,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.team_entry_paste_list_title),
                color = PointIqTeamsNavy,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.team_entry_paste_list_description),
                    color = PointIqTeamsBody,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { updatedText ->
                        pastedText = updatedText
                        hasOverflow = false
                    },
                    label = {
                        Text(text = stringResource(R.string.team_entry_paste_list_label))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 6,
                    colors = fieldColors,
                )
                if (hasOverflow) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.team_entry_paste_list_overflow),
                        color = PointIqTeamsDialogError,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = stringResource(R.string.cancel_action),
                    color = PointIqTeamsBody,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = TeamListParser.parse(pastedText)
                    if (result.hasOverflow) {
                        hasOverflow = true
                    } else {
                        onApply(result.teamNames)
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.team_entry_paste_list_apply_action),
                    color = PointIqTeamsFieldCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
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
