package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchInput
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class MatchPlacementViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val saveMatchPlacements: SaveMatchPlacementsUseCase,
    private val saveDraftValue: SaveMatchDraftValueUseCase,
    private val clearDraftMatch: ClearDraftMatchUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchPlacementUiState())
    val uiState: StateFlow<MatchPlacementUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedMatchKey: String? = null
    private val draftWriteMutex = Mutex()
    private var draftWriteJob: Job? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        _uiState.update {
            MatchPlacementUiState(
                isLoading = true,
                tournamentId = tournamentId,
                matchId = matchId,
            )
        }
        loadJob = viewModelScope.launch {
            combine(
                observeMatches(tournamentId),
                observeTournamentSlots(tournamentId),
                observeRoster(tournamentId),
                observeDraftValues(tournamentId, matchId),
            ) { matches, slots, rosters, draftValues ->
                val match = matches.firstOrNull { it.id == matchId }
                if (match == null || match.status != MatchStatus.DRAFT) {
                    MatchPlacementUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                } else {
                    val savedPlacements = match.placements.associateBy { it.teamSlotNumber }
                    MatchPlacementUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        rows = slots.sortedBy { it.slotNumber }.map { slot ->
                            MatchPlacementRowUiState(
                                teamSlotNumber = slot.slotNumber,
                                teamName = slot.teamName,
                                placementInput = draftValues[slot.slotNumber]?.placementInput
                                    ?: savedPlacements[slot.slotNumber]?.position?.toString().orEmpty(),
                                playerNames = rosters[slot.slotNumber].orEmpty().map { it.displayName },
                            )
                        },
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        rows = state.rows.map { row ->
                            row.copy(
                                placementInput = current.rows
                                    .firstOrNull { it.teamSlotNumber == row.teamSlotNumber }
                                    ?.placementInput
                                    ?: row.placementInput,
                            )
                        },
                        validationErrors = current.validationErrors,
                        globalError = current.globalError,
                        isSubmitting = current.isSubmitting,
                        navigation = current.navigation,
                    )
                }
            }
        }
    }

    fun onPlacementChanged(teamSlotNumber: Int, value: String) {
        val currentState = _uiState.value
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.teamSlotNumber == teamSlotNumber) row.copy(placementInput = value) else row
                },
                validationErrors = state.validationErrors - teamSlotNumber,
                globalError = null,
            )
        }
        val tournamentId = currentState.tournamentId ?: return
        val matchId = currentState.matchId ?: return
        enqueueDraftWrite(
            SaveMatchDraftValueInput(
                tournamentId = tournamentId,
                matchId = matchId,
                teamSlotNumber = teamSlotNumber,
                placementInput = value,
            ),
        )
    }

    fun save() {
        val currentState = _uiState.value
        val matchId = currentState.matchId ?: return
        if (!currentState.canSave) return
        _uiState.update { it.copy(isSubmitting = true, globalError = null) }
        viewModelScope.launch {
            currentState.rows.forEach { row ->
                enqueueDraftWrite(
                    SaveMatchDraftValueInput(
                        tournamentId = currentState.tournamentId ?: return@forEach,
                        matchId = matchId,
                        teamSlotNumber = row.teamSlotNumber,
                        placementInput = row.placementInput,
                    ),
                )
            }
            draftWriteJob?.join()
            when (
                val result = saveMatchPlacements(
                    SaveMatchPlacementsInput(
                        matchId = matchId,
                        placementsByTeamSlot = currentState.rows.associate { row ->
                            row.teamSlotNumber to row.placementInput
                        },
                    ),
                )
            ) {
                is SaveMatchPlacementsResult.Saved -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        navigation = MatchPlacementNavigation.SAVED,
                    )
                }
                is SaveMatchPlacementsResult.Invalid -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        validationErrors = result.errorsByTeamSlot,
                        globalError = result.globalError,
                    )
                }
            }
        }
    }

    fun resetDraft() {
        val tournamentId = _uiState.value.tournamentId ?: return
        val matchId = _uiState.value.matchId ?: return
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { it.copy(placementInput = "") },
                validationErrors = emptyMap(),
                globalError = null,
            )
        }
        viewModelScope.launch {
            draftWriteJob?.join()
            clearDraftMatch(ClearDraftMatchInput(tournamentId, matchId))
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            draftWriteJob?.join()
            if (!_uiState.value.isSubmitting) {
                _uiState.update { it.copy(navigation = MatchPlacementNavigation.BACK) }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    private fun enqueueDraftWrite(input: SaveMatchDraftValueInput) {
        val previousWrite = draftWriteJob
        draftWriteJob = viewModelScope.launch {
            previousWrite?.join()
            draftWriteMutex.withLock {
                saveDraftValue(input)
            }
        }
    }
}
