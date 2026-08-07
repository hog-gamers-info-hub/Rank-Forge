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

sealed interface MatchResultScreenshotAssetSaveResult {
    data object Saved : MatchResultScreenshotAssetSaveResult
    data object InvalidIdentity : MatchResultScreenshotAssetSaveResult
    data class DuplicateFingerprint(
        val existing: MatchResultScreenshotAssetEntity,
    ) : MatchResultScreenshotAssetSaveResult

    data object StateConflict : MatchResultScreenshotAssetSaveResult
}

sealed interface MatchResultScreenshotCropSaveResult {
    data object Saved : MatchResultScreenshotCropSaveResult
    data object MissingAsset : MatchResultScreenshotCropSaveResult
    data object InvalidIdentity : MatchResultScreenshotCropSaveResult
    data object InvalidCrop : MatchResultScreenshotCropSaveResult
}

interface MatchResultScreenshotAssetRepository {
    fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>>

    fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?>

    suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity?

    fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>>

    suspend fun findDuplicateFingerprint(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
    ): MatchResultScreenshotAssetEntity?

    suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult

    suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long)

    suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long)

    suspend fun persistConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult

    suspend fun clearConfirmedCrop(
        identity: MatchResultScreenshotIdentity,
        updatedAt: Long,
    ): MatchResultScreenshotCropSaveResult

    suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity)

    suspend fun deleteByMatchId(matchId: String)
}

@Singleton
class RoomMatchResultScreenshotAssetRepository @Inject constructor(
    private val dao: MatchResultScreenshotAssetDao,
) : MatchResultScreenshotAssetRepository {
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

    override suspend fun findDuplicateFingerprint(
        identity: MatchResultScreenshotIdentity,
        sha256: String,
    ): MatchResultScreenshotAssetEntity? =
        dao.readDuplicateFingerprint(
            tournamentId = identity.tournamentId,
            sha256 = sha256,
            matchId = identity.matchId,
            screenshotRole = identity.role.name,
        )

    override suspend fun saveOrReplace(
        asset: MatchResultScreenshotAssetEntity,
    ): MatchResultScreenshotAssetSaveResult {
        val identity = asset.identityOrNull() ?: return MatchResultScreenshotAssetSaveResult.InvalidIdentity
        val duplicate = try {
            findDuplicateFingerprint(identity, asset.sha256)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchResultScreenshotAssetSaveResult.StateConflict
        }
        if (duplicate != null) {
            return MatchResultScreenshotAssetSaveResult.DuplicateFingerprint(duplicate)
        }
        val existing = try {
            dao.readByMatchAndRole(identity.matchId, identity.role.name)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchResultScreenshotAssetSaveResult.StateConflict
        }
        val assetToSave = if (existing != null && existing.sha256 != asset.sha256) {
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
        return MatchResultScreenshotAssetSaveResult.Saved
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
    ): MatchResultScreenshotCropSaveResult {
        val asset = getByIdentity(identity) ?: return MatchResultScreenshotCropSaveResult.MissingAsset
        if (asset.identityOrNull() == null) return MatchResultScreenshotCropSaveResult.InvalidIdentity
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return MatchResultScreenshotCropSaveResult.InvalidCrop
        return when (OcrCropValidator.validate(crop, dimensions, OcrCropValidationProfiles.MatchResult)) {
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
    ): MatchResultScreenshotCropSaveResult {
        if (getByIdentity(identity) == null) return MatchResultScreenshotCropSaveResult.MissingAsset
        dao.clearConfirmedCrop(identity.matchId, identity.role.name, updatedAt)
        return MatchResultScreenshotCropSaveResult.Saved
    }

    override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) {
        dao.deleteByMatchAndRole(identity.matchId, identity.role.name)
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
