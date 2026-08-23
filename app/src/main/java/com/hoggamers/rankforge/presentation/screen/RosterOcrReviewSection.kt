package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProviderResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val ROSTER_OCR_REVIEW_SECTION_TEST_TAG = "roster_ocr_review_section"
const val ROSTER_OCR_REVIEW_START_PROCESSING_TEST_TAG = "roster_ocr_review_start_processing"
const val ROSTER_OCR_REVIEW_PROCESSING_TEST_TAG = "roster_ocr_review_processing"
const val ROSTER_OCR_REVIEW_STATUS_TEST_TAG = "roster_ocr_review_status"
const val ROSTER_OCR_REVIEW_VALIDATION_TEST_TAG = "roster_ocr_review_validation"
const val ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG = "roster_ocr_review_request_confirmation"
const val ROSTER_OCR_REVIEW_DIALOG_TEST_TAG = "roster_ocr_review_confirmation_dialog"
const val ROSTER_OCR_REVIEW_CONFIRM_TEST_TAG = "roster_ocr_review_confirm"
const val ROSTER_OCR_REVIEW_ABANDON_TEST_TAG = "roster_ocr_review_abandon"
const val ROSTER_OCR_REVIEW_RESET_ALL_TEST_TAG = "roster_ocr_review_reset_all"

fun rosterOcrReviewSlotTestTag(slotNumber: Int) = "roster_ocr_review_slot_$slotNumber"
fun rosterOcrReviewTeamTestTag(slotNumber: Int) = "roster_ocr_review_team_$slotNumber"
fun rosterOcrReviewPlayerTestTag(slotNumber: Int, playerRowIndex: Int) =
    "roster_ocr_review_player_${slotNumber}_$playerRowIndex"
fun rosterOcrReviewOriginalCandidateTestTag(slotNumber: Int, playerRowIndex: Int) =
    "roster_ocr_review_original_candidate_${slotNumber}_$playerRowIndex"
fun rosterOcrReviewCorrectionIndicatorTestTag(slotNumber: Int, playerRowIndex: Int) =
    "roster_ocr_review_correction_indicator_${slotNumber}_$playerRowIndex"
fun rosterOcrReviewSourceStatusTestTag(slotNumber: Int, playerRowIndex: Int) =
    "roster_ocr_review_source_status_${slotNumber}_$playerRowIndex"
fun rosterOcrReviewSlotIssueTestTag(slotNumber: Int) =
    "roster_ocr_review_slot_ocr_issue_$slotNumber"
fun rosterOcrReviewManualOnlyTestTag(slotNumber: Int, playerRowIndex: Int) =
    "roster_ocr_review_manual_only_${slotNumber}_$playerRowIndex"
fun rosterOcrReviewResetPlayerTestTag(slotNumber: Int, playerRowIndex: Int) =
    "roster_ocr_review_reset_player_${slotNumber}_$playerRowIndex"
fun rosterOcrReviewResetSlotTestTag(slotNumber: Int) = "roster_ocr_review_reset_slot_$slotNumber"

@Composable
fun RosterOcrReviewSection(
    state: RosterOcrReviewUiState,
    onStartProcessing: () -> Unit,
    onUpdatePlayerName: (Int, Int, String) -> Unit,
    onResetPlayerCorrection: (Int, Int) -> Unit,
    onResetSlotCorrections: (Int) -> Unit,
    onResetAllCorrections: () -> Unit,
    onAbandonReview: () -> Unit,
    onRequestConfirmation: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmReplacement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ROSTER_OCR_REVIEW_SECTION_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.roster_ocr_review_title),
            style = MaterialTheme.typography.titleLarge,
        )
        when (state) {
            is RosterOcrReviewUiState.LoadingTeamContext -> LoadingContent()
            is RosterOcrReviewUiState.ReadyToProcess -> ReadyContent(
                state = state,
                onStartProcessing = onStartProcessing,
            )
            is RosterOcrReviewUiState.Processing -> ProcessingContent()
            is RosterOcrReviewUiState.Reviewing -> ReviewingContent(
                state = state,
                onUpdatePlayerName = onUpdatePlayerName,
                onResetPlayerCorrection = onResetPlayerCorrection,
                onResetSlotCorrections = onResetSlotCorrections,
                onResetAllCorrections = onResetAllCorrections,
                onAbandonReview = onAbandonReview,
                onRequestConfirmation = onRequestConfirmation,
                onDismissConfirmation = onDismissConfirmation,
                onConfirmReplacement = onConfirmReplacement,
            )
            is RosterOcrReviewUiState.LocalReplacementCommitted ->
                LocalReplacementCommittedContent(state)
            is RosterOcrReviewUiState.Completed -> CompletedContent(state)
            is RosterOcrReviewUiState.Unavailable -> UnavailableContent()
        }
    }
}

