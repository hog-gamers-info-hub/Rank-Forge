package com.hoggamers.rankforge.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue_entries")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val operationType: String,
    val tournamentId: String?,
    val createdAtEpochMillis: Long,
    val status: String,
    val failureCategory: String?,
    val attemptCount: Int,
)

@androidx.room.Dao
interface SyncQueueDao {
    @androidx.room.Query("SELECT * FROM sync_queue_entries ORDER BY createdAtEpochMillis")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>>
    @androidx.room.Insert
    suspend fun insert(entry: SyncQueueEntity)
    @androidx.room.Query("UPDATE sync_queue_entries SET status = :status, failureCategory = :failureCategory WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, failureCategory: String?)
    @androidx.room.Query("DELETE FROM sync_queue_entries WHERE id = :id")
    suspend fun delete(id: String)
}
