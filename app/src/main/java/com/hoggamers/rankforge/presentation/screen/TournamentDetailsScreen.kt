package com.hoggamers.rankforge.presentation.screen

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeLoadingState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

private val detailsDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

const val TOURNAMENT_DETAILS_SCREEN_TEST_TAG = "tournament_details_screen"
const val TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG = "tournament_details_not_found"
const val TOURNAMENT_SLOT_LIST_TEST_TAG = "tournament_slot_list"
const val TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX = "tournament_slot_item_"
const val TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG = "tournament_cloud_upload_action"
const val TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG = "tournament_cloud_upload_status"
const val DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG = "draft_match_cloud_sync_action"
const val DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG = "draft_match_cloud_sync_status"
const val FINALIZED_MATCH_CLOUD_SYNC_ACTION_TEST_TAG = "finalized_match_cloud_sync_action"
const val FINALIZED_MATCH_CLOUD_SYNC_STATUS_TEST_TAG = "finalized_match_cloud_sync_status"
const val MATCH_CLOUD_RESTORE_ACTION_TEST_TAG = "match_cloud_restore_action"
const val MATCH_CLOUD_RESTORE_STATUS_TEST_TAG = "match_cloud_restore_status"

@Composable
fun TournamentDetailsRoute(
    tournamentId: String,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    onCreateMatch: (String) -> Unit = {},
    onEnterMatchPlacements: (String, String) -> Unit = { _, _ -> },
    onEnterMatchKills: (String, String) -> Unit = { _, _ -> },
    onReviewMatch: (String, String) -> Unit = { _, _ -> },
    onOpenStandings: (String) -> Unit = {},
    viewModel: TournamentDetailsViewModel = hiltViewModel(),
    uploadViewModel: TournamentCloudUploadViewModel? = null,
    draftMatchSyncViewModel: DraftMatchCloudSyncViewModel? = null,
    finalizedMatchSyncViewModel: FinalizedMatchCloudSyncViewModel? = null,
    matchCloudRestorationViewModel: MatchCloudRestorationViewModel? = null,
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uploadUiState = if (uploadViewModel == null) {
        TournamentCloudUploadUiState.Idle
    } else {
        val state by uploadViewModel.uiState.collectAsStateWithLifecycle()
        state
    }
    val draftMatchSyncUiState = if (draftMatchSyncViewModel == null) {
        DraftMatchCloudSyncUiState.Idle
    } else {
        val state by draftMatchSyncViewModel.uiState.collectAsStateWithLifecycle()
        state
    }
    val finalizedMatchSyncUiState = if (finalizedMatchSyncViewModel == null) {
        FinalizedMatchCloudSyncUiState.Idle
    } else {
        val state by finalizedMatchSyncViewModel.uiState.collectAsStateWithLifecycle()
        state
    }
    val matchCloudRestorationUiState = if (matchCloudRestorationViewModel == null) MatchCloudRestorationUiState.Idle else {
        val state by matchCloudRestorationViewModel.uiState.collectAsStateWithLifecycle(); state
    }

    TournamentDetailsScreen(
        uiState = uiState,
        onBackToList = onBackToList,
        onEnterTeams = onEnterTeams,
        onCreateMatch = onCreateMatch,
        onEnterMatchPlacements = onEnterMatchPlacements,
        onEnterMatchKills = onEnterMatchKills,
        onReviewMatch = onReviewMatch,
        onOpenStandings = onOpenStandings,
        uploadUiState = uploadUiState,
        onUpload = { id -> uploadViewModel?.upload(id) },
        draftMatchSyncUiState = draftMatchSyncUiState,
        onSyncDraftMatches = { id -> draftMatchSyncViewModel?.sync(id) },
        finalizedMatchSyncUiState = finalizedMatchSyncUiState,
        onSyncFinalizedMatches = { id -> finalizedMatchSyncViewModel?.sync(id) },
        matchCloudRestorationUiState = matchCloudRestorationUiState,
        onRestoreMatches = { id -> matchCloudRestorationViewModel?.restore(id) },
    )
}

