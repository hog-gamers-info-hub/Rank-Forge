package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.export.AndroidExportBlockedReason
import com.hoggamers.rankforge.data.export.AndroidExportCoordinator
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.domain.export.TournamentCsvExportFailure
import com.hoggamers.rankforge.domain.export.TournamentCsvExportInput
import com.hoggamers.rankforge.domain.export.TournamentCsvExportResult
import com.hoggamers.rankforge.domain.export.TournamentCsvExporter
import com.hoggamers.rankforge.domain.export.TournamentStandingsExportRowsResult
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentDetailsViewModel @Inject constructor(
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeMatches: ObserveMatchesUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val googleSheetsStandingsExport: GoogleSheetsStandingsExportRemoteDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentDetailsUiState())
    val uiState: StateFlow<TournamentDetailsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        loadJob?.cancel()
        exportJob?.cancel()
        _uiState.update { TournamentDetailsUiState(isLoading = true) }
        loadJob = viewModelScope.launch {
            combine(
                getTournamentById(tournamentId),
                observeTournamentSlots(tournamentId),
                observeMatches(tournamentId),
            ) { tournament, slots, matches ->
                if (tournament == null) {
                    TournamentDetailsUiState(isLoading = false)
                } else {
                    TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournament.toDetailsItemUiState(slots, matches),
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        csvExportResult = current.csvExportResult,
                        googleSheetsExportResult = current.googleSheetsExportResult,
                    )
                }
            }
        }
    }

    fun prepareStandingsCsvExport() {
        val tournamentId = _uiState.value.tournament?.id ?: return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            val tournament = getTournamentById(tournamentId).first()
            val result = if (tournament == null) {
                AndroidExportCoordinator().blockStandingsCsv(
                    tournamentId = tournamentId,
                    reason = AndroidExportBlockedReason.MISSING_CONTEXT,
                )
            } else {
                val exportResult = TournamentCsvExporter().export(
                    TournamentCsvExportInput(
                        tournament = tournament,
                        matches = observeMatches(tournamentId).first(),
                        teamSlots = observeTournamentSlots(tournamentId).first(),
                        rosterPlayers = observeRoster(tournamentId).first().values.flatten(),
                    ),
                )
                when (exportResult) {
                    is TournamentCsvExportResult.Success -> AndroidExportCoordinator()
                        .prepareStandingsCsv(tournamentId, exportResult.csv)
                    is TournamentCsvExportResult.Failure -> AndroidExportCoordinator()
                        .blockStandingsCsv(
                            tournamentId = tournamentId,
                            reason = if (TournamentCsvExportFailure.NO_FINALIZED_MATCHES in exportResult.failures) {
                                AndroidExportBlockedReason.NO_FINALIZED_STANDINGS
                            } else {
                                AndroidExportBlockedReason.INVALID_FINALIZED_STANDINGS
                            },
                        )
                }
            }
            _uiState.update { state ->
                if (state.tournament?.id == tournamentId) {
                    state.copy(csvExportResult = result)
                } else {
                    state
                }
            }
        }
    }

    fun prepareGoogleSheetsStandingsExport() {
        val tournamentId = _uiState.value.tournament?.id ?: return
        if (exportJob?.isActive == true) return

        _uiState.update { state ->
            if (state.tournament?.id == tournamentId) {
                state.copy(
                    googleSheetsExportResult = AndroidExportCoordinator()
                        .googleSheetsStandingsExporting(tournamentId),
                )
            } else {
                state
            }
        }

        exportJob = viewModelScope.launch {
            val result = buildStandingsExportInput(tournamentId)?.let { input ->
                when (val rowsResult = TournamentCsvExporter().buildStandingsRows(input)) {
                    is TournamentStandingsExportRowsResult.Success -> {
                        when (val exportResult = googleSheetsStandingsExport.export(tournamentId, rowsResult.rows)) {
                            is GoogleSheetsStandingsExportExecutionResult.Success ->
                                AndroidExportCoordinator().googleSheetsStandingsSuccess(
                                    tournamentId = tournamentId,
                                    exportedMatchCount = exportResult.exportedMatchCount,
                                    rowsWritten = exportResult.rowsWritten,
                                )
                            is GoogleSheetsStandingsExportExecutionResult.Failure ->
                                AndroidExportCoordinator().googleSheetsStandingsFailure(
                                    tournamentId = tournamentId,
                                    reason = exportResult.reason,
                                )
                        }
                    }
                    is TournamentStandingsExportRowsResult.Failure ->
                        AndroidExportCoordinator().blockGoogleSheetsStandings(
                            tournamentId = tournamentId,
                            reason = if (TournamentCsvExportFailure.NO_FINALIZED_MATCHES in rowsResult.failures) {
                                AndroidExportBlockedReason.NO_FINALIZED_STANDINGS
                            } else {
                                AndroidExportBlockedReason.INVALID_FINALIZED_STANDINGS
                            },
                        )
                }
            } ?: AndroidExportCoordinator().blockGoogleSheetsStandings(
                tournamentId = tournamentId,
                reason = AndroidExportBlockedReason.MISSING_CONTEXT,
            )

            _uiState.update { state ->
                if (state.tournament?.id == tournamentId) {
                    state.copy(googleSheetsExportResult = result)
                } else {
                    state
                }
            }
        }
    }

    private suspend fun buildStandingsExportInput(
        tournamentId: String,
    ): TournamentCsvExportInput? {
        val tournament = getTournamentById(tournamentId).first() ?: return null
        return TournamentCsvExportInput(
            tournament = tournament,
            matches = observeMatches(tournamentId).first(),
            teamSlots = observeTournamentSlots(tournamentId).first(),
            rosterPlayers = observeRoster(tournamentId).first().values.flatten(),
        )
    }
}
