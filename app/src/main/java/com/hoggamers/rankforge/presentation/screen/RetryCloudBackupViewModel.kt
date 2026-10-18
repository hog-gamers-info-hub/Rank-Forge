package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.RetryCloudBackupAction
import com.hoggamers.rankforge.domain.tournament.RetryCloudBackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RetryCloudBackupUiState {
    data object Idle : RetryCloudBackupUiState
    data object Loading : RetryCloudBackupUiState
    data object Success : RetryCloudBackupUiState
    data object AuthenticationRequired : RetryCloudBackupUiState
    data object AuthorizationFailure : RetryCloudBackupUiState
    data object DeletionBlocked : RetryCloudBackupUiState
    data object LocalDataIncomplete : RetryCloudBackupUiState
    data object RevisionConflict : RetryCloudBackupUiState
    data object NetworkFailure : RetryCloudBackupUiState
    data object ValidationFailure : RetryCloudBackupUiState
    data object QueuePersistenceFailure : RetryCloudBackupUiState
    data object PartialFailure : RetryCloudBackupUiState
    data object Failure : RetryCloudBackupUiState
}

@HiltViewModel
class RetryCloudBackupViewModel @Inject constructor(
    private val retryCloudBackup: RetryCloudBackupAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RetryCloudBackupUiState>(RetryCloudBackupUiState.Idle)
    val uiState: StateFlow<RetryCloudBackupUiState> = _uiState.asStateFlow()

    fun retry(tournamentId: String) {
        if (_uiState.value is RetryCloudBackupUiState.Loading) return
        _uiState.value = RetryCloudBackupUiState.Loading
        viewModelScope.launch {
            val result = try {
                retryCloudBackup(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                RetryCloudBackupResult.Failure
            }
            _uiState.value = result.toUiState()
        }
    }

    fun reset() {
        _uiState.value = RetryCloudBackupUiState.Idle
    }
}

private fun RetryCloudBackupResult.toUiState(): RetryCloudBackupUiState = when (this) {
    RetryCloudBackupResult.Success -> RetryCloudBackupUiState.Success
    RetryCloudBackupResult.AuthenticationRequired -> RetryCloudBackupUiState.AuthenticationRequired
    RetryCloudBackupResult.AuthorizationFailure -> RetryCloudBackupUiState.AuthorizationFailure
    RetryCloudBackupResult.DeletionBlocked -> RetryCloudBackupUiState.DeletionBlocked
    RetryCloudBackupResult.LocalDataIncomplete -> RetryCloudBackupUiState.LocalDataIncomplete
    RetryCloudBackupResult.RevisionConflict -> RetryCloudBackupUiState.RevisionConflict
    RetryCloudBackupResult.NetworkFailure -> RetryCloudBackupUiState.NetworkFailure
    RetryCloudBackupResult.ValidationFailure -> RetryCloudBackupUiState.ValidationFailure
    RetryCloudBackupResult.QueuePersistenceFailure -> RetryCloudBackupUiState.QueuePersistenceFailure
    RetryCloudBackupResult.PartialFailure -> RetryCloudBackupUiState.PartialFailure
    RetryCloudBackupResult.Failure -> RetryCloudBackupUiState.Failure
}
