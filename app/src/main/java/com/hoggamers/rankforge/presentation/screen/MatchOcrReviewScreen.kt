package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

object MatchOcrReviewTestTags {
    const val SCREEN = "match_ocr_review_screen"
    const val LOADING = "match_ocr_review_loading"
    const val EMPTY = "match_ocr_review_empty"
    const val ERROR = "match_ocr_review_error"
    const val EMPTY_CONTENT = "match_ocr_review_empty_content"
    const val READY_CONTENT = "match_ocr_review_ready_content"
    const val PREVIEW = "match_ocr_review_match_result_preview"
    const val LOBBY_PLAYERS = "match_ocr_review_lobby_players"
    const val COMPACT_LIST = "match_ocr_review_compact_list"
    const val ROW_LIST = "match_ocr_review_row_list"
    const val BACK_ACTION = "match_ocr_review_back_action"
    const val CORRECTION_ROOT = "match_ocr_review_correction_root"
    const val RESET_ALL = "match_ocr_review_reset_all"
    const val FINALIZATION_SUMMARY = "match_ocr_review_finalization_summary"
    const val FINALIZE_ACTION = "match_ocr_review_finalize_action"
    const val FINALIZE_BLOCKED_LABEL = "match_ocr_review_finalize_blocked_label"
    const val FINALIZE_WARNING_COUNT = "match_ocr_review_finalize_warning_count"
    const val FINALIZE_WARNING_DIALOG = "match_ocr_review_finalize_warning_dialog"
    const val CONFIRM_FINALIZE_WARNINGS = "match_ocr_review_confirm_finalize_warnings"
    const val DISMISS_FINALIZE_WARNINGS = "match_ocr_review_dismiss_finalize_warnings"
    const val FINALIZATION_SUCCESS = "match_ocr_review_finalization_success"
    const val FINALIZATION_ERROR = "match_ocr_review_finalization_error"
    private const val ROW_PREFIX = "match_ocr_review_row_"

