package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterResult
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
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
class RosterReviewViewModel @Inject constructor(
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRosterPlayers: ObserveRosterPlayersUseCase,
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
    private val confirmTournamentRoster: ConfirmTournamentRosterUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RosterReviewUiState())
    val uiState: StateFlow<RosterReviewUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        loadJob?.cancel()
        _uiState.update {
            RosterReviewUiState(
                isLoading = true,
                tournamentId = tournamentId,
            )
        }
        val rosterFlows = RosterPlayerSlotNumbers.map { slotNumber ->
            observeRosterPlayers(tournamentId, slotNumber)
        }
        loadJob = viewModelScope.launch {
            combine(
                getTournamentById(tournamentId),
                observeTournamentSlots(tournamentId),
                combine(rosterFlows) { rosters -> rosters.toList() },
            ) { tournament, slots, rosters ->
                if (tournament == null || slots.isEmpty()) {
                    RosterReviewUiState(
                        isLoading = false,
                        tournamentId = tournamentId,
                    )
                } else {
                    val orderedSlots = slots.sortedBy { it.slotNumber }
                    val teams = orderedSlots.map { slot ->
                        val players = rosters
                            .getOrNull(slot.slotNumber - 1)
                            .orEmpty()
                        RosterReviewTeamUiState(
                            slotNumber = slot.slotNumber,
                            teamName = slot.teamName,
                            players = players.mapIndexed { index, player ->
                                RosterReviewPlayerUiState(
                                    playerIndex = index,
                                    displayName = player.displayName,
                                )
                            },
                        )
                    }
                    val validation = validateTournamentRoster(tournamentId)
                    RosterReviewUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        status = tournament.status,
                        teams = teams,
                        validationIssues = validation.toUiState(),
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        isConfirming = current.isConfirming,
                        hasConfirmError = current.hasConfirmError,
                    )
                }
            }
        }
    }

    fun confirmRoster() {
        val tournamentId = uiState.value.tournamentId ?: return
        if (!uiState.value.canConfirm) return
        _uiState.update { it.copy(isConfirming = true, hasConfirmError = false) }
        viewModelScope.launch {
            runCatching { confirmTournamentRoster(tournamentId) }
                .onSuccess { result ->
                    when (result) {
                        is ConfirmTournamentRosterResult.Confirmed,
                        is ConfirmTournamentRosterResult.AlreadyConfirmed -> {
                            _uiState.update {
                                it.copy(
                                    isConfirming = false,
                                    status = com.hoggamers.rankforge.domain.tournament.TournamentStatus.CONFIRMED,
                                )
                            }
                        }
                        is ConfirmTournamentRosterResult.Invalid -> {
                            _uiState.update {
                                it.copy(
                                    isConfirming = false,
                                    validationIssues = result.validation.toUiState(),
                                )
                            }
                        }
                        ConfirmTournamentRosterResult.NotFound -> {
                            _uiState.update {
                                it.copy(isConfirming = false, isAvailable = false)
                            }
                        }
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isConfirming = false, hasConfirmError = true)
                    }
                }
        }
    }

    companion object {
        private val RosterPlayerSlotNumbers = (1..12).toList()
    }
}
