package com.hoggamers.rankforge.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import androidx.room.withTransaction

sealed interface ScreenshotMetadataMutationResult {
    data object Saved : ScreenshotMetadataMutationResult
    data object AuthenticationRequired : ScreenshotMetadataMutationResult
    data object MatchNotFound : ScreenshotMetadataMutationResult
}

enum class ScreenshotMetadataFailureCode {
    ROOM_WRITE_FAILED,
    LOCAL_FILE_MISSING,
    CLEANUP_FAILED,
    CLOUD_METADATA_WRITE_FAILED,
    RLS_DENIED,
    FINALIZED_MATCH_PROTECTED,
}

interface ScreenshotMetadataRepository {
    fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?>

    suspend fun getByMatchId(matchId: String): ScreenshotMetadataEntity?

    fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>>

    fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<ScreenshotMetadataEntity?> = flowOf(null)

    suspend fun getByMatchIdAndOwner(matchId: String, ownerUserId: String): ScreenshotMetadataEntity? = null

    fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<ScreenshotMetadataEntity>> = flowOf(emptyList())

    suspend fun createOrReplaceByOwner(
        metadata: ScreenshotMetadataEntity,
        ownerUserId: String,
    ): ScreenshotMetadataMutationResult = ScreenshotMetadataMutationResult.AuthenticationRequired

    suspend fun updateUploadSuccessByOwner(
        matchId: String,
        ownerUserId: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): ScreenshotMetadataMutationResult = ScreenshotMetadataMutationResult.AuthenticationRequired

    suspend fun updateUploadFailureByOwner(
        matchId: String,
        ownerUserId: String,
        failureCode: String,
        updatedAt: Long,
    ): ScreenshotMetadataMutationResult = ScreenshotMetadataMutationResult.AuthenticationRequired

    suspend fun markLocalMissingByOwner(
        matchId: String,
        ownerUserId: String,
        updatedAt: Long,
    ): ScreenshotMetadataMutationResult = ScreenshotMetadataMutationResult.AuthenticationRequired

    suspend fun markCleanupFailureByOwner(
        matchId: String,
        ownerUserId: String,
        updatedAt: Long,
    ): ScreenshotMetadataMutationResult = ScreenshotMetadataMutationResult.AuthenticationRequired

    suspend fun deleteByMatchIdAndOwner(
        matchId: String,
        ownerUserId: String,
    ): ScreenshotMetadataMutationResult = ScreenshotMetadataMutationResult.AuthenticationRequired

    suspend fun createOrReplace(metadata: ScreenshotMetadataEntity)

    suspend fun updateUploadSuccess(
        matchId: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    )

    suspend fun updateUploadFailure(
        matchId: String,
        failureCode: String,
        updatedAt: Long,
    )

    suspend fun markLocalMissing(matchId: String, updatedAt: Long)

    suspend fun markCleanupFailure(matchId: String, updatedAt: Long)

    suspend fun deleteByMatchId(matchId: String)

    suspend fun deleteByTournamentId(tournamentId: String)
}