    fun row(rowIndex: Int): String = ROW_PREFIX + rowIndex
    fun previewRow(position: Int): String = "${PREVIEW}_row_$position"
    fun lobbySlot(slot: Int): String = "${LOBBY_PLAYERS}_slot_$slot"
    fun lobbyPlayer(slot: Int, player: Int): String = "${lobbySlot(slot)}_player_$player"
    fun compactRow(position: Int): String = "${COMPACT_LIST}_row_$position"
    fun compactPlacement(position: Int): String = "${compactRow(position)}_placement"
    fun compactTeam(position: Int): String = "${compactRow(position)}_team"
    fun compactPlayer(position: Int, slot: Int): String = "${compactRow(position)}_player_$slot"
    fun compactPlayerRow(position: Int, row: Int): String = "${compactRow(position)}_players_$row"
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
        onFinalizeOcrCorrection = viewModel::onFinalizeOcrCorrection,
        onConfirmFinalizeWarnings = viewModel::onConfirmFinalizeWarnings,
        onDismissFinalizeWarnings = viewModel::onDismissFinalizeWarnings,
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
    onFinalizeOcrCorrection: () -> Unit = {},
    onConfirmFinalizeWarnings: () -> Unit = {},
    onDismissFinalizeWarnings: () -> Unit = {},
) {
    when (uiState) {
        MatchOcrReviewUiState.Loading -> MatchOcrReviewLoadingState()
        is MatchOcrReviewUiState.Empty -> MatchOcrReviewEmptyState(uiState, onBack)
        is MatchOcrReviewUiState.Error -> MatchOcrReviewErrorState(uiState, onBack)
        is MatchOcrReviewUiState.Ready -> MatchOcrReviewReadyState(
            uiState = uiState,
            onBack = onBack,
            onPlacementChanged = onPlacementChanged,
            onKillsChanged = onKillsChanged,
            onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
            onResetRowCorrection = onResetRowCorrection,
            onResetAllCorrections = onResetAllCorrections,
            onFinalizeOcrCorrection = onFinalizeOcrCorrection,
            onConfirmFinalizeWarnings = onConfirmFinalizeWarnings,
            onDismissFinalizeWarnings = onDismissFinalizeWarnings,
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
private fun MatchOcrReviewEmptyState(
    uiState: MatchOcrReviewUiState.Empty,
    onBack: () -> Unit,
) {
    val preview = uiState.matchResultOcrPreview
    val hasUsefulPreview = preview is MatchResultOcrPreviewUiState.Ready && preview.rows.isNotEmpty()
    RankForgeScreenContainer(
        modifier = Modifier.testTag(MatchOcrReviewTestTags.SCREEN),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag(MatchOcrReviewTestTags.EMPTY_CONTENT),
        ) {
            if (hasUsefulPreview) {
                Text(
                    text = stringResource(R.string.match_ocr_review_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.match_ocr_review_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag(MatchOcrReviewTestTags.EMPTY),
                )
                Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
                Text(text = stringResource(R.string.match_ocr_review_empty_message))
            }
            if (uiState.lobbyPlayers.isNotEmpty()) {
                MatchOcrReviewLobbyPlayersSection(
                    lobbyPlayers = uiState.lobbyPlayers,
                    teamNamesBySlot = uiState.teamNamesBySlot,
                )
            }
            if (hasUsefulPreview) {
                MatchOcrReviewCompactPreviewList(
                    preview = preview,
                    reviewRowsByPosition = emptyMap(),
                    teamNamesBySlot = emptyMap(),
                )
            } else {
                MatchResultOcrPreviewSection(preview)
            }
            MatchOcrReviewBackAction(onBack)
        }
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
    onFinalizeOcrCorrection: () -> Unit,
    onConfirmFinalizeWarnings: () -> Unit,
    onDismissFinalizeWarnings: () -> Unit,
) {
    val correctionRowsByIndex = uiState.correctionDraft?.rows.orEmpty().associateBy { it.rowIndex }
    val previewRowsByPosition = (uiState.matchResultOcrPreview as? MatchResultOcrPreviewUiState.Ready)
        ?.rows
        .orEmpty()
        .associateBy { it.position }
    RankForgeScreenContainer(
        modifier = Modifier.testTag(MatchOcrReviewTestTags.SCREEN),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag(MatchOcrReviewTestTags.READY_CONTENT),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.match_ocr_review_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (uiState.lobbyPlayers.isNotEmpty()) {
                MatchOcrReviewLobbyPlayersSection(
                    lobbyPlayers = uiState.lobbyPlayers,
                    teamNamesBySlot = uiState.teamNamesBySlot,
                )
            }
            MatchOcrReviewResultContent(
                uiState = uiState,
                onPlacementChanged = onPlacementChanged,
                onKillsChanged = onKillsChanged,
                onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                onResetRowCorrection = onResetRowCorrection,
                onResetAllCorrections = onResetAllCorrections,
                onFinalizeOcrCorrection = onFinalizeOcrCorrection,
                onConfirmFinalizeWarnings = onConfirmFinalizeWarnings,
                onDismissFinalizeWarnings = onDismissFinalizeWarnings,
                previewRowsByPosition = previewRowsByPosition,
                correctionRowsByIndex = correctionRowsByIndex,
            )
            MatchOcrReviewBackAction(onBack)
        }
    }
}

@Composable
internal fun MatchOcrReviewLobbyPlayersSection(
    lobbyPlayers: List<MatchOcrReviewLobbySlotUiState>,
    teamNamesBySlot: Map<Int, String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.LOBBY_PLAYERS),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.match_ocr_review_lobby_players_title),
            style = MaterialTheme.typography.titleLarge,
        )
        lobbyPlayers.sortedBy { it.slotNumber }.forEach { slot ->
            val teamName = teamNamesBySlot[slot.slotNumber]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.match_ocr_review_compact_not_named)
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag(MatchOcrReviewTestTags.lobbySlot(slot.slotNumber)),
                ) {
                    Text(
                        text = stringResource(
                            R.string.match_ocr_review_compact_team,
                            slot.slotNumber,
                            teamName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                LobbyPlayerRow(slot, leftPlayer = 1, rightPlayer = 3)
                LobbyPlayerRow(slot, leftPlayer = 2, rightPlayer = 4)
            }
        }
    }
}

