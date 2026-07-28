package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncStage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FinalizedMatchCloudSyncUiState {
    data object Idle : FinalizedMatchCloudSyncUiState
    data object Loading : FinalizedMatchCloudSyncUiState
    data object Success : FinalizedMatchCloudSyncUiState
    data object AuthenticationRequired : FinalizedMatchCloudSyncUiState
    data object AuthorizationFailure : FinalizedMatchCloudSyncUiState
    data object ValidationFailure : FinalizedMatchCloudSyncUiState
    data object NetworkFailure : FinalizedMatchCloudSyncUiState
    data class PartialFailure(
        val completedStage: FinalizedMatchCloudSyncStage,
    ) : FinalizedMatchCloudSyncUiState
}

@HiltViewModel
class FinalizedMatchCloudSyncViewModel @Inject constructor(
    private val syncFinalizedMatches: FinalizedMatchCloudSyncAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FinalizedMatchCloudSyncUiState>(
        FinalizedMatchCloudSyncUiState.Idle,
    )
    val uiState: StateFlow<FinalizedMatchCloudSyncUiState> = _uiState.asStateFlow()

    fun sync(tournamentId: String) {
        if (_uiState.value is FinalizedMatchCloudSyncUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = FinalizedMatchCloudSyncUiState.Loading
            val result = try {
                syncFinalizedMatches(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                FinalizedMatchCloudSyncResult.NetworkFailure
            }
            _uiState.value = result.toUiState()
        }
    }

    fun reset() {
        _uiState.value = FinalizedMatchCloudSyncUiState.Idle
    }
}

private fun FinalizedMatchCloudSyncResult.toUiState(): FinalizedMatchCloudSyncUiState = when (this) {
    FinalizedMatchCloudSyncResult.Success -> FinalizedMatchCloudSyncUiState.Success
    FinalizedMatchCloudSyncResult.AuthenticationRequired ->
        FinalizedMatchCloudSyncUiState.AuthenticationRequired
    FinalizedMatchCloudSyncResult.ValidationFailure -> FinalizedMatchCloudSyncUiState.ValidationFailure
    FinalizedMatchCloudSyncResult.AuthorizationFailure -> FinalizedMatchCloudSyncUiState.AuthorizationFailure
    FinalizedMatchCloudSyncResult.NetworkFailure -> FinalizedMatchCloudSyncUiState.NetworkFailure
    is FinalizedMatchCloudSyncResult.PartialFailure ->
        FinalizedMatchCloudSyncUiState.PartialFailure(completedStage)
}
