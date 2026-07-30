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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

object MatchOcrReviewTestTags {
    const val SCREEN = "match_ocr_review_screen"
    const val LOADING = "match_ocr_review_loading"
    const val EMPTY = "match_ocr_review_empty"
    const val ERROR = "match_ocr_review_error"
    const val READY_CONTENT = "match_ocr_review_ready_content"
    const val ROW_LIST = "match_ocr_review_row_list"
    const val BACK_ACTION = "match_ocr_review_back_action"
    const val CORRECTION_ROOT = "match_ocr_review_correction_root"
    const val RESET_ALL = "match_ocr_review_reset_all"
    private const val ROW_PREFIX = "match_ocr_review_row_"

    fun row(rowIndex: Int): String = ROW_PREFIX + rowIndex
    fun placement(rowIndex: Int): String = "${row(rowIndex)}_placement"
    fun playerName(rowIndex: Int): String = "${row(rowIndex)}_player_name"
    fun kills(rowIndex: Int): String = "${row(rowIndex)}_kills"
    fun suggestions(rowIndex: Int): String = "${row(rowIndex)}_suggestions"
    fun confidence(rowIndex: Int): String = "${row(rowIndex)}_confidence"
    fun safety(rowIndex: Int): String = "${row(rowIndex)}_safety"
    fun warning(rowIndex: Int): String = "${row(rowIndex)}_warning"
    fun blocking(rowIndex: Int): String = "${row(rowIndex)}_blocking"
    fun placementInput(rowIndex: Int): String = "${row(rowIndex)}_placement_input"
    fun killsInput(rowIndex: Int): String = "${row(rowIndex)}_kills_input"
    fun teamSlotInput(rowIndex: Int): String = "${row(rowIndex)}_team_slot_input"
    fun rowDirty(rowIndex: Int): String = "${row(rowIndex)}_dirty"
    fun rowBlocker(rowIndex: Int): String = "${row(rowIndex)}_blocker"
    fun rowWarning(rowIndex: Int): String = "${row(rowIndex)}_warning_label"
    fun resetRow(rowIndex: Int): String = "${row(rowIndex)}_reset"
}

@Composable
fun MatchOcrReviewRoute(
    tournamentId: String,
    matchId: String,
    onBack: () -> Unit,
    viewModel: MatchOcrReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId, matchId) {
        viewModel.load(tournamentId, matchId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    MatchOcrReviewScreen(
        uiState = uiState,
        onBack = onBack,
        onPlacementChanged = viewModel::onPlacementChanged,
        onKillsChanged = viewModel::onKillsChanged,
        onAssignedTeamSlotChanged = viewModel::onAssignedTeamSlotChanged,
        onResetRowCorrection = viewModel::onResetRowCorrection,
        onResetAllCorrections = viewModel::onResetAllCorrections,
    )
}

@Composable
fun MatchOcrReviewScreen(
    uiState: MatchOcrReviewUiState,
    onBack: () -> Unit,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onKillsChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onResetRowCorrection: (rowIndex: Int) -> Unit = {},
    onResetAllCorrections: () -> Unit = {},
) {
    when (uiState) {
        MatchOcrReviewUiState.Loading -> MatchOcrReviewLoadingState()
        is MatchOcrReviewUiState.Empty -> MatchOcrReviewEmptyState(onBack)
        is MatchOcrReviewUiState.Error -> MatchOcrReviewErrorState(uiState, onBack)
        is MatchOcrReviewUiState.Ready -> MatchOcrReviewReadyState(
            uiState = uiState,
            onBack = onBack,
            onPlacementChanged = onPlacementChanged,
            onKillsChanged = onKillsChanged,
            onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
            onResetRowCorrection = onResetRowCorrection,
            onResetAllCorrections = onResetAllCorrections,
        )
    }
}

@Composable
private fun MatchOcrReviewLoadingState() {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(MatchOcrReviewTestTags.SCREEN),
    ) {
        Text(
            text = stringResource(R.string.match_ocr_review_loading),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(MatchOcrReviewTestTags.LOADING),
        )
    }
}

@Composable
private fun MatchOcrReviewEmptyState(onBack: () -> Unit) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(MatchOcrReviewTestTags.SCREEN),
    ) {
        Text(
            text = stringResource(R.string.match_ocr_review_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(MatchOcrReviewTestTags.EMPTY),
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.match_ocr_review_empty_message))
        MatchOcrReviewBackAction(onBack)
    }
}

