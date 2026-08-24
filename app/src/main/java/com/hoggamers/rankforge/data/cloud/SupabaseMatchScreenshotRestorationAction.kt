package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.MatchScreenshotRestorationAction
import com.hoggamers.rankforge.presentation.screen.LocalImagePreservationResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import com.hoggamers.rankforge.presentation.screen.NoOpScreenshotOwnerProvider
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SupabaseMatchScreenshotRestorationAction @Inject constructor(
    private val lobbyCloud: MatchLobbyScreenshotAssetCloudDataSource,
    private val resultCloud: MatchResultScreenshotAssetCloudDataSource,
    private val storage: AuthenticatedScreenshotStorageDownloader,
    private val localImagePreserver: LocalImagePreserver,
    private val lobbyAssets: MatchLobbyScreenshotAssetRepository,
    private val resultAssets: MatchResultScreenshotAssetRepository,
    private val ownerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
) : MatchScreenshotRestorationAction {
    override suspend fun invoke(
        tournamentId: String,
        restoredMatchIds: Set<String>,
    ): MatchCloudRestorationResult {
        val expectedOwnerUserId = ownerProvider.currentOwnerUserId()
            ?.takeIf { it.isNotBlank() }
            ?: return MatchCloudRestorationResult.AuthorizationFailure
        return restore(tournamentId, restoredMatchIds, expectedOwnerUserId)
    }

    override suspend fun invoke(
        tournamentId: String,
        restoredMatchIds: Set<String>,
        expectedOwnerUserId: String,
    ): MatchCloudRestorationResult {
        if (expectedOwnerUserId.isBlank() || ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        return restore(tournamentId, restoredMatchIds, expectedOwnerUserId)
    }

    private suspend fun restore(
        tournamentId: String,
        restoredMatchIds: Set<String>,
        expectedOwnerUserId: String,
    ): MatchCloudRestorationResult {
        if (tournamentId.isBlank()) return MatchCloudRestorationResult.ValidationFailure
        if (restoredMatchIds.isEmpty()) return MatchCloudRestorationResult.Success

        val lobbyPayloads = when (val read = lobbyCloud.readByTournamentAndMatchIds(tournamentId, restoredMatchIds)) {
            is MatchLobbyScreenshotAssetCloudReadResult.Failed -> return read.failure.toMatchResult()
            is MatchLobbyScreenshotAssetCloudReadResult.Success -> read.assets
        }
        val resultPayloads = when (val read = resultCloud.readByTournamentAndMatchIds(tournamentId, restoredMatchIds)) {
            is MatchResultScreenshotAssetCloudReadResult.Failed -> return read.failure.toMatchResult()
            is MatchResultScreenshotAssetCloudReadResult.Success -> read.assets
        }

        if (lobbyPayloads.any { !validateLobby(it, tournamentId, restoredMatchIds, expectedOwnerUserId) } ||
            resultPayloads.any { !validateResult(it, tournamentId, restoredMatchIds, expectedOwnerUserId) }
        ) return MatchCloudRestorationResult.ValidationFailure

        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            return MatchCloudRestorationResult.AuthorizationFailure
        }

        lobbyPayloads.forEach { payload ->
            when (val outcome = restoreLobby(payload, expectedOwnerUserId)) {
                null -> Unit
                else -> return outcome
            }
        }
        resultPayloads.forEach { payload ->
            when (val outcome = restoreResult(payload, expectedOwnerUserId)) {
                null -> Unit
                else -> return outcome
            }
        }
        return MatchCloudRestorationResult.Success
    }

    private suspend fun restoreLobby(
        payload: MatchLobbyScreenshotAssetCloudPayload,
        expectedOwnerUserId: String,
    ): MatchCloudRestorationResult? {
        val identity = MatchLobbyScreenshotIdentity(payload.tournamentId, payload.matchId, payload.lobbyScreenshotIndex)
        val bytes = when (val download = downloadAndVerify(payload.storageBucket!!, payload.storageObjectPath!!, payload.byteSize, payload.sha256, expectedOwnerUserId)) {
            is VerifiedDownload.Valid -> download.bytes
            VerifiedDownload.Invalid -> return MatchCloudRestorationResult.ValidationFailure
            VerifiedDownload.Authorization -> return MatchCloudRestorationResult.AuthorizationFailure
            VerifiedDownload.Failed -> return MatchCloudRestorationResult.NetworkFailure
        }
        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        val fileResult = localImagePreserver.restoreMatchLobbyScreenshot(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
            extension = payload.localFileExtension,
            bytes = bytes,
        )
        val file = when (fileResult) {
            is LocalImagePreservationResult.Preserved -> fileResult.file
            is LocalImagePreservationResult.PreservedWithCleanupFailure -> fileResult.file
            is LocalImagePreservationResult.Failed -> return MatchCloudRestorationResult.LocalTransactionFailure
        }
        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            localImagePreserver.delete(file)
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        val asset = MatchLobbyScreenshotAssetEntity(
            tournamentId = payload.tournamentId,
            matchId = payload.matchId,
            lobbyScreenshotIndex = payload.lobbyScreenshotIndex,
            ownerUserId = payload.ownerId,
            localRelativePath = localImagePreserver.lobbyRelativePath(
                payload.tournamentId,
                payload.matchId,
                payload.lobbyScreenshotIndex,
                payload.localFileExtension,
            ),
            fileExtension = payload.localFileExtension,
            mimeType = payload.mimeType,
            originalWidth = payload.originalWidth,
            originalHeight = payload.originalHeight,
            byteSize = payload.byteSize,
            sha256 = payload.sha256,
            localStatus = ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadFailureCode = null,
            storageBucket = payload.storageBucket,
            storageObjectPath = payload.storageObjectPath,
            cropProfileId = payload.cropProfileId,
            cropLeft = payload.cropLeft,
            cropTop = payload.cropTop,
            cropRight = payload.cropRight,
            cropBottom = payload.cropBottom,
            createdAt = payload.createdAt.toEpochMillis()!!,
            updatedAt = payload.updatedAt.toEpochMillis()!!,
            preservedAt = payload.preservedAt.toEpochMillis()!!,
            uploadedAt = payload.uploadedAt?.toEpochMillis(),
            revision = payload.revision,
        )
        if (file != localImagePreserver.lobbyPreservedFile(
                identity.tournamentId,
                identity.matchId,
                identity.lobbyScreenshotIndex,
                payload.localFileExtension,
            )
        ) return MatchCloudRestorationResult.LocalTransactionFailure
        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            localImagePreserver.delete(file)
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        return when (lobbyAssets.restoreOrReplaceByOwner(asset, expectedOwnerUserId)) {
            MatchLobbyScreenshotAssetSaveResult.Saved -> null
            else -> MatchCloudRestorationResult.LocalTransactionFailure
        }
    }

    private suspend fun restoreResult(
        payload: MatchResultScreenshotAssetCloudPayload,
        expectedOwnerUserId: String,
    ): MatchCloudRestorationResult? {
        val role = runCatching { MatchResultScreenshotRole.valueOf(payload.screenshotRole) }.getOrNull()
            ?: return MatchCloudRestorationResult.ValidationFailure
        val identity = MatchResultScreenshotIdentity(payload.tournamentId, payload.matchId, role = role)
        val bytes = when (val download = downloadAndVerify(payload.storageBucket!!, payload.storageObjectPath!!, payload.byteSize, payload.sha256, expectedOwnerUserId)) {
            is VerifiedDownload.Valid -> download.bytes
            VerifiedDownload.Invalid -> return MatchCloudRestorationResult.ValidationFailure
            VerifiedDownload.Authorization -> return MatchCloudRestorationResult.AuthorizationFailure
            VerifiedDownload.Failed -> return MatchCloudRestorationResult.NetworkFailure
        }
        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        val fileResult = localImagePreserver.restoreMatchResultScreenshot(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            role = identity.role,
            extension = payload.localFileExtension,
            bytes = bytes,
        )
        val file = when (fileResult) {
            is LocalImagePreservationResult.Preserved -> fileResult.file
            is LocalImagePreservationResult.PreservedWithCleanupFailure -> fileResult.file
            is LocalImagePreservationResult.Failed -> return MatchCloudRestorationResult.LocalTransactionFailure
        }
        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            localImagePreserver.delete(file)
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        val asset = MatchResultScreenshotAssetEntity(
            tournamentId = payload.tournamentId,
            matchId = payload.matchId,
            screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
            screenshotRole = role.name,
            ownerUserId = payload.ownerId,
            localRelativePath = localImagePreserver.matchResultRelativePath(
                payload.tournamentId,
                payload.matchId,
                role,
                payload.localFileExtension,
            ),
            fileExtension = payload.localFileExtension,
            mimeType = payload.mimeType,
            originalWidth = payload.originalWidth,
            originalHeight = payload.originalHeight,
            byteSize = payload.byteSize,
            sha256 = payload.sha256,
            localStatus = ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadFailureCode = null,
            storageBucket = payload.storageBucket,
            storageObjectPath = payload.storageObjectPath,
            cropProfileId = payload.cropProfileId,
            cropLeft = payload.cropLeft,
            cropTop = payload.cropTop,
            cropRight = payload.cropRight,
            cropBottom = payload.cropBottom,
            createdAt = payload.createdAt.toEpochMillis()!!,
            updatedAt = payload.updatedAt.toEpochMillis()!!,
            preservedAt = payload.preservedAt.toEpochMillis()!!,
            uploadedAt = payload.uploadedAt?.toEpochMillis(),
            revision = payload.revision,
        )
        if (file != localImagePreserver.matchResultPreservedFile(
                identity.tournamentId,
                identity.matchId,
                identity.role,
                payload.localFileExtension,
            )
        ) return MatchCloudRestorationResult.LocalTransactionFailure
        if (ownerProvider.currentOwnerUserId() != expectedOwnerUserId) {
            localImagePreserver.delete(file)
            return MatchCloudRestorationResult.AuthorizationFailure
        }
        return when (resultAssets.restoreOrReplaceByOwner(asset, expectedOwnerUserId)) {
            MatchResultScreenshotAssetSaveResult.Saved -> null
            else -> MatchCloudRestorationResult.LocalTransactionFailure
        }
    }

    private suspend fun downloadAndVerify(
        bucket: String,
        objectPath: String,
        expectedSize: Long,
        expectedSha: String,
        expectedOwnerUserId: String,
    ): VerifiedDownload = try {
        val bytes = storage.download(expectedOwnerUserId, bucket, objectPath)
        if (bytes.size.toLong() != expectedSize || bytes.sha256() != expectedSha) {
            VerifiedDownload.Invalid
        } else {
            VerifiedDownload.Valid(bytes)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        VerifiedDownload.Authorization
    } catch (_: IOException) {
        VerifiedDownload.Failed
    } catch (_: RuntimeException) {
        VerifiedDownload.Failed
    }

    private fun validateLobby(
        payload: MatchLobbyScreenshotAssetCloudPayload,
        tournamentId: String,
        matchIds: Set<String>,
        expectedOwnerUserId: String,
    ): Boolean {
        if (payload.tournamentId != tournamentId || payload.matchId !in matchIds || payload.ownerId.isBlank() ||
            payload.ownerId != expectedOwnerUserId ||
            !storagePathMatchesOwner(payload.storageObjectPath, expectedOwnerUserId) ||
            payload.lobbyScreenshotIndex !in 1..3 || !validFormat(payload.localFileExtension, payload.mimeType) ||
            payload.originalWidth <= 0 || payload.originalHeight <= 0 || payload.byteSize <= 0 ||
            !validSha(payload.sha256) || payload.storageBucket.isNullOrBlank() || payload.storageObjectPath.isNullOrBlank() ||
            payload.uploadStatus != ScreenshotUploadStatus.UPLOADED.name ||
            payload.revision <= 0 || payload.createdAt.toEpochMillis() == null || payload.updatedAt.toEpochMillis() == null ||
            payload.preservedAt.toEpochMillis() == null || (payload.uploadedAt != null && payload.uploadedAt.toEpochMillis() == null)
        ) return false
        return validCropContract(
            payload.cropProfileId,
            payload.cropLeft,
            payload.cropTop,
            payload.cropRight,
            payload.cropBottom,
            payload.originalWidth,
            payload.originalHeight,
            OcrCropValidationProfiles.Lobby,
        )
    }

    private fun validateResult(
        payload: MatchResultScreenshotAssetCloudPayload,
        tournamentId: String,
        matchIds: Set<String>,
        expectedOwnerUserId: String,
    ): Boolean {
        if (payload.tournamentId != tournamentId || payload.matchId !in matchIds || payload.ownerId.isBlank() ||
            payload.ownerId != expectedOwnerUserId ||
            !storagePathMatchesOwner(payload.storageObjectPath, expectedOwnerUserId) ||
            payload.screenshotKind != OcrScreenshotKind.MATCH_RESULT.name ||
            runCatching { MatchResultScreenshotRole.valueOf(payload.screenshotRole) }.isFailure ||
            !validFormat(payload.localFileExtension, payload.mimeType) || payload.originalWidth <= 0 ||
            payload.originalHeight <= 0 || payload.byteSize <= 0 || !validSha(payload.sha256) ||
            payload.storageBucket.isNullOrBlank() || payload.storageObjectPath.isNullOrBlank() ||
            payload.uploadStatus != ScreenshotUploadStatus.UPLOADED.name || payload.revision <= 0 ||
            payload.createdAt.toEpochMillis() == null || payload.updatedAt.toEpochMillis() == null ||
            payload.preservedAt.toEpochMillis() == null || (payload.uploadedAt != null && payload.uploadedAt.toEpochMillis() == null)
        ) return false
        return validCropContract(
            payload.cropProfileId,
            payload.cropLeft,
            payload.cropTop,
            payload.cropRight,
            payload.cropBottom,
            payload.originalWidth,
            payload.originalHeight,
            OcrCropValidationProfiles.MatchResult,
        )
    }

    private fun validCropContract(
        profileId: String?,
        left: Double?,
        top: Double?,
        right: Double?,
        bottom: Double?,
        width: Int,
        height: Int,
        profile: com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfile,
    ): Boolean {
        if (profileId == null && left == null && top == null && right == null && bottom == null) return true
        if (profileId != profile.id || left == null || top == null || right == null || bottom == null) return false
        val crop = OcrNormalizedCropRect(left, top, right, bottom)
        return OcrCropValidator.validate(crop, OcrImageDimensions.from(width, height) ?: return false, profile) is OcrCropValidationResult.Valid
    }

    private fun validFormat(extension: String, mimeType: String): Boolean {
        val normalized = extension.lowercase(Locale.ROOT)
        return when (normalized) {
            "png" -> mimeType.equals("image/png", ignoreCase = true)
            "jpg", "jpeg" -> mimeType.equals("image/jpeg", ignoreCase = true)
            "webp" -> mimeType.equals("image/webp", ignoreCase = true)
            else -> false
        }
    }

    private fun validSha(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{64}"))

    private fun storagePathMatchesOwner(path: String?, expectedOwnerUserId: String): Boolean =
        path?.split('/')?.let { it.size >= 2 && it[0] == "users" && it[1] == expectedOwnerUserId } == true

    private fun String.toEpochMillis(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private sealed interface VerifiedDownload {
        data class Valid(val bytes: ByteArray) : VerifiedDownload
        data object Invalid : VerifiedDownload
        data object Authorization : VerifiedDownload
        data object Failed : VerifiedDownload
    }
}

private fun MatchLobbyScreenshotAssetCloudFailure.toMatchResult() = when (this) {
    MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION -> MatchCloudRestorationResult.AuthenticationRequired
    MatchLobbyScreenshotAssetCloudFailure.AUTHORIZATION -> MatchCloudRestorationResult.AuthorizationFailure
    MatchLobbyScreenshotAssetCloudFailure.INVALID_IDENTITY,
    MatchLobbyScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE,
    -> MatchCloudRestorationResult.ValidationFailure
    MatchLobbyScreenshotAssetCloudFailure.NETWORK,
    MatchLobbyScreenshotAssetCloudFailure.READ_FAILED,
    MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED,
    -> MatchCloudRestorationResult.NetworkFailure
}

private fun MatchResultScreenshotAssetCloudFailure.toMatchResult() = when (this) {
    MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION -> MatchCloudRestorationResult.AuthenticationRequired
    MatchResultScreenshotAssetCloudFailure.AUTHORIZATION -> MatchCloudRestorationResult.AuthorizationFailure
    MatchResultScreenshotAssetCloudFailure.INVALID_IDENTITY,
    MatchResultScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE,
    -> MatchCloudRestorationResult.ValidationFailure
    MatchResultScreenshotAssetCloudFailure.NETWORK,
    MatchResultScreenshotAssetCloudFailure.READ_FAILED,
    MatchResultScreenshotAssetCloudFailure.WRITE_FAILED,
    -> MatchCloudRestorationResult.NetworkFailure
}
