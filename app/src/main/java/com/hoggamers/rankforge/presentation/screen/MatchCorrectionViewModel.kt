package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftInput
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftResult
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftUseCase
import com.hoggamers.rankforge.domain.tournament.MatchResultRowInput
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionGlobalError
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionInput
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionResult
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
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
class MatchCorrectionViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val validateMatchResult: ValidateMatchResultUseCase,
    private val submitCorrection: SubmitMatchCorrectionUseCase,
    private val saveDraftValue: SaveMatchDraftValueUseCase,
    private val clearCorrectionDraft: ClearMatchCorrectionDraftUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchCorrectionUiState())
    val uiState: StateFlow<MatchCorrectionUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedMatchKey: String? = null
    private val draftWriteMutex = Mutex()
    private var draftWriteJob: Job? = null
    private var draftWriteError: MatchCorrectionGlobalError? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        _uiState.update {
            MatchCorrectionUiState(
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
                if (match == null || match.status != MatchStatus.FINALIZED) {
                    MatchCorrectionUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                } else {
                    val fallbackSlots = TeamSlot.fixedSlotsForTournament(tournamentId)
                        .associateBy { it.slotNumber }
                    val slotsByNumber = slots.associateBy { it.slotNumber }
                    val placementsBySlot = match.placements.associateBy { it.teamSlotNumber }
                    val killsBySlot = match.kills.associateBy { it.teamSlotNumber }
                    val rows = TeamSlot.SLOT_NUMBERS.map { teamSlotNumber ->
                        val slot = slotsByNumber[teamSlotNumber] ?: fallbackSlots.getValue(teamSlotNumber)
                        val draft = draftValues[teamSlotNumber]
                        val previousPlacement = placementsBySlot[teamSlotNumber]?.position?.toString().orEmpty()
                        val previousKills = killsBySlot[teamSlotNumber]?.kills?.toString().orEmpty()
                        MatchCorrectionRowUiState(
                            teamSlotNumber = teamSlotNumber,
                            teamName = slot.teamName,
                            playerNames = rosters[teamSlotNumber].orEmpty().map { it.displayName },
                            previousPlacement = previousPlacement,
                            previousKills = previousKills,
                            placementInput = draft?.placementInput ?: previousPlacement,
                            killsInput = draft?.killsInput ?: previousKills,
                        )
                    }
                    val validation = validateMatchResult(
                        rows.map { row ->
                            MatchResultRowInput(row.teamSlotNumber, row.placementInput, row.killsInput)
                        },
                    )
                    MatchCorrectionUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        rows = rows.map { row ->
                            row.copy(validationErrors = validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty())
                        },
                        validationErrors = validation.errorsByTeamSlot,
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        rows = state.rows.map { row ->
                            row.copy(
                                placementInput = current.rows.firstOrNull { it.teamSlotNumber == row.teamSlotNumber }
                                    ?.placementInput ?: row.placementInput,
                                killsInput = current.rows.firstOrNull { it.teamSlotNumber == row.teamSlotNumber }
                                    ?.killsInput ?: row.killsInput,
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
        updateRow(teamSlotNumber) { it.copy(placementInput = value) }
        enqueueDraftWrite(teamSlotNumber)
    }

    fun onKillsChanged(teamSlotNumber: Int, value: String) {
        updateRow(teamSlotNumber) { it.copy(killsInput = value) }
        enqueueDraftWrite(teamSlotNumber)
    }

    fun submit() {
        val current = _uiState.value
        val matchId = current.matchId ?: return
        val tournamentId = current.tournamentId ?: return
        if (!current.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, globalError = null) }
        viewModelScope.launch {
            draftWriteError = null
            current.rows.forEach { row -> enqueueDraftWrite(row.teamSlotNumber) }
            draftWriteJob?.join()
            draftWriteError?.let { error ->
                _uiState.update { it.copy(isSubmitting = false, globalError = error) }
                return@launch
            }
            when (
                val result = submitCorrection(
                    SubmitMatchCorrectionInput(
                        matchId = matchId,
                        rows = current.rows.map { row ->
                            MatchResultRowInput(row.teamSlotNumber, row.placementInput, row.killsInput)
                        },
                    ),
                )
            ) {
                is SubmitMatchCorrectionResult.Submitted -> _uiState.update {
                    it.copy(isSubmitting = false, navigation = MatchCorrectionNavigation.REVIEW)
                }
                is SubmitMatchCorrectionResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        validationErrors = result.validation.errorsByTeamSlot,
                        rows = state.rows.map { row ->
                            row.copy(validationErrors = result.validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty())
                        },
                        globalError = result.globalError,
                    )
                }
            }
        }
    }

    fun discard() {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        viewModelScope.launch {
            draftWriteJob?.join()
            when (clearCorrectionDraft(ClearMatchCorrectionDraftInput(tournamentId, matchId))) {
                ClearMatchCorrectionDraftResult.Cleared -> _uiState.update {
                    it.copy(navigation = MatchCorrectionNavigation.REVIEW)
                }
                ClearMatchCorrectionDraftResult.AuthenticationRequired -> _uiState.update {
                    it.copy(globalError = MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED)
                }
                ClearMatchCorrectionDraftResult.MatchNotFound -> _uiState.update {
                    it.copy(globalError = MatchCorrectionGlobalError.MATCH_NOT_FOUND)
                }
            }
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            draftWriteJob?.join()
            if (!_uiState.value.isSubmitting) {
                _uiState.update { it.copy(navigation = MatchCorrectionNavigation.REVIEW) }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    private fun updateRow(
        teamSlotNumber: Int,
        transform: (MatchCorrectionRowUiState) -> MatchCorrectionRowUiState,
    ) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.teamSlotNumber == teamSlotNumber) transform(row) else row
                },
                validationErrors = emptyMap(),
                globalError = null,
            )
        }
    }

    private fun enqueueDraftWrite(teamSlotNumber: Int) {
        val state = _uiState.value
        val tournamentId = state.tournamentId ?: return
        val matchId = state.matchId ?: return
        val row = state.rows.firstOrNull { it.teamSlotNumber == teamSlotNumber } ?: return
        val input = SaveMatchDraftValueInput(
            tournamentId = tournamentId,
            matchId = matchId,
            teamSlotNumber = teamSlotNumber,
            placementInput = row.placementInput,
            killsInput = row.killsInput,
        )
        val previousWrite = draftWriteJob
        draftWriteJob = viewModelScope.launch {
            previousWrite?.join()
            draftWriteMutex.withLock {
                draftWriteError = when (saveDraftValue(input)) {
                    SaveMatchDraftValueResult.Saved -> draftWriteError
                    SaveMatchDraftValueResult.AuthenticationRequired ->
                        MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED
                    SaveMatchDraftValueResult.MatchNotFound -> MatchCorrectionGlobalError.MATCH_NOT_FOUND
                }
            }
        }
    }
}
