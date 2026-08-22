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
        MatchPlacementEntity::class,
        MatchKillEntity::class,
        MatchParticipantResultEntity::class,
        MatchDraftValueEntity::class,
        MatchCorrectionEntity::class,
        SyncQueueEntity::class,
        SyncRevisionEntity::class,
        DeletionIntentEntity::class,
        ScreenshotMetadataEntity::class,
        MatchResultScreenshotAssetEntity::class,
        MatchResultOcrCacheEntity::class,
        MatchLobbyOcrCacheEntity::class,
        MatchLobbyScreenshotAssetEntity::class,
        TournamentLobbyTemplateAssetEntity::class,
        RosterScreenshotMetadataEntity::class,
        MatchOcrEvidenceEntity::class,
        MatchOcrRowEvidenceEntity::class,
        MatchOcrCorrectionSnapshotEntity::class,
    ],
    version = 17,
    exportSchema = true,
)
abstract class RankForgeDatabase : RoomDatabase() {
    abstract fun stateDao(): RankForgeStateDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun teamSlotDao(): TeamSlotDao
    abstract fun rosterPlayerDao(): RosterPlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun matchPlacementDao(): MatchPlacementDao
    abstract fun matchKillDao(): MatchKillDao
    abstract fun matchParticipantResultDao(): MatchParticipantResultDao
    abstract fun matchDraftValueDao(): MatchDraftValueDao
    abstract fun matchCorrectionDao(): MatchCorrectionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncRevisionDao(): SyncRevisionDao
    abstract fun deletionIntentDao(): DeletionIntentDao
    abstract fun screenshotMetadataDao(): ScreenshotMetadataDao
    abstract fun matchResultScreenshotAssetDao(): MatchResultScreenshotAssetDao
    abstract fun matchResultOcrCacheDao(): MatchResultOcrCacheDao
    abstract fun matchLobbyOcrCacheDao(): MatchLobbyOcrCacheDao
    abstract fun matchLobbyScreenshotAssetDao(): MatchLobbyScreenshotAssetDao
    abstract fun tournamentLobbyTemplateAssetDao(): TournamentLobbyTemplateAssetDao
    abstract fun rosterScreenshotMetadataDao(): RosterScreenshotMetadataDao
    abstract fun matchOcrEvidenceDao(): MatchOcrEvidenceDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_placements` (
                        `match_id` TEXT NOT NULL,
                        `team_slot_number` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `team_slot_number`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_kills` (
                        `match_id` TEXT NOT NULL,
                        `team_slot_number` INTEGER NOT NULL,
                        `kills` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `team_slot_number`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_draft_values` (
                        `match_id` TEXT NOT NULL,
                        `team_slot_number` INTEGER NOT NULL,
                        `placement_input` TEXT NOT NULL,
                        `kills_input` TEXT NOT NULL,
                        PRIMARY KEY(`match_id`, `team_slot_number`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_corrections` (
                        `match_id` TEXT NOT NULL,
                        `correction_index` INTEGER NOT NULL,
                        `previous_placements` TEXT NOT NULL,
                        `previous_kills` TEXT NOT NULL,
                        `corrected_placements` TEXT NOT NULL,
                        `corrected_kills` TEXT NOT NULL,
                        PRIMARY KEY(`match_id`, `correction_index`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_placements_match_id` ON `match_placements` (`match_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_kills_match_id` ON `match_kills` (`match_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_draft_values_match_id` ON `match_draft_values` (`match_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_corrections_match_id` ON `match_corrections` (`match_id`)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue_entries` (`id` TEXT NOT NULL, `operationType` TEXT NOT NULL, `tournamentId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `failureCategory` TEXT, `attemptCount` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_revisions` (`tournament_id` TEXT NOT NULL, `local_revision` INTEGER NOT NULL, `base_cloud_revision` INTEGER, PRIMARY KEY(`tournament_id`))",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `screenshot_metadata` (
                        `match_id` TEXT NOT NULL,
                        `tournament_id` TEXT NOT NULL,
                        `owner_user_id` TEXT NOT NULL,
                        `local_relative_path` TEXT NOT NULL,
                        `file_extension` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `byte_size` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `storage_bucket` TEXT,
                        `storage_object_path` TEXT,
                        `local_status` TEXT NOT NULL,
                        `upload_status` TEXT NOT NULL,
                        `upload_failure_code` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `preserved_at` INTEGER NOT NULL,
                        `uploaded_at` INTEGER,
                        `revision` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_metadata_tournament_id` ON `screenshot_metadata` (`tournament_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_metadata_owner_user_id` ON `screenshot_metadata` (`owner_user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_metadata_sha256` ON `screenshot_metadata` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_metadata_upload_status` ON `screenshot_metadata` (`upload_status`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `roster_screenshot_metadata` (
                        `tournament_id` TEXT NOT NULL,
                        `roster_screenshot_index` INTEGER NOT NULL,
                        `local_relative_path` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `validation_status` TEXT NOT NULL,
                        `crop_left` REAL,
                        `crop_top` REAL,
                        `crop_right` REAL,
                        `crop_bottom` REAL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`tournament_id`, `roster_screenshot_index`),
                        FOREIGN KEY(`tournament_id`) REFERENCES `tournaments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_roster_screenshot_metadata_tournament_id` ON `roster_screenshot_metadata` (`tournament_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_roster_screenshot_metadata_sha256` ON `roster_screenshot_metadata` (`sha256`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_ocr_evidence` (
                        `match_id` TEXT NOT NULL,
                        `tournament_id` TEXT NOT NULL,
                        `source_screenshot_id` TEXT,
                        `preserved_at` INTEGER NOT NULL,
                        `provenance` TEXT NOT NULL,
                        PRIMARY KEY(`match_id`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_ocr_row_evidence` (
                        `match_id` TEXT NOT NULL,
                        `tournament_id` TEXT NOT NULL,
                        `row_index` INTEGER NOT NULL,
                        `original_ocr_text` TEXT,
                        `original_placement` INTEGER,
                        `original_kills` INTEGER,
                        `original_suggested_team_slot` INTEGER,
                        `confidence_summary` TEXT,
                        `safety_summary` TEXT,
                        `manual_review_required` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `row_index`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_ocr_correction_snapshots` (
                        `match_id` TEXT NOT NULL,
                        `tournament_id` TEXT NOT NULL,
                        `row_index` INTEGER NOT NULL,
                        `corrected_placement` INTEGER NOT NULL,
                        `corrected_kills` INTEGER NOT NULL,
                        `corrected_team_slot` INTEGER NOT NULL,
                        `placement_changed` INTEGER NOT NULL,
                        `kills_changed` INTEGER NOT NULL,
                        `team_slot_changed` INTEGER NOT NULL,
                        `preserved_at` INTEGER NOT NULL,
                        `provenance` TEXT NOT NULL,
                        PRIMARY KEY(`match_id`, `row_index`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_ocr_evidence_tournament_id` ON `match_ocr_evidence` (`tournament_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_ocr_row_evidence_match_id` ON `match_ocr_row_evidence` (`match_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_ocr_row_evidence_tournament_id` ON `match_ocr_row_evidence` (`tournament_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_ocr_correction_snapshots_match_id` ON `match_ocr_correction_snapshots` (`match_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_ocr_correction_snapshots_tournament_id` ON `match_ocr_correction_snapshots` (`tournament_id`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_result_screenshot_assets` (
                        `tournament_id` TEXT NOT NULL,
                        `match_id` TEXT NOT NULL,
                        `screenshot_kind` TEXT NOT NULL,
                        `screenshot_role` TEXT NOT NULL,
                        `owner_user_id` TEXT NOT NULL,
                        `local_relative_path` TEXT NOT NULL,
                        `file_extension` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `original_width` INTEGER NOT NULL,
                        `original_height` INTEGER NOT NULL,
                        `byte_size` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `local_status` TEXT NOT NULL,
                        `upload_status` TEXT NOT NULL,
                        `upload_failure_code` TEXT,
                        `storage_bucket` TEXT,
                        `storage_object_path` TEXT,
                        `crop_profile_id` TEXT,
                        `crop_left` REAL,
                        `crop_top` REAL,
                        `crop_right` REAL,
                        `crop_bottom` REAL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `preserved_at` INTEGER NOT NULL,
                        `uploaded_at` INTEGER,
                        `revision` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `screenshot_role`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_result_screenshot_assets_tournament_id` ON `match_result_screenshot_assets` (`tournament_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_result_screenshot_assets_sha256` ON `match_result_screenshot_assets` (`sha256`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_result_screenshot_assets_upload_status` ON `match_result_screenshot_assets` (`upload_status`)",
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `tournaments` ADD COLUMN `creation_order` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "UPDATE `tournaments` SET `creation_order` = rowid",
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_lobby_screenshot_assets` (
                        `tournament_id` TEXT NOT NULL,
                        `match_id` TEXT NOT NULL,
                        `lobby_screenshot_index` INTEGER NOT NULL,
                        `owner_user_id` TEXT NOT NULL,
                        `local_relative_path` TEXT NOT NULL,
                        `file_extension` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `original_width` INTEGER NOT NULL,
                        `original_height` INTEGER NOT NULL,
                        `byte_size` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `local_status` TEXT NOT NULL,
                        `upload_status` TEXT NOT NULL,
                        `upload_failure_code` TEXT,
                        `storage_bucket` TEXT,
                        `storage_object_path` TEXT,
                        `crop_profile_id` TEXT,
                        `crop_left` REAL,
                        `crop_top` REAL,
                        `crop_right` REAL,
                        `crop_bottom` REAL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `preserved_at` INTEGER NOT NULL,
                        `uploaded_at` INTEGER,
                        `revision` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `lobby_screenshot_index`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_lobby_screenshot_assets_tournament_id` ON `match_lobby_screenshot_assets` (`tournament_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_lobby_screenshot_assets_sha256` ON `match_lobby_screenshot_assets` (`sha256`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_lobby_screenshot_assets_upload_status` ON `match_lobby_screenshot_assets` (`upload_status`)",
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tournament_lobby_template_assets` (
                        `tournament_id` TEXT NOT NULL,
                        `lobby_screenshot_index` INTEGER NOT NULL,
                        `owner_user_id` TEXT NOT NULL,
                        `local_relative_path` TEXT NOT NULL,
                        `file_extension` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `original_width` INTEGER NOT NULL,
                        `original_height` INTEGER NOT NULL,
                        `byte_size` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `crop_profile_id` TEXT NOT NULL,
                        `crop_left` REAL NOT NULL,
                        `crop_top` REAL NOT NULL,
                        `crop_right` REAL NOT NULL,
                        `crop_bottom` REAL NOT NULL,
                        `source_match_id` TEXT NOT NULL,
                        `saved_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        PRIMARY KEY(`tournament_id`, `lobby_screenshot_index`),
                        FOREIGN KEY(`tournament_id`) REFERENCES `tournaments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tournament_lobby_template_assets_tournament_id` " +
                        "ON `tournament_lobby_template_assets` (`tournament_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tournament_lobby_template_assets_sha256` " +
                        "ON `tournament_lobby_template_assets` (`sha256`)",
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_result_ocr_cache` (
                        `tournament_id` TEXT NOT NULL,
                        `match_id` TEXT NOT NULL,
                        `screenshot_role` TEXT NOT NULL,
                        `screenshot_sha256` TEXT NOT NULL,
                        `original_width` INTEGER NOT NULL,
                        `original_height` INTEGER NOT NULL,
                        `crop_profile_id` TEXT NOT NULL,
                        `crop_left` REAL NOT NULL,
                        `crop_top` REAL NOT NULL,
                        `crop_right` REAL NOT NULL,
                        `crop_bottom` REAL NOT NULL,
                        `ocr_pipeline_version` INTEGER NOT NULL,
                        `processed_payload_json` TEXT NOT NULL,
                        `cached_at` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `screenshot_role`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_result_ocr_cache_tournament_id` " +
                        "ON `match_result_ocr_cache` (`tournament_id`)",
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_lobby_ocr_cache` (
                        `tournament_id` TEXT NOT NULL,
                        `match_id` TEXT NOT NULL,
                        `lobby_screenshot_index` INTEGER NOT NULL,
                        `screenshot_sha256` TEXT NOT NULL,
                        `original_width` INTEGER NOT NULL,
                        `original_height` INTEGER NOT NULL,
                        `crop_profile_id` TEXT NOT NULL,
                        `crop_left` REAL NOT NULL,
                        `crop_top` REAL NOT NULL,
                        `crop_right` REAL NOT NULL,
                        `crop_bottom` REAL NOT NULL,
                        `ocr_pipeline_version` INTEGER NOT NULL,
                        `processed_payload_json` TEXT NOT NULL,
                        `cached_at` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `lobby_screenshot_index`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_lobby_ocr_cache_tournament_id` " +
                        "ON `match_lobby_ocr_cache` (`tournament_id`)",
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_participant_results` (
                        `match_id` TEXT NOT NULL,
                        `team_slot_number` INTEGER NOT NULL,
                        `participation_status` TEXT NOT NULL,
                        `placement` INTEGER,
                        `kills` INTEGER NOT NULL,
                        PRIMARY KEY(`match_id`, `team_slot_number`),
                        FOREIGN KEY(`match_id`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        CHECK (`participation_status` IN ('PARTICIPATED', 'NO_SHOW')),
                        CHECK (`kills` >= 0),
                        CHECK (
                            (`participation_status` = 'PARTICIPATED' AND `placement` IS NOT NULL) OR
                            (`participation_status` = 'NO_SHOW' AND `placement` IS NULL AND `kills` = 0)
                        )
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_match_participant_results_match_id` " +
                        "ON `match_participant_results` (`match_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_match_participant_results_match_id_placement` " +
                        "ON `match_participant_results` (`match_id`, `placement`)",
                )
                db.execSQL(
                    """
                    INSERT INTO `match_participant_results` (
                        `match_id`, `team_slot_number`, `participation_status`, `placement`, `kills`
                    )
                    SELECT placements.`match_id`, placements.`team_slot_number`,
                        'PARTICIPATED', placements.`position`, kills.`kills`
                    FROM `match_placements` AS placements
                    INNER JOIN `match_kills` AS kills
                        ON kills.`match_id` = placements.`match_id`
                        AND kills.`team_slot_number` = placements.`team_slot_number`
                    INNER JOIN `matches` AS matches
                        ON matches.`id` = placements.`match_id`
                    WHERE matches.`status` = 'FINALIZED'
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `deletion_intents` (
                        `target_type` TEXT NOT NULL,
                        `target_id` TEXT NOT NULL,
                        `tournament_id` TEXT NOT NULL,
                        `owner_user_id` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `updated_at_epoch_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`target_type`, `target_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_deletion_intents_tournament_id` " +
                        "ON `deletion_intents` (`tournament_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_deletion_intents_phase` " +
                        "ON `deletion_intents` (`phase`)",
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `tournaments` ADD COLUMN `last_updated_epoch_millis` INTEGER",
                )
            }
        }
    }
}
