package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
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
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
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
                validationIssues = emptyList(),
                hasTeamNameGap = false,
            )
        }
    }

    fun saveTeamNames() {
        val tournamentId = loadedTournamentId ?: return
        val slotsToSave = uiState.value.slots
        val teamNamesBySlotNumber = slotsToSave.associate { slot ->
            slot.slotNumber to slot.teamName.trim()
        }
        val participation = teamNamesBySlotNumber.analyzeTeamSlotParticipation()
        if (participation.hasGap) {
            _uiState.update {
                it.copy(
                    hasTeamNameGap = true,
                    hasSaveError = false,
                )
            }
            return
        }
        persistTeamNames(
            tournamentId = tournamentId,
            teamNamesBySlotNumber = teamNamesBySlotNumber,
            activeSlotNumbers = participation.activeSlotNumbers.toSet(),
        )
    }

    private fun persistTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
        activeSlotNumbers: Set<Int>,
    ) {
        _uiState.update {
            it.copy(
                isSaving = true,
                hasSaveError = false,
                hasTeamNameGap = false,
            )
        }
        viewModelScope.launch {
            runCatching {
                val validation = validateTournamentRoster(
                    tournamentId = tournamentId,
                    teamNamesBySlotNumber = teamNamesBySlotNumber,
                    activeTeamSlotNumbers = activeSlotNumbers,
                )
                if (validation.hasBlockingIssues) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            validationIssues = validation.toUiState(),
                        )
                    }
                } else {
                    saveTeamSlotNames(
                        tournamentId = tournamentId,
                        teamNamesBySlotNumber = teamNamesBySlotNumber,
                    )
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            validationIssues = validation.toUiState(),
                            slots = current.slots.map { slot ->
                                slot.copy(teamName = teamNamesBySlotNumber.getValue(slot.slotNumber))
                            },
                        )
                    }
                }
            }.onFailure {
                _uiState.update {
                    it.copy(isSaving = false, hasSaveError = true)
                }
            }
        }
    }

}