@Composable
private fun MatchOcrReviewErrorState(
    uiState: MatchOcrReviewUiState.Error,
    onBack: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(MatchOcrReviewTestTags.SCREEN),
    ) {
        Text(
            text = stringResource(R.string.match_ocr_review_error_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(MatchOcrReviewTestTags.ERROR),
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = uiState.message)
        MatchOcrReviewBackAction(onBack)
    }
}

@Composable
private fun MatchOcrReviewReadyState(
    uiState: MatchOcrReviewUiState.Ready,
    onBack: () -> Unit,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onResetRowCorrection: (rowIndex: Int) -> Unit,
    onResetAllCorrections: () -> Unit,
) {
    val correctionRowsByIndex = uiState.correctionDraft?.rows.orEmpty().associateBy { it.rowIndex }
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(MatchOcrReviewTestTags.SCREEN)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.READY_CONTENT),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.match_ocr_review_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    R.string.match_ocr_review_match_value,
                    uiState.matchDisplayLabel ?: uiState.matchId,
                ),
            )
            Text(text = stringResource(R.string.match_ocr_review_row_count_value, uiState.rowCount))
            Text(
                text = stringResource(
                    R.string.match_ocr_review_summary_value,
                    uiState.blockerCount,
                    uiState.warningCount,
                    if (uiState.manualReviewRequired) {
                        stringResource(R.string.match_ocr_review_yes)
                    } else {
                        stringResource(R.string.match_ocr_review_no)
                    },
                ),
            )
            Text(
                text = stringResource(
                    R.string.match_ocr_review_safety_summary_value,
                    uiState.safeRowCount,
                    uiState.reviewRequiredRowCount,
                    uiState.manualRequiredRowCount,
                ),
            )
            if (uiState.hasUnavailableEvidence) {
                Text(
                    text = stringResource(R.string.match_ocr_review_unavailable_evidence_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            uiState.correctionDraft?.let { correctionDraft ->
                MatchOcrReviewCorrectionSummary(
                    correctionDraft = correctionDraft,
                    onResetAllCorrections = onResetAllCorrections,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MatchOcrReviewTestTags.ROW_LIST),
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
            ) {
                uiState.rows.forEach { row ->
                    MatchOcrReviewRow(
                        row = row,
                        correctionDraft = correctionRowsByIndex[row.rowIndex],
                        onPlacementChanged = onPlacementChanged,
                        onKillsChanged = onKillsChanged,
                        onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                        onResetRowCorrection = onResetRowCorrection,
                    )
                }
            }
            MatchOcrReviewBackAction(onBack)
        }
    }
}

@Composable
private fun MatchOcrReviewCorrectionSummary(
    correctionDraft: MatchOcrReviewCorrectionDraft,
    onResetAllCorrections: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.CORRECTION_ROOT),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        Text(
            text = stringResource(
                R.string.match_ocr_review_correction_summary_value,
                correctionDraft.blockerCount,
                correctionDraft.warningCount,
                if (correctionDraft.isDirty) {
                    stringResource(R.string.match_ocr_review_yes)
                } else {
                    stringResource(R.string.match_ocr_review_no)
                },
                stringResource(correctionDraft.status.toMessageRes()),
            ),
        )
        if (correctionDraft.isDirty) {
            Text(text = stringResource(R.string.match_ocr_review_correction_dirty_summary))
        }
        Button(
            onClick = onResetAllCorrections,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.RESET_ALL),
        ) {
            Text(text = stringResource(R.string.match_ocr_review_reset_all_action))
        }
    }
}

