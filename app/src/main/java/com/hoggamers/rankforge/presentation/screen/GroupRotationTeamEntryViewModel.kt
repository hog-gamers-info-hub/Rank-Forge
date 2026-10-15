package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentitySetupSaveResult
import com.hoggamers.rankforge.domain.tournament.toSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupValidator
import com.hoggamers.rankforge.domain.tournament.ReadGroupRotationTeamSetupResult
import com.hoggamers.rankforge.domain.tournament.ReadGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupResult
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamIdentitySetupUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadStage
import com.hoggamers.rankforge.domain.tournament.orderedGroupRotationPairings
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GroupRotationTeamEntryNavigationEvent {
    data object BackToTournamentDetails : GroupRotationTeamEntryNavigationEvent
}

@HiltViewModel
class GroupRotationTeamEntryViewModel @Inject constructor(
    private val readGroupRotationTeamSetup: ReadGroupRotationTeamSetupUseCase,
    private val saveGroupRotationTeamSetup: SaveGroupRotationTeamSetupUseCase,
    private val saveGroupRotationTeamIdentitySetup: SaveGroupRotationTeamIdentitySetupUseCase,
    private val draftRepository: GroupRotationTeamSetupDraftRepository,
    private val uploadTournament: TournamentCloudUploadAction,
) : ViewModel() {
    private sealed interface DraftWriteCommand {
        data class Save(
            val tournament: Tournament,
            val candidate: GroupRotationTeamSetupCandidate,
        ) : DraftWriteCommand

        data class ClearIfUnchanged(
            val tournamentId: String,
            val pairing: GroupPairing,
            val expectedLoadGeneration: Long,
            val expectedEditGeneration: Long,
            val completion: CompletableDeferred<Unit>,
        ) : DraftWriteCommand

        data class Barrier(val completion: CompletableDeferred<Unit>) : DraftWriteCommand
    }

    private val _uiState = MutableStateFlow(GroupRotationTeamEntryUiState())
    val uiState: StateFlow<GroupRotationTeamEntryUiState> = _uiState.asStateFlow()
    private val navigationEventsChannel = Channel<GroupRotationTeamEntryNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<GroupRotationTeamEntryNavigationEvent> = navigationEventsChannel.receiveAsFlow()
    private val draftWriteChannel = Channel<DraftWriteCommand>(Channel.UNLIMITED)
    private val validator = GroupRotationTeamSetupValidator()
    private val locallyEditedKeys = mutableSetOf<Pair<String, Int>>()
    private var loadJob: Job? = null
    private var loadedTournament: Tournament? = null
    private var loadedTournamentId: String? = null
    private var loadGeneration = 0L
    private var editGeneration = 0L

    init {
        viewModelScope.launch {
            for (command in draftWriteChannel) {
                when (command) {
                    is DraftWriteCommand.Save -> {
                        try {
                            require(command.tournament.id == command.candidate.tournamentId)
                            draftRepository.replaceDraft(command.tournament, command.candidate)
                        } catch (throwable: Throwable) {
                            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                        }
                    }

                    is DraftWriteCommand.ClearIfUnchanged -> {
                        try {
                            if (
                                loadedTournamentId == command.tournamentId &&
                                loadGeneration == command.expectedLoadGeneration &&
                                editGeneration == command.expectedEditGeneration
                            ) {
                                draftRepository.clearDraftForPairing(command.tournamentId, command.pairing)
                            }
                        } catch (throwable: Throwable) {
                            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                        } finally {
                            command.completion.complete(Unit)
                        }
                    }

                    is DraftWriteCommand.Barrier -> command.completion.complete(Unit)
                }
            }
        }
    }

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId && loadJob?.isActive == true) return
        loadGeneration += 1
        loadedTournamentId = tournamentId
        loadedTournament = null
        editGeneration = 0L
        locallyEditedKeys.clear()
        loadJob?.cancel()
        _uiState.value = GroupRotationTeamEntryUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                when (val result = readGroupRotationTeamSetup(tournamentId)) {
                    is ReadGroupRotationTeamSetupResult.Loaded ->
                        hydrate(result.tournament, result.candidate, result.identityAuthoritative)
                    is ReadGroupRotationTeamSetupResult.NoSavedSetup ->
                        hydrate(result.tournament, null, false)
                    ReadGroupRotationTeamSetupResult.AuthenticationRequired ->
                        showLoadError(GroupRotationTeamEntryLoadError.AuthenticationRequired)
                    ReadGroupRotationTeamSetupResult.TournamentNotFound ->
                        showLoadError(GroupRotationTeamEntryLoadError.TournamentNotFound)
                    ReadGroupRotationTeamSetupResult.InvalidStoredSetup ->
                        showLoadError(GroupRotationTeamEntryLoadError.InvalidStoredSetup)
                }
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                showLoadError(GroupRotationTeamEntryLoadError.InvalidStoredSetup)
            }
        }
    }

    fun onPairingSelected(pairing: GroupPairing) {
        if (_uiState.value.pairingSections.any { it.pairing == pairing }) {
            _uiState.update { it.copy(selectedPairingKey = pairing.canonicalKey) }
        }
    }

    fun onTeamNameChanged(
        pairing: GroupPairing,
        lobbySlotNumber: Int,
        teamName: String,
    ) {
        val current = _uiState.value
        if (current.pairingSections.none { it.pairing == pairing }) return
        locallyEditedKeys += pairing.canonicalKey to lobbySlotNumber
        editGeneration += 1
        val updated = current.copy(
            pairingSections = current.pairingSections.map { section ->
                if (section.pairing != pairing) {
                    section
                } else {
                    section.copy(
                        rows = section.rows.map { row ->
                            if (row.lobbySlotNumber == lobbySlotNumber) row.copy(teamName = teamName) else row
                        },
                    )
                }
            },
            selectedPairingKey = pairing.canonicalKey,
            validationIssues = emptyList(),
            saveError = null,
            cloudSyncError = null,
        )
        _uiState.value = updated.copy(uniqueTeamCount = updated.uniqueNormalizedTeamCount())
        enqueueDraftSave(pairing)
    }

    fun onBulkTeamNamesApplied(teamNames: List<String>) {
        val current = _uiState.value
        val selectedPairing = current.selectedPairing ?: return
        if (teamNames.isEmpty() || teamNames.size > GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER) return
        locallyEditedKeys += GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS.map { lobbySlotNumber ->
            selectedPairing.pairing.canonicalKey to lobbySlotNumber
        }
        editGeneration += 1
        val updated = current.copy(
            pairingSections = current.pairingSections.map { section ->
                if (section.pairing != selectedPairing.pairing) {
                    section
                } else {
                    section.copy(
                        rows = section.rows.map { row ->
                            row.copy(teamName = teamNames.getOrNull(row.lobbySlotNumber - 1).orEmpty())
                        },
                    )
                }
            },
            validationIssues = emptyList(),
            saveError = null,
            cloudSyncError = null,
        )
        _uiState.value = updated.copy(uniqueTeamCount = updated.uniqueNormalizedTeamCount())
        enqueueDraftSave(selectedPairing.pairing)
    }

    fun saveTeamNames() {
        if (_uiState.value.isSaving) return
        val tournament = loadedTournament ?: return
        val selectedPairing = _uiState.value.selectedPairing ?: return
        val pairingsToSave = pairingsToSave(tournament, selectedPairing.pairing)
        if (pairingsToSave.isEmpty()) return
        val saveTournamentId = tournament.id
        val saveLoadGeneration = loadGeneration
        val saveEditGeneration = editGeneration
        _uiState.update {
            it.copy(
                isSaving = true,
                validationIssues = emptyList(),
                saveError = null,
                cloudSyncError = null,
            )
        }
        viewModelScope.launch {
            try {
                awaitDraftWrites()
                if (finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                    return@launch
                }
                if (tournament.format == com.hoggamers.rankforge.domain.tournament.TournamentFormat.GROUP_ROTATION) {
                    when (
                        val result = saveGroupRotationTeamIdentitySetup.savePairings(
                            tournament = tournament,
                            candidatesByPairing = pairingsToSave.associateWith {
                                candidateForSave(tournament, it)
                            },
                        )
                    ) {
                        is GroupRotationTeamIdentitySetupSaveResult.Saved ->
                            finishIdentitySave(
                                tournament = tournament,
                                savedPairings = pairingsToSave,
                                snapshot = result.snapshot,
                                tournamentId = saveTournamentId,
                                expectedLoadGeneration = saveLoadGeneration,
                                expectedEditGeneration = saveEditGeneration,
                            )
                        GroupRotationTeamIdentitySetupSaveResult.AuthenticationRequired -> {
                            if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                                showSaveError(GroupRotationTeamEntrySaveError.AuthenticationRequired)
                            }
                        }
                        GroupRotationTeamIdentitySetupSaveResult.ProtectedHistory -> {
                            if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                                showSaveError(GroupRotationTeamEntrySaveError.ProtectedHistory)
                            }
                        }
                        GroupRotationTeamIdentitySetupSaveResult.OwnershipMismatch,
                        GroupRotationTeamIdentitySetupSaveResult.Unsupported,
                        -> {
                            if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                                showSaveError(GroupRotationTeamEntrySaveError.Unexpected)
                            }
                        }
                        is GroupRotationTeamIdentitySetupSaveResult.InvalidSetup -> {
                            if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                                showValidationIssues(result.issues)
                            }
                        }
                    }
                    return@launch
                }
                val candidate = candidateFromState(tournament, selectedPairing.pairing)
                val validated = when (val validation = validator.validate(tournament, candidate)) {
                    is com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupValidationResult.Invalid -> {
                        if (finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                            return@launch
                        }
                        showValidationIssues(validation.issues)
                        return@launch
                    }
                    is com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupValidationResult.Valid -> validation
                }
                when (val result = saveGroupRotationTeamSetup(candidate)) {
                    SaveGroupRotationTeamSetupResult.Saved -> {
                        val cleanedNames = validated.entries.associate {
                            (it.pairing.canonicalKey to it.lobbySlotNumber) to it.displayTeamName
                        }
                        val cloudResult: QueueAwareActionResult<TournamentCloudUploadResult> = try {
                            uploadTournament(saveTournamentId)
                        } catch (throwable: Throwable) {
                            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                            QueueAwareActionResult(
                                primaryResult = TournamentCloudUploadResult.PartialFailure(
                                    TournamentCloudUploadStage.TOURNAMENT,
                                ),
                                queueRecordingResult = QueueRecordingResult.PERSISTENCE_FAILED,
                            )
                        }
                        if (finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                            return@launch
                        }
                        if (cloudResult.shouldNavigateAfterLocalSave()) {
                            clearDraftIfUnchanged(
                                tournamentId = saveTournamentId,
                                pairing = selectedPairing.pairing,
                                expectedLoadGeneration = saveLoadGeneration,
                                expectedEditGeneration = saveEditGeneration,
                            )
                            if (finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                                return@launch
                            }
                            val cleanedSections = _uiState.value.pairingSections.map { section ->
                                if (section.pairing != selectedPairing.pairing) section else section.copy(
                                    rows = section.rows.map { row ->
                                        row.copy(
                                            teamName = cleanedNames.getValue(
                                                section.pairing.canonicalKey to row.lobbySlotNumber,
                                            ),
                                        )
                                    },
                                )
                            }
                            _uiState.update { current ->
                                val updated = current.copy(
                                    pairingSections = cleanedSections,
                                    uniqueTeamCount = current.copy(
                                        pairingSections = cleanedSections,
                                    ).uniqueNormalizedTeamCount(),
                                    isSaving = false,
                                    cloudSyncError = null,
                                )
                                updated
                            }
                            navigationEventsChannel.trySend(
                                GroupRotationTeamEntryNavigationEvent.BackToTournamentDetails,
                            )
                        } else {
                            val cleanedSections = _uiState.value.pairingSections.map { section ->
                                if (section.pairing != selectedPairing.pairing) section else section.copy(
                                    rows = section.rows.map { row ->
                                        row.copy(
                                            teamName = cleanedNames.getValue(
                                                section.pairing.canonicalKey to row.lobbySlotNumber,
                                            ),
                                        )
                                    },
                                )
                            }
                            _uiState.update { current ->
                                current.copy(
                                    pairingSections = cleanedSections,
                                    uniqueTeamCount = current.copy(
                                        pairingSections = cleanedSections,
                                    ).uniqueNormalizedTeamCount(),
                                    isSaving = false,
                                    cloudSyncError = cloudResult.toCloudSyncError(),
                                )
                            }
                        }
                    }
                    is SaveGroupRotationTeamSetupResult.InvalidSetup -> {
                        if (finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                            return@launch
                        }
                        showValidationIssues(result.issues)
                    }
                    SaveGroupRotationTeamSetupResult.AuthenticationRequired ->
                        if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                            showSaveError(GroupRotationTeamEntrySaveError.AuthenticationRequired)
                        }
                    SaveGroupRotationTeamSetupResult.TournamentNotFound ->
                        if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                            showSaveError(GroupRotationTeamEntrySaveError.TournamentNotFound)
                        }
                    SaveGroupRotationTeamSetupResult.ProtectedHistory ->
                        if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                            showSaveError(GroupRotationTeamEntrySaveError.ProtectedHistory)
                        }
                }
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                if (!finishStaleSaveIfNeeded(saveTournamentId, saveLoadGeneration, saveEditGeneration)) {
                    showSaveError(GroupRotationTeamEntrySaveError.Unexpected)
                }
            }
        }
    }

    private suspend fun hydrate(
        tournament: Tournament,
        authoritativeCandidate: GroupRotationTeamSetupCandidate?,
        identityAuthoritative: Boolean,
    ) {
        loadedTournament = tournament
        val rawDraft = try {
            draftRepository.readDraft(tournament.id)
        } catch (throwable: Throwable) {
            showLoadError(GroupRotationTeamEntryLoadError.InvalidStoredDraft)
            return
        }
        val draftCandidate = if (rawDraft.isEmpty()) {
            null
        } else {
            GroupRotationTeamSetupCandidate(tournament.id, rawDraft)
        }
        if (draftCandidate != null) {
            val draftIssues = com.hoggamers.rankforge.domain.tournament.validateGroupRotationTeamSetupDraft(
                tournament,
                draftCandidate,
            )
            if (draftIssues.isNotEmpty()) {
                publishInvalidDraftState(tournament)
                return
            }
        }
        val authoritative = authoritativeCandidate ?: blankCandidate(tournament)
        val effectiveDraftEntries = draftCandidate?.entries.orEmpty()
        if (
            tournament.format == com.hoggamers.rankforge.domain.tournament.TournamentFormat.GROUP_ROTATION &&
                identityAuthoritative
        ) {
            val authoritativeEntriesByKey = authoritative.entries.associateBy {
                it.pairing.canonicalKey to it.lobbySlotNumber
            }
            effectiveDraftEntries
                .filter { entry ->
                    entry.teamName.isBlank() &&
                        authoritativeEntriesByKey[entry.pairing.canonicalKey to entry.lobbySlotNumber]
                            ?.teamName
                            ?.isNotBlank() == true
                }
                .forEach { entry ->
                    locallyEditedKeys += entry.pairing.canonicalKey to entry.lobbySlotNumber
                }
        }
        effectiveDraftEntries
            .filter { it.teamName.isNotBlank() }
            .forEach { entry ->
                locallyEditedKeys += entry.pairing.canonicalKey to entry.lobbySlotNumber
            }
        val hydratedCandidate = if (effectiveDraftEntries.isEmpty()) {
            authoritative
        } else {
            val draftedKeys = effectiveDraftEntries
                .map { it.pairing.canonicalKey to it.lobbySlotNumber }
                .toSet()
            GroupRotationTeamSetupCandidate(
                tournamentId = tournament.id,
                entries = authoritative.entries.filterNot {
                    it.pairing.canonicalKey to it.lobbySlotNumber in draftedKeys
                } + effectiveDraftEntries,
            )
        }
        publishLoadedState(tournament, hydratedCandidate)
    }

    private suspend fun finishIdentitySave(
        tournament: Tournament,
        savedPairings: List<GroupPairing>,
        snapshot: GroupRotationTeamIdentityLocalSnapshot,
        tournamentId: String,
        expectedLoadGeneration: Long,
        expectedEditGeneration: Long,
    ) {
        if (finishStaleSaveIfNeeded(tournamentId, expectedLoadGeneration, expectedEditGeneration)) return
        val savedPairingKeys = savedPairings.map { it.canonicalKey }.toSet()
        locallyEditedKeys.removeAll { it.first in savedPairingKeys }
        savedPairings.forEach { pairing ->
            clearDraftIfUnchanged(
                tournamentId = tournamentId,
                pairing = pairing,
                expectedLoadGeneration = expectedLoadGeneration,
                expectedEditGeneration = expectedEditGeneration,
            )
        }
        if (finishStaleSaveIfNeeded(tournamentId, expectedLoadGeneration, expectedEditGeneration)) return
        val persistedSections = sectionsFor(tournament, snapshot.toSetupCandidate(tournament))
        val cloudResult: QueueAwareActionResult<TournamentCloudUploadResult> = try {
            uploadTournament(tournamentId)
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            QueueAwareActionResult(
                primaryResult = TournamentCloudUploadResult.PartialFailure(
                    TournamentCloudUploadStage.TOURNAMENT,
                ),
                queueRecordingResult = QueueRecordingResult.PERSISTENCE_FAILED,
            )
        }
        if (finishStaleSaveIfNeeded(tournamentId, expectedLoadGeneration, expectedEditGeneration)) return
        val navigateAfterLocalSave = cloudResult.shouldNavigateAfterLocalSave()
        _uiState.update { state ->
            state.copy(
                pairingSections = persistedSections,
                uniqueTeamCount = state.copy(pairingSections = persistedSections).uniqueNormalizedTeamCount(),
                isSaving = false,
                validationIssues = emptyList(),
                saveError = null,
                cloudSyncError = if (navigateAfterLocalSave) null else cloudResult.toCloudSyncError(),
            )
        }
        if (navigateAfterLocalSave) {
            navigationEventsChannel.trySend(GroupRotationTeamEntryNavigationEvent.BackToTournamentDetails)
        }
    }

    private fun publishLoadedState(
        tournament: Tournament,
        candidate: GroupRotationTeamSetupCandidate,
        loadError: GroupRotationTeamEntryLoadError? = null,
    ) {
        loadedTournament = tournament
        val sections = sectionsFor(tournament, candidate)
        val keys = sections.map { it.pairing.canonicalKey }
        _uiState.value = GroupRotationTeamEntryUiState(
            isLoading = false,
            tournamentName = tournament.name,
            stageName = tournament.stageName,
            pairingSections = sections,
            selectedPairingKey = keys.firstOrNull(),
            uniqueTeamCount = sections
                .let { GroupRotationTeamEntryUiState(pairingSections = it).uniqueNormalizedTeamCount() },
            maximumUniqueTeams = maximumUniqueTeams(tournament),
            loadError = loadError,
        )
    }

    private fun publishInvalidDraftState(tournament: Tournament) {
        loadedTournament = tournament
        _uiState.value = GroupRotationTeamEntryUiState(
            isLoading = false,
            tournamentName = tournament.name,
            stageName = tournament.stageName,
            maximumUniqueTeams = maximumUniqueTeams(tournament),
            loadError = GroupRotationTeamEntryLoadError.InvalidStoredDraft,
        )
    }

    private fun sectionsFor(
        tournament: Tournament,
        candidate: GroupRotationTeamSetupCandidate,
    ): List<GroupRotationPairingEntryUiState> {
        val entriesByPairingAndSlot = candidate.entries.associateBy {
            it.pairing.canonicalKey to it.lobbySlotNumber
        }
        return orderedGroupRotationPairings(tournament).map { pairing ->
            GroupRotationPairingEntryUiState(
                pairing = pairing,
                rows = GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS.map { lobbySlotNumber ->
                    GroupRotationLobbyTeamUiState(
                        lobbySlotNumber = lobbySlotNumber,
                        teamName = entriesByPairingAndSlot[pairing.canonicalKey to lobbySlotNumber]
                            ?.teamName
                            .orEmpty(),
                    )
                },
            )
        }
    }

    private fun blankCandidate(tournament: Tournament): GroupRotationTeamSetupCandidate =
        GroupRotationTeamSetupCandidate(
            tournamentId = tournament.id,
            entries = orderedGroupRotationPairings(tournament).flatMap { pairing ->
                GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS.map { lobbySlotNumber ->
                    GroupRotationPairingTeamEntry(pairing, lobbySlotNumber, "")
                }
            },
        )

    private fun candidateFromState(
        tournament: Tournament,
        pairing: GroupPairing,
    ): GroupRotationTeamSetupCandidate =
        GroupRotationTeamSetupCandidate(
            tournamentId = tournament.id,
            entries = _uiState.value.pairingSections
                .firstOrNull { it.pairing == pairing }
                ?.rows
                .orEmpty()
                .map { row ->
                    GroupRotationPairingTeamEntry(pairing, row.lobbySlotNumber, row.teamName)
                },
        )

    private fun pairingsToSave(
        tournament: Tournament,
        selectedPairing: GroupPairing,
    ): List<GroupPairing> {
        val editedPairingKeys = locallyEditedKeys.map { it.first }.toSet()
        return orderedGroupRotationPairings(tournament).filter { pairing ->
            pairing == selectedPairing || pairing.canonicalKey in editedPairingKeys
        }
    }

    private fun candidateForSave(
        tournament: Tournament,
        pairing: GroupPairing,
    ): GroupRotationTeamSetupCandidate = candidateFromState(tournament, pairing)

    private fun enqueueDraftSave(pairing: GroupPairing) {
        val tournament = loadedTournament ?: return
        draftWriteChannel.trySend(
            DraftWriteCommand.Save(
                tournament = tournament,
                candidate = candidateFromState(tournament, pairing),
            ),
        )
    }

    private suspend fun awaitDraftWrites() {
        val completion = CompletableDeferred<Unit>()
        draftWriteChannel.send(DraftWriteCommand.Barrier(completion))
        completion.await()
    }

    private suspend fun clearDraftIfUnchanged(
        tournamentId: String,
        pairing: GroupPairing,
        expectedLoadGeneration: Long,
        expectedEditGeneration: Long,
    ) {
        val completion = CompletableDeferred<Unit>()
        draftWriteChannel.send(
            DraftWriteCommand.ClearIfUnchanged(
                tournamentId = tournamentId,
                pairing = pairing,
                expectedLoadGeneration = expectedLoadGeneration,
                expectedEditGeneration = expectedEditGeneration,
                completion = completion,
            ),
        )
        completion.await()
    }

    private fun finishStaleSaveIfNeeded(
        tournamentId: String,
        expectedLoadGeneration: Long,
        expectedEditGeneration: Long,
    ): Boolean {
        if (
            loadedTournamentId == tournamentId &&
            loadGeneration == expectedLoadGeneration &&
            editGeneration == expectedEditGeneration
        ) {
            return false
        }
        if (loadedTournamentId == tournamentId && loadGeneration == expectedLoadGeneration) {
            _uiState.update { it.copy(isSaving = false) }
        }
        return true
    }

    private fun showLoadError(error: GroupRotationTeamEntryLoadError) {
        _uiState.update { it.copy(isLoading = false, loadError = error) }
    }

    private fun showSaveError(error: GroupRotationTeamEntrySaveError) {
        _uiState.update { it.copy(isSaving = false, saveError = error, cloudSyncError = null) }
    }

    private fun showValidationIssues(issues: List<GroupRotationTeamSetupIssue>) {
        _uiState.update {
            it.copy(
                isSaving = false,
                validationIssues = issues,
            )
        }
        val firstPairingKey = orderedGroupRotationPairings(loadedTournament ?: return)
            .map { it.canonicalKey }
            .firstOrNull { key -> issues.any { issue -> issue.pairingKey == key } }
        if (firstPairingKey != null) {
            _uiState.update { it.copy(selectedPairingKey = firstPairingKey) }
        }
    }

    private fun maximumUniqueTeams(tournament: Tournament): Int = when (tournament.groupCount) {
        3 -> 18
        4 -> 24
        else -> 0
    }
}

