package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.NoOpMatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.NoOpMatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.OCR_SCREENSHOTS_BUCKET
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.ScreenshotCloudReconciliationCoordinator
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface MatchResultScreenshotUploadCheckpointResult {
    data object Completed : MatchResultScreenshotUploadCheckpointResult
    data object Skipped : MatchResultScreenshotUploadCheckpointResult
    data object Failed : MatchResultScreenshotUploadCheckpointResult
}

fun interface MatchResultScreenshotUploadCheckpointAction {
    suspend fun run(identity: MatchResultScreenshotIdentity): MatchResultScreenshotUploadCheckpointResult

    suspend fun run(
        identity: MatchResultScreenshotIdentity,
        expectedOwnerUserId: String,
    ): MatchResultScreenshotUploadCheckpointResult =
        throw SecurityException("Expected screenshot owner is required.")
}

class MatchResultScreenshotUploadCheckpoint @Inject constructor(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val clock: Clock,
    private val storageUploader: MatchResultScreenshotStorageUploader = NoOpMatchResultScreenshotStorageUploader(),
    private val cloudDataSource: MatchResultScreenshotAssetCloudDataSource =
        NoOpMatchResultScreenshotAssetCloudDataSource(),
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
) : MatchResultScreenshotUploadCheckpointAction {
    override suspend fun run(identity: MatchResultScreenshotIdentity): MatchResultScreenshotUploadCheckpointResult =
        ScreenshotCloudReconciliationCoordinator.withLock(
            ScreenshotCloudReconciliationCoordinator.key(identity),
        ) {
            reconcile(identity)
        }

    override suspend fun run(
        identity: MatchResultScreenshotIdentity,
        expectedOwnerUserId: String,
    ): MatchResultScreenshotUploadCheckpointResult {
        if (expectedOwnerUserId.isBlank() || !isCurrentOwner(expectedOwnerUserId)) {
            return MatchResultScreenshotUploadCheckpointResult.Skipped
        }
        return ScreenshotCloudReconciliationCoordinator.withLock(
            ScreenshotCloudReconciliationCoordinator.key(identity),
        ) {
            reconcile(identity, expectedOwnerUserId)
        }
    }

    private suspend fun reconcile(
        identity: MatchResultScreenshotIdentity,
        expectedOwnerUserId: String? = null,
    ): MatchResultScreenshotUploadCheckpointResult {
        for (pass in 0 until MAX_RECONCILIATION_PASSES) {
            val current = readLatestAsset(identity, expectedOwnerUserId) ?: return MatchResultScreenshotUploadCheckpointResult.Skipped
            if (current.identityOrNull() != identity || !current.hasConfirmedCrop()) {
                return MatchResultScreenshotUploadCheckpointResult.Skipped
            }
            val localFile = localImagePreserver.resolveRelativePath(current.localRelativePath)
            if (!isReadable(localFile)) {
                if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                    return MatchResultScreenshotUploadCheckpointResult.Skipped
                }
                if (!markUploadFailure(
                        identity,
                        current.sha256,
                        current.revision,
                        MatchResultScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED.name,
                    )
                ) continue
                return MatchResultScreenshotUploadCheckpointResult.Failed
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
                    return MatchResultScreenshotUploadCheckpointResult.Skipped
                }
                upload(identity, localFile!!, expectedOwnerUserId)
            }
            when (storageResult) {
                is MatchResultScreenshotStorageUploadResult.Failed -> {
                    if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                        return MatchResultScreenshotUploadCheckpointResult.Skipped
                    }
                    if (!markUploadFailure(identity, current.sha256, current.revision, storageResult.failure.name, expectedOwnerUserId)) {
                        continue
                    }
                    return MatchResultScreenshotUploadCheckpointResult.Failed
                }

                is MatchResultScreenshotStorageUploadResult.Uploaded -> {
                    if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                        return MatchResultScreenshotUploadCheckpointResult.Skipped
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
                ?: return MatchResultScreenshotUploadCheckpointResult.Skipped
            if (beforeCloud.identityOrNull() != identity || !beforeCloud.hasConfirmedCrop()) {
                return MatchResultScreenshotUploadCheckpointResult.Skipped
            }
            if (beforeCloud.uploadStatus != ScreenshotUploadStatus.UPLOADED.name ||
                beforeCloud.uploadFailureCode != null
            ) continue
            when (val cloudResult = upsert(beforeCloud, expectedOwnerUserId)) {
                is MatchResultScreenshotAssetCloudResult.Failed -> {
                    if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                        return MatchResultScreenshotUploadCheckpointResult.Skipped
                    }
                    if (!markUploadFailure(identity, beforeCloud.sha256, beforeCloud.revision, cloudResult.failure.name, expectedOwnerUserId)) {
                        continue
                    }
                    return MatchResultScreenshotUploadCheckpointResult.Failed
                }

                MatchResultScreenshotAssetCloudResult.Success -> Unit
            }

            if (expectedOwnerUserId != null && !isCurrentOwner(expectedOwnerUserId)) {
                return MatchResultScreenshotUploadCheckpointResult.Skipped
            }
            val afterCloud = readLatestAsset(identity, expectedOwnerUserId)
                ?: return MatchResultScreenshotUploadCheckpointResult.Skipped
            if (afterCloud.identityOrNull() != identity || !afterCloud.hasConfirmedCrop()) {
                return MatchResultScreenshotUploadCheckpointResult.Skipped
            }
            if (sameEligibleGeneration(beforeCloud, afterCloud)) {
                return MatchResultScreenshotUploadCheckpointResult.Completed
            }
        }
        return MatchResultScreenshotUploadCheckpointResult.Skipped
    }

    private suspend fun upload(
        identity: MatchResultScreenshotIdentity,
        localFile: File,
        expectedOwnerUserId: String? = null,
    ): MatchResultScreenshotStorageUploadResult = try {
        if (expectedOwnerUserId == null) {
            storageUploader.upload(identity.tournamentId, identity.matchId, identity.role, localFile)
        } else {
            storageUploader.upload(expectedOwnerUserId, identity.tournamentId, identity.matchId, identity.role, localFile)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        MatchResultScreenshotStorageUploadResult.Failed(MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED)
    }

    private suspend fun markUploadFailure(
        identity: MatchResultScreenshotIdentity,
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
            identity = identity,
            ownerUserId = ownerUserId,
            sha256 = sha256,
            expectedRevision = expectedRevision,
            failureCode = failureCode,
            updatedAt = clock.millis(),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

    private suspend fun readLatestAsset(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String? = null,
    ): MatchResultScreenshotAssetEntity? = try {
        if (ownerUserId == null) assetRepository.getByIdentity(identity)
        else assetRepository.getByIdentityAndOwner(identity, ownerUserId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun updateUploadSuccess(
        identity: MatchResultScreenshotIdentity,
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
        asset: MatchResultScreenshotAssetEntity,
        ownerUserId: String?,
    ): MatchResultScreenshotAssetCloudResult = try {
        if (ownerUserId == null) cloudDataSource.upsert(asset)
        else cloudDataSource.upsert(asset, ownerUserId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        MatchResultScreenshotAssetCloudResult.Failed(MatchResultScreenshotAssetCloudFailure.WRITE_FAILED)
    }

    private suspend fun isCurrentOwner(expectedOwnerUserId: String): Boolean = try {
        screenshotOwnerProvider.currentOwnerUserId() == expectedOwnerUserId
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

    private fun MatchResultScreenshotAssetEntity.hasConfirmedCrop(): Boolean =
        cropProfileId == OcrCropValidationProfiles.MatchResult.id && validCrop(
            originalWidth,
            originalHeight,
            cropLeft,
            cropTop,
            cropRight,
            cropBottom,
            OcrCropValidationProfiles.MatchResult,
        )

    private fun sameEligibleGeneration(
        first: MatchResultScreenshotAssetEntity,
        second: MatchResultScreenshotAssetEntity,
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

    private fun hasRecordedStorageObject(asset: MatchResultScreenshotAssetEntity): Boolean =
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
