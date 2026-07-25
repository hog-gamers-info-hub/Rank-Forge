package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val observeTournaments: ObserveTournamentsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentListUiState())
    val uiState: StateFlow<TournamentListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeTournaments().collect { tournaments ->
                _uiState.update {
                    TournamentListUiState(
                        tournaments = tournaments.map { tournament -> tournament.toListItemUiState() },
                    )
                }
            }
        }
    }
}
