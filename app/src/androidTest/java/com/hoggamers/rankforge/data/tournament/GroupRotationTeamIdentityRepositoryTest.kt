package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.cloud.TournamentCloudIdentity
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.GroupRotationPairingLobbySlotEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.TeamSlotEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentGroupPairingEntity
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityWriteResult
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationTeamIdentityRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var databaseFile: File
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomGroupRotationTeamIdentityRepository

    @Before
    fun setUp() = runBlocking {
        databaseFile = File(context.cacheDir, "group-rotation-identities-${UUID.randomUUID()}.db")
        database = openDatabase()
        database.tournamentDao().upsert(
            TournamentEntity(
                id = TOURNAMENT_ID,
                name = "Rotation Cup",
                stageName = "Stage",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT.name,
                format = TournamentFormat.GROUP_ROTATION.name,
                groupCount = 3,
            ),
        )
        database.teamSlotDao().upsertAll(
            (1..18).map { slotNumber ->
                TeamSlotEntity(TOURNAMENT_ID, slotNumber, "")
            },
        )
        database.tournamentGroupPairingDao().upsertAll(
            listOf(
                TournamentGroupPairingEntity(TOURNAMENT_ID, "A:B", "A", "B"),
                TournamentGroupPairingEntity(TOURNAMENT_ID, "A:C", "A", "C"),
                TournamentGroupPairingEntity(TOURNAMENT_ID, "B:C", "B", "C"),
            ),
        )
        repository = RoomGroupRotationTeamIdentityRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-wal").delete()
    }

    @Test
    fun zeroOccupiedAndBlankSlotsCreateNoIdentityRows() = runBlocking {
        val result = repository.savePairing(tournament(), pairing("A:B"), emptyList())

        assertTrue(result is GroupRotationTeamIdentityWriteResult.Saved)
        assertTrue(database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).isEmpty())
        assertTrue(
            database.groupRotationPairingTeamIdentityAssignmentDao()
                .readByTournamentId(TOURNAMENT_ID)
                .isEmpty(),
        )
    }

    @Test
    fun independentUuidAndSparseAssignmentsSurviveDatabaseReopen() = runBlocking {
        repository.savePairing(
            tournament(),
            pairing("A:B"),
            entries(1 to "Alpha", 5 to "Bravo"),
        )
        val before = loaded()
        assertEquals(2, before.teams.size)
        assertEquals(listOf(1, 5), before.assignments.map { it.lobbySlotNumber })

        database.close()
        database = openDatabase()
        repository = RoomGroupRotationTeamIdentityRepository(database)

        val after = loaded()
        assertEquals(before, after)
        assertTrue(after.teams.map { it.teamUUID }.distinct().size == 2)
    }

    @Test
    fun sameUuidReusesAcrossPairingsAndSavingOnePairingPreservesTheOther() = runBlocking {
        repository.savePairing(tournament(), pairing("A:B"), entries(1 to "Alpha"))
        val first = loaded()
        val alphaUuid = first.teams.single().teamUUID

        repository.savePairing(tournament(), pairing("A:C"), entries(7 to "alpha"))
        val second = loaded()
        assertEquals(alphaUuid, second.assignments.single { it.pairingKey == pairing("A:C") }.teamUUID)

        repository.savePairing(tournament(), pairing("A:B"), entries(1 to "Alpha Renamed"))
        val third = loaded()
        assertEquals(alphaUuid, third.teams.single().teamUUID)
        assertEquals("Alpha Renamed", third.teams.single().displayName)
        assertEquals(alphaUuid, third.assignments.single { it.pairingKey == pairing("A:C") }.teamUUID)
    }

    @Test
    fun explicitClearsRemoveOnlyThatPairingAssignmentsAndPreserveIdentitiesAndHistory() = runBlocking {
        repository.savePairing(tournament(), pairing("A:B"), entries(1 to "Alpha", 2 to "Beta"))
        repository.savePairing(
            tournament(),
            pairing("A:C"),
            entries(
                1 to "alpha",
                2 to "beta",
                3 to "Team 3",
                4 to "Team 4",
                5 to "Team 5",
                6 to "Team 6",
            ),
        )
        val before = loaded()
        val match = MatchEntity(
            id = "history-match",
            tournamentId = TOURNAMENT_ID,
            matchNumber = 1,
            date = "2026-10-01",
            mapName = "Bermuda",
            status = "FINALIZED",
            groupPairingKey = "A:B",
        )
        database.matchDao().upsert(match)

        val oneCleared = repository.savePairing(
            tournament(),
            pairing("A:C"),
            entries(1 to ""),
        )
        assertTrue(oneCleared is GroupRotationTeamIdentityWriteResult.Saved)
        val afterOne = loaded()
        val afterOnePairingAssignments = afterOne.assignments
            .filter { it.pairingKey == pairing("A:C") }
        assertEquals(
            listOf(2, 3, 4, 5, 6),
            afterOnePairingAssignments.filterNot { it.nameCleared }.map { it.lobbySlotNumber },
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), afterOnePairingAssignments.map { it.lobbySlotNumber })
        assertTrue(afterOnePairingAssignments.single { it.lobbySlotNumber == 1 }.nameCleared)
        assertEquals(
            before.teams.map { it.teamUUID },
            afterOne.teams.map { it.teamUUID },
        )
        assertEquals(
            before.assignments.filter { it.pairingKey == pairing("A:B") },
            afterOne.assignments.filter { it.pairingKey == pairing("A:B") },
        )

        val allCleared = repository.savePairing(
            tournament(),
            pairing("A:C"),
            entries(2 to "", 3 to "", 4 to "", 5 to "", 6 to ""),
        )
        assertTrue(allCleared is GroupRotationTeamIdentityWriteResult.Saved)
        val afterAll = loaded()
        val afterAllPairingAssignments = afterAll.assignments
            .filter { it.pairingKey == pairing("A:C") }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), afterAllPairingAssignments.map { it.lobbySlotNumber })
        assertTrue(afterAllPairingAssignments.all { it.nameCleared })
        assertEquals(before.teams.map { it.teamUUID }, afterAll.teams.map { it.teamUUID })
        val pairingAbTeamUuids = before.assignments
            .filter { it.pairingKey == pairing("A:B") }
            .map { it.teamUUID }
            .toSet()
        assertEquals(
            before.teams.filter { it.teamUUID in pairingAbTeamUuids },
            afterAll.teams.filter { it.teamUUID in pairingAbTeamUuids },
        )
        val pairingAcOnlyTeamUuids = before.assignments
            .filter { it.pairingKey == pairing("A:C") }
            .map { it.teamUUID }
            .toSet() - pairingAbTeamUuids
        assertTrue(
            afterAll.teams
                .filter { it.teamUUID in pairingAcOnlyTeamUuids }
                .all { it.displayName.isBlank() },
        )
        assertEquals(
            before.assignments.filter { it.pairingKey == pairing("A:B") },
            afterAll.assignments.filter { it.pairingKey == pairing("A:B") },
        )
        assertEquals(match, database.matchDao().readForTournamentId(TOURNAMENT_ID).single())
    }

    @Test
    fun pairingAssignmentNameClearedRoundTripsWithoutAffectingOtherPairings() = runBlocking {
        val alphaUuid = UUID(0, 101).toString()
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(GroupRotationTeamIdentityEntity(TOURNAMENT_ID, alphaUuid, "Alpha", "alpha")),
        )
        database.groupRotationPairingTeamIdentityAssignmentDao().upsertAll(
            listOf(
                GroupRotationPairingTeamIdentityAssignmentEntity(
                    tournamentId = TOURNAMENT_ID,
                    pairingKey = "A:B",
                    lobbySlotNumber = 1,
                    teamUUID = alphaUuid,
                    nameCleared = false,
                ),
                GroupRotationPairingTeamIdentityAssignmentEntity(
                    tournamentId = TOURNAMENT_ID,
                    pairingKey = "A:C",
                    lobbySlotNumber = 7,
                    teamUUID = alphaUuid,
                    nameCleared = true,
                ),
            ),
        )

        val assignments = database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(TOURNAMENT_ID)
        assertEquals(false, assignments.single { it.pairingKey == "A:B" }.nameCleared)
        assertEquals(true, assignments.single { it.pairingKey == "A:C" }.nameCleared)
        assertEquals(alphaUuid, assignments.single { it.pairingKey == "A:B" }.teamUUID)
        assertEquals(alphaUuid, assignments.single { it.pairingKey == "A:C" }.teamUUID)
        assertEquals("Alpha", database.groupRotationTeamIdentityDao()
            .readByTournamentId(TOURNAMENT_ID).single().displayName)
    }

    @Test
    fun duplicateNamesAndUnknownUuidAreRejectedWithoutWrites() = runBlocking {
        val duplicate = repository.savePairing(
            tournament(),
            pairing("A:B"),
            entries(1 to "Alpha", 2 to " alpha "),
        )
        assertInvalid(duplicate)
        assertTrue(database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).isEmpty())

        val unknownUuid = repository.savePairing(
            tournament(),
            pairing("A:B"),
            listOf(entry(1, "Alpha", UUID(0, 7).toString())),
        )
        assertInvalid(unknownUuid)
        assertTrue(database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).isEmpty())
    }

    @Test
    fun capacityExhaustionIsRejectedAfterEighteenRegisteredTeams() = runBlocking {
        repository.savePairing(
            tournament(),
            pairing("A:B"),
            (1..12).map { lobby -> entry(lobby, "Team $lobby") },
        )
        repository.savePairing(
            tournament(),
            pairing("A:C"),
            (1..6).map { lobby -> entry(lobby, "Team ${lobby + 12}") },
        )
        assertEquals(18, database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).size)

        val rejected = repository.savePairing(
            tournament(),
            pairing("A:C"),
            listOf(entry(7, "Team 19")),
        )

        assertInvalid(rejected)
        assertEquals(18, database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).size)
    }

    @Test
    fun legacyOccupiedSlotsBackfillCloudIdentityAndRemainIdempotent() = runBlocking {
        database.teamSlotDao().upsertAll(
            listOf(TeamSlotEntity(TOURNAMENT_ID, 1, "Legacy Alpha")),
        )

        val first = loaded()
        val expectedUuid = TournamentCloudIdentity.teamSlotId(UUID.fromString(TOURNAMENT_ID), 1)
        assertEquals(expectedUuid, first.teams.single().teamUUID)
        assertEquals("Legacy Alpha", first.teams.single().displayName)
        assertTrue(
            database.teamSlotDao().readByTournamentId(TOURNAMENT_ID)
                .any { it.slotNumber == 2 && it.teamName.isBlank() },
        )

        val renamed = repository.renameTeam(tournament(), expectedUuid, "Saved Legacy")
        assertTrue(renamed is GroupRotationTeamIdentityWriteResult.Saved)
        val second = loaded()
        assertEquals(expectedUuid, second.teams.single().teamUUID)
        assertEquals("Saved Legacy", second.teams.single().displayName)
        assertEquals(1, database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).size)
    }

    @Test
    fun invalidLegacySnapshotDoesNotPersistPartialIdentityBackfill() = runBlocking {
        database.teamSlotDao().upsertAll(
            listOf(TeamSlotEntity(TOURNAMENT_ID, 1, "Legacy Alpha")),
        )
        database.groupRotationPairingLobbySlotDao().insertAll(
            listOf(GroupRotationPairingLobbySlotEntity(TOURNAMENT_ID, "A:B", 0, 1)),
        )

        val result = repository.read(tournament())

        assertTrue(result is GroupRotationTeamIdentityReadResult.Invalid)
        assertTrue(database.groupRotationTeamIdentityDao().readByTournamentId(TOURNAMENT_ID).isEmpty())
        assertTrue(
            database.groupRotationPairingTeamIdentityAssignmentDao()
                .readByTournamentId(TOURNAMENT_ID)
                .isEmpty(),
        )
    }

    @Test
    fun pairingHistoryProtectsAssignmentChangesButAllowsUuidPreservingRename() = runBlocking {
        val initial = repository.savePairing(tournament(), pairing("A:B"), entries(1 to "Alpha"))
        assertTrue(initial is GroupRotationTeamIdentityWriteResult.Saved)
        val before = loaded()
        val match = MatchEntity(
            id = "history-match",
            tournamentId = TOURNAMENT_ID,
            matchNumber = 1,
            date = "2026-10-01",
            mapName = "Bermuda",
            status = "FINALIZED",
            groupPairingKey = "A:B",
        )
        database.matchDao().upsert(match)

        val changed = repository.savePairing(
            tournament(),
            pairing("A:B"),
            entries(1 to "Alpha", 2 to "Beta"),
        )
        assertTrue(changed is GroupRotationTeamIdentityWriteResult.ProtectedHistory)
        assertEquals(before, loaded())

        val renamed = repository.renameTeam(tournament(), before.teams.single().teamUUID, "Alpha Renamed")
        assertTrue(renamed is GroupRotationTeamIdentityWriteResult.Saved)
        val afterRename = loaded()
        assertEquals(before.assignments, afterRename.assignments)
        assertEquals(before.teams.single().teamUUID, afterRename.teams.single().teamUUID)
        assertEquals("Alpha Renamed", afterRename.teams.single().displayName)
        assertEquals(match, database.matchDao().readForTournamentId(TOURNAMENT_ID).single())
    }

    @Test
    fun conflictingForeignStoredAssignmentIsRejectedByRoomConstraint() = runBlocking {
        val foreignTournamentId = UUID.randomUUID().toString()
        database.tournamentDao().upsert(
            TournamentEntity(
                id = foreignTournamentId,
                name = "Other",
                stageName = "Stage",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT.name,
                format = TournamentFormat.GROUP_ROTATION.name,
                groupCount = 3,
            ),
        )
        database.tournamentGroupPairingDao().upsertAll(
            listOf(TournamentGroupPairingEntity(foreignTournamentId, "A:B", "A", "B")),
        )
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(
                GroupRotationTeamIdentityEntity(
                    foreignTournamentId,
                    UUID.randomUUID().toString(),
                    "Foreign",
                    "foreign",
                ),
            ),
        )

        val foreignUuid = database.groupRotationTeamIdentityDao()
            .readByTournamentId(foreignTournamentId)
            .single()
            .teamUUID
        var rejected = false
        try {
            database.groupRotationPairingTeamIdentityAssignmentDao().upsertAll(
                listOf(
                    com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity(
                        TOURNAMENT_ID,
                        "A:B",
                        1,
                        foreignUuid,
                    ),
                ),
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private suspend fun loaded(): com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot =
        (repository.read(tournament()) as GroupRotationTeamIdentityReadResult.Loaded).snapshot

    private fun assertInvalid(result: GroupRotationTeamIdentityWriteResult) {
        assertTrue(result is GroupRotationTeamIdentityWriteResult.Invalid)
    }

    private fun openDatabase(): RankForgeDatabase = Room.databaseBuilder(
        context,
        RankForgeDatabase::class.java,
        databaseFile.absolutePath,
    ).allowMainThreadQueries().build()

    private fun tournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation Cup",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(pairing("A:B"), pairing("A:C"), pairing("B:C")),
    )

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private fun entries(vararg values: Pair<Int, String>) = values.map { (slot, name) ->
        entry(slot, name)
    }

    private fun entry(slot: Int, name: String, teamUUID: String? = null) =
        GroupRotationPairingTeamIdentityEntry(slot, name, teamUUID)

    private companion object {
        const val TOURNAMENT_ID = "00000000-0000-0000-0000-000000000001"
    }
}
