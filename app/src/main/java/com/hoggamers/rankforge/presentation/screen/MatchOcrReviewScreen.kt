package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    )
}

@Composable
fun MatchOcrReviewScreen(
    uiState: MatchOcrReviewUiState,
    onBack: () -> Unit,
) {
    when (uiState) {
        MatchOcrReviewUiState.Loading -> MatchOcrReviewLoadingState()
        is MatchOcrReviewUiState.Empty -> MatchOcrReviewEmptyState(onBack)
        is MatchOcrReviewUiState.Error -> MatchOcrReviewErrorState(uiState, onBack)
        is MatchOcrReviewUiState.Ready -> MatchOcrReviewReadyState(uiState, onBack)
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
) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MatchOcrReviewTestTags.ROW_LIST),
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
            ) {
                uiState.rows.forEach { row ->
                    MatchOcrReviewRow(row = row)
                }
            }
            MatchOcrReviewBackAction(onBack)
        }
    }
}

@Composable
private fun MatchOcrReviewRow(row: MatchOcrReviewRowUiState) {
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
