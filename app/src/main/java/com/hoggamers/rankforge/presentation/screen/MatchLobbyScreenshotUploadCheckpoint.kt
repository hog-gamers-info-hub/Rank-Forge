package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploadFailure
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.NoOpMatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.NoOpMatchLobbyScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.OCR_SCREENSHOTS_BUCKET
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface MatchLobbyScreenshotUploadCheckpointResult {
    data object Completed : MatchLobbyScreenshotUploadCheckpointResult
    data object Skipped : MatchLobbyScreenshotUploadCheckpointResult
    data object Failed : MatchLobbyScreenshotUploadCheckpointResult
}

class MatchLobbyScreenshotUploadCheckpoint @Inject constructor(
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val clock: Clock,
    private val storageUploader: MatchLobbyScreenshotStorageUploader = NoOpMatchLobbyScreenshotStorageUploader(),
    private val cloudDataSource: MatchLobbyScreenshotAssetCloudDataSource = NoOpMatchLobbyScreenshotAssetCloudDataSource(),
) : MatchLobbyScreenshotUploadCheckpointAction {
    override suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult {
        val latest = readLatestAsset(identity) ?: return MatchLobbyScreenshotUploadCheckpointResult.Skipped
        if (latest.identityOrNull() != identity) return MatchLobbyScreenshotUploadCheckpointResult.Skipped
        val submittedSha256 = latest.sha256
        val localFile = localImagePreserver.resolveRelativePath(latest.localRelativePath)
        val readable = localFile?.let {
            runCatching { it.isFile && it.canRead() && it.length() > 0L }.getOrDefault(false)
        } == true
        if (!readable) {
            markUploadFailure(identity, submittedSha256, MatchLobbyScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED.name)
            return MatchLobbyScreenshotUploadCheckpointResult.Failed
        }

        val storageResult = if (
            latest.uploadStatus == ScreenshotUploadStatus.UPLOADED.name &&
            !latest.storageBucket.isNullOrBlank() &&
            !latest.storageObjectPath.isNullOrBlank()
        ) {
            null
        } else {
            try {
                storageUploader.upload(
                    tournamentId = identity.tournamentId,
                    matchId = identity.matchId,
                    lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
                    localFile = localFile,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchLobbyScreenshotStorageUploadResult.Failed(
                    MatchLobbyScreenshotStorageUploadFailure.UPLOAD_FAILED,
                )
            }
        }

        when (storageResult) {
            is MatchLobbyScreenshotStorageUploadResult.Failed -> {
                markUploadFailure(identity, submittedSha256, storageResult.failure.name)
                return MatchLobbyScreenshotUploadCheckpointResult.Failed
            }
            is MatchLobbyScreenshotStorageUploadResult.Uploaded -> {
                val current = readLatestAsset(identity)
                if (current?.identityOrNull() != identity || current.sha256 != submittedSha256) {
                    return MatchLobbyScreenshotUploadCheckpointResult.Skipped
                }
                val uploadedAt = clock.millis()
                val uploaded = current.copy(
                    storageBucket = OCR_SCREENSHOTS_BUCKET,
                    storageObjectPath = storageResult.objectPath,
                    uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                    uploadFailureCode = null,
                    uploadedAt = uploadedAt,
                    updatedAt = uploadedAt,
                    revision = current.revision + 1L,
                )
                if (assetRepository.saveOrReplace(uploaded) !is MatchLobbyScreenshotAssetSaveResult.Saved) {
                    return MatchLobbyScreenshotUploadCheckpointResult.Failed
                }
            }
            null -> Unit
        }

        val updated = readLatestAsset(identity)
        if (updated?.identityOrNull() != identity || updated.sha256 != submittedSha256) {
            return MatchLobbyScreenshotUploadCheckpointResult.Skipped
        }
        val cloudResult = try {
            cloudDataSource.upsert(updated)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchLobbyScreenshotAssetCloudResult.Failed(MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED)
        }
        if (cloudResult is MatchLobbyScreenshotAssetCloudResult.Failed) {
            markUploadFailure(identity, submittedSha256, cloudResult.failure.name)
            return MatchLobbyScreenshotUploadCheckpointResult.Failed
        }
        return MatchLobbyScreenshotUploadCheckpointResult.Completed
    }

    private suspend fun markUploadFailure(
        identity: MatchLobbyScreenshotIdentity,
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
                    revision = latest.revision + 1L,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // A failed checkpoint must not prevent the local workflow from continuing.
        }
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
}
