package com.hoggamers.rankforge.presentation.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteAction
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteFailure
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteResult
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveFailure
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveRequest
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveResult
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreResult
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryResult
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorDetector
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorDetectionResult
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridBuilder
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridOverrides
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEditableGridInitializer
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrRunner
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrSource
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRawOcrDocument
import com.hoggamers.rankforge.domain.ocr.customdesign.resolveCustomDesignEffectiveGridGeometry
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
    private val customDesignGridBuilder: CustomDesignGridBuilder,
    private val customDesignSaveAction: CustomDesignSaveAction,
    private val customDesignRestoreAction: CustomDesignRestoreAction,
    private val customDesignDeleteAction: CustomDesignDeleteAction,
    private val customDesignSavedIdDiscoveryAction: CustomDesignSavedIdDiscoveryAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomDesignSetupUiState())
    val uiState: StateFlow<CustomDesignSetupUiState> = _uiState.asStateFlow()

    private var imageValidationJob: Job? = null
    private var imageValidationGeneration = 0L
    private var ocrJob: Job? = null
    private var rawOcrDocument: CustomDesignRawOcrDocument? = null
    private var ocrGeneration = 0L
    private var saveJob: Job? = null
    private var saveGeneration = 0L
    private var restoreJob: Job? = null
    private var restoreGeneration = 0L
    private var deleteJob: Job? = null
    private var deleteGeneration = 0L
    private var discoveryJob: Job? = null

    init {
        discoverAndRestoreSavedCustomDesign()
    }

    private fun discoverAndRestoreSavedCustomDesign() {
        discoveryJob = viewModelScope.launch {
            val result = try {
                customDesignSavedIdDiscoveryAction.find()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (_uiState.value != CustomDesignSetupUiState()) return@launch
            if (result is CustomDesignSavedIdDiscoveryResult.Found) {
                restoreSavedCustomDesign(result.customDesignId)
            }
        }
    }

    fun onSaveActionRequested() {
        val state = _uiState.value
        if (state.selectedImageReference == null ||
            state.savedCustomDesignId != null ||
            state.saveStatus == CustomDesignSaveStatus.SAVING ||
            state.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            state.deleteStatus == CustomDesignDeleteStatus.DELETING ||
            state.isImageValidationInProgress ||
            state.isPhotoPickerLaunchPending
        ) return

        saveNewCustomDesign()
    }

    fun saveNewCustomDesign() {
        val state = _uiState.value
        if (state.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            state.deleteStatus == CustomDesignDeleteStatus.DELETING ||
            state.saveStatus == CustomDesignSaveStatus.SAVING ||
            state.savedCustomDesignId != null ||
            state.isImageValidationInProgress ||
            state.isPhotoPickerLaunchPending
        ) return
        val draft = state.draft
        val currentSourceWidth = state.sourceImageWidth
        val currentSourceHeight = state.sourceImageHeight
        if (draft == null || currentSourceWidth == null || currentSourceHeight == null) {
            _uiState.update { it.copy(saveStatus = CustomDesignSaveStatus.FAILED) }
            return
        }
        val request = CustomDesignSaveRequest(
            imageReference = draft.imageReference,
            draftSourceWidth = draft.imageWidth,
            draftSourceHeight = draft.imageHeight,
            currentSourceWidth = currentSourceWidth,
            currentSourceHeight = currentSourceHeight,
            labels = CustomDesignOcrLabels(
                teamName = draft.teamNameLabel,
                win = draft.winLabel,
                totalKills = draft.totalKillsLabel,
                positionPoints = draft.positionPointsLabel,
                totalPoints = draft.totalPointsLabel,
            ),
            effectiveGridGeometry = resolveCustomDesignEffectiveGridGeometry(
                editable = state.editableGridGeometry,
                overrides = state.manualGridOverrides,
            ),
        )
        val generation = ++saveGeneration
        _uiState.update { it.copy(saveStatus = CustomDesignSaveStatus.SAVING) }
        saveJob = viewModelScope.launch {
            val result = try {
                customDesignSaveAction.save(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                CustomDesignSaveResult.Failed(CustomDesignSaveFailure.DATABASE_INSERT)
            }
            if (generation != saveGeneration) return@launch
            _uiState.update {
                when (result) {
                    is CustomDesignSaveResult.Success -> it.copy(
                        saveStatus = CustomDesignSaveStatus.SAVED,
                        savedCustomDesignId = result.customDesignId,
                    )
                    is CustomDesignSaveResult.Failed -> it.copy(
                        saveStatus = CustomDesignSaveStatus.FAILED,
                    )
                }
            }
        }
    }

    fun onSaveSuccessMessageHandled() {
        if (_uiState.value.saveStatus == CustomDesignSaveStatus.SAVED) {
            _uiState.update { it.copy(saveStatus = CustomDesignSaveStatus.IDLE) }
        }
    }

    fun restoreSavedCustomDesign(customDesignId: String) {
        if (customDesignId.isBlank() ||
            _uiState.value.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            _uiState.value.deleteStatus == CustomDesignDeleteStatus.DELETING ||
            _uiState.value.savedCustomDesignId != null
        ) return
        imageValidationJob?.cancel()
        ocrJob?.cancel()
        saveJob?.cancel()
        imageValidationGeneration += 1
        ocrGeneration += 1
        saveGeneration += 1
        rawOcrDocument = null
        val generation = ++restoreGeneration
        _uiState.update { it.copy(restoreStatus = CustomDesignRestoreStatus.RESTORING) }
        restoreJob = viewModelScope.launch {
            val result = try {
                customDesignRestoreAction.restore(customDesignId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (generation != restoreGeneration) return@launch
            when (result) {
                is CustomDesignRestoreResult.Success -> {
                    val design = result.design
                    val editable = CustomDesignEditableGridInitializer.initialize(
                        sourceWidth = design.sourceWidth,
                        sourceHeight = design.sourceHeight,
                        automatic = null,
                    ) ?: return@launch
                    rawOcrDocument = null
                    _uiState.update {
                        it.copy(
                            teamNameLabel = design.labels.teamName,
                            winLabel = design.labels.win,
                            totalKillsLabel = design.labels.totalKills,
                            positionPointsLabel = design.labels.positionPoints,
                            totalPointsLabel = design.labels.totalPoints,
                            selectedImageReference = design.localImageReference,
                            sourceImageWidth = design.sourceWidth,
                            sourceImageHeight = design.sourceHeight,
                            draft = CustomDesignDraft(
                                imageReference = design.localImageReference,
                                imageWidth = design.sourceWidth,
                                imageHeight = design.sourceHeight,
                                teamNameLabel = design.labels.teamName,
                                winLabel = design.labels.win,
                                totalKillsLabel = design.labels.totalKills,
                                positionPointsLabel = design.labels.positionPoints,
                                totalPointsLabel = design.labels.totalPoints,
                            ),
                            validationErrors = emptySet(),
                            imageValidationError = null,
                            photoPickerError = null,
                            isPhotoPickerLaunchPending = false,
                            isImageValidationInProgress = false,
                            ocrStatus = CustomDesignOcrStatus.IDLE,
                            ocrAnchors = null,
                            gridGeometry = null,
                            editableGridGeometry = editable,
                            manualGridOverrides = CustomDesignGridOverrides(
                                columnX = design.geometry.columnX,
                                rowY = design.geometry.rowY,
                            ),
                            saveStatus = CustomDesignSaveStatus.SAVED,
                            savedCustomDesignId = design.customDesignId,
                            restoreStatus = CustomDesignRestoreStatus.RESTORED,
                            deleteStatus = CustomDesignDeleteStatus.IDLE,
                        )
                    }
                }
                is CustomDesignRestoreResult.Failed,
                null,
                -> _uiState.update { it.copy(restoreStatus = CustomDesignRestoreStatus.FAILED) }
            }
        }
    }

    fun deleteSavedCustomDesign() {
        val state = _uiState.value
        val customDesignId = state.savedCustomDesignId ?: return
        if (state.deleteStatus == CustomDesignDeleteStatus.DELETING ||
            state.saveStatus == CustomDesignSaveStatus.SAVING ||
            state.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            state.isImageValidationInProgress ||
            state.isPhotoPickerLaunchPending
        ) return

        imageValidationJob?.cancel()
        ocrJob?.cancel()
        imageValidationGeneration += 1
        ocrGeneration += 1
        saveGeneration += 1
        rawOcrDocument = null
        val generation = ++deleteGeneration
        _uiState.update { it.copy(deleteStatus = CustomDesignDeleteStatus.DELETING) }
        deleteJob = viewModelScope.launch {
            val result = try {
                customDesignDeleteAction.delete(customDesignId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.DATABASE_DELETE)
            }
            if (generation != deleteGeneration) return@launch
            when (result) {
                CustomDesignDeleteResult.Success -> _uiState.update {
                    it.copy(
                        teamNameLabel = "",
                        winLabel = "",
                        totalKillsLabel = "",
                        positionPointsLabel = "",
                        totalPointsLabel = "",
                        selectedImageReference = null,
                        sourceImageWidth = null,
                        sourceImageHeight = null,
                        draft = null,
                        validationErrors = emptySet(),
                        imageValidationError = null,
                        photoPickerError = null,
                        isPhotoPickerLaunchPending = false,
                        isImageValidationInProgress = false,
                        ocrStatus = CustomDesignOcrStatus.IDLE,
                        ocrAnchors = null,
                        gridGeometry = null,
                        editableGridGeometry = null,
                        manualGridOverrides = CustomDesignGridOverrides(),
                        saveStatus = CustomDesignSaveStatus.IDLE,
                        savedCustomDesignId = null,
                        restoreStatus = CustomDesignRestoreStatus.IDLE,
                        deleteStatus = CustomDesignDeleteStatus.DELETED,
                    )
                }
                is CustomDesignDeleteResult.Failed -> _uiState.update {
                    it.copy(deleteStatus = CustomDesignDeleteStatus.FAILED)
                }
            }
        }
    }

    fun onTeamNameChanged(value: String) {
        if (isRestoreLocked()) return
        updateLabels { it.copy(teamNameLabel = value) }
    }

    fun onWinChanged(value: String) {
        if (isRestoreLocked()) return
        updateLabels { it.copy(winLabel = value) }
    }

    fun onTotalKillsChanged(value: String) {
        if (isRestoreLocked()) return
        updateLabels { it.copy(totalKillsLabel = value) }
    }

    fun onPositionPointsChanged(value: String) {
        if (isRestoreLocked()) return
        updateLabels { it.copy(positionPointsLabel = value) }
    }

    fun onTotalPointsChanged(value: String) {
        if (isRestoreLocked()) return
        updateLabels { it.copy(totalPointsLabel = value) }
    }

    fun setManualColumnX(
        field: CustomDesignAnchorField,
        sourceX: Float,
    ) {
        if (isRestoreLocked()) return
        val sourceWidth = _uiState.value.sourceImageWidth ?: return
        if (!sourceX.isFinite() || sourceX !in 0f..sourceWidth.toFloat()) return
        _uiState.update { state ->
            val overrides = state.manualGridOverrides.copy(
                columnX = state.manualGridOverrides.columnX + (field to sourceX),
            )
            state.copy(manualGridOverrides = overrides)
        }
    }

    fun setManualRowY(
        rank: Int,
        sourceY: Float,
    ) {
        if (isRestoreLocked()) return
        val sourceHeight = _uiState.value.sourceImageHeight ?: return
        if (rank !in CUSTOM_DESIGN_RANK_RANGE ||
            !sourceY.isFinite() ||
            sourceY !in 0f..sourceHeight.toFloat()
        ) {
            return
        }
        _uiState.update { state ->
            val overrides = state.manualGridOverrides.copy(
                rowY = state.manualGridOverrides.rowY + (rank to sourceY),
            )
            state.copy(manualGridOverrides = overrides)
        }
    }

    fun clearManualColumnX(field: CustomDesignAnchorField) {
        if (isRestoreLocked()) return
        _uiState.update { state ->
            state.copy(
                manualGridOverrides = state.manualGridOverrides.copy(
                    columnX = state.manualGridOverrides.columnX - field,
                ),
            )
        }
    }

    fun clearManualRowY(rank: Int) {
        if (isRestoreLocked()) return
        _uiState.update { state ->
            state.copy(
                manualGridOverrides = state.manualGridOverrides.copy(
                    rowY = state.manualGridOverrides.rowY - rank,
                ),
            )
        }
    }

    fun clearManualGridOverrides() {
        if (isRestoreLocked()) return
        _uiState.update { it.copy(manualGridOverrides = CustomDesignGridOverrides()) }
    }

    fun requestPhotoPicker() {
        val currentState = _uiState.value
        if (currentState.savedCustomDesignId != null ||
            currentState.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            currentState.deleteStatus == CustomDesignDeleteStatus.DELETING ||
            currentState.isPhotoPickerLaunchPending ||
            currentState.isImageValidationInProgress
        ) {
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
        if (isRestoreLocked()) return
        val uri = selectedUri?.takeIf { it.isNotBlank() } ?: return
        imageValidationJob?.cancel()
        val validationGeneration = ++imageValidationGeneration
        saveJob?.cancel()
        saveGeneration += 1
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
            if (validationGeneration != imageValidationGeneration) return@launch
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
                ).withDraft().resetOcr(clearManualGridOverrides = true).copy(
                    saveStatus = CustomDesignSaveStatus.IDLE,
                    savedCustomDesignId = null,
                    restoreStatus = CustomDesignRestoreStatus.IDLE,
                    deleteStatus = CustomDesignDeleteStatus.IDLE,
                )
            }
            _uiState.value.draft?.let { draft -> startOcr(draft) }
        }
    }

    private fun updateLabels(transform: (CustomDesignSetupUiState) -> CustomDesignSetupUiState) {
        if (_uiState.value.saveStatus == CustomDesignSaveStatus.SAVING) {
            saveJob?.cancel()
            saveGeneration += 1
        }
        _uiState.update { current ->
            val nextState = transform(current).copy(
                imageValidationError = null,
                photoPickerError = null,
                restoreStatus = CustomDesignRestoreStatus.IDLE,
            ).withDraft()
            val nextStateWithSaveStatus = if (current.savedCustomDesignId == null) {
                nextState.copy(saveStatus = CustomDesignSaveStatus.IDLE)
            } else {
                nextState.copy(saveStatus = CustomDesignSaveStatus.SAVED)
            }
            if (nextState.draft == null) {
                nextStateWithSaveStatus.resetOcr(clearManualGridOverrides = false)
            } else {
                nextStateWithSaveStatus
            }
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

    private fun isRestoreLocked(): Boolean =
        _uiState.value.savedCustomDesignId != null ||
            _uiState.value.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            _uiState.value.deleteStatus == CustomDesignDeleteStatus.DELETING

    private fun startOcr(draft: CustomDesignDraft) {
        ocrJob?.cancel()
        ocrGeneration += 1
        val generation = ocrGeneration
        rawOcrDocument = null
        _uiState.update {
            it.copy(
                ocrStatus = CustomDesignOcrStatus.PROCESSING,
                ocrAnchors = null,
                gridGeometry = null,
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
                            gridGeometry = null,
                            editableGridGeometry = CustomDesignEditableGridInitializer.initialize(
                                sourceWidth = draft.imageWidth,
                                sourceHeight = draft.imageHeight,
                                automatic = null,
                            ),
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
        val gridGeometry = customDesignGridBuilder.build(detection)
        logDetection(detection)
        logGridGeometry(gridGeometry)
        _uiState.update {
            it.copy(
                ocrStatus = CustomDesignOcrStatus.COMPLETED,
                ocrAnchors = detection.anchors,
                gridGeometry = gridGeometry,
                editableGridGeometry = CustomDesignEditableGridInitializer.initialize(
                    sourceWidth = draft.imageWidth,
                    sourceHeight = draft.imageHeight,
                    automatic = gridGeometry,
                ),
            )
        }
    }

    private fun CustomDesignSetupUiState.resetOcr(
        clearManualGridOverrides: Boolean,
    ): CustomDesignSetupUiState {
        ocrJob?.cancel()
        ocrGeneration += 1
        if (clearManualGridOverrides) {
            rawOcrDocument = null
        }
        return copy(
            ocrStatus = CustomDesignOcrStatus.IDLE,
            ocrAnchors = null,
            gridGeometry = null,
            editableGridGeometry = CustomDesignEditableGridInitializer.initialize(
                sourceWidth = sourceImageWidth ?: 0,
                sourceHeight = sourceImageHeight ?: 0,
                automatic = null,
            ),
            manualGridOverrides = if (clearManualGridOverrides) {
                CustomDesignGridOverrides()
            } else {
                manualGridOverrides
            },
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

    private fun logGridGeometry(geometry: CustomDesignGridGeometry) {
        if (!BuildConfig.DEBUG) return
        debugLog("GRID rowStep=${geometry.estimatedRowStep}")
        (1..12).forEach { rank ->
            val row = geometry.rowY[rank]
            if (row == null) {
                debugLog("GRID ROW $rank UNRESOLVED")
            } else {
                debugLog("GRID ROW $rank y=${row.y} source=${row.source}")
            }
        }
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
