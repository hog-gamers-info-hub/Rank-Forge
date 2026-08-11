package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val ALL_TOURNAMENTS_SCREEN_TEST_TAG = "all_tournaments_screen"
const val ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG = "all_tournaments_home_action"
const val ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG = "all_tournaments_back_action"
const val ALL_TOURNAMENTS_LOCAL_HEADING_TEST_TAG = "all_tournaments_local_heading"
const val ALL_TOURNAMENTS_CLOUD_HEADING_TEST_TAG = "all_tournaments_cloud_heading"

@Composable
fun AllTournamentsRoute(
    onHome: () -> Unit,
    onBack: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    viewModel: TournamentListViewModel? = null,
    restorationViewModel: TournamentCloudRestorationViewModel? = null,
) {
    val resolvedViewModel = viewModel ?: hiltViewModel<TournamentListViewModel>()
    val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()

    val restorationUiState = if (restorationViewModel == null) {
        null
    } else {
        val state by restorationViewModel.uiState.collectAsStateWithLifecycle()
        state
    }

    AllTournamentsScreen(
        uiState = uiState,
        restorationUiState = restorationUiState,
        onHome = onHome,
        onBack = onBack,
        onOpenTournamentDetails = onOpenTournamentDetails,
        onLoadCloudTournaments = {
            restorationViewModel?.loadAvailable()
        },
        onRestoreCloudTournament = { tournamentId ->
            restorationViewModel?.restore(tournamentId)
        },
    )
}

@Composable
fun AllTournamentsScreen(
    uiState: TournamentListUiState,
    restorationUiState: TournamentCloudRestorationUiState?,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onLoadCloudTournaments: () -> Unit = {},
    onRestoreCloudTournament: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    RankForgeScreenContainer(
        modifier = Modifier.testTag(ALL_TOURNAMENTS_SCREEN_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onHome,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.auth_home_action))
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.back_action))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            item {
                Text(
                    text = stringResource(R.string.all_tournaments_local_heading),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag(
                        ALL_TOURNAMENTS_LOCAL_HEADING_TEST_TAG,
                    ),
                )
            }

            if (uiState.isEmpty) {
                item {
                    Text(
                        text = stringResource(
                            R.string.tournament_list_empty_message,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(
                            TOURNAMENT_LIST_EMPTY_TEST_TAG,
                        ),
                    )
                }
            } else {
                items(
                    items = uiState.tournaments,
                    key = { tournament -> tournament.id },
                ) { tournament ->
                    TournamentListItemCard(
                        tournament = tournament,
                        onClick = {
                            onOpenTournamentDetails(tournament.id)
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(
                        R.string.all_tournaments_cloud_heading,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag(
                        ALL_TOURNAMENTS_CLOUD_HEADING_TEST_TAG,
                    ),
                )
            }

            if (restorationUiState != null) {
                item {
                    TournamentCloudRestorationSection(
                        uiState = restorationUiState,
                        onLoadCloudTournaments = onLoadCloudTournaments,
                        onRestoreCloudTournament = onRestoreCloudTournament,
                    )
                }
            }
        }
    }
}
