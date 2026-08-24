package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
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

@HiltViewModel
class MatchLobbyScreenshotCropViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val clock: Clock,
    private val uploadCheckpoint: MatchLobbyScreenshotUploadCheckpointAction,
    private val reconciliationScheduler: ScreenshotReconciliationScheduler,
    private val autoCropProposer: MatchLobbyAutoCropProposer,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchLobbyScreenshotCropUiState())
    val uiState: StateFlow<MatchLobbyScreenshotCropUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedKey: String? = null
    private var draftEdited = false
    private val missingMarked = mutableSetOf<String>()
    private val autoCropAttemptedKeys = mutableSetOf<AutoProposalKey>()
    private val autoProposals = mutableMapOf<AutoProposalKey, OcrNormalizedCropRect>()
    private var currentAutoProposalKey: AutoProposalKey? = null

    fun load(tournamentId: String, matchId: String, lobbyScreenshotIndex: Int) {
        val key = "$tournamentId:$matchId:$lobbyScreenshotIndex"
        if (loadedKey == key) return
        loadedKey = key
        loadJob?.cancel()
        draftEdited = false
        currentAutoProposalKey = null
        if (tournamentId.isBlank() || matchId.isBlank() || lobbyScreenshotIndex !in 1..3) {
            _uiState.value = MatchLobbyScreenshotCropUiState(
                isLoading = false,
                tournamentId = tournamentId,
                matchId = matchId,
                lobbyScreenshotIndex = lobbyScreenshotIndex,
                error = MatchLobbyScreenshotCropError.INVALID_INDEX,
            )
            return
        }
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, lobbyScreenshotIndex)
        _uiState.value = MatchLobbyScreenshotCropUiState(
            isLoading = true,
            tournamentId = tournamentId,
            matchId = matchId,
            lobbyScreenshotIndex = lobbyScreenshotIndex,
        )
        loadJob = viewModelScope.launch {
            val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()
            combine(
                if (ownerUserId.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(null)
                else assetRepository.observeByIdentityAndOwner(identity, ownerUserId),
                observeMatches(tournamentId),
            ) { asset, matches ->
                val match = matches.firstOrNull { it.id == matchId && it.tournamentId == tournamentId }
                when {
                    match == null -> MatchLobbyScreenshotCropUiState(
                        isLoading = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        lobbyScreenshotIndex = lobbyScreenshotIndex,
                        error = MatchLobbyScreenshotCropError.SAVE_FAILED,
                    )
                    asset == null -> MatchLobbyScreenshotCropUiState(
                        isLoading = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        lobbyScreenshotIndex = lobbyScreenshotIndex,
                        isFinalized = match.status == MatchStatus.FINALIZED,
                        error = MatchLobbyScreenshotCropError.MISSING_ASSET,
                    )
                    else -> asset.toUiState(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        index = lobbyScreenshotIndex,
                        isFinalized = match.status == MatchStatus.FINALIZED,
                        previous = _uiState.value,
                    )
                }
            }.collect { state ->
                if (state.error == MatchLobbyScreenshotCropError.MISSING_LOCAL_FILE) markMissingIfNeeded(identity)
                _uiState.value = state
                maybeLaunchAutoCrop(identity)
            }
        }
    }

    fun onCropChanged(crop: OcrNormalizedCropRect) {
        draftEdited = true
        _uiState.update {
            it.copy(
                draftCrop = crop,
                error = if (it.error == MatchLobbyScreenshotCropError.INVALID_CROP) null else it.error,
            )
        }
    }

    fun confirmCrop(onConfirmed: () -> Unit) {
        val current = _uiState.value
        if (current.isSaving) return
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() } ?: return
        val matchId = current.matchId?.takeIf { it.isNotBlank() } ?: return
        val index = current.lobbyScreenshotIndex ?: return
        if (current.isFinalized) {
            _uiState.update { it.copy(error = MatchLobbyScreenshotCropError.FINALIZED_MATCH) }
            return
        }
        if (current.imageUri == null) {
            _uiState.update { it.copy(error = MatchLobbyScreenshotCropError.MISSING_LOCAL_FILE) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val expectedOwnerUserId = screenshotOwnerProvider.currentOwnerUserId()
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    _uiState.update { it.copy(isSaving = false, error = MatchLobbyScreenshotCropError.SAVE_FAILED) }
                    return@launch
                }
            val match = try {
                observeMatches(tournamentId).first().firstOrNull { it.id == matchId }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (match == null) {
                _uiState.update { it.copy(isSaving = false, error = MatchLobbyScreenshotCropError.SAVE_FAILED) }
                return@launch
            }
            if (match.status != MatchStatus.DRAFT) {
                _uiState.update { it.copy(isSaving = false, isFinalized = true, error = MatchLobbyScreenshotCropError.FINALIZED_MATCH) }
                return@launch
            }
            val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, index)
            val result = try {
                assetRepository.persistConfirmedCropByOwner(
                    identity,
                    expectedOwnerUserId,
                    current.draftCrop,
                    clock.millis(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchLobbyScreenshotCropSaveResult.InvalidCrop
            }
            when (result) {
                MatchLobbyScreenshotCropSaveResult.Saved -> {
                    draftEdited = false
                    _uiState.update {
                        it.copy(
                            confirmedCrop = current.draftCrop,
                            error = null,
                        )
                    }
                    reconciliationScheduler.schedule(expectedOwnerUserId, screenshotOwnerProvider) {
                        uploadCheckpoint.run(identity, expectedOwnerUserId)
                    }
                    _uiState.update { it.copy(isSaving = false) }
                    onConfirmed()
                }
                MatchLobbyScreenshotCropSaveResult.MissingAsset ->
                    _uiState.update { it.copy(isSaving = false, error = MatchLobbyScreenshotCropError.MISSING_ASSET) }
                MatchLobbyScreenshotCropSaveResult.InvalidIdentity,
                MatchLobbyScreenshotCropSaveResult.InvalidCrop,
                MatchLobbyScreenshotCropSaveResult.AuthenticationRequired,
                MatchLobbyScreenshotCropSaveResult.MatchNotFound,
                -> _uiState.update { it.copy(isSaving = false, error = MatchLobbyScreenshotCropError.INVALID_CROP) }
            }
        }
    }

    private fun markMissingIfNeeded(identity: MatchLobbyScreenshotIdentity) {
        val key = "${identity.tournamentId}:${identity.matchId}:${identity.lobbyScreenshotIndex}"
        if (!missingMarked.add(key)) return
        viewModelScope.launch {
            try {
                val expectedOwnerUserId = screenshotOwnerProvider.currentOwnerUserId()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
                assetRepository.markLocalMissingByOwner(
                    identity,
                    expectedOwnerUserId,
                    clock.millis(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    private fun MatchLobbyScreenshotAssetEntity.toUiState(
        tournamentId: String,
        matchId: String,
        index: Int,
        isFinalized: Boolean,
        previous: MatchLobbyScreenshotCropUiState,
    ): MatchLobbyScreenshotCropUiState {
        val file = localImagePreserver.resolveRelativePath(localRelativePath)
        val exists = file?.let { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) } == true
        val confirmed = confirmedLobbyCropOrNull()
        val proposalKey = autoProposalKey()
        val sourceChanged = currentAutoProposalKey != null && currentAutoProposalKey != proposalKey
        if (sourceChanged) draftEdited = false
        val sameSource = currentAutoProposalKey == proposalKey
        currentAutoProposalKey = proposalKey
        return MatchLobbyScreenshotCropUiState(
            isLoading = false,
            tournamentId = tournamentId,
            matchId = matchId,
            lobbyScreenshotIndex = index,
            imageUri = if (exists) file?.toURI()?.toString() else null,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            confirmedCrop = confirmed,
            draftCrop = if (sameSource && draftEdited && previous.lobbyScreenshotIndex == index) previous.draftCrop
            else confirmed ?: autoProposals[proposalKey]
                ?: OcrVisualCropDefaults.FullImageCrop,
            isFinalized = isFinalized,
            error = if (exists) null else MatchLobbyScreenshotCropError.MISSING_LOCAL_FILE,
        )
    }

    private fun MatchLobbyScreenshotAssetEntity.confirmedLobbyCropOrNull(): OcrNormalizedCropRect? {
        if (cropProfileId != OcrCropValidationProfiles.Lobby.id) return null
        val crop = OcrNormalizedCropRect(
            cropLeft ?: return null,
            cropTop ?: return null,
            cropRight ?: return null,
            cropBottom ?: return null,
        )
        return when (OcrCropValidator.validate(crop, OcrCropValidationProfiles.Lobby)) {
            is OcrCropValidationResult.Valid -> crop
            is OcrCropValidationResult.Invalid -> null
        }
    }

    private fun maybeLaunchAutoCrop(identity: MatchLobbyScreenshotIdentity) {
        val current = _uiState.value
        val proposalKey = currentAutoProposalKey ?: return
        if (
            current.isLoading ||
            current.tournamentId != identity.tournamentId ||
            current.matchId != identity.matchId ||
            current.lobbyScreenshotIndex != identity.lobbyScreenshotIndex ||
            current.isFinalized ||
            current.imageUri == null ||
            current.confirmedCrop != null ||
            draftEdited ||
            autoProposals.containsKey(proposalKey) ||
            !autoCropAttemptedKeys.add(proposalKey)
        ) return

        viewModelScope.launch {
            val asset = try {
                assetRepository.getByIdentityAndOwner(identity, screenshotOwnerProvider.currentOwnerUserId().orEmpty())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            } ?: return@launch
            if (asset.autoProposalKey() != proposalKey) return@launch
            val localFile = localImagePreserver.resolveRelativePath(asset.localRelativePath)
                ?.takeIf { file -> runCatching { file.isFile && file.length() > 0L }.getOrDefault(false) }
                ?: return@launch
            val result = try {
                autoCropProposer.propose(localFile)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchLobbyAutoCropResult.NoProposal
            }
            val proposedCrop = (result as? MatchLobbyAutoCropResult.Proposed)?.crop ?: return@launch
            autoProposals[proposalKey] = proposedCrop
            if (!isCurrentAutoProposal(identity, proposalKey)) return@launch
            _uiState.update { state ->
                if (isCurrentAutoProposal(identity, proposalKey)) state.copy(draftCrop = proposedCrop) else state
            }
        }
    }

    private fun isCurrentAutoProposal(
        identity: MatchLobbyScreenshotIdentity,
        proposalKey: AutoProposalKey,
    ): Boolean {
        val current = _uiState.value
        return currentAutoProposalKey == proposalKey &&
            current.tournamentId == identity.tournamentId &&
            current.matchId == identity.matchId &&
            current.lobbyScreenshotIndex == identity.lobbyScreenshotIndex &&
            current.imageUri != null &&
            !current.isFinalized &&
            current.confirmedCrop == null &&
            !draftEdited
    }

    private fun MatchLobbyScreenshotAssetEntity.autoProposalKey(): AutoProposalKey = AutoProposalKey(
        tournamentId = tournamentId,
        matchId = matchId,
        screenshotIndex = lobbyScreenshotIndex,
        sha256 = sha256,
        revision = revision,
    )

    private data class AutoProposalKey(
        val tournamentId: String,
        val matchId: String,
        val screenshotIndex: Int,
        val sha256: String,
        val revision: Long,
    )
}
