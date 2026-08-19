package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.NoOpMatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReader
import com.hoggamers.rankforge.data.ocr.NoOpMatchOcrCacheReader
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchWarning
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionRowInput
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult

@HiltViewModel
class MatchOcrReviewViewModel @Inject constructor(
    private val finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    private val matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
    private val matchLobbyPlayersOcrRunner: MatchLobbyPlayersOcrRunner,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val finalizedMatchCloudSync: FinalizedMatchCloudSyncAction =
        FinalizedMatchCloudSyncAction {
            QueueAwareActionResult(
                primaryResult = FinalizedMatchCloudSyncResult.ValidationFailure,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        },
    private val tournamentRepository: TournamentRepository = NO_OP_MATCHING_REPOSITORY,
    private val lobbyScreenshotAssetRepository: MatchLobbyScreenshotAssetRepository =
        NoOpMatchLobbyScreenshotAssetRepository(),
    private val matchOcrCacheReader: MatchOcrCacheReader = NoOpMatchOcrCacheReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchOcrReviewUiState>(MatchOcrReviewUiState.Loading)
    val uiState: StateFlow<MatchOcrReviewUiState> = _uiState.asStateFlow()
    private val _cacheAvailability = MutableStateFlow(MatchOcrCacheAvailability.UNKNOWN)
    val cacheAvailability: StateFlow<MatchOcrCacheAvailability> = _cacheAvailability.asStateFlow()

    private var loadedMatchKey: String? = null
    private var previewJob: Job? = null
    private var cacheLoadJob: Job? = null

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    ) : this(
        finalizeOcrCorrectionMatch,
        NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
    )

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        finalizedMatchCloudSync: FinalizedMatchCloudSyncAction,
        initialUiState: MatchOcrReviewUiState,
    ) : this(
        finalizeOcrCorrectionMatch,
        NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
        finalizedMatchCloudSync,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
        initialUiState: MatchOcrReviewUiState,
    ) : this(
        finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchOcrCacheReader: MatchOcrCacheReader,
        initialUiState: MatchOcrReviewUiState = MatchOcrReviewUiState.Loading,
    ) : this(
        finalizeOcrCorrectionMatch = finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner = NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        matchLobbyPlayersOcrRunner = NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        observeTournamentSlots = NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        observeRoster = NO_OP_OBSERVE_ROSTER,
        matchOcrCacheReader = matchOcrCacheReader,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        observeRoster: ObserveRosterByTournamentUseCase,
        initialUiState: MatchOcrReviewUiState,
    ) : this(
        finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        observeTournamentSlots,
        observeRoster,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
        matchLobbyPlayersOcrRunner: MatchLobbyPlayersOcrRunner,
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        observeRoster: ObserveRosterByTournamentUseCase,
        initialUiState: MatchOcrReviewUiState,
        tournamentRepository: TournamentRepository = NO_OP_MATCHING_REPOSITORY,
    ) : this(
        finalizeOcrCorrectionMatch = finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner = matchResultOcrPreviewRunner,
        matchLobbyPlayersOcrRunner = matchLobbyPlayersOcrRunner,
        observeTournamentSlots = observeTournamentSlots,
        observeRoster = observeRoster,
        tournamentRepository = tournamentRepository,
    ) {
        _uiState.value = initialUiState
    }

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        startOcrProcessing(
            tournamentId = tournamentId,
            matchId = matchId,
            allowIncompleteEvidence = false,
        )
    }

    /** Explicit user-triggered refresh; unlike load(), this always reruns current screenshot evidence. */
    fun reprocess(
        tournamentId: String,
        matchId: String,
        allowIncompleteEvidence: Boolean,
    ) {
        loadedMatchKey = "$tournamentId:$matchId"
        startOcrProcessing(
            tournamentId = tournamentId,
            matchId = matchId,
            allowIncompleteEvidence = allowIncompleteEvidence,
        )
    }

    private fun startOcrProcessing(
        tournamentId: String,
        matchId: String,
        allowIncompleteEvidence: Boolean,
    ) {
        cacheLoadJob?.cancel()

        _uiState.update {
            MatchOcrReviewUiState.Empty(
                tournamentId = tournamentId,
                matchId = matchId,
                matchResultOcrPreview = MatchResultOcrPreviewUiState.Processing,
            )
        }
        _cacheAvailability.value = MatchOcrCacheAvailability.UNKNOWN
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val (roleResults, lobbyResult) = coroutineScope {
                val resultOcr = async {
                    MatchResultScreenshotRole.entries.map { role ->
                        MatchResultOcrPreviewRoleResult(
                            role = role,
                            result = matchResultOcrPreviewRunner.process(
                                MatchResultScreenshotIdentity(
                                    tournamentId = tournamentId,
                                    matchId = matchId,
                                    role = role,
                                ),
                            ),
                        )
                    }
                }
                val lobbyOcr = async {
                    try {
                        matchLobbyPlayersOcrRunner.process(tournamentId, matchId)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        MatchLobbyPlayersOcrResult.unavailable()
                    }
                }
                resultOcr.await() to lobbyOcr.await()
            }
            val preview = mapPreviewResults(roleResults)
            val teamContext = loadTeamContext(tournamentId)
            val matchedRows = MatchResultOcrPreviewTeamSuggestionMapper.map(
                preview = preview,
                resultRows = roleResults.processedResultRows(),
                lobbyOcrResult = lobbyResult,
            )
            val lobbyPlayers = lobbyResult.toUiState()
                .takeIf { lobbyResult.hasLobbyOcrEvidence(matchId, lobbyScreenshotAssetRepository) }
                .orEmpty()
            _uiState.update { state ->
                val reviewState = if (
                    allowIncompleteEvidence || roleResults.all {
                        it.result is MatchResultOcrPreviewProcessingResult.Processed
                    }
                ) {
                    completeReviewStateFromPreview(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        preview = preview,
                        reviewRows = matchedRows,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                        lobbyPlayers = lobbyPlayers,
                    )
                } else {
                    null
                } ?: if (allowIncompleteEvidence) {
                    completeManualFallbackReviewState(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        preview = preview,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                        lobbyPlayers = lobbyPlayers,
                    )
                } else {
                    null
                }
                when (state) {
                    is MatchOcrReviewUiState.Empty -> {
                        if (state.tournamentId == tournamentId && state.matchId == matchId) {
                            reviewState ?: state.copy(
                                matchResultOcrPreview = preview,
                                teamNamesBySlot = teamContext.teamNamesBySlot,
                            )
                        } else {
                            state
                        }
                    }
                    is MatchOcrReviewUiState.Ready -> reviewState ?: state.copy(
                        matchResultOcrPreview = preview,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                    )
                    is MatchOcrReviewUiState.Error -> state.copy(matchResultOcrPreview = preview)
                    MatchOcrReviewUiState.Loading -> state
                }
            }
        }
    }

    fun loadCached(tournamentId: String, matchId: String) {
        cacheLoadJob?.cancel()
        cacheLoadJob = viewModelScope.launch {
            val cached = try {
                matchOcrCacheReader.read(tournamentId, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            val availability = cached?.availability ?: MatchOcrCacheAvailability.NOT_AVAILABLE
            _cacheAvailability.value = availability
            if (cached == null || availability != MatchOcrCacheAvailability.READY) {
                _uiState.update { state ->
                    when (state) {
                        is MatchOcrReviewUiState.Ready -> {
                            if (state.tournamentId == tournamentId && state.matchId == matchId) {
                                MatchOcrReviewUiState.Empty(
                                    tournamentId = tournamentId,
                                    matchId = matchId,
                                    teamNamesBySlot = state.teamNamesBySlot,
                                )
                            } else {
                                state
                            }
                        }
                        is MatchOcrReviewUiState.Empty -> {
                            if (state.tournamentId == tournamentId && state.matchId == matchId) {
                                state
                            } else {
                                state
                            }
                        }
                        is MatchOcrReviewUiState.Error,
                        MatchOcrReviewUiState.Loading,
                        -> state
                    }
                }
                return@launch
            }

            val preview = mapPreviewResults(cached.resultRoleResults)
            if (preview !is MatchResultOcrPreviewUiState.Ready) {
                _cacheAvailability.value = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE
                return@launch
            }
            val matchedRows = MatchResultOcrPreviewTeamSuggestionMapper.map(
                preview = preview,
                resultRows = cached.resultRoleResults.processedResultRows(),
                lobbyOcrResult = cached.lobbyResult,
            )
            val teamNamesBySlot = loadTeamContext(tournamentId).teamNamesBySlot
            val lobbyPlayers = cached.lobbyResult.toUiState()
                .takeIf { cached.lobbyResult.hasLobbyOcrEvidence(matchId, lobbyScreenshotAssetRepository) }
                .orEmpty()
            _uiState.update { state ->
                when (state) {
                    is MatchOcrReviewUiState.Ready -> state
                    is MatchOcrReviewUiState.Empty -> {
                        if (state.tournamentId == tournamentId && state.matchId == matchId) {
                            completeReviewStateFromPreview(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                preview = preview,
                                reviewRows = matchedRows,
                                teamNamesBySlot = teamNamesBySlot,
                                lobbyPlayers = lobbyPlayers,
                            ) ?: state
                        } else {
                            state
                        }
                    }
                    is MatchOcrReviewUiState.Error,
                    -> state
                    MatchOcrReviewUiState.Loading -> completeReviewStateFromPreview(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        preview = preview,
                        reviewRows = matchedRows,
                        teamNamesBySlot = teamNamesBySlot,
                        lobbyPlayers = lobbyPlayers,
                    ) ?: state
                }
            }
        }
    }

    fun loadHistoricalEvidence(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId:historical"
        if (loadedMatchKey == matchKey && _uiState.value !is MatchOcrReviewUiState.Loading) return
        loadedMatchKey = matchKey
        cacheLoadJob?.cancel()
        _cacheAvailability.value = MatchOcrCacheAvailability.NOT_AVAILABLE
        previewJob?.cancel()
        _uiState.value = MatchOcrReviewUiState.Loading
        viewModelScope.launch {
            val evidence = try {
                tournamentRepository.readPreservedMatchOcrEvidence(tournamentId, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (evidence == null) {
                _uiState.value = MatchOcrReviewUiState.Empty(
                    tournamentId = tournamentId,
                    matchId = matchId,
                )
                return@launch
            }
            _uiState.value = evidence.toHistoricalUiState(
                teamNamesBySlot = loadTeamContext(tournamentId).teamNamesBySlot,
            )
        }
    }

    /**
     * Loads already-computed OCR and team-matching evidence for this exact match.
     * This entry point remains display-only and does not replace an existing review draft.
     */
    fun loadDisplayInput(input: MatchOcrReviewDisplayInput) {
        val matchKey = "${input.tournamentId}:${input.matchId}"
        if (loadedMatchKey == matchKey && _uiState.value !is MatchOcrReviewUiState.Empty) return
        loadedMatchKey = matchKey
        _uiState.value = MatchOcrReviewUiStateMapper.map(input)
    }

    fun onPlacementChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, rowIndex, value)
        }
    }

    fun onKillsChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, rowIndex, value)
        }
    }

    fun onAssignedTeamSlotChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(draft, rowIndex, value)
        }
    }

    fun onResetRowCorrection(rowIndex: Int) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(draft, rowIndex)
        }
    }

    fun onResetAllCorrections() {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onResetAllCorrections(draft)
        }
    }

    private fun updateCorrectionDraft(
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft,
    ) {
        _uiState.update { state ->
            if (state is MatchOcrReviewUiState.Ready) {
                if (state.finalization.isFinalized) return@update state
                val currentDraft = state.correctionDraft
                    ?: MatchOcrReviewCorrectionDraftReducer.createInitialDraft(state.rows)
                state.copy(
                    correctionDraft = transform(currentDraft),
                    finalization = state.finalization.copy(
                        showWarningConfirmation = false,
                        error = null,
                    ),
                )
            } else {
                state
            }
        }
    }

    fun onFinalizeOcrCorrection() {
        val current = _uiState.value as? MatchOcrReviewUiState.Ready ?: return
        if (current.finalization.isFinalizing || current.finalization.isFinalized) return
        val correctionDraft = current.correctionDraft
        if (correctionDraft == null) {
            updateFinalizationError(MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT)
            return
        }
        if (correctionDraft.blockerCount > 0) {
            updateFinalizationError(MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED)
            return
        }
        if (correctionDraft.warningCount > 0) {
            _uiState.updateReady {
                it.copy(
                    finalization = it.finalization.copy(
                        showWarningConfirmation = true,
                        error = null,
                    ),
                )
            }
            return
        }

        finalizeCurrentCorrectionDraft(warningConfirmationAccepted = false)
    }

    fun onConfirmFinalizeWarnings() {
        val current = _uiState.value as? MatchOcrReviewUiState.Ready ?: return
        val correctionDraft = current.correctionDraft ?: return
        if (
            current.finalization.isFinalizing ||
            current.finalization.isFinalized ||
            !current.finalization.showWarningConfirmation ||
            correctionDraft.blockerCount > 0
        ) return

        finalizeCurrentCorrectionDraft(warningConfirmationAccepted = true)
    }

    fun onDismissFinalizeWarnings() {
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(showWarningConfirmation = false),
            )
        }
    }

    private fun finalizeCurrentCorrectionDraft(warningConfirmationAccepted: Boolean) {
        val current = _uiState.value as? MatchOcrReviewUiState.Ready ?: return
        val correctionDraft = current.correctionDraft
        if (correctionDraft == null) {
            updateFinalizationError(MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT)
            return
        }
        if (correctionDraft.blockerCount > 0) {
            updateFinalizationError(MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED)
            return
        }

        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    isFinalizing = true,
                    showWarningConfirmation = false,
                    error = null,
                ),
            )
        }
        viewModelScope.launch {
            val result = finalizeOcrCorrectionMatch(
                FinalizeOcrCorrectionMatchInput(
                    tournamentId = current.tournamentId,
                    matchId = current.matchId,
                    correctionRows = correctionDraft.toFinalizeRows(current.rows),
                    warningConfirmationAccepted = warningConfirmationAccepted,
                    sourceScreenshotId = null,
                ),
            )
            _uiState.updateReady { ready ->
                when (result) {
                    is FinalizeOcrCorrectionMatchResult.Finalized -> ready.copy(
                        finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
                    )
                    is FinalizeOcrCorrectionMatchResult.ConfirmationRequired -> ready.copy(
                        finalization = ready.finalization.copy(
                            isFinalizing = false,
                            showWarningConfirmation = true,
                            error = null,
                        ),
                    )
                    is FinalizeOcrCorrectionMatchResult.Blocked -> ready.copy(
                        finalization = ready.finalization.copy(
                            isFinalizing = false,
                            showWarningConfirmation = false,
                            error = result.failures.toUiError(),
                        ),
                    )
                    FinalizeOcrCorrectionMatchResult.UnexpectedFailure -> ready.copy(
                        finalization = ready.finalization.copy(
                            isFinalizing = false,
                            showWarningConfirmation = false,
                            error = MatchOcrReviewFinalizationError.UNEXPECTED_FAILURE,
                        ),
                    )
                }
            }
            if (result is FinalizeOcrCorrectionMatchResult.Finalized) {
                launchFinalizedMatchCloudSync(current.tournamentId)
            }
        }
    }

    private fun launchFinalizedMatchCloudSync(tournamentId: String) {
        viewModelScope.launch {
            try {
                finalizedMatchCloudSync(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Local finalization remains authoritative when cloud sync fails.
            }
        }
    }

    private fun updateFinalizationError(error: MatchOcrReviewFinalizationError) {
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    isFinalizing = false,
                    showWarningConfirmation = false,
                    error = error,
                ),
            )
        }
    }

    private fun MutableStateFlow<MatchOcrReviewUiState>.updateReady(
        transform: (MatchOcrReviewUiState.Ready) -> MatchOcrReviewUiState.Ready,
    ) {
        update { state ->
            if (state is MatchOcrReviewUiState.Ready) transform(state) else state
        }
    }

    private fun MatchOcrReviewCorrectionDraft.toFinalizeRows(
        reviewRows: List<MatchOcrReviewRowUiState>,
    ): List<FinalizeOcrCorrectionRowInput> {
        val reviewRowsByIndex = reviewRows.associateBy { it.rowIndex }
        return rows.map { row ->
            val reviewRow = reviewRowsByIndex[row.rowIndex]
            FinalizeOcrCorrectionRowInput(
                rowIndex = row.rowIndex,
                correctedPlacement = row.placementDraftValue,
                correctedKills = row.killsDraftValue,
                correctedTeamSlotNumber = row.assignedTeamSlotDraftValue,
                warnings = row.validation.warnings.mapNotNull { it.toFinalizeWarning() }.toSet(),
                originalOcrText = reviewRow?.detectedPlayerNameEvidenceLabel,
                originalPlacement = reviewRow?.originalParsedPlacementValue,
                originalKills = reviewRow?.originalParsedKillValue,
                originalSuggestedTeamSlot = reviewRow?.originalSuggestedTeamSlot,
                confidenceSummary = reviewRow?.let {
                    "${it.confidenceTierLabel}|${it.confidenceScoreDisplayValue}"
                },
                safetySummary = reviewRow?.assignmentSafetyStatusLabel,
                manualReviewRequired = reviewRow?.let {
                    it.blockerLabels.isNotEmpty() || it.warningLabels.isNotEmpty()
                } ?: row.originallyRequiredManualReview,
            )
        }
    }

    private fun MatchOcrReviewCorrectionReason.toFinalizeWarning(): FinalizeOcrCorrectionMatchWarning? =
        when (this) {
            MatchOcrReviewCorrectionReason.PLACEMENT_CHANGED_FROM_OCR ->
                FinalizeOcrCorrectionMatchWarning.PLACEMENT_CHANGED_FROM_OCR
            MatchOcrReviewCorrectionReason.KILLS_CHANGED_FROM_OCR ->
                FinalizeOcrCorrectionMatchWarning.KILLS_CHANGED_FROM_OCR
            MatchOcrReviewCorrectionReason.TEAM_SLOT_CHANGED_FROM_SUGGESTION ->
                FinalizeOcrCorrectionMatchWarning.TEAM_SLOT_CHANGED_FROM_SUGGESTION
            MatchOcrReviewCorrectionReason.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW ->
                FinalizeOcrCorrectionMatchWarning.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW
            MatchOcrReviewCorrectionReason.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE ->
                FinalizeOcrCorrectionMatchWarning.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE
            else -> null
        }

    private fun Set<FinalizeOcrCorrectionMatchFailure>.toUiError(): MatchOcrReviewFinalizationError =
        when {
            FinalizeOcrCorrectionMatchFailure.MISSING_CORRECTION_DRAFT in this ->
                MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT
            FinalizeOcrCorrectionMatchFailure.MISSING_TOURNAMENT in this ->
                MatchOcrReviewFinalizationError.MISSING_TOURNAMENT
            FinalizeOcrCorrectionMatchFailure.MISSING_MATCH in this ->
                MatchOcrReviewFinalizationError.MISSING_MATCH
            FinalizeOcrCorrectionMatchFailure.ALREADY_FINALIZED in this ->
                MatchOcrReviewFinalizationError.ALREADY_FINALIZED
            FinalizeOcrCorrectionMatchFailure.UNEXPECTED_FAILURE in this ->
                MatchOcrReviewFinalizationError.UNEXPECTED_FAILURE
            else -> MatchOcrReviewFinalizationError.FINALIZATION_FAILED
        }

    private fun mapPreviewResults(
        roleResults: List<MatchResultOcrPreviewRoleResult>,
    ): MatchResultOcrPreviewUiState {
        if (roleResults.any { it.result is MatchResultOcrPreviewProcessingResult.Processed }) {
            return MatchResultOcrPreviewUiStateMapper.map(roleResults)
        }
        return when {
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop } ->
                MatchResultOcrPreviewUiState.Error("Confirm the screenshot crop before running OCR preview.")
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.MissingAsset } ->
                MatchResultOcrPreviewUiState.Error("The match-result screenshot asset is unavailable.")
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.MissingLocalOriginal } ->
                MatchResultOcrPreviewUiState.Error("The local match-result screenshot is unavailable.")
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.InvalidCrop } ->
                MatchResultOcrPreviewUiState.Error("The confirmed screenshot crop is invalid.")
            else -> MatchResultOcrPreviewUiState.Error("OCR preview could not be processed.")
        }
    }

    private fun completeReviewStateFromPreview(
        tournamentId: String,
        matchId: String,
        preview: MatchResultOcrPreviewUiState,
        reviewRows: List<MatchOcrReviewRowUiState>?,
        teamNamesBySlot: Map<Int, String>,
        lobbyPlayers: List<MatchOcrReviewLobbySlotUiState>,
    ): MatchOcrReviewUiState.Ready? {
        val rows = reviewRows ?: MatchResultOcrPreviewUiStateMapper.toReviewRows(preview) ?: return null
        val correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
            rows = rows,
            assignmentRequired = true,
        )
        return MatchOcrReviewUiState.Ready(
            tournamentId = tournamentId,
            matchId = matchId,
            rowCount = rows.size,
            rows = rows,
            blockerCount = correctionDraft.blockerCount,
            warningCount = correctionDraft.warningCount,
            safeRowCount = rows.count { it.assignmentSafetyStatusLabel == SAFE_AUTOMATIC_ASSIGNMENT_LABEL },
            manualRequiredRowCount = rows.count { it.assignmentSafetyStatusLabel == MANUAL_REQUIRED_LABEL },
            reviewRequiredRowCount = rows.count { it.assignmentSafetyStatusLabel == REVIEW_REQUIRED_LABEL },
            manualReviewRequired = correctionDraft.blockerCount > 0 || correctionDraft.warningCount > 0,
            hasUnavailableEvidence = rows.any { row ->
                row.confidenceTierLabel == UNAVAILABLE_LABEL ||
                    row.assignmentSafetyStatusLabel == UNAVAILABLE_LABEL ||
                    row.topThreeSuggestionsSummary == listOf(NO_SUGGESTIONS_LABEL)
            },
            correctionDraft = correctionDraft,
            matchResultOcrPreview = preview,
            teamNamesBySlot = teamNamesBySlot,
            lobbyPlayers = lobbyPlayers,
        )
    }

    private fun completeManualFallbackReviewState(
        tournamentId: String,
        matchId: String,
        preview: MatchResultOcrPreviewUiState,
        teamNamesBySlot: Map<Int, String>,
        lobbyPlayers: List<MatchOcrReviewLobbySlotUiState>,
    ): MatchOcrReviewUiState.Ready {
        val rows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows()
        val correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
            rows = rows,
            assignmentRequired = true,
        )
        return MatchOcrReviewUiState.Ready(
            tournamentId = tournamentId,
            matchId = matchId,
            rowCount = rows.size,
            rows = rows,
            blockerCount = correctionDraft.blockerCount,
            warningCount = correctionDraft.warningCount,
            safeRowCount = 0,
            manualRequiredRowCount = rows.size,
            reviewRequiredRowCount = 0,
            manualReviewRequired = true,
            hasUnavailableEvidence = true,
            correctionDraft = correctionDraft,
            matchResultOcrPreview = preview,
            teamNamesBySlot = teamNamesBySlot,
            lobbyPlayers = lobbyPlayers,
        )
    }

    private suspend fun loadTeamContext(tournamentId: String): OcrReviewTeamContext = try {
        val persistedSlots = observeTournamentSlots(tournamentId).first()
        OcrReviewTeamContext(persistedSlots.associate { it.slotNumber to it.teamName })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        OcrReviewTeamContext(emptyMap())
    }

    private data class OcrReviewTeamContext(
        val teamNamesBySlot: Map<Int, String>,
    )

    private fun PreservedMatchOcrEvidence.toHistoricalUiState(
        teamNamesBySlot: Map<Int, String>,
    ): MatchOcrReviewUiState {
        if (rows.isEmpty()) {
            return MatchOcrReviewUiState.Empty(
                tournamentId = tournamentId,
                matchId = matchId,
                teamNamesBySlot = teamNamesBySlot,
            )
        }

        val snapshotsByRow = correctionSnapshots.associateBy { it.rowIndex }
        val reviewRows = rows.sortedBy { it.rowIndex }.map { row ->
            val correction = snapshotsByRow[row.rowIndex]
            val placement = correction?.correctedPlacement ?: row.originalPlacement
            val kills = correction?.correctedKills ?: row.originalKills
            val teamSlot = correction?.correctedTeamSlot ?: row.originalSuggestedTeamSlot
            MatchOcrReviewRowUiState(
                rowIndex = row.rowIndex,
                expectedPlacementLabel = (row.rowIndex + 1).toString(),
                detectedPlacementDisplayValue = placement?.toString() ?: "Unavailable",
                placementStatusLabel = "Preserved OCR evidence",
                detectedKillDisplayValue = kills?.toString() ?: "Unavailable",
                killStatusLabel = "Preserved OCR evidence",
                detectedPlayerNameEvidenceLabel = row.originalOcrText ?: "Unavailable",
                playerNameStatusLabel = "Preserved OCR evidence",
                suggestedTeamSlotDisplayValue = teamSlot?.toString() ?: "Unavailable",
                confidenceScoreDisplayValue = row.confidenceSummary ?: "Unavailable",
                confidenceTierLabel = "Preserved OCR evidence",
                assignmentSafetyStatusLabel = row.safetySummary ?: "Unavailable",
                topThreeSuggestionsSummary = listOf(
                    "Original suggested slot: ${row.originalSuggestedTeamSlot ?: "Unavailable"}",
                ),
                warningLabels = emptyList(),
                blockerLabels = if (row.manualReviewRequired) {
                    listOf("Manual review required")
                } else {
                    emptyList()
                },
                severity = if (row.manualReviewRequired) {
                    MatchOcrReviewSeverity.BLOCKING
                } else {
                    MatchOcrReviewSeverity.INFORMATIONAL
                },
                originalParsedPlacementValue = row.originalPlacement,
                originalParsedKillValue = row.originalKills,
                originalSuggestedTeamSlot = row.originalSuggestedTeamSlot,
            )
        }
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
            rows = reviewRows,
            assignmentRequired = true,
        )
        val finalizedDraft = MatchOcrReviewCorrectionDraftReducer.validate(
            initialDraft.copy(
                rows = initialDraft.rows.map { draftRow ->
                    val reviewRow = reviewRows.first { it.rowIndex == draftRow.rowIndex }
                    draftRow.copy(
                        placementDraftValue = reviewRow.detectedPlacementDisplayValue,
                        killsDraftValue = reviewRow.detectedKillDisplayValue,
                        assignedTeamSlotDraftValue = reviewRow.suggestedTeamSlotDisplayValue,
                    )
                },
            ),
        )
        val unavailableEvidence = reviewRows.any { row ->
            row.detectedPlacementDisplayValue == "Unavailable" ||
                row.detectedKillDisplayValue == "Unavailable" ||
                row.detectedPlayerNameEvidenceLabel == "Unavailable" ||
                row.confidenceScoreDisplayValue == "Unavailable" ||
                row.assignmentSafetyStatusLabel == "Unavailable"
        }
        val manualReviewCount = reviewRows.count { it.blockerLabels.isNotEmpty() }
        return MatchOcrReviewUiState.Ready(
            tournamentId = tournamentId,
            matchId = matchId,
            rowCount = reviewRows.size,
            rows = reviewRows,
            blockerCount = manualReviewCount,
            warningCount = 0,
            safeRowCount = reviewRows.size - manualReviewCount,
            manualRequiredRowCount = manualReviewCount,
            reviewRequiredRowCount = 0,
            manualReviewRequired = manualReviewCount > 0,
            hasUnavailableEvidence = unavailableEvidence,
            correctionDraft = finalizedDraft,
            finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
            matchResultOcrPreview = MatchResultOcrPreviewUiState.NotRequested,
            teamNamesBySlot = teamNamesBySlot,
        )
    }
}

