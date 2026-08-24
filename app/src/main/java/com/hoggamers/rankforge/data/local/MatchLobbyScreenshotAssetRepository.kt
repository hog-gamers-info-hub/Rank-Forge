package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import androidx.room.withTransaction
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

sealed interface MatchLobbyScreenshotAssetSaveResult {
    data object Saved : MatchLobbyScreenshotAssetSaveResult
    data object InvalidIdentity : MatchLobbyScreenshotAssetSaveResult
    data class DuplicateFingerprint(
        val existing: MatchLobbyScreenshotAssetEntity,
    ) : MatchLobbyScreenshotAssetSaveResult

    data object StateConflict : MatchLobbyScreenshotAssetSaveResult
    data object AuthenticationRequired : MatchLobbyScreenshotAssetSaveResult
    data object MatchNotFound : MatchLobbyScreenshotAssetSaveResult
}

sealed interface MatchLobbyScreenshotCropSaveResult {
    data object Saved : MatchLobbyScreenshotCropSaveResult
    data object MissingAsset : MatchLobbyScreenshotCropSaveResult
    data object InvalidIdentity : MatchLobbyScreenshotCropSaveResult
    data object InvalidCrop : MatchLobbyScreenshotCropSaveResult
    data object AuthenticationRequired : MatchLobbyScreenshotCropSaveResult
    data object MatchNotFound : MatchLobbyScreenshotCropSaveResult
}

interface MatchLobbyScreenshotAssetRepository {
    fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>>

    fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?>

    suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity?

    fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>>

    fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())

    fun observeByIdentityAndOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
    ): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(null)

    suspend fun getByIdentityAndOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetEntity? = null

    fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())

    suspend fun findDuplicateFingerprintAndOwner(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetEntity? = null

    suspend fun saveOrReplaceByOwner(
        asset: MatchLobbyScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetSaveResult = MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired

    suspend fun restoreOrReplaceByOwner(
        asset: MatchLobbyScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetSaveResult = saveOrReplaceByOwner(asset, ownerUserId)

    suspend fun findDuplicateFingerprint(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
    ): MatchLobbyScreenshotAssetEntity?

    suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult

    suspend fun restoreOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult =
        saveOrReplace(asset)

    suspend fun updateUploadSuccessIfFingerprintMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadFailureIfFingerprintMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadSuccessIfGenerationMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadSuccessIfGenerationMatchesByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadFailureIfGenerationMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun updateUploadFailureIfGenerationMatchesByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long)

    suspend fun markLocalMissingByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): Boolean = false

    suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long)

    suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity)

    suspend fun deleteByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String): Boolean = false

    suspend fun deleteByMatchId(matchId: String)

    suspend fun persistConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult

    suspend fun persistConfirmedCropByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.AuthenticationRequired

    suspend fun clearConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult

    suspend fun clearConfirmedCropByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.AuthenticationRequired
}

internal object ScreenshotAssetMutationCoordinator {
    private val locks = Array(64) { Mutex() }

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val lock = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    fun key(identity: MatchLobbyScreenshotIdentity): String =
        "${identity.tournamentId}|${identity.matchId}|lobby:${identity.lobbyScreenshotIndex}"

    fun key(identity: MatchResultScreenshotIdentity): String =
        "${identity.tournamentId}|${identity.matchId}|result:${identity.role.name}"
}

internal object ScreenshotCloudReconciliationCoordinator {
    private val locks = Array(64) { Mutex() }

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val lock = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    fun key(identity: MatchLobbyScreenshotIdentity): String =
        ScreenshotAssetMutationCoordinator.key(identity)

    fun key(identity: MatchResultScreenshotIdentity): String =
        ScreenshotAssetMutationCoordinator.key(identity)
}