@Composable
internal fun MatchOcrReviewResultContent(
    uiState: MatchOcrReviewUiState.Ready,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onKillsChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit = { _, _ -> },
    onResetRowCorrection: (rowIndex: Int) -> Unit = {},
    onResetAllCorrections: () -> Unit = {},
    onFinalizeOcrCorrection: () -> Unit = {},
    onConfirmFinalizeWarnings: () -> Unit = {},
    onDismissFinalizeWarnings: () -> Unit = {},
    previewRowsByPosition: Map<Int, MatchResultOcrPreviewRowUiState> =
        (uiState.matchResultOcrPreview as? MatchResultOcrPreviewUiState.Ready)
            ?.rows
            .orEmpty()
            .associateBy { it.position },
    correctionRowsByIndex: Map<Int, MatchOcrReviewRowCorrectionDraft> =
        uiState.correctionDraft?.rows.orEmpty().associateBy { it.rowIndex },
) {
    uiState.correctionDraft?.let { correctionDraft ->
        MatchOcrReviewCorrectionSummary(
            correctionDraft = correctionDraft,
            finalization = uiState.finalization,
            onResetAllCorrections = onResetAllCorrections,
            onFinalizeOcrCorrection = onFinalizeOcrCorrection,
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
                previewRow = previewRowsByPosition[row.rowIndex + 1],
                teamNamesBySlot = uiState.teamNamesBySlot,
                correctionDraft = correctionRowsByIndex[row.rowIndex],
                onPlacementChanged = onPlacementChanged,
                onKillsChanged = onKillsChanged,
                onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                onResetRowCorrection = onResetRowCorrection,
                correctionEnabled = !uiState.finalization.isFinalized,
            )
        }
    }
    if (uiState.finalization.showWarningConfirmation) {
        MatchOcrReviewFinalizeWarningDialog(
            warningCount = uiState.correctionDraft?.warningCount ?: 0,
            onConfirmFinalizeWarnings = onConfirmFinalizeWarnings,
            onDismissFinalizeWarnings = onDismissFinalizeWarnings,
        )
    }
}

@Composable
private fun LobbyPlayerRow(
    slot: MatchOcrReviewLobbySlotUiState,
    leftPlayer: Int,
    rightPlayer: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        LobbyPlayerCell(slot, leftPlayer, Modifier.weight(1f))
        LobbyPlayerCell(slot, rightPlayer, Modifier.weight(1f))
    }
}

