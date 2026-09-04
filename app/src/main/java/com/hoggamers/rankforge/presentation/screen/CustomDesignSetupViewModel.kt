package com.hoggamers.rankforge.presentation.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorDetector
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorDetectionResult
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrRunner
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrSource
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRawOcrDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CustomDesignSetupViewModel @Inject constructor(
    private val imageCandidateValidator: ImageCandidateValidator,
    private val customDesignOcrRunner: CustomDesignOcrRunner,
    private val customDesignAnchorDetector: CustomDesignAnchorDetector,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomDesignSetupUiState())
    val uiState: StateFlow<CustomDesignSetupUiState> = _uiState.asStateFlow()

    private var imageValidationJob: Job? = null
    private var ocrJob: Job? = null
    private var rawOcrDocument: CustomDesignRawOcrDocument? = null
    private var ocrGeneration = 0L

    fun onTeamNameChanged(value: String) {
        updateLabels { it.copy(teamNameLabel = value) }
    }

    fun onWinChanged(value: String) {
        updateLabels { it.copy(winLabel = value) }
    }

    fun onTotalKillsChanged(value: String) {
        updateLabels { it.copy(totalKillsLabel = value) }
    }

    fun onPositionPointsChanged(value: String) {
        updateLabels { it.copy(positionPointsLabel = value) }
    }

    fun onTotalPointsChanged(value: String) {
        updateLabels { it.copy(totalPointsLabel = value) }
    }

    fun requestPhotoPicker() {
        val currentState = _uiState.value
        if (currentState.isPhotoPickerLaunchPending || currentState.isImageValidationInProgress) {
            return
        }
        val validationErrors = currentState.requiredLabelErrors()
        _uiState.update {
            it.copy(
                validationErrors = validationErrors,
                imageValidationError = null,
                photoPickerError = null,
                isPhotoPickerLaunchPending = validationErrors.isEmpty(),
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
                photoPickerError = PhotoPickerError.LAUNCH_FAILED,
            )
        }
    }

    fun onPhotoPickerResult(selectedUri: String?) {
        val uri = selectedUri?.takeIf { it.isNotBlank() } ?: return
        imageValidationJob?.cancel()
        _uiState.update {
            it.copy(
                isImageValidationInProgress = true,
                imageValidationError = null,
                photoPickerError = null,
            )
        }
        imageValidationJob = viewModelScope.launch {
            val validation = try {
                imageCandidateValidator.validate(uri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                ImageCandidateValidationResult.Invalid(ImageValidationError.DECODE_FAILED)
            }
            val metadata = if (validation == ImageCandidateValidationResult.Valid) {
                try {
                    imageCandidateValidator.readValidMetadata(uri)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            if (validation is ImageCandidateValidationResult.Invalid || metadata == null) {
                _uiState.update {
                    it.copy(
                        isImageValidationInProgress = false,
                        imageValidationError = (validation as? ImageCandidateValidationResult.Invalid)?.error
                            ?: ImageValidationError.DECODE_FAILED,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedImageReference = uri,
                    sourceImageWidth = metadata.width,
                    sourceImageHeight = metadata.height,
                    isImageValidationInProgress = false,
                    imageValidationError = null,
                ).withDraft().resetOcr()
            }
            _uiState.value.draft?.let { draft -> startOcr(draft) }
        }
    }

    private fun updateLabels(transform: (CustomDesignSetupUiState) -> CustomDesignSetupUiState) {
        _uiState.update { current ->
            val nextState = transform(current).copy(
                imageValidationError = null,
                photoPickerError = null,
            ).withDraft()
            if (nextState.draft == null) nextState.resetOcr() else nextState
        }
        val currentState = _uiState.value
        val cachedDocument = rawOcrDocument
        when {
            currentState.draft != null && cachedDocument != null -> {
                rematchCachedOcr(currentState.draft, cachedDocument)
            }
            currentState.draft != null && currentState.ocrStatus == CustomDesignOcrStatus.IDLE -> {
                startOcr(currentState.draft)
            }
        }
    }

    private fun startOcr(draft: CustomDesignDraft) {
        ocrJob?.cancel()
        ocrGeneration += 1
        val generation = ocrGeneration
        rawOcrDocument = null
        _uiState.update {
            it.copy(
                ocrStatus = CustomDesignOcrStatus.PROCESSING,
                ocrAnchors = null,
            )
        }
        ocrJob = viewModelScope.launch {
            try {
                val document = customDesignOcrRunner.recognize(
                    CustomDesignOcrSource(
                        imageReference = draft.imageReference,
                        sourceWidth = draft.imageWidth,
                        sourceHeight = draft.imageHeight,
                    ),
                )
                currentCoroutineContext().ensureActive()
                if (generation != ocrGeneration) return@launch
                if (
                    document.sourceWidth != draft.imageWidth ||
                    document.sourceHeight != draft.imageHeight
                ) {
                    throw IllegalStateException("Custom Design OCR source dimensions changed")
                }
                rawOcrDocument = document
                val currentDraft = _uiState.value.draft ?: return@launch
                if (!sameImage(currentDraft, draft)) return@launch
                rematchCachedOcr(currentDraft, document)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (generation == ocrGeneration && _uiState.value.draft?.let { sameImage(it, draft) } == true) {
                    _uiState.update {
                        it.copy(
                            ocrStatus = CustomDesignOcrStatus.FAILED,
                            ocrAnchors = null,
                        )
                    }
                }
            }
        }
    }

    private fun rematchCachedOcr(
        draft: CustomDesignDraft,
        document: CustomDesignRawOcrDocument,
    ) {
        if (!sameImage(draft, document)) return
        val detection = customDesignAnchorDetector.detectDetailed(
            sourceWidth = draft.imageWidth,
            sourceHeight = draft.imageHeight,
            labels = draft.ocrLabels(),
            blocks = document.blocks,
        )
        logDetection(detection)
        _uiState.update {
            it.copy(
                ocrStatus = CustomDesignOcrStatus.COMPLETED,
                ocrAnchors = detection.anchors,
            )
        }
    }

    private fun CustomDesignSetupUiState.resetOcr(): CustomDesignSetupUiState {
        ocrJob?.cancel()
        ocrGeneration += 1
        rawOcrDocument = null
        return copy(
            ocrStatus = CustomDesignOcrStatus.IDLE,
            ocrAnchors = null,
        )
    }

    private fun CustomDesignDraft.ocrLabels() = CustomDesignOcrLabels(
        teamName = teamNameLabel,
        win = winLabel,
        totalKills = totalKillsLabel,
        positionPoints = positionPointsLabel,
        totalPoints = totalPointsLabel,
    )

    private fun sameImage(left: CustomDesignDraft, right: CustomDesignDraft): Boolean =
        left.imageReference == right.imageReference &&
            left.imageWidth == right.imageWidth &&
            left.imageHeight == right.imageHeight

    private fun sameImage(draft: CustomDesignDraft, document: CustomDesignRawOcrDocument): Boolean =
        draft.imageWidth == document.sourceWidth && draft.imageHeight == document.sourceHeight

    private fun logDetection(
        detection: CustomDesignAnchorDetectionResult,
    ) {
        if (!BuildConfig.DEBUG) return
        val anchors = detection.anchors
        debugLog("SOURCE width=${anchors.sourceWidth} height=${anchors.sourceHeight}")
        CustomDesignAnchorField.entries.forEach { field ->
            when {
                field in detection.ambiguousFields -> debugLog("HEADER $field AMBIGUOUS")
                anchors.columnX[field] != null -> debugLog(
                    "HEADER $field centerX=${anchors.columnX[field]} centerY=${detection.headerCenterY[field]}",
                )
                else -> debugLog("HEADER $field NOT_FOUND")
            }
        }
        (1..12).forEach { rank ->
            when {
                rank in detection.ambiguousRanks -> debugLog("RANK $rank AMBIGUOUS")
                anchors.rowY[rank] != null -> debugLog("RANK $rank centerY=${anchors.rowY[rank]}")
                else -> debugLog("RANK $rank NOT_FOUND")
            }
        }
        debugLog("COLUMNS ${anchors.columnX}")
        debugLog("ROWS ${anchors.rowY}")
    }

    private fun debugLog(message: String) {
        try {
            Log.d(CUSTOM_DESIGN_OCR_LOG_TAG, message)
        } catch (_: RuntimeException) {
            // Android's Log is unavailable in JVM unit tests; diagnostics must never affect OCR state.
        }
    }

    private companion object {
        const val CUSTOM_DESIGN_OCR_LOG_TAG = "CustomDesignOCR"
    }

    private fun CustomDesignSetupUiState.requiredLabelErrors(): Set<CustomDesignLabelField> =
        buildSet {
            if (teamNameLabel.isBlank()) add(CustomDesignLabelField.TEAM_NAME)
            if (winLabel.isBlank()) add(CustomDesignLabelField.WIN)
            if (totalKillsLabel.isBlank()) add(CustomDesignLabelField.TOTAL_KILLS)
            if (positionPointsLabel.isBlank()) add(CustomDesignLabelField.POSITION_POINTS)
            if (totalPointsLabel.isBlank()) add(CustomDesignLabelField.TOTAL_POINTS)
        }

    private fun CustomDesignSetupUiState.withDraft(): CustomDesignSetupUiState {
        val imageReference = selectedImageReference
        val imageWidth = sourceImageWidth
        val imageHeight = sourceImageHeight
        val hasRequiredLabels = requiredLabelErrors().isEmpty()
        return copy(
            draft = if (
                imageReference != null &&
                imageWidth != null &&
                imageHeight != null &&
                hasRequiredLabels
            ) {
                CustomDesignDraft(
                    imageReference = imageReference,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    teamNameLabel = teamNameLabel,
                    winLabel = winLabel,
                    totalKillsLabel = totalKillsLabel,
                    positionPointsLabel = positionPointsLabel,
                    totalPointsLabel = totalPointsLabel,
                )
            } else {
                null
            },
        )
    }
}
