package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgePageBackground
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

private val PointIqTeamsNavy = Color(0xFF071B3E)
private val PointIqTeamsBody = Color(0xFF607393)
private val PointIqTeamsBlue = Color(0xFF176AF7)
private val PointIqTeamsBorder = Color(0xFFD6E3F4)
private val PointIqTeamsCard = Color(0xFFFFFFFF)

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

        else -> {
            TeamEntryContent(
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
                hasTeamNameGap = uiState.hasTeamNameGap,
            )
        }
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
    hasTeamNameGap: Boolean,
) {
    val focusRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(focusSlotNumber) {
        if (focusSlotNumber != null) {
            focusRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RankForgePageBackground)
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
        Text(
            text = stringResource(R.string.team_entry_title),
            color = PointIqTeamsNavy,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.pointiq_team_entry_description),
            color = PointIqTeamsBody,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(22.dp))

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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFocusedSlot) {
                            Modifier.bringIntoViewRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    ),
                shape = RoundedCornerShape(16.dp),
                color = PointIqTeamsCard,
                border = BorderStroke(1.dp, PointIqTeamsBorder),
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.team_slot_label, slot.slotNumber),
                        color = PointIqTeamsNavy,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = slot.teamName,
                        onValueChange = { teamName ->
                            onTeamNameChanged(slot.slotNumber, teamName)
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    R.string.team_name_slot_label,
                                    slot.slotNumber,
                                ),
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PointIqTeamsBlue,
                            unfocusedBorderColor = PointIqTeamsBorder,
                            focusedLabelColor = PointIqTeamsBlue,
                            unfocusedLabelColor = PointIqTeamsBody,
                            focusedTextColor = PointIqTeamsNavy,
                            unfocusedTextColor = PointIqTeamsNavy,
                            cursorColor = PointIqTeamsBlue,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                        ),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + slot.slotNumber),
                        singleLine = true,
                    )

                    if (SHOW_TEAM_ENTRY_ROSTER_ACTIONS) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onEditRoster(slot.slotNumber) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PointIqTeamsBlue,
                            ),
                            border = BorderStroke(1.dp, PointIqTeamsBorder),
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier
                                .fillMaxWidth()
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

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onSave,
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = PointIqTeamsBlue,
                disabledContainerColor = PointIqTeamsBlue.copy(alpha = 0.45f),
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = stringResource(
                    if (isSaving) {
                        R.string.saving_team_names_action
                    } else {
                        R.string.save_team_names_action
                    },
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (hasSaveError) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.team_names_save_error),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBackToDetails,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PointIqTeamsNavy,
            ),
            border = BorderStroke(1.dp, PointIqTeamsBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text(
                text = stringResource(R.string.back_to_tournament_details_action),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
