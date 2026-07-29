package com.hoggamers.rankforge.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

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
) : ScreenshotMetadataRepository {
    override fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?> =
        dao.observeByMatchId(matchId)

    override suspend fun getByMatchId(matchId: String): ScreenshotMetadataEntity? =
        dao.readByMatchId(matchId)

    override fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>> =
        dao.observeByTournamentId(tournamentId)

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
