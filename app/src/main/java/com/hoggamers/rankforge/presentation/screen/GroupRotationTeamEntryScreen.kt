package com.hoggamers.rankforge.presentation.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState

private val GroupRotationPointIqBackground = Color(0xFF031225)
private val GroupRotationPointIqAmbientBlue = Color(0xFF0B386F)
private val GroupRotationPointIqHeader = Color(0xFFF6F8FF)
private val GroupRotationPointIqBody = Color(0xFF91AFE0)
private val GroupRotationPointIqBlue = Color(0xFF176AF7)
private val GroupRotationPointIqError = Color(0xFFFF6B6B)
private val GroupRotationPointIqSurface = Color(0xFF071B3E)

const val GROUP_ROTATION_TEAM_ENTRY_SCREEN_TEST_TAG = "group_rotation_team_entry_screen"
const val GROUP_ROTATION_TEAM_ENTRY_PAIRING_TEST_TAG_PREFIX = "group_rotation_team_entry_pairing_"
const val GROUP_ROTATION_TEAM_ENTRY_INPUT_TEST_TAG_PREFIX = "group_rotation_team_entry_input_"
const val GROUP_ROTATION_TEAM_ENTRY_UNIQUE_COUNT_TEST_TAG = "group_rotation_team_entry_unique_count"
const val GROUP_ROTATION_TEAM_ENTRY_LOAD_ERROR_TEST_TAG = "group_rotation_team_entry_load_error"
const val GROUP_ROTATION_TEAM_ENTRY_VALIDATION_ERROR_TEST_TAG = "group_rotation_team_entry_validation_error"
const val GROUP_ROTATION_TEAM_ENTRY_SAVE_ERROR_TEST_TAG = "group_rotation_team_entry_save_error"
const val GROUP_ROTATION_TEAM_ENTRY_CLOUD_ERROR_TEST_TAG = "group_rotation_team_entry_cloud_error"
const val GROUP_ROTATION_TEAM_ENTRY_PASTE_TEST_TAG = "group_rotation_team_entry_paste"
const val GROUP_ROTATION_TEAM_ENTRY_SAVE_TEST_TAG = "group_rotation_team_entry_save"

@Composable
fun GroupRotationTeamEntryRoute(
    tournamentId: String,
    onBackToDetails: () -> Unit,
    viewModel: GroupRotationTeamEntryViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    LaunchedEffect(viewModel.navigationEvents) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                GroupRotationTeamEntryNavigationEvent.BackToTournamentDetails -> onBackToDetails()
            }
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GroupRotationTeamEntryScreen(
        uiState = uiState,
        onBackToDetails = onBackToDetails,
        onPairingSelected = viewModel::onPairingSelected,
        onTeamNameChanged = viewModel::onTeamNameChanged,
        onBulkTeamNamesApplied = viewModel::onBulkTeamNamesApplied,
        onSave = viewModel::saveTeamNames,
    )
}

