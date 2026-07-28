package com.hoggamers.rankforge.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun migrationTestHelper() = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RankForgeDatabase::class.java,
    )

    private companion object {
        const val MIGRATION_DATABASE_NAME = "rank-forge-migration-test.db"
    }
}
