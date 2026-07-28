package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MatchCloudRestorationUiState {
    data object Idle : MatchCloudRestorationUiState
    data object Loading : MatchCloudRestorationUiState
    data object Success : MatchCloudRestorationUiState
    data object NoCloudMatches : MatchCloudRestorationUiState
    data object AuthenticationRequired : MatchCloudRestorationUiState
    data object AuthorizationFailure : MatchCloudRestorationUiState
    data object ValidationFailure : MatchCloudRestorationUiState
    data object NetworkFailure : MatchCloudRestorationUiState
    data object LocalTransactionFailure : MatchCloudRestorationUiState
    data object Queued : MatchCloudRestorationUiState
    data object QueuePersistenceFailure : MatchCloudRestorationUiState
}

@HiltViewModel
class MatchCloudRestorationViewModel @Inject constructor(
    private val restoreMatches: MatchCloudRestorationAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchCloudRestorationUiState>(MatchCloudRestorationUiState.Idle)
    val uiState: StateFlow<MatchCloudRestorationUiState> = _uiState.asStateFlow()
    fun restore(tournamentId: String) { if (_uiState.value !is MatchCloudRestorationUiState.Loading) viewModelScope.launch { _uiState.value = MatchCloudRestorationUiState.Loading; _uiState.value = try { restoreMatches(tournamentId).toUiState() } catch (cancellation: CancellationException) { throw cancellation } catch (_: Throwable) { MatchCloudRestorationUiState.NetworkFailure } } }
}

private fun QueueAwareActionResult<MatchCloudRestorationResult>.toUiState(): MatchCloudRestorationUiState {
    if (primaryResult == MatchCloudRestorationResult.Success || primaryResult == MatchCloudRestorationResult.NoCloudMatches) {
        return primaryResult.toUiState()
    }

    return when (queueRecordingResult) {
        QueueRecordingResult.RECORDED -> MatchCloudRestorationUiState.Queued
        QueueRecordingResult.PERSISTENCE_FAILED -> MatchCloudRestorationUiState.QueuePersistenceFailure
        QueueRecordingResult.NOT_REQUIRED -> primaryResult.toUiState()
    }
}

private fun MatchCloudRestorationResult.toUiState(): MatchCloudRestorationUiState = when (this) {
    MatchCloudRestorationResult.Success -> MatchCloudRestorationUiState.Success
    MatchCloudRestorationResult.NoCloudMatches -> MatchCloudRestorationUiState.NoCloudMatches
    MatchCloudRestorationResult.AuthenticationRequired -> MatchCloudRestorationUiState.AuthenticationRequired
    MatchCloudRestorationResult.AuthorizationFailure -> MatchCloudRestorationUiState.AuthorizationFailure
    MatchCloudRestorationResult.ValidationFailure -> MatchCloudRestorationUiState.ValidationFailure
    MatchCloudRestorationResult.NetworkFailure -> MatchCloudRestorationUiState.NetworkFailure
    MatchCloudRestorationResult.LocalTransactionFailure -> MatchCloudRestorationUiState.LocalTransactionFailure
    is MatchCloudRestorationResult.Conflict -> MatchCloudRestorationUiState.ValidationFailure
}
