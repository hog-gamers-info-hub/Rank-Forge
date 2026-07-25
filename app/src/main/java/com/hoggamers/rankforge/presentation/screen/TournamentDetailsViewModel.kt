package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentDetailsViewModel @Inject constructor(
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentDetailsUiState())
    val uiState: StateFlow<TournamentDetailsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        loadJob?.cancel()
        _uiState.update { TournamentDetailsUiState(isLoading = true) }
        loadJob = viewModelScope.launch {
            combine(
                getTournamentById(tournamentId),
                observeTournamentSlots(tournamentId),
            ) { tournament, slots ->
                if (tournament == null) {
                    TournamentDetailsUiState(isLoading = false)
                } else {
                    TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournament.toDetailsItemUiState(slots),
                    )
                }
            }.collect { uiState ->
                _uiState.update {
                    uiState
                }
            }
        }
    }
}
