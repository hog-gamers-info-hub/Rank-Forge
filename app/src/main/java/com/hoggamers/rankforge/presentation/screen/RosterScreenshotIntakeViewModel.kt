package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RosterScreenshotIntakeViewModel @Inject constructor(
    private val imageCandidateValidator: ImageCandidateValidator,
    private val fingerprintGenerator: ImageSourceFingerprintGenerator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RosterScreenshotIntakeUiState())
    val uiState: StateFlow<RosterScreenshotIntakeUiState> = _uiState.asStateFlow()
    private var loadedTournamentId: String? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        _uiState.value = if (tournamentId.isBlank()) {
            RosterScreenshotIntakeUiState(
                intakeError = RosterScreenshotIntakeError.MISSING_TOURNAMENT_ID,
            )
        } else {
            RosterScreenshotIntakeUiState(tournamentId = tournamentId)
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
        updateSlot(slotIndex) { RosterScreenshotSlotUiState(index = it.index) }
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

        updateSlot(slotIndex) { slot ->
            slot.copy(
                selectedImageUri = selectedUri,
                isSelectedImageValidated = true,
                selectedImageMimeType = metadata.mimeType,
                selectedImageWidth = metadata.width,
                selectedImageHeight = metadata.height,
                selectedImageFingerprint = fingerprint,
                isValidationInProgress = false,
                lastValidationError = null,
                duplicateSelectionState = null,
            )
        }
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
}