@Singleton
class RoomMatchLobbyScreenshotAssetRepository @Inject constructor(
    private val dao: MatchLobbyScreenshotAssetDao,
    private val database: RankForgeDatabase?,
) : MatchLobbyScreenshotAssetRepository {
    constructor(dao: MatchLobbyScreenshotAssetDao) : this(dao, null)
    override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
        dao.observeByMatchId(matchId)

    override fun observeByIdentity(
        identity: MatchLobbyScreenshotIdentity,
    ): Flow<MatchLobbyScreenshotAssetEntity?> =
        dao.observeByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)

    override suspend fun getByIdentity(
        identity: MatchLobbyScreenshotIdentity,
    ): MatchLobbyScreenshotAssetEntity? =
        dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)

    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
        dao.observeByTournamentId(tournamentId)

    override fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByMatchIdAndOwner(matchId, ownerUserId)

    override fun observeByIdentityAndOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
    ): Flow<MatchLobbyScreenshotAssetEntity?> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)
            .map { asset -> asset?.takeIf { it.tournamentId == identity.tournamentId } }

    override suspend fun getByIdentityAndOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetEntity? =
        if (ownerUserId.isBlank()) null else dao.readByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)
            ?.takeIf { it.tournamentId == identity.tournamentId }

    override fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<MatchLobbyScreenshotAssetEntity>> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByTournamentIdAndOwner(tournamentId, ownerUserId)

    override suspend fun findDuplicateFingerprintAndOwner(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetEntity? =
        if (ownerUserId.isBlank()) null else dao.readDuplicateFingerprintAndOwner(sha256, identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)

    override suspend fun saveOrReplaceByOwner(
        asset: MatchLobbyScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetSaveResult {
        if (ownerUserId.isBlank()) return MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired
        val db = database ?: return MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired
        val identity = asset.identityOrNull() ?: return MatchLobbyScreenshotAssetSaveResult.InvalidIdentity
        return ScreenshotAssetMutationCoordinator.withLock(ScreenshotAssetMutationCoordinator.key(identity)) {
            db.withTransaction {
                if (!db.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                    return@withTransaction MatchLobbyScreenshotAssetSaveResult.MatchNotFound
                }
                val duplicate = dao.readDuplicateFingerprintAndOwner(asset.sha256, identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)
                if (duplicate != null) return@withTransaction MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(duplicate)
                val existing = dao.readByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)
                val ownerBoundAsset = asset.copy(ownerUserId = ownerUserId)
                val toSave = if (existing != null && existing.sha256 != asset.sha256) ownerBoundAsset.copy(
                    cropProfileId = null, cropLeft = null, cropTop = null, cropRight = null, cropBottom = null,
                ) else ownerBoundAsset
                dao.upsert(toSave)
                MatchLobbyScreenshotAssetSaveResult.Saved
            }
        }
    }

    override suspend fun restoreOrReplaceByOwner(
        asset: MatchLobbyScreenshotAssetEntity,
        ownerUserId: String,
    ): MatchLobbyScreenshotAssetSaveResult {
        if (ownerUserId.isBlank()) return MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired
        val db = database ?: return MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired
        val identity = asset.identityOrNull() ?: return MatchLobbyScreenshotAssetSaveResult.InvalidIdentity
        return ScreenshotAssetMutationCoordinator.withLock(ScreenshotAssetMutationCoordinator.key(identity)) {
            db.withTransaction {
                if (!db.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                    return@withTransaction MatchLobbyScreenshotAssetSaveResult.MatchNotFound
                }
                val duplicate = dao.readDuplicateFingerprintAndOwner(asset.sha256, identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)
                if (duplicate != null) return@withTransaction MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(duplicate)
                dao.upsert(asset.copy(ownerUserId = ownerUserId))
                MatchLobbyScreenshotAssetSaveResult.Saved
            }
        }
    }

    override suspend fun findDuplicateFingerprint(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
    ): MatchLobbyScreenshotAssetEntity? =
        dao.readDuplicateFingerprint(
            sha256 = sha256,
            matchId = identity.matchId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
        )

    override suspend fun saveOrReplace(
        asset: MatchLobbyScreenshotAssetEntity,
    ): MatchLobbyScreenshotAssetSaveResult = saveOrReplaceInternal(asset, clearCropOnReplacement = true)

    override suspend fun restoreOrReplace(
        asset: MatchLobbyScreenshotAssetEntity,
    ): MatchLobbyScreenshotAssetSaveResult = saveOrReplaceInternal(asset, clearCropOnReplacement = false)

    private suspend fun saveOrReplaceInternal(
        asset: MatchLobbyScreenshotAssetEntity,
        clearCropOnReplacement: Boolean,
    ): MatchLobbyScreenshotAssetSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(
            asset.identityOrNull() ?: return MatchLobbyScreenshotAssetSaveResult.InvalidIdentity,
        ),
    ) {
        val identity = asset.identityOrNull() ?: return@withLock MatchLobbyScreenshotAssetSaveResult.InvalidIdentity
        val duplicate = try {
            findDuplicateFingerprint(identity, asset.sha256)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchLobbyScreenshotAssetSaveResult.StateConflict
        }
        if (duplicate != null) {
            return@withLock MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(duplicate)
        }
        val existing = try {
            dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchLobbyScreenshotAssetSaveResult.StateConflict
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
        try {
            dao.upsert(assetToSave)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchLobbyScreenshotAssetSaveResult.StateConflict
        }
        MatchLobbyScreenshotAssetSaveResult.Saved
    }

    override suspend fun updateUploadSuccessIfFingerprintMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = dao.updateUploadSuccessIfFingerprintMatches(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
        sha256 = sha256,
        storageBucket = storageBucket,
        storageObjectPath = storageObjectPath,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadedAt = uploadedAt,
        updatedAt = updatedAt,
    ) > 0

    override suspend fun updateUploadFailureIfFingerprintMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = dao.updateUploadFailureIfFingerprintMatches(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
        sha256 = sha256,
        uploadStatus = ScreenshotUploadStatus.FAILED.name,
        uploadFailureCode = failureCode,
        updatedAt = updatedAt,
    ) > 0

    override suspend fun updateUploadSuccessIfGenerationMatches(
        identity: MatchLobbyScreenshotIdentity,
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
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
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
        identity: MatchLobbyScreenshotIdentity,
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
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
            sha256 = sha256,
            expectedRevision = expectedRevision,
            uploadStatus = ScreenshotUploadStatus.FAILED.name,
            uploadFailureCode = failureCode,
            updatedAt = updatedAt,
        ) > 0
    }

    override suspend fun updateUploadSuccessIfGenerationMatchesByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        if (dao.readByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId) == null) return@withTransaction false
        dao.updateUploadSuccessIfGenerationMatches(
            identity.tournamentId, identity.matchId, identity.lobbyScreenshotIndex, sha256, expectedRevision,
            storageBucket, storageObjectPath, ScreenshotUploadStatus.UPLOADED.name, uploadedAt, updatedAt,
        ) > 0
    }

    override suspend fun updateUploadFailureIfGenerationMatchesByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        if (dao.readByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId) == null) return@withTransaction false
        dao.updateUploadFailureIfGenerationMatches(
            identity.tournamentId, identity.matchId, identity.lobbyScreenshotIndex, sha256, expectedRevision,
            ScreenshotUploadStatus.FAILED.name, failureCode, updatedAt,
        ) > 0
    }

    override suspend fun markLocalMissing(
        identity: MatchLobbyScreenshotIdentity,
        updatedAt: Long,
    ) {
        dao.markLocalMissing(
            matchId = identity.matchId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
            localStatus = ScreenshotLocalStatus.MISSING.name,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markLocalMissingByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): Boolean = if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
        if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) return@withTransaction false
        dao.markLocalMissing(identity.matchId, identity.lobbyScreenshotIndex, ScreenshotLocalStatus.MISSING.name, updatedAt)
        true
    }

    override suspend fun markCleanupFailure(
        identity: MatchLobbyScreenshotIdentity,
        updatedAt: Long,
    ) {
        dao.markCleanupFailure(
            matchId = identity.matchId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
            localStatus = ScreenshotLocalStatus.CLEANUP_FAILED.name,
            updatedAt = updatedAt,
        )
    }

    override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) {
        dao.deleteByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
    }

    override suspend fun deleteByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String): Boolean =
        if (ownerUserId.isBlank() || database == null) false else database.withTransaction {
            if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                return@withTransaction false
            }
            dao.deleteByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
            true
        }

    override suspend fun deleteByMatchId(matchId: String) {
        dao.deleteByMatchId(matchId)
    }

    override suspend fun persistConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        val asset = try {
            dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        } ?: return@withLock MatchLobbyScreenshotCropSaveResult.MissingAsset
        val storedIdentity = asset.identityOrNull()
            ?: return@withLock MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        if (storedIdentity != identity) return@withLock MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return@withLock MatchLobbyScreenshotCropSaveResult.InvalidCrop
        when (OcrCropValidator.validate(crop, dimensions, OcrCropValidationProfiles.Lobby)) {
            is OcrCropValidationResult.Invalid -> MatchLobbyScreenshotCropSaveResult.InvalidCrop
            is OcrCropValidationResult.Valid -> {
                dao.updateConfirmedCrop(
                    matchId = identity.matchId,
                    lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
                    cropProfileId = OcrCropValidationProfiles.Lobby.id,
                    cropLeft = crop.left,
                    cropTop = crop.top,
                    cropRight = crop.right,
                    cropBottom = crop.bottom,
                    updatedAt = updatedAt,
                )
                MatchLobbyScreenshotCropSaveResult.Saved
            }
        }
    }

    override suspend fun clearConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        val asset = try {
            dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withLock MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        } ?: return@withLock MatchLobbyScreenshotCropSaveResult.MissingAsset
        val storedIdentity = asset.identityOrNull()
            ?: return@withLock MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        if (storedIdentity != identity) return@withLock MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        dao.clearConfirmedCrop(identity.matchId, identity.lobbyScreenshotIndex, updatedAt)
        MatchLobbyScreenshotCropSaveResult.Saved
    }

    override suspend fun persistConfirmedCropByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult {
        if (ownerUserId.isBlank() || database == null) return MatchLobbyScreenshotCropSaveResult.AuthenticationRequired
        return ScreenshotAssetMutationCoordinator.withLock(ScreenshotAssetMutationCoordinator.key(identity)) {
            database!!.withTransaction {
                if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                    return@withTransaction MatchLobbyScreenshotCropSaveResult.MatchNotFound
                }
                val asset = dao.readByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId)
                    ?: return@withTransaction MatchLobbyScreenshotCropSaveResult.MissingAsset
                val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
                    ?: return@withTransaction MatchLobbyScreenshotCropSaveResult.InvalidCrop
                when (OcrCropValidator.validate(crop, dimensions, OcrCropValidationProfiles.Lobby)) {
                    is OcrCropValidationResult.Invalid -> MatchLobbyScreenshotCropSaveResult.InvalidCrop
                    is OcrCropValidationResult.Valid -> {
                        dao.updateConfirmedCrop(identity.matchId, identity.lobbyScreenshotIndex, OcrCropValidationProfiles.Lobby.id, crop.left, crop.top, crop.right, crop.bottom, updatedAt)
                        MatchLobbyScreenshotCropSaveResult.Saved
                    }
                }
            }
        }
    }

    override suspend fun clearConfirmedCropByOwner(
        identity: MatchLobbyScreenshotIdentity,
        ownerUserId: String,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = ScreenshotAssetMutationCoordinator.withLock(
        ScreenshotAssetMutationCoordinator.key(identity),
    ) {
        if (ownerUserId.isBlank() || database == null) return@withLock MatchLobbyScreenshotCropSaveResult.AuthenticationRequired
        database!!.withTransaction {
            if (!database!!.matchDao().existsByIdAndTournamentAndOwner(identity.matchId, identity.tournamentId, ownerUserId)) {
                return@withTransaction MatchLobbyScreenshotCropSaveResult.MatchNotFound
            }
            if (dao.readByMatchAndIndexAndOwner(identity.matchId, identity.lobbyScreenshotIndex, ownerUserId) == null) {
                return@withTransaction MatchLobbyScreenshotCropSaveResult.MissingAsset
            }
            dao.clearConfirmedCrop(identity.matchId, identity.lobbyScreenshotIndex, updatedAt)
            MatchLobbyScreenshotCropSaveResult.Saved
        }
    }
}