@Composable
private fun LobbyPlayerCell(
    slot: MatchOcrReviewLobbySlotUiState,
    playerNumber: Int,
    modifier: Modifier,
) {
    val playerName = slot.players.firstOrNull { it.playerNumber == playerNumber }
        ?.playerName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.match_ocr_review_compact_not_detected)
    Text(
        text = stringResource(R.string.match_ocr_review_lobby_player, playerNumber, playerName),
        modifier = modifier.testTag(MatchOcrReviewTestTags.lobbyPlayer(slot.slotNumber, playerNumber)),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun MatchOcrReviewCompactPreviewList(
    preview: MatchResultOcrPreviewUiState.Ready,
    reviewRowsByPosition: Map<Int, MatchOcrReviewRowUiState>,
    teamNamesBySlot: Map<Int, String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.COMPACT_LIST),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        preview.rows.forEachIndexed { index, previewRow ->
            MatchOcrReviewCompactRow(
                previewRow = previewRow,
                reviewRow = reviewRowsByPosition[previewRow.position],
                teamNamesBySlot = teamNamesBySlot,
            )
            if (index < preview.rows.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun MatchOcrReviewCompactRow(
    previewRow: MatchResultOcrPreviewRowUiState,
    reviewRow: MatchOcrReviewRowUiState?,
    teamNamesBySlot: Map<Int, String>,
) {
    val placement = previewRow.placementText.trim().ifBlank { previewRow.position.toString() }
    val suggestedSlot = reviewRow?.suggestedTeamSlotDisplayValue
        ?.toIntOrNull()
        ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
    val slotLabel = suggestedSlot?.toString()
        ?: stringResource(R.string.match_ocr_review_compact_not_matched)
    val teamNameLabel = if (suggestedSlot == null) {
        stringResource(R.string.match_ocr_review_compact_not_matched)
    } else {
        teamNamesBySlot[suggestedSlot]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.match_ocr_review_compact_not_named)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.compactRow(previewRow.position)),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        Text(
            text = stringResource(R.string.match_ocr_review_compact_position, placement),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(MatchOcrReviewTestTags.compactPlacement(previewRow.position)),
        )
        Text(
            text = stringResource(
                R.string.match_ocr_review_compact_team,
                slotLabel,
                teamNameLabel,
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(MatchOcrReviewTestTags.compactTeam(previewRow.position)),
        )
        CompactPlayerRow(previewRow, row = 1, leftSlot = 1, rightSlot = 3)
        CompactPlayerRow(previewRow, row = 2, leftSlot = 2, rightSlot = 4)
    }
}

private fun MatchOcrReviewRowUiState.toCompactPreviewRow(): MatchResultOcrPreviewRowUiState =
    MatchResultOcrPreviewRowUiState(
        position = rowIndex + 1,
        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        sourceLabel = "",
        placementText = detectedPlacementDisplayValue,
        slots = (1..4).map { slot ->
            MatchResultOcrPreviewSlotUiState(
                slot = slot,
                playerText = if (slot == 1) detectedPlayerNameEvidenceLabel else "",
                playerOcrText = "",
                playerStatusLabel = "",
                killText = if (slot == 1) detectedKillDisplayValue else "",
                killOcrText = "",
                killStatusLabel = "",
            )
        },
    )

@Composable
private fun CompactPlayerRow(
    previewRow: MatchResultOcrPreviewRowUiState,
    row: Int,
    leftSlot: Int,
    rightSlot: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.compactPlayerRow(previewRow.position, row)),
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        CompactPlayerCell(previewRow, leftSlot, Modifier.weight(1f))
        CompactPlayerCell(previewRow, rightSlot, Modifier.weight(1f))
    }
}

@Composable
private fun CompactPlayerCell(
    previewRow: MatchResultOcrPreviewRowUiState,
    slot: Int,
    modifier: Modifier,
) {
    val player = previewRow.slots.firstOrNull { it.slot == slot }
    val playerName = player?.playerText?.trim().orEmpty().ifBlank {
        stringResource(R.string.match_ocr_review_compact_not_detected)
    }
    val kill = player?.killText?.trim().orEmpty().ifBlank {
        stringResource(R.string.match_ocr_review_compact_unknown_kill)
    }
    Text(
        text = stringResource(R.string.match_ocr_review_compact_player, slot, playerName, kill),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(MatchOcrReviewTestTags.compactPlayer(previewRow.position, slot)),
    )
}