@Composable
private fun MatchOcrReviewRow(
    row: MatchOcrReviewRowUiState,
    correctionDraft: MatchOcrReviewRowCorrectionDraft?,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onResetRowCorrection: (rowIndex: Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.row(row.rowIndex)),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        Text(
            text = stringResource(
                R.string.match_ocr_review_row_title,
                row.rowIndex + 1,
                row.expectedPlacementLabel,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.match_ocr_review_placement_value,
                row.detectedPlacementDisplayValue,
                row.placementStatusLabel,
            ),
            modifier = Modifier.testTag(MatchOcrReviewTestTags.placement(row.rowIndex)),
        )
        Text(
            text = stringResource(
                R.string.match_ocr_review_kills_value,
                row.detectedKillDisplayValue,
                row.killStatusLabel,
            ),
            modifier = Modifier.testTag(MatchOcrReviewTestTags.kills(row.rowIndex)),
        )
        Text(
            text = stringResource(
                R.string.match_ocr_review_player_name_value,
                row.detectedPlayerNameEvidenceLabel,
                row.playerNameStatusLabel,
            ),
            modifier = Modifier.testTag(MatchOcrReviewTestTags.playerName(row.rowIndex)),
        )
        Text(
            text = stringResource(
                R.string.match_ocr_review_confidence_value,
                row.confidenceScoreDisplayValue,
                row.confidenceTierLabel,
            ),
            modifier = Modifier.testTag(MatchOcrReviewTestTags.confidence(row.rowIndex)),
        )
        Text(
            text = stringResource(
                R.string.match_ocr_review_safety_value,
                row.suggestedTeamSlotDisplayValue,
                row.assignmentSafetyStatusLabel,
            ),
            modifier = Modifier.testTag(MatchOcrReviewTestTags.safety(row.rowIndex)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.suggestions(row.rowIndex)),
        ) {
            Text(text = stringResource(R.string.match_ocr_review_suggestions_title))
            row.topThreeSuggestionsSummary.forEach { suggestion ->
                Text(text = suggestion)
            }
        }
        if (row.warningLabels.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MatchOcrReviewTestTags.warning(row.rowIndex)),
            ) {
                Text(
                    text = stringResource(R.string.match_ocr_review_warnings_title),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                row.warningLabels.forEach { warning ->
                    Text(text = stringResource(R.string.match_ocr_review_warning_value, warning))
                }
            }
        }
        if (row.blockerLabels.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MatchOcrReviewTestTags.blocking(row.rowIndex)),
            ) {
                Text(
                    text = stringResource(R.string.match_ocr_review_blockers_title),
                    color = MaterialTheme.colorScheme.error,
                )
                row.blockerLabels.forEach { blocker ->
                    Text(text = stringResource(R.string.match_ocr_review_blocker_value, blocker))
                }
            }
        }
        if (correctionDraft != null) {
            MatchOcrReviewCorrectionFields(
                correctionDraft = correctionDraft,
                onPlacementChanged = onPlacementChanged,
                onKillsChanged = onKillsChanged,
                onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                onResetRowCorrection = onResetRowCorrection,
            )
        }
    }
}

