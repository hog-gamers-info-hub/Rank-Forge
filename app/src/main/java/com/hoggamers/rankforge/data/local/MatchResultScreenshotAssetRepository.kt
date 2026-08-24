package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

sealed interface MatchResultScreenshotAssetSaveResult {
    data object Saved : MatchResultScreenshotAssetSaveResult
    data object InvalidIdentity : MatchResultScreenshotAssetSaveResult
    data class DuplicateFingerprint(
        val existing: MatchResultScreenshotAssetEntity,
    ) : MatchResultScreenshotAssetSaveResult

    data object StateConflict : MatchResultScreenshotAssetSaveResult
    data object AuthenticationRequired : MatchResultScreenshotAssetSaveResult
    data object MatchNotFound : MatchResultScreenshotAssetSaveResult
}

sealed interface MatchResultScreenshotCropSaveResult {
    data object Saved : MatchResultScreenshotCropSaveResult
    data object MissingAsset : MatchResultScreenshotCropSaveResult
    data object InvalidIdentity : MatchResultScreenshotCropSaveResult
    data object InvalidCrop : MatchResultScreenshotCropSaveResult
    data object AuthenticationRequired : MatchResultScreenshotCropSaveResult
    data object MatchNotFound : MatchResultScreenshotCropSaveResult
}

interface MatchResultScreenshotAssetRepository {
    fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>>

    fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?>

    suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity?

    fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>>

    fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())

    fun observeByIdentityAndOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): Flow<MatchResultScreenshotAssetEntity?> = flowOf(null)

    suspend fun getByIdentityAndOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): MatchResultScreenshotAssetEntity? = null

    fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())

    suspend fun findDuplicateFingerprintAndOwner(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        ownerUserId: String,
    ): MatchResultScreenshotAssetEntity? = null

    suspend fun saveOrReplaceByOwner(
        asset: MatchResultScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchResultScreenshotAssetSaveResult = MatchResultScreenshotAssetSaveResult.AuthenticationRequired

    suspend fun restoreOrReplaceByOwner(
        asset: MatchResultScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchResultScreenshotAssetSaveResult = saveOrReplaceByOwner(asset, ownerUserId)

    suspend fun findDuplicateFingerprint(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
    ): MatchResultScreenshotAssetEntity?

    suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult

    suspend fun restoreOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult =
        saveOrReplace(asset)

    suspend fun updateUploadSuccessIfFingerprintMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadFailureIfFingerprintMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadSuccessIfGenerationMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadSuccessIfGenerationMatchesByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadFailureIfGenerationMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadFailureIfGenerationMatchesByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long)

    suspend fun markLocalMissingByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun markCleanupFailureByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long)

    suspend fun persistConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult

    suspend fun persistConfirmedCropByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.AuthenticationRequired

    suspend fun clearConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult

    suspend fun clearConfirmedCropByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.AuthenticationRequired

    suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity)

    suspend fun deleteByIdentityAndOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): Boolean = false

    suspend fun deleteByMatchId(matchId: String)
}

