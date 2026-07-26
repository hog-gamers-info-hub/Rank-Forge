package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    entities = [
        RankForgeStateEntity::class,
        TournamentEntity::class,
        TeamSlotEntity::class,
        RosterPlayerEntity::class,
        MatchEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RankForgeDatabase : RoomDatabase() {
    abstract fun stateDao(): RankForgeStateDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun teamSlotDao(): TeamSlotDao
    abstract fun rosterPlayerDao(): RosterPlayerDao
    abstract fun matchDao(): MatchDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tournaments` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `organizer_name` TEXT NOT NULL,
                        `organizer_contact_number` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `team_slots` (
                        `tournament_id` TEXT NOT NULL,
                        `slot_number` INTEGER NOT NULL,
                        `team_name` TEXT NOT NULL,
                        PRIMARY KEY(`tournament_id`, `slot_number`),
                        FOREIGN KEY(`tournament_id`) REFERENCES `tournaments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `roster_players` (
                        `tournament_id` TEXT NOT NULL,
                        `slot_number` INTEGER NOT NULL,
                        `roster_position` INTEGER NOT NULL,
                        `display_name` TEXT NOT NULL,
                        PRIMARY KEY(`tournament_id`, `slot_number`, `roster_position`),
                        FOREIGN KEY(`tournament_id`, `slot_number`) REFERENCES `team_slots`(`tournament_id`, `slot_number`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `matches` (
                        `id` TEXT NOT NULL,
                        `tournament_id` TEXT NOT NULL,
                        `match_number` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `map_name` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`tournament_id`) REFERENCES `tournaments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_team_slots_tournament_id` ON `team_slots` (`tournament_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_roster_players_tournament_id_slot_number` ON `roster_players` (`tournament_id`, `slot_number`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_matches_tournament_id` ON `matches` (`tournament_id`)",
                )
            }
        }
    }
}
