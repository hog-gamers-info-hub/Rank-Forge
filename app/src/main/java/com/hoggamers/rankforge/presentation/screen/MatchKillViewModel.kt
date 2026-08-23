package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.KillGlobalError
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchInput
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchResult
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
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
class MatchKillViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val saveMatchKills: SaveMatchKillsUseCase,
    private val saveDraftValue: SaveMatchDraftValueUseCase,
    private val clearDraftMatch: ClearDraftMatchUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchKillUiState())
    val uiState: StateFlow<MatchKillUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedMatchKey: String? = null
    private val draftWriteMutex = Mutex()
    private var draftWriteJob: Job? = null
    private var draftWriteError: KillGlobalError? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        _uiState.update {
            MatchKillUiState(
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
                    MatchKillUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                } else {
                    val savedKills = match.kills.associateBy { it.teamSlotNumber }
                    MatchKillUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        rows = slots.sortedBy { it.slotNumber }.map { slot ->
                            MatchKillRowUiState(
                                teamSlotNumber = slot.slotNumber,
                                teamName = slot.teamName,
                                killsInput = draftValues[slot.slotNumber]?.killsInput
                                    ?: savedKills[slot.slotNumber]?.kills?.toString().orEmpty(),
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
                                killsInput = current.rows
                                    .firstOrNull { it.teamSlotNumber == row.teamSlotNumber }
                                    ?.killsInput
                                    ?: row.killsInput,
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

    fun onKillsChanged(teamSlotNumber: Int, value: String) {
        val currentState = _uiState.value
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.teamSlotNumber == teamSlotNumber) row.copy(killsInput = value) else row
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
                killsInput = value,
            ),
        )
    }

    fun save() {
        val currentState = _uiState.value
        val tournamentId = currentState.tournamentId ?: return
        val matchId = currentState.matchId ?: return
        if (!currentState.canSave) return
        _uiState.update { it.copy(isSubmitting = true, globalError = null) }
        viewModelScope.launch {
            draftWriteError = null
            currentState.rows.forEach { row ->
                enqueueDraftWrite(
                    SaveMatchDraftValueInput(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        teamSlotNumber = row.teamSlotNumber,
                        killsInput = row.killsInput,
                    ),
                )
            }
            draftWriteJob?.join()
            draftWriteError?.let { error ->
                _uiState.update { it.copy(isSubmitting = false, globalError = error) }
                return@launch
            }
            when (
                val result = saveMatchKills(
                    SaveMatchKillsInput(
                        matchId = matchId,
                        killsByTeamSlot = currentState.rows.associate { row ->
                            row.teamSlotNumber to row.killsInput
                        },
                    ),
                )
            ) {
                is SaveMatchKillsResult.Saved -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        navigation = MatchKillNavigation.Saved(tournamentId, matchId),
                    )
                }
                is SaveMatchKillsResult.Invalid -> _uiState.update {
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
                rows = state.rows.map { it.copy(killsInput = "") },
                validationErrors = emptyMap(),
                globalError = null,
            )
        }
        viewModelScope.launch {
            draftWriteJob?.join()
            when (clearDraftMatch(ClearDraftMatchInput(tournamentId, matchId))) {
                ClearDraftMatchResult.Cleared -> Unit
                ClearDraftMatchResult.AuthenticationRequired -> _uiState.update {
                    it.copy(globalError = KillGlobalError.AUTHENTICATION_REQUIRED)
                }
                ClearDraftMatchResult.MatchNotFound -> _uiState.update {
                    it.copy(globalError = KillGlobalError.MATCH_NOT_FOUND)
                }
            }
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            draftWriteJob?.join()
            if (!_uiState.value.isSubmitting) {
                _uiState.update { it.copy(navigation = MatchKillNavigation.Back) }
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
                draftWriteError = when (saveDraftValue(input)) {
                    SaveMatchDraftValueResult.Saved -> draftWriteError
                    SaveMatchDraftValueResult.AuthenticationRequired -> KillGlobalError.AUTHENTICATION_REQUIRED
                    SaveMatchDraftValueResult.MatchNotFound -> KillGlobalError.MATCH_NOT_FOUND
                }
            }
        }
    }
}