@Composable
fun GroupRotationTeamEntryScreen(
    uiState: GroupRotationTeamEntryUiState,
    onBackToDetails: () -> Unit,
    onPairingSelected: (com.hoggamers.rankforge.domain.tournament.GroupPairing) -> Unit,
    onTeamNameChanged: (com.hoggamers.rankforge.domain.tournament.GroupPairing, Int, String) -> Unit,
    onBulkTeamNamesApplied: (List<String>) -> Unit,
    onSave: () -> Unit,
) {
    if (uiState.isLoading) {
        GroupRotationPointIqSurface {
            Box(contentAlignment = Alignment.Center) {
                RankForgeLoadingState(message = stringResource(R.string.team_entry_loading))
            }
        }
        return
    }
    if (uiState.pairingSections.isEmpty()) {
        GroupRotationPointIqSurface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                GroupRotationBackAction(onClick = onBackToDetails)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = loadErrorMessage(uiState.loadError),
                    color = GroupRotationPointIqError,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_LOAD_ERROR_TEST_TAG),
                )
            }
        }
        return
    }

    var showPasteDialog by remember { mutableStateOf(false) }
    GroupRotationPointIqSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(GROUP_ROTATION_TEAM_ENTRY_SCREEN_TEST_TAG),
            horizontalAlignment = Alignment.Start,
        ) {
            GroupRotationBackAction(onClick = onBackToDetails)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.team_entry_title),
                color = GroupRotationPointIqHeader,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.group_rotation_team_entry_description),
                color = GroupRotationPointIqBody,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (uiState.loadError != null) {
                Text(
                    text = loadErrorMessage(uiState.loadError),
                    color = GroupRotationPointIqError,
                    modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_LOAD_ERROR_TEST_TAG),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.pairingSections.forEach { section ->
                    FilterChip(
                        selected = section.pairing.canonicalKey == uiState.selectedPairingKey,
                        onClick = { onPairingSelected(section.pairing) },
                        label = {
                            Text(
                                text = stringResource(
                                    R.string.group_rotation_pairing_completion,
                                    groupRotationPairingLabel(section.pairing),
                                    section.filledCount,
                                ),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = GroupRotationPointIqSurface,
                            labelColor = GroupRotationPointIqBody,
                            selectedContainerColor = GroupRotationPointIqBlue,
                            selectedLabelColor = GroupRotationPointIqHeader,
                        ),
                        modifier = Modifier.testTag(
                            GROUP_ROTATION_TEAM_ENTRY_PAIRING_TEST_TAG_PREFIX + section.pairing.canonicalKey,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(
                    R.string.group_rotation_unique_team_count,
                    uiState.uniqueTeamCount,
                    uiState.maximumUniqueTeams,
                ),
                color = GroupRotationPointIqBody,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_UNIQUE_COUNT_TEST_TAG),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { showPasteDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GroupRotationPointIqSurface,
                        contentColor = GroupRotationPointIqHeader,
                    ),
                    modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_PASTE_TEST_TAG),
                ) {
                    Text(text = stringResource(R.string.team_entry_paste_list_action))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            uiState.selectedPairing?.let { section ->
                Text(
                    text = groupRotationPairingLabel(section.pairing),
                    color = GroupRotationPointIqHeader,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                section.rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PointIqUnderlineTextField(
                            value = row.teamName,
                            placeholder = stringResource(R.string.group_rotation_team_name_placeholder),
                            fieldDescription = stringResource(
                                R.string.group_rotation_lobby_slot_team_name_label,
                                groupRotationPairingLabel(section.pairing),
                                row.lobbySlotNumber,
                            ),
                            onValueChange = { value ->
                                onTeamNameChanged(section.pairing, row.lobbySlotNumber, value)
                            },
                            leadingContent = { color ->
                                Text(
                                    text = row.lobbySlotNumber.toString().padStart(2, '0'),
                                    color = color,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    GROUP_ROTATION_TEAM_ENTRY_INPUT_TEST_TAG_PREFIX +
                                        "${section.pairing.canonicalKey}_${row.lobbySlotNumber}",
                                ),
                        )
                    }
                }
            }

            if (uiState.validationIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_VALIDATION_ERROR_TEST_TAG)) {
                    uiState.validationIssues
                        .map { issue -> validationMessage(issue, uiState.maximumUniqueTeams) }
                        .distinct()
                        .forEach { message ->
                            Text(text = message, color = GroupRotationPointIqError)
                        }
                }
            }
            uiState.saveError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = saveErrorMessage(error),
                    color = GroupRotationPointIqError,
                    modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_SAVE_ERROR_TEST_TAG),
                )
            }
            uiState.cloudSyncError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = cloudSyncErrorMessage(error),
                    color = GroupRotationPointIqError,
                    modifier = Modifier.testTag(GROUP_ROTATION_TEAM_ENTRY_CLOUD_ERROR_TEST_TAG),
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GroupRotationPointIqBlue,
                    contentColor = GroupRotationPointIqHeader,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GROUP_ROTATION_TEAM_ENTRY_SAVE_TEST_TAG),
            ) {
                Text(
                    text = if (uiState.isSaving) {
                        stringResource(R.string.group_rotation_saving_team_names)
                    } else {
                        stringResource(R.string.save_team_names_action)
                    },
                )
            }
        }
    }

    if (showPasteDialog) {
        GroupRotationPasteTeamListDialog(
            pairingLabel = uiState.selectedPairing?.let { groupRotationPairingLabel(it.pairing) }.orEmpty(),
            onDismissRequest = { showPasteDialog = false },
            onApply = { teamNames ->
                if (teamNames.isNotEmpty()) {
                    onBulkTeamNamesApplied(teamNames)
                    showPasteDialog = false
                }
            },
        )
    }
}

@Composable
private fun GroupRotationPointIqSurface(content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    DisposableEffect(window, view) {
        if (window == null) {
            onDispose { }
        } else {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
            window.statusBarColor = GroupRotationPointIqBackground.toArgb()
            window.navigationBarColor = GroupRotationPointIqBackground.toArgb()
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
                insetsController.isAppearanceLightStatusBars = previousLightStatusBars
                insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GroupRotationPointIqBackground)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GroupRotationPointIqAmbientBlue.copy(alpha = 0.42f),
                            GroupRotationPointIqAmbientBlue.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(-size.width * 0.04f, size.height * 0.34f),
                        radius = size.width * 0.78f,
                    ),
                )
            },
        content = content,
    )
}

