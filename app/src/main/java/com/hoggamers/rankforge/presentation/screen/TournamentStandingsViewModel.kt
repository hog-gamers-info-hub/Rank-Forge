package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentStandingsViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val cumulativeStandings: CumulativeTournamentStandingsEngine,
    private val tieBreakRules: TieBreakRules,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentStandingsUiState())
    val uiState: StateFlow<TournamentStandingsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        loadJob?.cancel()
        _uiState.value = TournamentStandingsUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            observeMatches(tournamentId).collect { matches ->
                _uiState.value = TournamentStandingsUiState(
                    isLoading = false,
                    rows = tieBreakRules(cumulativeStandings(matches))
                        .toTournamentStandingsUiState(),
                )
            }
        }
    }
}
