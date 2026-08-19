package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.NoOpMatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
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
class MatchLobbyScreenshotIntakeViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val imageCandidateValidator: ImageCandidateValidator,
    private val duplicateDetector: MatchLobbyScreenshotDuplicateDetector,
    private val localImagePreserver: LocalImagePreserver,
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val clock: Clock,
    private val saveLobbyTemplate: SaveLobbyTemplateUseCase,
    private val unsaveLobbyTemplate: UnsaveLobbyTemplateUseCase,
    private val templateRepository: TournamentLobbyTemplateAssetRepository,
    private val cloudDataSource: MatchLobbyScreenshotAssetCloudDataSource = NoOpMatchLobbyScreenshotAssetCloudDataSource(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchLobbyScreenshotIntakeUiState())
    val uiState: StateFlow<MatchLobbyScreenshotIntakeUiState> = _uiState.asStateFlow()

    private var loadedKey: String? = null
    private var loadJob: Job? = null
    private val missingMarked = mutableSetOf<String>()

    fun load(tournamentId: String, matchId: String) {
        val key = "$tournamentId:$matchId"
        if (loadedKey == key) return
        loadedKey = key
        loadJob?.cancel()
        missingMarked.clear()
        if (tournamentId.isBlank() || matchId.isBlank()) {
            _uiState.value = MatchLobbyScreenshotIntakeUiState(
                isLoading = false,
                tournamentId = tournamentId,
                matchId = matchId,
                intakeError = MatchLobbyScreenshotIntakeError.INVALID_CONTEXT,
            )
            return
        }
        _uiState.value = MatchLobbyScreenshotIntakeUiState(
            isLoading = true,
            tournamentId = tournamentId,
            matchId = matchId,
        )
        loadJob = viewModelScope.launch {
            combine(
                observeMatches(tournamentId),
                assetRepository.observeByMatchId(matchId),
                templateRepository.observeByTournamentId(tournamentId),
            ) { matches, assets, templates ->
                val match = matches.firstOrNull { it.id == matchId && it.tournamentId == tournamentId }
                if (match == null) {
                    MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        intakeError = MatchLobbyScreenshotIntakeError.MATCH_NOT_FOUND,
                        isLobbySavedForNextMatches = isCompleteLobbyTemplate(
                            tournamentId,
                            templates,
                            localImagePreserver,
                        ),
                    )
                } else {
                    MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        status = match.status,
                        slots = defaultMatchLobbyScreenshotSlots().map { emptySlot ->
                            assets.firstOrNull {
                                it.lobbyScreenshotIndex == emptySlot.index &&
                                    it.matchId == matchId &&
                                    it.tournamentId == tournamentId
                            }?.toUiState(emptySlot.index) ?: emptySlot
                        },
                        pendingCropNavigationSlotIndex = _uiState.value.pendingCropNavigationSlotIndex,
                        isLobbySavedForNextMatches = isCompleteLobbyTemplate(
                            tournamentId,
                            templates,
                            localImagePreserver,
                        ),
                    )
                }
            }.collect { state ->
                state.slots.filter { it.isLocalFileMissing && it.hasLinkedAsset }.forEach { slot ->
                    markMissingIfNeeded(state.tournamentId!!, state.matchId!!, slot.index)
                }
                _uiState.value = mergeTransientState(state)
            }
        }
    }

    fun requestPhotoPicker(index: Int) {
        val current = _uiState.value
        if (index !in 1..3) {
            _uiState.update { it.copy(intakeError = MatchLobbyScreenshotIntakeError.INVALID_INDEX) }
            return
        }
        if (!current.isAvailable) return
        if (current.isFinalized) {
            _uiState.update { it.copy(intakeError = MatchLobbyScreenshotIntakeError.FINALIZED_MATCH) }
            return
        }
        if (current.slots.any { it.isPhotoPickerRequestActive } || current.slots.any { it.isPhotoPickerLaunchPending }) return
        _uiState.update {
            it.replaceSlot(index) { slot ->
                slot.copy(
                    isPhotoPickerLaunchPending = true,
                    isPhotoPickerRequestActive = true,
                    photoPickerError = null,
                )
            }.copy(intakeError = null)
        }
    }

    fun onPhotoPickerLaunchHandled(index: Int) {
        _uiState.update {
            it.replaceSlot(index) { slot -> slot.copy(isPhotoPickerLaunchPending = false) }
        }
    }

    fun onPhotoPickerLaunchFailed(index: Int) {
        _uiState.update {
            it.replaceSlot(index) {
                it.copy(
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                    photoPickerError = MatchLobbyScreenshotIntakeError.PHOTO_PICKER_LAUNCH_FAILED,
                )
            }
        }
    }

    fun onPhotoPickerResult(selectedUri: String?) {
        val index = _uiState.value.slots.firstOrNull { it.isPhotoPickerRequestActive }?.index
        val slot = index?.let(_uiState.value::slot) ?: return
        _uiState.update {
            it.replaceSlot(index) { current ->
                current.copy(
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                )
            }
        }
        val replacementUri = selectedUri?.takeIf { it.isNotBlank() } ?: return
        _uiState.update {
            it.replaceSlot(index) { current ->
                current.copy(
                    isValidationInProgress = true,
                    imageValidationError = null,
                    duplicateError = null,
                    preservationError = null,
                )
            }
        }
        viewModelScope.launch { processSelection(index, replacementUri) }
    }

    fun onCropNavigationHandled() {
        _uiState.update { it.copy(pendingCropNavigationSlotIndex = null) }
    }

    fun saveLobbyForNextMatches() {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        if (!current.canSaveLobbyForNextMatches) return
        _uiState.update {
            it.copy(
                isLobbyTemplateMutationInProgress = true,
                lobbyTemplateSaveStatus = null,
            )
        }
        viewModelScope.launch {
            val result = try {
                saveLobbyTemplate(tournamentId, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                SaveLobbyTemplateResult.Failed
            }
            _uiState.update {
                it.copy(
                    isLobbyTemplateMutationInProgress = false,
                    lobbyTemplateSaveStatus = when (result) {
                        SaveLobbyTemplateResult.Saved -> MatchLobbyTemplateSaveStatus.SAVED
                        SaveLobbyTemplateResult.NotReady,
                        SaveLobbyTemplateResult.Failed,
                        -> MatchLobbyTemplateSaveStatus.FAILED
                    },
                )
            }
        }
    }

    fun unsaveLobbyForNextMatches() {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        if (!current.canUnsaveLobbyForNextMatches) return
        _uiState.update {
            it.copy(
                isLobbyTemplateMutationInProgress = true,
                lobbyTemplateSaveStatus = null,
            )
        }
        viewModelScope.launch {
            val result = try {
                unsaveLobbyTemplate(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                UnsaveLobbyTemplateResult.Failed
            }
            _uiState.update {
                it.copy(
                    isLobbyTemplateMutationInProgress = false,
                    lobbyTemplateSaveStatus = when (result) {
                        UnsaveLobbyTemplateResult.Unsaved -> MatchLobbyTemplateSaveStatus.UNSAVED
                        UnsaveLobbyTemplateResult.Failed -> MatchLobbyTemplateSaveStatus.FAILED
                    },
                )
            }
        }
    }

    fun requestCropEditor(index: Int) {
        val current = _uiState.value
        val slot = current.slot(index)
        if (slot == null) {
            _uiState.update { it.copy(intakeError = MatchLobbyScreenshotIntakeError.INVALID_INDEX) }
            return
        }
        if (!current.isAvailable || current.isFinalized) return
        if (slot.isBusy || !slot.hasLinkedAsset || slot.isLocalFileMissing) return
        _uiState.update { it.copy(pendingCropNavigationSlotIndex = index) }
    }

    fun removeScreenshot(index: Int) {
        val current = _uiState.value
        val slot = current.slot(index) ?: run {
            _uiState.update { it.copy(intakeError = MatchLobbyScreenshotIntakeError.INVALID_INDEX) }
            return
        }
        if (current.isFinalized) {
            _uiState.update { it.copy(intakeError = MatchLobbyScreenshotIntakeError.FINALIZED_MATCH) }
            return
        }
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        val fingerprint = slot.fingerprint
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, index)
        viewModelScope.launch {
            val cleanup = if (slot.localRelativePath != null) {
                localImagePreserver.cleanupLobbyScreenshot(tournamentId, matchId, index)
            } else {
                LocalImageCleanupResult.Cleaned
            }
            if (cleanup == LocalImageCleanupResult.Failed) {
                _uiState.update {
                    it.replaceSlot(index) { existing ->
                        existing.copy(preservationError = MatchLobbyScreenshotPreservationError.CLEANUP_FAILED)
                    }.copy(intakeError = MatchLobbyScreenshotIntakeError.REMOVE_FAILED)
                }
                return@launch
            }
            try {
                assetRepository.deleteByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, index))
                duplicateDetector.unlink(MatchLobbyScreenshotIdentity(tournamentId, matchId, index), fingerprint)
                _uiState.update {
                    it.replaceSlot(index) { MatchLobbyScreenshotSlotUiState(index) }
                }
                viewModelScope.launch {
                    try {
                        cloudDataSource.deleteByIdentity(identity)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Local removal is authoritative; cloud cleanup is best effort.
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update { it.copy(intakeError = MatchLobbyScreenshotIntakeError.REMOVE_FAILED) }
            }
        }
    }

    private suspend fun processSelection(index: Int, selectedUri: String) {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        val existing = current.slot(index)
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, index)
        val existingAsset = try {
            assetRepository.getByIdentity(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            updateSlot(index) { it.copy(isValidationInProgress = false, preservationError = MatchLobbyScreenshotPreservationError.SAVE_FAILED) }
            return
        }
        when (val validation = imageCandidateValidator.validate(selectedUri)) {
            is ImageCandidateValidationResult.Invalid -> {
                updateSlot(index) { it.copy(isValidationInProgress = false, imageValidationError = validation.error) }
                return
            }
            ImageCandidateValidationResult.Valid -> Unit
        }
        val metadata = imageCandidateValidator.readValidMetadata(selectedUri)
        if (metadata == null || metadata.mimeType.isNullOrBlank()) {
            updateSlot(index) {
                it.copy(isValidationInProgress = false, imageValidationError = ImageValidationError.UNREADABLE_URI)
            }
            return
        }
        updateSlot(index) { it.copy(isValidationInProgress = false, isDuplicateDetectionInProgress = true) }
        val duplicateResult = duplicateDetector.link(identity, selectedUri, existing?.fingerprint)
        var sameIdentityRecovery = false
        val fingerprint = when (duplicateResult) {
            MatchLobbyScreenshotDuplicateLinkResult.SameIdentity -> {
                if (existing?.isLocalFileMissing != true) {
                    updateSlot(index) { it.copy(isDuplicateDetectionInProgress = false) }
                    _uiState.update { it.copy(pendingCropNavigationSlotIndex = index) }
                    return
                }
                sameIdentityRecovery = true
                existingAsset?.sha256 ?: run {
                    updateSlot(index) {
                        it.copy(
                            isDuplicateDetectionInProgress = false,
                            duplicateError = MatchLobbyScreenshotDuplicateError.STATE_CONFLICT,
                        )
                    }
                    return
                }
            }
            is MatchLobbyScreenshotDuplicateLinkResult.Linked -> duplicateResult.fingerprint
            MatchLobbyScreenshotDuplicateLinkResult.FingerprintFailure,
            MatchLobbyScreenshotDuplicateLinkResult.StateConflict,
            is MatchLobbyScreenshotDuplicateLinkResult.LinkedToOtherIdentity,
            -> ""
        }
        when (duplicateResult) {
            MatchLobbyScreenshotDuplicateLinkResult.FingerprintFailure -> {
                updateSlot(index) { it.copy(isDuplicateDetectionInProgress = false, duplicateError = MatchLobbyScreenshotDuplicateError.STATE_CONFLICT) }
                return
            }
            MatchLobbyScreenshotDuplicateLinkResult.StateConflict -> {
                updateSlot(index) { it.copy(isDuplicateDetectionInProgress = false, duplicateError = MatchLobbyScreenshotDuplicateError.STATE_CONFLICT) }
                return
            }
            is MatchLobbyScreenshotDuplicateLinkResult.LinkedToOtherIdentity -> {
                updateSlot(index) { it.copy(isDuplicateDetectionInProgress = false, duplicateError = MatchLobbyScreenshotDuplicateError.USED_BY_ANOTHER_LOBBY_SCREENSHOT) }
                return
            }
            MatchLobbyScreenshotDuplicateLinkResult.SameIdentity,
            is MatchLobbyScreenshotDuplicateLinkResult.Linked,
            -> Unit
        }
        updateSlot(index) { it.copy(isDuplicateDetectionInProgress = false, isPreservationInProgress = true) }
        val preservation = try {
            localImagePreserver.preserveLobbyScreenshot(tournamentId, matchId, index, selectedUri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        val file = when (preservation) {
            is LocalImagePreservationResult.Preserved -> preservation.file
            is LocalImagePreservationResult.PreservedWithCleanupFailure -> preservation.file
            is LocalImagePreservationResult.Failed -> {
                if (!sameIdentityRecovery) duplicateDetector.rollback(identity, fingerprint, existing?.fingerprint)
                updateSlot(index) { it.copy(isPreservationInProgress = false, preservationError = MatchLobbyScreenshotPreservationError.PRESERVATION_FAILED) }
                return
            }
        }
        val retainCloudState = sameIdentityRecovery &&
            existingAsset?.uploadStatus == ScreenshotUploadStatus.UPLOADED.name &&
            !existingAsset?.storageBucket.isNullOrBlank() &&
            !existingAsset?.storageObjectPath.isNullOrBlank()
        val ownerId = if (sameIdentityRecovery) {
            existingAsset?.ownerUserId?.takeIf { it.isNotBlank() }
        } else {
            screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
        }
        if (ownerId == null) {
            if (!sameIdentityRecovery) duplicateDetector.rollback(identity, fingerprint, existing?.fingerprint)
            localImagePreserver.cleanupLobbyScreenshot(tournamentId, matchId, index)
            updateSlot(index) { it.copy(isPreservationInProgress = false, preservationError = MatchLobbyScreenshotPreservationError.OWNER_MISSING) }
            return
        }
        val now = clock.millis()
        val asset = MatchLobbyScreenshotAssetEntity(
            tournamentId = tournamentId,
            matchId = matchId,
            lobbyScreenshotIndex = index,
            ownerUserId = ownerId,
            localRelativePath = localImagePreserver.relativePathFor(file)
                ?: localImagePreserver.lobbyRelativePath(tournamentId, matchId, index, extensionFor(metadata.mimeType)),
            fileExtension = extensionFor(metadata.mimeType),
            mimeType = metadata.mimeType,
            originalWidth = metadata.width,
            originalHeight = metadata.height,
            byteSize = runCatching { file.length() }.getOrDefault(0L),
            sha256 = fingerprint,
            localStatus = if (preservation is LocalImagePreservationResult.PreservedWithCleanupFailure) {
                ScreenshotLocalStatus.CLEANUP_FAILED.name
            } else ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = if (retainCloudState) existingAsset?.uploadStatus.orEmpty() else ScreenshotUploadStatus.PENDING.name,
            uploadFailureCode = if (retainCloudState) existingAsset?.uploadFailureCode else null,
            storageBucket = if (retainCloudState) existingAsset?.storageBucket else null,
            storageObjectPath = if (retainCloudState) existingAsset?.storageObjectPath else null,
            cropProfileId = if (sameIdentityRecovery) existingAsset?.cropProfileId else null,
            cropLeft = if (sameIdentityRecovery) existingAsset?.cropLeft else null,
            cropTop = if (sameIdentityRecovery) existingAsset?.cropTop else null,
            cropRight = if (sameIdentityRecovery) existingAsset?.cropRight else null,
            cropBottom = if (sameIdentityRecovery) existingAsset?.cropBottom else null,
            createdAt = existingAsset?.let { assetCreatedAt(it) } ?: now,
            updatedAt = now,
            preservedAt = now,
            uploadedAt = if (retainCloudState) existingAsset?.uploadedAt else null,
            revision = (existingAsset?.revision ?: 0L) + 1L,
        )
        val saveResult = assetRepository.saveOrReplace(asset)
        when (saveResult) {
            MatchLobbyScreenshotAssetSaveResult.Saved -> {
                updateSlot(index) {
                    it.copy(
                        selectedScreenshotUri = file.toURI().toString(),
                        selectedScreenshotMimeType = metadata.mimeType,
                        selectedScreenshotWidth = metadata.width,
                        selectedScreenshotHeight = metadata.height,
                        fingerprint = fingerprint,
                        localRelativePath = asset.localRelativePath,
                        hasLinkedAsset = true,
                        isLocalFileMissing = false,
                         confirmedCrop = if (sameIdentityRecovery) it.confirmedCrop else null,
                         cropProfileId = if (sameIdentityRecovery) it.cropProfileId else null,
                        isPreservationInProgress = false,
                        preservationError = null,
                    )
                }
                _uiState.update { it.copy(pendingCropNavigationSlotIndex = index) }
                if (retainCloudState) {
                    syncRetainedCloudMetadata(identity, fingerprint)
                }
            }
            MatchLobbyScreenshotAssetSaveResult.InvalidIdentity,
            MatchLobbyScreenshotAssetSaveResult.StateConflict,
            -> {
                if (!sameIdentityRecovery) duplicateDetector.rollback(identity, fingerprint, existing?.fingerprint)
                updateSlot(index) { it.copy(isPreservationInProgress = false, preservationError = MatchLobbyScreenshotPreservationError.SAVE_FAILED) }
            }
            is MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint -> {
                if (!sameIdentityRecovery) duplicateDetector.rollback(identity, fingerprint, existing?.fingerprint)
                localImagePreserver.cleanupLobbyScreenshot(tournamentId, matchId, index)
                updateSlot(index) { it.copy(isPreservationInProgress = false, preservationError = MatchLobbyScreenshotPreservationError.SAVE_FAILED) }
            }
        }
    }

    private suspend fun syncRetainedCloudMetadata(
        identity: MatchLobbyScreenshotIdentity,
        uploadSha256: String,
    ) {
        val latest = readLatestAsset(identity) ?: return
        if (latest.identityOrNull() != identity || latest.sha256 != uploadSha256) return
        val result = try {
            cloudDataSource.upsert(latest)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchLobbyScreenshotAssetCloudResult.Failed(
                MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED,
            )
        }
        if (result is MatchLobbyScreenshotAssetCloudResult.Failed) {
            markCloudFailure(identity, uploadSha256, result.failure.name)
        }
    }

    private suspend fun markCloudFailure(
        identity: MatchLobbyScreenshotIdentity,
        uploadSha256: String,
        failureCode: String,
    ) {
        val latest = readLatestAsset(identity) ?: return
        if (latest.sha256 != uploadSha256) return
        val failedAt = clock.millis()
        val failed = latest.copy(
            uploadStatus = ScreenshotUploadStatus.FAILED.name,
            uploadFailureCode = failureCode,
            updatedAt = failedAt,
            revision = latest.revision + 1L,
        )
        assetRepository.saveOrReplace(failed)
    }

    private suspend fun readLatestAsset(
        identity: MatchLobbyScreenshotIdentity,
    ): MatchLobbyScreenshotAssetEntity? = try {
        assetRepository.getByIdentity(identity)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private fun assetCreatedAt(asset: MatchLobbyScreenshotAssetEntity): Long = asset.createdAt

    private fun mergeTransientState(state: MatchLobbyScreenshotIntakeUiState): MatchLobbyScreenshotIntakeUiState {
        val current = _uiState.value
        if (current.tournamentId != state.tournamentId || current.matchId != state.matchId) return state
        return state.copy(
            slots = state.slots.map { restored ->
                val transient = current.slot(restored.index)
                if (transient?.isBusy == true) transient else restored
            },
            pendingCropNavigationSlotIndex = current.pendingCropNavigationSlotIndex,
            isLobbyTemplateMutationInProgress = current.isLobbyTemplateMutationInProgress,
            lobbyTemplateSaveStatus = current.lobbyTemplateSaveStatus,
        )
    }

    private fun updateSlot(index: Int, transform: (MatchLobbyScreenshotSlotUiState) -> MatchLobbyScreenshotSlotUiState) {
        _uiState.update { it.replaceSlot(index, transform) }
    }

    private fun markMissingIfNeeded(tournamentId: String, matchId: String, index: Int) {
        val key = "$tournamentId:$matchId:$index"
        if (!missingMarked.add(key)) return
        viewModelScope.launch {
            runCatching {
                assetRepository.markLocalMissing(MatchLobbyScreenshotIdentity(tournamentId, matchId, index), clock.millis())
            }
        }
    }

    private fun MatchLobbyScreenshotAssetEntity.toUiState(index: Int): MatchLobbyScreenshotSlotUiState {
        val file = localImagePreserver.resolveRelativePath(localRelativePath)
        val exists = file?.let { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) } == true
        val crop = confirmedLobbyCropOrNull()
        return MatchLobbyScreenshotSlotUiState(
            index = index,
            selectedScreenshotUri = if (exists) file?.toURI()?.toString() else null,
            selectedScreenshotMimeType = mimeType,
            selectedScreenshotWidth = originalWidth,
            selectedScreenshotHeight = originalHeight,
            fingerprint = sha256,
            localRelativePath = localRelativePath,
            hasLinkedAsset = true,
            isLocalFileMissing = !exists,
            confirmedCrop = crop,
            cropProfileId = cropProfileId,
        )
    }

    private fun MatchLobbyScreenshotAssetEntity.confirmedLobbyCropOrNull() =
        if (cropProfileId != com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles.Lobby.id) {
            null
        } else {
            val values = listOf(cropLeft, cropTop, cropRight, cropBottom)
            if (values.any { it == null }) null else runCatching {
                com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect(
                    cropLeft!!,
                    cropTop!!,
                    cropRight!!,
                    cropBottom!!,
                )
            }.getOrNull()
        }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }

}
