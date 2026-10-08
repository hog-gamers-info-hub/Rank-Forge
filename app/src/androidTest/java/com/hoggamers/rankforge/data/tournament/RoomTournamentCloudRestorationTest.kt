package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationPairingLobbySlotEntity
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamEntryDraftEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RestoredRosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTournamentCloudRestorationTest {
    @Test
    fun ownerBoundTournamentRestoreRejectsForeignAndNullOwnersWithoutWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-owner-guard.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            repository.create(tournament("foreign", "Foreign", TournamentStatus.DRAFT).copy(ownerUserId = "owner-b"))
            repository.create(tournament("legacy", "Legacy", TournamentStatus.DRAFT))
            val foreignRevision = database.syncRevisionDao().readByTournamentId("foreign")
            val legacyRevision = database.syncRevisionDao().readByTournamentId("legacy")

            listOf("foreign", "legacy").forEach { id ->
                assertSecurityFailure {
                    repository.restoreByOwner(
                        snapshot = tournamentSnapshot(id, "owner-a"),
                        expectedOwnerUserId = "owner-a",
                    )
                }
            }

            assertEquals("Foreign", repository.observeById("foreign").first()!!.name)
            assertEquals("Legacy", repository.observeById("legacy").first()!!.name)
            assertEquals(foreignRevision, database.syncRevisionDao().readByTournamentId("foreign"))
            assertEquals(legacyRevision, database.syncRevisionDao().readByTournamentId("legacy"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundStandardRestoreAcceptsExpectedOrderedSlots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-standard-ordered.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            val snapshot = tournamentSnapshot("standard-ordered", "owner-a")

            repository.restoreByOwner(snapshot, expectedOwnerUserId = "owner-a")

            assertEquals(
                (1..12).toList(),
                repository.observeSlotsByTournamentId("standard-ordered")
                    .first()
                    .map { it.slotNumber },
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundStandardRestoreRejectsReorderedSlotsBeforeMutation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-standard-reordered.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            val tournament = tournament("standard-reordered", "Local Standard", TournamentStatus.DRAFT)
                .copy(ownerUserId = "owner-a")
            repository.create(tournament)
            val beforeTournament = repository.observeById(tournament.id).first()
            val beforeSlots = repository.observeSlotsByTournamentId(tournament.id).first()
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournament.id)
            val snapshot = tournamentSnapshot(tournament.id, "owner-a")
            val reorderedSlots = snapshot.slots.toMutableList().apply {
                val first = this[0]
                this[0] = this[1]
                this[1] = first
            }

            assertIllegalArgument {
                repository.restoreByOwner(
                    snapshot = snapshot.copy(slots = reorderedSlots),
                    expectedOwnerUserId = "owner-a",
                )
            }

            assertEquals(beforeTournament, repository.observeById(tournament.id).first())
            assertEquals(beforeSlots, repository.observeSlotsByTournamentId(tournament.id).first())
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournament.id))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundStandardRestoreAcceptsBlankRosterDisplayName() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-standard-blank-roster-name.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            val snapshot = tournamentSnapshot("standard-blank-roster-name", "owner-a").copy(
                players = listOf(
                    RestoredRosterPlayer(
                        tournamentId = "standard-blank-roster-name",
                        slotNumber = 1,
                        rosterPosition = 1,
                        displayName = "",
                    ),
                ),
            )

            repository.restoreByOwner(snapshot, expectedOwnerUserId = "owner-a")

            assertEquals(
                listOf(""),
                repository.observeRosterByTournamentAndSlot(snapshot.tournament.id, 1)
                    .first()
                    .map { it.displayName },
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundMatchRestoreRejectsForeignAndNullParentsBeforeWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-match-owner-guard.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            repository.create(tournament("foreign-match", "Foreign", TournamentStatus.DRAFT).copy(ownerUserId = "owner-b"))
            repository.create(tournament("legacy-match", "Legacy", TournamentStatus.DRAFT))
            val foreignRevision = database.syncRevisionDao().readByTournamentId("foreign-match")
            val legacyRevision = database.syncRevisionDao().readByTournamentId("legacy-match")
            val foreignSnapshot = MatchCloudRestorationSnapshot(
                tournamentId = "foreign-match",
                matches = listOf(match("foreign-match")),
            )
            val legacySnapshot = foreignSnapshot.copy(
                tournamentId = "legacy-match",
                matches = listOf(match("legacy-match")),
            )

            assertSecurityFailure {
                repository.replaceMatchesByOwner("foreign-match", "owner-a", foreignSnapshot)
            }
            assertSecurityFailure {
                repository.replaceMatchesByOwner("legacy-match", "owner-a", legacySnapshot)
            }

            assertTrue(repository.observeMatchesByTournamentId("foreign-match").first().isEmpty())
            assertTrue(repository.observeMatchesByTournamentId("legacy-match").first().isEmpty())
            assertEquals(foreignRevision, database.syncRevisionDao().readByTournamentId("foreign-match"))
            assertEquals(legacyRevision, database.syncRevisionDao().readByTournamentId("legacy-match"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restorationRemovesStaleRosterPlayersAcrossDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-stale-roster-reopen.db"
        val expectedNames = listOf(
            "Restored Player 1",
            "Restored Player 2",
            "Restored Player 3",
            "Restored Player 4",
        )
        var database: RankForgeDatabase? = null
        var reopenedDatabase: RankForgeDatabase? = null

        suspend fun assertRestoredRoster(repository: RoomTournamentRepository) {
            val players = repository.observeRosterByTournamentAndSlot(TARGET_ID, 1).first()
            assertEquals(4, players.size)
            assertEquals(expectedNames, players.map { it.displayName })
            assertTrue(players.none { it.displayName == "Old Player 5" })
            assertTrue(players.none { it.displayName == "Old Player 6" })
        }

        context.deleteDatabase(databaseName)
        try {
            database = Room.databaseBuilder(
                context,
                RankForgeDatabase::class.java,
                databaseName,
            ).build()
            val repository = RoomTournamentRepository(database!!)
            repository.create(tournament(TARGET_ID, "Restoration Target", TournamentStatus.DRAFT))
            repository.saveTeamNames(TARGET_ID, mapOf(1 to "Initial Team"))
            repository.saveRoster(
                TARGET_ID,
                1,
                (1..6).map { playerNumber ->
                    RosterPlayer(TARGET_ID, 1, "Old Player $playerNumber")
                },
            )

            repository.restore(
                TournamentCloudRestorationSnapshot(
                    tournament = tournament(TARGET_ID, "Restored Target", TournamentStatus.DRAFT),
                    slots = TeamSlot.fixedSlotsForTournament(TARGET_ID).map { slot ->
                        if (slot.slotNumber == 1) slot.copy(teamName = "Restored Team") else slot
                    },
                    players = expectedNames.mapIndexed { index, name ->
                        RestoredRosterPlayer(TARGET_ID, 1, index + 1, name)
                    },
                ),
            )

            assertRestoredRoster(repository)

            database?.close()
            database = null
            reopenedDatabase = Room.databaseBuilder(
                context,
                RankForgeDatabase::class.java,
                databaseName,
            ).build()
            assertRestoredRoster(RoomTournamentRepository(reopenedDatabase!!))
        } finally {
            database?.close()
            reopenedDatabase?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restorationReplacesOnlyTargetTournamentRosterAndPreservesUnrelatedData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-replacement.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseName,
        ).build()
        try {
            val repository = RoomTournamentRepository(database)
            repository.create(tournament(TARGET_ID, "Old Target", TournamentStatus.CONFIRMED))
            repository.create(tournament(OTHER_ID, "Other Local", TournamentStatus.DRAFT))
            repository.saveTeamNames(TARGET_ID, mapOf(1 to "Old Team"))
            repository.saveRoster(TARGET_ID, 1, listOf(com.hoggamers.rankforge.domain.tournament.RosterPlayer(
                TARGET_ID,
                1,
                "Old Player",
            )))
            repository.confirmTournament(TARGET_ID)
            repository.createDraftMatch(
                Match(
                    id = MATCH_ID,
                    tournamentId = TARGET_ID,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                ),
            )

            repository.restore(
                TournamentCloudRestorationSnapshot(
                    tournament = tournament(TARGET_ID, "Restored Target", TournamentStatus.DRAFT),
                    slots = TeamSlot.fixedSlotsForTournament(TARGET_ID).map { slot ->
                        if (slot.slotNumber == 1) slot.copy(teamName = "Restored Team") else slot
                    },
                    players = listOf(
                        RestoredRosterPlayer(TARGET_ID, 1, 1, "Restored Player"),
                    ),
                ),
            )

            assertEquals("Restored Target", repository.observeById(TARGET_ID).first()!!.name)
            assertEquals(
                "Restored Team",
                repository.observeSlotsByTournamentId(TARGET_ID).first().first().teamName,
            )
            assertEquals(
                listOf("Restored Player"),
                repository.observeRosterByTournamentAndSlot(TARGET_ID, 1)
                    .first()
                    .map { it.displayName },
            )
            assertEquals("Other Local", repository.observeById(OTHER_ID).first()!!.name)
            assertTrue(repository.observeMatchById(MATCH_ID).first() != null)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundGroupRotationRestoreReplacesMappingsAndClearsDraft() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-group-rotation-replace.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            val tournament = groupRotationTournament("gr-replace", "owner-a")
            repository.create(tournament)
            seedGroupRotationState(database, tournament, listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))

            repository.restoreByOwner(
                snapshot = groupRotationSnapshot(
                    tournament = tournament.copy(name = "Remote Group Rotation"),
                    mappingTeamSlots = canonicalExampleMapping(),
                ),
                expectedOwnerUserId = "owner-a",
            )

            assertEquals(
                canonicalExampleMapping(),
                database.groupRotationPairingLobbySlotDao()
                    .readByTournamentId(tournament.id)
                    .map { it.teamSlotNumber },
            )
            assertTrue(database.groupRotationPairingTeamEntryDraftDao().readByTournamentId(tournament.id).isEmpty())
            assertEquals(9, database.syncRevisionDao().readByTournamentId(tournament.id)!!.localRevision)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundGroupRotationZeroMappingsClearExistingMappings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-group-rotation-zero-mappings.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            val tournament = groupRotationTournament("gr-zero", "owner-a")
            repository.create(tournament)
            seedGroupRotationState(database, tournament, listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))

            repository.restoreByOwner(
                snapshot = groupRotationSnapshot(tournament, mappingTeamSlots = emptyList()),
                expectedOwnerUserId = "owner-a",
            )

            assertTrue(database.groupRotationPairingLobbySlotDao().readByTournamentId(tournament.id).isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun ownerBoundGroupRotationMappingPersistsAcrossDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-group-rotation-reopen.db"
        context.deleteDatabase(databaseName)
        var database: RankForgeDatabase? = null
        var reopened: RankForgeDatabase? = null
        try {
            database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
            val tournament = groupRotationTournament("gr-reopen", "owner-a")
            val repository = RoomTournamentRepository(database!!)
            repository.create(tournament)
            repository.restoreByOwner(
                snapshot = groupRotationSnapshot(tournament, canonicalExampleMapping()),
                expectedOwnerUserId = "owner-a",
            )
            database!!.close()
            database = null

            reopened = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
            val lobbyThree = reopened!!.groupRotationPairingLobbySlotDao()
                .readByTournamentAndPairing(tournament.id, "A:C")
                .single { it.lobbySlotNumber == 3 }
            assertEquals(18, lobbyThree.teamSlotNumber)
        } finally {
            database?.close()
            reopened?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun invalidOwnerBoundGroupRotationRestoreLeavesAllExistingStateUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-group-rotation-invalid.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, RankForgeDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTournamentRepository(database)
            val tournament = groupRotationTournament("gr-invalid", "owner-a")
            repository.create(tournament)
            seedGroupRotationState(database, tournament, canonicalExampleMapping())
            val beforeTournament = repository.observeById(tournament.id).first()!!
            val beforeSlots = repository.observeSlotsByTournamentId(tournament.id).first()
            val beforeRoster = database.rosterPlayerDao().readByTournamentId(tournament.id)
            val beforePairings = database.tournamentGroupPairingDao().readByTournamentId(tournament.id)
            val beforeMappings = database.groupRotationPairingLobbySlotDao().readByTournamentId(tournament.id)
            val beforeDraft = database.groupRotationPairingTeamEntryDraftDao().readByTournamentId(tournament.id)
            val beforeRevision = database.syncRevisionDao().readByTournamentId(tournament.id)

            val validSnapshot = groupRotationSnapshot(
                tournament = tournament.copy(name = "Invalid Remote"),
                mappingTeamSlots = canonicalExampleMapping(),
            )
            val invalidMappings = validSnapshot.pairingLobbySlots.toMutableList().apply {
                this[1] = this[0].copy(lobbySlotNumber = 1, teamSlotNumber = 13)
            }
            assertIllegalArgument {
                repository.restoreByOwner(
                    snapshot = validSnapshot.copy(pairingLobbySlots = invalidMappings),
                    expectedOwnerUserId = "owner-a",
                )
            }

            assertEquals(beforeTournament, repository.observeById(tournament.id).first())
            assertEquals(beforeSlots, repository.observeSlotsByTournamentId(tournament.id).first())
            assertEquals(beforeRoster, database.rosterPlayerDao().readByTournamentId(tournament.id))
            assertEquals(beforePairings, database.tournamentGroupPairingDao().readByTournamentId(tournament.id))
            assertEquals(beforeMappings, database.groupRotationPairingLobbySlotDao().readByTournamentId(tournament.id))
            assertEquals(beforeDraft, database.groupRotationPairingTeamEntryDraftDao().readByTournamentId(tournament.id))
            assertEquals(beforeRevision, database.syncRevisionDao().readByTournamentId(tournament.id))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restoringNewTournamentAppendsItAfterExistingCreationOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "cloud-restoration-creation-order.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseName,
        ).build()
        try {
            val repository = RoomTournamentRepository(database)
            repository.create(tournament("test1-id", "test 1", TournamentStatus.DRAFT))
            repository.create(tournament("test2-id", "test 2", TournamentStatus.DRAFT))

            repository.restore(
                TournamentCloudRestorationSnapshot(
                    tournament = tournament("test3-id", "test 3", TournamentStatus.DRAFT),
                    slots = TeamSlot.fixedSlotsForTournament("test3-id"),
                    players = emptyList(),
                ),
            )

            assertEquals(
                listOf("test1-id", "test2-id", "test3-id"),
                repository.observeAll().first().map { it.id },
            )

            repository.restore(
                TournamentCloudRestorationSnapshot(
                    tournament = tournament("test2-id", "test 2 updated", TournamentStatus.DRAFT),
                    slots = TeamSlot.fixedSlotsForTournament("test2-id"),
                    players = emptyList(),
                ),
            )

            assertEquals(
                listOf("test1-id", "test2-id", "test3-id"),
                repository.observeAll().first().map { it.id },
            )
            assertEquals("test 2 updated", repository.observeById("test2-id").first()!!.name)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun tournament(id: String, name: String, status: TournamentStatus) = Tournament(
        id = id,
        name = name,
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = status,
    )

    private fun tournamentSnapshot(id: String, ownerUserId: String) =
        TournamentCloudRestorationSnapshot(
            tournament = tournament(id, "Restored", TournamentStatus.DRAFT).copy(ownerUserId = ownerUserId),
            slots = TeamSlot.fixedSlotsForTournament(id),
            players = emptyList(),
            cloudRevision = CloudRevision(1),
        )

    private fun groupRotationTournament(id: String, ownerUserId: String) = Tournament(
        id = id,
        name = "Local Group Rotation",
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
        ownerUserId = ownerUserId,
        format = com.hoggamers.rankforge.domain.tournament.TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(GroupPairing(TournamentGroup.A, TournamentGroup.C)),
    )

    private fun groupRotationSnapshot(
        tournament: Tournament,
        mappingTeamSlots: List<Int>,
    ) = TournamentCloudRestorationSnapshot(
        tournament = tournament,
        slots = (1..18).map { slotNumber ->
            TeamSlot.create(
                tournamentId = tournament.id,
                slotNumber = slotNumber,
                teamName = "Remote Team $slotNumber",
                group = TournamentGroup.entries[(slotNumber - 1) / 6],
            )
        },
        players = listOf(RestoredRosterPlayer(tournament.id, 1, 1, "Remote Player")),
        cloudRevision = CloudRevision(9),
        pairingLobbySlots = mappingTeamSlots.mapIndexed { index, teamSlotNumber ->
            GroupRotationPairingLobbySlotEntity(
                tournamentId = tournament.id,
                pairingKey = "A:C",
                lobbySlotNumber = index + 1,
                teamSlotNumber = teamSlotNumber,
            ).let { entity ->
                GroupRotationPairingLobbySlot(
                    tournamentId = entity.tournamentId,
                    pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
                    lobbySlotNumber = entity.lobbySlotNumber,
                    teamSlotNumber = entity.teamSlotNumber,
                )
            }
        },
    )

    private suspend fun seedGroupRotationState(
        database: RankForgeDatabase,
        tournament: Tournament,
        mappingTeamSlots: List<Int>,
    ) {
        database.groupRotationPairingLobbySlotDao().upsertAll(
            mappingTeamSlots.mapIndexed { index, teamSlotNumber ->
                GroupRotationPairingLobbySlotEntity(
                    tournamentId = tournament.id,
                    pairingKey = "A:C",
                    lobbySlotNumber = index + 1,
                    teamSlotNumber = teamSlotNumber,
                )
            },
        )
        database.rosterPlayerDao().upsertAll(
            listOf(
                com.hoggamers.rankforge.data.local.RosterPlayerEntity(
                    tournamentId = tournament.id,
                    slotNumber = 1,
                    rosterPosition = 1,
                    displayName = "Old Player",
                ),
            ),
        )
        database.groupRotationPairingTeamEntryDraftDao().upsertAll(
            listOf(
                GroupRotationPairingTeamEntryDraftEntity(
                    tournamentId = tournament.id,
                    pairingKey = "A:C",
                    lobbySlotNumber = 1,
                    rawTeamName = "Old Draft",
                ),
            ),
        )
    }

    private fun canonicalExampleMapping() = listOf(14, 3, 18, 7, 1, 2, 4, 5, 6, 13, 15, 16)

    private fun match(tournamentId: String) = Match(
        id = "match-$tournamentId",
        tournamentId = tournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    private suspend fun assertSecurityFailure(block: suspend () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: SecurityException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private companion object {
        const val TARGET_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_ID = "22222222-2222-2222-2222-222222222222"
        const val MATCH_ID = "33333333-3333-3333-3333-333333333333"
    }
}