@Composable
fun TournamentDetailsScreen(
    uiState: TournamentDetailsUiState,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    onCreateMatch: (String) -> Unit = {},
    onEnterMatchPlacements: (String, String) -> Unit = { _, _ -> },
    onEnterMatchKills: (String, String) -> Unit = { _, _ -> },
    onReviewMatch: (String, String) -> Unit = { _, _ -> },
    onOpenStandings: (String) -> Unit = {},
    uploadUiState: TournamentCloudUploadUiState = TournamentCloudUploadUiState.Idle,
    onUpload: (String) -> Unit = {},
    draftMatchSyncUiState: DraftMatchCloudSyncUiState = DraftMatchCloudSyncUiState.Idle,
    onSyncDraftMatches: (String) -> Unit = {},
    finalizedMatchSyncUiState: FinalizedMatchCloudSyncUiState = FinalizedMatchCloudSyncUiState.Idle,
    onSyncFinalizedMatches: (String) -> Unit = {},
    matchCloudRestorationUiState: MatchCloudRestorationUiState = MatchCloudRestorationUiState.Idle,
    onRestoreMatches: (String) -> Unit = {},
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.tournament_details_loading),
        )

        uiState.isNotFound -> TournamentDetailsNotFoundState(onBackToList)

        uiState.tournament != null -> TournamentDetailsContent(
            tournament = uiState.tournament,
            onBackToList = onBackToList,
            onEnterTeams = onEnterTeams,
            onCreateMatch = onCreateMatch,
            onEnterMatchPlacements = onEnterMatchPlacements,
            onEnterMatchKills = onEnterMatchKills,
            onReviewMatch = onReviewMatch,
            onOpenStandings = onOpenStandings,
            uploadUiState = uploadUiState,
            onUpload = onUpload,
            draftMatchSyncUiState = draftMatchSyncUiState,
            onSyncDraftMatches = onSyncDraftMatches,
            finalizedMatchSyncUiState = finalizedMatchSyncUiState,
            onSyncFinalizedMatches = onSyncFinalizedMatches,
            matchCloudRestorationUiState = matchCloudRestorationUiState,
            onRestoreMatches = onRestoreMatches,
        )
    }
}

