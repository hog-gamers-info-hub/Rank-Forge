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

private val detailsDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

const val TOURNAMENT_DETAILS_SCREEN_TEST_TAG = "tournament_details_screen"
const val TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG = "tournament_details_not_found"
const val TOURNAMENT_SLOT_LIST_TEST_TAG = "tournament_slot_list"
const val TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX = "tournament_slot_item_"

@Composable
fun TournamentDetailsRoute(
    tournamentId: String,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
    viewModel: TournamentDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TournamentDetailsScreen(
        uiState = uiState,
        onBackToList = onBackToList,
        onEnterTeams = onEnterTeams,
    )
}

@Composable
fun TournamentDetailsScreen(
    uiState: TournamentDetailsUiState,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
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
        )
    }
}

@Composable
private fun TournamentDetailsContent(
    tournament: TournamentDetailsItemUiState,
    onBackToList: () -> Unit,
    onEnterTeams: (String) -> Unit,
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
        TeamSlotList(slots = tournament.slots)
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
