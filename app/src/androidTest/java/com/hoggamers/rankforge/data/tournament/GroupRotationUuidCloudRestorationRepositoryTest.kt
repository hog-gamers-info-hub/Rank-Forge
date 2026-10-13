package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchParticipantResultEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchTeamAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.SyncRevisionEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentGroupPairingEntity
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidLocalRestoreResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.time.LocalDate
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
    fun restoresTwoFinalizedUuidMatchesWithExactResultsIntoEmptyDatabase() = runBlocking {
        repository = RoomTournamentRepository(database)
        repository.observeAll().first()

        val restored = repository.restoreGroupRotationUuidSnapshot(twoFinalizedMatchSnapshot(), OWNER)

        assertEquals(GroupRotationUuidLocalRestoreResult.Restored, restored)
        assertEquals(19, database.syncRevisionDao().readByTournamentId(T)?.localRevision)
        assertEquals(18, database.teamSlotDao().readByTournamentId(T).size)
        assertEquals(12, database.groupRotationTeamIdentityDao().readByTournamentId(T).size)
        assertEquals(12, database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(T).size)

        val matches = database.matchDao().readForTournamentId(T)
        assertEquals(listOf(MATCH_1, MATCH_2), matches.map { it.id })
        assertEquals(listOf(MatchStatus.FINALIZED.name, MatchStatus.FINALIZED.name), matches.map { it.status })

        val expectedAssignments = (1..12).map { lobbySlot ->
            GroupRotationMatchTeamAssignmentEntity(
                matchId = MATCH_1,
                tournamentId = T,
                lobbySlotNumber = lobbySlot,
                teamUUID = teamUuid(lobbySlot),
            )
        }
        assertEquals(
            expectedAssignments,
            database.groupRotationMatchTeamAssignmentDao().readByMatchId(MATCH_1),
        )
        assertEquals(
            expectedAssignments.map { it.copy(matchId = MATCH_2) },
            database.groupRotationMatchTeamAssignmentDao().readByMatchId(MATCH_2),
        )

        val expectedMatch1Results = expectedUuidResults(MATCH_1, 79)
        val expectedMatch2Results = expectedUuidResults(MATCH_2, 107)
        assertEquals(
            expectedMatch1Results,
            database.groupRotationMatchParticipantResultDao().readByMatchId(MATCH_1),
        )
        assertEquals(
            expectedMatch2Results,
            database.groupRotationMatchParticipantResultDao().readByMatchId(MATCH_2),
        )
        assertEquals(
            pairing("A:B"),
            database.groupRotationMatchIdentityHeaderDao().readByMatchId(MATCH_1)?.pairingKey
                ?.let(::pairing),
        )
        assertEquals(
            pairing("A:B"),
            database.groupRotationMatchIdentityHeaderDao().readByMatchId(MATCH_2)?.pairingKey
                ?.let(::pairing),
        )
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

    private fun twoFinalizedMatchSnapshot() = GroupRotationUuidCloudRestorationSnapshot(
        parent = TournamentCloudRestorationSnapshot(
            tournament = tournament().copy(selectedGroupPairings = listOf(pairing("A:B"))),
            slots = (1..18).map { slot -> TeamSlot.create(T, slot) },
            players = emptyList(),
            cloudRevision = CloudRevision(19),
            pairingLobbySlots = emptyList(),
        ),
        identities = (1..12).map { slot ->
            GroupRotationTeamIdentity(
                tournamentId = T,
                teamUUID = teamUuid(slot),
                displayName = "Team $slot",
                normalizedName = "team $slot",
            )
        },
        assignments = (1..12).map { lobbySlot ->
            GroupRotationPairingAssignment(T, pairing("A:B"), lobbySlot, teamUuid(lobbySlot))
        },
        matches = listOf(
            finalizedMatch(MATCH_1),
            finalizedMatch(MATCH_2),
        ),
        uuidMatches = listOf(
            finalizedUuidMatch(MATCH_1, 79),
            finalizedUuidMatch(MATCH_2, 107),
        ),
        cloudRevision = CloudRevision(19),
    )

    private fun finalizedMatch(id: String) = Match(
        id = id,
        tournamentId = T,
        matchNumber = if (id == MATCH_1) 1 else 2,
        date = LocalDate.of(2026, 10, 12),
        mapName = "Erangel",
        status = MatchStatus.FINALIZED,
        groupPairing = pairing("A:B"),
    )

    private fun finalizedUuidMatch(
        matchId: String,
        totalKills: Int,
    ) = com.hoggamers.rankforge.domain.tournament.GroupRotationUuidMatchRestorationSnapshot(
        match = finalizedMatch(matchId),
        identity = GroupRotationMatchIdentitySnapshot(
            tournamentId = T,
            pairing = pairing("A:B"),
            teams = (1..12).map { lobbySlot ->
                GroupRotationMatchTeamIdentity(
                    lobbySlotNumber = lobbySlot,
                    teamUUID = teamUuid(lobbySlot),
                    displayName = "Team $lobbySlot",
                )
            },
        ),
        participantResults = expectedDomainResults(matchId, totalKills),
    )

    private fun expectedDomainResults(matchId: String, totalKills: Int) =
        (1..12).map { placement ->
            GroupRotationMatchParticipantResult(
                teamUUID = teamUuid(placement),
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = placement,
                kills = if (totalKills == 79) {
                    if (placement == 12) 13 else placement
                } else {
                    if (placement == 12) 30 else placement + 1
                },
                pointAdjustment = 0,
            )
        }

    private fun expectedUuidResults(matchId: String, totalKills: Int) =
        expectedDomainResults(matchId, totalKills).map { result ->
            GroupRotationMatchParticipantResultEntity(
                matchId = matchId,
                tournamentId = T,
                teamUUID = result.teamUUID,
                participationStatus = result.participationStatus.name,
                placement = result.placement,
                kills = result.kills,
                pointAdjustment = result.pointAdjustment,
            )
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

    private fun teamUuid(slot: Int): String =
        "00000000-0000-0000-0000-${slot.toString().padStart(12, '0')}"

    private companion object {
        const val T = "00000000-0000-0000-0000-000000000001"
        const val OWNER = "00000000-0000-0000-0000-000000000099"
        const val MATCH = "00000000-0000-0000-0000-000000000101"
        const val MATCH_1 = "018b05a8-5fea-46e9-a581-e77aa603bdd4"
        const val MATCH_2 = "cfd65c4b-a79d-485a-8ea0-02f0e09f018f"
        const val U1 = "00000000-0000-0000-0000-0000000000a1"
        const val U2 = "00000000-0000-0000-0000-0000000000b2"
    }
}
