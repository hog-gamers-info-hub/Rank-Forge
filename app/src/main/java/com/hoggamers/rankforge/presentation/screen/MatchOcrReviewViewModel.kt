package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchWarning
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionRowInput
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MatchOcrReviewViewModel @Inject constructor(
    private val finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    private val matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchOcrReviewUiState>(MatchOcrReviewUiState.Loading)
    val uiState: StateFlow<MatchOcrReviewUiState> = _uiState.asStateFlow()

    private var loadedMatchKey: String? = null
    private var previewJob: Job? = null

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    ) : this(
        finalizeOcrCorrectionMatch,
        NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
    )

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        initialUiState: MatchOcrReviewUiState,
    ) : this(
        finalizeOcrCorrectionMatch,
        NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
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
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
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
        observeTournamentSlots,
        observeRoster,
    ) {
        _uiState.value = initialUiState
    }

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey

        _uiState.update {
            MatchOcrReviewUiState.Empty(
                tournamentId = tournamentId,
                matchId = matchId,
                matchResultOcrPreview = MatchResultOcrPreviewUiState.Processing,
            )
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val roleResults = MatchResultScreenshotRole.entries.map { role ->
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
            val preview = mapPreviewResults(roleResults)
            val teamContext = loadTeamContext(tournamentId)
            val matchedRows = MatchResultOcrPreviewTeamSuggestionMapper.map(
                preview = preview,
                candidateTeams = teamContext.candidateTeams,
            )
            _uiState.update { state ->
                when (state) {
                    is MatchOcrReviewUiState.Empty -> {
                        if (state.tournamentId == tournamentId && state.matchId == matchId) {
                            completeReviewStateFromPreview(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                preview = preview,
                                reviewRows = matchedRows,
                                teamNamesBySlot = teamContext.teamNamesBySlot,
                            ) ?: state.copy(matchResultOcrPreview = preview)
                        } else {
                            state
                        }
                    }
                    is MatchOcrReviewUiState.Ready -> state.copy(matchResultOcrPreview = preview)
                    is MatchOcrReviewUiState.Error -> state.copy(matchResultOcrPreview = preview)
                    MatchOcrReviewUiState.Loading -> state
                }
            }
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
            safeRowCount = 0,
            manualRequiredRowCount = correctionDraft.blockerCount,
            reviewRequiredRowCount = 0,
            manualReviewRequired = true,
            hasUnavailableEvidence = true,
            correctionDraft = correctionDraft,
            matchResultOcrPreview = preview,
            teamNamesBySlot = teamNamesBySlot,
        )
    }

    private suspend fun loadTeamContext(tournamentId: String): OcrReviewTeamContext = try {
        val persistedSlots = observeTournamentSlots(tournamentId).first()
        val persistedSlotNumbers = persistedSlots.map { it.slotNumber }.distinct()
        val candidateSlotNumbers = persistedSlotNumbers.ifEmpty { TeamSlot.SLOT_NUMBERS.toList() }
        val rosterBySlot = observeRoster(tournamentId).first()
        if (rosterBySlot.values.flatten().none { it.displayName.isNotBlank() }) {
            OcrReviewTeamContext(
                candidateTeams = emptyList(),
                teamNamesBySlot = persistedSlots.associate { it.slotNumber to it.teamName },
            )
        } else {
            OcrReviewTeamContext(
                candidateTeams = candidateSlotNumbers.map { slotNumber ->
                    TeamCandidateRosterInput(
                        teamSlot = slotNumber,
                        rosterPlayerNames = rosterBySlot[slotNumber].orEmpty().map { it.displayName },
                    )
                },
                teamNamesBySlot = persistedSlots.associate { it.slotNumber to it.teamName },
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        OcrReviewTeamContext(emptyList(), emptyMap())
    }

    private data class OcrReviewTeamContext(
        val candidateTeams: List<TeamCandidateRosterInput>,
        val teamNamesBySlot: Map<Int, String>,
    )
}

private val NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER = MatchResultOcrPreviewRunner {
    MatchResultOcrPreviewProcessingResult.MissingAsset
}

private val NO_OP_MATCHING_REPOSITORY = InMemoryTournamentRepository()
private val NO_OP_OBSERVE_TOURNAMENT_SLOTS = ObserveTournamentSlotsUseCase(NO_OP_MATCHING_REPOSITORY)
private val NO_OP_OBSERVE_ROSTER = ObserveRosterByTournamentUseCase(NO_OP_MATCHING_REPOSITORY)
