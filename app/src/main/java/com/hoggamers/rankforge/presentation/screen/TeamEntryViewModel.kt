package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

sealed interface TeamEntryNavigationEvent {
    data object BackToTournamentDetails : TeamEntryNavigationEvent
}

@HiltViewModel
class TeamEntryViewModel @Inject constructor(
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val saveTeamSlotNames: SaveTeamSlotNamesUseCase,
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
    private val uploadTournament: TournamentCloudUploadAction,
    private val tournamentRepository: TournamentRepository,
) : ViewModel() {
    private sealed interface DraftWriteCommand {
        data class Save(
            val tournamentId: String,
            val namesBySlotNumber: Map<Int, String>,
        ) : DraftWriteCommand

        data class ClearIfUnchanged(
            val tournamentId: String,
            val expectedEditGeneration: Long,
            val completion: CompletableDeferred<Unit>,
        ) : DraftWriteCommand

        data class Barrier(val completion: CompletableDeferred<Unit>) : DraftWriteCommand
    }

    private val _uiState = MutableStateFlow(TeamEntryUiState())
    val uiState: StateFlow<TeamEntryUiState> = _uiState.asStateFlow()
    private val navigationEventsChannel = Channel<TeamEntryNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<TeamEntryNavigationEvent> = navigationEventsChannel.receiveAsFlow()
    private val draftWriteChannel = Channel<DraftWriteCommand>(Channel.UNLIMITED)
    private var loadJob: Job? = null
    private var loadedTournamentId: String? = null
    private var initializedDraftValues = false
    private var editGeneration = 0L

    init {
        viewModelScope.launch {
            for (command in draftWriteChannel) {
                when (command) {
                    is DraftWriteCommand.Save -> {
                        try {
                            tournamentRepository.saveTeamEntryDraft(
                                tournamentId = command.tournamentId,
                                namesBySlotNumber = command.namesBySlotNumber,
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            // A draft write must not stop later edits or explicit Save.
                        }
                    }

                    is DraftWriteCommand.ClearIfUnchanged -> {
                        try {
                            if (
                                loadedTournamentId == command.tournamentId &&
                                editGeneration == command.expectedEditGeneration
                            ) {
                                tournamentRepository.clearTeamEntryDraft(command.tournamentId)
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            // Explicit Save retains its existing error behavior.
                        } finally {
                            command.completion.complete(Unit)
                        }
                    }

                    is DraftWriteCommand.Barrier -> command.completion.complete(Unit)
                }
            }
        }
    }

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
                    val draft = tournamentRepository.readTeamEntryDraft(tournamentId)
                    initializedDraftValues = true
                    _uiState.update {
                        TeamEntryUiState(
                            isLoading = false,
                            slots = slots.toTeamEntrySlotUiState().map { slot ->
                                slot.copy(teamName = draft?.get(slot.slotNumber) ?: slot.teamName)
                            },
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
        editGeneration += 1
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
        val tournamentId = loadedTournamentId ?: return
        val snapshot = _uiState.value.slots.associate { slot ->
            slot.slotNumber to slot.teamName
        }
        draftWriteChannel.trySend(
            DraftWriteCommand.Save(
                tournamentId = tournamentId,
                namesBySlotNumber = snapshot,
            ),
        )
    }

    fun saveTeamNames() {
        if (_uiState.value.isSaving) return
        val tournamentId = loadedTournamentId ?: return
        val slotsToSave = uiState.value.slots
        val teamNamesBySlotNumber = slotsToSave.associate { slot ->
            slot.slotNumber to slot.teamName.trim()
        }
        val participation = teamNamesBySlotNumber.analyzeTeamSlotParticipation()
        val saveEditGeneration = editGeneration
        persistTeamNames(
            tournamentId = tournamentId,
            teamNamesBySlotNumber = teamNamesBySlotNumber,
            activeSlotNumbers = participation.activeSlotNumbers.toSet(),
            saveEditGeneration = saveEditGeneration,
        )
    }

    private fun persistTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
        activeSlotNumbers: Set<Int>,
        saveEditGeneration: Long,
    ) {
        _uiState.update {
            it.copy(
                isSaving = true,
                hasSaveError = false,
                hasTeamNameGap = false,
            )
        }
        viewModelScope.launch {
            try {
                awaitDraftWrites()
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
                    return@launch
                }

                when (saveTeamSlotNames(
                    tournamentId = tournamentId,
                    teamNamesBySlotNumber = teamNamesBySlotNumber,
                )) {
                    SaveTeamSlotNamesResult.AuthenticationRequired,
                    SaveTeamSlotNamesResult.TournamentNotFound -> {
                        _uiState.update { it.copy(isSaving = false, hasSaveError = true) }
                        return@launch
                    }
                    SaveTeamSlotNamesResult.Saved -> Unit
                }
                clearDraftIfUnchanged(tournamentId, saveEditGeneration)
                _uiState.update { current ->
                    current.copy(
                        validationIssues = validation.toUiState(),
                        slots = if (editGeneration == saveEditGeneration) {
                            current.slots.map { slot ->
                                slot.copy(teamName = teamNamesBySlotNumber.getValue(slot.slotNumber))
                            }
                        } else {
                            current.slots
                        },
                    )
                }
                try {
                    uploadTournament(tournamentId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Local team names remain saved when the immediate cloud attempt throws.
                }
                _uiState.update { it.copy(isSaving = false) }
                navigationEventsChannel.trySend(TeamEntryNavigationEvent.BackToTournamentDetails)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(isSaving = false, hasSaveError = true)
                }
            }
        }
    }

    private suspend fun awaitDraftWrites() {
        val completion = CompletableDeferred<Unit>()
        draftWriteChannel.send(DraftWriteCommand.Barrier(completion))
        completion.await()
    }

    private suspend fun clearDraftIfUnchanged(
        tournamentId: String,
        expectedEditGeneration: Long,
    ) {
        val completion = CompletableDeferred<Unit>()
        draftWriteChannel.send(
            DraftWriteCommand.ClearIfUnchanged(
                tournamentId = tournamentId,
                expectedEditGeneration = expectedEditGeneration,
                completion = completion,
            ),
        )
        completion.await()
    }

}
