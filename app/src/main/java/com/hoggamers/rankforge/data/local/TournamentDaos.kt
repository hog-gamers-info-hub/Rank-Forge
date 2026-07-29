package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRevisionDao {
    @Query("SELECT * FROM sync_revisions WHERE tournament_id = :tournamentId")
    suspend fun readByTournamentId(tournamentId: String): SyncRevisionEntity?

    @Upsert
    suspend fun upsert(revision: SyncRevisionEntity)

    @Query("UPDATE sync_revisions SET local_revision = local_revision + 1 WHERE tournament_id = :tournamentId")
    suspend fun incrementLocalRevision(tournamentId: String)
}

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY date, id")
    fun observeAll(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :tournamentId")
    fun observeById(tournamentId: String): Flow<TournamentEntity?>

    @Upsert
    suspend fun upsert(tournament: TournamentEntity)

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
