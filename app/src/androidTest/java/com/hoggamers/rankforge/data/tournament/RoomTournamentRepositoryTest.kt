package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.RankForgeStateEntity
import com.hoggamers.rankforge.data.local.MatchCorrectionEntity
import com.hoggamers.rankforge.data.local.MatchDraftValueEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.MatchKillEntity
import com.hoggamers.rankforge.data.local.MatchPlacementEntity
import com.hoggamers.rankforge.data.local.RosterPlayerEntity
import com.hoggamers.rankforge.data.local.TeamSlotEntity
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionRepositoryResult
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTournamentRepositoryTest {
    @Test
    fun twelveTeamSlotsPersistThroughDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-slots-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            repository.create(tournament("tournament-1", TournamentStatus.DRAFT))
            assertEquals(12, repository.observeSlotsByTournamentId("tournament-1").first().size)
            databases.last().close()

            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)

            assertEquals(12, reopenedRepository.observeSlotsByTournamentId("tournament-1").first().size)
            assertEquals(12, reopenedDatabase.teamSlotDao().observeByTournamentId("tournament-1").first().size)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun createPersistsTournamentInNormalizedTableAndObservationsReadIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-tournament-create.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            val tournament = tournament("tournament-1", TournamentStatus.DRAFT)

            repository.create(tournament)

            assertEquals(tournament, repository.observeAll().first { it.isNotEmpty() }.single())
            assertEquals(tournament, repository.observeById(tournament.id).first { it != null })
            assertEquals(
                tournament,
                database.tournamentDao().observeById(tournament.id).first()!!.toDomain(),
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun tournamentAndConfirmedStatusSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-tournament-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            val tournament = tournament("tournament-1", TournamentStatus.DRAFT)
            repository.create(tournament)
            assertTrue(repository.confirmTournament(tournament.id))
            databases.last().close()

            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)

            assertEquals(
                TournamentStatus.CONFIRMED,
                reopenedRepository.observeById(tournament.id).first { it != null }!!.status,
            )
            assertEquals(
                TournamentStatus.CONFIRMED,
                reopenedDatabase.tournamentDao().observeById(tournament.id).first()!!.status.let(TournamentStatus::valueOf),
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun editingTeamNamesInvalidatesConfirmedStatusInNormalizedTableAndSurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-team-status.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            repository.create(tournament("tournament-1", TournamentStatus.CONFIRMED))
            repository.saveTeamNames("tournament-1", mapOf(1 to "Alpha"))
            databases.last().close()

            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)

            assertEquals(TournamentStatus.DRAFT, reopenedRepository.observeById("tournament-1").first { it != null }!!.status)
            assertEquals(
                "Alpha",
                reopenedRepository.observeSlotsByTournamentId("tournament-1")
                    .first { slots -> slots.any { it.teamName == "Alpha" } }
                    .first { it.slotNumber == 1 }
                    .teamName,
            )
            assertEquals("DRAFT", reopenedDatabase.tournamentDao().observeById("tournament-1").first()!!.status)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun editingRosterInvalidatesConfirmedStatusInNormalizedTableAndSurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-roster-status.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            repository.create(tournament("tournament-1", TournamentStatus.CONFIRMED))
            repository.saveRoster("tournament-1", 1, listOf(RosterPlayer("tournament-1", 1, "Player One")))
            databases.last().close()

            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)

            assertEquals(TournamentStatus.DRAFT, reopenedRepository.observeById("tournament-1").first { it != null }!!.status)
            assertEquals("DRAFT", reopenedDatabase.tournamentDao().observeById("tournament-1").first()!!.status)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun legacyOnlySlotsAndRostersAreBackfilledIdempotently() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-legacy-backfill.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            database.stateDao().save(
                RankForgeStateEntity(
                    payload = legacyPayload(
                        id = "legacy-tournament",
                        name = "Legacy Cup",
                        status = "DRAFT",
                        slots = (1..12).map { it to "Legacy Team $it" },
                        rosters = listOf(
                            Triple(1, 1, "Legacy Player One"),
                            Triple(1, 2, "Legacy Player Two"),
                        ),
                    ),
                ),
            )
            val repository = RoomTournamentRepository(database)

            val backfilledTournament = repository.observeById("legacy-tournament").first { it != null }!!
            assertEquals("Legacy Cup", backfilledTournament.name)
            assertEquals(
                listOf(backfilledTournament),
                repository.observeAll().first(),
            )
            assertEquals(1, database.tournamentDao().observeAll().first().size)
            assertEquals(
                (1..12).toList(),
                database.teamSlotDao().observeByTournamentId("legacy-tournament").first().map { it.slotNumber },
            )
            assertEquals(
                listOf("Legacy Player One", "Legacy Player Two"),
                database.rosterPlayerDao().observeByTournamentAndSlot("legacy-tournament", 1)
                    .first()
                    .map { it.displayName },
            )

            database.close()
            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)
            assertEquals("Legacy Cup", reopenedRepository.observeById("legacy-tournament").first { it != null }!!.name)
            assertEquals(1, reopenedDatabase.tournamentDao().observeAll().first().size)
            assertEquals(12, reopenedDatabase.teamSlotDao().observeByTournamentId("legacy-tournament").first().size)
            assertEquals(2, reopenedDatabase.rosterPlayerDao().observeByTournamentAndSlot("legacy-tournament", 1).first().size)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun legacyOnlyMatchesAndResultsAreBackfilledIdempotently() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-legacy-matches.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val legacyMatch = finalizedMatch("legacy-tournament", "match-1")
            database.stateDao().save(
                RankForgeStateEntity(
                    payload = legacyPayload(
                        id = "legacy-tournament",
                        name = "Legacy Cup",
                        status = "CONFIRMED",
                        matches = listOf(legacyMatch),
                        draftValues = listOf(
                            Triple("match-1", 1, MatchDraftFieldValues("raw-placement", "raw-kills")),
                        ),
                    ),
                ),
            )

            val repository = RoomTournamentRepository(database)
            val restored = repository.observeMatchById("match-1").first { it != null }!!
            assertEquals(legacyMatch, restored)
            assertEquals(
                16,
                regenerateStandings(repository, "legacy-tournament")
                    .first { it.teamSlotNumber == 1 }
                    .totalPoints,
            )
            assertEquals(1, database.matchDao().observeAll().first().size)
            assertEquals(2, database.matchPlacementDao().observeByMatchId("match-1").first().size)
            assertEquals(2, database.matchKillDao().observeByMatchId("match-1").first().size)
            assertEquals(1, database.matchCorrectionDao().observeByMatchId("match-1").first().size)
            assertEquals(1, database.matchDraftValueDao().observeByMatchId("match-1").first().size)

            database.close()
            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)
            assertEquals(legacyMatch, reopenedRepository.observeMatchById("match-1").first { it != null })
            assertEquals(
                16,
                regenerateStandings(reopenedRepository, "legacy-tournament")
                    .first { it.teamSlotNumber == 1 }
                    .totalPoints,
            )
            assertEquals(1, reopenedDatabase.matchDao().observeAll().first().size)
            assertEquals(2, reopenedDatabase.matchPlacementDao().observeByMatchId("match-1").first().size)
            assertEquals(2, reopenedDatabase.matchKillDao().observeByMatchId("match-1").first().size)
            assertEquals(1, reopenedDatabase.matchCorrectionDao().observeByMatchId("match-1").first().size)
            assertEquals(1, reopenedDatabase.matchDraftValueDao().observeByMatchId("match-1").first().size)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun standingsRegenerateFromNormalizedFinalizedMatchesAcrossReopenAndIgnoreDrafts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-standings-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.finalizeDraftMatch(
                "match-1",
                (1..12).map { slot -> MatchPlacement(slot, slot) },
                (1..12).map { slot -> MatchKill(slot, if (slot == 1) 3 else 0) },
            )
            repository.createDraftMatch(draftMatch("tournament-1", "match-2", 2))
            repository.saveDraftMatchPlacements("match-2", listOf(MatchPlacement(1, 1)))
            repository.saveDraftMatchKills("match-2", listOf(MatchKill(1, 99)))

            val expected = regenerateStandings(repository, "tournament-1")
            assertEquals(12, expected.size)
            assertEquals(15, expected.first { it.teamSlotNumber == 1 }.totalPoints)
            assertEquals(1, expected.first { it.teamSlotNumber == 1 }.matchesIncluded)
            assertEquals(0, expected.first { it.teamSlotNumber == 12 }.totalPoints)

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(openDatabase(context, databaseName, databases))

            assertEquals(expected, regenerateStandings(reopenedRepository, "tournament-1"))
            assertEquals(
                MatchStatus.DRAFT,
                reopenedRepository.observeMatchById("match-2").first { it != null }!!.status,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun correctedFinalizedMatchesRegenerateCumulativeStandingsAcrossReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-standings-correction.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.finalizeDraftMatch(
                "match-1",
                (1..12).map { slot -> MatchPlacement(slot, slot) },
                (1..12).map { slot -> MatchKill(slot, if (slot == 1) 3 else 0) },
            )
            repository.createDraftMatch(draftMatch("tournament-1", "match-2", 2))
            repository.finalizeDraftMatch(
                "match-2",
                (1..12).map { slot -> MatchPlacement(slot, slot) },
                (1..12).map { slot -> MatchKill(slot, if (slot == 1) 4 else 0) },
            )

            val beforeCorrection = regenerateStandings(repository, "tournament-1")
            assertEquals(31, beforeCorrection.first { it.teamSlotNumber == 1 }.totalPoints)
            assertEquals(2, beforeCorrection.first { it.teamSlotNumber == 1 }.matchesIncluded)

            repository.saveDraftMatchValue("tournament-1", "match-2", 1, "2", "9")
            repository.submitMatchCorrection(
                "match-2",
                (1..12).map { slot ->
                    MatchPlacement(slot, when (slot) {
                        1 -> 2
                        2 -> 1
                        else -> slot
                    })
                },
                (1..12).map { slot -> MatchKill(slot, if (slot == 1) 9 else 0) },
            )

            val afterCorrection = regenerateStandings(repository, "tournament-1")
            assertEquals(33, afterCorrection.first { it.teamSlotNumber == 1 }.totalPoints)
            assertEquals(1, afterCorrection.first { it.teamSlotNumber == 1 }.firstPlaceFinishes)
            assertEquals(2, afterCorrection.first { it.teamSlotNumber == 1 }.latestMatchPlacement)
            assertEquals(
                1,
                repository.observeMatchById("match-2").first { it != null }!!.correctionHistory.size,
            )
            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-2").first().isEmpty())

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(openDatabase(context, databaseName, databases))

            assertEquals(afterCorrection, regenerateStandings(reopenedRepository, "tournament-1"))
            assertEquals(
                1,
                reopenedRepository.observeMatchById("match-2").first { it != null }!!.correctionHistory.size,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun normalizedMatchRecordsAreNotOverwrittenByStaleLegacyJson() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-normalized-match-authority.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val normalizedTournament = tournament("tournament-1", TournamentStatus.CONFIRMED)
            val normalizedMatch = Match(
                id = "match-1",
                tournamentId = normalizedTournament.id,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Normalized Map",
                status = MatchStatus.DRAFT,
            )
            database.tournamentDao().upsert(normalizedTournament.toEntity())
            database.teamSlotDao().upsertAll(TeamSlotEntity(normalizedTournament.id, 1, "Team One").let { listOf(it) })
            database.matchDao().upsert(normalizedMatch.toEntity())
            database.matchPlacementDao().upsertAll(listOf(MatchPlacementEntity("match-1", 1, 9)))
            database.matchKillDao().upsertAll(listOf(MatchKillEntity("match-1", 1, 8)))
            database.matchDraftValueDao().upsert(
                MatchDraftValueEntity("match-1", 1, "normalized-placement", "normalized-kills"),
            )
            database.stateDao().save(
                RankForgeStateEntity(
                    payload = legacyPayload(
                        id = normalizedTournament.id,
                        name = normalizedTournament.name,
                        status = "CONFIRMED",
                        matches = listOf(
                            normalizedMatch.copy(
                                mapName = "Stale Legacy Map",
                                placements = listOf(MatchPlacement(1, 1)),
                                kills = listOf(MatchKill(1, 1)),
                            ),
                        ),
                        draftValues = listOf(
                            Triple("match-1", 1, MatchDraftFieldValues("stale-placement", "stale-kills")),
                        ),
                    ),
                ),
            )

            val repository = RoomTournamentRepository(database)
            val restored = repository.observeMatchById("match-1").first { it != null }!!
            assertEquals("Normalized Map", restored.mapName)
            assertEquals(listOf(MatchPlacement(1, 9)), restored.placements)
            assertEquals(listOf(MatchKill(1, 8)), restored.kills)
            assertEquals(
                MatchDraftFieldValues("normalized-placement", "normalized-kills"),
                repository.observeDraftMatchValues("tournament-1", "match-1").first()[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun normalizedTournamentIsNotOverwrittenByStaleLegacyJson() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-normalized-authority.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val normalized = tournament("tournament-1", TournamentStatus.CONFIRMED)
            database.tournamentDao().upsert(normalized.toEntity())
            database.teamSlotDao().upsertAll(
                (1..12).map { slotNumber ->
                    TeamSlotEntity(normalized.id, slotNumber, "Normalized Team $slotNumber")
                },
            )
            database.rosterPlayerDao().upsertAll(
                listOf(RosterPlayerEntity(normalized.id, 1, 1, "Normalized Player")),
            )
            database.stateDao().save(
                RankForgeStateEntity(
                    payload = legacyPayload(
                        id = normalized.id,
                        name = "Stale Legacy Name",
                        status = "DRAFT",
                        slots = listOf(1 to "Stale Legacy Team"),
                        rosters = listOf(Triple(1, 1, "Stale Legacy Player")),
                    ),
                ),
            )

            val repository = RoomTournamentRepository(database)

            assertEquals(normalized, repository.observeById(normalized.id).first { it != null })
            assertEquals(normalized, database.tournamentDao().observeById(normalized.id).first()!!.toDomain())

            database.close()
            val reopenedDatabase = openDatabase(context, databaseName, databases)
            val reopenedRepository = RoomTournamentRepository(reopenedDatabase)
            assertEquals(normalized, reopenedRepository.observeById(normalized.id).first { it != null })
            assertEquals(
                "Normalized Team 1",
                reopenedRepository.observeSlotsByTournamentId(normalized.id).first().first().teamName,
            )
            assertEquals(
                "Normalized Player",
                reopenedRepository.observeRosterByTournamentId(normalized.id).first()[1]!!.single().displayName,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun observeRosterByTournamentIdReadsNormalizedRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-normalized-roster-read.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            repository.create(tournament("tournament-1", TournamentStatus.DRAFT))
            database.rosterPlayerDao().upsertAll(
                listOf(RosterPlayerEntity("tournament-1", 1, 1, "Normalized Player")),
            )

            assertEquals(
                "Normalized Player",
                repository.observeRosterByTournamentId("tournament-1")
                    .first { it.isNotEmpty() }[1]!!.single().displayName,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun saveTeamNamesRollsBackAllNormalizedSlotChangesWhenOneWriteFails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-slots-transaction.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            repository.create(tournament("tournament-1", TournamentStatus.CONFIRMED))
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_team_slot_insert
                BEFORE INSERT ON team_slots
                WHEN NEW.tournament_id = 'tournament-1' AND NEW.slot_number = 12
                BEGIN SELECT RAISE(ABORT, 'forced slot insert failure'); END
                """.trimIndent(),
            )
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_team_slot_update
                BEFORE UPDATE ON team_slots
                WHEN NEW.tournament_id = 'tournament-1' AND NEW.slot_number = 12
                BEGIN SELECT RAISE(ABORT, 'forced slot update failure'); END
                """.trimIndent(),
            )

            var failed = false
            try {
                repository.saveTeamNames("tournament-1", (1..12).associateWith { "Updated Team $it" })
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(failed)
            assertTrue(database.teamSlotDao().observeByTournamentId("tournament-1").first().all { it.teamName.isEmpty() })
            assertEquals("CONFIRMED", database.tournamentDao().observeById("tournament-1").first()!!.status)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun saveRosterRollsBackDeleteAndInsertWhenOneWriteFails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-roster-transaction.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            repository.create(tournament("tournament-1", TournamentStatus.DRAFT))
            repository.saveRoster(
                "tournament-1",
                1,
                listOf(RosterPlayer("tournament-1", 1, "Original Player")),
            )
            assertTrue(repository.confirmTournament("tournament-1"))
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_roster_insert
                BEFORE INSERT ON roster_players
                WHEN NEW.tournament_id = 'tournament-1' AND NEW.roster_position = 2
                BEGIN SELECT RAISE(ABORT, 'forced roster insert failure'); END
                """.trimIndent(),
            )

            var failed = false
            try {
                repository.saveRoster(
                    "tournament-1",
                    1,
                    listOf(
                        RosterPlayer("tournament-1", 1, "Updated One"),
                        RosterPlayer("tournament-1", 1, "Updated Two"),
                    ),
                )
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(
                listOf("Original Player"),
                database.rosterPlayerDao().observeByTournamentAndSlot("tournament-1", 1)
                    .first()
                    .map { it.displayName },
            )
            assertEquals("CONFIRMED", database.tournamentDao().observeById("tournament-1").first()!!.status)
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun finalizeMatchRollsBackMetadataAndAllResultTablesWhenOneWriteFails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-match-transaction.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_match_kill_insert
                BEFORE INSERT ON match_kills
                WHEN NEW.match_id = 'match-1' AND NEW.team_slot_number = 12
                BEGIN SELECT RAISE(ABORT, 'forced match kill failure'); END
                """.trimIndent(),
            )

            var failed = false
            try {
                repository.finalizeDraftMatch(
                    "match-1",
                    (1..12).map { MatchPlacement(it, it) },
                    (1..12).map { MatchKill(it, it) },
                )
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(failed)
            assertEquals("DRAFT", database.matchDao().observeById("match-1").first()!!.status)
            assertTrue(database.matchPlacementDao().observeByMatchId("match-1").first().isEmpty())
            assertTrue(database.matchKillDao().observeByMatchId("match-1").first().isEmpty())
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun databaseReopenRestoresDraftState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val database = openDatabase(context, databaseName, databases)
            val repository = RoomTournamentRepository(database)
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 7)))
            repository.saveDraftMatchKills("match-1", listOf(MatchKill(1, 3)))
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "7", "3")

            database.close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )

            assertEquals(
                listOf(MatchPlacement(1, 7)),
                reopenedRepository.observeMatchById("match-1").first { it != null }!!.placements,
            )
            assertEquals(
                listOf(MatchKill(1, 3)),
                reopenedRepository.observeMatchById("match-1").first { it != null }!!.kills,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun rosterAndMatchRestoreAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-roster-match.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveRoster(
                "tournament-1",
                1,
                listOf(RosterPlayer("tournament-1", 1, "Player One")),
            )

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )

            assertEquals(
                "Player One",
                reopenedRepository.observeRosterByTournamentId("tournament-1")
                    .first { it.isNotEmpty() }[1]!!.single().displayName,
            )
            assertEquals(
                "match-1",
                reopenedRepository.observeMatchesByTournamentId("tournament-1")
                    .first { it.isNotEmpty() }.single().id,
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun rawPlacementAndKillInputsRestoreAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-raw-inputs.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveDraftMatchValue(
                "tournament-1",
                "match-1",
                1,
                placementInput = "not-a-number",
            )
            repository.saveDraftMatchValue(
                "tournament-1",
                "match-1",
                1,
                killsInput = "-2",
            )

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )

            assertEquals(
                MatchDraftFieldValues("not-a-number", "-2"),
                reopenedRepository.observeDraftMatchValues("tournament-1", "match-1")
                    .first { it.isNotEmpty() }[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun newMatchDoesNotInheritAnotherMatchDraftValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-match-isolation.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.createDraftMatch(draftMatch("tournament-1", "match-2", 2))
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "7", "3")

            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-2").first().isEmpty())
            assertEquals(
                MatchDraftFieldValues("7", "3"),
                repository.observeDraftMatchValues("tournament-1", "match-1").first()[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun resetAffectsOnlyTheSelectedMatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-reset-isolation.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.createDraftMatch(draftMatch("tournament-1", "match-2", 2))
            repository.saveDraftMatchPlacements("match-1", listOf(MatchPlacement(1, 7)))
            repository.saveDraftMatchKills("match-1", listOf(MatchKill(1, 3)))
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "7", "3")
            repository.saveDraftMatchValue("tournament-1", "match-2", 1, "1", "9")

            repository.clearDraftMatch("tournament-1", "match-1")

            val resetMatch = repository.observeMatchById("match-1").first { it != null }!!
            assertTrue(resetMatch.placements.isEmpty())
            assertTrue(resetMatch.kills.isEmpty())
            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-1").first().isEmpty())
            assertEquals(
                MatchDraftFieldValues("1", "9"),
                repository.observeDraftMatchValues("tournament-1", "match-2").first()[1],
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun finalizedMatchResultsAndStatusSurviveDatabaseReopenAndDraftCacheIsCleared() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-finalized-match.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "1", "4")
            val result = repository.finalizeDraftMatch(
                matchId = "match-1",
                placements = (1..12).map { MatchPlacement(it, it) },
                kills = (1..12).map { MatchKill(it, it - 1) },
            )
            assertTrue(result is FinalizeMatchRepositoryResult.Finalized)

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(
                openDatabase(context, databaseName, databases),
            )
            val reopenedMatch = reopenedRepository.observeMatchById("match-1").first { it != null }!!

            assertEquals(MatchStatus.FINALIZED, reopenedMatch.status)
            assertEquals((1..12).toList(), reopenedMatch.placements.map { it.position })
            assertEquals((0..11).toList(), reopenedMatch.kills.map { it.kills })
            assertTrue(
                reopenedRepository.observeDraftMatchValues("tournament-1", "match-1")
                    .first()
                    .isEmpty(),
            )
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun correctionPersistsHistoryAndClearsCorrectionCacheAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-correction-reopen.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.finalizeDraftMatch(
                "match-1",
                (1..12).map { MatchPlacement(it, it) },
                (1..12).map { MatchKill(it, it - 1) },
            )
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "2", "1")
            val result = repository.submitMatchCorrection(
                "match-1",
                (1..12).map { slot -> MatchPlacement(slot, if (slot == 1) 2 else if (slot == 2) 1 else slot) },
                (1..12).map { slot -> MatchKill(slot, if (slot == 1) 1 else slot - 1) },
            )
            assertTrue(result is SubmitMatchCorrectionRepositoryResult.Submitted)

            databases.last().close()
            val reopenedRepository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            val reopened = reopenedRepository.observeMatchById("match-1").first { it != null }!!

            assertEquals(MatchStatus.FINALIZED, reopened.status)
            assertEquals(2, reopened.placements.first { it.teamSlotNumber == 1 }.position)
            assertEquals(1, reopened.kills.first { it.teamSlotNumber == 1 }.kills)
            assertEquals(1, reopened.correctionHistory.size)
            assertEquals(1, reopened.correctionHistory.single().previousPlacements.first().position)
            assertEquals(2, reopened.correctionHistory.single().correctedPlacements.first().position)
            assertTrue(reopenedRepository.observeDraftMatchValues("tournament-1", "match-1").first().isEmpty())
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun discardingCorrectionKeepsOriginalFinalizedResultUnchanged() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room-repository-correction-discard.db"
        context.deleteDatabase(databaseName)
        val databases = mutableListOf<RankForgeDatabase>()
        try {
            val repository = RoomTournamentRepository(openDatabase(context, databaseName, databases))
            seedTournamentAndMatch(repository, "tournament-1", "match-1")
            repository.finalizeDraftMatch(
                "match-1",
                (1..12).map { MatchPlacement(it, it) },
                (1..12).map { MatchKill(it, it - 1) },
            )
            val before = repository.observeMatchById("match-1").first { it != null }!!
            repository.saveDraftMatchValue("tournament-1", "match-1", 1, "12", "88")

            repository.clearMatchCorrectionDraft("tournament-1", "match-1")

            assertEquals(before, repository.observeMatchById("match-1").first { it != null })
            assertTrue(repository.observeDraftMatchValues("tournament-1", "match-1").first().isEmpty())
        } finally {
            databases.forEach { if (it.isOpen) it.close() }
            context.deleteDatabase(databaseName)
        }
    }

    private fun openDatabase(
        context: android.content.Context,
        databaseName: String,
        databases: MutableList<RankForgeDatabase>,
    ): RankForgeDatabase = Room.databaseBuilder(
        context,
        RankForgeDatabase::class.java,
        databaseName,
    ).build().also { databases += it }

    private fun tournament(id: String, status: TournamentStatus) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = status,
    )

    private fun legacyPayload(
        id: String,
        name: String,
        status: String,
        slots: List<Pair<Int, String>> = emptyList(),
        rosters: List<Triple<Int, Int, String>> = emptyList(),
        matches: List<Match> = emptyList(),
        draftValues: List<Triple<String, Int, MatchDraftFieldValues>> = emptyList(),
    ): String {
        val slotsJson = slots.joinToString(",") { (slotNumber, teamName) ->
            """{"tournamentId":"$id","slotNumber":$slotNumber,"teamName":"$teamName"}"""
        }
        val rostersJson = rosters.joinToString(",") { (slotNumber, _, displayName) ->
            """{"tournamentId":"$id","slotNumber":$slotNumber,"displayName":"$displayName"}"""
        }
        val matchesJson = matches.joinToString(",") { match ->
            val placementsJson = match.placements.joinToString(",") {
                """{"teamSlotNumber":${it.teamSlotNumber},"position":${it.position}}"""
            }
            val killsJson = match.kills.joinToString(",") {
                """{"teamSlotNumber":${it.teamSlotNumber},"kills":${it.kills}}"""
            }
            val correctionsJson = match.correctionHistory.joinToString(",") { correction ->
                val previousPlacements = correction.previousPlacements.joinToString(",") {
                    """{"teamSlotNumber":${it.teamSlotNumber},"position":${it.position}}"""
                }
                val previousKills = correction.previousKills.joinToString(",") {
                    """{"teamSlotNumber":${it.teamSlotNumber},"kills":${it.kills}}"""
                }
                val correctedPlacements = correction.correctedPlacements.joinToString(",") {
                    """{"teamSlotNumber":${it.teamSlotNumber},"position":${it.position}}"""
                }
                val correctedKills = correction.correctedKills.joinToString(",") {
                    """{"teamSlotNumber":${it.teamSlotNumber},"kills":${it.kills}}"""
                }
                """{"previousPlacements":[$previousPlacements],"previousKills":[$previousKills],"correctedPlacements":[$correctedPlacements],"correctedKills":[$correctedKills]}"""
            }
            """{"id":"${match.id}","tournamentId":"${match.tournamentId}","matchNumber":${match.matchNumber},"date":"${match.date}","mapName":"${match.mapName}","status":"${match.status}","placements":[$placementsJson],"kills":[$killsJson],"correctionHistory":[$correctionsJson]}"""
        }
        val draftValuesJson = draftValues.joinToString(",") { (matchId, slotNumber, values) ->
            """{"tournamentId":"$id","matchId":"$matchId","values":[{"teamSlotNumber":$slotNumber,"placementInput":"${values.placementInput}","killsInput":"${values.killsInput}"}]}"""
        }
        return """{"tournaments":[{"id":"$id","name":"$name","date":"2026-07-24","organizerName":"Organizer","organizerContactNumber":"123","status":"$status"}],"slots":[$slotsJson],"rosters":[$rostersJson],"matches":[$matchesJson],"draftValues":[$draftValuesJson]}"""
    }

    private fun finalizedMatch(tournamentId: String, matchId: String): Match {
        val placements = listOf(MatchPlacement(1, 1), MatchPlacement(2, 2))
        val kills = listOf(MatchKill(1, 4), MatchKill(2, 3))
        return Match(
            id = matchId,
            tournamentId = tournamentId,
            matchNumber = 1,
            date = LocalDate.of(2026, 7, 24),
            mapName = "Bermuda",
            status = MatchStatus.FINALIZED,
            placements = placements,
            kills = kills,
            correctionHistory = listOf(
                MatchCorrectionRecord(
                    previousPlacements = placements,
                    previousKills = kills,
                    correctedPlacements = listOf(MatchPlacement(1, 2), MatchPlacement(2, 1)),
                    correctedKills = listOf(MatchKill(1, 5), MatchKill(2, 3)),
                ),
            ),
        )
    }

    private suspend fun seedTournamentAndMatch(
        repository: RoomTournamentRepository,
        tournamentId: String,
        matchId: String,
    ) {
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.createDraftMatch(draftMatch(tournamentId, matchId, 1))
    }

    private fun draftMatch(tournamentId: String, matchId: String, matchNumber: Int) = Match(
        id = matchId,
        tournamentId = tournamentId,
        matchNumber = matchNumber,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    private suspend fun regenerateStandings(
        repository: RoomTournamentRepository,
        tournamentId: String,
    ) = CumulativeTournamentStandingsEngine()(
        repository.observeMatchesByTournamentId(tournamentId).first(),
    )
}
