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
import com.hoggamers.rankforge.presentation.component.LoggedInHomeMenuShell
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary

private val listDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

const val TOURNAMENT_LIST_SCREEN_TEST_TAG = "tournament_list_screen"
const val TOURNAMENT_LIST_EMPTY_TEST_TAG = "tournament_list_empty"
const val TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX = "tournament_list_item_"
const val TOURNAMENT_LIST_AUTH_ENTRY_TEST_TAG = "tournament_list_auth_entry"
const val TOURNAMENT_CLOUD_RESTORATION_ACTION_TEST_TAG = "tournament_cloud_restoration_action"
const val TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG = "tournament_cloud_restoration_status"
const val TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX = "tournament_cloud_restoration_item_"

@Composable
fun TournamentListRoute(
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    authUiState: AuthUiState = AuthUiState(),
    onOpenAuth: () -> Unit = {},
    onOpenAllTournaments: () -> Unit = {},
    openDrawerOnEnter: Boolean = false,
    onDrawerOpenRequestConsumed: () -> Unit = {},
    viewModel: TournamentListViewModel = hiltViewModel(),
    restorationViewModel: TournamentCloudRestorationViewModel? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restorationUiState = if (restorationViewModel == null) {
        null
    } else {
        val state by restorationViewModel.uiState.collectAsStateWithLifecycle()
        state
    }

    TournamentListScreen(
        uiState = uiState,
        authUiState = authUiState,
        onCreateTournament = onCreateTournament,
        onOpenTournamentDetails = onOpenTournamentDetails,
        onOpenAuth = onOpenAuth,
        onOpenAllTournaments = onOpenAllTournaments,
        openDrawerOnEnter = openDrawerOnEnter,
        onDrawerOpenRequestConsumed = onDrawerOpenRequestConsumed,
        restorationUiState = restorationUiState,
        onLoadCloudTournaments = { restorationViewModel?.loadAvailable() },
        onRestoreCloudTournament = { tournamentId -> restorationViewModel?.restore(tournamentId) },
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
    onOpenAllTournaments: () -> Unit = {},
    openDrawerOnEnter: Boolean = false,
    onDrawerOpenRequestConsumed: () -> Unit = {},
    restorationUiState: TournamentCloudRestorationUiState? = null,
    onLoadCloudTournaments: () -> Unit = {},
    onRestoreCloudTournament: (String) -> Unit = {},
) {
    if (authUiState.isSignedIn) {
        LoggedInHomeMenuShell(
            onOpenAccount = onOpenAuth,
            onOpenAllTournaments = onOpenAllTournaments,
            content = {
                LoggedInTournamentHomeContent(
                    uiState = uiState,
                    onCreateTournament = onCreateTournament,
                    onOpenTournamentDetails = onOpenTournamentDetails,
                )
            },
            openDrawerOnEnter = openDrawerOnEnter,
            onDrawerOpenRequestConsumed = onDrawerOpenRequestConsumed,
        )
    } else {
        TournamentListHomeContent(
            uiState = uiState,
            authUiState = authUiState,
            onCreateTournament = onCreateTournament,
            onOpenTournamentDetails = onOpenTournamentDetails,
            onOpenAuth = onOpenAuth,
            restorationUiState = restorationUiState,
            onLoadCloudTournaments = onLoadCloudTournaments,
            onRestoreCloudTournament = onRestoreCloudTournament,
        )
    }
}

@Composable
private fun LoggedInTournamentHomeContent(
    uiState: TournamentListUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(TOURNAMENT_LIST_SCREEN_TEST_TAG),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Medium),
        ) {
            item {
                Button(
                    onClick = onCreateTournament,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.open_tournament_creation))
                }
            }

            item {
                Text(
                    text = stringResource(R.string.recent_tournaments_heading),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            if (uiState.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.tournament_list_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(TOURNAMENT_LIST_EMPTY_TEST_TAG),
                    )
                }
            } else {
                items(
                    items = uiState.tournaments.takeLast(3),
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
private fun TournamentListHomeContent(
    uiState: TournamentListUiState,
    authUiState: AuthUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAuth: () -> Unit,
    restorationUiState: TournamentCloudRestorationUiState?,
    onLoadCloudTournaments: () -> Unit,
    onRestoreCloudTournament: (String) -> Unit,
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

        if (restorationUiState != null) {
            TournamentCloudRestorationSection(
                uiState = restorationUiState,
                onLoadCloudTournaments = onLoadCloudTournaments,
                onRestoreCloudTournament = onRestoreCloudTournament,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        }

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
internal fun TournamentCloudRestorationSection(
    uiState: TournamentCloudRestorationUiState,
    onLoadCloudTournaments: () -> Unit,
    onRestoreCloudTournament: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RankForgeSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.restore_tournament_action),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onLoadCloudTournaments,
                enabled = uiState !is TournamentCloudRestorationUiState.Loading &&
                    uiState !is TournamentCloudRestorationUiState.Restoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TOURNAMENT_CLOUD_RESTORATION_ACTION_TEST_TAG),
            ) {
                Text(
                    text = if (
                        uiState is TournamentCloudRestorationUiState.Loading ||
                        uiState is TournamentCloudRestorationUiState.Restoring
                    ) {
                        stringResource(R.string.restore_tournament_loading)
                    } else {
                        stringResource(R.string.restore_tournament_action)
                    },
                )
            }
            Text(
                text = uiState.restoreStatusText(),
                modifier = Modifier.testTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message"),
            )
            if (uiState is TournamentCloudRestorationUiState.Available) {
                if (uiState.tournaments.isEmpty()) {
                    Text(text = stringResource(R.string.restore_tournament_empty))
                } else {
                    uiState.tournaments.forEach { tournament ->
                        CloudTournamentRestoreItem(
                            tournament = tournament,
                            onRestore = { onRestoreCloudTournament(tournament.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudTournamentRestoreItem(
    tournament: TournamentCloudRestorationSummary,
    onRestore: () -> Unit,
) {
    OutlinedButton(
        onClick = onRestore,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + tournament.id),
    ) {
        Text(text = stringResource(R.string.restore_tournament_item, tournament.name))
    }
}

@Composable
private fun TournamentCloudRestorationUiState.restoreStatusText(): String = when (this) {
    TournamentCloudRestorationUiState.Idle -> stringResource(R.string.restore_tournament_action)
    TournamentCloudRestorationUiState.Loading,
    is TournamentCloudRestorationUiState.Restoring,
    -> stringResource(R.string.restore_tournament_loading)
    is TournamentCloudRestorationUiState.Available -> stringResource(R.string.restore_tournament_available)
    is TournamentCloudRestorationUiState.Success -> stringResource(
        R.string.restore_tournament_success,
        tournamentName,
    )
    TournamentCloudRestorationUiState.AuthenticationRequired ->
        stringResource(R.string.restore_tournament_authentication_required)
    TournamentCloudRestorationUiState.AuthorizationFailure ->
        stringResource(R.string.restore_tournament_authorization_failure)
    TournamentCloudRestorationUiState.ValidationFailure ->
        stringResource(R.string.restore_tournament_validation_failure)
    TournamentCloudRestorationUiState.NetworkFailure ->
        stringResource(R.string.restore_tournament_network_failure)
    TournamentCloudRestorationUiState.LocalTransactionFailure ->
        stringResource(R.string.restore_tournament_local_failure)
    TournamentCloudRestorationUiState.Queued ->
        stringResource(R.string.restore_tournament_queued)
    TournamentCloudRestorationUiState.QueuePersistenceFailure ->
        stringResource(R.string.restore_tournament_queue_persistence_failed)
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
internal fun TournamentListItemCard(
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
