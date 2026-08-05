package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrProcessor
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrSourceProviderResult
import com.hoggamers.rankforge.domain.ocr.review.ProcessMatchOcrEvidence
import com.hoggamers.rankforge.domain.ocr.review.ProcessMatchOcrFailure
import com.hoggamers.rankforge.domain.ocr.review.ProcessMatchOcrResult
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchWarning
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionRowInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MatchOcrReviewViewModel @Inject constructor(
    private val matchOcrProcessor: MatchOcrProcessor,
    private val finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchOcrReviewUiState>(MatchOcrReviewUiState.Loading)
    val uiState: StateFlow<MatchOcrReviewUiState> = _uiState.asStateFlow()

    private var loadedMatchKey: String? = null

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    ) : this(
        matchOcrProcessor = MatchOcrProcessor { _, _ ->
            ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.SourceLoading(
                    MatchOcrSourceProviderResult.MetadataNotFound,
                ),
            )
        },
        finalizeOcrCorrectionMatch = finalizeOcrCorrectionMatch,
    )

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        initialUiState: MatchOcrReviewUiState,
    ) : this(finalizeOcrCorrectionMatch) {
        _uiState.value = initialUiState
    }

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey

        _uiState.value = MatchOcrReviewUiState.Loading

        viewModelScope.launch {
            val result = try {
                matchOcrProcessor(tournamentId, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (loadedMatchKey == matchKey) {
                    _uiState.value = MatchOcrReviewUiState.Error(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        message = "OCR processing could not complete.",
                    )
                }
                return@launch
            }

            if (loadedMatchKey != matchKey) return@launch

            _uiState.value = when (result) {
                is ProcessMatchOcrResult.Success -> {
                    mapDisplayInput(
                        result.evidence.toDisplayInput(
                            tournamentId = tournamentId,
                            matchId = matchId,
                        ),
                    )
                }

                is ProcessMatchOcrResult.Failed -> {
                    result.failure.toUiState(
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                }
            }
        }
    }

    /**
     * Loads already-computed OCR and team-matching evidence for this exact match.
     *
     * This entry point remains available for deterministic presentation tests and callers that
     * already own approved OCR/matching evidence. Production route loading now uses MatchOcrProcessor
     * and still does not persist or finalize any OCR result automatically.
     */
    fun loadDisplayInput(input: MatchOcrReviewDisplayInput) {
        val matchKey = "${input.tournamentId}:${input.matchId}"
        if (loadedMatchKey == matchKey && _uiState.value !is MatchOcrReviewUiState.Empty) return

        loadedMatchKey = matchKey
        _uiState.value = mapDisplayInput(input)
    }

    private fun mapDisplayInput(
        input: MatchOcrReviewDisplayInput,
    ): MatchOcrReviewUiState {
        val mapped = MatchOcrReviewUiStateMapper.map(input)

        return if (mapped is MatchOcrReviewUiState.Ready) {
            mapped.copy(
                correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
                    mapped.rows,
                ),
            )
        } else {
            mapped
        }
    }

    private fun ProcessMatchOcrEvidence.toDisplayInput(
        tournamentId: String,
        matchId: String,
    ): MatchOcrReviewDisplayInput {
        val reviewRowsByPlacement = reviewResult.rows.associateBy {
            it.expectedPlacementId
        }
        val placementRowsByPlacement = placementResult?.rows
            ?.associateBy { it.expectedPlacementId }
            .orEmpty()
        val playerNameRowsByPlacement = playerNameResult?.rows
            ?.associateBy { it.expectedPlacementId }
            .orEmpty()
        val killRowsByPlacement = killResult?.rows
            ?.associateBy { it.expectedPlacementId }
            .orEmpty()
        val teamRowsByPlacement = teamIdentification?.rows
            ?.associateBy { it.expectedPlacementId }
            .orEmpty()

        return MatchOcrReviewDisplayInput(
            tournamentId = tournamentId,
            matchId = matchId,
            rows = EXPECTED_PLACEMENT_IDS.map { expectedPlacementId ->
                val reviewRow = reviewRowsByPlacement[expectedPlacementId]
                val placementRow = placementRowsByPlacement[expectedPlacementId]
                val playerNameRow = playerNameRowsByPlacement[expectedPlacementId]
                val killRow = killRowsByPlacement[expectedPlacementId]
                val teamRow = teamRowsByPlacement[expectedPlacementId]

                val detectedPlayerName = playerNameRow
                    ?.detectedName
                    ?.takeIf { it.isNotBlank() }
                    ?: teamRow
                        ?.detectedPlayerNames
                        .orEmpty()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.joinToString(" | ")

                MatchOcrReviewRowEvidenceInput(
                    rowIndex = expectedPlacementId - 1,
                    expectedPlacementId = expectedPlacementId,
                    detectedPlacementValue = placementRow?.detectedValue,
                    detectedKillValue = killRow?.detectedValue,
                    detectedPlayerName = detectedPlayerName,
                    ocrFields = reviewRow?.fields.orEmpty(),
                    suggestions = teamRow?.suggestions,
                    confidenceAssessment = teamRow?.confidenceAssessment,
                    safetyResult = teamRow?.assignmentSafety,
                )
            },
        )
    }

    private fun ProcessMatchOcrFailure.toUiState(
        tournamentId: String,
        matchId: String,
    ): MatchOcrReviewUiState.Error = MatchOcrReviewUiState.Error(
        tournamentId = tournamentId,
        matchId = matchId,
        message = when (this) {
            ProcessMatchOcrFailure.InvalidContext ->
                "OCR review context is invalid."

            is ProcessMatchOcrFailure.SourceLoading -> when (result) {
                MatchOcrSourceProviderResult.InvalidContext ->
                    "OCR review context is invalid."

                MatchOcrSourceProviderResult.MetadataNotFound ->
                    "No preserved screenshot is available for OCR review."

                MatchOcrSourceProviderResult.TournamentMismatch ->
                    "The linked screenshot does not belong to this tournament."

                MatchOcrSourceProviderResult.LocalFileMissing ->
                    "The linked screenshot is no longer available locally."

                MatchOcrSourceProviderResult.UnreadableImage ->
                    "The linked screenshot could not be decoded for OCR."

                MatchOcrSourceProviderResult.UnsafeImage ->
                    "The linked screenshot is outside the supported OCR image limits."

                MatchOcrSourceProviderResult.LoadingFailure ->
                    "The linked screenshot could not be loaded for OCR."

                is MatchOcrSourceProviderResult.Loaded ->
                    "The OCR screenshot source could not be prepared."
            }

            ProcessMatchOcrFailure.MissingBaselineCandidate ->
                "OCR preprocessing did not produce the approved baseline candidate."

            ProcessMatchOcrFailure.UnexpectedPreprocessing ->
                "OCR preprocessing could not complete."

            ProcessMatchOcrFailure.UnexpectedExtraction ->
                "OCR text extraction could not complete."

            ProcessMatchOcrFailure.UnexpectedPlacementParsing ->
                "Placement OCR parsing could not complete."

            ProcessMatchOcrFailure.UnexpectedPlayerNameParsing ->
                "Player-name OCR parsing could not complete."

            ProcessMatchOcrFailure.UnexpectedKillParsing ->
                "Kill OCR parsing could not complete."

            ProcessMatchOcrFailure.UnexpectedReviewAnalysis ->
                "OCR review evidence could not be prepared."

            ProcessMatchOcrFailure.RosterLoadingFailure ->
                "The tournament roster could not be loaded for team matching."

            ProcessMatchOcrFailure.UnexpectedTeamIdentification ->
                "Team matching could not complete."
        },
    )

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
        ) {
            return
        }

        finalizeCurrentCorrectionDraft(warningConfirmationAccepted = true)
    }

    fun onDismissFinalizeWarnings() {
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    showWarningConfirmation = false,
                ),
            )
        }
    }

    private fun finalizeCurrentCorrectionDraft(
        warningConfirmationAccepted: Boolean,
    ) {
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
                        finalization = MatchOcrReviewFinalizationUiState(
                            isFinalized = true,
                        ),
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

    private fun updateFinalizationError(
        error: MatchOcrReviewFinalizationError,
    ) {
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
            if (state is MatchOcrReviewUiState.Ready) {
                transform(state)
            } else {
                state
            }
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
                warnings = row.validation.warnings
                    .mapNotNull { it.toFinalizeWarning() }
                    .toSet(),
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

    private fun MatchOcrReviewCorrectionReason.toFinalizeWarning():
        FinalizeOcrCorrectionMatchWarning? =
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

    private fun Set<FinalizeOcrCorrectionMatchFailure>.toUiError():
        MatchOcrReviewFinalizationError =
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

            else ->
                MatchOcrReviewFinalizationError.FINALIZATION_FAILED
        }

    private companion object {
        val EXPECTED_PLACEMENT_IDS = 1..12
    }
}