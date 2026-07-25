package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TeamEntryViewModel @Inject constructor(
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val saveTeamSlotNames: SaveTeamSlotNamesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TeamEntryUiState())
    val uiState: StateFlow<TeamEntryUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedTournamentId: String? = null
    private var initializedDraftValues = false

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        initializedDraftValues = false
        loadJob?.cancel()
        _uiState.update { TeamEntryUiState(isLoading = true) }
        loadJob = viewModelScope.launch {
            observeTournamentSlots(tournamentId).collect { slots ->
                if (slots.isEmpty()) {
                    _uiState.update { TeamEntryUiState(isLoading = false) }
                } else if (!initializedDraftValues) {
                    initializedDraftValues = true
                    _uiState.update {
                        TeamEntryUiState(
                            isLoading = false,
                            slots = slots.toTeamEntrySlotUiState(),
                        )
                    }
                }
            }
        }
    }

    fun onTeamNameChanged(
        slotNumber: Int,
        teamName: String,
    ) {
        _uiState.update { current ->
            current.copy(
                slots = current.slots.map { slot ->
                    if (slot.slotNumber == slotNumber) {
                        slot.copy(teamName = teamName)
                    } else {
                        slot
                    }
                },
            )
        }
    }

    fun saveTeamNames() {
        val tournamentId = loadedTournamentId ?: return
        val slotsToSave = uiState.value.slots
        viewModelScope.launch {
            saveTeamSlotNames(
                tournamentId = tournamentId,
                teamNamesBySlotNumber = slotsToSave.associate { slot ->
                    slot.slotNumber to slot.teamName
                },
            )
            _uiState.update { current ->
                current.copy(
                    slots = current.slots.map { slot ->
                        slot.copy(teamName = slot.teamName.trim())
                    },
                )
            }
        }
    }
}
