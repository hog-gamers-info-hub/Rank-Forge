package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadStage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TournamentCloudUploadUiState {
    data object Idle : TournamentCloudUploadUiState
    data object Loading : TournamentCloudUploadUiState
    data object Success : TournamentCloudUploadUiState
    data object AuthenticationRequired : TournamentCloudUploadUiState
    data object AuthorizationFailure : TournamentCloudUploadUiState
    data object ValidationFailure : TournamentCloudUploadUiState
    data object NetworkFailure : TournamentCloudUploadUiState
    data object Queued : TournamentCloudUploadUiState
    data object QueuePersistenceFailure : TournamentCloudUploadUiState
    data class PartialFailure(
        val completedStage: TournamentCloudUploadStage,
    ) : TournamentCloudUploadUiState
}

@HiltViewModel
class TournamentCloudUploadViewModel @Inject constructor(
    private val uploadTournament: TournamentCloudUploadAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TournamentCloudUploadUiState>(TournamentCloudUploadUiState.Idle)
    val uiState: StateFlow<TournamentCloudUploadUiState> = _uiState.asStateFlow()

    fun upload(tournamentId: String) {
        if (_uiState.value is TournamentCloudUploadUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = TournamentCloudUploadUiState.Loading
            val result = try {
                uploadTournament(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = TournamentCloudUploadUiState.NetworkFailure
                return@launch
            }
            _uiState.value = result.toUiState()
        }
    }

    fun reset() {
        _uiState.value = TournamentCloudUploadUiState.Idle
    }
}

private fun QueueAwareActionResult<TournamentCloudUploadResult>.toUiState(): TournamentCloudUploadUiState {
    if (primaryResult == TournamentCloudUploadResult.Success) {
        return TournamentCloudUploadUiState.Success
    }

    return when (queueRecordingResult) {
        QueueRecordingResult.RECORDED -> TournamentCloudUploadUiState.Queued
        QueueRecordingResult.PERSISTENCE_FAILED -> TournamentCloudUploadUiState.QueuePersistenceFailure
        QueueRecordingResult.NOT_REQUIRED -> primaryResult.toUiState()
    }
}

private fun TournamentCloudUploadResult.toUiState(): TournamentCloudUploadUiState = when (this) {
    TournamentCloudUploadResult.Success -> TournamentCloudUploadUiState.Success
    TournamentCloudUploadResult.AuthenticationRequired ->
        TournamentCloudUploadUiState.AuthenticationRequired
    TournamentCloudUploadResult.ValidationFailure ->
        TournamentCloudUploadUiState.ValidationFailure
    TournamentCloudUploadResult.AuthorizationFailure ->
        TournamentCloudUploadUiState.AuthorizationFailure
    TournamentCloudUploadResult.NetworkFailure -> TournamentCloudUploadUiState.NetworkFailure
    is TournamentCloudUploadResult.Conflict -> TournamentCloudUploadUiState.ValidationFailure
    is TournamentCloudUploadResult.PartialFailure ->
        TournamentCloudUploadUiState.PartialFailure(completedStage)
}
