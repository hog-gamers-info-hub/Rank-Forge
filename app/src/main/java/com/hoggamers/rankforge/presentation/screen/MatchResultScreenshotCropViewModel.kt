package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.NoOpMatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultOcrPreviewProcessor
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewLocalFileResolver
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchResultScreenshotCropUiState())
    val uiState: StateFlow<MatchResultScreenshotCropUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedKey: String? = null
    private var draftEdited = false
    private val missingMarked = mutableSetOf<String>()

    fun load(tournamentId: String, matchId: String, roleName: String) {
        val key = "$tournamentId:$matchId:$roleName"
        if (loadedKey == key) return
        loadedKey = key
        draftEdited = false
        loadJob?.cancel()
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
            combine(
                assetRepository.observeByIdentity(identity),
                observeMatches(tournamentId),
            ) { asset, matches ->
                val match = matches.firstOrNull { it.id == matchId }
                if (match == null) {
                    MatchResultScreenshotCropUiState(
                        isLoading = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        role = role,
                        isFinalized = true,
                        error = MatchResultScreenshotCropError.SAVE_FAILED,
                    )
                } else if (asset == null) {
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
            }
        }
    }

    fun onCropChanged(crop: com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect) {
        draftEdited = true
        _uiState.update {
            it.copy(
                draftCrop = crop,
                error = if (it.error == MatchResultScreenshotCropError.INVALID_CROP) null else it.error,
            )
        }
    }

    fun confirmCrop(onConfirmed: () -> Unit) {
        val current = _uiState.value
        if (current.isSaving) return
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
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
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

            val identity = MatchResultScreenshotIdentity(
                tournamentId = tournamentId,
                matchId = matchId,
                role = role,
            )
            val existing = try {
                assetRepository.getByIdentity(identity)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (existing?.identityOrNull() != identity) {
                _uiState.update { it.copy(isSaving = false, error = MatchResultScreenshotCropError.INVALID_ROLE) }
                return@launch
            }
            val result = try {
                assetRepository.persistConfirmedCrop(
                    identity = identity,
                    crop = current.draftCrop,
                    updatedAt = clock.millis(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchResultScreenshotCropSaveResult.InvalidCrop
            }
            when (result) {
                MatchResultScreenshotCropSaveResult.Saved -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            confirmedCrop = current.draftCrop,
                            error = null,
                        )
                    }
                    reconciliationScheduler.schedule {
                        uploadCheckpoint.run(identity)
                    }
                    try {
                        AndroidMatchResultOcrPreviewProcessor(
                            assetRepository = assetRepository,
                            localFileResolver = MatchResultOcrPreviewLocalFileResolver(
                                localImagePreserver::resolveRelativePath,
                            ),
                        ).processAndLog(identity)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Calibration logging must not convert a saved crop into a save failure.
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
            }
        }
    }

    private fun MatchResultScreenshotAssetEntity.toCropUiState(
        role: MatchResultScreenshotRole,
        isFinalized: Boolean,
        previous: MatchResultScreenshotCropUiState,
    ): MatchResultScreenshotCropUiState {
        val file = localImagePreserver.resolveRelativePath(localRelativePath)
        val fileExists = file?.let { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) } == true
        val confirmedCrop = confirmedCropOrNull()
        val draft = if (draftEdited && previous.role == role) {
            previous.draftCrop
        } else {
            confirmedCrop ?: OcrVisualCropDefaults.FullImageCrop
        }
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
            error = if (fileExists) null else MatchResultScreenshotCropError.MISSING_LOCAL_FILE,
        )
    }

    private fun markMissingIfNeeded(identity: MatchResultScreenshotIdentity) {
        val key = "${identity.tournamentId}:${identity.matchId}:${identity.role.name}"
        if (!missingMarked.add(key)) return
        viewModelScope.launch {
            runCatching {
                assetRepository.markLocalMissing(identity, clock.millis())
            }
        }
    }

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
