package com.hoggamers.rankforge.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.di.TournamentDataProvidersModule
import com.hoggamers.rankforge.data.tournament.RoomTournamentRepository
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrCorrectionSnapshot
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrRowEvidence
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.Match as DomainMatch
import com.hoggamers.rankforge.domain.tournament.Tournament as DomainTournament
import java.time.LocalDate
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RankForgeDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(MIGRATION_DATABASE_NAME)
    }

    @Test
    fun migrationFromVersion1PreservesLegacyStatePayloadAndValidatesVersion2Schema() {
        val payload = """{"tournaments":[{"id":"tournament-1","name":"Summer Cup"}]}"""
        createVersion1Database().use { database ->
            database.execSQL(
                "INSERT INTO rank_forge_state (id, payload) VALUES (?, ?)",
                arrayOf<Any>(1, payload),
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            2,
            true,
            RankForgeDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT payload FROM rank_forge_state WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(payload, cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun productionDatabaseProviderRegistersCompleteMigrationChainFromVersion1() = runBlocking {
        val payload = "{\"legacy\":true}"
        createVersion1Database().use { database ->
            database.execSQL(
                "INSERT INTO rank_forge_state (id, payload) VALUES (?, ?)",
                arrayOf<Any>(1, payload),
            )
        }

        val redirectedContext = ProductionMigrationDatabaseContext(
            baseContext = context,
            redirectedDatabaseFile = context.getDatabasePath(MIGRATION_DATABASE_NAME),
        )
        val database = TournamentDataProvidersModule.provideRankForgeDatabase(redirectedContext)
        try {
            val openedDatabase = database.openHelper.writableDatabase

            openedDatabase.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(17, cursor.getInt(0))
            }
            openedDatabase.query(
                "SELECT payload FROM rank_forge_state WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(payload, cursor.getString(0))
            }
            openedDatabase.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'match_result_screenshot_assets'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            openedDatabase.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'tournament_lobby_template_assets'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            openedDatabase.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'match_result_ocr_cache'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromVersion15AddsDeletionIntentsWithoutDroppingExistingData() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 15).use { database ->
            database.execSQL(
                "INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) " +
                    "VALUES ('tournament-intent', 'Intent Cup', '2026-08-21', 'Organizer', '123', 'DRAFT')",
            )
            database.execSQL(
                "INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) " +
                    "VALUES ('match-intent', 'tournament-intent', 4, '2026-08-21', 'Alpine', 'DRAFT')",
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            16,
            true,
            RankForgeDatabase.MIGRATION_15_16,
        )

        migrated.query("SELECT id, match_number FROM matches WHERE id = 'match-intent'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("match-intent", cursor.getString(0))
            assertEquals(4, cursor.getInt(1))
        }
        assertTrue(migrated.hasTable("deletion_intents"))
        migrated.query("PRAGMA table_info(deletion_intents)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(
                setOf(
                    "target_type",
                    "target_id",
                    "tournament_id",
                    "owner_user_id",
                    "phase",
                    "updated_at_epoch_millis",
                ),
                columns,
            )
        }
        assertTrue(migrated.hasIndex("index_deletion_intents_tournament_id"))
        assertTrue(migrated.hasIndex("index_deletion_intents_phase"))
        migrated.close()
    }

    @Test
    fun migrationFromVersion16AddsUnknownLastUpdatedWithoutFabricatingHistory() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 16).use { database ->
            database.execSQL(
                "INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status, creation_order) " +
                    "VALUES ('legacy-summary', 'Legacy Cup', '2026-08-22', 'Organizer', '123', 'DRAFT', 1)",
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            17,
            true,
            RankForgeDatabase.MIGRATION_16_17,
        )

        migrated.query(
            "SELECT name, last_updated_epoch_millis FROM tournaments WHERE id = 'legacy-summary'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Cup", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion2AddsMatchResultTablesWithoutDroppingExistingMatch() {
        createVersion2Database().use { database ->
            database.execSQL(
                """
                INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status)
                VALUES ('tournament-1', 'Summer Cup', '2026-07-26', 'Organizer', '1234567890', 'CONFIRMED')
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO matches (id, tournament_id, match_number, date, map_name, status)
                VALUES ('match-1', 'tournament-1', 1, '2026-07-26', 'Bermuda', 'DRAFT')
                """.trimIndent(),
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            3,
            true,
            RankForgeDatabase.MIGRATION_1_2,
            RankForgeDatabase.MIGRATION_2_3,
        )

        migrated.query("SELECT id, status FROM matches WHERE id = 'match-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("match-1", cursor.getString(0))
            assertEquals("DRAFT", cursor.getString(1))
        }
        listOf(
            "match_placements",
            "match_kills",
            "match_draft_values",
            "match_corrections",
        ).forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
            }
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion3AddsQueueTableWithoutDroppingTournamentOrMatchData() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 3).use { database ->
            database.execSQL("INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) VALUES ('tournament-queue', 'Queue Cup', '2026-07-26', 'Organizer', '123', 'CONFIRMED')")
            database.execSQL("INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) VALUES ('match-queue', 'tournament-queue', 1, '2026-07-26', 'Bermuda', 'DRAFT')")
        }
        val migrated = migrationTestHelper().runMigrationsAndValidate(MIGRATION_DATABASE_NAME, 4, true, RankForgeDatabase.MIGRATION_3_4)
        migrated.query("SELECT id FROM matches WHERE id = 'match-queue'").use { assertTrue(it.moveToFirst()) }
        migrated.query("PRAGMA table_info(sync_queue_entries)").use { cursor ->
            val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            assertTrue(setOf("id", "operationType", "tournamentId", "createdAtEpochMillis", "status", "failureCategory", "attemptCount").all(columns::contains))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion4PreservesDataAndAddsSafeRevisionStorage() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 4).use { database ->
            database.execSQL("INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) VALUES ('tournament-revision', 'Revision Cup', '2026-07-26', 'Organizer', '123', 'CONFIRMED')")
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            5,
            true,
            RankForgeDatabase.MIGRATION_4_5,
        )
        migrated.query("SELECT id FROM tournaments WHERE id = 'tournament-revision'").use {
            assertTrue(it.moveToFirst())
        }
        migrated.query("PRAGMA table_info(sync_revisions)").use { cursor ->
            val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            assertTrue(setOf("tournament_id", "local_revision", "base_cloud_revision").all(columns::contains))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion5AddsScreenshotMetadataWithoutDroppingExistingMatchData() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 5).use { database ->
            database.execSQL("INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) VALUES ('tournament-metadata', 'Metadata Cup', '2026-07-29', 'Organizer', '123', 'CONFIRMED')")
            database.execSQL("INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) VALUES ('match-metadata', 'tournament-metadata', 1, '2026-07-29', 'Bermuda', 'DRAFT')")
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            6,
            true,
            RankForgeDatabase.MIGRATION_5_6,
        )

        migrated.query("SELECT id FROM matches WHERE id = 'match-metadata'").use {
            assertTrue(it.moveToFirst())
        }
        migrated.query("PRAGMA table_info(screenshot_metadata)").use { cursor ->
            val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            assertTrue(
                setOf(
                    "match_id",
                    "tournament_id",
                    "owner_user_id",
                    "local_relative_path",
                    "file_extension",
                    "mime_type",
                    "width",
                    "height",
                    "byte_size",
                    "sha256",
                    "storage_bucket",
                    "storage_object_path",
                    "local_status",
                    "upload_status",
                    "upload_failure_code",
                    "created_at",
                    "updated_at",
                    "preserved_at",
                    "uploaded_at",
                    "revision",
                ).all(columns::contains),
            )
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion6AddsRosterScreenshotMetadataWithoutDroppingExistingData() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 6).use { database ->
            database.execSQL("INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) VALUES ('tournament-roster-screenshots', 'Roster Cup', '2026-07-30', 'Organizer', '123', 'CONFIRMED')")
            database.execSQL("INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) VALUES ('match-roster-screenshots', 'tournament-roster-screenshots', 1, '2026-07-30', 'Bermuda', 'DRAFT')")
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            7,
            true,
            RankForgeDatabase.MIGRATION_6_7,
        )

        migrated.query("SELECT id FROM matches WHERE id = 'match-roster-screenshots'").use {
            assertTrue(it.moveToFirst())
        }
        migrated.query("PRAGMA table_info(roster_screenshot_metadata)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(
                setOf(
                    "tournament_id",
                    "roster_screenshot_index",
                    "local_relative_path",
                    "mime_type",
                    "width",
                    "height",
                    "sha256",
                    "validation_status",
                    "crop_left",
                    "crop_top",
                    "crop_right",
                    "crop_bottom",
                    "created_at",
                    "updated_at",
                ).all(columns::contains),
            )
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion7AddsOcrPreservationTablesWithoutDroppingExistingMatchData() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 7).use { database ->
            database.execSQL("INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) VALUES ('tournament-ocr-preservation', 'OCR Cup', '2026-07-31', 'Organizer', '123', 'CONFIRMED')")
            database.execSQL("INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) VALUES ('match-ocr-preservation', 'tournament-ocr-preservation', 1, '2026-07-31', 'Bermuda', 'DRAFT')")
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            8,
            true,
            RankForgeDatabase.MIGRATION_7_8,
        )

        migrated.query("SELECT id FROM matches WHERE id = 'match-ocr-preservation'").use {
            assertTrue(it.moveToFirst())
        }
        listOf(
            "match_ocr_evidence",
            "match_ocr_row_evidence",
            "match_ocr_correction_snapshots",
        ).forEach { table ->
            assertTrue(migrated.hasTable(table))
        }
        listOf(
            "index_match_ocr_evidence_tournament_id",
            "index_match_ocr_row_evidence_match_id",
            "index_match_ocr_row_evidence_tournament_id",
            "index_match_ocr_correction_snapshots_match_id",
            "index_match_ocr_correction_snapshots_tournament_id",
        ).forEach { index ->
            assertTrue(migrated.hasIndex(index))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion8AddsMatchResultScreenshotAssetsWithoutMigratingLegacyScreenshotMetadata() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 8).use { database ->
            database.execSQL("INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status) VALUES ('tournament-screenshot-identity', 'Identity Cup', '2026-08-07', 'Organizer', '123', 'CONFIRMED')")
            database.execSQL("INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) VALUES ('match-screenshot-identity', 'tournament-screenshot-identity', 1, '2026-08-07', 'Bermuda', 'DRAFT')")
            database.execSQL(
                """
                INSERT INTO screenshot_metadata (
                    match_id, tournament_id, owner_user_id, local_relative_path, file_extension,
                    mime_type, width, height, byte_size, sha256, storage_bucket, storage_object_path,
                    local_status, upload_status, upload_failure_code, created_at, updated_at,
                    preserved_at, uploaded_at, revision
                ) VALUES (
                    'match-screenshot-identity', 'tournament-screenshot-identity', 'owner-1',
                    'screenshots/tournament/match/original.png', 'png', 'image/png', 1600, 720,
                    4, '${"a".repeat(64)}', NULL, NULL, 'PRESERVED', 'PENDING', NULL, 1, 1, 1, NULL, 1
                )
                """.trimIndent(),
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            9,
            true,
            RankForgeDatabase.MIGRATION_8_9,
        )

        migrated.query("SELECT id FROM matches WHERE id = 'match-screenshot-identity'").use {
            assertTrue(it.moveToFirst())
        }
        migrated.query("SELECT match_id FROM screenshot_metadata WHERE match_id = 'match-screenshot-identity'").use {
            assertTrue(it.moveToFirst())
        }
        assertTrue(migrated.hasTable("match_result_screenshot_assets"))
        listOf(
            "index_match_result_screenshot_assets_tournament_id",
            "index_match_result_screenshot_assets_sha256",
        ).forEach { index ->
            assertTrue(migrated.hasIndex(index))
        }
        migrated.query("SELECT COUNT(*) FROM match_result_screenshot_assets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA foreign_key_list('match_result_screenshot_assets')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("matches", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion9AddsDurableTournamentCreationOrder() {
        val expectedIds = listOf("test1-id", "test2-id", "test3-id", "test4-id")
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 9).use { database ->
            expectedIds.forEachIndexed { index, id ->
                database.execSQL(
                    """
                    INSERT INTO tournaments (
                        id, name, date, organizer_name, organizer_contact_number, status
                    ) VALUES (?, ?, '2026-08-11', 'Organizer', '123', 'DRAFT')
                    """.trimIndent(),
                    arrayOf<Any>(id, "test ${index + 1}"),
                )
            }
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            10,
            true,
            RankForgeDatabase.MIGRATION_9_10,
        )

        migrated.query("PRAGMA table_info(tournaments)").use { cursor ->
            var foundCreationOrder = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "creation_order") {
                    foundCreationOrder = true
                    assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                }
            }
            assertTrue(foundCreationOrder)
        }

        val migratedIds = mutableListOf<String>()
        val creationOrders = mutableListOf<Long>()
        migrated.query(
            "SELECT id, creation_order FROM tournaments ORDER BY creation_order, id",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                assertTrue(!cursor.isNull(1))
                migratedIds += cursor.getString(0)
                creationOrders += cursor.getLong(1)
            }
        }
        assertEquals(expectedIds, migratedIds)
        assertEquals(creationOrders.size, creationOrders.distinct().size)
        assertTrue(creationOrders.all { it > 0L })
        migrated.close()
    }

    @Test
    fun migrationFromVersion10AddsLobbyScreenshotAssetsWithoutDroppingExistingData() {
        migrationTestHelper().createDatabase(MIGRATION_DATABASE_NAME, 10).use { database ->
            database.execSQL(
                "INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status, creation_order) VALUES ('tournament-lobby', 'Lobby Cup', '2026-08-12', 'Organizer', '123', 'CONFIRMED', 1)",
            )
            database.execSQL(
                "INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) VALUES ('match-lobby', 'tournament-lobby', 1, '2026-08-12', 'Bermuda', 'DRAFT')",
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            11,
            true,
            RankForgeDatabase.MIGRATION_10_11,
        )

        migrated.query("SELECT id FROM tournaments WHERE id = 'tournament-lobby'").use {
            assertTrue(it.moveToFirst())
        }
        migrated.query("SELECT id FROM matches WHERE id = 'match-lobby'").use {
            assertTrue(it.moveToFirst())
        }
        assertTrue(migrated.hasTable("match_lobby_screenshot_assets"))
        migrated.query("PRAGMA table_info('match_lobby_screenshot_assets')").use { cursor ->
            val columns = buildMap<String, Int> {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("pk")),
                    )
                }
            }
            assertTrue(
                setOf(
                    "tournament_id",
                    "match_id",
                    "lobby_screenshot_index",
                    "owner_user_id",
                    "local_relative_path",
                    "file_extension",
                    "mime_type",
                    "original_width",
                    "original_height",
                    "byte_size",
                    "sha256",
                    "local_status",
                    "upload_status",
                    "upload_failure_code",
                    "storage_bucket",
                    "storage_object_path",
                    "crop_profile_id",
                    "crop_left",
                    "crop_top",
                    "crop_right",
                    "crop_bottom",
                    "created_at",
                    "updated_at",
                    "preserved_at",
                    "uploaded_at",
                    "revision",
                ).all(columns::containsKey),
            )
            assertEquals(1, columns["match_id"])
            assertEquals(2, columns["lobby_screenshot_index"])
        }
        migrated.query("PRAGMA foreign_key_list('match_lobby_screenshot_assets')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("matches", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("id", cursor.getString(cursor.getColumnIndexOrThrow("to")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
        listOf(
            "index_match_lobby_screenshot_assets_tournament_id",
            "index_match_lobby_screenshot_assets_sha256",
            "index_match_lobby_screenshot_assets_upload_status",
        ).forEach { index -> assertTrue(migrated.hasIndex(index)) }
        migrated.close()
    }

    @Test
    fun migrationFromVersion1ToVersion12PreservesLegacyStateAndFinalSchema() {
        val payload = """{"tournaments":[{"id":"legacy-tournament","name":"Legacy Cup"}]}"""
        createVersion1Database().use { database ->
            database.execSQL(
                "INSERT INTO rank_forge_state (id, payload) VALUES (?, ?)",
                arrayOf<Any>(1, payload),
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            12,
            true,
            RankForgeDatabase.MIGRATION_1_2,
            RankForgeDatabase.MIGRATION_2_3,
            RankForgeDatabase.MIGRATION_3_4,
            RankForgeDatabase.MIGRATION_4_5,
            RankForgeDatabase.MIGRATION_5_6,
            RankForgeDatabase.MIGRATION_6_7,
            RankForgeDatabase.MIGRATION_7_8,
            RankForgeDatabase.MIGRATION_8_9,
            RankForgeDatabase.MIGRATION_9_10,
            RankForgeDatabase.MIGRATION_10_11,
            RankForgeDatabase.MIGRATION_11_12,
        )

        migrated.query("SELECT payload FROM rank_forge_state WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(payload, cursor.getString(0))
        }
        listOf(
            "sync_queue_entries",
            "sync_revisions",
            "roster_screenshot_metadata",
            "match_ocr_evidence",
            "match_ocr_row_evidence",
            "match_ocr_correction_snapshots",
            "match_result_screenshot_assets",
            "match_lobby_screenshot_assets",
            "tournament_lobby_template_assets",
        ).forEach { table ->
            assertTrue(migrated.hasTable(table))
        }
        listOf(
            "index_roster_screenshot_metadata_tournament_id",
            "index_roster_screenshot_metadata_sha256",
            "index_match_ocr_evidence_tournament_id",
            "index_match_ocr_row_evidence_match_id",
            "index_match_ocr_correction_snapshots_match_id",
            "index_match_result_screenshot_assets_tournament_id",
            "index_match_result_screenshot_assets_sha256",
            "index_match_lobby_screenshot_assets_tournament_id",
            "index_match_lobby_screenshot_assets_sha256",
            "index_match_lobby_screenshot_assets_upload_status",
        ).forEach { index ->
            assertTrue(migrated.hasIndex(index))
        }
        migrated.query("PRAGMA foreign_key_list('roster_screenshot_metadata')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("tournaments", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion12AddsMatchResultOcrCacheWithoutDroppingExistingData() {
        createVersion12Database().use { database ->
            database.execSQL(
                "INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status, creation_order) " +
                    "VALUES ('tournament-cache', 'Cache Cup', '2026-08-14', 'Organizer', '123', 'DRAFT', 1)",
            )
            database.execSQL(
                "INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) " +
                    "VALUES ('match-cache', 'tournament-cache', 1, '2026-08-14', 'Bermuda', 'DRAFT')",
            )
            database.execSQL(
                """
                INSERT INTO match_result_screenshot_assets (
                    tournament_id, match_id, screenshot_kind, screenshot_role, owner_user_id,
                    local_relative_path, file_extension, mime_type, original_width, original_height,
                    byte_size, sha256, local_status, upload_status, upload_failure_code,
                    storage_bucket, storage_object_path, crop_profile_id, crop_left, crop_top,
                    crop_right, crop_bottom, created_at, updated_at, preserved_at, uploaded_at, revision
                ) VALUES (
                    'tournament-cache', 'match-cache', 'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'owner',
                    'result.png', 'png', 'image/png', 1000, 800, 100, '${"a".repeat(64)}',
                    'PRESERVED', 'PENDING', NULL, NULL, NULL, 'match-result', 0.0, 0.0,
                    1.0, 1.0, 1, 1, 1, NULL, 1
                )
                """.trimIndent(),
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            13,
            true,
            RankForgeDatabase.MIGRATION_12_13,
        )

        migrated.query("SELECT id FROM tournaments WHERE id = 'tournament-cache'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.query("SELECT id FROM matches WHERE id = 'match-cache'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.query(
            "SELECT match_id FROM match_result_screenshot_assets WHERE match_id = 'match-cache'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        assertTrue(migrated.hasTable("match_result_ocr_cache"))
        assertTrue(migrated.hasIndex("index_match_result_ocr_cache_tournament_id"))
        migrated.query("PRAGMA table_info('match_result_ocr_cache')").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(
                listOf(
                    "tournament_id",
                    "match_id",
                    "screenshot_role",
                    "screenshot_sha256",
                    "original_width",
                    "original_height",
                    "crop_profile_id",
                    "crop_left",
                    "crop_top",
                    "crop_right",
                    "crop_bottom",
                    "ocr_pipeline_version",
                    "processed_payload_json",
                    "cached_at",
                ).all(columns::contains),
            )
        }
        migrated.query("SELECT COUNT(*) FROM match_result_ocr_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA foreign_key_list('match_result_ocr_cache')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("matches", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion13AddsLobbyOcrCacheWithoutDroppingExistingData() {
        createVersion13Database().use { database ->
            database.execSQL(
                "INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status, creation_order) " +
                    "VALUES ('tournament-lobby-cache', 'Lobby Cache Cup', '2026-08-16', 'Organizer', '123', 'DRAFT', 1)",
            )
            database.execSQL(
                "INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) " +
                    "VALUES ('match-lobby-cache', 'tournament-lobby-cache', 1, '2026-08-16', 'Bermuda', 'DRAFT')",
            )
            database.execSQL(
                """
                INSERT INTO match_result_ocr_cache (
                    tournament_id, match_id, screenshot_role, screenshot_sha256,
                    original_width, original_height, crop_profile_id, crop_left,
                    crop_top, crop_right, crop_bottom, ocr_pipeline_version,
                    processed_payload_json, cached_at
                ) VALUES (
                    'tournament-lobby-cache', 'match-lobby-cache', 'MATCH_RESULT_UPPER',
                    '${"a".repeat(64)}', 100, 100, 'match-result', 0.0, 0.0,
                    1.0, 1.0, 1, '{}', 1
                )
                """.trimIndent(),
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            14,
            true,
            RankForgeDatabase.MIGRATION_13_14,
        )

        migrated.query("SELECT id FROM tournaments WHERE id = 'tournament-lobby-cache'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.query("SELECT id FROM matches WHERE id = 'match-lobby-cache'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.query(
            "SELECT match_id FROM match_result_ocr_cache WHERE match_id = 'match-lobby-cache'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        assertTrue(migrated.hasTable("match_lobby_ocr_cache"))
        assertTrue(migrated.hasIndex("index_match_lobby_ocr_cache_tournament_id"))
        migrated.query("PRAGMA table_info('match_lobby_ocr_cache')").use { cursor ->
            val primaryKeys = mutableMapOf<String, Int>()
            val columns = buildSet {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    add(name)
                    primaryKeys[name] = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                }
            }
            assertTrue(
                listOf(
                    "tournament_id",
                    "match_id",
                    "lobby_screenshot_index",
                    "screenshot_sha256",
                    "original_width",
                    "original_height",
                    "crop_profile_id",
                    "crop_left",
                    "crop_top",
                    "crop_right",
                    "crop_bottom",
                    "ocr_pipeline_version",
                    "processed_payload_json",
                    "cached_at",
                ).all(columns::contains),
            )
            assertEquals(1, primaryKeys["match_id"])
            assertEquals(2, primaryKeys["lobby_screenshot_index"])
        }
        migrated.query("PRAGMA foreign_key_list('match_lobby_ocr_cache')").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("matches", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
        migrated.close()
    }

    @Test
    fun migrationFromVersion14CreatesParticipantSnapshotWithoutGuessingNoShows() {
        createVersion13Database().use { database ->
            database.execSQL(
                "INSERT INTO tournaments (id, name, date, organizer_name, organizer_contact_number, status, creation_order) " +
                    "VALUES ('tournament-participants', 'Participant Cup', '2026-08-18', 'Organizer', '123', 'CONFIRMED', 1)",
            )
            database.execSQL(
                "INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) " +
                    "VALUES ('match-finalized', 'tournament-participants', 1, '2026-08-18', 'Bermuda', 'FINALIZED')",
            )
            database.execSQL(
                "INSERT INTO matches (id, tournament_id, match_number, date, map_name, status) " +
                    "VALUES ('match-draft', 'tournament-participants', 2, '2026-08-18', 'Bermuda', 'DRAFT')",
            )
            database.execSQL(
                "INSERT INTO match_placements (match_id, team_slot_number, position) VALUES " +
                    "('match-finalized', 1, 1), ('match-finalized', 2, 2)",
            )
            database.execSQL(
                "INSERT INTO match_kills (match_id, team_slot_number, kills) VALUES " +
                    "('match-finalized', 1, 5), ('match-finalized', 2, 0)",
            )
            database.execSQL(
                "INSERT INTO match_draft_values (match_id, team_slot_number, placement_input, kills_input) " +
                    "VALUES ('match-draft', 1, '1', '0')",
            )
        }

        migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            14,
            true,
            RankForgeDatabase.MIGRATION_13_14,
        ).close()

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            15,
            true,
            RankForgeDatabase.MIGRATION_14_15,
        )

        assertTrue(migrated.hasTable("match_participant_results"))
        assertTrue(migrated.hasIndex("index_match_participant_results_match_id"))
        assertTrue(migrated.hasIndex("index_match_participant_results_match_id_placement"))
        migrated.query(
            "SELECT participation_status, placement, kills FROM match_participant_results " +
                "WHERE match_id = 'match-finalized' ORDER BY team_slot_number",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("PARTICIPATED", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(5, cursor.getInt(2))
            assertTrue(cursor.moveToNext())
            assertEquals("PARTICIPATED", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(!cursor.moveToNext())
        }
        migrated.query(
            "SELECT COUNT(*) FROM match_participant_results WHERE match_id = 'match-draft'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query(
            "SELECT placement_input FROM match_draft_values WHERE match_id = 'match-draft'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("1", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun ocrEvidenceSnapshotTransactionPersistsOriginalAndCorrectedRowsSeparately() = runBlocking {
        val database = createInMemoryDatabase()

        try {
            insertTournamentAndMatch(database)

            database.matchOcrEvidenceDao().insertSnapshot(
                matchEvidence = matchOcrEvidence(),
                rowEvidence = matchOcrRows(),
                correctionSnapshots = matchOcrCorrectionSnapshots(),
            )

            assertEquals(matchOcrEvidence(), database.matchOcrEvidenceDao().readMatchEvidence("match-ocr"))

            val rows = database.matchOcrEvidenceDao().readRowEvidence("match-ocr")
            val corrections = database.matchOcrEvidenceDao().readCorrectionSnapshots("match-ocr")
            assertEquals((0..11).toList(), rows.map { it.rowIndex })
            assertEquals((0..11).toList(), corrections.map { it.rowIndex })
            assertEquals(12, rows.size)
            assertEquals(12, corrections.size)
            assertEquals("OCR row 0", rows.first().originalOcrText)
            assertEquals(1, rows.first().originalPlacement)
            assertEquals(12, corrections.first().correctedPlacement)
            assertEquals(0, rows.first().originalKills)
            assertEquals(1, corrections.first().correctedKills)
        } finally {
            database.close()
        }
    }

    @Test
    fun duplicateOcrEvidenceSnapshotIsRejectedWithoutOverwritingOriginalEvidence() = runBlocking {
        val database = createInMemoryDatabase()

        try {
            insertTournamentAndMatch(database)
            database.matchOcrEvidenceDao().insertSnapshot(
                matchEvidence = matchOcrEvidence(),
                rowEvidence = matchOcrRows(),
                correctionSnapshots = matchOcrCorrectionSnapshots(),
            )

            assertThrows(Exception::class.java) {
                runBlocking {
                    database.matchOcrEvidenceDao().insertSnapshot(
                        matchEvidence = matchOcrEvidence(provenance = "changed-provenance"),
                        rowEvidence = matchOcrRows(ocrPrefix = "Changed OCR row"),
                        correctionSnapshots = matchOcrCorrectionSnapshots(),
                    )
                }
            }

            assertEquals(matchOcrEvidence(), database.matchOcrEvidenceDao().readMatchEvidence("match-ocr"))
            assertEquals("OCR row 0", database.matchOcrEvidenceDao().readRowEvidence("match-ocr").first().originalOcrText)
            assertEquals(12, database.matchOcrEvidenceDao().readRowEvidence("match-ocr").size)
            assertEquals(12, database.matchOcrEvidenceDao().readCorrectionSnapshots("match-ocr").size)
        } finally {
            database.close()
        }
    }

    @Test
    fun ocrEvidenceSnapshotTransactionRollsBackWhenOneChildInsertFails() = runBlocking {
        val database = createInMemoryDatabase()

        try {
            insertTournamentAndMatch(database)
            val duplicatedRowEvidence = matchOcrRows().toMutableList().also { rows ->
                rows[1] = rows[0].copy(originalOcrText = "duplicate row key")
            }

            assertThrows(Exception::class.java) {
                runBlocking {
                    database.matchOcrEvidenceDao().insertSnapshot(
                        matchEvidence = matchOcrEvidence(),
                        rowEvidence = duplicatedRowEvidence,
                        correctionSnapshots = matchOcrCorrectionSnapshots(),
                    )
                }
            }

            assertNull(database.matchOcrEvidenceDao().readMatchEvidence("match-ocr"))
            assertTrue(database.matchOcrEvidenceDao().readRowEvidence("match-ocr").isEmpty())
            assertTrue(database.matchOcrEvidenceDao().readCorrectionSnapshots("match-ocr").isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingMatchCascadesOcrEvidenceAndCorrectionSnapshots() = runBlocking {
        val database = createInMemoryDatabase()

        try {
            insertTournamentAndMatch(database)
            database.matchOcrEvidenceDao().insertSnapshot(
                matchEvidence = matchOcrEvidence(),
                rowEvidence = matchOcrRows(),
                correctionSnapshots = matchOcrCorrectionSnapshots(),
            )

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM matches WHERE id = ?",
                arrayOf("match-ocr"),
            )

            assertNull(database.matchOcrEvidenceDao().readMatchEvidence("match-ocr"))
            assertTrue(database.matchOcrEvidenceDao().readRowEvidence("match-ocr").isEmpty())
            assertTrue(database.matchOcrEvidenceDao().readCorrectionSnapshots("match-ocr").isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun roomRepositoryFinalizeDraftMatchWithOcrEvidenceCommitsMatchAndEvidenceAtomically() = runBlocking {
        val database = createInMemoryDatabase()

        try {
            val repository = RoomTournamentRepository(database)
            repository.create(domainTournament())
            repository.saveTeamNames(
                "tournament-ocr",
                TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" },
            )
            repository.createDraftMatch(domainMatch())

            val result = repository.finalizeDraftMatchWithOcrEvidence(
                matchId = "match-ocr",
                placements = finalizedPlacements(),
                kills = finalizedKills(),
                evidence = preservedOcrEvidence(),
            )

            val finalized = result as FinalizeMatchRepositoryResult.Finalized
            assertEquals(MatchStatus.FINALIZED, finalized.match.status)
            assertEquals("FINALIZED", database.matchDao().observeById("match-ocr").first()!!.status)
            assertEquals(12, database.matchPlacementDao().observeByMatchId("match-ocr").first().size)
            assertEquals(12, database.matchKillDao().observeByMatchId("match-ocr").first().size)
            assertEquals("OCR_REVIEW_FINALIZATION", database.matchOcrEvidenceDao().readMatchEvidence("match-ocr")!!.provenance)

            val rows = database.matchOcrEvidenceDao().readRowEvidence("match-ocr")
            val corrections = database.matchOcrEvidenceDao().readCorrectionSnapshots("match-ocr")
            assertEquals((0..11).toList(), rows.map { it.rowIndex })
            assertEquals((0..11).toList(), corrections.map { it.rowIndex })
            assertEquals("Repository OCR row 0", rows.first().originalOcrText)
            assertEquals(12, rows.first().originalPlacement)
            assertEquals(10, rows.first().originalKills)
            assertEquals(1, corrections.first().correctedPlacement)
            assertEquals(0, corrections.first().correctedKills)
            assertEquals(1, corrections.first().correctedTeamSlot)
        } finally {
            database.close()
        }
    }

    @Test
    fun roomRepositoryFinalizeDraftMatchWithDuplicateOcrEvidenceRollsBackFinalization() = runBlocking {
        val database = createInMemoryDatabase()

        try {
            val repository = RoomTournamentRepository(database)
            repository.create(domainTournament())
            repository.saveTeamNames(
                "tournament-ocr",
                TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" },
            )
            repository.createDraftMatch(domainMatch())
            database.matchOcrEvidenceDao().insertSnapshot(
                matchEvidence = matchOcrEvidence(),
                rowEvidence = matchOcrRows(),
                correctionSnapshots = matchOcrCorrectionSnapshots(),
            )

            val result = repository.finalizeDraftMatchWithOcrEvidence(
                matchId = "match-ocr",
                placements = finalizedPlacements(),
                kills = finalizedKills(),
                evidence = preservedOcrEvidence(),
            )

            val rejected = result as FinalizeMatchRepositoryResult.Rejected
            assertEquals(FinalizeMatchFailure.INVALID_DATA, rejected.reason)
            assertEquals("DRAFT", database.matchDao().observeById("match-ocr").first()!!.status)
            assertTrue(database.matchPlacementDao().observeByMatchId("match-ocr").first().isEmpty())
            assertTrue(database.matchKillDao().observeByMatchId("match-ocr").first().isEmpty())
            assertEquals(matchOcrEvidence(), database.matchOcrEvidenceDao().readMatchEvidence("match-ocr"))
            assertEquals(12, database.matchOcrEvidenceDao().readRowEvidence("match-ocr").size)
            assertEquals(12, database.matchOcrEvidenceDao().readCorrectionSnapshots("match-ocr").size)
        } finally {
            database.close()
        }
    }

    @Test
    fun freshVersion3DatabaseProvidesObservableDaosAndEnforcesStructuralForeignKeys() {
        runBlocking {
            val database = Room.inMemoryDatabaseBuilder(
                context,
                RankForgeDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()

            try {
                val tournament = TournamentEntity(
                    id = "tournament-1",
                    name = "Summer Cup",
                    date = LocalDate.of(2026, 7, 26).toString(),
                    organizerName = "Organizer",
                    organizerContactNumber = "1234567890",
                    status = "DRAFT",
                )
                database.tournamentDao().upsert(tournament)
                assertEquals(
                    tournament,
                    database.tournamentDao().observeById(tournament.id).first(),
                )

                val secondSlot = TeamSlotEntity(
                    tournamentId = tournament.id,
                    slotNumber = 2,
                    teamName = "Team Two",
                )
                val firstSlot = TeamSlotEntity(
                    tournamentId = tournament.id,
                    slotNumber = 1,
                    teamName = "Team One",
                )
                database.teamSlotDao().upsertAll(listOf(secondSlot, firstSlot))
                assertEquals(
                    listOf(firstSlot, secondSlot),
                    database.teamSlotDao()
                        .observeByTournamentId(tournament.id)
                        .first(),
                )

                val secondPlayer = RosterPlayerEntity(
                    tournamentId = tournament.id,
                    slotNumber = 1,
                    rosterPosition = 2,
                    displayName = "Player Two",
                )
                val firstPlayer = RosterPlayerEntity(
                    tournamentId = tournament.id,
                    slotNumber = 1,
                    rosterPosition = 1,
                    displayName = "Player One",
                )
                database.rosterPlayerDao().upsertAll(
                    listOf(secondPlayer, firstPlayer),
                )
                assertEquals(
                    listOf(firstPlayer, secondPlayer),
                    database.rosterPlayerDao()
                        .observeByTournamentAndSlot(tournament.id, 1)
                        .first(),
                )

                val match = MatchEntity(
                    id = "match-1",
                    tournamentId = tournament.id,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 26).toString(),
                    mapName = "Bermuda",
                    status = "DRAFT",
                )
                database.matchDao().upsert(match)
                assertEquals(
                    listOf(match),
                    database.matchDao()
                        .observeByTournamentId(tournament.id)
                        .first(),
                )

                assertThrows(Exception::class.java) {
                    runBlocking {
                        database.teamSlotDao().upsertAll(
                            listOf(
                                TeamSlotEntity(
                                    tournamentId = "missing-tournament",
                                    slotNumber = 1,
                                    teamName = "Missing",
                                ),
                            ),
                        )
                    }
                }

                assertThrows(Exception::class.java) {
                    runBlocking {
                        database.rosterPlayerDao().upsertAll(
                            listOf(
                                RosterPlayerEntity(
                                    tournamentId = tournament.id,
                                    slotNumber = 12,
                                    rosterPosition = 1,
                                    displayName = "Missing Parent",
                                ),
                            ),
                        )
                    }
                }

                assertThrows(Exception::class.java) {
                    runBlocking {
                        database.matchDao().upsert(
                            MatchEntity(
                                id = "missing-parent-match",
                                tournamentId = "missing-tournament",
                                matchNumber = 2,
                                date = LocalDate.of(2026, 7, 26).toString(),
                                mapName = "Bermuda",
                                status = "DRAFT",
                            ),
                        )
                    }
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun deletingTournamentCascadesAllLocalChildData() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            RankForgeDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            database.tournamentDao().upsert(
                TournamentEntity(
                    id = "tournament-1",
                    name = "Summer Cup",
                    date = LocalDate.of(2026, 7, 26).toString(),
                    organizerName = "Organizer",
                    organizerContactNumber = "1234567890",
                    status = "CONFIRMED",
                ),
            )
            database.teamSlotDao().upsertAll(
                listOf(TeamSlotEntity("tournament-1", 1, "Team One")),
            )
            database.rosterPlayerDao().upsertAll(
                listOf(RosterPlayerEntity("tournament-1", 1, 1, "Player One")),
            )
            database.matchDao().upsert(
                MatchEntity(
                    id = "match-1",
                    tournamentId = "tournament-1",
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 26).toString(),
                    mapName = "Bermuda",
                    status = "FINALIZED",
                ),
            )
            database.matchPlacementDao().upsertAll(
                listOf(MatchPlacementEntity("match-1", 1, 1)),
            )
            database.matchKillDao().upsertAll(
                listOf(MatchKillEntity("match-1", 1, 3)),
            )
            database.matchDraftValueDao().upsert(
                MatchDraftValueEntity("match-1", 1, "1", "3"),
            )
            database.matchCorrectionDao().upsertAll(
                listOf(MatchCorrectionEntity("match-1", 0, "[]", "[]", "[]", "[]")),
            )

            database.tournamentDao().deleteById("tournament-1")

            assertTrue(database.tournamentDao().observeAll().first().isEmpty())
            assertTrue(database.teamSlotDao().observeByTournamentId("tournament-1").first().isEmpty())
            assertTrue(database.rosterPlayerDao().observeByTournamentId("tournament-1").first().isEmpty())
            assertTrue(database.matchDao().observeAll().first().isEmpty())
            assertTrue(database.matchPlacementDao().observeByMatchId("match-1").first().isEmpty())
            assertTrue(database.matchKillDao().observeByMatchId("match-1").first().isEmpty())
            assertTrue(database.matchDraftValueDao().observeByMatchId("match-1").first().isEmpty())
            assertTrue(database.matchCorrectionDao().observeByMatchId("match-1").first().isEmpty())
        } finally {
            database.close()
        }
    }

    private fun createVersion1Database(): SupportSQLiteDatabase =
        migrationTestHelper().createDatabase(
            MIGRATION_DATABASE_NAME,
            1,
        )

    private fun createVersion2Database(): SupportSQLiteDatabase =
        migrationTestHelper().createDatabase(
            MIGRATION_DATABASE_NAME,
            2,
        )

    private fun createVersion12Database(): SupportSQLiteDatabase =
        migrationTestHelper().createDatabase(
            MIGRATION_DATABASE_NAME,
            12,
        )

    private fun createVersion13Database(): SupportSQLiteDatabase =
        migrationTestHelper().createDatabase(
            MIGRATION_DATABASE_NAME,
            13,
        )

    private fun createInMemoryDatabase(): RankForgeDatabase =
        Room.inMemoryDatabaseBuilder(
            context,
            RankForgeDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

    private suspend fun insertTournamentAndMatch(database: RankForgeDatabase) {
        database.tournamentDao().upsert(
            TournamentEntity(
                id = "tournament-ocr",
                name = "OCR Cup",
                date = LocalDate.of(2026, 7, 31).toString(),
                organizerName = "Organizer",
                organizerContactNumber = "1234567890",
                status = "CONFIRMED",
            ),
        )
        database.matchDao().upsert(
            MatchEntity(
                id = "match-ocr",
                tournamentId = "tournament-ocr",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 31).toString(),
                mapName = "Bermuda",
                status = "DRAFT",
            ),
        )
    }

    private fun matchOcrEvidence(
        provenance: String = "ocr-review-finalization",
    ) = MatchOcrEvidenceEntity(
        matchId = "match-ocr",
        tournamentId = "tournament-ocr",
        sourceScreenshotId = "screenshot-ocr",
        preservedAt = 1_800_000_000_000,
        provenance = provenance,
    )

    private fun matchOcrRows(
        ocrPrefix: String = "OCR row",
    ): List<MatchOcrRowEvidenceEntity> =
        (0..11).map { rowIndex ->
            MatchOcrRowEvidenceEntity(
                matchId = "match-ocr",
                tournamentId = "tournament-ocr",
                rowIndex = rowIndex,
                originalOcrText = "$ocrPrefix $rowIndex",
                originalPlacement = rowIndex + 1,
                originalKills = rowIndex,
                originalSuggestedTeamSlot = 12 - rowIndex,
                confidenceSummary = "confidence-$rowIndex",
                safetySummary = "safety-$rowIndex",
                manualReviewRequired = rowIndex % 2 == 0,
            )
        }

    private fun matchOcrCorrectionSnapshots(): List<MatchOcrCorrectionSnapshotEntity> =
        (0..11).map { rowIndex ->
            MatchOcrCorrectionSnapshotEntity(
                matchId = "match-ocr",
                tournamentId = "tournament-ocr",
                rowIndex = rowIndex,
                correctedPlacement = 12 - rowIndex,
                correctedKills = rowIndex + 1,
                correctedTeamSlot = rowIndex + 1,
                placementChanged = true,
                killsChanged = true,
                teamSlotChanged = true,
                preservedAt = 1_800_000_000_000,
                provenance = "ocr-review-finalization",
            )
        }

    private fun domainTournament(): DomainTournament =
        DomainTournament(
            id = "tournament-ocr",
            name = "OCR Cup",
            date = LocalDate.of(2026, 7, 31),
            organizerName = "Organizer",
            organizerContactNumber = "1234567890",
            status = TournamentStatus.CONFIRMED,
        )

    private fun domainMatch(): DomainMatch =
        DomainMatch(
            id = "match-ocr",
            tournamentId = "tournament-ocr",
            matchNumber = 1,
            date = LocalDate.of(2026, 7, 31),
            mapName = "Bermuda",
            status = MatchStatus.DRAFT,
        )

    private fun finalizedPlacements(): List<MatchPlacement> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchPlacement(
                teamSlotNumber = slotNumber,
                position = slotNumber,
            )
        }

    private fun finalizedKills(): List<MatchKill> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            MatchKill(
                teamSlotNumber = slotNumber,
                kills = slotNumber - 1,
            )
        }

    private fun preservedOcrEvidence(): PreservedMatchOcrEvidence =
        PreservedMatchOcrEvidence(
            tournamentId = "tournament-ocr",
            matchId = "match-ocr",
            sourceScreenshotId = "screenshot-ocr",
            preservedAt = 1_800_000_000_000,
            provenance = "OCR_REVIEW_FINALIZATION",
            rows = (0..11).map { rowIndex ->
                PreservedMatchOcrRowEvidence(
                    rowIndex = rowIndex,
                    originalOcrText = "Repository OCR row $rowIndex",
                    originalPlacement = 12 - rowIndex,
                    originalKills = rowIndex + 10,
                    originalSuggestedTeamSlot = 12 - rowIndex,
                    confidenceSummary = "repository-confidence-$rowIndex",
                    safetySummary = "repository-safety-$rowIndex",
                    manualReviewRequired = rowIndex % 2 == 0,
                )
            },
            correctionSnapshots = (0..11).map { rowIndex ->
                PreservedMatchOcrCorrectionSnapshot(
                    rowIndex = rowIndex,
                    correctedPlacement = rowIndex + 1,
                    correctedKills = rowIndex,
                    correctedTeamSlot = rowIndex + 1,
                    placementChanged = true,
                    killsChanged = true,
                    teamSlotChanged = true,
                )
            },
        )

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun SupportSQLiteDatabase.hasIndex(indexName: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(indexName)).use { cursor ->
            cursor.moveToFirst()
        }

    private class ProductionMigrationDatabaseContext(
        baseContext: Context,
        private val redirectedDatabaseFile: File,
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File =
            if (name == PRODUCTION_DATABASE_NAME) {
                redirectedDatabaseFile
            } else {
                super.getDatabasePath(name)
            }
    }

    private fun migrationTestHelper() = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RankForgeDatabase::class.java,
    )

    private companion object {
        const val MIGRATION_DATABASE_NAME = "rank-forge-migration-test.db"
        const val PRODUCTION_DATABASE_NAME = "rank_forge.db"
    }
}
