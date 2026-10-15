package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.KillGlobalError
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
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsInput
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.ReadMatchTeamIdentityContextUseCase
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContextReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftMutationResult
import com.hoggamers.rankforge.domain.tournament.ObserveGroupRotationMatchDraftUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.ClearGroupRotationMatchDraftUseCase
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
class MatchKillViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val saveMatchKills: SaveMatchKillsUseCase,
    private val saveDraftValue: SaveMatchDraftValueUseCase,
    private val clearDraftMatch: ClearDraftMatchUseCase,
    private val readMatchTeamIdentityContext: ReadMatchTeamIdentityContextUseCase =
        ReadMatchTeamIdentityContextUseCase(),
    private val observeGroupRotationMatchDraft: ObserveGroupRotationMatchDraftUseCase? = null,
    private val saveGroupRotationMatchDraftValue: SaveGroupRotationMatchDraftValueUseCase? = null,
    private val saveGroupRotationMatchKills: SaveGroupRotationMatchKillsUseCase? = null,
    private val clearGroupRotationMatchDraft: ClearGroupRotationMatchDraftUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchKillUiState())
    val uiState: StateFlow<MatchKillUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedMatchKey: String? = null
    private val draftWriteMutex = Mutex()
    private var draftWriteJob: Job? = null
    private var draftWriteError: KillGlobalError? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        _uiState.update {
            MatchKillUiState(
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
                if (match == null || match.status != MatchStatus.DRAFT) {
                    MatchKillUiState(
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
                            return@combine MatchKillUiState(
                                isLoading = false,
                                isAvailable = false,
                                tournamentId = tournamentId,
                                matchId = matchId,
                            )
                        }
                        return@combine MatchKillUiState(
                            isLoading = false,
                            isAvailable = true,
                            tournamentId = tournamentId,
                            matchId = matchId,
                            matchNumber = match.matchNumber,
                            isUuidBackedGroupRotation = true,
                            rows = loadedProjection.rows.map { team ->
                                    MatchKillRowUiState(
                                        teamSlotNumber = team.lobbySlotNumber,
                                        teamName = team.displayName,
                                        killsInput = team.killsInput,
                                        lobbySlotNumber = team.lobbySlotNumber,
                                    )
                                },
                        )
                    }
                    if (match.groupPairing != null &&
                        groupDraftResult != null &&
                        groupDraftResult !is GroupRotationMatchDraftReadResult.NotUuidBacked
                    ) {
                        return@combine MatchKillUiState(
                            isLoading = false,
                            isAvailable = false,
                            tournamentId = tournamentId,
                            matchId = matchId,
                        )
                    }
                    val identityContext = (readMatchTeamIdentityContext.forMatch(match.id)
                        as? MatchTeamIdentityContextReadResult.Loaded)?.context
                    if ((match.groupPairing != null || slots.any { it.slotNumber !in TeamSlot.SLOT_NUMBERS }) &&
                        identityContext == null
                    ) {
                        return@combine MatchKillUiState(
                            isLoading = false,
                            isAvailable = false,
                            tournamentId = tournamentId,
                            matchId = matchId,
                        )
                    }
                    val identities = identityContext?.orderedTeams
                        ?: slots.sortedBy { it.slotNumber }.map { slot ->
                            com.hoggamers.rankforge.domain.tournament.MatchLobbyTeamIdentity(
                                lobbySlotNumber = slot.slotNumber,
                                teamSlotNumber = slot.slotNumber,
                            )
                        }
                    val savedKills = match.kills.associateBy { it.teamSlotNumber }
                    MatchKillUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        rows = identities.mapNotNull { identity ->
                            val slot = slots.firstOrNull { it.slotNumber == identity.teamSlotNumber }
                                ?: return@mapNotNull null
                            MatchKillRowUiState(
                                teamSlotNumber = slot.slotNumber,
                                teamName = slot.teamName,
                                killsInput = draftValues[slot.slotNumber]?.killsInput
                                    ?: savedKills[slot.slotNumber]?.kills?.toString().orEmpty(),
                                playerNames = rosters[slot.slotNumber].orEmpty().map { it.displayName },
                                lobbySlotNumber = identity.lobbySlotNumber,
                            )
                        },
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        rows = state.rows.map { row ->
                            row.copy(
                                killsInput = current.rows
                                    .firstOrNull { it.teamSlotNumber == row.teamSlotNumber }
                                    ?.killsInput
                                    ?: row.killsInput,
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

    fun onKillsChanged(teamSlotNumber: Int, value: String) {
        val currentState = _uiState.value
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.teamSlotNumber == teamSlotNumber) row.copy(killsInput = value) else row
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
            killsInput = value,
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
                        killsInput = row.killsInput,
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
                val groupResult = saveGroupRotationMatchKills?.invoke(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    killsByLobbySlot = currentState.rows.associate { row ->
                        row.teamSlotNumber to row.killsInput
                    },
                ) ?: GroupRotationMatchDraftMutationResult.INVALID_DATA
                when (groupResult) {
                    GroupRotationMatchDraftMutationResult.SAVED -> _uiState.update {
                        it.copy(isSubmitting = false, navigation = MatchKillNavigation.Saved(tournamentId, matchId))
                    }
                    else -> _uiState.update {
                        it.copy(isSubmitting = false, globalError = groupResult.toKillError())
                    }
                }
                return@launch
            }
            when (
                val result = saveMatchKills(
                    SaveMatchKillsInput(
                        matchId = matchId,
                        killsByTeamSlot = currentState.rows.associate { row ->
                            row.teamSlotNumber to row.killsInput
                        },
                    ),
                )
            ) {
                is SaveMatchKillsResult.Saved -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        navigation = MatchKillNavigation.Saved(tournamentId, matchId),
                    )
                }
                is SaveMatchKillsResult.Invalid -> _uiState.update {
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
        val tournamentId = _uiState.value.tournamentId ?: return
        val matchId = _uiState.value.matchId ?: return
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { it.copy(killsInput = "") },
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
                        it.copy(globalError = KillGlobalError.AUTHENTICATION_REQUIRED)
                    }
                    GroupRotationMatchDraftMutationResult.MATCH_NOT_FOUND -> _uiState.update {
                        it.copy(globalError = KillGlobalError.MATCH_NOT_FOUND)
                    }
                    GroupRotationMatchDraftMutationResult.INVALID_DATA -> _uiState.update {
                        it.copy(globalError = KillGlobalError.INVALID_DATA)
                    }
                    null -> _uiState.update { it.copy(globalError = KillGlobalError.INVALID_DATA) }
                }
                return@launch
            }
            when (clearDraftMatch(ClearDraftMatchInput(tournamentId, matchId))) {
                ClearDraftMatchResult.Cleared -> Unit
                ClearDraftMatchResult.AuthenticationRequired -> _uiState.update {
                    it.copy(globalError = KillGlobalError.AUTHENTICATION_REQUIRED)
                }
                ClearDraftMatchResult.MatchNotFound -> _uiState.update {
                    it.copy(globalError = KillGlobalError.MATCH_NOT_FOUND)
                }
            }
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            draftWriteJob?.join()
            if (!_uiState.value.isSubmitting) {
                _uiState.update { it.copy(navigation = MatchKillNavigation.Back) }
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
                    SaveMatchDraftValueResult.AuthenticationRequired -> KillGlobalError.AUTHENTICATION_REQUIRED
                    SaveMatchDraftValueResult.MatchNotFound -> KillGlobalError.MATCH_NOT_FOUND
                    SaveMatchDraftValueResult.InvalidData -> KillGlobalError.INVALID_DATA
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
                    GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED -> KillGlobalError.AUTHENTICATION_REQUIRED
                    GroupRotationMatchDraftMutationResult.MATCH_NOT_FOUND -> KillGlobalError.MATCH_NOT_FOUND
                    GroupRotationMatchDraftMutationResult.INVALID_DATA -> KillGlobalError.INVALID_DATA
                }
            }
        }
    }

    private fun GroupRotationMatchDraftMutationResult.toKillError(): KillGlobalError = when (this) {
        GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED -> KillGlobalError.AUTHENTICATION_REQUIRED
        GroupRotationMatchDraftMutationResult.MATCH_NOT_FOUND -> KillGlobalError.MATCH_NOT_FOUND
        GroupRotationMatchDraftMutationResult.INVALID_DATA -> KillGlobalError.INVALID_DATA
        GroupRotationMatchDraftMutationResult.SAVED -> error("Successful Group Rotation write has no UI error")
    }
}
