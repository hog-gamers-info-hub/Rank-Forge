package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
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
            tournamentId = identity.tournamentId,
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
}

fun MatchLobbyScreenshotAssetEntity.identityOrNull(): MatchLobbyScreenshotIdentity? = runCatching {
    MatchLobbyScreenshotIdentity(
        tournamentId = tournamentId,
        matchId = matchId,
        lobbyScreenshotIndex = lobbyScreenshotIndex,
    )
}.getOrNull()