@Composable
private fun MatchOcrReviewCorrectionFields(
    correctionDraft: MatchOcrReviewRowCorrectionDraft,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onResetRowCorrection: (rowIndex: Int) -> Unit,
) {
    OutlinedTextField(
        value = correctionDraft.placementDraftValue,
        onValueChange = { onPlacementChanged(correctionDraft.rowIndex, it) },
        label = { Text(text = stringResource(R.string.match_ocr_review_correction_placement_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = correctionDraft.validation.blockers.any {
            it == MatchOcrReviewCorrectionReason.MISSING_PLACEMENT ||
                it == MatchOcrReviewCorrectionReason.INVALID_PLACEMENT ||
                it == MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.placementInput(correctionDraft.rowIndex)),
    )
    OutlinedTextField(
        value = correctionDraft.killsDraftValue,
        onValueChange = { onKillsChanged(correctionDraft.rowIndex, it) },
        label = { Text(text = stringResource(R.string.match_ocr_review_correction_kills_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = correctionDraft.validation.blockers.any {
            it == MatchOcrReviewCorrectionReason.MISSING_KILLS ||
                it == MatchOcrReviewCorrectionReason.INVALID_KILLS ||
                it == MatchOcrReviewCorrectionReason.NEGATIVE_KILLS
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.killsInput(correctionDraft.rowIndex)),
    )
    OutlinedTextField(
        value = correctionDraft.assignedTeamSlotDraftValue,
        onValueChange = { onAssignedTeamSlotChanged(correctionDraft.rowIndex, it) },
        label = { Text(text = stringResource(R.string.match_ocr_review_correction_team_slot_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = correctionDraft.validation.blockers.any {
            it == MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT ||
                it == MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT ||
                it == MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.teamSlotInput(correctionDraft.rowIndex)),
    )
    if (correctionDraft.isDirty) {
        Text(
            text = stringResource(R.string.match_ocr_review_correction_dirty_row),
            modifier = Modifier.testTag(MatchOcrReviewTestTags.rowDirty(correctionDraft.rowIndex)),
        )
    }
    if (correctionDraft.validation.blockers.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.rowBlocker(correctionDraft.rowIndex)),
        ) {
            Text(
                text = stringResource(R.string.match_ocr_review_correction_blockers_title),
                color = MaterialTheme.colorScheme.error,
            )
            correctionDraft.validation.blockers.sortedBy { it.ordinal }.forEach { blocker ->
                Text(text = stringResource(R.string.match_ocr_review_blocker_value, stringResource(blocker.toMessageRes())))
            }
        }
    }
    if (correctionDraft.validation.warnings.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.rowWarning(correctionDraft.rowIndex)),
        ) {
            Text(
                text = stringResource(R.string.match_ocr_review_correction_warnings_title),
                color = MaterialTheme.colorScheme.tertiary,
            )
            correctionDraft.validation.warnings.sortedBy { it.ordinal }.forEach { warning ->
                Text(text = stringResource(R.string.match_ocr_review_warning_value, stringResource(warning.toMessageRes())))
            }
        }
    }
    Button(
        onClick = { onResetRowCorrection(correctionDraft.rowIndex) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.resetRow(correctionDraft.rowIndex)),
    ) {
        Text(text = stringResource(R.string.match_ocr_review_reset_row_action))
    }
}

@Composable
private fun MatchOcrReviewBackAction(onBack: () -> Unit) {
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    Button(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.BACK_ACTION),
    ) {
        Text(text = stringResource(R.string.back_to_match_details_action))
    }
}

private fun MatchOcrReviewCorrectionDraftStatus.toMessageRes(): Int = when (this) {
    MatchOcrReviewCorrectionDraftStatus.VALID -> R.string.match_ocr_review_correction_status_valid
    MatchOcrReviewCorrectionDraftStatus.WARNING -> R.string.match_ocr_review_correction_status_warning
    MatchOcrReviewCorrectionDraftStatus.BLOCKED -> R.string.match_ocr_review_correction_status_blocked
}

private fun MatchOcrReviewCorrectionReason.toMessageRes(): Int = when (this) {
    MatchOcrReviewCorrectionReason.MISSING_PLACEMENT ->
        R.string.match_ocr_review_correction_missing_placement
    MatchOcrReviewCorrectionReason.INVALID_PLACEMENT ->
        R.string.match_ocr_review_correction_invalid_placement
    MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT ->
        R.string.match_ocr_review_correction_duplicate_placement
    MatchOcrReviewCorrectionReason.MISSING_KILLS ->
        R.string.match_ocr_review_correction_missing_kills
    MatchOcrReviewCorrectionReason.INVALID_KILLS ->
        R.string.match_ocr_review_correction_invalid_kills
    MatchOcrReviewCorrectionReason.NEGATIVE_KILLS ->
        R.string.match_ocr_review_correction_negative_kills
    MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT ->
        R.string.match_ocr_review_correction_missing_team_slot
    MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT ->
        R.string.match_ocr_review_correction_invalid_team_slot
    MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT ->
        R.string.match_ocr_review_correction_duplicate_team_slot
    MatchOcrReviewCorrectionReason.MALFORMED_ROW_DRAFT ->
        R.string.match_ocr_review_correction_malformed_row_draft
    MatchOcrReviewCorrectionReason.PLACEMENT_CHANGED_FROM_OCR ->
        R.string.match_ocr_review_correction_placement_changed
    MatchOcrReviewCorrectionReason.KILLS_CHANGED_FROM_OCR ->
        R.string.match_ocr_review_correction_kills_changed
    MatchOcrReviewCorrectionReason.TEAM_SLOT_CHANGED_FROM_SUGGESTION ->
        R.string.match_ocr_review_correction_team_slot_changed
    MatchOcrReviewCorrectionReason.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW ->
        R.string.match_ocr_review_correction_row_originally_manual
    MatchOcrReviewCorrectionReason.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE ->
        R.string.match_ocr_review_correction_weak_evidence
}
