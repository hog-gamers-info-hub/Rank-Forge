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
import com.hoggamers.rankforge.data.local.ScreenshotCloudReconciliationCoordinator
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.io.File
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
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
) : MatchLobbyScreenshotUploadCheckpointAction {
    override suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult =
        ScreenshotCloudReconciliationCoordinator.withLock(
            ScreenshotCloudReconciliationCoordinator.key(identity),
        ) {
            reconcile(identity)
        }

    override suspend fun run(
        identity: MatchLobbyScreenshotIdentity,
        expectedOwnerUserId: String,
    ): MatchLobbyScreenshotUploadCheckpointResult {
        if (expectedOwnerUserId.isBlank() || !isCurrentOwner(expectedOwnerUserId)) {
            return MatchLobbyScreenshotUploadCheckpointResult.Skipped
        }
        return ScreenshotCloudReconciliationCoordinator.withLock(
            ScreenshotCloudReconciliationCoordinator.key(identity),
        ) {
            reconcile(identity, expectedOwnerUserId)
        }
    }

    private suspend fun reconcile(
        identity: MatchLobbyScreenshotIdentity,
        expectedOwnerUserId: String? = null,
    ): MatchLobbyScreenshotUploadCheckpointResult {
        for (pass in 0 until MAX_RECONCILIATION_PASSES) {
            val current = readLatestAsset(identity, expectedOwnerUserId) ?: return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            if (current.identityOrNull() != identity || !current.hasConfirmedCrop()) {
                return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            }
            val localFile = localImagePreserver.resolveRelativePath(current.localRelativePath)
            if (!isReadable(localFile)) {
                if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                    return MatchLobbyScreenshotUploadCheckpointResult.Skipped
                }
                if (!markUploadFailure(
                        identity,
                        current.sha256,
                        current.revision,
                        MatchLobbyScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED.name,
                    )
                ) continue
                return MatchLobbyScreenshotUploadCheckpointResult.Failed
            }

            val storageResult = if (hasRecordedStorageObject(current)) {
                if (current.uploadStatus != ScreenshotUploadStatus.UPLOADED.name ||
                    current.uploadFailureCode != null
                ) {
                    if (!updateUploadSuccess(
                            identity = identity,
                            ownerUserId = expectedOwnerUserId,
                            sha256 = current.sha256,
                            expectedRevision = current.revision,
                            storageBucket = current.storageBucket!!,
                            storageObjectPath = current.storageObjectPath!!,
                            uploadedAt = current.uploadedAt ?: clock.millis(),
                            updatedAt = clock.millis(),
                        )
                    ) continue
                }
                null
            } else {
                if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                    return MatchLobbyScreenshotUploadCheckpointResult.Skipped
                }
                upload(identity, localFile!!, expectedOwnerUserId)
            }
            when (storageResult) {
                is MatchLobbyScreenshotStorageUploadResult.Failed -> {
                    if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                        return MatchLobbyScreenshotUploadCheckpointResult.Skipped
                    }
                    if (!markUploadFailure(identity, current.sha256, current.revision, storageResult.failure.name, expectedOwnerUserId)) {
                        continue
                    }
                    return MatchLobbyScreenshotUploadCheckpointResult.Failed
                }

                is MatchLobbyScreenshotStorageUploadResult.Uploaded -> {
                    if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                        return MatchLobbyScreenshotUploadCheckpointResult.Skipped
                    }
                    if (!updateUploadSuccess(
                            identity = identity,
                            ownerUserId = expectedOwnerUserId,
                            sha256 = current.sha256,
                            expectedRevision = current.revision,
                            storageBucket = OCR_SCREENSHOTS_BUCKET,
                            storageObjectPath = storageResult.objectPath,
                            uploadedAt = clock.millis(),
                            updatedAt = clock.millis(),
                        )
                    ) continue
                }

                null -> Unit
            }

            val beforeCloud = readLatestAsset(identity, expectedOwnerUserId)
                ?: return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            if (beforeCloud.identityOrNull() != identity || !beforeCloud.hasConfirmedCrop()) {
                return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            }
            if (beforeCloud.uploadStatus != ScreenshotUploadStatus.UPLOADED.name ||
                beforeCloud.uploadFailureCode != null
            ) continue
            when (val cloudResult = upsert(beforeCloud, expectedOwnerUserId)) {
                is MatchLobbyScreenshotAssetCloudResult.Failed -> {
                    if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                        return MatchLobbyScreenshotUploadCheckpointResult.Skipped
                    }
                    if (!markUploadFailure(identity, beforeCloud.sha256, beforeCloud.revision, cloudResult.failure.name, expectedOwnerUserId)) {
                        continue
                    }
                    return MatchLobbyScreenshotUploadCheckpointResult.Failed
                }

                MatchLobbyScreenshotAssetCloudResult.Success -> Unit
            }

            if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            }
            val afterCloud = readLatestAsset(identity, expectedOwnerUserId)
                ?: return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            if (afterCloud.identityOrNull() != identity || !afterCloud.hasConfirmedCrop()) {
                return MatchLobbyScreenshotUploadCheckpointResult.Skipped
            }
            if (sameEligibleGeneration(beforeCloud, afterCloud)) {
                return MatchLobbyScreenshotUploadCheckpointResult.Completed
            }
        }
        return MatchLobbyScreenshotUploadCheckpointResult.Skipped
    }

    private suspend fun upload(
        identity: MatchLobbyScreenshotIdentity,
        localFile: File,
        expectedOwnerUserId: String? = null,
    ): MatchLobbyScreenshotStorageUploadResult = try {
        if (expectedOwnerUserId == null) {
            storageUploader.upload(identity.tournamentId, identity.matchId, identity.lobbyScreenshotIndex, localFile)
        } else {
            storageUploader.upload(expectedOwnerUserId, identity.tournamentId, identity.matchId, identity.lobbyScreenshotIndex, localFile)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        MatchLobbyScreenshotStorageUploadResult.Failed(MatchLobbyScreenshotStorageUploadFailure.UPLOAD_FAILED)
    }

    private suspend fun markUploadFailure(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        ownerUserId: String? = null,
    ): Boolean = try {
        if (ownerUserId != null && !isCurrentOwner(ownerUserId)) return false
        if (ownerUserId == null) assetRepository.updateUploadFailureIfGenerationMatches(
            identity = identity,
            sha256 = sha256,
            expectedRevision = expectedRevision,
            failureCode = failureCode,
            updatedAt = clock.millis(),
        ) else assetRepository.updateUploadFailureIfGenerationMatchesByOwner(
            identity, ownerUserId, sha256, expectedRevision, failureCode, clock.millis(),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

    private suspend fun readLatestAsset(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String? = null,
    ): MatchLobbyScreenshotAssetEntity? = try {
        if (ownerUserId == null) assetRepository.getByIdentity(identity)
        else assetRepository.getByIdentityAndOwner(identity, ownerUserId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun updateUploadSuccess(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String?,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean {
        if (ownerUserId != null && !isCurrentOwner(ownerUserId)) return false
        return if (ownerUserId == null) {
            assetRepository.updateUploadSuccessIfGenerationMatches(identity, sha256, expectedRevision, storageBucket, storageObjectPath, uploadedAt, updatedAt)
        } else {
            assetRepository.updateUploadSuccessIfGenerationMatchesByOwner(identity, ownerUserId, sha256, expectedRevision, storageBucket, storageObjectPath, uploadedAt, updatedAt)
        }
    }

    private suspend fun upsert(
        asset: MatchLobbyScreenshotAssetEntity,
        ownerUserId: String?,
    ): MatchLobbyScreenshotAssetCloudResult = try {
        if (ownerUserId == null) cloudDataSource.upsert(asset)
        else cloudDataSource.upsert(asset, ownerUserId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        MatchLobbyScreenshotAssetCloudResult.Failed(MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED)
    }

    private suspend fun isCurrentOwner(expectedOwnerUserId: String): Boolean = try {
        screenshotOwnerProvider.currentOwnerUserId() == expectedOwnerUserId
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

    private fun MatchLobbyScreenshotAssetEntity.hasConfirmedCrop(): Boolean =
        cropProfileId == OcrCropValidationProfiles.Lobby.id && validCrop(
            originalWidth,
            originalHeight,
            cropLeft,
            cropTop,
            cropRight,
            cropBottom,
            OcrCropValidationProfiles.Lobby,
        )

    private fun sameEligibleGeneration(
        first: MatchLobbyScreenshotAssetEntity,
        second: MatchLobbyScreenshotAssetEntity,
    ): Boolean = first.identityOrNull() == second.identityOrNull() &&
        first.sha256 == second.sha256 &&
        first.revision == second.revision &&
        first.localRelativePath == second.localRelativePath &&
        first.cropProfileId == second.cropProfileId &&
        first.cropLeft == second.cropLeft &&
        first.cropTop == second.cropTop &&
        first.cropRight == second.cropRight &&
        first.cropBottom == second.cropBottom &&
        first.storageBucket == second.storageBucket &&
        first.storageObjectPath == second.storageObjectPath

    private fun hasRecordedStorageObject(asset: MatchLobbyScreenshotAssetEntity): Boolean =
        !asset.storageBucket.isNullOrBlank() && !asset.storageObjectPath.isNullOrBlank()

    private fun isReadable(file: File?): Boolean = file?.let {
        runCatching { it.isFile && it.canRead() && it.length() > 0L }.getOrDefault(false)
    } == true

    private fun validCrop(
        width: Int,
        height: Int,
        left: Double?,
        top: Double?,
        right: Double?,
        bottom: Double?,
        profile: com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfile,
    ): Boolean {
        val dimensions = OcrImageDimensions.from(width, height) ?: return false
        val crop = OcrNormalizedCropRect(
            left ?: return false,
            top ?: return false,
            right ?: return false,
            bottom ?: return false,
        )
        return OcrCropValidator.validate(crop, dimensions, profile) is OcrCropValidationResult.Valid
    }

    private companion object {
        const val MAX_RECONCILIATION_PASSES = 8
    }
}
