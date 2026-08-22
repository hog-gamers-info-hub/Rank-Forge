package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRevisionDao {
    @Query("SELECT * FROM sync_revisions WHERE tournament_id = :tournamentId")
    suspend fun readByTournamentId(tournamentId: String): SyncRevisionEntity?

    @Upsert
    suspend fun upsert(revision: SyncRevisionEntity)

    @Query("DELETE FROM sync_revisions WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)

    @Query("UPDATE sync_revisions SET local_revision = local_revision + 1 WHERE tournament_id = :tournamentId")
    suspend fun incrementLocalRevision(tournamentId: String)
}

@Dao
interface DeletionIntentDao {
    @Query("SELECT * FROM deletion_intents WHERE target_type = :targetType AND target_id = :targetId")
    suspend fun read(targetType: String, targetId: String): DeletionIntentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(intent: DeletionIntentEntity)

    @Query(
        "UPDATE deletion_intents SET phase = 'REMOTE_DELETED_LOCAL_CLEANUP_PENDING', " +
            "updated_at_epoch_millis = :updatedAtEpochMillis " +
            "WHERE target_type = :targetType AND target_id = :targetId",
    )
    suspend fun markRemoteDeleted(targetType: String, targetId: String, updatedAtEpochMillis: Long)

    @Query("DELETE FROM deletion_intents WHERE target_type = :targetType AND target_id = :targetId")
    suspend fun delete(targetType: String, targetId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM deletion_intents WHERE tournament_id = :tournamentId)")
    suspend fun isBlocking(tournamentId: String): Boolean

    @Query("SELECT * FROM deletion_intents ORDER BY updated_at_epoch_millis")
    suspend fun readAll(): List<DeletionIntentEntity>

    @Query(
        "SELECT * FROM deletion_intents " +
            "WHERE phase = 'REMOTE_DELETED_LOCAL_CLEANUP_PENDING' ORDER BY updated_at_epoch_millis",
    )
    suspend fun readPendingLocalCleanup(): List<DeletionIntentEntity>
}

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY creation_order, id")
    fun observeAll(): Flow<List<TournamentEntity>>

    @Query(
        """
        SELECT
            tournaments.id,
            tournaments.name,
            tournaments.date,
            tournaments.organizer_name,
            tournaments.organizer_contact_number,
            tournaments.status,
            (
                SELECT COUNT(*) FROM team_slots
                WHERE team_slots.tournament_id = tournaments.id
                    AND TRIM(team_slots.team_name) <> ''
            ) AS total_teams,
            (
                SELECT COUNT(*) FROM matches
                WHERE matches.tournament_id = tournaments.id
            ) AS total_matches,
            tournaments.last_updated_epoch_millis
        FROM tournaments
        ORDER BY tournaments.creation_order, tournaments.id
        """,
    )
    fun observeSummaries(): Flow<List<TournamentSummaryProjection>>

    @Query("SELECT * FROM tournaments WHERE id = :tournamentId")
    fun observeById(tournamentId: String): Flow<TournamentEntity?>

    @Upsert
    suspend fun upsert(tournament: TournamentEntity)

    @Query("UPDATE tournaments SET last_updated_epoch_millis = :lastUpdatedEpochMillis WHERE id = :tournamentId")
    suspend fun updateLastUpdatedEpochMillis(tournamentId: String, lastUpdatedEpochMillis: Long)

    @Query("SELECT COALESCE(MAX(creation_order), 0) + 1 FROM tournaments")
    suspend fun nextCreationOrder(): Long

    @Query("DELETE FROM tournaments WHERE id = :tournamentId")
    suspend fun deleteById(tournamentId: String)
}

@Dao
interface TeamSlotDao {
    @Query("SELECT * FROM team_slots WHERE tournament_id = :tournamentId ORDER BY slot_number")
    fun observeByTournamentId(tournamentId: String): Flow<List<TeamSlotEntity>>

    @Upsert
    suspend fun upsertAll(teamSlots: List<TeamSlotEntity>)

