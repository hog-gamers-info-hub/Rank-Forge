package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.LoggedInHomeMenuShell
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary

private val PointIqListNavy = Color(0xFF071B3E)
private val PointIqListBlue = Color(0xFF176AF7)
private val PointIqListBody = Color(0xFF607393)
private val PointIqListContainer = Color(0xFFF7FAFF)

const val TOURNAMENT_LIST_SCREEN_TEST_TAG = "tournament_list_screen"
const val TOURNAMENT_LIST_EMPTY_TEST_TAG = "tournament_list_empty"
const val TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX = "tournament_list_item_"
const val TOURNAMENT_CLOUD_RESTORATION_ACTION_TEST_TAG = "tournament_cloud_restoration_action"
const val TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG = "tournament_cloud_restoration_status"
const val TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX = "tournament_cloud_restoration_item_"

@Composable
fun TournamentListRoute(
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAuth: () -> Unit = {},
    onOpenAllTournaments: () -> Unit = {},
    openDrawerOnEnter: Boolean = false,
    onDrawerOpenRequestConsumed: () -> Unit = {},
    viewModel: TournamentListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TournamentListScreen(
        uiState = uiState,
        onCreateTournament = onCreateTournament,
        onOpenTournamentDetails = onOpenTournamentDetails,
        onOpenAuth = onOpenAuth,
        onOpenAllTournaments = onOpenAllTournaments,
        openDrawerOnEnter = openDrawerOnEnter,
        onDrawerOpenRequestConsumed = onDrawerOpenRequestConsumed,
    )
}

@Composable
fun TournamentListPlaceholderScreen(
    onCreateTournament: () -> Unit,
) {
    TournamentListScreen(
        uiState = TournamentListUiState(),
        onCreateTournament = onCreateTournament,
        onOpenTournamentDetails = {},
        onOpenAuth = {},
    )
}

@Composable
fun TournamentListScreen(
    uiState: TournamentListUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAuth: () -> Unit,
    onOpenAllTournaments: () -> Unit = {},
    openDrawerOnEnter: Boolean = false,
    onDrawerOpenRequestConsumed: () -> Unit = {},
) {
    LoggedInHomeMenuShell(
        onOpenAccount = onOpenAuth,
        onOpenAllTournaments = onOpenAllTournaments,
        content = {
            LoggedInTournamentHomeContent(
                uiState = uiState,
                onCreateTournament = onCreateTournament,
                onOpenTournamentDetails = onOpenTournamentDetails,
                onOpenAllTournaments = onOpenAllTournaments,
            )
        },
        openDrawerOnEnter = openDrawerOnEnter,
        onDrawerOpenRequestConsumed = onDrawerOpenRequestConsumed,
    )
}

@Composable
private fun LoggedInTournamentHomeContent(
    uiState: TournamentListUiState,
    onCreateTournament: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onOpenAllTournaments: () -> Unit,
) {
    PointIqTournamentHomeContent(
        uiState = uiState,
        onCreateTournament = onCreateTournament,
        onOpenTournamentDetails = onOpenTournamentDetails,
        onOpenAllTournaments = onOpenAllTournaments,
    )
}

@Composable
internal fun TournamentCloudRestorationSection(
    uiState: TournamentCloudRestorationUiState,
    onLoadCloudTournaments: () -> Unit,
    onRestoreCloudTournament: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(RankForgeSpacing.Medium),
        colors = CardDefaults.cardColors(containerColor = PointIqListContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            Button(
                onClick = onLoadCloudTournaments,
                enabled = uiState !is TournamentCloudRestorationUiState.Loading &&
                    uiState !is TournamentCloudRestorationUiState.Restoring,
                colors = ButtonDefaults.buttonColors(containerColor = PointIqListBlue),
                shape = RoundedCornerShape(RankForgeSpacing.Medium),
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
                style = MaterialTheme.typography.bodyMedium,
                color = PointIqListBody,
                modifier = Modifier.testTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message"),
            )
            if (uiState is TournamentCloudRestorationUiState.Available) {
                if (uiState.tournaments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.restore_tournament_empty),
                        color = PointIqListBody,
                    )
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PointIqListBlue),
        shape = RoundedCornerShape(RankForgeSpacing.Medium),
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
internal fun TournamentListItemCard(
    tournament: TournamentListItemUiState,
    onClick: () -> Unit,
) {
    PointIqTournamentSummaryCard(
        tournament = tournament,
        onClick = onClick,
    )
}
