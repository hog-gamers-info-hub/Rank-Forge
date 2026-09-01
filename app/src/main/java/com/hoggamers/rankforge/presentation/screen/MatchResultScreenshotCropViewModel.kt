package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.NoOpMatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropResult
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
class MatchResultScreenshotCropViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val assetRepository: MatchResultScreenshotAssetRepository = NoOpMatchResultScreenshotAssetRepository(),
    private val localImagePreserver: LocalImagePreserver,
    private val clock: Clock = Clock.systemUTC(),
    private val uploadCheckpoint: MatchResultScreenshotUploadCheckpointAction,
    private val reconciliationScheduler: ScreenshotReconciliationScheduler,
    private val autoCropProposer: MatchResultAutoCropProposer = MatchResultAutoCropProposer {
        MatchResultAutoCropResult.OcrFailed
    },
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchResultScreenshotCropUiState())
    val uiState: StateFlow<MatchResultScreenshotCropUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var confirmJob: Job? = null
    private var loadedKey: String? = null
    private var draftEdited = false
    private val missingMarked = mutableSetOf<String>()
    private val autoCropAttemptedKeys = mutableSetOf<AutoProposalKey>()
    private val autoProposals = mutableMapOf<AutoProposalKey, OcrNormalizedCropRect>()
    private var currentAutoProposalKey: AutoProposalKey? = null

    fun load(tournamentId: String, matchId: String, roleName: String) {
        val key = "$tournamentId:$matchId:$roleName"
        if (loadedKey == key) return
        loadedKey = key
        draftEdited = false
        loadJob?.cancel()
        currentAutoProposalKey = null
        val role = runCatching { MatchResultScreenshotRole.valueOf(roleName) }.getOrNull()
        if (role == null || tournamentId.isBlank() || matchId.isBlank()) {
            _uiState.value = MatchResultScreenshotCropUiState(
                isLoading = false,
                tournamentId = tournamentId,
                matchId = matchId,
                error = MatchResultScreenshotCropError.INVALID_ROLE,
            )
            return
        }
        val identity = MatchResultScreenshotIdentity(
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
        )
        _uiState.value = MatchResultScreenshotCropUiState(
            isLoading = true,
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
        )
        loadJob = viewModelScope.launch {
            val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()
            combine(
                if (ownerUserId.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(null)
                else assetRepository.observeByIdentityAndOwner(identity, ownerUserId),
                observeMatches(tournamentId),
            ) { asset, matches ->
                val match = matches.firstOrNull { it.id == matchId }
                if (match == null) {
                    currentAutoProposalKey = null
                    MatchResultScreenshotCropUiState(
                        isLoading = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        role = role,
                        isFinalized = true,
                        error = MatchResultScreenshotCropError.SAVE_FAILED,
                    )
                } else if (asset == null) {
                    currentAutoProposalKey = null
                    MatchResultScreenshotCropUiState(
                        isLoading = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        role = role,
                        isFinalized = match.status == MatchStatus.FINALIZED,
                        error = MatchResultScreenshotCropError.MISSING_ASSET,
                    )
                } else {
                    asset.toCropUiState(
                        role = role,
                        isFinalized = match.status == MatchStatus.FINALIZED,
                        previous = _uiState.value,
                    )
                }
            }.collect { state ->
                if (state.error == MatchResultScreenshotCropError.MISSING_LOCAL_FILE) {
                    markMissingIfNeeded(identity)
                }
                _uiState.value = state
                maybeLaunchAutoCrop(identity)
            }
        }
    }

    fun onCropChanged(crop: com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect) {
        draftEdited = true
        _uiState.update {
            it.copy(
                draftCrop = crop,
                error = when (it.error) {
                    MatchResultScreenshotCropError.INVALID_CROP,
                    -> null
                    else -> it.error
                },
            )
        }
    }

    fun confirmCrop(onConfirmed: () -> Unit) {
        val current = _uiState.value
        if (current.isSaving || confirmJob?.isActive == true) return
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() } ?: return
        val matchId = current.matchId?.takeIf { it.isNotBlank() } ?: return
        val role = current.role ?: return
        if (current.isFinalized) {
            _uiState.update {
                it.copy(
                    error = if (current.error == MatchResultScreenshotCropError.SAVE_FAILED) {
                        MatchResultScreenshotCropError.SAVE_FAILED
                    } else {
                        MatchResultScreenshotCropError.FINALIZED_MATCH
                    },
                )
            }
            return
        }
        if (current.imageUri == null) {
            _uiState.update { it.copy(error = MatchResultScreenshotCropError.MISSING_LOCAL_FILE) }
            return
        }
        val identity = MatchResultScreenshotIdentity(
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
        )
        confirmJob = viewModelScope.launch {
            try {
                val expectedOwnerUserId = screenshotOwnerProvider.currentOwnerUserId()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
                val match = try {
                    observeMatches(tournamentId)
                        .first()
                        .firstOrNull { it.id == matchId }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            if (match == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isFinalized = true,
                        error = MatchResultScreenshotCropError.SAVE_FAILED,
                    )
                }
                return@launch
            }
            if (match.status != MatchStatus.DRAFT) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isFinalized = true,
                        error = MatchResultScreenshotCropError.FINALIZED_MATCH,
                    )
                }
                return@launch
            }

            val existing = try {
                    assetRepository.getByIdentityAndOwner(identity, expectedOwnerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (existing?.identityOrNull() != identity) {
                _uiState.update { it.copy(error = MatchResultScreenshotCropError.INVALID_ROLE) }
                return@launch
            }
            val dimensions = OcrImageDimensions.from(existing.originalWidth, existing.originalHeight)
            when (
                val geometry = OcrCropValidator.validate(
                    crop = current.draftCrop,
                    dimensions = dimensions,
                    profile = OcrCropValidationProfiles.MatchResult,
                )
            ) {
                is OcrCropValidationResult.Valid -> Unit
                is OcrCropValidationResult.Invalid -> {
                    _uiState.update { it.copy(error = MatchResultScreenshotCropError.INVALID_CROP) }
                    return@launch
                }
            }

            val localFile = localImagePreserver.resolveRelativePath(existing.localRelativePath)
                ?.takeIf { file ->
                    runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)
                }
            if (localFile == null) {
                _uiState.update { it.copy(error = MatchResultScreenshotCropError.MISSING_LOCAL_FILE) }
                return@launch
            }
            if (!isCurrentConfirmationSnapshot(current, identity, existing, existing)) return@launch

            val latest = try {
                assetRepository.getByIdentityAndOwner(identity, expectedOwnerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (latest == null || !isCurrentConfirmationSnapshot(current, identity, existing, latest)) {
                clearConfirmationIfCurrent(identity)
                return@launch
            }

            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = try {
                assetRepository.persistConfirmedCropByOwner(
                    identity = identity,
                    ownerUserId = expectedOwnerUserId,
                    crop = current.draftCrop,
                    updatedAt = clock.millis(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchResultScreenshotCropSaveResult.InvalidCrop
            }
            handleSaveResult(result, identity, current.draftCrop, onConfirmed, expectedOwnerUserId)
            } catch (cancellation: CancellationException) {
                clearConfirmationIfCurrent(identity)
                throw cancellation
            } finally {
                confirmJob = null
            }
        }
    }

    private fun handleSaveResult(
        result: MatchResultScreenshotCropSaveResult,
        identity: MatchResultScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        onConfirmed: () -> Unit,
        expectedOwnerUserId: String,
    ) {
        when (result) {
                MatchResultScreenshotCropSaveResult.Saved -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            confirmedCrop = crop,
                            error = null,
                        )
                    }
                    reconciliationScheduler.schedule(expectedOwnerUserId, screenshotOwnerProvider) {
                        uploadCheckpoint.run(identity, expectedOwnerUserId)
                    }
                    draftEdited = false
                    onConfirmed()
                }
                MatchResultScreenshotCropSaveResult.MissingAsset -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = MatchResultScreenshotCropError.MISSING_ASSET)
                    }
                }
                MatchResultScreenshotCropSaveResult.InvalidIdentity -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = MatchResultScreenshotCropError.INVALID_ROLE)
                    }
                }
                MatchResultScreenshotCropSaveResult.InvalidCrop -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = MatchResultScreenshotCropError.INVALID_CROP)
                    }
                }
                MatchResultScreenshotCropSaveResult.AuthenticationRequired,
                MatchResultScreenshotCropSaveResult.MatchNotFound,
                -> _uiState.update { it.copy(isSaving = false, error = MatchResultScreenshotCropError.SAVE_FAILED) }
            }
    }

    private fun isCurrentConfirmationSnapshot(
        snapshot: MatchResultScreenshotCropUiState,
        identity: MatchResultScreenshotIdentity,
        expectedAsset: MatchResultScreenshotAssetEntity,
        currentAsset: MatchResultScreenshotAssetEntity,
    ): Boolean {
        val current = _uiState.value
        return current.tournamentId == snapshot.tournamentId &&
            current.matchId == snapshot.matchId &&
            current.role == snapshot.role &&
            current.draftCrop == snapshot.draftCrop &&
            current.originalWidth == expectedAsset.originalWidth &&
            current.originalHeight == expectedAsset.originalHeight &&
            currentAsset.identityOrNull() == identity &&
            currentAsset.sha256 == expectedAsset.sha256 &&
            currentAsset.localRelativePath == expectedAsset.localRelativePath &&
            currentAsset.originalWidth == expectedAsset.originalWidth &&
            currentAsset.originalHeight == expectedAsset.originalHeight &&
            currentAsset.byteSize == expectedAsset.byteSize
    }

    private fun clearConfirmationIfCurrent(identity: MatchResultScreenshotIdentity) {
        _uiState.update { state ->
            if (
                state.tournamentId == identity.tournamentId &&
                state.matchId == identity.matchId &&
                state.role == identity.role
            ) {
                state.copy(isSaving = false)
            } else {
                state
            }
        }
    }

    private fun MatchResultScreenshotAssetEntity.toCropUiState(
        role: MatchResultScreenshotRole,
        isFinalized: Boolean,
        previous: MatchResultScreenshotCropUiState,
    ): MatchResultScreenshotCropUiState {
        val proposalKey = autoProposalKey(role)
        currentAutoProposalKey = proposalKey
        val file = localImagePreserver.resolveRelativePath(localRelativePath)
        val fileExists = file?.let { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) } == true
        val confirmedCrop = confirmedCropOrNull()
        val draft = if (draftEdited && previous.role == role) {
            previous.draftCrop
        } else {
            confirmedCrop ?: autoProposals[proposalKey] ?: OcrVisualCropDefaults.FullImageCrop
        }
        val sameIdentity = previous.tournamentId == tournamentId &&
            previous.matchId == matchId &&
            previous.role == role
        return MatchResultScreenshotCropUiState(
            isLoading = false,
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
            imageUri = if (fileExists) file.toURI().toString() else null,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            confirmedCrop = confirmedCrop,
            draftCrop = draft,
            isFinalized = isFinalized,
            isSaving = if (sameIdentity) previous.isSaving else false,
            error = if (fileExists) {
                null
            } else {
                MatchResultScreenshotCropError.MISSING_LOCAL_FILE
            },
        )
    }

    private fun markMissingIfNeeded(identity: MatchResultScreenshotIdentity) {
        val key = "${identity.tournamentId}:${identity.matchId}:${identity.role.name}"
        if (!missingMarked.add(key)) return
        viewModelScope.launch {
            runCatching {
                assetRepository.markLocalMissingByOwner(
                    identity,
                    screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                    clock.millis(),
                )
            }
        }
    }

    private fun maybeLaunchAutoCrop(identity: MatchResultScreenshotIdentity) {
        val current = _uiState.value
        val proposalKey = currentAutoProposalKey ?: return
        if (
            current.isLoading ||
            current.role != identity.role ||
            current.tournamentId != identity.tournamentId ||
            current.matchId != identity.matchId ||
            current.isFinalized ||
            current.imageUri == null ||
            current.confirmedCrop != null ||
            draftEdited ||
            autoProposals.containsKey(proposalKey) ||
            !autoCropAttemptedKeys.add(proposalKey)
        ) {
            return
        }

        viewModelScope.launch {
            val asset = try {
                assetRepository.getByIdentityAndOwner(identity, screenshotOwnerProvider.currentOwnerUserId().orEmpty())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            } ?: return@launch
            if (asset.autoProposalKey(identity.role) != proposalKey) return@launch
            val localFile = localImagePreserver.resolveRelativePath(asset.localRelativePath)
                ?.takeIf { file ->
                    runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)
                }
                ?: return@launch
            val result = try {
                autoCropProposer.propose(localFile)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchResultAutoCropResult.OcrFailed
            }
            val proposedCrop = (result as? MatchResultAutoCropResult.Proposed)?.crop ?: return@launch
            autoProposals[proposalKey] = proposedCrop
            if (!isCurrentAutoProposal(identity, proposalKey)) return@launch
            _uiState.update { state ->
                if (isCurrentAutoProposal(identity, proposalKey)) {
                    state.copy(draftCrop = proposedCrop)
                } else {
                    state
                }
            }
        }
    }

    private fun isCurrentAutoProposal(
        identity: MatchResultScreenshotIdentity,
        proposalKey: AutoProposalKey,
    ): Boolean {
        val current = _uiState.value
        return currentAutoProposalKey == proposalKey &&
            loadedKey == "${identity.tournamentId}:${identity.matchId}:${identity.role.name}" &&
            current.tournamentId == identity.tournamentId &&
            current.matchId == identity.matchId &&
            current.role == identity.role &&
            current.imageUri != null &&
            !current.isFinalized &&
            current.confirmedCrop == null &&
            !draftEdited
    }

    private fun MatchResultScreenshotAssetEntity.autoProposalKey(
        role: MatchResultScreenshotRole,
    ): AutoProposalKey = AutoProposalKey(
        tournamentId = tournamentId,
        matchId = matchId,
        role = role,
        sha256 = sha256,
    )

    private data class AutoProposalKey(
        val tournamentId: String,
        val matchId: String,
        val role: MatchResultScreenshotRole,
        val sha256: String,
    )

}
private fun MatchResultScreenshotAssetEntity.confirmedCropOrNull():
    com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect? {
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    return runCatching {
        com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }.getOrNull()
}
