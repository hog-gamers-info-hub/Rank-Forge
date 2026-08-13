package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
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

sealed interface MatchLobbyScreenshotAssetSaveResult {
    data object Saved : MatchLobbyScreenshotAssetSaveResult
    data object InvalidIdentity : MatchLobbyScreenshotAssetSaveResult
    data class DuplicateFingerprint(
        val existing: MatchLobbyScreenshotAssetEntity,
    ) : MatchLobbyScreenshotAssetSaveResult

    data object StateConflict : MatchLobbyScreenshotAssetSaveResult
}

sealed interface MatchLobbyScreenshotCropSaveResult {
    data object Saved : MatchLobbyScreenshotCropSaveResult
    data object MissingAsset : MatchLobbyScreenshotCropSaveResult
    data object InvalidIdentity : MatchLobbyScreenshotCropSaveResult
    data object InvalidCrop : MatchLobbyScreenshotCropSaveResult
}

interface MatchLobbyScreenshotAssetRepository {
    fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>>

    fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?>

    suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity?

    fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>>

    suspend fun findDuplicateFingerprint(
        identity: MatchLobbyScreenshotIdentity,
        sha256: String,
    ): MatchLobbyScreenshotAssetEntity?

    suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult

    suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long)

    suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long)

    suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity)

    suspend fun deleteByMatchId(matchId: String)

    suspend fun persistConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult

    suspend fun clearConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult
}

@Singleton
class RoomMatchLobbyScreenshotAssetRepository @Inject constructor(
    private val dao: MatchLobbyScreenshotAssetDao,
) : MatchLobbyScreenshotAssetRepository {
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
    ): MatchLobbyScreenshotAssetSaveResult {
        val identity = asset.identityOrNull() ?: return MatchLobbyScreenshotAssetSaveResult.InvalidIdentity
        val duplicate = try {
            findDuplicateFingerprint(identity, asset.sha256)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchLobbyScreenshotAssetSaveResult.StateConflict
        }
        if (duplicate != null) {
            return MatchLobbyScreenshotAssetSaveResult.DuplicateFingerprint(duplicate)
        }
        val existing = try {
            dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchLobbyScreenshotAssetSaveResult.StateConflict
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
        try {
            dao.upsert(assetToSave)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchLobbyScreenshotAssetSaveResult.StateConflict
        }
        return MatchLobbyScreenshotAssetSaveResult.Saved
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

    override suspend fun deleteByMatchId(matchId: String) {
        dao.deleteByMatchId(matchId)
    }

    override suspend fun persistConfirmedCrop(
        identity: MatchLobbyScreenshotIdentity,
        crop: OcrNormalizedCropRect,
        updatedAt: Long,
    ): MatchLobbyScreenshotCropSaveResult {
        val asset = try {
            dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        } ?: return MatchLobbyScreenshotCropSaveResult.MissingAsset
        val storedIdentity = asset.identityOrNull()
            ?: return MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        if (storedIdentity != identity) return MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return MatchLobbyScreenshotCropSaveResult.InvalidCrop
        return when (OcrCropValidator.validate(crop, dimensions, OcrCropValidationProfiles.Lobby)) {
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
    ): MatchLobbyScreenshotCropSaveResult {
        val asset = try {
            dao.readByMatchAndIndex(identity.matchId, identity.lobbyScreenshotIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        } ?: return MatchLobbyScreenshotCropSaveResult.MissingAsset
        val storedIdentity = asset.identityOrNull()
            ?: return MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        if (storedIdentity != identity) return MatchLobbyScreenshotCropSaveResult.InvalidIdentity
        dao.clearConfirmedCrop(identity.matchId, identity.lobbyScreenshotIndex, updatedAt)
        return MatchLobbyScreenshotCropSaveResult.Saved
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
