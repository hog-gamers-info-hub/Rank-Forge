package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun TournamentDetailsRoute(
    tournamentId: String,
    onBackToList: () -> Unit,
    viewModel: TournamentDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TournamentDetailsScreen(
        uiState = uiState,
        onBackToList = onBackToList,
    )
}

@Composable
fun TournamentDetailsScreen(
    uiState: TournamentDetailsUiState,
    onBackToList: () -> Unit,
) {
    when {
        uiState.isLoading -> RankForgeLoadingState(
            message = stringResource(R.string.tournament_details_loading),
        )

        uiState.isNotFound -> TournamentDetailsNotFoundState(onBackToList)

        uiState.tournament != null -> TournamentDetailsContent(
            tournament = uiState.tournament,
            onBackToList = onBackToList,
        )
    }
}

@Composable
private fun TournamentDetailsContent(
    tournament: TournamentDetailsItemUiState,
    onBackToList: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG),
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
        Text(text = stringResource(R.string.tournament_status_value, stringResource(R.string.tournament_status_draft)))
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
