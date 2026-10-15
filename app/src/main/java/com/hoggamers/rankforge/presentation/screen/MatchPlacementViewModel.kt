package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.PlacementGlobalError
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchInput
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchResult
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ReadMatchTeamIdentityContextUseCase
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContextReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftMutationResult
import com.hoggamers.rankforge.domain.tournament.ObserveGroupRotationMatchDraftUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.ClearGroupRotationMatchDraftUseCase
import com.hoggamers.rankforge.domain.tournament.finalizedHistoricalTeamSlotNumbers
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class MatchPlacementViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val saveMatchPlacements: SaveMatchPlacementsUseCase,
    private val saveDraftValue: SaveMatchDraftValueUseCase,
    private val clearDraftMatch: ClearDraftMatchUseCase,
    private val readMatchTeamIdentityContext: ReadMatchTeamIdentityContextUseCase =
        ReadMatchTeamIdentityContextUseCase(),
    private val observeGroupRotationMatchDraft: ObserveGroupRotationMatchDraftUseCase? = null,
    private val saveGroupRotationMatchDraftValue: SaveGroupRotationMatchDraftValueUseCase? = null,
    private val saveGroupRotationMatchPlacements: SaveGroupRotationMatchPlacementsUseCase? = null,
    private val clearGroupRotationMatchDraft: ClearGroupRotationMatchDraftUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchPlacementUiState())
    val uiState: StateFlow<MatchPlacementUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedMatchKey: String? = null
    private val draftWriteMutex = Mutex()
    private var draftWriteJob: Job? = null
    private var draftWriteError: PlacementGlobalError? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        _uiState.update {
            MatchPlacementUiState(
                isLoading = true,
                tournamentId = tournamentId,
                matchId = matchId,
            )
        }
        loadJob = viewModelScope.launch {
            val groupDraft = observeGroupRotationMatchDraft?.invoke(tournamentId, matchId)
                ?: kotlinx.coroutines.flow.flowOf(null)
            combine(
                observeMatches(tournamentId),
                observeTournamentSlots(tournamentId),
                observeRoster(tournamentId),
                observeDraftValues(tournamentId, matchId),
                groupDraft,
            ) { matches, slots, rosters, draftValues, groupDraftResult ->
                val match = matches.firstOrNull { it.id == matchId }
                if (match == null) {
                    MatchPlacementUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                } else {
                    val uuidDraft = groupDraftResult as? GroupRotationMatchDraftReadResult.Loaded
                    if (uuidDraft != null) {
                        val projection = GroupRotationMatchPresentationProjection.project(uuidDraft.state)
                        val loadedProjection = projection as? GroupRotationMatchPresentationProjectionResult.Loaded
                        if (loadedProjection == null) {
                            return@combine MatchPlacementUiState(
                                isLoading = false,
                                isAvailable = false,
                                tournamentId = tournamentId,
                                matchId = matchId,
                            )
                        }
                        return@combine MatchPlacementUiState(
                            isLoading = false,
                            isAvailable = true,
                            tournamentId = tournamentId,
                            matchId = matchId,
                            matchNumber = match.matchNumber,
                            isReadOnly = match.status == MatchStatus.FINALIZED,
                            isUuidBackedGroupRotation = true,
                            rows = loadedProjection.rows.map { team ->
                                    MatchPlacementRowUiState(
                                        teamSlotNumber = team.lobbySlotNumber,
                                        teamName = team.displayName,
                                        placementInput = team.placementInput,
                                        lobbySlotNumber = team.lobbySlotNumber,
                                    )
                                },
                        )
                    }
                    if (match.groupPairing != null &&
                        groupDraftResult != null &&
                        groupDraftResult !is GroupRotationMatchDraftReadResult.NotUuidBacked
                    ) {
                        return@combine MatchPlacementUiState(
                            isLoading = false,
                            isAvailable = false,
                            tournamentId = tournamentId,
                            matchId = matchId,
                        )
                    }
                    val identityContext = (readMatchTeamIdentityContext.forMatch(match.id)
                        as? MatchTeamIdentityContextReadResult.Loaded)?.context
                    val isGroupRotationMatch = match.groupPairing != null ||
                        slots.any { it.slotNumber !in TeamSlot.SLOT_NUMBERS }
                    val isLegacyFinalizedGroupRotation = isGroupRotationMatch &&
                        match.status == MatchStatus.FINALIZED &&
                        identityContext == null
                    if (isGroupRotationMatch && identityContext == null &&
                        !isLegacyFinalizedGroupRotation
                    ) {
                        return@combine MatchPlacementUiState(
                            isLoading = false,
                            isAvailable = false,
                            tournamentId = tournamentId,
                            matchId = matchId,
                        )
                    }
                    val identities = identityContext?.orderedTeams
                        ?: if (isLegacyFinalizedGroupRotation) emptyList() else slots.sortedBy { it.slotNumber }.map { slot ->
                            com.hoggamers.rankforge.domain.tournament.MatchLobbyTeamIdentity(
                                lobbySlotNumber = slot.slotNumber,
                                teamSlotNumber = slot.slotNumber,
                            )
                        }
                    val canonicalTeamSlotNumbers = if (isLegacyFinalizedGroupRotation) {
                        match.finalizedHistoricalTeamSlotNumbers()
                    } else {
                        identities.map { it.teamSlotNumber }
                    }
                    val isReadOnly = match.status == MatchStatus.FINALIZED
                    val savedPlacements = match.placements.associateBy { it.teamSlotNumber }
                    MatchPlacementUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        isReadOnly = isReadOnly,
                        rows = canonicalTeamSlotNumbers.mapNotNull { teamSlotNumber ->
                            val slot = slots.firstOrNull { it.slotNumber == teamSlotNumber }
                                ?: return@mapNotNull null
                            MatchPlacementRowUiState(
                                teamSlotNumber = slot.slotNumber,
                                teamName = slot.teamName,
                                placementInput = if (isReadOnly) {
                                    savedPlacements[slot.slotNumber]?.position?.toString().orEmpty()
                                } else {
                                    draftValues[slot.slotNumber]?.placementInput
                                        ?: savedPlacements[slot.slotNumber]?.position?.toString().orEmpty()
                                },
                                playerNames = rosters[slot.slotNumber].orEmpty().map { it.displayName },
                                lobbySlotNumber = identities.firstOrNull {
                                    it.teamSlotNumber == teamSlotNumber
                                }?.lobbySlotNumber,
                            )
                        },
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        rows = state.rows.map { row ->
                            row.copy(
                                placementInput = current.rows
                                    .firstOrNull { it.teamSlotNumber == row.teamSlotNumber }
                                    ?.placementInput
                                    ?: row.placementInput,
                            )
                        },
                        validationErrors = current.validationErrors,
                        globalError = current.globalError,
                        isSubmitting = current.isSubmitting,
                        navigation = current.navigation,
                    )
                }
            }
        }
    }

    fun onPlacementChanged(teamSlotNumber: Int, value: String) {
        val currentState = _uiState.value
        if (currentState.isReadOnly) return
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.teamSlotNumber == teamSlotNumber) row.copy(placementInput = value) else row
                },
                validationErrors = state.validationErrors - teamSlotNumber,
                globalError = null,
            )
        }
        val tournamentId = currentState.tournamentId ?: return
        val matchId = currentState.matchId ?: return
        val input = SaveMatchDraftValueInput(
            tournamentId = tournamentId,
            matchId = matchId,
            teamSlotNumber = teamSlotNumber,
            placementInput = value,
        )
        if (currentState.isUuidBackedGroupRotation) {
            enqueueGroupDraftWrite(input)
        } else {
            enqueueDraftWrite(input)
        }
    }

    fun save() {
        val currentState = _uiState.value
        val tournamentId = currentState.tournamentId ?: return
        val matchId = currentState.matchId ?: return
        if (!currentState.canSave) return
        _uiState.update { it.copy(isSubmitting = true, globalError = null) }
        viewModelScope.launch {
            draftWriteError = null
            currentState.rows.forEach { row ->
                val input = SaveMatchDraftValueInput(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        teamSlotNumber = row.teamSlotNumber,
                        placementInput = row.placementInput,
                    )
                if (currentState.isUuidBackedGroupRotation) {
                    enqueueGroupDraftWrite(input)
                } else {
                    enqueueDraftWrite(input)
                }
            }
            draftWriteJob?.join()
            draftWriteError?.let { error ->
                _uiState.update { it.copy(isSubmitting = false, globalError = error) }
                return@launch
            }
            if (currentState.isUuidBackedGroupRotation) {
                val groupResult = saveGroupRotationMatchPlacements?.invoke(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    placementsByLobbySlot = currentState.rows.associate { row ->
                        row.teamSlotNumber to row.placementInput
                    },
                ) ?: GroupRotationMatchDraftMutationResult.INVALID_DATA
                when (groupResult) {
                    GroupRotationMatchDraftMutationResult.SAVED -> _uiState.update {
                        it.copy(isSubmitting = false, navigation = MatchPlacementNavigation.Saved(tournamentId, matchId))
                    }
                    else -> _uiState.update {
                        it.copy(isSubmitting = false, globalError = groupResult.toPlacementError())
                    }
                }
                return@launch
            }
            when (
                val result = saveMatchPlacements(
                    SaveMatchPlacementsInput(
                        matchId = matchId,
                        placementsByTeamSlot = currentState.rows.associate { row ->
                            row.teamSlotNumber to row.placementInput
                        },
                    ),
                )
            ) {
                is SaveMatchPlacementsResult.Saved -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        navigation = MatchPlacementNavigation.Saved(tournamentId, matchId),
                    )
                }
                is SaveMatchPlacementsResult.Invalid -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        validationErrors = result.errorsByTeamSlot,
                        globalError = result.globalError,
                    )
                }
            }
        }
    }

    fun resetDraft() {
        if (_uiState.value.isReadOnly) return
        val tournamentId = _uiState.value.tournamentId ?: return
        val matchId = _uiState.value.matchId ?: return
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { it.copy(placementInput = "") },
                validationErrors = emptyMap(),
                globalError = null,
            )
        }
        viewModelScope.launch {
            draftWriteJob?.join()
            if (_uiState.value.isUuidBackedGroupRotation) {
                when (clearGroupRotationMatchDraft?.invoke(tournamentId, matchId)) {
                    GroupRotationMatchDraftMutationResult.SAVED -> Unit
                    GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED -> _uiState.update {
                        it.copy(globalError = PlacementGlobalError.AUTHENTICATION_REQUIRED)
                    }
                    GroupRotationMatchDraftMutationResult.MATCH_NOT_FOUND -> _uiState.update {
                        it.copy(globalError = PlacementGlobalError.MATCH_NOT_FOUND)
                    }
                    GroupRotationMatchDraftMutationResult.INVALID_DATA -> _uiState.update {
                        it.copy(globalError = PlacementGlobalError.INVALID_DATA)
                    }
                    null -> _uiState.update { it.copy(globalError = PlacementGlobalError.INVALID_DATA) }
                }
                return@launch
            }
            when (clearDraftMatch(ClearDraftMatchInput(tournamentId, matchId))) {
                ClearDraftMatchResult.Cleared -> Unit
                ClearDraftMatchResult.AuthenticationRequired -> _uiState.update {
                    it.copy(globalError = PlacementGlobalError.AUTHENTICATION_REQUIRED)
                }
                ClearDraftMatchResult.MatchNotFound -> _uiState.update {
                    it.copy(globalError = PlacementGlobalError.MATCH_NOT_FOUND)
                }
            }
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            draftWriteJob?.join()
            if (!_uiState.value.isSubmitting) {
                _uiState.update { it.copy(navigation = MatchPlacementNavigation.Back) }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    private fun enqueueDraftWrite(input: SaveMatchDraftValueInput) {
        val previousWrite = draftWriteJob
        draftWriteJob = viewModelScope.launch {
            previousWrite?.join()
            draftWriteMutex.withLock {
                draftWriteError = when (saveDraftValue(input)) {
                    SaveMatchDraftValueResult.Saved -> draftWriteError
                    SaveMatchDraftValueResult.AuthenticationRequired -> PlacementGlobalError.AUTHENTICATION_REQUIRED
                    SaveMatchDraftValueResult.MatchNotFound -> PlacementGlobalError.MATCH_NOT_FOUND
                    SaveMatchDraftValueResult.InvalidData -> PlacementGlobalError.INVALID_DATA
                }
            }
        }
    }

    private fun enqueueGroupDraftWrite(input: SaveMatchDraftValueInput) {
        val previousWrite = draftWriteJob
        draftWriteJob = viewModelScope.launch {
            previousWrite?.join()
            draftWriteMutex.withLock {
                val result = saveGroupRotationMatchDraftValue?.invoke(input)
                    ?: GroupRotationMatchDraftMutationResult.INVALID_DATA
                draftWriteError = when (result) {
                    GroupRotationMatchDraftMutationResult.SAVED -> draftWriteError
                    GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED ->
                        PlacementGlobalError.AUTHENTICATION_REQUIRED
                    GroupRotationMatchDraftMutationResult.MATCH_NOT_FOUND -> PlacementGlobalError.MATCH_NOT_FOUND
                    GroupRotationMatchDraftMutationResult.INVALID_DATA -> PlacementGlobalError.INVALID_DATA
                }
            }
        }
    }

    private fun GroupRotationMatchDraftMutationResult.toPlacementError(): PlacementGlobalError = when (this) {
        GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED -> PlacementGlobalError.AUTHENTICATION_REQUIRED
        GroupRotationMatchDraftMutationResult.MATCH_NOT_FOUND -> PlacementGlobalError.MATCH_NOT_FOUND
        GroupRotationMatchDraftMutationResult.INVALID_DATA -> PlacementGlobalError.INVALID_DATA
        GroupRotationMatchDraftMutationResult.SAVED -> error("Successful Group Rotation write has no UI error")
    }
}