private fun QueueAwareActionResult<TournamentCloudUploadResult>.shouldNavigateAfterLocalSave(): Boolean =
    primaryResult is TournamentCloudUploadResult.Success ||
        (primaryResult == TournamentCloudUploadResult.NetworkFailure &&
            queueRecordingResult == QueueRecordingResult.RECORDED)

private fun QueueAwareActionResult<TournamentCloudUploadResult>.toCloudSyncError(): GroupRotationTeamEntryCloudSyncError =
    if (queueRecordingResult == QueueRecordingResult.PERSISTENCE_FAILED) {
        GroupRotationTeamEntryCloudSyncError.QueuePersistenceFailed
    } else {
        when (val result = primaryResult) {
            is TournamentCloudUploadResult.Success ->
                GroupRotationTeamEntryCloudSyncError.Unexpected
            TournamentCloudUploadResult.AuthenticationRequired ->
                GroupRotationTeamEntryCloudSyncError.AuthenticationRequired
            TournamentCloudUploadResult.AuthorizationFailure ->
                GroupRotationTeamEntryCloudSyncError.AuthorizationFailure
            TournamentCloudUploadResult.ValidationFailure ->
                GroupRotationTeamEntryCloudSyncError.ValidationFailure
            TournamentCloudUploadResult.NetworkFailure ->
                GroupRotationTeamEntryCloudSyncError.NetworkFailure
            TournamentCloudUploadResult.TournamentLimitReached ->
                GroupRotationTeamEntryCloudSyncError.TournamentLimitReached
            is TournamentCloudUploadResult.Conflict ->
                GroupRotationTeamEntryCloudSyncError.Conflict
            is TournamentCloudUploadResult.PartialFailure ->
                GroupRotationTeamEntryCloudSyncError.PartialFailure
        }
    }
