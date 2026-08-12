package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
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
            )
        }
    }

    fun saveTeamNames() {
        val tournamentId = loadedTournamentId ?: return
        val slotsToSave = uiState.value.slots
        val resolvedTeamNamesBySlotNumber = slotsToSave.associate { slot ->
            val trimmedName = slot.teamName.trim()
            slot.slotNumber to if (trimmedName.isBlank()) {
                defaultTeamName(slot.slotNumber)
            } else {
                trimmedName
            }
        }
        _uiState.update { it.copy(isSaving = true, hasSaveError = false) }
        viewModelScope.launch {
            runCatching {
                val validation = validateTournamentRoster(
                    tournamentId = tournamentId,
                    teamNamesBySlotNumber = resolvedTeamNamesBySlotNumber,
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
                        teamNamesBySlotNumber = resolvedTeamNamesBySlotNumber,
                    )
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            validationIssues = validation.toUiState(),
                            slots = current.slots.map { slot ->
                                slot.copy(teamName = resolvedTeamNamesBySlotNumber.getValue(slot.slotNumber))
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

    private fun defaultTeamName(slotNumber: Int): String =
        "Team ${slotNumber.toString().padStart(2, '0')}"
}
