package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import com.hoggamers.rankforge.domain.tournament.DeleteTournamentResult
import com.hoggamers.rankforge.domain.tournament.DeleteTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSummariesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun DeleteTournamentResult.toUiError(): TournamentDeletionUiError = when (this) {
    DeleteTournamentResult.TargetNotFound -> TournamentDeletionUiError.TARGET_NOT_FOUND
    DeleteTournamentResult.AuthenticationRequired -> TournamentDeletionUiError.AUTHENTICATION_REQUIRED
    DeleteTournamentResult.PendingSyncPreparationFailed -> TournamentDeletionUiError.PREPARATION_FAILURE
    DeleteTournamentResult.RemoteDeletedLocalCleanupFailed -> TournamentDeletionUiError.LOCAL_CLEANUP_FAILURE
    is DeleteTournamentResult.StorageDeletionFailed -> category.toUiError(TournamentDeletionUiError.STORAGE_FAILURE)
    is DeleteTournamentResult.RemoteDeletionFailed -> category.toUiError(TournamentDeletionUiError.REMOTE_FAILURE)
    DeleteTournamentResult.Success -> error("Successful deletion has no UI error")
}

private fun CloudDeletionFailureCategory.toUiError(default: TournamentDeletionUiError): TournamentDeletionUiError = when (this) {
    CloudDeletionFailureCategory.AUTHENTICATION -> TournamentDeletionUiError.AUTHENTICATION_REQUIRED
    CloudDeletionFailureCategory.AUTHORIZATION -> TournamentDeletionUiError.AUTHORIZATION_FAILURE
    CloudDeletionFailureCategory.VALIDATION -> TournamentDeletionUiError.VALIDATION_FAILURE
    else -> default
}

@HiltViewModel
class TournamentListViewModel @Inject constructor(
    private val observeTournamentSummaries: ObserveTournamentSummariesUseCase,
    private val deleteTournamentUseCase: DeleteTournamentUseCase? = null,
) : ViewModel() {
    constructor(observeTournaments: ObserveTournamentsUseCase) : this(
        ObserveTournamentSummariesUseCase(observeTournaments),
    )

    private val _uiState = MutableStateFlow(TournamentListUiState())
    val uiState: StateFlow<TournamentListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeTournamentSummaries().collect { summaries ->
                _uiState.update { current ->
                    current.copy(
                        tournaments = summaries.map { summary -> summary.toListItemUiState() },
                    )
                }
            }
        }
    }

    fun deleteTournament(tournamentId: String) {
        val current = _uiState.value
        if (current.isDeleting || tournamentId.isBlank()) return
        _uiState.update {
            it.copy(
                isDeleting = true,
                deletionError = null,
            )
        }
        viewModelScope.launch {
            val useCase = deleteTournamentUseCase ?: run {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = TournamentDeletionUiError.UNKNOWN,
                    )
                }
                return@launch
            }
            val result = try {
                useCase(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = TournamentDeletionUiError.UNKNOWN,
                    )
                }
                return@launch
            }
            if (result == DeleteTournamentResult.Success) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = result.toUiError(),
                    )
                }
            }
        }
    }
}
