package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

private val listDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

const val TOURNAMENT_LIST_SCREEN_TEST_TAG = "tournament_list_screen"
const val TOURNAMENT_LIST_EMPTY_TEST_TAG = "tournament_list_empty"
const val TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX = "tournament_list_item_"
const val TOURNAMENT_LIST_AUTH_ENTRY_TEST_TAG = "tournament_list_auth_entry"

@Composable
fun TournamentListRoute(
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    authUiState: AuthUiState = AuthUiState(),
    onOpenAuth: () -> Unit = {},
    viewModel: TournamentListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TournamentListScreen(
        uiState = uiState,
        authUiState = authUiState,
        onCreateTournament = onCreateTournament,
        onOpenTournamentDetails = onOpenTournamentDetails,
        onOpenAuth = onOpenAuth,
    )
}

@Composable
fun TournamentListPlaceholderScreen(
    onCreateTournament: () -> Unit,
) {
    TournamentListScreen(
        uiState = TournamentListUiState(),
        authUiState = AuthUiState(),
        onCreateTournament = onCreateTournament,
        onOpenTournamentDetails = {},
        onOpenAuth = {},
    )
}

@Composable
fun TournamentListScreen(
    uiState: TournamentListUiState,
    authUiState: AuthUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAuth: () -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(TOURNAMENT_LIST_SCREEN_TEST_TAG),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.tournament_list_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        TournamentListAuthCard(
            authUiState = authUiState,
            onOpenAuth = onOpenAuth,
        )

        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        Button(
            onClick = onCreateTournament,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.open_tournament_creation))
        }

        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        if (uiState.isEmpty) {
            Text(
                text = stringResource(R.string.tournament_list_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(TOURNAMENT_LIST_EMPTY_TEST_TAG),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = uiState.tournaments,
                    key = { tournament -> tournament.id },
                ) { tournament ->
                    TournamentListItemCard(
                        tournament = tournament,
                        onClick = { onOpenTournamentDetails(tournament.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TournamentListAuthCard(
    authUiState: AuthUiState,
    onOpenAuth: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RankForgeSpacing.Medium),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.auth_account_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = when {
                    authUiState.isSessionLoading -> stringResource(R.string.auth_checking_session)
                    authUiState.isSignedIn -> stringResource(
                        R.string.auth_signed_in_as,
                        authUiState.accountEmail ?: stringResource(R.string.auth_unknown_account),
                    )
                    else -> stringResource(R.string.auth_signed_out)
                },
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TOURNAMENT_LIST_AUTH_ENTRY_TEST_TAG),
                ) {
                    Text(text = stringResource(R.string.auth_account_action))
                }
            }
        }
    }
}

@Composable
private fun TournamentListItemCard(
    tournament: TournamentListItemUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RankForgeSpacing.Medium),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = tournament.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(text = stringResource(R.string.tournament_date_value, tournament.date.format(listDateFormatter)))
            Text(text = stringResource(R.string.organizer_name_value, tournament.organizerName))
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
        }
    }
}
