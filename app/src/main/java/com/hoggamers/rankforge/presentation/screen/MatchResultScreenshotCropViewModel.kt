package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.NoOpMatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.NoOpMatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.NoOpMatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
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
    private val cloudDataSource: MatchResultScreenshotAssetCloudDataSource =
        NoOpMatchResultScreenshotAssetCloudDataSource(),
    private val localImagePreserver: LocalImagePreserver,
    private val clock: Clock = Clock.systemUTC(),
    private val storageUploader: MatchResultScreenshotStorageUploader = NoOpMatchResultScreenshotStorageUploader(),
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
                    uploadAndSyncCheckpoint(identity)
                    viewModelScope.launch {
                        AndroidMatchResultOcrPreviewProcessor(
                            assetRepository = assetRepository,
                            localFileResolver = MatchResultOcrPreviewLocalFileResolver(
                                localImagePreserver::resolveRelativePath,
                            ),
                        ).processAndLog(identity)
                    }
                    draftEdited = false
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            confirmedCrop = current.draftCrop,
                            error = null,
                        )
                    }
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

    private suspend fun uploadAndSyncCheckpoint(identity: MatchResultScreenshotIdentity) {
        val asset = try {
            assetRepository.getByIdentity(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return

        if (asset.identityOrNull() != identity) return
        val submittedSha256 = asset.sha256
        val localFile = localImagePreserver.resolveRelativePath(asset.localRelativePath)
        val readable = localFile?.let { runCatching { it.isFile && it.canRead() && it.length() > 0L }.getOrDefault(false) } == true
        if (!readable) {
            markUploadFailure(identity, submittedSha256, MatchResultScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED.name)
            return
        }

        val storageResult = if (
            asset.uploadStatus == ScreenshotUploadStatus.UPLOADED.name &&
            !asset.storageBucket.isNullOrBlank() &&
            !asset.storageObjectPath.isNullOrBlank()
        ) {
            null
        } else {
            try {
                storageUploader.upload(
                    tournamentId = identity.tournamentId,
                    matchId = identity.matchId,
                    role = identity.role,
                    localFile = localFile,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchResultScreenshotStorageUploadResult.Failed(
                    MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED,
                )
            }
        }

        when (storageResult) {
            is MatchResultScreenshotStorageUploadResult.Failed -> {
                markUploadFailure(identity, submittedSha256, storageResult.failure.name)
                return
            }
            is MatchResultScreenshotStorageUploadResult.Uploaded -> {
                val latest = readLatestAsset(identity)
                if (latest?.identityOrNull() != identity || latest.sha256 != submittedSha256) return
                val uploadedAt = clock.millis()
                val uploaded = latest.copy(
                    storageBucket = com.hoggamers.rankforge.data.cloud.OCR_SCREENSHOTS_BUCKET,
                    storageObjectPath = storageResult.objectPath,
                    uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                    uploadFailureCode = null,
                    uploadedAt = uploadedAt,
                    updatedAt = uploadedAt,
                    revision = latest.revision + 1,
                )
                if (assetRepository.saveOrReplace(uploaded) !is MatchResultScreenshotAssetSaveResult.Saved) return
            }
            null -> Unit
        }

        val updated = readLatestAsset(identity)
        if (updated?.identityOrNull() != identity || updated.sha256 != submittedSha256) return
        val cloudResult = try {
            cloudDataSource.upsert(updated)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchResultScreenshotAssetCloudResult.Failed(MatchResultScreenshotAssetCloudFailure.WRITE_FAILED)
        }
        if (cloudResult is MatchResultScreenshotAssetCloudResult.Failed) {
            markUploadFailure(identity, submittedSha256, cloudResult.failure.name)
        }
    }

    private suspend fun markUploadFailure(
        identity: MatchResultScreenshotIdentity,
        submittedSha256: String,
        failureCode: String,
    ) {
        val latest = readLatestAsset(identity) ?: return
        if (latest.identityOrNull() != identity || latest.sha256 != submittedSha256) return
        val failedAt = clock.millis()
        try {
            assetRepository.saveOrReplace(
                latest.copy(
                    uploadStatus = ScreenshotUploadStatus.FAILED.name,
                    uploadFailureCode = failureCode,
                    updatedAt = failedAt,
                    revision = latest.revision + 1,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Cloud failure must not prevent confirmed crop navigation.
        }
    }

    private suspend fun readLatestAsset(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultScreenshotAssetEntity? = try {
        assetRepository.getByIdentity(identity)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
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