@Composable
private fun TournamentDetailsContent(
    tournament: TournamentDetailsItemUiState,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    onCreateMatch: (String) -> Unit,
    onEnterMatchPlacements: (String, String) -> Unit,
    onEnterMatchKills: (String, String) -> Unit,
    onReviewMatch: (String, String) -> Unit,
    onOpenStandings: (String) -> Unit,
    uploadUiState: TournamentCloudUploadUiState,
    onUpload: (String) -> Unit,
    draftMatchSyncUiState: DraftMatchCloudSyncUiState,
    onSyncDraftMatches: (String) -> Unit,
    finalizedMatchSyncUiState: FinalizedMatchCloudSyncUiState,
    onSyncFinalizedMatches: (String) -> Unit,
    matchCloudRestorationUiState: MatchCloudRestorationUiState,
    onRestoreMatches: (String) -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.tournament_details_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Text(
            text = tournament.name,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(text = stringResource(R.string.tournament_date_value, tournament.date.format(detailsDateFormatter)))
        Text(text = stringResource(R.string.organizer_name_value, tournament.organizerName))
        Text(text = stringResource(R.string.organizer_contact_number_value, tournament.organizerContactNumber))
        Text(
            text = stringResource(
                R.string.tournament_status_value,
                stringResource(
                    if (tournament.status == com.hoggamers.rankforge.domain.tournament.TournamentStatus.CONFIRMED) {
                        R.string.tournament_status_confirmed
                    } else {
                        R.string.tournament_status_draft
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = { onEnterTeams(tournament.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.enter_teams_action))
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        TournamentCloudUploadSection(
            tournamentId = tournament.id,
            uiState = uploadUiState,
            onUpload = onUpload,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        DraftMatchCloudSyncSection(
            tournamentId = tournament.id,
            uiState = draftMatchSyncUiState,
            onSync = onSyncDraftMatches,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        FinalizedMatchCloudSyncSection(
            tournamentId = tournament.id,
            uiState = finalizedMatchSyncUiState,
            onSync = onSyncFinalizedMatches,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        TeamSlotList(slots = tournament.slots)
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        MatchList(
            tournament = tournament,
            onCreateMatch = onCreateMatch,
            onEnterMatchPlacements = onEnterMatchPlacements,
            onEnterMatchKills = onEnterMatchKills,
            onReviewMatch = onReviewMatch,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        MatchCloudRestorationSection(tournament.id, matchCloudRestorationUiState, onRestoreMatches)
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = { onOpenStandings(tournament.id) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OPEN_STANDINGS_ACTION_TEST_TAG),
        ) {
            Text(text = stringResource(R.string.open_standings_action))
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onBackToList,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.back_to_tournament_list_action))
        }
    }
}

@Composable
private fun MatchCloudRestorationSection(tournamentId: String, uiState: MatchCloudRestorationUiState, onRestore: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
        Button(onClick = { onRestore(tournamentId) }, enabled = uiState !is MatchCloudRestorationUiState.Loading, modifier = Modifier.fillMaxWidth().testTag(MATCH_CLOUD_RESTORE_ACTION_TEST_TAG)) {
            Text(text = stringResource(if (uiState is MatchCloudRestorationUiState.Loading) R.string.restore_matches_loading else R.string.restore_matches_action))
        }
        Text(text = when (uiState) {
            MatchCloudRestorationUiState.Idle -> stringResource(R.string.restore_matches_ready)
            MatchCloudRestorationUiState.Loading -> stringResource(R.string.restore_matches_loading)
            MatchCloudRestorationUiState.Success -> stringResource(R.string.restore_matches_success)
            MatchCloudRestorationUiState.NoCloudMatches -> stringResource(R.string.restore_matches_none)
            MatchCloudRestorationUiState.AuthenticationRequired -> stringResource(R.string.restore_matches_authentication_required)
            MatchCloudRestorationUiState.AuthorizationFailure -> stringResource(R.string.restore_matches_authorization_failure)
            MatchCloudRestorationUiState.ValidationFailure -> stringResource(R.string.restore_matches_validation_failure)
            MatchCloudRestorationUiState.NetworkFailure -> stringResource(R.string.restore_matches_network_failure)
            MatchCloudRestorationUiState.LocalTransactionFailure -> stringResource(R.string.restore_matches_local_failure)
        }, modifier = Modifier.testTag(MATCH_CLOUD_RESTORE_STATUS_TEST_TAG))
    }
}

@Composable
private fun TournamentCloudUploadSection(
    tournamentId: String,
    uiState: TournamentCloudUploadUiState,
    onUpload: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Button(
            onClick = { onUpload(tournamentId) },
            enabled = uiState !is TournamentCloudUploadUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState is TournamentCloudUploadUiState.Loading) {
                        R.string.upload_tournament_loading
                    } else {
                        R.string.upload_tournament_action
                    },
                ),
            )
        }
        Text(
            text = when (uiState) {
                TournamentCloudUploadUiState.Idle -> stringResource(R.string.upload_tournament_ready_message)
                TournamentCloudUploadUiState.Loading -> stringResource(R.string.upload_tournament_loading)
                TournamentCloudUploadUiState.Success -> stringResource(R.string.upload_tournament_success)
                TournamentCloudUploadUiState.AuthenticationRequired ->
                    stringResource(R.string.upload_tournament_authentication_required)
                TournamentCloudUploadUiState.AuthorizationFailure ->
                    stringResource(R.string.upload_tournament_authorization_failure)
                TournamentCloudUploadUiState.ValidationFailure ->
                    stringResource(R.string.upload_tournament_validation_failure)
                TournamentCloudUploadUiState.NetworkFailure ->
                    stringResource(R.string.upload_tournament_network_failure)
                is TournamentCloudUploadUiState.PartialFailure ->
                    stringResource(R.string.upload_tournament_partial_failure)
            },
            modifier = Modifier.testTag(TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG),
        )
    }
}

@Composable
private fun DraftMatchCloudSyncSection(
    tournamentId: String,
    uiState: DraftMatchCloudSyncUiState,
    onSync: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Button(
            onClick = { onSync(tournamentId) },
            enabled = uiState !is DraftMatchCloudSyncUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState is DraftMatchCloudSyncUiState.Loading) {
                        R.string.sync_draft_matches_loading
                    } else {
                        R.string.sync_draft_matches_action
                    },
                ),
            )
        }
        Text(
            text = when (uiState) {
                DraftMatchCloudSyncUiState.Idle -> stringResource(R.string.sync_draft_matches_ready_message)
                DraftMatchCloudSyncUiState.Loading -> stringResource(R.string.sync_draft_matches_loading)
                DraftMatchCloudSyncUiState.Success -> stringResource(R.string.sync_draft_matches_success)
                DraftMatchCloudSyncUiState.AuthenticationRequired ->
                    stringResource(R.string.sync_draft_matches_authentication_required)
                DraftMatchCloudSyncUiState.AuthorizationFailure ->
                    stringResource(R.string.sync_draft_matches_authorization_failure)
                DraftMatchCloudSyncUiState.ValidationFailure ->
                    stringResource(R.string.sync_draft_matches_validation_failure)
                DraftMatchCloudSyncUiState.NetworkFailure ->
                    stringResource(R.string.sync_draft_matches_network_failure)
                is DraftMatchCloudSyncUiState.PartialFailure ->
                    stringResource(R.string.sync_draft_matches_partial_failure)
            },
            modifier = Modifier.testTag(DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG),
        )
    }
}