@Composable
private fun LoadingContent() {
    CircularProgressIndicator()
    Text(text = stringResource(R.string.roster_ocr_review_loading))
}

@Composable
private fun ReadyContent(
    state: RosterOcrReviewUiState.ReadyToProcess,
    onStartProcessing: () -> Unit,
) {
    Text(text = stringResource(R.string.roster_ocr_review_ready))
    state.processingFailure?.let { failure ->
        Text(
            text = processingFailureMessage(failure),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
        )
    }
    Button(
        onClick = onStartProcessing,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ROSTER_OCR_REVIEW_START_PROCESSING_TEST_TAG),
    ) {
        Text(
            text = stringResource(
                if (state.processingFailure == null) {
                    R.string.roster_ocr_review_process_action
                } else {
                    R.string.roster_ocr_review_retry_action
                },
            ),
        )
    }
}

@Composable
private fun ProcessingContent() {
    Column(
        modifier = Modifier.testTag(ROSTER_OCR_REVIEW_PROCESSING_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(text = stringResource(R.string.roster_ocr_review_processing))
    }
}

@Composable
private fun ReviewingContent(
    state: RosterOcrReviewUiState.Reviewing,
    onUpdatePlayerName: (Int, Int, String) -> Unit,
    onResetPlayerCorrection: (Int, Int) -> Unit,
    onResetSlotCorrections: (Int) -> Unit,
    onResetAllCorrections: () -> Unit,
    onAbandonReview: () -> Unit,
    onRequestConfirmation: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmReplacement: () -> Unit,
) {
    val canEdit = state.localReplacement is RosterOcrLocalReplacementState.Ready ||
        state.localReplacement is RosterOcrLocalReplacementState.Failed
    DraftStatus(state.draft)
    state.draft.slots.sortedBy { it.slotNumber }.forEach { slot ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(rosterOcrReviewSlotTestTag(slot.slotNumber)),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.roster_ocr_review_slot_label, slot.slotNumber),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = stringResource(R.string.roster_ocr_review_team_label))
            Text(
                text = slot.currentTeamName,
                modifier = Modifier.testTag(rosterOcrReviewTeamTestTag(slot.slotNumber)),
            )
            Text(
                text = stringResource(
                    R.string.roster_ocr_review_slot_ocr_issue_summary,
                    slot.slotNumber,
                    slot.sourceIssues.size,
                ),
                modifier = Modifier.testTag(rosterOcrReviewSlotIssueTestTag(slot.slotNumber)),
            )
            slot.players.sortedBy { it.playerRowIndex }.forEach { player ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (player.isManualOnly) {
                        Text(
                            text = stringResource(R.string.roster_ocr_review_manual_only),
                            modifier = Modifier.testTag(
                                rosterOcrReviewManualOnlyTestTag(
                                    slot.slotNumber,
                                    player.playerRowIndex,
                                ),
                            ),
                        )
                    } else {
                        Text(
                            text = player.originalOcrValue.ifBlank {
                                stringResource(R.string.roster_ocr_review_no_usable_candidate)
                            },
                            modifier = Modifier.testTag(
                                rosterOcrReviewOriginalCandidateTestTag(
                                    slot.slotNumber,
                                    player.playerRowIndex,
                                ),
                            ),
                        )
                        if (player.isDirty) {
                            Text(
                                text = stringResource(
                                    R.string.roster_ocr_review_corrected_from_candidate,
                                ),
                                modifier = Modifier.testTag(
                                    rosterOcrReviewCorrectionIndicatorTestTag(
                                        slot.slotNumber,
                                        player.playerRowIndex,
                                    ),
                                ),
                            )
                        }
                        Text(
                            text = sourceStatusMessage(player.sourceStatus),
                            modifier = Modifier.testTag(
                                rosterOcrReviewSourceStatusTestTag(
                                    slot.slotNumber,
                                    player.playerRowIndex,
                                ),
                            ),
                        )
                    }
                    OutlinedTextField(
                        value = player.draftValue,
                        onValueChange = {
                            onUpdatePlayerName(slot.slotNumber, player.playerRowIndex, it)
                        },
                        enabled = canEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                rosterOcrReviewPlayerTestTag(
                                    slot.slotNumber,
                                    player.playerRowIndex,
                                ),
                            ),
                        label = {
                            Text(
                                stringResource(
                                    if (player.isManualOnly) {
                                        R.string.roster_ocr_review_manual_player_label
                                    } else {
                                        R.string.roster_ocr_review_player_label
                                    },
                                    player.playerRowIndex,
                                ),
                            )
                        },
                        singleLine = true,
                        isError = player.validation.isNotEmpty(),
                    )
                    TextButton(
                        onClick = {
                            onResetPlayerCorrection(slot.slotNumber, player.playerRowIndex)
                        },
                        enabled = canEdit,
                        modifier = Modifier.testTag(
                            rosterOcrReviewResetPlayerTestTag(
                                slot.slotNumber,
                                player.playerRowIndex,
                            ),
                        ),
                    ) {
                        Text(
                            stringResource(
                                R.string.roster_ocr_review_reset_player_action,
                                player.playerRowIndex,
                            ),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
                TextButton(
                    onClick = { onResetSlotCorrections(slot.slotNumber) },
                    enabled = canEdit,
                    modifier = Modifier.testTag(rosterOcrReviewResetSlotTestTag(slot.slotNumber)),
                ) {
                    Text(stringResource(R.string.roster_ocr_review_reset_slot_action))
                }
            }
        }
    }

    if (state.localReplacement is RosterOcrLocalReplacementState.InProgress) {
        Text(
            text = stringResource(R.string.roster_ocr_review_local_replacement_in_progress),
            modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
        )
    }
    if (state.localReplacement is RosterOcrLocalReplacementState.Failed) {
        Text(
            text = localReplacementErrorMessage(state.localReplacement.error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
        )
    }
    Button(
        onClick = onResetAllCorrections,
        enabled = canEdit,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ROSTER_OCR_REVIEW_RESET_ALL_TEST_TAG),
    ) {
        Text(stringResource(R.string.roster_ocr_review_reset_all_action))
    }
    Button(
        onClick = onRequestConfirmation,
        enabled = canEdit && state.draft.canConfirm,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG),
    ) {
        Text(stringResource(R.string.roster_ocr_review_request_confirmation_action))
    }
    TextButton(
        onClick = onAbandonReview,
        enabled = canEdit,
        modifier = Modifier.testTag(ROSTER_OCR_REVIEW_ABANDON_TEST_TAG),
    ) {
        Text(stringResource(R.string.roster_ocr_review_abandon_action))
    }

    if (
        state.confirmation == RosterOcrReviewConfirmationState.Requested &&
            canEdit &&
            state.draft.canConfirm
    ) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            modifier = Modifier.testTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG),
            title = { Text(stringResource(R.string.roster_ocr_review_confirmation_title)) },
            text = { Text(stringResource(R.string.roster_ocr_review_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmReplacement,
                    modifier = Modifier.testTag(ROSTER_OCR_REVIEW_CONFIRM_TEST_TAG),
                ) {
                    Text(stringResource(R.string.roster_ocr_review_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmation) {
                    Text(stringResource(R.string.roster_ocr_review_dismiss_action))
                }
            },
        )
    }
}