    @Query("DELETE FROM team_slots WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}

@Dao
interface RosterPlayerDao {
    @Query(
        """
        SELECT * FROM roster_players
        WHERE tournament_id = :tournamentId
        ORDER BY slot_number, roster_position
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<RosterPlayerEntity>>

    @Query(
        """
        SELECT * FROM roster_players
        WHERE tournament_id = :tournamentId AND slot_number = :slotNumber
        ORDER BY roster_position
        """,
    )
    fun observeByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayerEntity>>

    @Upsert
    suspend fun upsertAll(rosterPlayers: List<RosterPlayerEntity>)

    @Query(
        "DELETE FROM roster_players WHERE tournament_id = :tournamentId AND slot_number = :slotNumber",
    )
    suspend fun deleteByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    )
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY tournament_id, match_number, id")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE tournament_id = :tournamentId ORDER BY match_number, id")
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    fun observeById(matchId: String): Flow<MatchEntity?>

    @Upsert
    suspend fun upsert(match: MatchEntity)

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteById(matchId: String)

    @Query("DELETE FROM matches WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)

    @Query("DELETE FROM matches WHERE tournament_id = :tournamentId AND status = 'DRAFT'")
    suspend fun deleteDraftByTournamentId(tournamentId: String)
}

@Dao
interface ScreenshotMetadataDao {
    @Query("SELECT * FROM screenshot_metadata WHERE match_id = :matchId")
    fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?>

    @Query("SELECT * FROM screenshot_metadata WHERE match_id = :matchId")
    suspend fun readByMatchId(matchId: String): ScreenshotMetadataEntity?

    @Query("SELECT * FROM screenshot_metadata WHERE tournament_id = :tournamentId ORDER BY updated_at DESC, match_id")
    fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>>

    @Upsert
    suspend fun upsert(metadata: ScreenshotMetadataEntity)

    @Query(
        """
        UPDATE screenshot_metadata
        SET storage_bucket = :storageBucket,
            storage_object_path = :storageObjectPath,
            upload_status = :uploadStatus,
            upload_failure_code = NULL,
            uploaded_at = :uploadedAt,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
        """,
    )
    suspend fun updateUploadSuccess(
        matchId: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadStatus: String,
        uploadedAt: Long,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE screenshot_metadata
        SET upload_status = :uploadStatus,
            upload_failure_code = :failureCode,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
        """,
    )
    suspend fun updateUploadFailure(
        matchId: String,
        uploadStatus: String,
        failureCode: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE screenshot_metadata
        SET local_status = :localStatus,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
        """,
    )
    suspend fun markLocalMissing(matchId: String, localStatus: String, updatedAt: Long)

    @Query(
        """
        UPDATE screenshot_metadata
        SET local_status = :localStatus,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
        """,
    )
    suspend fun markCleanupFailure(matchId: String, localStatus: String, updatedAt: Long)

    @Query("DELETE FROM screenshot_metadata WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)

    @Query("DELETE FROM screenshot_metadata WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}

@Dao
interface RosterScreenshotMetadataDao {
    @Query(
        "SELECT * FROM roster_screenshot_metadata WHERE tournament_id = :tournamentId ORDER BY roster_screenshot_index",
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>>

    @Query(
        "SELECT * FROM roster_screenshot_metadata WHERE tournament_id = :tournamentId AND roster_screenshot_index = :index",
    )
    suspend fun readByTournamentAndIndex(
        tournamentId: String,
        index: Int,
    ): RosterScreenshotMetadataEntity?

    @Query(
        "SELECT * FROM roster_screenshot_metadata WHERE tournament_id = :tournamentId " +
            "ORDER BY roster_screenshot_index",
    )
    suspend fun readByTournamentId(tournamentId: String): List<RosterScreenshotMetadataEntity>

    @Query(
        "SELECT * FROM roster_screenshot_metadata WHERE tournament_id = :tournamentId AND sha256 = :sha256 AND roster_screenshot_index != :index LIMIT 1",
    )
    suspend fun readDuplicateFingerprint(
        tournamentId: String,
        sha256: String,
        index: Int,
    ): RosterScreenshotMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: RosterScreenshotMetadataEntity)

    @Query(
        "DELETE FROM roster_screenshot_metadata WHERE tournament_id = :tournamentId AND roster_screenshot_index = :index",
    )
    suspend fun deleteByTournamentAndIndex(
        tournamentId: String,
        index: Int,
    )
}

@Dao
interface MatchResultScreenshotAssetDao {
    @Query(
        """
        SELECT * FROM match_result_screenshot_assets
        WHERE match_id = :matchId
        ORDER BY
            CASE screenshot_role
                WHEN 'MATCH_RESULT_UPPER' THEN 0
                WHEN 'MATCH_RESULT_LOWER' THEN 1
                ELSE 2
            END,
            screenshot_role
        """,
    )
    fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>>

    @Query(
        """
        SELECT * FROM match_result_screenshot_assets
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    fun observeByMatchAndRole(
        matchId: String,
        screenshotRole: String,
    ): Flow<MatchResultScreenshotAssetEntity?>

    @Query(
        """
        SELECT * FROM match_result_screenshot_assets
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    suspend fun readByMatchAndRole(
        matchId: String,
        screenshotRole: String,
    ): MatchResultScreenshotAssetEntity?

    @Query(
        """
        SELECT * FROM match_result_screenshot_assets
        WHERE tournament_id = :tournamentId
        ORDER BY match_id,
            CASE screenshot_role
                WHEN 'MATCH_RESULT_UPPER' THEN 0
                WHEN 'MATCH_RESULT_LOWER' THEN 1
                ELSE 2
            END,
            screenshot_role
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>>

    @Query(
        """
        SELECT * FROM match_result_screenshot_assets
        WHERE tournament_id = :tournamentId
        ORDER BY match_id,
            CASE screenshot_role
                WHEN 'MATCH_RESULT_UPPER' THEN 0
                WHEN 'MATCH_RESULT_LOWER' THEN 1
                ELSE 2
            END,
            screenshot_role
        """,
    )
    suspend fun readByTournamentId(tournamentId: String): List<MatchResultScreenshotAssetEntity>

    @Query(
        """
        SELECT * FROM match_result_screenshot_assets
        WHERE match_id = :matchId
            AND sha256 = :sha256
            AND screenshot_role != :screenshotRole
        LIMIT 1
        """,
    )
    suspend fun readDuplicateFingerprint(
        sha256: String,
        matchId: String,
        screenshotRole: String,
    ): MatchResultScreenshotAssetEntity?

    @Upsert
    suspend fun upsert(asset: MatchResultScreenshotAssetEntity)

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET storage_bucket = :storageBucket,
            storage_object_path = :storageObjectPath,
            upload_status = :uploadStatus,
            upload_failure_code = NULL,
            uploaded_at = :uploadedAt,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND screenshot_role = :screenshotRole
            AND sha256 = :sha256
        """,
    )
    suspend fun updateUploadSuccessIfFingerprintMatches(
        tournamentId: String,
        matchId: String,
        screenshotRole: String,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadStatus: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET storage_bucket = :storageBucket,
            storage_object_path = :storageObjectPath,
            upload_status = :uploadStatus,
            upload_failure_code = NULL,
            uploaded_at = :uploadedAt,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND screenshot_role = :screenshotRole
            AND sha256 = :sha256
            AND revision = :expectedRevision
        """,
    )
    suspend fun updateUploadSuccessIfGenerationMatches(
        tournamentId: String,
        matchId: String,
        screenshotRole: String,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadStatus: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET upload_status = :uploadStatus,
            upload_failure_code = :uploadFailureCode,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND screenshot_role = :screenshotRole
            AND sha256 = :sha256
        """,
    )
    suspend fun updateUploadFailureIfFingerprintMatches(
        tournamentId: String,
        matchId: String,
        screenshotRole: String,
        sha256: String,
        uploadStatus: String,
        uploadFailureCode: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET upload_status = :uploadStatus,
            upload_failure_code = :uploadFailureCode,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND screenshot_role = :screenshotRole
            AND sha256 = :sha256
            AND revision = :expectedRevision
        """,
    )
    suspend fun updateUploadFailureIfGenerationMatches(
        tournamentId: String,
        matchId: String,
        screenshotRole: String,
        sha256: String,
        expectedRevision: Long,
        uploadStatus: String,
        uploadFailureCode: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET local_status = :localStatus,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    suspend fun markLocalMissing(
        matchId: String,
        screenshotRole: String,
        localStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET local_status = :localStatus,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    suspend fun markCleanupFailure(
        matchId: String,
        screenshotRole: String,
        localStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET crop_profile_id = :cropProfileId,
            crop_left = :cropLeft,
            crop_top = :cropTop,
            crop_right = :cropRight,
            crop_bottom = :cropBottom,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    suspend fun updateConfirmedCrop(
        matchId: String,
        screenshotRole: String,
        cropProfileId: String,
        cropLeft: Double,
        cropTop: Double,
        cropRight: Double,
        cropBottom: Double,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE match_result_screenshot_assets
        SET crop_profile_id = NULL,
            crop_left = NULL,
            crop_top = NULL,
            crop_right = NULL,
            crop_bottom = NULL,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    suspend fun clearConfirmedCrop(
        matchId: String,
        screenshotRole: String,
        updatedAt: Long,
    )

    @Query(
        "DELETE FROM match_result_screenshot_assets WHERE match_id = :matchId AND screenshot_role = :screenshotRole",
    )
    suspend fun deleteByMatchAndRole(
        matchId: String,
        screenshotRole: String,
    )

    @Query("DELETE FROM match_result_screenshot_assets WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface MatchResultOcrCacheDao {
    @Query(
        """
        SELECT * FROM match_result_ocr_cache
        WHERE match_id = :matchId AND screenshot_role = :screenshotRole
        """,
    )
    suspend fun readByMatchAndRole(
        matchId: String,
        screenshotRole: String,
    ): MatchResultOcrCacheEntity?

    @Upsert
    suspend fun upsert(cache: MatchResultOcrCacheEntity)

    @Query(
        "DELETE FROM match_result_ocr_cache WHERE match_id = :matchId AND screenshot_role = :screenshotRole",
    )
    suspend fun deleteByMatchAndRole(matchId: String, screenshotRole: String)
}

@Dao
interface MatchLobbyOcrCacheDao {
    @Query(
        """
        SELECT * FROM match_lobby_ocr_cache
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    suspend fun readByMatchAndIndex(
        matchId: String,
        lobbyScreenshotIndex: Int,
    ): MatchLobbyOcrCacheEntity?

    @Upsert
    suspend fun upsert(cache: MatchLobbyOcrCacheEntity)

    @Query(
        "DELETE FROM match_lobby_ocr_cache WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex",
    )
    suspend fun deleteByMatchAndIndex(matchId: String, lobbyScreenshotIndex: Int)
}

@Dao
interface MatchLobbyScreenshotAssetDao {
    @Query(
        """
        SELECT * FROM match_lobby_screenshot_assets
        WHERE match_id = :matchId
        ORDER BY lobby_screenshot_index ASC
        """,
    )
    fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>>

    @Query(
        """
        SELECT * FROM match_lobby_screenshot_assets
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    fun observeByMatchAndIndex(
        matchId: String,
        lobbyScreenshotIndex: Int,
    ): Flow<MatchLobbyScreenshotAssetEntity?>

    @Query(
        """
        SELECT * FROM match_lobby_screenshot_assets
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    suspend fun readByMatchAndIndex(
        matchId: String,
        lobbyScreenshotIndex: Int,
    ): MatchLobbyScreenshotAssetEntity?

    @Query(
        """
        SELECT * FROM match_lobby_screenshot_assets
        WHERE tournament_id = :tournamentId
        ORDER BY match_id ASC, lobby_screenshot_index ASC
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>>

    @Query(
        """
        SELECT * FROM match_lobby_screenshot_assets
        WHERE tournament_id = :tournamentId
        ORDER BY match_id ASC, lobby_screenshot_index ASC
        """,
    )
    suspend fun readByTournamentId(tournamentId: String): List<MatchLobbyScreenshotAssetEntity>

    @Query(
        """
        SELECT * FROM match_lobby_screenshot_assets
        WHERE match_id = :matchId
            AND sha256 = :sha256
            AND lobby_screenshot_index != :lobbyScreenshotIndex
        LIMIT 1
        """,
    )
    suspend fun readDuplicateFingerprint(
        sha256: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
    ): MatchLobbyScreenshotAssetEntity?

    @Upsert
    suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity)

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET storage_bucket = :storageBucket,
            storage_object_path = :storageObjectPath,
            upload_status = :uploadStatus,
            upload_failure_code = NULL,
            uploaded_at = :uploadedAt,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND lobby_screenshot_index = :lobbyScreenshotIndex
            AND sha256 = :sha256
        """,
    )
    suspend fun updateUploadSuccessIfFingerprintMatches(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        sha256: String,
        storageBucket: String,
        storageObjectPath: String,
        uploadStatus: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET storage_bucket = :storageBucket,
            storage_object_path = :storageObjectPath,
            upload_status = :uploadStatus,
            upload_failure_code = NULL,
            uploaded_at = :uploadedAt,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND lobby_screenshot_index = :lobbyScreenshotIndex
            AND sha256 = :sha256
            AND revision = :expectedRevision
        """,
    )
    suspend fun updateUploadSuccessIfGenerationMatches(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        sha256: String,
        expectedRevision: Long,
        storageBucket: String,
        storageObjectPath: String,
        uploadStatus: String,
        uploadedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET upload_status = :uploadStatus,
            upload_failure_code = :uploadFailureCode,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND lobby_screenshot_index = :lobbyScreenshotIndex
            AND sha256 = :sha256
        """,
    )
    suspend fun updateUploadFailureIfFingerprintMatches(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        sha256: String,
        uploadStatus: String,
        uploadFailureCode: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET upload_status = :uploadStatus,
            upload_failure_code = :uploadFailureCode,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId
            AND tournament_id = :tournamentId
            AND lobby_screenshot_index = :lobbyScreenshotIndex
            AND sha256 = :sha256
            AND revision = :expectedRevision
        """,
    )
    suspend fun updateUploadFailureIfGenerationMatches(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        sha256: String,
        expectedRevision: Long,
        uploadStatus: String,
        uploadFailureCode: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET local_status = :localStatus,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    suspend fun markLocalMissing(
        matchId: String,
        lobbyScreenshotIndex: Int,
        localStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET local_status = :localStatus,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    suspend fun markCleanupFailure(
        matchId: String,
        lobbyScreenshotIndex: Int,
        localStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET crop_profile_id = :cropProfileId,
            crop_left = :cropLeft,
            crop_top = :cropTop,
            crop_right = :cropRight,
            crop_bottom = :cropBottom,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    suspend fun updateConfirmedCrop(
        matchId: String,
        lobbyScreenshotIndex: Int,
        cropProfileId: String,
        cropLeft: Double,
        cropTop: Double,
        cropRight: Double,
        cropBottom: Double,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE match_lobby_screenshot_assets
        SET crop_profile_id = NULL,
            crop_left = NULL,
            crop_top = NULL,
            crop_right = NULL,
            crop_bottom = NULL,
            updated_at = :updatedAt,
            revision = revision + 1
        WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex
        """,
    )
    suspend fun clearConfirmedCrop(
        matchId: String,
        lobbyScreenshotIndex: Int,
        updatedAt: Long,
    )

    @Query(
        "DELETE FROM match_lobby_screenshot_assets WHERE match_id = :matchId AND lobby_screenshot_index = :lobbyScreenshotIndex",
    )
    suspend fun deleteByMatchAndIndex(matchId: String, lobbyScreenshotIndex: Int)

    @Query("DELETE FROM match_lobby_screenshot_assets WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface TournamentLobbyTemplateAssetDao {
    @Query(
        """
        SELECT * FROM tournament_lobby_template_assets
        WHERE tournament_id = :tournamentId
        ORDER BY lobby_screenshot_index ASC
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>>

    @Query(
        """
        SELECT * FROM tournament_lobby_template_assets
        WHERE tournament_id = :tournamentId
        ORDER BY lobby_screenshot_index ASC
        """,
    )
    suspend fun readByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity>

    @Upsert
    suspend fun upsertAll(assets: List<TournamentLobbyTemplateAssetEntity>)

    @Query("DELETE FROM tournament_lobby_template_assets WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)

    @Transaction
    suspend fun replaceForTournament(
        tournamentId: String,
        assets: List<TournamentLobbyTemplateAssetEntity>,
    ) {
        deleteByTournamentId(tournamentId)
        upsertAll(assets)
    }
}

@Dao
interface MatchOcrEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMatchEvidence(entity: MatchOcrEvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRowEvidence(entities: List<MatchOcrRowEvidenceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorrectionSnapshots(entities: List<MatchOcrCorrectionSnapshotEntity>)

    @Query("SELECT * FROM match_ocr_evidence WHERE match_id = :matchId")
    suspend fun readMatchEvidence(matchId: String): MatchOcrEvidenceEntity?

    @Query("SELECT * FROM match_ocr_row_evidence WHERE match_id = :matchId ORDER BY row_index")
    suspend fun readRowEvidence(matchId: String): List<MatchOcrRowEvidenceEntity>

    @Query("SELECT * FROM match_ocr_correction_snapshots WHERE match_id = :matchId ORDER BY row_index")
    suspend fun readCorrectionSnapshots(matchId: String): List<MatchOcrCorrectionSnapshotEntity>

    @Transaction
    suspend fun insertSnapshot(
        matchEvidence: MatchOcrEvidenceEntity,
        rowEvidence: List<MatchOcrRowEvidenceEntity>,
        correctionSnapshots: List<MatchOcrCorrectionSnapshotEntity>,
    ) {
        insertMatchEvidence(matchEvidence)
        insertRowEvidence(rowEvidence)
        insertCorrectionSnapshots(correctionSnapshots)
    }
}

@Dao
interface MatchPlacementDao {
    @Query(
        """
        SELECT match_placements.* FROM match_placements
        INNER JOIN matches ON matches.id = match_placements.match_id
        WHERE matches.tournament_id = :tournamentId
        ORDER BY matches.match_number, matches.id, match_placements.team_slot_number
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchPlacementEntity>>

    @Query("SELECT * FROM match_placements WHERE match_id = :matchId ORDER BY team_slot_number")
    fun observeByMatchId(matchId: String): Flow<List<MatchPlacementEntity>>

    @Upsert
    suspend fun upsertAll(placements: List<MatchPlacementEntity>)

    @Query("DELETE FROM match_placements WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface MatchKillDao {
    @Query(
        """
        SELECT match_kills.* FROM match_kills
        INNER JOIN matches ON matches.id = match_kills.match_id
        WHERE matches.tournament_id = :tournamentId
        ORDER BY matches.match_number, matches.id, match_kills.team_slot_number
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchKillEntity>>

    @Query("SELECT * FROM match_kills WHERE match_id = :matchId ORDER BY team_slot_number")
    fun observeByMatchId(matchId: String): Flow<List<MatchKillEntity>>

    @Upsert
    suspend fun upsertAll(kills: List<MatchKillEntity>)

    @Query("DELETE FROM match_kills WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface MatchParticipantResultDao {
    @Query(
        """
        SELECT match_participant_results.* FROM match_participant_results
        INNER JOIN matches ON matches.id = match_participant_results.match_id
        WHERE matches.tournament_id = :tournamentId
        ORDER BY matches.match_number, matches.id, match_participant_results.team_slot_number
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchParticipantResultEntity>>

    @Query(
        "SELECT * FROM match_participant_results " +
            "WHERE match_id = :matchId ORDER BY team_slot_number",
    )
    fun observeByMatchId(matchId: String): Flow<List<MatchParticipantResultEntity>>

    @Upsert
    suspend fun upsertAll(results: List<MatchParticipantResultEntity>)

    @Query("DELETE FROM match_participant_results WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface MatchDraftValueDao {
    @Query("SELECT * FROM match_draft_values WHERE match_id = :matchId ORDER BY team_slot_number")
    fun observeByMatchId(matchId: String): Flow<List<MatchDraftValueEntity>>

    @Upsert
    suspend fun upsert(value: MatchDraftValueEntity)

    @Upsert
    suspend fun upsertAll(values: List<MatchDraftValueEntity>)

    @Query("DELETE FROM match_draft_values WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface MatchCorrectionDao {
    @Query(
        """
        SELECT match_corrections.* FROM match_corrections
        INNER JOIN matches ON matches.id = match_corrections.match_id
        WHERE matches.tournament_id = :tournamentId
        ORDER BY matches.match_number, matches.id, match_corrections.correction_index
        """,
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchCorrectionEntity>>

    @Query("SELECT * FROM match_corrections WHERE match_id = :matchId ORDER BY correction_index")
    fun observeByMatchId(matchId: String): Flow<List<MatchCorrectionEntity>>

    @Upsert
    suspend fun upsertAll(corrections: List<MatchCorrectionEntity>)
}