@Singleton
class RoomMatchResultScreenshotAssetRepository @Inject constructor(
    private val dao: MatchResultScreenshotAssetDao,
    private val database: RankForgeDatabase?,
) : MatchResultScreenshotAssetRepository {
    constructor(dao: MatchResultScreenshotAssetDao) : this(dao, null)
    override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
        dao.observeByMatchId(matchId)

    override fun observeByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): Flow<MatchResultScreenshotAssetEntity?> =
        dao.observeByMatchAndRole(
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
        )

    override suspend fun getByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultScreenshotAssetEntity? =
        dao.readByMatchAndRole(identity.matchId, identity.role.name)

    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
        dao.observeByTournamentId(tournamentId)

    override fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByMatchIdAndOwner(matchId, ownerUserId)

    override fun observeByIdentityAndOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): Flow<MatchResultScreenshotAssetEntity?> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId)
            .map { asset -> asset?.takeIf { it.tournamentId == identity.tournamentId } }

    override suspend fun getByIdentityAndOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): MatchResultScreenshotAssetEntity? =
        if (ownerUserId.isBlank()) null else dao.readByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId)
            ?.takeIf { it.tournamentId == identity.tournamentId }

    override fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<MatchResultScreenshotAssetEntity>> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByTournamentIdAndOwner(tournamentId, ownerUserId)

    override suspend fun findDuplicateFingerprintAndOwner(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        ownerUserId: String,
    ): MatchResultScreenshotAssetEntity? =
        if (ownerUserId.isBlank()) null else dao.readDuplicateFingerprintAndOwner(sha256, identity.matchId, identity.role.name, ownerUserId)

    override suspend fun saveOrReplaceByOwner(
        asset: MatchResultScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchResultScreenshotAssetSaveResult {
        if (ownerUserId.isBlank()) return MatchResultScreenshotAssetSaveResult.AuthenticationRequired
        val db = database ?: return MatchResultScreenshotAssetSaveResult.AuthenticationRequired
        val identity = asset.identityOrNull() ?: return MatchResultScreenshotAssetSaveResult.InvalidIdentity
        return ScreenshotAssetMutationCoordinator.withLock(ScreenshotAssetMutationCoordinator.key(identity)) {
            db.withTransaction {
                if (!db.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                    return@withTransaction MatchResultScreenshotAssetSaveResult.MatchNotFound
                }
                if (db.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) {
                    return@withTransaction MatchResultScreenshotAssetSaveResult.StateConflict
                }
                if (dao.readDuplicateFingerprintAndOwner(asset.sha256, identity.matchId, identity.role.name, ownerUserId) != null) {
                    return@withTransaction MatchResultScreenshotAssetSaveResult.DuplicateFingerprint(
                        dao.readDuplicateFingerprintAndOwner(asset.sha256, identity.matchId, identity.role.name, ownerUserId)!!,
                    )
                }
                val existing = dao.readByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId)
                val ownerBoundAsset = asset.copy(ownerUserId = ownerUserId)
                val toSave = if (existing != null && existing.sha256 != asset.sha256) ownerBoundAsset.copy(
                    cropProfileId = null, cropLeft = null, cropTop = null, cropRight = null, cropBottom = null,
                ) else ownerBoundAsset
                dao.upsert(toSave)
                MatchResultScreenshotAssetSaveResult.Saved
            }
        }
    }

    override suspend fun restoreOrReplaceByOwner(
        asset: MatchResultScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchResultScreenshotAssetSaveResult {
        if (ownerUserId.isBlank()) return MatchResultScreenshotAssetSaveResult.AuthenticationRequired
        val db = database ?: return MatchResultScreenshotAssetSaveResult.AuthenticationRequired
        val identity = asset.identityOrNull() ?: return MatchResultScreenshotAssetSaveResult.InvalidIdentity
        return ScreenshotAssetMutationCoordinator.withLock(ScreenshotAssetMutationCoordinator.key(identity)) {
            db.withTransaction {
                if (!db.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                    return@withTransaction MatchResultScreenshotAssetSaveResult.MatchNotFound
                }
                if (db.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) {
                    return@withTransaction MatchResultScreenshotAssetSaveResult.StateConflict
                }
                if (dao.readDuplicateFingerprintAndOwner(asset.sha256, identity.matchId, identity.role.name, ownerUserId) != null) {
                    return@withTransaction MatchResultScreenshotAssetSaveResult.DuplicateFingerprint(
                        dao.readDuplicateFingerprintAndOwner(asset.sha256, identity.matchId, identity.role.name, ownerUserId)!!,
                    )
                }
                dao.upsert(asset.copy(ownerUserId = ownerUserId))
                MatchResultScreenshotAssetSaveResult.Saved
            }
        }
    }

    override suspend fun findDuplicateFingerprint(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
    ): MatchResultScreenshotAssetEntity? =
        dao.readDuplicateFingerprint(
            sha256 = sha256,
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
        )

    override suspend fun saveOrReplace(
        asset: MatchResultScreenshotAssetEntity,
    ): MatchResultScreenshotAssetSaveResult = saveOrReplaceInternal(asset, clearCropOnReplacement = true)

    override suspend fun restoreOrReplace(
        asset: MatchResultScreenshotAssetEntity,
    ): MatchResultScreenshotAssetSaveResult = saveOrReplaceInternal(asset, clearCropOnReplacement = false)

    private suspend fun saveOrReplaceInternal(
        asset: MatchResultScreenshotAssetEntity,
        clearCropOnReplacement: Boolean,
    ): MatchResultScreenshotAssetSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(
            asset.identityOrNull() ?: return MatchResultScreenshotAssetSaveResult.InvalidIdentity,
        ),
    ) {
        val identity = asset.identityOrNull() ?: return@withLock MatchResultScreenshotAssetSaveResult.InvalidIdentity
        val duplicate = try {
            findDuplicateFingerprint(identity, asset.sha256)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchResultScreenshotAssetSaveResult.StateConflict
        }
        if (duplicate != null) {
            return@withLock MatchResultScreenshotAssetSaveResult.DuplicateFingerprint(duplicate)
        }
        val existing = try {
            dao.readByMatchAndRole(identity.matchId, identity.role.name)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchResultScreenshotAssetSaveResult.StateConflict
        }
        val assetToSave = if (clearCropOnReplacement && existing != null && existing.sha256 != asset.sha256) {
            asset.copy(
                cropProfileId = null,
                cropLeft = null,
                cropTop = null,
                cropRight = null,
                cropBottom = null,
            )
        } else {
            asset
        }
        dao.upsert(assetToSave)
        MatchResultScreenshotAssetSaveResult.Saved
    }

    override suspend fun updateUploadSuccessIfFingerprintMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = dao.updateUploadSuccessIfFingerprintMatches(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotRole = identity.role.name,
        sha256 = sha256,
        storageBucket = storageBucket,
        storageObjectPath = storageObjectPath,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadedAt = uploadedAt,
        updatedAt = updatedAt,
    ) > 0

    override suspend fun updateUploadFailureIfFingerprintMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = dao.updateUploadFailureIfFingerprintMatches(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotRole = identity.role.name,
        sha256 = sha256,
        uploadStatus = ScreenshotUploadStatus.FAILED.name,
        uploadFailureCode = failureCode,
        updatedAt = updatedAt,
    ) > 0

    override suspend fun updateUploadSuccessIfGenerationMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        dao.updateUploadSuccessIfGenerationMatches(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
            sha256 = sha256,
            expectedRevision = expectedRevision,
            storageBucket = storageBucket,
            storageObjectPath = storageObjectPath,
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadedAt = uploadedAt,
            updatedAt = updatedAt,
        ) > 0
    }

    override suspend fun updateUploadFailureIfGenerationMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        dao.updateUploadFailureIfGenerationMatches(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
            sha256 = sha256,
            expectedRevision = expectedRevision,
            uploadStatus = ScreenshotUploadStatus.FAILED.name,
            uploadFailureCode = failureCode,
            updatedAt = updatedAt,
        ) > 0
    }

    override suspend fun updateUploadSuccessIfGenerationMatchesByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        if (database!!.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) return@withTransaction false
        if (dao.readByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId) == null) return@withTransaction false
        dao.updateUploadSuccessIfGenerationMatches(
            identity.tournamentId, identity.matchId, identity.role.name, sha256, expectedRevision,
            storageBucket, storageObjectPath, ScreenshotUploadStatus.UPLOADED.name, uploadedAt, updatedAt,
        ) > 0
    }

    override suspend fun updateUploadFailureIfGenerationMatchesByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        if (database!!.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) return@withTransaction false
        if (dao.readByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId) == null) return@withTransaction false
        dao.updateUploadFailureIfGenerationMatches(
            identity.tournamentId, identity.matchId, identity.role.name, sha256, expectedRevision,
            ScreenshotUploadStatus.FAILED.name, failureCode, updatedAt,
        ) > 0
    }

    override suspend fun markLocalMissing(
        identity: MatchResultScreenshotIdentity,
        updatedAt: Long,
    ) {
        dao.markLocalMissing(
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
            localStatus = ScreenshotLocalStatus.MISSING.name,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markLocalMissingByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        if (database!!.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) return@withTransaction false
        dao.markLocalMissing(identity.matchId, identity.role.name, ScreenshotLocalStatus.MISSING.name, updatedAt)
        true
    }

    override suspend fun markCleanupFailureByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        if (database!!.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) return@withTransaction false
        dao.markCleanupFailure(identity.matchId, identity.role.name, ScreenshotLocalStatus.CLEANUP_FAILED.name, updatedAt)
        true
    }

    override suspend fun markCleanupFailure(
        identity: MatchResultScreenshotIdentity,
        updatedAt: Long,
    ) {
        dao.markCleanupFailure(
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
            localStatus = ScreenshotLocalStatus.CLEANUP_FAILED.name,
            updatedAt = updatedAt,
        )
    }

    override suspend fun persistConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        val asset = getByIdentity(identity) ?: return@withLock MatchResultScreenshotCropSaveResult.MissingAsset
        val storedIdentity = asset.identityOrNull()
            ?: return@withLock MatchResultScreenshotCropSaveResult.InvalidIdentity
        if (storedIdentity != identity) return@withLock MatchResultScreenshotCropSaveResult.InvalidIdentity
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return@withLock MatchResultScreenshotCropSaveResult.InvalidCrop
        when (OcrCropValidator.validate(crop, dimensions, OcrCropValidationProfiles.MatchResult)) {
            is OcrCropValidationResult.Invalid -> MatchResultScreenshotCropSaveResult.InvalidCrop
            is OcrCropValidationResult.Valid -> {
                dao.updateConfirmedCrop(
                    matchId = identity.matchId,
                    screenshotRole = identity.role.name,
                    cropProfileId = OcrCropValidationProfiles.MatchResult.id,
                    cropLeft = crop.left,
                    cropTop = crop.top,
                    cropRight = crop.right,
                    cropBottom = crop.bottom,
                    updatedAt = updatedAt,
                )
                MatchResultScreenshotCropSaveResult.Saved
            }
        }
    }

    override suspend fun clearConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        val asset = getByIdentity(identity) ?: return@withLock MatchResultScreenshotCropSaveResult.MissingAsset
        val storedIdentity = asset.identityOrNull()
            ?: return@withLock MatchResultScreenshotCropSaveResult.InvalidIdentity
        if (storedIdentity != identity) return@withLock MatchResultScreenshotCropSaveResult.InvalidIdentity
        dao.clearConfirmedCrop(identity.matchId, identity.role.name, updatedAt)
        MatchResultScreenshotCropSaveResult.Saved
    }

    override suspend fun persistConfirmedCropByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult {
        if (ownerUserId.isBlank()) return MatchResultScreenshotCropSaveResult.AuthenticationRequired
        if (database == null) return MatchResultScreenshotCropSaveResult.AuthenticationRequired
        return persistConfirmedCropOwnerInternal(identity, ownerUserId, crop, updatedAt)
    }

    private suspend fun persistConfirmedCropOwnerInternal(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        database!!.withTransaction {
            if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                return@withTransaction MatchResultScreenshotCropSaveResult.MatchNotFound
            }
            if (database!!.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) {
                return@withTransaction MatchResultScreenshotCropSaveResult.MissingAsset
            }
            val asset = dao.readByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId)
                ?: return@withTransaction MatchResultScreenshotCropSaveResult.MissingAsset
            val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
                ?: return@withTransaction MatchResultScreenshotCropSaveResult.InvalidCrop
            when (OcrCropValidator.validate(crop, dimensions, OcrCropValidationProfiles.MatchResult)) {
                is OcrCropValidationResult.Invalid -> MatchResultScreenshotCropSaveResult.InvalidCrop
                is OcrCropValidationResult.Valid -> {
                    dao.updateConfirmedCrop(identity.matchId, identity.role.name, OcrCropValidationProfiles.MatchResult.id, crop.left, crop.top, crop.right, crop.bottom, updatedAt)
                    MatchResultScreenshotCropSaveResult.Saved
                }
            }
        }
    }

    override suspend fun clearConfirmedCropByOwner(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult {
        if (ownerUserId.isBlank()) return MatchResultScreenshotCropSaveResult.AuthenticationRequired
        if (database == null) return MatchResultScreenshotCropSaveResult.AuthenticationRequired
        return ScreenshotAssetMutationCoordinator.withLock(ScreenshotAssetMutationCoordinator.key(identity)) {
            database!!.withTransaction {
                if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                    return@withTransaction MatchResultScreenshotCropSaveResult.MatchNotFound
                }
                if (database!!.deletionIntentDao().isLocalMutationBlocked(identity.tournamentId, identity.matchId, ownerUserId)) {
                    return@withTransaction MatchResultScreenshotCropSaveResult.MissingAsset
                }
                if (dao.readByMatchAndRoleAndOwner(identity.matchId, identity.role.name, ownerUserId) == null) {
                    return@withTransaction MatchResultScreenshotCropSaveResult.MissingAsset
                }
                dao.clearConfirmedCrop(identity.matchId, identity.role.name, updatedAt)
                MatchResultScreenshotCropSaveResult.Saved
            }
        }
    }

    override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) {
        dao.deleteByMatchAndRole(identity.matchId, identity.role.name)
    }

    override suspend fun deleteByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String): Boolean =
        if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
            if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                return@withTransaction false
            }
            dao.deleteByMatchAndRole(identity.matchId, identity.role.name)
            true
        }

    override suspend fun deleteByMatchId(matchId: String) {
        dao.deleteByMatchId(matchId)
    }
}

