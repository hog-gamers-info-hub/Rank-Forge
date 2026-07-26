package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "rank_forge_state")
data class RankForgeStateEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val payload: String,
)

@Dao
interface RankForgeStateDao {
    @Query("SELECT payload FROM rank_forge_state WHERE id = 1")
    suspend fun readPayload(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: RankForgeStateEntity)
}

@Database(
    entities = [RankForgeStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RankForgeDatabase : RoomDatabase() {
    abstract fun stateDao(): RankForgeStateDao
}