@Composable
internal fun MatchResultOcrPreviewSection(
    preview: MatchResultOcrPreviewUiState,
) {
    when (preview) {
        MatchResultOcrPreviewUiState.NotRequested -> Unit
        MatchResultOcrPreviewUiState.Processing -> Text(
            text = "OCR Preview: processing...",
            modifier = Modifier.testTag(MatchOcrReviewTestTags.PREVIEW),
        )
        MatchResultOcrPreviewUiState.Empty -> Text(
            text = "OCR Preview: no rows extracted.",
            modifier = Modifier.testTag(MatchOcrReviewTestTags.PREVIEW),
        )
        is MatchResultOcrPreviewUiState.Error -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.PREVIEW),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
        ) {
            Text(text = "OCR Preview unavailable", style = MaterialTheme.typography.titleMedium)
            Text(text = preview.message, color = MaterialTheme.colorScheme.error)
        }
        is MatchResultOcrPreviewUiState.Ready -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.PREVIEW),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
        ) {
            Text(text = "OCR Preview", style = MaterialTheme.typography.titleMedium)
            Text(text = "Roles: ${preview.roles.joinToString { it.name }} | Rows: ${preview.rows.size}")
            preview.rows.forEach { row ->
                Text(
                    text = "Position ${row.position} (${row.sourceLabel}, ${row.role.name}) " +
                        "placement=${row.placementText}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(MatchOcrReviewTestTags.previewRow(row.position)),
                )
                (1..4).forEach { slot ->
                    val playerSlot = row.slots.firstOrNull { it.slot == slot }
                    Text(
                        text = if (playerSlot == null) {
                            "P$slot: absent"
                        } else {
                            "P$slot: ${playerSlot.playerText.ifBlank { "[blank]" }} | " +
                                "K$slot: ${playerSlot.killText.ifBlank { "[blank]" }} " +
                                "(${playerSlot.killStatusLabel})"
                        },
                    )
                }
            }
            preview.ignoredLowerRows.forEach { ignored ->
                Text(
                    text = "Ignored lower row ${ignored.visualRow}: placement=" +
                        "${ignored.detectedPlacement ?: "blank"} (${ignored.reason})",
                )
            }
            preview.manualReviewRows.forEach { manual ->
                Text(
                    text = "Lower row ${manual.visualRow} requires manual review: " +
                        "placement=${manual.detectedPlacementText.ifBlank { "blank" }} (${manual.reason})",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MatchOcrReviewCorrectionSummary(
    correctionDraft: MatchOcrReviewCorrectionDraft,
    finalization: MatchOcrReviewFinalizationUiState,
    onResetAllCorrections: () -> Unit,
    onFinalizeOcrCorrection: () -> Unit,
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
        MatchOcrReviewFinalizationSummary(
            correctionDraft = correctionDraft,
            finalization = finalization,
            onFinalizeOcrCorrection = onFinalizeOcrCorrection,
        )
        Button(
            onClick = onResetAllCorrections,
            enabled = !finalization.isFinalized,
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
    previewRow: MatchResultOcrPreviewRowUiState?,
    teamNamesBySlot: Map<Int, String>,
    correctionDraft: MatchOcrReviewRowCorrectionDraft?,
    onPlacementChanged: (rowIndex: Int, value: String) -> Unit,
    onKillsChanged: (rowIndex: Int, value: String) -> Unit,
    onAssignedTeamSlotChanged: (rowIndex: Int, value: String) -> Unit,
    onResetRowCorrection: (rowIndex: Int) -> Unit,
    correctionEnabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.row(row.rowIndex)),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        MatchOcrReviewCompactRow(
            previewRow = previewRow ?: row.toCompactPreviewRow(),
            reviewRow = row,
            teamNamesBySlot = teamNamesBySlot,
        )
        if (correctionDraft != null) {
            MatchOcrReviewCorrectionFields(
                correctionDraft = correctionDraft,
                onPlacementChanged = onPlacementChanged,
                onKillsChanged = onKillsChanged,
                onAssignedTeamSlotChanged = onAssignedTeamSlotChanged,
                onResetRowCorrection = onResetRowCorrection,
                correctionEnabled = correctionEnabled,
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
    correctionEnabled: Boolean,
) {
    OutlinedTextField(
        value = correctionDraft.placementDraftValue,
        onValueChange = { onPlacementChanged(correctionDraft.rowIndex, it) },
        enabled = correctionEnabled,
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
        enabled = correctionEnabled,
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
        enabled = correctionEnabled,
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
        enabled = correctionEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.resetRow(correctionDraft.rowIndex)),
    ) {
        Text(text = stringResource(R.string.match_ocr_review_reset_row_action))
    }
}

@Composable
private fun MatchOcrReviewFinalizationSummary(
    correctionDraft: MatchOcrReviewCorrectionDraft,
    finalization: MatchOcrReviewFinalizationUiState,
    onFinalizeOcrCorrection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MatchOcrReviewTestTags.FINALIZATION_SUMMARY),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        Text(text = stringResource(R.string.match_ocr_review_finalization_ready))
        if (correctionDraft.blockerCount > 0) {
            Text(
                text = stringResource(
                    R.string.match_ocr_review_finalization_blocked,
                    correctionDraft.blockerCount,
                ),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MatchOcrReviewTestTags.FINALIZE_BLOCKED_LABEL),
            )
        }
        if (correctionDraft.warningCount > 0) {
            Text(
                text = stringResource(
                    R.string.match_ocr_review_finalization_warning_count,
                    correctionDraft.warningCount,
                ),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.testTag(MatchOcrReviewTestTags.FINALIZE_WARNING_COUNT),
            )
        }
        if (finalization.isFinalized) {
            Text(
                text = stringResource(R.string.match_ocr_review_finalization_success),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(MatchOcrReviewTestTags.FINALIZATION_SUCCESS),
            )
        }
        finalization.error?.let { error ->
            Text(
                text = stringResource(error.toMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MatchOcrReviewTestTags.FINALIZATION_ERROR),
            )
        }
        Button(
            onClick = onFinalizeOcrCorrection,
            enabled = correctionDraft.blockerCount == 0 &&
                !finalization.isFinalizing &&
                !finalization.isFinalized,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MatchOcrReviewTestTags.FINALIZE_ACTION),
        ) {
            Text(
                text = stringResource(
                    if (finalization.isFinalizing) {
                        R.string.match_ocr_review_finalization_in_progress
                    } else {
                        R.string.match_ocr_review_finalize_action
                    },
                ),
            )
        }
    }
}