@Composable
private fun DraftStatus(draft: RosterOcrReviewDraft) {
    Column(
        modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (draft.finalValidation.hasBlockers) {
            Column(
                modifier = Modifier.testTag(ROSTER_OCR_REVIEW_VALIDATION_TEST_TAG),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.roster_ocr_review_blocked_status,
                        draft.blockerCount,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                draft.finalValidation.issues.forEach { issue ->
                    Text(text = finalValidationIssueMessage(issue))
                }
            }
        } else {
            Text(text = stringResource(R.string.roster_ocr_review_ready_status))
        }
        if (draft.warningCount > 0) {
            Text(text = stringResource(R.string.roster_ocr_review_issue_status, draft.warningCount))
            Text(text = stringResource(R.string.roster_ocr_review_evidence_info))
        }
    }
}

@Composable
private fun LocalReplacementCommittedContent(
    state: RosterOcrReviewUiState.LocalReplacementCommitted,
) {
    Text(
        text = stringResource(R.string.roster_ocr_review_local_replacement_success),
        modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
    )
    ReadOnlyDraft(state.draft)
    when (state.cloudSynchronization) {
        RosterOcrCloudSynchronizationState.InProgress -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = stringResource(R.string.roster_ocr_review_cloud_in_progress))
        }
        RosterOcrCloudSynchronizationState.UnexpectedFailure -> Text(
            text = stringResource(R.string.roster_ocr_review_cloud_unexpected_failure),
            color = MaterialTheme.colorScheme.error,
        )
        is RosterOcrCloudSynchronizationState.Failed -> Text(
            text = cloudResultMessage(state.cloudSynchronization.result.primaryResult),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CompletedContent(state: RosterOcrReviewUiState.Completed) {
    Text(
        text = stringResource(R.string.roster_ocr_review_local_replacement_success),
        modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
    )
    ReadOnlyDraft(state.draft)
    when (state.cloudResult.primaryResult) {
        is TournamentRosterCloudReplacementResult.Success -> Text(
            text = stringResource(R.string.roster_ocr_review_cloud_success),
        )
        else -> Text(
            text = cloudResultMessage(state.cloudResult.primaryResult),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReadOnlyDraft(draft: RosterOcrReviewDraft) {
    draft.slots.sortedBy { it.slotNumber }.forEach { slot ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(rosterOcrReviewSlotTestTag(slot.slotNumber)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.roster_ocr_review_slot_label, slot.slotNumber),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = slot.currentTeamName,
                modifier = Modifier.testTag(rosterOcrReviewTeamTestTag(slot.slotNumber)),
            )
            slot.players.sortedBy { it.playerRowIndex }.forEach { player ->
                Text(
                    text = stringResource(
                        R.string.roster_ocr_review_read_only_player_value,
                        player.playerRowIndex,
                        player.draftValue.ifBlank {
                            stringResource(R.string.roster_empty_message)
                        },
                    ),
                    modifier = Modifier.testTag(
                        rosterOcrReviewPlayerTestTag(slot.slotNumber, player.playerRowIndex),
                    ),
                )
            }
        }
    }
}

@Composable
private fun UnavailableContent() {
    Text(
        text = stringResource(R.string.roster_ocr_review_unavailable),
        modifier = Modifier.testTag(ROSTER_OCR_REVIEW_STATUS_TEST_TAG),
    )
}

@Composable
private fun processingFailureMessage(failure: RosterOcrReviewProcessingFailure): String = when (failure) {
    is RosterOcrReviewProcessingFailure.Controlled -> processFailureMessage(failure.failure)
    is RosterOcrReviewProcessingFailure.DraftCreation -> stringResource(
        when (failure.failure) {
            RosterOcrReviewDraftCreationFailure.INVALID_TOURNAMENT_CONTEXT ->
                R.string.roster_ocr_review_failure_invalid_tournament
            RosterOcrReviewDraftCreationFailure.INCOMPLETE_TEAM_CONTEXT ->
                R.string.roster_ocr_review_failure_incomplete_team_context
            RosterOcrReviewDraftCreationFailure.DUPLICATE_TEAM_CONTEXT ->
                R.string.roster_ocr_review_failure_duplicate_team_context
            RosterOcrReviewDraftCreationFailure.INVALID_TEAM_SLOT ->
                R.string.roster_ocr_review_failure_invalid_team_slot
            RosterOcrReviewDraftCreationFailure.MISMATCHED_TOURNAMENT_CONTEXT ->
                R.string.roster_ocr_review_failure_mismatched_tournament
        },
    )
    RosterOcrReviewProcessingFailure.UnexpectedFailure -> stringResource(
        R.string.roster_ocr_review_failure_unexpected_processing,
    )
}

@Composable
private fun processFailureMessage(failure: ProcessRosterOcrFailure): String = when (failure) {
    ProcessRosterOcrFailure.InvalidTournamentContext -> stringResource(
        R.string.roster_ocr_review_failure_invalid_tournament,
    )
    is ProcessRosterOcrFailure.SourceLoading -> sourceLoadingFailureMessage(failure.failure)
    is ProcessRosterOcrFailure.PanelPreparation -> stringResource(
        when (failure.failure) {
            RosterOcrPanelPreparationFailure.MISSING_LOCAL_ORIGINAL ->
                R.string.roster_ocr_review_failure_missing_local_original
            RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE ->
                R.string.roster_ocr_review_failure_unreadable_screenshot
            RosterOcrPanelPreparationFailure.INVALID_CROP ->
                R.string.roster_ocr_review_failure_invalid_crop
            RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS ->
                R.string.roster_ocr_review_failure_unsafe_dimensions
            RosterOcrPanelPreparationFailure.CROP_FAILURE ->
                R.string.roster_ocr_review_failure_crop
            RosterOcrPanelPreparationFailure.UNKNOWN ->
                R.string.roster_ocr_review_failure_panel_preparation
        },
    )
    is ProcessRosterOcrFailure.UnexpectedExtraction -> stringResource(
        R.string.roster_ocr_review_failure_extraction,
    )
    is ProcessRosterOcrFailure.UnexpectedPanelRelease -> stringResource(
        R.string.roster_ocr_review_failure_panel_release,
    )
    is ProcessRosterOcrFailure.UnexpectedExtractionAndPanelRelease -> stringResource(
        R.string.roster_ocr_review_failure_extraction_and_release,
    )
    ProcessRosterOcrFailure.UnexpectedParser -> stringResource(
        R.string.roster_ocr_review_failure_parser,
    )
    ProcessRosterOcrFailure.UnexpectedAssociation -> stringResource(
        R.string.roster_ocr_review_failure_association,
    )
    ProcessRosterOcrFailure.UnexpectedValidation -> stringResource(
        R.string.roster_ocr_review_failure_validation,
    )
}

@Composable
private fun sourceLoadingFailureMessage(failure: RosterOcrSourceProviderResult): String = stringResource(
    when (failure) {
        is RosterOcrSourceProviderResult.Loaded -> R.string.roster_ocr_review_error
        RosterOcrSourceProviderResult.InvalidTournamentContext ->
            R.string.roster_ocr_review_failure_invalid_tournament
        is RosterOcrSourceProviderResult.MismatchedTournamentContext ->
            R.string.roster_ocr_review_failure_mismatched_screenshot
        RosterOcrSourceProviderResult.IncompleteScreenshotSet ->
            R.string.roster_ocr_review_failure_incomplete_screenshot_set
        is RosterOcrSourceProviderResult.DuplicateScreenshotPositions ->
            R.string.roster_ocr_review_failure_duplicate_screenshot_positions
        is RosterOcrSourceProviderResult.UnsupportedScreenshotPosition ->
            R.string.roster_ocr_review_failure_unsupported_screenshot_position
        is RosterOcrSourceProviderResult.MissingCropMetadata ->
            R.string.roster_ocr_review_failure_missing_crop
        RosterOcrSourceProviderResult.LoadingFailure ->
            R.string.roster_ocr_review_failure_source_loading
    },
)

@Composable
private fun finalValidationIssueMessage(issue: RosterOcrReviewDraftIssue): String {
    val typeMessage = stringResource(
        when (issue.type) {
            RosterOcrReviewDraftIssueType.MALFORMED_STRUCTURE ->
                R.string.roster_ocr_review_issue_malformed_structure
            RosterOcrReviewDraftIssueType.MISSING_TEAM_NAME ->
                R.string.roster_ocr_review_issue_missing_team_name
            RosterOcrReviewDraftIssueType.DUPLICATE_TEAM_NAME ->
                R.string.roster_ocr_review_issue_duplicate_team_name
            RosterOcrReviewDraftIssueType.INVALID_PLAYER_COUNT ->
                R.string.roster_ocr_review_issue_invalid_player_count
            RosterOcrReviewDraftIssueType.DUPLICATE_PLAYER_NAME ->
                R.string.roster_ocr_review_issue_duplicate_player_name
        },
    )
    return when {
        issue.slotNumber != null && issue.playerRowIndex != null -> stringResource(
            R.string.roster_ocr_review_final_validation_player_issue,
            issue.slotNumber,
            issue.playerRowIndex,
            typeMessage,
        )
        issue.slotNumber != null -> stringResource(
            R.string.roster_ocr_review_final_validation_slot_issue,
            issue.slotNumber,
            typeMessage,
        )
        else -> stringResource(R.string.roster_ocr_review_final_validation_global_issue, typeMessage)
    }
}

@Composable
private fun cloudResultMessage(result: TournamentRosterCloudReplacementResult): String = stringResource(
    when (result) {
        TournamentRosterCloudReplacementResult.AuthenticationRequired ->
            R.string.roster_ocr_review_cloud_authentication_required
        TournamentRosterCloudReplacementResult.NetworkFailure ->
            R.string.roster_ocr_review_cloud_network_failure
        TournamentRosterCloudReplacementResult.ValidationFailure ->
            R.string.roster_ocr_review_cloud_validation_failure
        TournamentRosterCloudReplacementResult.AuthorizationFailure ->
            R.string.roster_ocr_review_cloud_authorization_failure
        is TournamentRosterCloudReplacementResult.Conflict -> when (result.conflict) {
            RevisionConflict.MissingRevision -> R.string.roster_ocr_review_cloud_network_failure
            is RevisionConflict.StaleWrite,
            is RevisionConflict.LocalCloudDivergence,
            -> R.string.roster_ocr_review_cloud_conflict
        }
        TournamentRosterCloudReplacementResult.BlockedByExistingMatches ->
            R.string.roster_ocr_review_cloud_blocked_by_matches
        TournamentRosterCloudReplacementResult.UnknownFailure ->
            R.string.roster_ocr_review_cloud_unknown_failure
        is TournamentRosterCloudReplacementResult.Success ->
            R.string.roster_ocr_review_cloud_success
    },
)

@Composable
private fun localReplacementErrorMessage(
    error: RosterOcrReviewLocalReplacementError,
): String = stringResource(
    when (error) {
        RosterOcrReviewLocalReplacementError.DRAFT_BLOCKED ->
            R.string.roster_ocr_review_local_error_draft_blocked
        RosterOcrReviewLocalReplacementError.AUTHENTICATION_REQUIRED ->
            R.string.roster_ocr_review_local_error_tournament_not_found
        RosterOcrReviewLocalReplacementError.TOURNAMENT_NOT_FOUND ->
            R.string.roster_ocr_review_local_error_tournament_not_found
        RosterOcrReviewLocalReplacementError.INVALID_CANDIDATE ->
            R.string.roster_ocr_review_local_error_invalid_candidate
        RosterOcrReviewLocalReplacementError.BLOCKED_BY_EXISTING_MATCHES ->
            R.string.roster_ocr_review_local_error_blocked_by_matches
        RosterOcrReviewLocalReplacementError.UNEXPECTED_FAILURE ->
            R.string.roster_ocr_review_local_error_unexpected
    },
)

@Composable
private fun sourceStatusMessage(status: RosterCandidateParseStatus?): String = stringResource(
    when (status) {
        null -> R.string.roster_ocr_review_no_usable_candidate
        RosterCandidateParseStatus.PARSED -> R.string.roster_ocr_review_source_parsed
        RosterCandidateParseStatus.EMPTY -> R.string.roster_ocr_review_source_empty
        RosterCandidateParseStatus.MISSING -> R.string.roster_ocr_review_source_missing
        RosterCandidateParseStatus.AMBIGUOUS -> R.string.roster_ocr_review_source_ambiguous
        RosterCandidateParseStatus.DUPLICATE -> R.string.roster_ocr_review_source_duplicate
        RosterCandidateParseStatus.MALFORMED -> R.string.roster_ocr_review_source_malformed
        RosterCandidateParseStatus.UNCERTAIN -> R.string.roster_ocr_review_source_uncertain
        RosterCandidateParseStatus.UNSUPPORTED -> R.string.roster_ocr_review_source_unsupported
        RosterCandidateParseStatus.INPUT_FAILURE -> R.string.roster_ocr_review_source_input_failure
    },
)
