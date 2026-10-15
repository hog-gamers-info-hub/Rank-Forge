package com.hoggamers.rankforge.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationV4SyncJournalMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration33To34PreservesStandardQueueAndUuidMatchData() {
        migrationTestHelper().createDatabase(DATABASE_NAME, 33).use { database ->
            database.execSQL(
                "INSERT INTO tournaments " +
                    "(id, name, organizer_name, organizer_contact_number, status, creation_order, owner_user_id, format, group_count) " +
                    "VALUES ('standard-queue', 'Standard Queue', 'Stage', '123', 'DRAFT', 1, 'owner-a', 'STANDARD', NULL)",
            )
            database.execSQL(
                "INSERT INTO sync_queue_entries " +
                    "(id, operationType, tournamentId, createdAtEpochMillis, status, failureCategory, attemptCount, owner_user_id) " +
                    "VALUES ('standard-queue-entry', 'TOURNAMENT_UPLOAD', 'standard-queue', 11, 'BLOCKED_NETWORK', 'network', 2, 'owner-a')",
            )
            database.execSQL(
                "INSERT INTO tournaments " +
                    "(id, name, organizer_name, organizer_contact_number, status, creation_order, owner_user_id, format, group_count) " +
                    "VALUES ('rotation-journal', 'Rotation Journal', 'Stage', '123', 'CONFIRMED', 2, 'owner-a', 'GROUP_ROTATION', 3)",
            )
            database.execSQL(
                "INSERT INTO tournament_group_pairings " +
                    "(tournament_id, pairing_key, first_group, second_group) " +
                    "VALUES ('rotation-journal', 'A:B', 'A', 'B')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_team_identities " +
                    "(tournament_id, team_uuid, display_name, normalized_name) " +
                    "VALUES ('rotation-journal', '00000000-0000-4000-8000-000000000101', 'Journal Team', 'journal team')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_pairing_team_identity_assignments " +
                    "(tournament_id, pairing_key, lobby_slot_number, team_uuid) " +
                    "VALUES ('rotation-journal', 'A:B', 4, '00000000-0000-4000-8000-000000000101')",
            )
            database.execSQL(
                "INSERT INTO matches " +
                    "(id, tournament_id, match_number, date, map_name, status, group_pairing_key) " +
                    "VALUES ('rotation-journal-match', 'rotation-journal', 1, '2026-10-12', 'Bermuda', 'DRAFT', 'A:B')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_match_identity_headers " +
                    "(match_id, tournament_id, pairing_key) " +
                    "VALUES ('rotation-journal-match', 'rotation-journal', 'A:B')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_match_team_assignments " +
                    "(match_id, tournament_id, lobby_slot_number, team_uuid) " +
                    "VALUES ('rotation-journal-match', 'rotation-journal', 4, '00000000-0000-4000-8000-000000000101')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_match_participant_results " +
                    "(match_id, tournament_id, team_uuid, participation_status, placement, kills, point_adjustment) " +
                    "VALUES ('rotation-journal-match', 'rotation-journal', '00000000-0000-4000-8000-000000000101', 'PARTICIPATED', NULL, 3, 0)",
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            DATABASE_NAME,
            34,
            true,
            RankForgeDatabase.MIGRATION_33_34,
        )

        assertTrue(migrated.hasTable("group_rotation_v4_sync_journal"))
        assertTrue(migrated.hasIndex("index_group_rotation_v4_sync_journal_request_id"))
        assertTrue(
            migrated.hasIndex(
                "index_group_rotation_v4_sync_journal_owner_user_id_tournament_id_status_updated_at_epoch_millis",
            ),
        )
        migrated.query(
            "SELECT status, attemptCount, owner_user_id FROM sync_queue_entries WHERE id = 'standard-queue-entry'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BLOCKED_NETWORK", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals("owner-a", cursor.getString(2))
        }
        migrated.query(
            "SELECT display_name FROM group_rotation_team_identities " +
                "WHERE tournament_id = 'rotation-journal'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Journal Team", cursor.getString(0))
        }
        migrated.query(
            "SELECT pairing_key, lobby_slot_number, team_uuid " +
                "FROM group_rotation_pairing_team_identity_assignments " +
                "WHERE tournament_id = 'rotation-journal'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A:B", cursor.getString(0))
            assertEquals(4, cursor.getInt(1))
            assertEquals("00000000-0000-4000-8000-000000000101", cursor.getString(2))
        }
        migrated.query(
            "SELECT pairing_key FROM group_rotation_match_identity_headers " +
                "WHERE match_id = 'rotation-journal-match'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A:B", cursor.getString(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        migrated.close()
    }

    @Test
    fun migration34To35DefaultsNameClearedWithoutChangingAssignmentIdentity() {
        migrationTestHelper().createDatabase(DATABASE_NAME, 34).use { database ->
            database.execSQL(
                "INSERT INTO tournaments " +
                    "(id, name, organizer_name, organizer_contact_number, status, creation_order, owner_user_id, format, group_count) " +
                    "VALUES ('name-cleared-migration', 'Name Cleared', 'Stage', '123', 'DRAFT', 1, 'owner-a', 'GROUP_ROTATION', 3)",
            )
            database.execSQL(
                "INSERT INTO tournament_group_pairings " +
                    "(tournament_id, pairing_key, first_group, second_group) " +
                    "VALUES ('name-cleared-migration', 'A:B', 'A', 'B')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_team_identities " +
                    "(tournament_id, team_uuid, display_name, normalized_name) " +
                    "VALUES ('name-cleared-migration', '00000000-0000-4000-8000-000000000201', 'Stable Team', 'stable team')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_pairing_team_identity_assignments " +
                    "(tournament_id, pairing_key, lobby_slot_number, team_uuid) " +
                    "VALUES ('name-cleared-migration', 'A:B', 3, '00000000-0000-4000-8000-000000000201')",
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            DATABASE_NAME,
            35,
            true,
            RankForgeDatabase.MIGRATION_34_35,
        )

        migrated.query(
            "SELECT pairing_key, lobby_slot_number, team_uuid, name_cleared " +
                "FROM group_rotation_pairing_team_identity_assignments " +
                "WHERE tournament_id = 'name-cleared-migration'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A:B", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals("00000000-0000-4000-8000-000000000201", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
        migrated.close()
    }

    @Test
    fun migration35To36AllowsMultipleClearedIdentitiesWithoutChangingAssignments() {
        migrationTestHelper().createDatabase(DATABASE_NAME, 35).use { database ->
            database.execSQL(
                "INSERT INTO tournaments " +
                    "(id, name, organizer_name, organizer_contact_number, status, creation_order, owner_user_id, format, group_count) " +
                    "VALUES ('multiple-cleared-identities', 'Multiple Cleared', 'Stage', '123', 'DRAFT', 1, 'owner-a', 'GROUP_ROTATION', 3)",
            )
            database.execSQL(
                "INSERT INTO tournament_group_pairings " +
                    "(tournament_id, pairing_key, first_group, second_group) " +
                    "VALUES ('multiple-cleared-identities', 'A:B', 'A', 'B')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_team_identities " +
                    "(tournament_id, team_uuid, display_name, normalized_name) " +
                    "VALUES ('multiple-cleared-identities', '00000000-0000-4000-8000-000000000301', 'Stable Team', 'stable team')",
            )
            database.execSQL(
                "INSERT INTO group_rotation_pairing_team_identity_assignments " +
                    "(tournament_id, pairing_key, lobby_slot_number, team_uuid, name_cleared) " +
                    "VALUES ('multiple-cleared-identities', 'A:B', 3, '00000000-0000-4000-8000-000000000301', 0)",
            )
        }

        val migrated = migrationTestHelper().runMigrationsAndValidate(
            DATABASE_NAME,
            36,
            true,
            RankForgeDatabase.MIGRATION_35_36,
        )

        migrated.execSQL(
            "INSERT INTO group_rotation_team_identities " +
                "(tournament_id, team_uuid, display_name, normalized_name) VALUES " +
                "('multiple-cleared-identities', '00000000-0000-4000-8000-000000000302', '', ''), " +
                "('multiple-cleared-identities', '00000000-0000-4000-8000-000000000303', '', '')",
        )
        migrated.query(
            "SELECT COUNT(*) FROM group_rotation_team_identities " +
                "WHERE tournament_id = 'multiple-cleared-identities' AND normalized_name = ''",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query(
            "SELECT pairing_key, lobby_slot_number, team_uuid, name_cleared " +
                "FROM group_rotation_pairing_team_identity_assignments " +
                "WHERE tournament_id = 'multiple-cleared-identities'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A:B", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals("00000000-0000-4000-8000-000000000301", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
        migrated.close()
    }

    private fun migrationTestHelper() = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RankForgeDatabase::class.java,
    )

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun SupportSQLiteDatabase.hasIndex(indexName: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(indexName)).use { cursor ->
            cursor.moveToFirst()
        }

    private companion object {
        const val DATABASE_NAME = "group-rotation-v4-journal-migration-test.db"
    }
}
