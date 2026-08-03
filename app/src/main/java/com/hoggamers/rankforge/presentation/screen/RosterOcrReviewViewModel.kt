package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrFailure
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrResult
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterResult
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.ReplaceTournamentRosterInCloudUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun interface RosterOcrCloudReplacementInvoker {
    suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentRosterCloudReplacementResult>
}

@HiltViewModel
class RosterOcrReviewViewModel @Inject constructor(
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val processRosterOcr: ProcessRosterOcrUseCase,
    private val replaceConfirmedTournamentRoster: ReplaceConfirmedTournamentRosterUseCase,
    private val replaceTournamentRosterInCloud: ReplaceTournamentRosterInCloudUseCase,
) : ViewModel() {
    private var cloudReplacementInvoker = RosterOcrCloudReplacementInvoker { tournamentId ->
        replaceTournamentRosterInCloud(tournamentId)
    }

    internal constructor(
        getTournamentById: GetTournamentByIdUseCase,
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        processRosterOcr: ProcessRosterOcrUseCase,
        replaceConfirmedTournamentRoster: ReplaceConfirmedTournamentRosterUseCase,
        replaceTournamentRosterInCloud: ReplaceTournamentRosterInCloudUseCase,
        cloudReplacementInvoker: RosterOcrCloudReplacementInvoker,
    ) : this(
        getTournamentById = getTournamentById,
        observeTournamentSlots = observeTournamentSlots,
        processRosterOcr = processRosterOcr,
        replaceConfirmedTournamentRoster = replaceConfirmedTournamentRoster,
        replaceTournamentRosterInCloud = replaceTournamentRosterInCloud,
    ) {
        this.cloudReplacementInvoker = cloudReplacementInvoker
    }

    private val _uiState = MutableStateFlow<RosterOcrReviewUiState>(
        RosterOcrReviewUiState.Unavailable(
            tournamentId = null,
            failure = RosterOcrReviewLoadFailure.INVALID_TOURNAMENT_CONTEXT,
        ),
    )
    val uiState: StateFlow<RosterOcrReviewUiState> = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private var loadedTournamentId: String? = null
    private var contextGeneration = 0L

    fun load(tournamentId: String) {
        if (tournamentId.isBlank()) {
            operationJob?.cancel()
            operationJob = null
            loadedTournamentId = null
            contextGeneration++
            _uiState.value = RosterOcrReviewUiState.Unavailable(
                tournamentId = null,
                failure = RosterOcrReviewLoadFailure.INVALID_TOURNAMENT_CONTEXT,
            )
            return
        }

        if (loadedTournamentId == tournamentId && _uiState.value !is RosterOcrReviewUiState.Unavailable) {
            return
        }

        operationJob?.cancel()
        loadedTournamentId = tournamentId
        val generation = ++contextGeneration
        _uiState.value = RosterOcrReviewUiState.LoadingTeamContext(tournamentId)
        operationJob = viewModelScope.launch {
            try {
                val tournament = getTournamentById(tournamentId).first()
                if (!isCurrent(generation, tournamentId)) return@launch
                if (tournament == null || tournament.id != tournamentId) {
                    _uiState.value = RosterOcrReviewUiState.Unavailable(
                        tournamentId = tournamentId,
                        failure = RosterOcrReviewLoadFailure.TOURNAMENT_NOT_FOUND,
                    )
                    return@launch
                }

                val slots = observeTournamentSlots(tournamentId).first().sortedBy { it.slotNumber }
                if (!isCompleteTeamContext(tournamentId, slots)) {
                    _uiState.value = RosterOcrReviewUiState.Unavailable(
                        tournamentId = tournamentId,
                        failure = RosterOcrReviewLoadFailure.INCOMPLETE_TEAM_CONTEXT,
                    )
                    return@launch
                }

                _uiState.value = RosterOcrReviewUiState.ReadyToProcess(
                    tournamentId = tournamentId,
                    teamSlots = slots,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (isCurrent(generation, tournamentId)) {
                    _uiState.value = RosterOcrReviewUiState.Unavailable(
                        tournamentId = tournamentId,
                        failure = RosterOcrReviewLoadFailure.UNEXPECTED_FAILURE,
                    )
                }
            }
        }
    }

    fun startProcessing() {
        val ready = _uiState.value as? RosterOcrReviewUiState.ReadyToProcess ?: return
        val generation = contextGeneration
        _uiState.value = RosterOcrReviewUiState.Processing(
            tournamentId = ready.tournamentId,
            teamSlots = ready.teamSlots,
        )
        operationJob = viewModelScope.launch {
            val result = try {
                processRosterOcr(ready.tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (isCurrent(generation, ready.tournamentId)) {
                    _uiState.value = RosterOcrReviewUiState.ReadyToProcess(
                        tournamentId = ready.tournamentId,
                        teamSlots = ready.teamSlots,
                        processingFailure = RosterOcrReviewProcessingFailure.UnexpectedFailure,
                    )
                }
                return@launch
            }
            if (!isCurrent(generation, ready.tournamentId)) return@launch

            when (result) {
                is ProcessRosterOcrResult.Failed -> {
                    _uiState.value = RosterOcrReviewUiState.ReadyToProcess(
                        tournamentId = ready.tournamentId,
                        teamSlots = ready.teamSlots,
                        processingFailure = RosterOcrReviewProcessingFailure.Controlled(result.failure),
                    )
                }
                is ProcessRosterOcrResult.Success -> {
                    when (
                        val creation = RosterOcrReviewDraftReducer.createInitialDraft(
                            tournamentId = ready.tournamentId,
                            currentTeamSlots = ready.teamSlots,
                            evidence = result.evidence,
                        )
                    ) {
                        is RosterOcrReviewDraftCreationResult.Created -> {
                            _uiState.value = RosterOcrReviewUiState.Reviewing(
                                tournamentId = ready.tournamentId,
                                teamSlots = ready.teamSlots,
                                evidence = result.evidence,
                                draft = creation.draft,
                            )
                        }
                        is RosterOcrReviewDraftCreationResult.Rejected -> {
                            _uiState.value = RosterOcrReviewUiState.ReadyToProcess(
                                tournamentId = ready.tournamentId,
                                teamSlots = ready.teamSlots,
                                processingFailure = RosterOcrReviewProcessingFailure.DraftCreation(
                                    creation.reason,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun updatePlayerName(slotNumber: Int, playerRowIndex: Int, value: String) {
        updateDraft { draft ->
            RosterOcrReviewDraftReducer.updatePlayerName(draft, slotNumber, playerRowIndex, value)
        }
    }

    fun onPlayerNameChanged(slotNumber: Int, playerRowIndex: Int, value: String) =
        updatePlayerName(slotNumber, playerRowIndex, value)

    fun resetPlayerCorrection(slotNumber: Int, playerRowIndex: Int) {
        updateDraft { draft ->
            RosterOcrReviewDraftReducer.resetPlayerCorrection(draft, slotNumber, playerRowIndex)
        }
    }

    fun onResetPlayerCorrection(slotNumber: Int, playerRowIndex: Int) =
        resetPlayerCorrection(slotNumber, playerRowIndex)

    fun resetSlotCorrections(slotNumber: Int) {
        updateDraft { draft ->
            RosterOcrReviewDraftReducer.resetSlotCorrections(draft, slotNumber)
        }
    }

    fun onResetSlotCorrections(slotNumber: Int) = resetSlotCorrections(slotNumber)

    fun resetAllCorrections() {
        updateDraft(RosterOcrReviewDraftReducer::resetAllCorrections)
    }

    fun onResetAllCorrections() = resetAllCorrections()

    fun abandonReview() {
        val reviewing = _uiState.value as? RosterOcrReviewUiState.Reviewing ?: return
        if (reviewing.localReplacement !is RosterOcrLocalReplacementState.Ready &&
            reviewing.localReplacement !is RosterOcrLocalReplacementState.Failed
        ) return
        _uiState.value = RosterOcrReviewUiState.ReadyToProcess(
            tournamentId = reviewing.tournamentId,
            teamSlots = reviewing.teamSlots,
        )
    }

    fun requestConfirmation() {
        val reviewing = _uiState.value as? RosterOcrReviewUiState.Reviewing ?: return
        if (!reviewing.draft.canConfirm ||
            reviewing.confirmation != RosterOcrReviewConfirmationState.NotRequested ||
            reviewing.localReplacement is RosterOcrLocalReplacementState.InProgress
        ) return
        _uiState.value = reviewing.copy(
            confirmation = RosterOcrReviewConfirmationState.Requested,
            localReplacement = RosterOcrLocalReplacementState.Ready,
        )
    }

    fun dismissConfirmation() {
        val reviewing = _uiState.value as? RosterOcrReviewUiState.Reviewing ?: return
        _uiState.value = reviewing.copy(
            confirmation = RosterOcrReviewConfirmationState.NotRequested,
        )
    }

    fun confirmReplacement() {
        val reviewing = _uiState.value as? RosterOcrReviewUiState.Reviewing ?: return
        if (reviewing.confirmation != RosterOcrReviewConfirmationState.Requested ||
            !reviewing.draft.canConfirm ||
            reviewing.localReplacement is RosterOcrLocalReplacementState.InProgress
        ) return

        val candidate = reviewing.draft.toConfirmedRosterReplacementCandidateOrNull()
        if (candidate == null) {
            _uiState.value = reviewing.copy(
                confirmation = RosterOcrReviewConfirmationState.NotRequested,
                localReplacement = RosterOcrLocalReplacementState.Failed(
                    RosterOcrReviewLocalReplacementError.DRAFT_BLOCKED,
                ),
            )
            return
        }

        val generation = contextGeneration
        _uiState.value = reviewing.copy(
            confirmation = RosterOcrReviewConfirmationState.NotRequested,
            localReplacement = RosterOcrLocalReplacementState.InProgress,
        )
        operationJob = viewModelScope.launch {
            val localResult = try {
                replaceConfirmedTournamentRoster(candidate)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (!isCurrent(generation, reviewing.tournamentId)) return@launch

            when (localResult) {
                ReplaceConfirmedTournamentRosterResult.Replaced -> synchronizeCloud(
                    generation = generation,
                    tournamentId = reviewing.tournamentId,
                    teamSlots = reviewing.teamSlots,
                    evidence = reviewing.evidence,
                    draft = reviewing.draft,
                )
                ReplaceConfirmedTournamentRosterResult.TournamentNotFound -> showLocalFailure(
                    reviewing,
                    RosterOcrReviewLocalReplacementError.TOURNAMENT_NOT_FOUND,
                )
                ReplaceConfirmedTournamentRosterResult.InvalidCandidate -> showLocalFailure(
                    reviewing,
                    RosterOcrReviewLocalReplacementError.INVALID_CANDIDATE,
                )
                ReplaceConfirmedTournamentRosterResult.BlockedByExistingMatches -> showLocalFailure(
                    reviewing,
                    RosterOcrReviewLocalReplacementError.BLOCKED_BY_EXISTING_MATCHES,
                )
                null -> showLocalFailure(
                    reviewing,
                    RosterOcrReviewLocalReplacementError.UNEXPECTED_FAILURE,
                )
            }
        }
    }

    private suspend fun synchronizeCloud(
        generation: Long,
        tournamentId: String,
        teamSlots: List<TeamSlot>,
        evidence: com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrEvidence,
        draft: RosterOcrReviewDraft,
    ) {
        if (!isCurrent(generation, tournamentId)) return
        val committed = RosterOcrReviewUiState.LocalReplacementCommitted(
            tournamentId = tournamentId,
            teamSlots = teamSlots,
            evidence = evidence,
            draft = draft,
            cloudSynchronization = RosterOcrCloudSynchronizationState.InProgress,
        )
        _uiState.value = committed
        try {
            val result = cloudReplacementInvoker.invoke(tournamentId)
            if (!isCurrent(generation, tournamentId)) return
            if (result.primaryResult is TournamentRosterCloudReplacementResult.Success) {
                _uiState.value = RosterOcrReviewUiState.Completed(
                    tournamentId = tournamentId,
                    teamSlots = teamSlots,
                    evidence = evidence,
                    draft = draft,
                    cloudResult = result,
                )
            } else {
                _uiState.value = committed.copy(
                    cloudSynchronization = RosterOcrCloudSynchronizationState.Failed(result),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            if (isCurrent(generation, tournamentId)) {
                _uiState.value = committed.copy(
                    cloudSynchronization = RosterOcrCloudSynchronizationState.UnexpectedFailure,
                )
            }
        }
    }

    private fun showLocalFailure(
        reviewing: RosterOcrReviewUiState.Reviewing,
        error: RosterOcrReviewLocalReplacementError,
    ) {
        _uiState.value = reviewing.copy(
            confirmation = RosterOcrReviewConfirmationState.NotRequested,
            localReplacement = RosterOcrLocalReplacementState.Failed(error),
        )
    }

    private fun updateDraft(
        transform: (RosterOcrReviewDraft) -> RosterOcrReviewDraft,
    ) {
        val reviewing = _uiState.value as? RosterOcrReviewUiState.Reviewing ?: return
        if (reviewing.localReplacement !is RosterOcrLocalReplacementState.Ready &&
            reviewing.localReplacement !is RosterOcrLocalReplacementState.Failed
        ) return
        _uiState.value = reviewing.copy(
            draft = transform(reviewing.draft),
            confirmation = RosterOcrReviewConfirmationState.NotRequested,
            localReplacement = RosterOcrLocalReplacementState.Ready,
        )
    }

    private fun isCurrent(generation: Long, tournamentId: String): Boolean =
        generation == contextGeneration && loadedTournamentId == tournamentId

    private fun isCompleteTeamContext(
        tournamentId: String,
        slots: List<TeamSlot>,
    ): Boolean = slots.size == TeamSlot.SLOT_NUMBERS.count() &&
        slots.map { it.slotNumber }.toSet() == TeamSlot.SLOT_NUMBERS.toSet() &&
        slots.all { it.tournamentId == tournamentId }
}