class NoOpMatchResultScreenshotAssetRepository : MatchResultScreenshotAssetRepository {
    override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())

    override fun observeByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): Flow<MatchResultScreenshotAssetEntity?> = flowOf(null)

    override suspend fun getByIdentity(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultScreenshotAssetEntity? = null

    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
        flowOf(emptyList())

    override suspend fun findDuplicateFingerprint(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
    ): MatchResultScreenshotAssetEntity? = null

    override suspend fun saveOrReplace(
        asset: MatchResultScreenshotAssetEntity,
    ): MatchResultScreenshotAssetSaveResult = MatchResultScreenshotAssetSaveResult.Saved

    override suspend fun updateUploadSuccessIfFingerprintMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun updateUploadSuccessIfGenerationMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun updateUploadFailureIfGenerationMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun updateUploadFailureIfFingerprintMatches(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

    override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

    override suspend fun persistConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.MissingAsset

    override suspend fun clearConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.MissingAsset

    override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit

    override suspend fun deleteByMatchId(matchId: String) = Unit
}

fun MatchResultScreenshotAssetEntity.identityOrNull(): MatchResultScreenshotIdentity? {
    if (tournamentId.isBlank() || matchId.isBlank()) return null
    val kind = runCatching { OcrScreenshotKind.valueOf(screenshotKind) }.getOrNull()
        ?: return null
    val role = runCatching { MatchResultScreenshotRole.valueOf(screenshotRole) }.getOrNull()
        ?: return null
    return runCatching {
        MatchResultScreenshotIdentity(
            tournamentId = tournamentId,
            matchId = matchId,
            kind = kind,
            role = role,
        )
    }.getOrNull()
}
