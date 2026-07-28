package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.ConflictOperation
import com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext
import com.hoggamers.rankforge.domain.tournament.ConflictResolvability
import com.hoggamers.rankforge.domain.tournament.DraftConflictResolutionResult
import com.hoggamers.rankforge.domain.tournament.DraftConflictResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DRAFT_CONFLICT_RESOLUTION_SCREEN_TEST_TAG = "draft_conflict_resolution_screen"
const val DRAFT_CONFLICT_KEEP_LOCAL_TEST_TAG = "draft_conflict_keep_local"
const val DRAFT_CONFLICT_ACCEPT_CLOUD_TEST_TAG = "draft_conflict_accept_cloud"
const val DRAFT_CONFLICT_CONFIRM_ACCEPT_TEST_TAG = "draft_conflict_confirm_accept_cloud"

sealed interface DraftConflictResolutionUiState {
    data class Ready(val context: ConflictResolutionContext) : DraftConflictResolutionUiState
    data object Loading : DraftConflictResolutionUiState
    data class ConfirmAcceptCloud(val context: ConflictResolutionContext) : DraftConflictResolutionUiState
    data object KeepLocalSucceeded : DraftConflictResolutionUiState
    data object AcceptedCloudDraft : DraftConflictResolutionUiState
    data object Blocked : DraftConflictResolutionUiState
    data object Failed : DraftConflictResolutionUiState
}

@HiltViewModel
class DraftConflictResolutionViewModel @Inject constructor(
    private val resolver: DraftConflictResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DraftConflictResolutionUiState>(DraftConflictResolutionUiState.Loading)
    val uiState: StateFlow<DraftConflictResolutionUiState> = _uiState.asStateFlow()

    fun load(tournamentId: String, currentCloudRevision: Int) {
        val current = CloudRevision(currentCloudRevision)
        _uiState.value = DraftConflictResolutionUiState.Ready(
            ConflictResolutionContext(
                tournamentId = tournamentId,
                operation = ConflictOperation.DRAFT_MATCH_SYNC,
                conflict = RevisionConflict.StaleWrite(current, current),
                resolvability = ConflictResolvability.DRAFT_RESOLVABLE,
                currentCloudRevision = current,
            ),
        )
    }

    fun keepLocal() = resolve { resolver.keepLocal(it) }
    fun requestAcceptCloud() {
        (_uiState.value as? DraftConflictResolutionUiState.Ready)?.let { ready ->
            _uiState.value = DraftConflictResolutionUiState.ConfirmAcceptCloud(ready.context)
        }
    }
    fun cancelAcceptCloud() {
        (_uiState.value as? DraftConflictResolutionUiState.ConfirmAcceptCloud)?.let { confirmation ->
            _uiState.value = DraftConflictResolutionUiState.Ready(confirmation.context)
        }
    }
    fun acceptCloud() = resolve { resolver.acceptCloudDraft(it) }

    private fun resolve(action: suspend (ConflictResolutionContext) -> DraftConflictResolutionResult) {
        val context = when (val current = _uiState.value) {
            is DraftConflictResolutionUiState.Ready -> current.context
            is DraftConflictResolutionUiState.ConfirmAcceptCloud -> current.context
            else -> return
        }
        viewModelScope.launch {
            _uiState.value = DraftConflictResolutionUiState.Loading
            _uiState.value = when (action(context)) {
                DraftConflictResolutionResult.KeepLocalSucceeded -> DraftConflictResolutionUiState.KeepLocalSucceeded
                DraftConflictResolutionResult.AcceptedCloudDraft -> DraftConflictResolutionUiState.AcceptedCloudDraft
                is DraftConflictResolutionResult.Conflict -> DraftConflictResolutionUiState.Ready(context)
                DraftConflictResolutionResult.Unsupported, DraftConflictResolutionResult.Deferred -> DraftConflictResolutionUiState.Blocked
                DraftConflictResolutionResult.Failed -> DraftConflictResolutionUiState.Failed
            }
        }
    }
}

@Composable
fun DraftConflictResolutionRoute(
    tournamentId: String,
    currentCloudRevision: Int,
    onBack: () -> Unit,
    viewModel: DraftConflictResolutionViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(tournamentId, currentCloudRevision) {
        viewModel.load(tournamentId, currentCloudRevision)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DraftConflictResolutionScreen(state, viewModel::keepLocal, viewModel::requestAcceptCloud, viewModel::acceptCloud, onBack)
}

@Composable
fun DraftConflictResolutionScreen(
    state: DraftConflictResolutionUiState,
    onKeepLocal: () -> Unit,
    onRequestAcceptCloud: () -> Unit,
    onAcceptCloud: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(DRAFT_CONFLICT_RESOLUTION_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(com.hoggamers.rankforge.presentation.theme.RankForgeSpacing.Small),
    ) {
        Text(stringResource(R.string.draft_conflict_resolution_title))
        Text(stringResource(R.string.draft_conflict_resolution_message))
        when (state) {
            is DraftConflictResolutionUiState.Ready -> {
                Button(onClick = onKeepLocal, modifier = Modifier.fillMaxWidth().testTag(DRAFT_CONFLICT_KEEP_LOCAL_TEST_TAG)) { Text(stringResource(R.string.keep_local_draft_action)) }
                Button(onClick = onRequestAcceptCloud, modifier = Modifier.fillMaxWidth().testTag(DRAFT_CONFLICT_ACCEPT_CLOUD_TEST_TAG)) { Text(stringResource(R.string.accept_cloud_draft_action)) }
            }
            DraftConflictResolutionUiState.Loading -> Text(stringResource(R.string.draft_conflict_resolving))
            DraftConflictResolutionUiState.KeepLocalSucceeded -> Text(stringResource(R.string.draft_conflict_keep_local_success))
            DraftConflictResolutionUiState.AcceptedCloudDraft -> Text(stringResource(R.string.draft_conflict_accept_cloud_success))
            DraftConflictResolutionUiState.Blocked -> Text(stringResource(R.string.draft_conflict_blocked))
            DraftConflictResolutionUiState.Failed -> Text(stringResource(R.string.draft_conflict_failed))
            is DraftConflictResolutionUiState.ConfirmAcceptCloud -> Unit
        }
        Spacer(Modifier.height(com.hoggamers.rankforge.presentation.theme.RankForgeSpacing.Small))
        TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_tournament_details_action)) }
    }
    if (state is DraftConflictResolutionUiState.ConfirmAcceptCloud) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.accept_cloud_draft_title)) },
            text = { Text(stringResource(R.string.accept_cloud_draft_message)) },
            confirmButton = { TextButton(onClick = onAcceptCloud, modifier = Modifier.testTag(DRAFT_CONFLICT_CONFIRM_ACCEPT_TEST_TAG)) { Text(stringResource(R.string.accept_cloud_draft_action)) } },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.cancel_action)) } },
        )
    }
}
