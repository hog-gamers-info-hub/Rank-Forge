package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "account_deletion_markers")
data class AccountDeletionMarkerEntity(
    @PrimaryKey
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    val phase: String,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,
)

@Dao
interface AccountDeletionMarkerDao {
    @Query("SELECT * FROM account_deletion_markers ORDER BY updated_at DESC LIMIT 1")
    suspend fun readLatest(): AccountDeletionMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(marker: AccountDeletionMarkerEntity)

    @Query("DELETE FROM account_deletion_markers WHERE owner_user_id = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)
}
