package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSummariesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentListViewModel @Inject constructor(
    private val observeTournamentSummaries: ObserveTournamentSummariesUseCase,
) : ViewModel() {
    constructor(observeTournaments: ObserveTournamentsUseCase) : this(
        ObserveTournamentSummariesUseCase(observeTournaments),
    )

    private val _uiState = MutableStateFlow(TournamentListUiState())
    val uiState: StateFlow<TournamentListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeTournamentSummaries().collect { summaries ->
                _uiState.update {
                    TournamentListUiState(
                        tournaments = summaries.map { summary -> summary.toListItemUiState() },
                    )
                }
            }
        }
    }
}