@Composable
private fun FinalizedMatchCloudSyncSection(
    tournamentId: String,
    uiState: FinalizedMatchCloudSyncUiState,
    onSync: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Button(
            onClick = { onSync(tournamentId) },
            enabled = uiState !is FinalizedMatchCloudSyncUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FINALIZED_MATCH_CLOUD_SYNC_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState is FinalizedMatchCloudSyncUiState.Loading) {
                        R.string.sync_finalized_matches_loading
                    } else {
                        R.string.sync_finalized_matches_action
                    },
                ),
            )
        }
        Text(
            text = when (uiState) {
                FinalizedMatchCloudSyncUiState.Idle -> stringResource(R.string.sync_finalized_matches_ready_message)
                FinalizedMatchCloudSyncUiState.Loading -> stringResource(R.string.sync_finalized_matches_loading)
                FinalizedMatchCloudSyncUiState.Success -> stringResource(R.string.sync_finalized_matches_success)
                FinalizedMatchCloudSyncUiState.AuthenticationRequired ->
                    stringResource(R.string.sync_finalized_matches_authentication_required)
                FinalizedMatchCloudSyncUiState.AuthorizationFailure ->
                    stringResource(R.string.sync_finalized_matches_authorization_failure)
                FinalizedMatchCloudSyncUiState.ValidationFailure ->
                    stringResource(R.string.sync_finalized_matches_validation_failure)
                FinalizedMatchCloudSyncUiState.NetworkFailure ->
                    stringResource(R.string.sync_finalized_matches_network_failure)
                is FinalizedMatchCloudSyncUiState.PartialFailure ->
                    stringResource(R.string.sync_finalized_matches_partial_failure)
            },
            modifier = Modifier.testTag(FINALIZED_MATCH_CLOUD_SYNC_STATUS_TEST_TAG),
        )
    }
}