class NoOpMatchLobbyScreenshotAssetRepository : MatchLobbyScreenshotAssetRepository {
    override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())

    override fun observeByIdentity(
        identity: MatchLobbyScreenshotIdentity,
    ): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(null)

    override suspend fun getByIdentity(
        identity: MatchLobbyScreenshotIdentity,
    ): MatchLobbyScreenshotAssetEntity? = null

    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
        flowOf(emptyList())

    override suspend fun findDuplicateFingerprint(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
    ): MatchLobbyScreenshotAssetEntity? = null

    override suspend fun saveOrReplace(
        asset: MatchLobbyScreenshotAssetEntity,
    ): MatchLobbyScreenshotAssetSaveResult = MatchLobbyScreenshotAssetSaveResult.Saved

    override suspend fun updateUploadSuccessIfFingerprintMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun updateUploadSuccessIfGenerationMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun updateUploadFailureIfGenerationMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        expectedRevision: Long,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun updateUploadFailureIfFingerprintMatches(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
        failureCode: String,
        updatedAt: Long,
    ): Boolean = false

    override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit

    override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit

    override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit

    override suspend fun deleteByMatchId(matchId: String) = Unit

    override suspend fun persistConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset

    override suspend fun clearConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset
}

fun MatchLobbyScreenshotAssetEntity.identityOrNull(): MatchLobbyScreenshotIdentity? = runCatching {
    MatchLobbyScreenshotIdentity(
        tournamentId = tournamentId,
        matchId = matchId,
        lobbyScreenshotIndex = lobbyScreenshotIndex,
    )
}.getOrNull()
