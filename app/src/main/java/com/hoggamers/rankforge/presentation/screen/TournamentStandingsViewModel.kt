package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.cloud.TournamentStandingsSharePublicationResult
import com.hoggamers.rankforge.data.cloud.TournamentStandingsShareRemoteDataSource
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentStandingsViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val cumulativeStandings: CumulativeTournamentStandingsEngine,
    private val tieBreakRules: TieBreakRules,
    private val shareRemoteDataSource: TournamentStandingsShareRemoteDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentStandingsUiState())
    val uiState: StateFlow<TournamentStandingsUiState> = _uiState.asStateFlow()
    private val shareEventsChannel = Channel<TournamentStandingsShareEvent>(Channel.BUFFERED)
    val shareEvents: Flow<TournamentStandingsShareEvent> = shareEventsChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, rows = emptyList()) }
        loadJob = viewModelScope.launch {
            combine(
                observeMatches(tournamentId),
                observeTournamentSlots(tournamentId),
            ) { matches, slots ->
                val teamNamesBySlotNumber = slots
                    .mapNotNull { slot ->
                        slot.teamName
                            .trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { slot.slotNumber to it }
                    }
                    .toMap()
                tieBreakRules(cumulativeStandings(matches))
                    .toTournamentStandingsUiState(teamNamesBySlotNumber)
            }.collect { rows ->
                _uiState.update { it.copy(isLoading = false, rows = rows) }
            }
        }
    }

    fun shareStandings() {
        val currentState = _uiState.value
        val tournamentId = loadedTournamentId ?: return
        if (currentState.isLoading || currentState.rows.isEmpty() || currentState.isPublishing) return

        val rows = currentState.rows
        _uiState.update { it.copy(isPublishing = true) }
        viewModelScope.launch {
            try {
                when (val result = shareRemoteDataSource.publish(tournamentId, rows)) {
                    is TournamentStandingsSharePublicationResult.Success ->
                        shareEventsChannel.send(
                            TournamentStandingsShareEvent.ShareUrl(result.publicUrl),
                        )

                    is TournamentStandingsSharePublicationResult.Failure ->
                        shareEventsChannel.send(TournamentStandingsShareEvent.ShareFailed)
                }
            } finally {
                _uiState.update { it.copy(isPublishing = false) }
            }
        }
    }
}
