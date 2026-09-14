package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.LoggedInHomeMenuShell
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary

private val PointIqListCard = Color(0xFF071B3E)
private val PointIqListBorder = Color(0xFF176AF7).copy(alpha = 0.55f)
private val PointIqListHeader = Color(0xFFF6F8FF)
private val PointIqListSecondary = Color(0xFF91AFE0)
private val PointIqListCyan = Color(0xFF17C9F2)

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
    onOpenContactUs: () -> Unit = {},
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
        onOpenContactUs = onOpenContactUs,
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
    onOpenContactUs: () -> Unit = {},
    openDrawerOnEnter: Boolean = false,
    onDrawerOpenRequestConsumed: () -> Unit = {},
) {
    LoggedInHomeMenuShell(
        onOpenAccount = onOpenAuth,
        onOpenAllTournaments = onOpenAllTournaments,
        onOpenContactUs = onOpenContactUs,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        when (uiState) {
            TournamentCloudRestorationUiState.Idle,
            TournamentCloudRestorationUiState.Loading,
            is TournamentCloudRestorationUiState.Restoring,
            -> {
                OutlinedButton(
                    onClick = onLoadCloudTournaments,
                    enabled = uiState is TournamentCloudRestorationUiState.Idle,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PointIqListCard,
                        contentColor = PointIqListHeader,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = PointIqListBorder,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(TOURNAMENT_CLOUD_RESTORATION_ACTION_TEST_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = PointIqListCyan,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (
                            uiState is TournamentCloudRestorationUiState.Loading ||
                            uiState is TournamentCloudRestorationUiState.Restoring
                        ) {
                            stringResource(R.string.restore_tournament_loading)
                        } else {
                            stringResource(R.string.restore_tournament_action)
                        },
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            else -> {
                Text(
                    text = uiState.restoreStatusText(),
                    color = PointIqListSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.testTag(
                        TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message",
                    ),
                )
            }
        }

        if (uiState is TournamentCloudRestorationUiState.Available) {
            if (uiState.tournaments.isEmpty()) {
                Text(
                    text = stringResource(R.string.restore_tournament_empty),
                    color = PointIqListSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
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

@Composable
private fun CloudTournamentRestoreItem(
    tournament: TournamentCloudRestorationSummary,
    onRestore: () -> Unit,
) {
    OutlinedButton(
        onClick = onRestore,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = PointIqListCard,
            contentColor = PointIqListHeader,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = PointIqListBorder,
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + tournament.id),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tournament.name,
                color = PointIqListHeader,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.pointiq_restore_action),
                color = PointIqListCyan,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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