private val NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER = MatchResultOcrPreviewRunner {
    MatchResultOcrPreviewProcessingResult.MissingAsset
}

private val NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER = MatchLobbyPlayersOcrRunner { _, _ ->
    MatchLobbyPlayersOcrResult.unavailable()
}

private fun MatchLobbyPlayersOcrResult.toUiState(): List<MatchOcrReviewLobbySlotUiState> =
    slots.sortedBy { it.slotNumber }.map { slot ->
        MatchOcrReviewLobbySlotUiState(
            slotNumber = slot.slotNumber,
            players = slot.players.sortedBy { it.playerNumber }.map { player ->
                MatchOcrReviewLobbyPlayerUiState(
                    playerNumber = player.playerNumber,
                    playerName = player.playerName,
                )
            },
        )
    }

private suspend fun MatchLobbyPlayersOcrResult.hasLobbyOcrEvidence(
    matchId: String,
    lobbyScreenshotAssetRepository: MatchLobbyScreenshotAssetRepository,
): Boolean {
    val hasUsableScreenshot = try {
        lobbyScreenshotAssetRepository
            .observeByMatchId(matchId)
            .first()
            .any { asset ->
                asset.cropProfileId == OcrCropValidationProfiles.Lobby.id &&
                    asset.cropLeft != null &&
                    asset.cropTop != null &&
                    asset.cropRight != null &&
                    asset.cropBottom != null
            }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
    return hasUsableScreenshot || slots != MatchLobbyPlayersOcrResult.unavailable().slots
}

private val NO_OP_MATCHING_REPOSITORY = InMemoryTournamentRepository()
private val NO_OP_OBSERVE_TOURNAMENT_SLOTS = ObserveTournamentSlotsUseCase(NO_OP_MATCHING_REPOSITORY)
private val NO_OP_OBSERVE_ROSTER = ObserveRosterByTournamentUseCase(NO_OP_MATCHING_REPOSITORY)

private fun List<MatchResultOcrPreviewRoleResult>.processedResultRows(): List<MatchResultOcrRow> =
    flatMap { roleResult ->
        (roleResult.result as? MatchResultOcrPreviewProcessingResult.Processed)
            ?.extraction
            ?.rows
            .orEmpty()
    }

private const val SAFE_AUTOMATIC_ASSIGNMENT_LABEL = "Safe automatic assignment"
private const val REVIEW_REQUIRED_LABEL = "Review required"
private const val MANUAL_REQUIRED_LABEL = "Manual required"
private const val UNAVAILABLE_LABEL = "Unavailable"
private const val NO_SUGGESTIONS_LABEL = "No suggestions"