@Composable
private fun MatchList(
    tournament: TournamentDetailsItemUiState,
    onCreateMatch: (String) -> Unit,
    onEnterMatchPlacements: (String, String) -> Unit,
    onEnterMatchKills: (String, String) -> Unit,
    onReviewMatch: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TOURNAMENT_MATCH_LIST_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.matches_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (tournament.status != TournamentStatus.CONFIRMED) {
            Text(text = stringResource(R.string.matches_require_confirmed_roster_message))
        } else if (tournament.matches.size >= com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT) {
            Text(text = stringResource(R.string.match_limit_reached_message))
        } else {
            Button(
                onClick = { onCreateMatch(tournament.id) },
                modifier = Modifier.fillMaxWidth().testTag(CREATE_MATCH_ACTION_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.create_match_action))
            }
        }
        if (tournament.matches.isEmpty()) {
            Text(text = stringResource(R.string.no_matches_message))
        } else {
            tournament.matches.forEach { match ->
                Column(
                    modifier = Modifier.testTag(MATCH_ITEM_TEST_TAG_PREFIX + match.matchNumber),
                ) {
                    Text(text = stringResource(R.string.match_number_value, match.matchNumber))
                    Text(text = stringResource(R.string.match_date_value, match.date.format(detailsDateFormatter)))
                    Text(text = stringResource(R.string.match_map_value, match.mapName))
                    Text(
                        text = stringResource(
                            R.string.match_status_value,
                            if (match.status == MatchStatus.DRAFT) {
                                stringResource(R.string.match_status_draft)
                            } else {
                                stringResource(R.string.match_status_finalized)
                            },
                        ),
                    )
                    if (match.placements.isEmpty()) {
                        Text(text = stringResource(R.string.no_match_placements_message))
                    } else {
                        match.placements.forEach { placement ->
                            Text(
                                text = stringResource(
                                    R.string.match_placement_value,
                                    placement.teamSlotNumber,
                                    placement.position,
                                ),
                            )
                        }
                    }
                    if (match.kills.isEmpty()) {
                        Text(text = stringResource(R.string.no_match_kills_message))
                    } else {
                        match.kills.forEach { kill ->
                            Text(
                                text = stringResource(
                                    R.string.match_kill_value,
                                    kill.teamSlotNumber,
                                    kill.kills,
                                ),
                            )
                        }
                    }
                    if (match.validationIssues.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.match_validation_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag(
                                MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX + match.matchNumber,
                            ),
                        )
                        match.validationIssues.forEach { issue ->
                            Text(
                                text = stringResource(
                                    R.string.match_validation_issue,
                                    issue.teamSlotNumber,
                                    stringResource(issue.error.toMessageRes()),
                                ),
                                modifier = Modifier.testTag(
                                    MATCH_VALIDATION_ISSUE_TEST_TAG_PREFIX +
                                        issue.teamSlotNumber + "_" + issue.error.name,
                                ),
                            )
                        }
                    }
                    if (match.status == MatchStatus.DRAFT) {
                        TextButton(
                            onClick = { onEnterMatchPlacements(tournament.id, match.id) },
                            modifier = Modifier.testTag(MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX + match.matchNumber),
                        ) {
                            Text(text = stringResource(R.string.enter_match_placements_action))
                        }
                        TextButton(
                            onClick = { onEnterMatchKills(tournament.id, match.id) },
                            modifier = Modifier.testTag(MATCH_KILLS_ACTION_TEST_TAG_PREFIX + match.matchNumber),
                        ) {
                            Text(text = stringResource(R.string.enter_match_kills_action))
                        }
                    }
                    TextButton(
                        onClick = { onReviewMatch(tournament.id, match.id) },
                        modifier = Modifier.testTag(MATCH_REVIEW_ACTION_TEST_TAG_PREFIX + match.matchNumber),
                    ) {
                        Text(text = stringResource(R.string.review_match_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSlotList(slots: List<TeamSlotUiState>) {
    Column(
        modifier = Modifier.testTag(TOURNAMENT_SLOT_LIST_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.team_slots_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        slots.forEach { slot ->
            Column(
                modifier = Modifier.testTag(TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX + slot.slotNumber),
            ) {
                Text(
                    text = stringResource(R.string.team_slot_label, slot.slotNumber),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = slot.teamName.ifBlank {
                        stringResource(R.string.empty_team_slot_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TournamentDetailsNotFoundState(
    onBackToList: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.tournament_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(
            text = stringResource(R.string.tournament_not_found_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBackToList) {
            Text(text = stringResource(R.string.back_to_tournament_list_action))
        }
    }
}

const val TOURNAMENT_MATCH_LIST_TEST_TAG = "tournament_match_list"
const val CREATE_MATCH_ACTION_TEST_TAG = "create_match_action"
const val MATCH_ITEM_TEST_TAG_PREFIX = "match_item_"
const val MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX = "match_placement_action_"
const val MATCH_KILLS_ACTION_TEST_TAG_PREFIX = "match_kills_action_"
const val MATCH_REVIEW_ACTION_TEST_TAG_PREFIX = "match_review_action_"
const val MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX = "match_validation_issues_"
const val MATCH_VALIDATION_ISSUE_TEST_TAG_PREFIX = "match_validation_issue_"

private fun MatchResultValidationError.toMessageRes(): Int = when (this) {
    MatchResultValidationError.MISSING_TEAM_RESULT_ROW -> R.string.match_validation_missing_team_result_row
    MatchResultValidationError.DUPLICATE_TEAM -> R.string.match_validation_duplicate_team
    MatchResultValidationError.MISSING_PLACEMENT -> R.string.match_validation_missing_placement
    MatchResultValidationError.DUPLICATE_PLACEMENT -> R.string.match_validation_duplicate_placement
    MatchResultValidationError.INVALID_PLACEMENT -> R.string.match_validation_invalid_placement
    MatchResultValidationError.MISSING_KILLS -> R.string.match_validation_missing_kills
    MatchResultValidationError.INVALID_KILLS -> R.string.match_validation_invalid_kills
}
