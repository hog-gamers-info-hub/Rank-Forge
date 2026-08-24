package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidationPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidationTeam
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterResult
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
class RosterEntryViewModel @Inject constructor(
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRosterPlayers: ObserveRosterPlayersUseCase,
    private val saveRoster: SaveRosterUseCase,
    private val rosterValidator: RosterValidator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RosterEntryUiState())
    val uiState: StateFlow<RosterEntryUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedKey: RosterKey? = null
    private var initializedDraftValues = false

    fun load(
        tournamentId: String,
        slotNumber: Int,
    ) {
        require(slotNumber in com.hoggamers.rankforge.domain.tournament.TeamSlot.SLOT_NUMBERS) {
            "Team slot number must be between 1 and 12."
        }
        val key = RosterKey(tournamentId, slotNumber)
        if (loadedKey == key) return

        loadedKey = key
        initializedDraftValues = false
        loadJob?.cancel()
        _uiState.update {
            RosterEntryUiState(
                isLoading = true,
                tournamentId = tournamentId,
                slotNumber = slotNumber,
            )
        }
        loadJob = viewModelScope.launch {
            combine(
                observeTournamentSlots(tournamentId),
                observeRosterPlayers(tournamentId, slotNumber),
            ) { slots, players ->
                val slot = slots.firstOrNull { it.slotNumber == slotNumber }
                slot to players
            }.collect { (slot, players) ->
                if (slot == null) {
                    _uiState.update {
                        RosterEntryUiState(
                            isLoading = false,
                            tournamentId = tournamentId,
                            slotNumber = slotNumber,
                        )
                    }
                } else if (!initializedDraftValues) {
                    initializedDraftValues = true
                    _uiState.update {
                        RosterEntryUiState(
                            isLoading = false,
                            isAvailable = true,
                            tournamentId = tournamentId,
                            slotNumber = slotNumber,
                            teamName = slot.teamName,
                            players = players.toRosterPlayerUiState(),
                        )
                    }
                }
            }
        }
    }

    fun onPlayerNameChanged(
        playerIndex: Int,
        displayName: String,
    ) {
        _uiState.update { current ->
            current.copy(
                players = current.players.mapIndexed { index, player ->
                    if (index == playerIndex) {
                        player.copy(displayName = displayName)
                    } else {
                        player
                    }
                },
                validationIssues = emptyList(),
            )
        }
    }

    fun addPlayer() {
        _uiState.update { current ->
            if (!current.canAddPlayer) {
                current
            } else {
                current.copy(
                    players = current.players + RosterPlayerUiState(displayName = ""),
                )
            }
        }
    }

    fun removePlayer(playerIndex: Int) {
        _uiState.update { current ->
            if (playerIndex !in current.players.indices) {
                current
            } else {
                current.copy(players = current.players.filterIndexed { index, _ -> index != playerIndex })
            }
        }
    }

    fun saveRoster() {
        val tournamentId = uiState.value.tournamentId ?: return
        val slotNumber = uiState.value.slotNumber ?: return
        val players = uiState.value.players.map { player ->
            RosterPlayer.create(
                tournamentId = tournamentId,
                slotNumber = slotNumber,
                displayName = player.displayName,
            )
        }
        if (players.size > RosterPlayer.MAX_PLAYERS) return

        _uiState.update { it.copy(isSaving = true, hasSaveError = false) }
        viewModelScope.launch {
            runCatching {
                val validation = rosterValidator.validate(
                    listOf(
                        RosterValidationTeam(
                            slotNumber = slotNumber,
                            teamName = uiState.value.teamName,
                            players = uiState.value.players.mapIndexed { index, player ->
                                RosterValidationPlayer(
                                    playerIndex = index,
                                    displayName = player.displayName,
                                )
                            },
                        ),
                    ),
                )
                _uiState.update { it.copy(validationIssues = validation.toUiState()) }
                if (!validation.hasBlockingIssues) {
                    saveRoster(
                        tournamentId = tournamentId,
                        slotNumber = slotNumber,
                        players = players,
                    )
                } else {
                    null
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasSaveError = result == SaveRosterResult.AuthenticationRequired ||
                            result == SaveRosterResult.TournamentNotFound,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, hasSaveError = true) }
            }
        }
    }

    private data class RosterKey(
        val tournamentId: String,
        val slotNumber: Int,
    )
}
