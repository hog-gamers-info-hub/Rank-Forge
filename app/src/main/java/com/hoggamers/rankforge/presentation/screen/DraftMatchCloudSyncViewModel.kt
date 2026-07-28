package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncStage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DraftMatchCloudSyncUiState {
    data object Idle : DraftMatchCloudSyncUiState
    data object Loading : DraftMatchCloudSyncUiState
    data object Success : DraftMatchCloudSyncUiState
    data object AuthenticationRequired : DraftMatchCloudSyncUiState
    data object AuthorizationFailure : DraftMatchCloudSyncUiState
    data object ValidationFailure : DraftMatchCloudSyncUiState
    data object NetworkFailure : DraftMatchCloudSyncUiState
    data class PartialFailure(
        val completedStage: DraftMatchCloudSyncStage,
    ) : DraftMatchCloudSyncUiState
}

@HiltViewModel
class DraftMatchCloudSyncViewModel @Inject constructor(
    private val syncDraftMatches: DraftMatchCloudSyncAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DraftMatchCloudSyncUiState>(DraftMatchCloudSyncUiState.Idle)
    val uiState: StateFlow<DraftMatchCloudSyncUiState> = _uiState.asStateFlow()

    fun sync(tournamentId: String) {
        if (_uiState.value is DraftMatchCloudSyncUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = DraftMatchCloudSyncUiState.Loading
            val result = try {
                syncDraftMatches(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                DraftMatchCloudSyncResult.NetworkFailure
            }
            _uiState.value = result.toUiState()
        }
    }

    fun reset() {
        _uiState.value = DraftMatchCloudSyncUiState.Idle
    }
}

private fun DraftMatchCloudSyncResult.toUiState(): DraftMatchCloudSyncUiState = when (this) {
    DraftMatchCloudSyncResult.Success -> DraftMatchCloudSyncUiState.Success
    DraftMatchCloudSyncResult.AuthenticationRequired -> DraftMatchCloudSyncUiState.AuthenticationRequired
    DraftMatchCloudSyncResult.ValidationFailure -> DraftMatchCloudSyncUiState.ValidationFailure
    DraftMatchCloudSyncResult.AuthorizationFailure -> DraftMatchCloudSyncUiState.AuthorizationFailure
    DraftMatchCloudSyncResult.NetworkFailure -> DraftMatchCloudSyncUiState.NetworkFailure
    is DraftMatchCloudSyncResult.PartialFailure -> DraftMatchCloudSyncUiState.PartialFailure(completedStage)
}
