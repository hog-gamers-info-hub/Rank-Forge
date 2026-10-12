package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.SyncRevisionEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentGroupPairingEntity
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidLocalRestoreResult
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationUuidCloudRestorationRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var databaseFile: File
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomTournamentRepository

    @Before
    fun setUp() {
        databaseFile = File(context.cacheDir, "group-rotation-uuid-restore-${System.nanoTime()}.db")
        database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseFile.path,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-wal").delete()
    }

    @Test
    fun restoresExactUuidPoolAndSparsePairingAssignments() = runBlocking {
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        val result = repository.restoreGroupRotationUuidSnapshot(snapshot(), OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, result)
        assertEquals(
            listOf(GroupRotationTeamIdentityEntity(T, U1, "Phoenix", "phoenix")),
            database.groupRotationTeamIdentityDao().readByTournamentId(T),
        )
        assertEquals(
            listOf(GroupRotationPairingTeamIdentityAssignmentEntity(T, "A:B", 5, U1)),
            database.groupRotationPairingTeamIdentityAssignmentDao().readByTournamentId(T),
        )
        assertEquals(18, database.teamSlotDao().readByTournamentId(T).size)
    }

    @Test
    fun finalizedLocalMatchBlocksUuidReplacementWithoutMutation() = runBlocking {
        seedExistingTournament()
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        database.matchDao().upsert(
            MatchEntity(
                id = MATCH,
                tournamentId = T,
                matchNumber = 1,
                date = "2026-10-12",
                mapName = "Erangel",
                status = "FINALIZED",
                groupPairingKey = "A:B",
            ),
        )
        val before = database.groupRotationTeamIdentityDao().readByTournamentId(T)

        val result = repository.restoreGroupRotationUuidSnapshot(snapshot(), OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Conflict, result)
        assertEquals(before, database.groupRotationTeamIdentityDao().readByTournamentId(T))
        assertEquals("FINALIZED", database.matchDao().readById(MATCH)?.status)
        assertEquals(7, database.syncRevisionDao().readByTournamentId(T)?.localRevision)
    }

    @Test
    fun invalidIdentityReplacementLeavesAllLocalWritesUnchanged() = runBlocking {
        seedExistingTournament()
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()
        val beforeAssignments = database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(T)

        val invalid = snapshot(
            identities = listOf(
                GroupRotationTeamIdentity(T, U1, "Phoenix", "same"),
                GroupRotationTeamIdentity(T, U2, "Other", "same"),
            ),
        )
        val result = repository.restoreGroupRotationUuidSnapshot(invalid, OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Invalid, result)
        assertEquals(
            listOf(GroupRotationTeamIdentityEntity(T, U1, "Original", "original")),
            database.groupRotationTeamIdentityDao().readByTournamentId(T),
        )
        assertEquals(beforeAssignments, database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(T))
        assertEquals(7, database.syncRevisionDao().readByTournamentId(T)?.localRevision)
    }

    private suspend fun seedExistingTournament() {
        database.withTransaction {
            database.tournamentDao().upsert(tournamentEntity())
            database.tournamentGroupPairingDao().upsertAll(
                listOf(TournamentGroupPairingEntity(T, "A:B", "A", "B")),
            )
            database.groupRotationTeamIdentityDao().upsertAll(
                listOf(GroupRotationTeamIdentityEntity(T, U1, "Original", "original")),
            )
            database.groupRotationPairingTeamIdentityAssignmentDao().upsertAll(
                listOf(GroupRotationPairingTeamIdentityAssignmentEntity(T, "A:B", 5, U1)),
            )
            database.syncRevisionDao().upsert(SyncRevisionEntity(T, 7, 7))
        }
    }

    private fun snapshot(
        identities: List<GroupRotationTeamIdentity> =
            listOf(GroupRotationTeamIdentity(T, U1, "Phoenix", "phoenix")),
    ) = GroupRotationUuidCloudRestorationSnapshot(
        parent = TournamentCloudRestorationSnapshot(
            tournament = tournament(),
            slots = (1..18).map { slot -> TeamSlot.create(T, slot) },
            players = emptyList(),
            cloudRevision = CloudRevision(7),
            pairingLobbySlots = emptyList(),
        ),
        identities = identities,
        assignments = listOf(GroupRotationPairingAssignment(T, pairing("A:B"), 5, U1)),
        matches = emptyList(),
        uuidMatches = emptyList(),
        cloudRevision = CloudRevision(7),
    )

    private fun tournament() = Tournament(
        id = T,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(pairing("A:B"), pairing("A:C")),
    )

    private fun tournamentEntity() = TournamentEntity(
        id = T,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "",
        status = TournamentStatus.DRAFT.name,
        ownerUserId = OWNER,
        format = TournamentFormat.GROUP_ROTATION.name,
        groupCount = 3,
    )

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private companion object {
        const val T = "00000000-0000-0000-0000-000000000001"
        const val OWNER = "00000000-0000-0000-0000-000000000099"
        const val MATCH = "00000000-0000-0000-0000-000000000101"
        const val U1 = "00000000-0000-0000-0000-0000000000a1"
        const val U2 = "00000000-0000-0000-0000-0000000000b2"
    }
}
