package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.local.NoOpRosterScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.RosterScreenshotAssociationSaveResult
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.RosterScreenshotValidationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RosterScreenshotIntakeViewModel @Inject constructor(
    private val imageCandidateValidator: ImageCandidateValidator,
    private val fingerprintGenerator: ImageSourceFingerprintGenerator,
    private val rosterScreenshotMetadataRepository: RosterScreenshotMetadataRepository =
        NoOpRosterScreenshotMetadataRepository(),
    private val rosterScreenshotLocalImageStore: RosterScreenshotLocalImageStore =
        NoOpRosterScreenshotLocalImageStore(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(RosterScreenshotIntakeUiState())
    val uiState: StateFlow<RosterScreenshotIntakeUiState> = _uiState.asStateFlow()
    private var loadedTournamentId: String? = null
    private var restoreJob: Job? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        restoreJob?.cancel()
        _uiState.value = if (tournamentId.isBlank()) {
            RosterScreenshotIntakeUiState(
                intakeError = RosterScreenshotIntakeError.MISSING_TOURNAMENT_ID,
            )
        } else {
            RosterScreenshotIntakeUiState(tournamentId = tournamentId)
        }
        if (tournamentId.isBlank()) return
        restoreJob = viewModelScope.launch {
            rosterScreenshotMetadataRepository.observeByTournamentId(tournamentId).collect { metadata ->
                _uiState.update { current ->
                    if (current.tournamentId != tournamentId) {
                        current
                    } else {
                        current.copy(
                            slots = defaultRosterScreenshotSlots().map { slot ->
                                metadata.firstOrNull { it.rosterScreenshotIndex == slot.index }
                                    ?.toUiState(slot.index)
                                    ?: slot
                            },
                        )
                    }
                }
            }
        }
    }

    fun requestPhotoPicker(slotIndex: Int) {
        val current = _uiState.value
        if (current.tournamentId.isNullOrBlank()) {
            _uiState.update {
                it.copy(intakeError = RosterScreenshotIntakeError.MISSING_TOURNAMENT_ID)
            }
            return
        }
        if (slotIndex !in 1..RosterScreenshotIntakeUiState.REQUIRED_SCREENSHOT_COUNT) return
        if (current.isPhotoPickerLaunchPending || current.activePhotoPickerSlotIndex != null) return

        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = true,
                activePhotoPickerSlotIndex = slotIndex,
                intakeError = null,
            )
        }
    }

    fun onPhotoPickerLaunchHandled() {
        _uiState.update { it.copy(isPhotoPickerLaunchPending = false) }
    }

    fun onPhotoPickerLaunchFailed() {
        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = false,
                activePhotoPickerSlotIndex = null,
                intakeError = RosterScreenshotIntakeError.PHOTO_PICKER_LAUNCH_FAILED,
            )
        }
    }

    fun onPhotoPickerResult(selectedUri: String?) {
        val slotIndex = _uiState.value.activePhotoPickerSlotIndex ?: return
        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = false,
                activePhotoPickerSlotIndex = null,
                intakeError = null,
            )
        }
        if (selectedUri.isNullOrBlank()) return

        updateSlot(slotIndex) { slot ->
            slot.copy(
                isValidationInProgress = true,
                lastValidationError = null,
                duplicateSelectionState = null,
            )
        }
        viewModelScope.launch {
            when (val validation = imageCandidateValidator.validate(selectedUri)) {
                is ImageCandidateValidationResult.Invalid -> updateSlot(slotIndex) { slot ->
                    slot.copy(
                        isValidationInProgress = false,
                        lastValidationError = validation.error,
                        duplicateSelectionState = null,
                    )
                }

                ImageCandidateValidationResult.Valid -> handleValidatedSelection(slotIndex, selectedUri)
            }
        }
    }

    fun removeSelectedImage(slotIndex: Int) {
        if (slotIndex !in 1..RosterScreenshotIntakeUiState.REQUIRED_SCREENSHOT_COUNT) return
        val tournamentId = _uiState.value.tournamentId ?: return
        updateSlot(slotIndex) { RosterScreenshotSlotUiState(index = it.index) }
        viewModelScope.launch {
            rosterScreenshotMetadataRepository.deleteByTournamentAndIndex(tournamentId, slotIndex)
            rosterScreenshotLocalImageStore.cleanup(tournamentId, slotIndex)
        }
    }

    fun onCropCoordinateChanged(
        slotIndex: Int,
        coordinate: RosterScreenshotCropCoordinate,
        value: String,
    ) {
        updateSlotIfSelected(slotIndex) { slot ->
            slot.copy(
                cropDraft = slot.cropDraft.withValue(coordinate, value),
                cropError = null,
            )
        }
    }

    fun setCrop(slotIndex: Int) {
        val slot = _uiState.value.slots.firstOrNull { it.index == slotIndex } ?: return
        if (!slot.hasValidatedImage) {
            updateSlot(slotIndex) {
                it.copy(cropError = RosterScreenshotCropError.MISSING_SELECTED_IMAGE)
            }
            return
        }
        val crop = slot.cropDraft.toNormalizedCropRectOrNull()
        if (crop == null) {
            updateSlot(slotIndex) {
                it.copy(cropError = RosterScreenshotCropError.INVALID_NUMBER)
            }
            return
        }
        when (val validation = NormalizedCropRectValidator.validate(crop)) {
            is RosterScreenshotCropValidationResult.Valid -> updateSlot(slotIndex) {
                it.copy(
                    cropState = RosterScreenshotCropState.Set(validation.crop),
                    cropError = null,
                    associationUpdatedAt = System.currentTimeMillis(),
                )
            }.also { persistCurrentSlot(slotIndex) }

            is RosterScreenshotCropValidationResult.Invalid -> updateSlot(slotIndex) {
                it.copy(cropError = validation.error.toRosterScreenshotCropError())
            }
        }
    }

    fun clearCrop(slotIndex: Int) {
        updateSlotIfSelected(slotIndex) { slot ->
            slot.copy(
                cropDraft = RosterScreenshotCropDraft(),
                cropState = RosterScreenshotCropState.NotSet,
                cropError = null,
                associationUpdatedAt = System.currentTimeMillis(),
            )
        }
        persistCurrentSlot(slotIndex)
    }

    private suspend fun handleValidatedSelection(
        slotIndex: Int,
        selectedUri: String,
    ) {
        val metadata = imageCandidateValidator.readValidMetadata(selectedUri)
        if (metadata == null) {
            updateSlot(slotIndex) { slot ->
                slot.copy(
                    isValidationInProgress = false,
                    lastValidationError = ImageValidationError.UNREADABLE_URI,
                )
            }
            return
        }
        val mimeType = metadata.mimeType ?: run {
            updateSlot(slotIndex) { slot ->
                slot.copy(
                    isValidationInProgress = false,
                    lastValidationError = ImageValidationError.NON_IMAGE_CONTENT,
                )
            }
            return
        }
        val fingerprint = when (val result = fingerprintGenerator.fingerprint(selectedUri)) {
            is ImageSourceFingerprintResult.Success -> result.value
            ImageSourceFingerprintResult.Failure -> {
                updateSlot(slotIndex) { slot ->
                    slot.copy(
                        isValidationInProgress = false,
                        lastValidationError = ImageValidationError.UNREADABLE_URI,
                    )
                }
                return
            }
        }
        val duplicateSlot = _uiState.value.slots.firstOrNull { slot ->
            slot.index != slotIndex && slot.selectedImageFingerprint == fingerprint
        }
        if (duplicateSlot != null) {
            updateSlot(slotIndex) { slot ->
                slot.copy(
                    isValidationInProgress = false,
                    duplicateSelectionState = RosterScreenshotDuplicateSelectionState
                        .SELECTED_FOR_ANOTHER_ROSTER_SCREENSHOT,
                )
            }
            return
        }

        val tournamentId = _uiState.value.tournamentId ?: return
        when (val preservation = rosterScreenshotLocalImageStore.preserve(tournamentId, slotIndex, selectedUri)) {
            RosterScreenshotLocalImageStoreResult.Failed -> updateSlot(slotIndex) { slot ->
                slot.copy(
                    isValidationInProgress = false,
                    lastValidationError = ImageValidationError.UNREADABLE_URI,
                )
            }

            is RosterScreenshotLocalImageStoreResult.Preserved -> {
                val now = System.currentTimeMillis()
                val existing = _uiState.value.slots.firstOrNull { it.index == slotIndex }
                val association = RosterScreenshotMetadataEntity(
                    tournamentId = tournamentId,
                    rosterScreenshotIndex = slotIndex,
                    localRelativePath = preservation.localRelativePath,
                    mimeType = mimeType,
                    width = metadata.width,
                    height = metadata.height,
                    sha256 = fingerprint,
                    validationStatus = RosterScreenshotValidationStatus.VALID.name,
                    cropLeft = null,
                    cropTop = null,
                    cropRight = null,
                    cropBottom = null,
                    createdAt = existing?.associationCreatedAt ?: now,
                    updatedAt = now,
                )
                when (rosterScreenshotMetadataRepository.saveOrReplace(association)) {
                    RosterScreenshotAssociationSaveResult.Saved -> updateSlot(slotIndex) { slot ->
                        slot.copy(
                            selectedImageUri = preservation.displayUri,
                            isSelectedImageValidated = true,
                            selectedImageMimeType = mimeType,
                            selectedImageWidth = metadata.width,
                            selectedImageHeight = metadata.height,
                            selectedImageFingerprint = fingerprint,
                            persistedLocalRelativePath = preservation.localRelativePath.takeIf { it.isNotBlank() },
                            associationCreatedAt = association.createdAt,
                            associationUpdatedAt = association.updatedAt,
                            isValidationInProgress = false,
                            lastValidationError = null,
                            duplicateSelectionState = null,
                            cropDraft = RosterScreenshotCropDraft(),
                            cropState = RosterScreenshotCropState.NotSet,
                            cropError = null,
                        )
                    }

                    RosterScreenshotAssociationSaveResult.DuplicateFingerprint -> {
                        rosterScreenshotLocalImageStore.cleanup(tournamentId, slotIndex)
                        updateSlot(slotIndex) { slot ->
                            slot.copy(
                                isValidationInProgress = false,
                                duplicateSelectionState = RosterScreenshotDuplicateSelectionState
                                    .SELECTED_FOR_ANOTHER_ROSTER_SCREENSHOT,
                            )
                        }
                    }

                    RosterScreenshotAssociationSaveResult.InvalidIndex -> updateSlot(slotIndex) { slot ->
                        slot.copy(
                            isValidationInProgress = false,
                            lastValidationError = ImageValidationError.UNREADABLE_URI,
                        )
                    }
                }
            }
        }
    }

    private fun updateSlotIfSelected(
        slotIndex: Int,
        transform: (RosterScreenshotSlotUiState) -> RosterScreenshotSlotUiState,
    ) {
        val slot = _uiState.value.slots.firstOrNull { it.index == slotIndex } ?: return
        if (!slot.hasValidatedImage) {
            updateSlot(slotIndex) {
                it.copy(cropError = RosterScreenshotCropError.MISSING_SELECTED_IMAGE)
            }
            return
        }
        updateSlot(slotIndex, transform)
    }

    private fun updateSlot(
        slotIndex: Int,
        transform: (RosterScreenshotSlotUiState) -> RosterScreenshotSlotUiState,
    ) {
        _uiState.update { current ->
            current.copy(
                slots = current.slots.map { slot ->
                    if (slot.index == slotIndex) transform(slot) else slot
                },
            )
        }
    }

    private fun persistCurrentSlot(slotIndex: Int) {
        val tournamentId = _uiState.value.tournamentId ?: return
        val slot = _uiState.value.slots.firstOrNull { it.index == slotIndex } ?: return
        val localRelativePath = slot.persistedLocalRelativePath ?: return
        val fingerprint = slot.selectedImageFingerprint ?: return
        val mimeType = slot.selectedImageMimeType ?: return
        val width = slot.selectedImageWidth ?: return
        val height = slot.selectedImageHeight ?: return
        val createdAt = slot.associationCreatedAt ?: return
        val crop = (slot.cropState as? RosterScreenshotCropState.Set)?.crop
        val updatedAt = slot.associationUpdatedAt ?: System.currentTimeMillis()
        viewModelScope.launch {
            rosterScreenshotMetadataRepository.saveOrReplace(
                RosterScreenshotMetadataEntity(
                    tournamentId = tournamentId,
                    rosterScreenshotIndex = slotIndex,
                    localRelativePath = localRelativePath,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    sha256 = fingerprint,
                    validationStatus = RosterScreenshotValidationStatus.VALID.name,
                    cropLeft = crop?.left,
                    cropTop = crop?.top,
                    cropRight = crop?.right,
                    cropBottom = crop?.bottom,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        }
    }

    private fun RosterScreenshotMetadataEntity.toUiState(index: Int): RosterScreenshotSlotUiState {
        val crop = normalizedCropOrNull()
        val displayUri = rosterScreenshotLocalImageStore.displayUriOrNull(localRelativePath)
        if (displayUri == null || validationStatus != RosterScreenshotValidationStatus.VALID.name) {
            return RosterScreenshotSlotUiState(
                index = index,
                lastValidationError = ImageValidationError.UNREADABLE_URI,
            )
        }
        return RosterScreenshotSlotUiState(
            index = index,
            selectedImageUri = displayUri,
            isSelectedImageValidated = true,
            selectedImageMimeType = mimeType,
            selectedImageWidth = width,
            selectedImageHeight = height,
            selectedImageFingerprint = sha256,
            persistedLocalRelativePath = localRelativePath,
            associationCreatedAt = createdAt,
            associationUpdatedAt = updatedAt,
            cropDraft = crop?.toDraft() ?: RosterScreenshotCropDraft(),
            cropState = crop?.let(RosterScreenshotCropState::Set) ?: RosterScreenshotCropState.NotSet,
        )
    }

    private fun RosterScreenshotMetadataEntity.normalizedCropOrNull(): NormalizedCropRect? {
        val crop = NormalizedCropRect(
            left = cropLeft ?: return null,
            top = cropTop ?: return null,
            right = cropRight ?: return null,
            bottom = cropBottom ?: return null,
        )
        return when (NormalizedCropRectValidator.validate(crop)) {
            is RosterScreenshotCropValidationResult.Valid -> crop
            is RosterScreenshotCropValidationResult.Invalid -> null
        }
    }

    private fun NormalizedCropRect.toDraft(): RosterScreenshotCropDraft = RosterScreenshotCropDraft(
        left = left.toString(),
        top = top.toString(),
        right = right.toString(),
        bottom = bottom.toString(),
    )
}