@Composable
private fun MatchOcrReviewFinalizeWarningDialog(
    warningCount: Int,
    onConfirmFinalizeWarnings: () -> Unit,
    onDismissFinalizeWarnings: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(MatchOcrReviewTestTags.FINALIZE_WARNING_DIALOG),
        onDismissRequest = onDismissFinalizeWarnings,
        title = { Text(text = stringResource(R.string.match_ocr_review_finalize_warning_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.match_ocr_review_finalize_warning_message,
                    warningCount,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmFinalizeWarnings,
                modifier = Modifier.testTag(MatchOcrReviewTestTags.CONFIRM_FINALIZE_WARNINGS),
            ) {
                Text(text = stringResource(R.string.match_ocr_review_confirm_finalize_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissFinalizeWarnings,
                modifier = Modifier.testTag(MatchOcrReviewTestTags.DISMISS_FINALIZE_WARNINGS),
            ) {
                Text(text = stringResource(R.string.cancel_action))
            }
        },
    )
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

private fun MatchOcrReviewFinalizationError.toMessageRes(): Int = when (this) {
    MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT ->
        R.string.match_ocr_review_finalization_missing_draft
    MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED ->
        R.string.match_ocr_review_finalization_blocked_error
    MatchOcrReviewFinalizationError.MISSING_TOURNAMENT ->
        R.string.match_ocr_review_finalization_missing_tournament
    MatchOcrReviewFinalizationError.MISSING_MATCH ->
        R.string.match_ocr_review_finalization_missing_match
    MatchOcrReviewFinalizationError.ALREADY_FINALIZED ->
        R.string.match_ocr_review_finalization_already_finalized
    MatchOcrReviewFinalizationError.FINALIZATION_FAILED ->
        R.string.match_ocr_review_finalization_failed
    MatchOcrReviewFinalizationError.UNEXPECTED_FAILURE ->
        R.string.match_ocr_review_finalization_failed
}
