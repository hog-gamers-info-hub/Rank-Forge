package com.hoggamers.rankforge.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue_entries",
    indices = [
        Index(value = ["owner_user_id", "createdAtEpochMillis", "id"]),
        Index(value = ["owner_user_id", "operationType", "tournamentId", "status", "createdAtEpochMillis", "id"]),
        Index(value = ["owner_user_id", "tournamentId"]),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val operationType: String,
    val tournamentId: String?,
    val createdAtEpochMillis: Long,
    val status: String,
    val failureCategory: String?,
    val attemptCount: Int,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String? = null,
)

@androidx.room.Dao
interface SyncQueueDao {
    @androidx.room.Query("SELECT * FROM sync_queue_entries ORDER BY createdAtEpochMillis")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>>
    @androidx.room.Query("SELECT * FROM sync_queue_entries WHERE owner_user_id = :ownerUserId ORDER BY createdAtEpochMillis, id")
    fun observeByOwner(ownerUserId: String): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>>
    @androidx.room.Query("SELECT * FROM sync_queue_entries WHERE owner_user_id = :ownerUserId AND operationType = :operationType AND ((:tournamentId IS NULL AND tournamentId IS NULL) OR tournamentId = :tournamentId) AND status != 'COMPLETED' ORDER BY createdAtEpochMillis, id LIMIT 1")
    suspend fun findOldestUnresolvedByOwner(ownerUserId: String, operationType: String, tournamentId: String?): SyncQueueEntity?
    @androidx.room.Insert
    suspend fun insert(entry: SyncQueueEntity)
    @androidx.room.Query("UPDATE sync_queue_entries SET status = :status, failureCategory = :failureCategory WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun updateStatusByIdAndOwner(id: String, ownerUserId: String, status: String, failureCategory: String?)
    @androidx.room.Query("UPDATE sync_queue_entries SET attemptCount = attemptCount + 1 WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun incrementAttemptCountByIdAndOwner(id: String, ownerUserId: String)
    @androidx.room.Query("DELETE FROM sync_queue_entries WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun deleteByIdAndOwner(id: String, ownerUserId: String)

    /** Trusted Phase 4B compatibility path; foreground recovery must not use it. */
    @androidx.room.Query("DELETE FROM sync_queue_entries WHERE tournamentId = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
    @androidx.room.Query("DELETE FROM sync_queue_entries WHERE tournamentId = :tournamentId AND owner_user_id = :ownerUserId")
    suspend fun deleteByTournamentIdAndOwner(tournamentId: String, ownerUserId: String)

    @androidx.room.Query("DELETE FROM sync_queue_entries WHERE owner_user_id = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)
}