@Singleton
class RoomScreenshotMetadataRepository @Inject constructor(
    private val dao: ScreenshotMetadataDao,
    private val database: RankForgeDatabase,
) : ScreenshotMetadataRepository {
    override fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?> =
        dao.observeByMatchId(matchId)

    override suspend fun getByMatchId(matchId: String): ScreenshotMetadataEntity? =
        dao.readByMatchId(matchId)

    override fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>> =
        dao.observeByTournamentId(tournamentId)

    override fun observeByMatchIdAndOwner(matchId: String, ownerUserId: String): Flow<ScreenshotMetadataEntity?> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByMatchIdAndOwner(matchId, ownerUserId)

    override suspend fun getByMatchIdAndOwner(matchId: String, ownerUserId: String): ScreenshotMetadataEntity? =
        if (ownerUserId.isBlank()) null else dao.readByMatchIdAndOwner(matchId, ownerUserId)

    override fun observeByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<ScreenshotMetadataEntity>> =
        if (ownerUserId.isBlank()) emptyFlow() else dao.observeByTournamentIdAndOwner(tournamentId, ownerUserId)

    override suspend fun createOrReplaceByOwner(
        metadata: ScreenshotMetadataEntity,
        ownerUserId: String,
    ): ScreenshotMetadataMutationResult {
        if (ownerUserId.isBlank()) return ScreenshotMetadataMutationResult.AuthenticationRequired
        return database.withTransaction {
            if (!database.matchDao().existsByIdAndTournamentAndOwner(metadata.matchId, metadata.tournamentId, ownerUserId)) {
                return@withTransaction ScreenshotMetadataMutationResult.MatchNotFound
            }
            dao.upsert(metadata.copy(ownerUserId = ownerUserId))
            ScreenshotMetadataMutationResult.Saved
        }
    }

    override suspend fun updateUploadSuccessByOwner(
        matchId: String,
        ownerUserId: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): ScreenshotMetadataMutationResult = database.withTransaction {
        if (ownerUserId.isBlank()) return@withTransaction ScreenshotMetadataMutationResult.AuthenticationRequired
        val match = database.matchDao().observeByIdAndOwner(matchId, ownerUserId).first()
            ?: return@withTransaction ScreenshotMetadataMutationResult.MatchNotFound
        dao.updateUploadSuccess(matchId, storageBucket, storageObjectPath, ScreenshotUploadStatus.UPLOADED.name, uploadedAt, updatedAt)
        ScreenshotMetadataMutationResult.Saved
    }

    override suspend fun updateUploadFailureByOwner(
        matchId: String,
        ownerUserId: String,
        failureCode: String,
        updatedAt: Long,
    ): ScreenshotMetadataMutationResult = database.withTransaction {
        if (ownerUserId.isBlank()) return@withTransaction ScreenshotMetadataMutationResult.AuthenticationRequired
        if (database.matchDao().observeByIdAndOwner(matchId, ownerUserId).first() == null) {
            return@withTransaction ScreenshotMetadataMutationResult.MatchNotFound
        }
        dao.updateUploadFailure(matchId, ScreenshotUploadStatus.FAILED.name, failureCode, updatedAt)
        ScreenshotMetadataMutationResult.Saved
    }

    override suspend fun markLocalMissingByOwner(matchId: String, ownerUserId: String, updatedAt: Long): ScreenshotMetadataMutationResult =
        database.withTransaction {
            if (ownerUserId.isBlank()) return@withTransaction ScreenshotMetadataMutationResult.AuthenticationRequired
            if (database.matchDao().observeByIdAndOwner(matchId, ownerUserId).first() == null) {
                return@withTransaction ScreenshotMetadataMutationResult.MatchNotFound
            }
            dao.markLocalMissing(matchId, ScreenshotLocalStatus.MISSING.name, updatedAt)
            ScreenshotMetadataMutationResult.Saved
        }

    override suspend fun markCleanupFailureByOwner(matchId: String, ownerUserId: String, updatedAt: Long): ScreenshotMetadataMutationResult =
        database.withTransaction {
            if (ownerUserId.isBlank()) return@withTransaction ScreenshotMetadataMutationResult.AuthenticationRequired
            if (database.matchDao().observeByIdAndOwner(matchId, ownerUserId).first() == null) {
                return@withTransaction ScreenshotMetadataMutationResult.MatchNotFound
            }
            dao.markCleanupFailure(matchId, ScreenshotLocalStatus.CLEANUP_FAILED.name, updatedAt)
            ScreenshotMetadataMutationResult.Saved
        }

    override suspend fun deleteByMatchIdAndOwner(matchId: String, ownerUserId: String): ScreenshotMetadataMutationResult =
        database.withTransaction {
            if (ownerUserId.isBlank()) return@withTransaction ScreenshotMetadataMutationResult.AuthenticationRequired
            if (database.matchDao().observeByIdAndOwner(matchId, ownerUserId).first() == null) {
                return@withTransaction ScreenshotMetadataMutationResult.MatchNotFound
            }
            dao.deleteByMatchId(matchId)
            ScreenshotMetadataMutationResult.Saved
        }

    override suspend fun createOrReplace(metadata: ScreenshotMetadataEntity) {
        dao.upsert(metadata)
    }

    override suspend fun updateUploadSuccess(
        matchId: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ) {
        dao.updateUploadSuccess(
            matchId = matchId,
            storageBucket = storageBucket,
            storageObjectPath = storageObjectPath,
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadedAt = uploadedAt,
            updatedAt = updatedAt,
        )
    }

    override suspend fun updateUploadFailure(
        matchId: String,
        failureCode: String,
        updatedAt: Long,
    ) {
        dao.updateUploadFailure(
            matchId = matchId,
            uploadStatus = ScreenshotUploadStatus.FAILED.name,
            failureCode = failureCode,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markLocalMissing(matchId: String, updatedAt: Long) {
        dao.markLocalMissing(
            matchId = matchId,
            localStatus = ScreenshotLocalStatus.MISSING.name,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markCleanupFailure(matchId: String, updatedAt: Long) {
        dao.markCleanupFailure(
            matchId = matchId,
            localStatus = ScreenshotLocalStatus.CLEANUP_FAILED.name,
            updatedAt = updatedAt,
        )
    }

    override suspend fun deleteByMatchId(matchId: String) {
        dao.deleteByMatchId(matchId)
    }

    override suspend fun deleteByTournamentId(tournamentId: String) {
        dao.deleteByTournamentId(tournamentId)
    }
}

class NoOpScreenshotMetadataRepository : ScreenshotMetadataRepository {
    override fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?> =
        kotlinx.coroutines.flow.flowOf(null)

    override suspend fun getByMatchId(matchId: String): ScreenshotMetadataEntity? = null

    override fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun createOrReplace(metadata: ScreenshotMetadataEntity) = Unit

    override suspend fun updateUploadSuccess(
        matchId: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadedAt: Long,
        updatedAt: Long,
    ) = Unit

    override suspend fun updateUploadFailure(
        matchId: String,
        failureCode: String,
        updatedAt: Long,
    ) = Unit

    override suspend fun markLocalMissing(matchId: String, updatedAt: Long) = Unit

    override suspend fun markCleanupFailure(matchId: String, updatedAt: Long) = Unit

    override suspend fun deleteByMatchId(matchId: String) = Unit

    override suspend fun deleteByTournamentId(tournamentId: String) = Unit
}