@Composable
private fun GroupRotationBackAction(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = stringResource(R.string.back_to_tournament_details_action),
            color = GroupRotationPointIqBody,
        )
    }
}

@Composable
private fun GroupRotationPasteTeamListDialog(
    pairingLabel: String,
    onDismissRequest: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    var pastedText by remember { mutableStateOf("") }
    var hasOverflow by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.team_entry_paste_list_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.group_rotation_paste_list_description,
                        pairingLabel,
                    ),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = {
                        pastedText = it
                        hasOverflow = false
                    },
                    label = { Text(text = stringResource(R.string.team_entry_paste_list_label)) },
                    minLines = 6,
                    singleLine = false,
                )
                if (hasOverflow) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.team_entry_paste_list_overflow),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cancel_action))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = TeamListParser.parse(pastedText)
                    if (result.hasOverflow) hasOverflow = true else onApply(result.teamNames)
                },
            ) {
                Text(text = stringResource(R.string.team_entry_paste_list_apply_action))
            }
        },
    )
}

@Composable
private fun loadErrorMessage(error: GroupRotationTeamEntryLoadError?): String = when (error) {
    GroupRotationTeamEntryLoadError.AuthenticationRequired ->
        stringResource(R.string.group_rotation_authentication_required)
    GroupRotationTeamEntryLoadError.TournamentNotFound ->
        stringResource(R.string.group_rotation_tournament_not_found)
    GroupRotationTeamEntryLoadError.InvalidStoredSetup ->
        stringResource(R.string.group_rotation_invalid_stored_setup)
    GroupRotationTeamEntryLoadError.InvalidStoredDraft ->
        stringResource(R.string.group_rotation_invalid_stored_draft)
    null -> stringResource(R.string.group_rotation_setup_unavailable)
}

@Composable
private fun saveErrorMessage(error: GroupRotationTeamEntrySaveError): String = when (error) {
    GroupRotationTeamEntrySaveError.AuthenticationRequired ->
        stringResource(R.string.group_rotation_authentication_required)
    GroupRotationTeamEntrySaveError.TournamentNotFound ->
        stringResource(R.string.group_rotation_tournament_not_found)
    GroupRotationTeamEntrySaveError.ProtectedHistory ->
        stringResource(R.string.group_rotation_protected_history)
    GroupRotationTeamEntrySaveError.Unexpected ->
        stringResource(R.string.group_rotation_save_failure)
}

@Composable
private fun cloudSyncErrorMessage(error: GroupRotationTeamEntryCloudSyncError): String = when (error) {
    GroupRotationTeamEntryCloudSyncError.AuthenticationRequired ->
        stringResource(R.string.group_rotation_authentication_required)
    GroupRotationTeamEntryCloudSyncError.QueuePersistenceFailed ->
        stringResource(R.string.group_rotation_cloud_queue_persistence_failed)
    GroupRotationTeamEntryCloudSyncError.AuthorizationFailure,
    GroupRotationTeamEntryCloudSyncError.ValidationFailure,
    GroupRotationTeamEntryCloudSyncError.NetworkFailure,
    GroupRotationTeamEntryCloudSyncError.Conflict,
    GroupRotationTeamEntryCloudSyncError.TournamentLimitReached,
    GroupRotationTeamEntryCloudSyncError.PartialFailure,
    GroupRotationTeamEntryCloudSyncError.Unexpected,
    -> stringResource(R.string.group_rotation_cloud_sync_failed)
}

@Composable
private fun validationMessage(
    issue: GroupRotationTeamSetupIssue,
    maximumUniqueTeams: Int,
): String {
    val pairing = issue.pairingKey?.let { key ->
        try {
            GroupPairing.fromCanonicalKey(key)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    val pairingLabel = if (pairing != null) groupRotationPairingLabel(pairing) else ""
    return when (issue.code) {
        GroupRotationTeamSetupIssueCode.BLANK_TEAM_NAME ->
            stringResource(R.string.group_rotation_blank_team_validation, pairingLabel)
        GroupRotationTeamSetupIssueCode.DUPLICATE_TEAM_IDENTITY_WITHIN_PAIRING ->
            stringResource(R.string.group_rotation_duplicate_team_validation, pairingLabel)
        GroupRotationTeamSetupIssueCode.TOO_MANY_UNIQUE_TEAMS ->
            stringResource(R.string.group_rotation_too_many_teams_validation, maximumUniqueTeams)
        else -> stringResource(R.string.group_rotation_setup_data_validation)
    }
}
