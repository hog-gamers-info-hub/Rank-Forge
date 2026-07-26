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
    fun freshVersion2DatabaseProvidesObservableDaosAndEnforcesStructuralForeignKeys() {
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

    private fun createVersion1Database(): SupportSQLiteDatabase =
        migrationTestHelper().createDatabase(
            MIGRATION_DATABASE_NAME,
            1,
        )

    private fun migrationTestHelper() = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RankForgeDatabase::class.java,
    )

    private companion object {
        const val MIGRATION_DATABASE_NAME = "rank-forge-migration-test.db"
    }
}
