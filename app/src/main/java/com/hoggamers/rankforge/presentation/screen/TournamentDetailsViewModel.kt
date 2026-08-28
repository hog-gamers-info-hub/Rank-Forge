package com.hoggamers.rankforge.presentation.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.BuildConfig
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
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesResult
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.domain.tournament.defaultTeamNameForSlot
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchFailure
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import com.hoggamers.rankforge.domain.tournament.DeleteTournamentResult
import com.hoggamers.rankforge.domain.tournament.DeleteTournamentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CALCULATE_POINTS_TIMING_TAG = "PointIQCalcTiming"

private data class CalculatePointsTimingTrace(
    val id: String,
    val startedAtNanos: Long,
)

private fun newCalculatePointsTimingTrace(): CalculatePointsTimingTrace = CalculatePointsTimingTrace(
    id = System.nanoTime().toString(36),
    startedAtNanos = System.nanoTime(),
)

private fun logCalculatePointsStage(
    trace: CalculatePointsTimingTrace,
    stage: String,
    stageStartedAtNanos: Long,
    tournamentId: String,
    matchId: String? = null,
    outcome: String? = null,
) {
    if (!BuildConfig.DEBUG) return
    val now = System.nanoTime()
    val durationMs = (now - stageStartedAtNanos) / 1_000_000L
    val sinceStartMs = (now - trace.startedAtNanos) / 1_000_000L
    val message = buildString {
        append("trace=")
        append(trace.id)
        append(" stage=")
        append(stage)
        append(" duration_ms=")
        append(durationMs)
        append(" since_start_ms=")
        append(sinceStartMs)
        append(" tournament_id=")
        append(tournamentId)
        matchId?.let {
            append(" match_id=")
            append(it)
        }
        outcome?.let {
            append(" outcome=")
            append(it)
        }
    }
    runCatching { Log.d(CALCULATE_POINTS_TIMING_TAG, message) }
        .onFailure { println("$CALCULATE_POINTS_TIMING_TAG $message") }
}

private fun logCalculatePointsMark(
    trace: CalculatePointsTimingTrace,
    stage: String,
    tournamentId: String,
    matchId: String? = null,
    outcome: String? = null,
) {
    logCalculatePointsStage(
        trace = trace,
        stage = stage,
        stageStartedAtNanos = System.nanoTime(),
        tournamentId = tournamentId,
        matchId = matchId,
        outcome = outcome,
    )
}

private fun DeleteTournamentResult.toUiError(): TournamentDeletionUiError = when (this) {
    DeleteTournamentResult.TargetNotFound -> TournamentDeletionUiError.TARGET_NOT_FOUND
    DeleteTournamentResult.AuthenticationRequired -> TournamentDeletionUiError.AUTHENTICATION_REQUIRED
    DeleteTournamentResult.PendingSyncPreparationFailed -> TournamentDeletionUiError.PREPARATION_FAILURE
    DeleteTournamentResult.RemoteDeletedLocalCleanupFailed -> TournamentDeletionUiError.LOCAL_CLEANUP_FAILURE
    is DeleteTournamentResult.StorageDeletionFailed -> category.toUiError(TournamentDeletionUiError.STORAGE_FAILURE)
    is DeleteTournamentResult.RemoteDeletionFailed -> category.toUiError(TournamentDeletionUiError.REMOTE_FAILURE)
    DeleteTournamentResult.Success -> error("Successful deletion has no UI error")
}

private fun CloudDeletionFailureCategory.toUiError(default: TournamentDeletionUiError): TournamentDeletionUiError = when (this) {
    CloudDeletionFailureCategory.AUTHENTICATION -> TournamentDeletionUiError.AUTHENTICATION_REQUIRED
    CloudDeletionFailureCategory.AUTHORIZATION -> TournamentDeletionUiError.AUTHORIZATION_FAILURE
    CloudDeletionFailureCategory.VALIDATION -> TournamentDeletionUiError.VALIDATION_FAILURE
    else -> default
}

