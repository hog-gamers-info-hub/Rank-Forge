package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentGroupPairingEntity
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationMatchIdentityPersistenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var databaseFile: File
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomGroupRotationMatchIdentityRepository

    @Before
    fun setUp() = runBlocking {
        databaseFile = File(context.cacheDir, "group-rotation-match-identities-${System.nanoTime()}.db")
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
        database.tournamentGroupPairingDao().upsertAll(
            listOf(
                TournamentGroupPairingEntity(TOURNAMENT_ID, "A:B", "A", "B"),
                TournamentGroupPairingEntity(TOURNAMENT_ID, "A:C", "A", "C"),
                TournamentGroupPairingEntity(TOURNAMENT_ID, "B:C", "B", "C"),
            ),
        )
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(identity(ALPHA_UUID, "Alpha"), identity(BETA_UUID, "Beta")),
        )
        database.matchDao().upsert(match(MATCH_ONE, "A:B"))
        repository = RoomGroupRotationMatchIdentityRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-wal").delete()
    }

    @Test
    fun sparseUuidIdentityAndParticipantResultsSurviveDatabaseReopen() = runBlocking {
        val saved = repository.save(
            tournament = tournament(),
            matchId = MATCH_ONE,
            snapshot = snapshot(pairing("A:B"), 3 to alpha(), 11 to beta()),
            participantResults = listOf(
                GroupRotationMatchParticipantResult(
                    teamUUID = ALPHA_UUID,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = null,
                    kills = 4,
                ),
                GroupRotationMatchParticipantResult(
                    teamUUID = BETA_UUID,
                    participationStatus = MatchParticipationStatus.NO_SHOW,
                    placement = null,
                    kills = 0,
                ),
            ),
        )

        assertTrue(saved is GroupRotationMatchPersistenceResult.Saved)
        val before = loaded(MATCH_ONE)
        assertEquals(listOf(3, 11), before.identity.teams.map { it.lobbySlotNumber })
        assertEquals(listOf(ALPHA_UUID, BETA_UUID), before.identity.teams.map { it.teamUUID })
        assertEquals(4, before.participantResults.first { it.teamUUID == ALPHA_UUID }.kills)

        database.close()
        database = openDatabase()
        repository = RoomGroupRotationMatchIdentityRepository(database)

        assertEquals(before, loaded(MATCH_ONE))
    }

    @Test
    fun sameUuidCanBeSavedAtDifferentLobbyPositionsAcrossPairings() = runBlocking {
        database.matchDao().upsert(match(MATCH_TWO, "A:C"))

        assertTrue(
            repository.save(
                tournament(),
                MATCH_ONE,
                snapshot(pairing("A:B"), 2 to alpha()),
            ) is GroupRotationMatchPersistenceResult.Saved,
        )
        assertTrue(
            repository.save(
                tournament(),
                MATCH_TWO,
                snapshot(pairing("A:C"), 9 to alpha()),
            ) is GroupRotationMatchPersistenceResult.Saved,
        )

        assertEquals(2, loaded(MATCH_ONE).identity.teams.single().lobbySlotNumber)
        assertEquals(9, loaded(MATCH_TWO).identity.teams.single().lobbySlotNumber)
        assertEquals(ALPHA_UUID, loaded(MATCH_TWO).identity.teams.single().teamUUID)
    }

    @Test
    fun renamedRegistryTeamUsesCurrentNameWithoutChangingSavedUuid() = runBlocking {
        repository.save(tournament(), MATCH_ONE, snapshot(pairing("A:B"), 4 to alpha()))
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(identity(ALPHA_UUID, "Alpha Renamed")),
        )

        val restored = loaded(MATCH_ONE).identity.teams.single()
        assertEquals(ALPHA_UUID, restored.teamUUID)
        assertEquals("Alpha Renamed", restored.displayName)
    }

    @Test
    fun assignmentReplacementIsRejectedAndOriginalSnapshotRemains() = runBlocking {
        repository.save(tournament(), MATCH_ONE, snapshot(pairing("A:B"), 3 to alpha()))
        val rejected = repository.save(tournament(), MATCH_ONE, snapshot(pairing("A:B"), 3 to beta()))

        assertTrue(rejected is GroupRotationMatchPersistenceResult.Invalid)
        assertEquals(ALPHA_UUID, loaded(MATCH_ONE).identity.teams.single().teamUUID)
        assertEquals(3, loaded(MATCH_ONE).identity.teams.single().lobbySlotNumber)
        assertEquals(1, database.groupRotationMatchTeamAssignmentDao().readByMatchId(MATCH_ONE).size)
    }

    @Test
    fun unknownAndCrossTournamentUuidsAreRejected() = runBlocking {
        val unknown = repository.save(
            tournament(),
            MATCH_ONE,
            snapshot(pairing("A:B"), 1 to team("missing-uuid", "Missing")),
        )
        assertTrue(unknown is GroupRotationMatchPersistenceResult.Invalid)

        val otherTournamentId = "00000000-0000-0000-0000-000000000002"
        database.tournamentDao().upsert(
            TournamentEntity(
                id = otherTournamentId,
                name = "Other",
                stageName = "Stage",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT.name,
                format = TournamentFormat.GROUP_ROTATION.name,
                groupCount = 3,
            ),
        )
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(
                GroupRotationTeamIdentityEntity(
                    tournamentId = otherTournamentId,
                    teamUUID = FOREIGN_UUID,
                    displayName = "Foreign",
                    normalizedName = normalizer.normalize("Foreign"),
                ),
            ),
        )
        val crossTournament = repository.save(
            tournament(),
            MATCH_ONE,
            snapshot(pairing("A:B"), 1 to team(FOREIGN_UUID, "Foreign")),
        )
        assertTrue(crossTournament is GroupRotationMatchPersistenceResult.Invalid)
    }

    @Test
    fun emptyPairingSnapshotIsPersistedAsEmpty() = runBlocking {
        val saved = repository.save(
            tournament(),
            MATCH_ONE,
            snapshot(pairing("A:B")),
        )

        assertTrue(saved is GroupRotationMatchPersistenceResult.Saved)
        assertTrue(loaded(MATCH_ONE).identity.teams.isEmpty())
        assertTrue(loaded(MATCH_ONE).participantResults.isEmpty())
    }

    @Test
    fun invalidParticipantUuidAndDuplicatePlacementAreRejectedAtomically() = runBlocking {
        val initial = repository.save(tournament(), MATCH_ONE, snapshot(pairing("A:B"), 1 to alpha(), 2 to beta()))
        assertTrue(initial is GroupRotationMatchPersistenceResult.Saved)
        val rejected = repository.save(
            tournament(),
            MATCH_ONE,
            snapshot(pairing("A:B"), 1 to alpha(), 2 to beta()),
            participantResults = listOf(
                GroupRotationMatchParticipantResult(ALPHA_UUID, MatchParticipationStatus.PARTICIPATED, 1, 1),
                GroupRotationMatchParticipantResult(BETA_UUID, MatchParticipationStatus.PARTICIPATED, 1, 2),
                GroupRotationMatchParticipantResult("absent", MatchParticipationStatus.PARTICIPATED, 2, 1),
            ),
        )

        assertTrue(rejected is GroupRotationMatchPersistenceResult.Invalid)
        assertTrue(loaded(MATCH_ONE).participantResults.isEmpty())
    }

    private suspend fun loaded(matchId: String): GroupRotationMatchPersistenceSnapshotHolder =
        when (val result = repository.read(tournament(), matchId)) {
            is GroupRotationMatchPersistenceResult.Loaded -> GroupRotationMatchPersistenceSnapshotHolder(
                result.snapshot.identity,
                result.snapshot.participantResults,
            )
            is GroupRotationMatchPersistenceResult.Invalid -> error("Expected loaded snapshot: ${result.issues}")
            is GroupRotationMatchPersistenceResult.Saved -> error("Expected loaded snapshot")
        }

    private fun openDatabase(): RankForgeDatabase = Room.databaseBuilder(
        context,
        RankForgeDatabase::class.java,
        databaseFile.path,
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

    private fun match(id: String, pairingKey: String) = MatchEntity(
        id = id,
        tournamentId = TOURNAMENT_ID,
        matchNumber = if (id == MATCH_ONE) 1 else 2,
        date = "2026-10-01",
        mapName = "Bermuda",
        status = MatchStatus.DRAFT.name,
        groupPairingKey = pairingKey,
    )

    private fun snapshot(
        pairing: GroupPairing,
        vararg teams: Pair<Int, GroupRotationMatchTeamIdentity>,
    ) = GroupRotationMatchIdentitySnapshot(
        tournamentId = TOURNAMENT_ID,
        pairing = pairing,
        teams = teams.map { (lobbySlot, team) -> team.copy(lobbySlotNumber = lobbySlot) },
    )

    private fun alpha() = team(ALPHA_UUID, "Alpha")

    private fun beta() = team(BETA_UUID, "Beta")

    private fun team(teamUUID: String, displayName: String) = GroupRotationMatchTeamIdentity(
        lobbySlotNumber = 1,
        teamUUID = teamUUID,
        displayName = displayName,
    )

    private fun identity(teamUUID: String, displayName: String) = GroupRotationTeamIdentityEntity(
        tournamentId = TOURNAMENT_ID,
        teamUUID = teamUUID,
        displayName = displayName,
        normalizedName = normalizer.normalize(displayName),
    )

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private data class GroupRotationMatchPersistenceSnapshotHolder(
        val identity: GroupRotationMatchIdentitySnapshot,
        val participantResults: List<GroupRotationMatchParticipantResult>,
    )

    private companion object {
        const val TOURNAMENT_ID = "00000000-0000-0000-0000-000000000001"
        const val MATCH_ONE = "match-one"
        const val MATCH_TWO = "match-two"
        const val ALPHA_UUID = "00000000-0000-0000-0000-0000000000a1"
        const val BETA_UUID = "00000000-0000-0000-0000-0000000000b2"
        const val FOREIGN_UUID = "00000000-0000-0000-0000-0000000000f3"
        val normalizer = GroupRotationTeamIdentityNormalizer()
    }
}
