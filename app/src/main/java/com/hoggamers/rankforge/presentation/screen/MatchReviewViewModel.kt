package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.MatchResultRowInput
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
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
class MatchReviewViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val validateMatchResult: ValidateMatchResultUseCase,
    private val finalizeMatch: FinalizeMatchUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchReviewUiState())
    val uiState: StateFlow<MatchReviewUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedMatchKey: String? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        _uiState.update {
            MatchReviewUiState(
                isLoading = true,
                tournamentId = tournamentId,
                matchId = matchId,
            )
        }
        loadJob = viewModelScope.launch {
            combine(
                observeMatches(tournamentId),
                observeTournamentSlots(tournamentId),
                observeRoster(tournamentId),
                observeDraftValues(tournamentId, matchId),
            ) { matches, slots, rosters, draftValues ->
                val match = matches.firstOrNull { it.id == matchId }
                if (match == null) {
                    MatchReviewUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                } else {
                    val fallbackSlots = TeamSlot.fixedSlotsForTournament(tournamentId)
                        .associateBy { it.slotNumber }
                    val slotsByNumber = slots.associateBy { it.slotNumber }
                    val placementsBySlot = match.placements.associateBy { it.teamSlotNumber }
                    val killsBySlot = match.kills.associateBy { it.teamSlotNumber }
                    val rows = TeamSlot.SLOT_NUMBERS.map { teamSlotNumber ->
                        val slot = slotsByNumber[teamSlotNumber] ?: fallbackSlots.getValue(teamSlotNumber)
                        val draft = draftValues[teamSlotNumber]
                            .takeIf { match.status == MatchStatus.DRAFT }
                        MatchReviewRowUiState(
                            teamSlotNumber = teamSlotNumber,
                            teamName = slot.teamName,
                            playerNames = rosters[teamSlotNumber].orEmpty().map { it.displayName },
                            placementInput = draft?.placementInput
                                ?: placementsBySlot[teamSlotNumber]?.position?.toString().orEmpty(),
                            killsInput = draft?.killsInput
                                ?: killsBySlot[teamSlotNumber]?.kills?.toString().orEmpty(),
                        )
                    }
                    val validation = if (match.status == MatchStatus.FINALIZED || draftValues.isEmpty()) {
                        validateMatchResult(match)
                    } else {
                        validateMatchResult(
                            rows.map { row ->
                                MatchResultRowInput(
                                    teamSlotNumber = row.teamSlotNumber,
                                    placement = row.placementInput,
                                    kills = row.killsInput,
                                )
                            },
                        )
                    }
                    MatchReviewUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        status = match.status,
                        correctionHistory = match.correctionHistory,
                        rows = rows.map { row ->
                            row.copy(validationErrors = validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty())
                        },
                        validationErrors = validation.errorsByTeamSlot,
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        navigation = current.navigation,
                        isFinalizing = current.isFinalizing,
                        finalizationError = current.finalizationError,
                        selectedScreenshotUri = current.selectedScreenshotUri,
                        isPhotoPickerLaunchPending = current.isPhotoPickerLaunchPending,
                        isPhotoPickerRequestActive = current.isPhotoPickerRequestActive,
                        photoPickerError = current.photoPickerError,
                    )
                }
            }
        }
    }

    fun openPlacements() {
        if (_uiState.value.isEditable) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.PLACEMENTS) }
        }
    }

    fun openKills() {
        if (_uiState.value.isEditable) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.KILLS) }
        }
    }

    fun openCorrection() {
        if (_uiState.value.status == MatchStatus.FINALIZED) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.CORRECTION) }
        }
    }

    fun onBackToDetails() {
        if (_uiState.value.isAvailable) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.DETAILS) }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    fun requestPhotoPicker() {
        val current = _uiState.value
        if (!current.isAvailable || current.isPhotoPickerRequestActive) return
        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = true,
                isPhotoPickerRequestActive = true,
                photoPickerError = null,
            )
        }
    }

    fun onPhotoPickerLaunchHandled() {
        _uiState.update { it.copy(isPhotoPickerLaunchPending = false) }
    }

    fun onPhotoPickerResult(selectedUri: String?) {
        _uiState.update { current ->
            when {
                selectedUri == null -> current.copy(
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                )
                selectedUri.isBlank() -> current.copy(
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                    photoPickerError = PhotoPickerError.INVALID_RESULT,
                )
                else -> current.copy(
                    selectedScreenshotUri = selectedUri,
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                    photoPickerError = null,
                )
            }
        }
    }

    fun onPhotoPickerLaunchFailed() {
        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = false,
                isPhotoPickerRequestActive = false,
                photoPickerError = PhotoPickerError.LAUNCH_FAILED,
            )
        }
    }

    fun finalize() {
        val current = _uiState.value
        val matchId = current.matchId ?: return
        if (!current.isEditable || !current.isValid || current.isFinalizing) return
        _uiState.update { it.copy(isFinalizing = true, finalizationError = null) }
        viewModelScope.launch {
            when (
                val result = finalizeMatch(
                    FinalizeMatchInput(
                        matchId = matchId,
                        rows = current.rows.map { row ->
                            MatchResultRowInput(
                                teamSlotNumber = row.teamSlotNumber,
                                placement = row.placementInput,
                                kills = row.killsInput,
                            )
                        },
                    ),
                )
            ) {
                is FinalizeMatchResult.Finalized -> _uiState.update {
                    it.copy(isFinalizing = false, finalizationError = null)
                }
                is FinalizeMatchResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        isFinalizing = false,
                        validationErrors = result.validation.errorsByTeamSlot,
                        rows = state.rows.map { row ->
                            row.copy(
                                validationErrors = result.validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty(),
                            )
                        },
                        finalizationError = result.globalError,
                    )
                }
            }
        }
    }
}
