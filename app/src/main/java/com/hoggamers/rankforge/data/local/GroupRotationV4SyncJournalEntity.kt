package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "group_rotation_v4_sync_journal",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["request_id"], unique = true),
        Index(value = ["tournament_id"]),
        Index(value = ["owner_user_id", "tournament_id", "status", "updated_at_epoch_millis"]),
        Index(value = ["owner_user_id", "tournament_id", "created_at_epoch_millis"]),
        Index(value = ["owner_user_id", "match_id"]),
    ],
)
data class GroupRotationV4SyncJournalEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "journal_id")
    val journalId: String,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "match_id")
    val matchId: String?,
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    @ColumnInfo(name = "request_id")
    val requestId: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "payload_format_version")
    val payloadFormatVersion: Int,
    @ColumnInfo(name = "payload_sha256")
    val payloadSha256: String,
    @ColumnInfo(name = "expected_cloud_revision")
    val expectedCloudRevision: Int,
    val status: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "last_failure_category")
    val lastFailureCategory: String?,
    @ColumnInfo(name = "confirmed_cloud_revision")
    val confirmedCloudRevision: Int?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Dao
interface GroupRotationV4SyncJournalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: GroupRotationV4SyncJournalEntity): Long

    @Query("SELECT * FROM group_rotation_v4_sync_journal WHERE journal_id = :journalId")
    suspend fun readByJournalId(journalId: String): GroupRotationV4SyncJournalEntity?

    @Query("SELECT * FROM group_rotation_v4_sync_journal WHERE request_id = :requestId")
    suspend fun readByRequestId(requestId: String): GroupRotationV4SyncJournalEntity?

    @Query(
        "SELECT * FROM group_rotation_v4_sync_journal " +
            "WHERE owner_user_id = :ownerUserId AND tournament_id = :tournamentId " +
            "AND status IN ('PENDING', 'IN_PROGRESS', 'RETRYABLE_FAILURE') " +
            "ORDER BY created_at_epoch_millis, journal_id",
    )
    suspend fun readPendingOrUnresolvedByOwner(
        ownerUserId: String,
        tournamentId: String,
    ): List<GroupRotationV4SyncJournalEntity>

    @Query(
        "UPDATE group_rotation_v4_sync_journal SET " +
            "status = :newStatus, last_failure_category = :lastFailureCategory, " +
            "confirmed_cloud_revision = :confirmedCloudRevision, " +
            "attempt_count = attempt_count + :attemptIncrement, " +
            "updated_at_epoch_millis = :updatedAtEpochMillis " +
            "WHERE journal_id = :journalId AND owner_user_id = :ownerUserId " +
            "AND payload_sha256 = :payloadSha256 AND status IN (:expectedStatuses)",
    )
    suspend fun updateIfExpected(
        journalId: String,
        ownerUserId: String,
        payloadSha256: String,
        expectedStatuses: List<String>,
        newStatus: String,
        lastFailureCategory: String?,
        confirmedCloudRevision: Int?,
        attemptIncrement: Int,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE group_rotation_v4_sync_journal SET " +
            "status = 'RETRYABLE_FAILURE', last_failure_category = 'process_interrupted', " +
            "attempt_count = attempt_count + 1, updated_at_epoch_millis = :updatedAtEpochMillis " +
            "WHERE owner_user_id = :ownerUserId AND tournament_id = :tournamentId AND status = 'IN_PROGRESS'",
    )
    suspend fun recoverInProgressByOwner(
        ownerUserId: String,
        tournamentId: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE group_rotation_v4_sync_journal SET " +
            "status = 'INVALIDATED', last_failure_category = 'tournament_deleted', " +
            "updated_at_epoch_millis = :updatedAtEpochMillis " +
            "WHERE owner_user_id = :ownerUserId AND tournament_id = :tournamentId " +
            "AND status IN ('PENDING', 'IN_PROGRESS', 'RETRYABLE_FAILURE')",
    )
    suspend fun invalidateUnresolvedByTournamentAndOwner(
        tournamentId: String,
        ownerUserId: String,
        updatedAtEpochMillis: Long,
    ): Int
}
