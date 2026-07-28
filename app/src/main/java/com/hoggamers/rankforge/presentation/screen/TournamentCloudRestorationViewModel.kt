package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TournamentCloudRestorationUiState {
    data object Idle : TournamentCloudRestorationUiState
    data object Loading : TournamentCloudRestorationUiState
    data class Available(
        val tournaments: List<com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary>,
    ) : TournamentCloudRestorationUiState
    data class Restoring(val tournamentId: String) : TournamentCloudRestorationUiState
    data class Success(val tournamentName: String) : TournamentCloudRestorationUiState
    data object AuthenticationRequired : TournamentCloudRestorationUiState
    data object AuthorizationFailure : TournamentCloudRestorationUiState
    data object ValidationFailure : TournamentCloudRestorationUiState
    data object NetworkFailure : TournamentCloudRestorationUiState
    data object LocalTransactionFailure : TournamentCloudRestorationUiState
    data object Queued : TournamentCloudRestorationUiState
    data object QueuePersistenceFailure : TournamentCloudRestorationUiState
}

@HiltViewModel
class TournamentCloudRestorationViewModel @Inject constructor(
    private val restoreTournament: TournamentCloudRestorationAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TournamentCloudRestorationUiState>(TournamentCloudRestorationUiState.Idle)
    val uiState: StateFlow<TournamentCloudRestorationUiState> = _uiState.asStateFlow()

    fun loadAvailable() {
        if (_uiState.value is TournamentCloudRestorationUiState.Loading ||
            _uiState.value is TournamentCloudRestorationUiState.Restoring
        ) return
        viewModelScope.launch {
            _uiState.value = TournamentCloudRestorationUiState.Loading
            _uiState.value = runAction { restoreTournament.loadAvailable() }
        }
    }

    fun restore(tournamentId: String) {
        if (_uiState.value is TournamentCloudRestorationUiState.Loading ||
            _uiState.value is TournamentCloudRestorationUiState.Restoring
        ) return
        viewModelScope.launch {
            _uiState.value = TournamentCloudRestorationUiState.Restoring(tournamentId)
            _uiState.value = runRestoreAction { restoreTournament.restore(tournamentId) }
        }
    }

    fun reset() {
        _uiState.value = TournamentCloudRestorationUiState.Idle
    }

    private suspend fun runAction(
        action: suspend () -> TournamentCloudRestorationResult,
    ): TournamentCloudRestorationUiState = try {
        action().toUiState()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        TournamentCloudRestorationUiState.NetworkFailure
    }

    private suspend fun runRestoreAction(
        action: suspend () -> QueueAwareActionResult<TournamentCloudRestorationResult>,
    ): TournamentCloudRestorationUiState = try {
        action().toUiState()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        TournamentCloudRestorationUiState.NetworkFailure
    }
}

private fun QueueAwareActionResult<TournamentCloudRestorationResult>.toUiState(): TournamentCloudRestorationUiState {
    if (primaryResult is TournamentCloudRestorationResult.Success) {
        return primaryResult.toUiState()
    }

    return when (queueRecordingResult) {
        QueueRecordingResult.RECORDED -> TournamentCloudRestorationUiState.Queued
        QueueRecordingResult.PERSISTENCE_FAILED -> TournamentCloudRestorationUiState.QueuePersistenceFailure
        QueueRecordingResult.NOT_REQUIRED -> primaryResult.toUiState()
    }
}

private fun TournamentCloudRestorationResult.toUiState(): TournamentCloudRestorationUiState = when (this) {
    is TournamentCloudRestorationResult.Available ->
        TournamentCloudRestorationUiState.Available(tournaments)
    is TournamentCloudRestorationResult.Success ->
        TournamentCloudRestorationUiState.Success(tournamentName)
    TournamentCloudRestorationResult.AuthenticationRequired ->
        TournamentCloudRestorationUiState.AuthenticationRequired
    TournamentCloudRestorationResult.AuthorizationFailure ->
        TournamentCloudRestorationUiState.AuthorizationFailure
    TournamentCloudRestorationResult.ValidationFailure ->
        TournamentCloudRestorationUiState.ValidationFailure
    TournamentCloudRestorationResult.NetworkFailure ->
        TournamentCloudRestorationUiState.NetworkFailure
    TournamentCloudRestorationResult.LocalTransactionFailure ->
        TournamentCloudRestorationUiState.LocalTransactionFailure
}