@HiltViewModel
class TournamentDetailsViewModel @Inject constructor(
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeMatches: ObserveMatchesUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val googleSheetsStandingsExport: GoogleSheetsStandingsExportRemoteDataSource,
    private val saveTeamSlotNames: SaveTeamSlotNamesUseCase,
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
    private val createNextMatch: CreateNextMatchUseCase,
    private val syncDraftMatches: DraftMatchCloudSyncAction,
    private val applyLobbyTemplate: ApplyLobbyTemplateAction = ApplyLobbyTemplateAction { _, _ -> ApplyLobbyTemplateResult.Unavailable },
    private val lobbyUploadCheckpoint: MatchLobbyScreenshotUploadCheckpointAction = MatchLobbyScreenshotUploadCheckpointAction { MatchLobbyScreenshotUploadCheckpointResult.Skipped },
    private val deleteTournamentUseCase: DeleteTournamentUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentDetailsUiState())
    val uiState: StateFlow<TournamentDetailsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var deletionJob: Job? = null
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        loadJob?.cancel()
        exportJob?.cancel()
        deletionJob?.cancel()
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
                        pendingTeamCountConfirmation = current.pendingTeamCountConfirmation,
                        calculatePointsMessage = current.calculatePointsMessage,
                        matchReviewRequest = current.matchReviewRequest,
                        isCreatingMatch = current.isCreatingMatch,
                        navigation = current.navigation,
                        isDeleting = current.isDeleting,
                        deletionError = current.deletionError,
                    )
                }
            }
        }
    }

    fun onCalculatePointsRequested() {
        val tournament = _uiState.value.tournament ?: return
        if (
            tournament.matches.size >= MAX_MATCHES_PER_TOURNAMENT ||
            _uiState.value.isCreatingMatch ||
            _uiState.value.matchReviewRequest != null
        ) return
        val trace = newCalculatePointsTimingTrace()
        logCalculatePointsMark(
            trace = trace,
            stage = "00_REQUESTED",
            tournamentId = tournament.id,
        )
        viewModelScope.launch {
            val preflightStartedAtNanos = System.nanoTime()
            var preflightOutcome = "THREW"
            val participation = try {
                val slots = observeTournamentSlots(tournament.id).first()
                slots.analyzeTeamSlotParticipation().also {
                    preflightOutcome = "ACTIVE_${it.activeCount}"
                }
            } finally {
                logCalculatePointsStage(
                    trace = trace,
                    stage = "01_SLOT_PREFLIGHT",
                    stageStartedAtNanos = preflightStartedAtNanos,
                    tournamentId = tournament.id,
                    outcome = preflightOutcome,
                )
            }
            when {
                participation.activeCount == 0 -> {
                    _uiState.update {
                        it.copy(
                            pendingTeamCountConfirmation = TeamCountConfirmationUiState(0, TeamSlot.MAX_SLOT_NUMBER),
                            calculatePointsMessage = null,
                        )
                    }
                    logCalculatePointsMark(
                        trace = trace,
                        stage = "PAUSED_FOR_TEAM_CONFIRMATION",
                        tournamentId = tournament.id,
                        outcome = "NO_ACTIVE_TEAMS",
                    )
                }
                participation.activeCount < TeamSlot.MAX_SLOT_NUMBER -> {
                    _uiState.update {
                        it.copy(
                            pendingTeamCountConfirmation = TeamCountConfirmationUiState(
                                enteredCount = participation.activeCount,
                                emptyCount = TeamSlot.MAX_SLOT_NUMBER - participation.activeCount,
                            ),
                            calculatePointsMessage = null,
                        )
                    }
                    logCalculatePointsMark(
                        trace = trace,
                        stage = "PAUSED_FOR_TEAM_CONFIRMATION",
                        tournamentId = tournament.id,
                        outcome = "ACTIVE_${participation.activeCount}",
                    )
                }
                else -> requestMatchCreation(tournament.id, trace)
            }
        }
    }

    fun cancelTeamCountConfirmation() {
        _uiState.update { it.copy(pendingTeamCountConfirmation = null) }
    }

    fun useEnteredTeams() {
        val tournamentId = _uiState.value.tournament?.id ?: return
        if (_uiState.value.pendingTeamCountConfirmation == null) return
        _uiState.update { it.copy(pendingTeamCountConfirmation = null) }
        requestMatchCreation(tournamentId)
    }

    fun useDefaults() {
        val tournamentId = _uiState.value.tournament?.id ?: return
        if (_uiState.value.pendingTeamCountConfirmation == null) return
        _uiState.update { it.copy(pendingTeamCountConfirmation = null) }
        viewModelScope.launch {
            val slots = observeTournamentSlots(tournamentId).first()
            val names = slots.associate { slot ->
                val trimmedName = slot.teamName.trim()
                slot.slotNumber to if (trimmedName.isBlank()) {
                    defaultTeamNameForSlot(slot.slotNumber)
                } else {
                    trimmedName
                }
            }
            val validation = validateTournamentRoster(
                tournamentId = tournamentId,
                teamNamesBySlotNumber = names,
                activeTeamSlotNumbers = TeamSlot.SLOT_NUMBERS.toSet(),
            )
            if (validation.hasBlockingIssues) {
                _uiState.update {
                    it.copy(
                        calculatePointsMessage = CalculatePointsMessage.VALIDATION_FAILED,
                    )
                }
            } else {
                runCatching {
                    saveTeamSlotNames(tournamentId, names)
                }.onSuccess { result ->
                    if (result != SaveTeamSlotNamesResult.Saved) {
                        _uiState.update {
                            it.copy(
                                calculatePointsMessage = CalculatePointsMessage.VALIDATION_FAILED,
                            )
                        }
                        return@onSuccess
                    }
                    _uiState.update {
                        it.copy(
                            calculatePointsMessage = null,
                        )
                    }
                    requestMatchCreation(tournamentId)
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            calculatePointsMessage = CalculatePointsMessage.VALIDATION_FAILED,
                        )
                    }
                }
            }
        }
    }

    fun onMatchReviewRequestHandled() {
        _uiState.update { it.copy(matchReviewRequest = null) }
    }

    fun deleteTournament() {
        val current = _uiState.value
        val tournamentId = current.tournament?.id
        if (current.isDeleting || tournamentId.isNullOrBlank()) return
        _uiState.update {
            it.copy(
                isDeleting = true,
                deletionError = null,
            )
        }
        deletionJob = viewModelScope.launch {
            val useCase = deleteTournamentUseCase ?: run {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = TournamentDeletionUiError.UNKNOWN,
                    )
                }
                return@launch
            }
            val result = try {
                useCase(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = TournamentDeletionUiError.UNKNOWN,
                    )
                }
                return@launch
            }
            when (result) {
                DeleteTournamentResult.Success -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = null,
                        navigation = TournamentDetailsNavigation.TOURNAMENT_LIST,
                    )
                }
                else -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = result.toUiError(),
                    )
                }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    private fun requestMatchCreation(
        tournamentId: String,
        existingTrace: CalculatePointsTimingTrace? = null,
    ) {
        if (_uiState.value.isCreatingMatch || _uiState.value.matchReviewRequest != null) return
        val trace = existingTrace ?: newCalculatePointsTimingTrace().also {
            logCalculatePointsMark(
                trace = it,
                stage = "00_CREATION_REQUESTED",
                tournamentId = tournamentId,
            )
        }
        _uiState.update {
            it.copy(
                calculatePointsMessage = null,
                isCreatingMatch = true,
            )
        }
        viewModelScope.launch {
            val creationStartedAtNanos = System.nanoTime()
            var creationOutcome = "THREW"
            val result = try {
                createNextMatch(tournamentId).also {
                    creationOutcome = when (it) {
                        is CreateNextMatchResult.Created -> "CREATED"
                        is CreateNextMatchResult.Rejected -> "REJECTED_${it.failure}"
                    }
                }
            } finally {
                logCalculatePointsStage(
                    trace = trace,
                    stage = "02_CREATE_NEXT_MATCH_TOTAL",
                    stageStartedAtNanos = creationStartedAtNanos,
                    tournamentId = tournamentId,
                    outcome = creationOutcome,
                )
            }
            when (result) {
                is CreateNextMatchResult.Created -> {
                    val matchId = result.match.id
                    val lobbyStartedAtNanos = System.nanoTime()
                    var lobbyOutcome = "THREW"
                    val inheritedLobby = try {
                        when (val applyResult = applyLobbyTemplate(result.match.tournamentId, matchId)) {
                            ApplyLobbyTemplateResult.Applied -> {
                                lobbyOutcome = "APPLIED"
                                true
                            }
                            ApplyLobbyTemplateResult.Unavailable -> {
                                lobbyOutcome = "UNAVAILABLE"
                                false
                            }
                            ApplyLobbyTemplateResult.AuthenticationRequired -> {
                                lobbyOutcome = "AUTHENTICATION_REQUIRED"
                                false
                            }
                            ApplyLobbyTemplateResult.Failed -> {
                                lobbyOutcome = "FAILED"
                                false
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        lobbyOutcome = "CANCELLED"
                        throw cancellation
                    } catch (throwable: Throwable) {
                        lobbyOutcome = "THREW_${throwable.javaClass.simpleName}"
                        false
                    } finally {
                        logCalculatePointsStage(
                            trace = trace,
                            stage = "03_APPLY_LOBBY_TOTAL",
                            stageStartedAtNanos = lobbyStartedAtNanos,
                            tournamentId = result.match.tournamentId,
                            matchId = matchId,
                            outcome = lobbyOutcome,
                        )
                    }

                    val syncStartedAtNanos = System.nanoTime()
                    var syncOutcome = "THREW"
                    try {
                        val syncResult = syncDraftMatches(result.match.tournamentId)
                        syncOutcome = "${syncResult.primaryResult.javaClass.simpleName}_${syncResult.queueRecordingResult}"
                    } catch (cancellation: CancellationException) {
                        syncOutcome = "CANCELLED"
                        throw cancellation
                    } catch (throwable: Throwable) {
                        syncOutcome = "THREW_${throwable.javaClass.simpleName}"
                        // Local match creation remains authoritative for navigation.
                    } finally {
                        logCalculatePointsStage(
                            trace = trace,
                            stage = "04_SYNC_DRAFT_TOTAL",
                            stageStartedAtNanos = syncStartedAtNanos,
                            tournamentId = result.match.tournamentId,
                            matchId = matchId,
                            outcome = syncOutcome,
                        )
                    }

                    if (inheritedLobby) {
                        (1..3).forEach { index ->
                            val checkpointStartedAtNanos = System.nanoTime()
                            var checkpointOutcome = "THREW"
                            try {
                                val checkpointResult = lobbyUploadCheckpoint.run(
                                    com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity(
                                        tournamentId = result.match.tournamentId,
                                        matchId = matchId,
                                        lobbyScreenshotIndex = index,
                                    ),
                                )
                                checkpointOutcome = checkpointResult.javaClass.simpleName
                            } catch (cancellation: CancellationException) {
                                checkpointOutcome = "CANCELLED"
                                throw cancellation
                            } catch (throwable: Throwable) {
                                checkpointOutcome = "THREW_${throwable.javaClass.simpleName}"
                                // Local inheritance and navigation remain authoritative.
                            } finally {
                                logCalculatePointsStage(
                                    trace = trace,
                                    stage = when (index) {
                                        1 -> "05_LOBBY_UPLOAD_1"
                                        2 -> "06_LOBBY_UPLOAD_2"
                                        else -> "07_LOBBY_UPLOAD_3"
                                    },
                                    stageStartedAtNanos = checkpointStartedAtNanos,
                                    tournamentId = result.match.tournamentId,
                                    matchId = matchId,
                                    outcome = checkpointOutcome,
                                )
                            }
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            matchReviewRequest = MatchReviewRequest(
                                tournamentId = result.match.tournamentId,
                                matchId = matchId,
                            ),
                        )
                    }
                    logCalculatePointsMark(
                        trace = trace,
                        stage = "08_NAVIGATION_REQUEST",
                        tournamentId = result.match.tournamentId,
                        matchId = matchId,
                        outcome = "READY",
                    )
                    logCalculatePointsStage(
                        trace = trace,
                        stage = "CALCULATE_TOTAL",
                        stageStartedAtNanos = trace.startedAtNanos,
                        tournamentId = result.match.tournamentId,
                        matchId = matchId,
                        outcome = "NAVIGATION_REQUESTED",
                    )
                }
                is CreateNextMatchResult.Rejected -> {
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            calculatePointsMessage = result.failure.toCalculatePointsMessage(),
                        )
                    }
                    logCalculatePointsStage(
                        trace = trace,
                        stage = "CALCULATE_TOTAL",
                        stageStartedAtNanos = trace.startedAtNanos,
                        tournamentId = tournamentId,
                        outcome = "REJECTED_${result.failure}",
                    )
                }
            }
        }
    }

    private fun CreateNextMatchFailure.toCalculatePointsMessage(): CalculatePointsMessage = when (this) {
        CreateNextMatchFailure.NO_PARTICIPATING_TEAMS -> CalculatePointsMessage.NO_TEAMS_SAVED
        CreateNextMatchFailure.INVALID_TEAM_SLOTS -> CalculatePointsMessage.INVALID_TEAM_SLOTS
        CreateNextMatchFailure.AUTHENTICATION_REQUIRED,
        CreateNextMatchFailure.TOURNAMENT_NOT_FOUND,
        CreateNextMatchFailure.LIMIT_REACHED,
        CreateNextMatchFailure.REPOSITORY_REJECTED,
        -> CalculatePointsMessage.MATCH_CREATION_FAILED
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
