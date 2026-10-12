package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.FinalizeGroupRotationMatchInput
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationFailure
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationMatchFinalizationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var databaseFile: File
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomTournamentRepository
    private lateinit var identityRepository: RoomGroupRotationMatchIdentityRepository

    @Before
    fun setUp() = runBlocking {
        databaseFile = File(context.cacheDir, "group-rotation-match-finalization-${System.nanoTime()}.db")
        database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseFile.path,
        ).allowMainThreadQueries().build()
        repository = RoomTournamentRepository(database)
        identityRepository = RoomGroupRotationMatchIdentityRepository(database)
        repository.create(tournament())
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(identity(ALPHA_UUID, "Alpha"), identity(BETA_UUID, "Beta")),
        )
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-wal").delete()
    }

    @Test
    fun validSparseUuidMatchFinalizesUsingOnlyUuidResultRows() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 2 to alpha(), 9 to beta())

        val result = finalize(
            MATCH_ONE,
            participated(ALPHA_UUID, 1, 4),
            participated(BETA_UUID, 2, 3),
        )

        assertTrue(result is GroupRotationMatchFinalizationResult.Finalized)
        assertEquals(MatchStatus.FINALIZED.name, database.matchDao().readById(MATCH_ONE)?.status)
        val loaded = identityRepository.read(tournament(), MATCH_ONE) as GroupRotationMatchPersistenceResult.Loaded
        assertEquals(listOf(ALPHA_UUID, BETA_UUID), loaded.snapshot.participantResults.map { it.teamUUID })
        assertTrue(database.matchPlacementDao().observeByMatchId(MATCH_ONE).first().isEmpty())
        assertTrue(database.matchKillDao().observeByMatchId(MATCH_ONE).first().isEmpty())
        assertTrue(database.matchParticipantResultDao().observeByMatchId(MATCH_ONE).first().isEmpty())
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ONE).first()?.status)
    }

    @Test
    fun registryRenameDoesNotChangeFinalizedUuidOwnership() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 7 to alpha())
        database.groupRotationTeamIdentityDao().upsertAll(listOf(identity(ALPHA_UUID, "Alpha Renamed")))

        assertTrue(finalize(MATCH_ONE, participated(ALPHA_UUID, 1, 1)) is GroupRotationMatchFinalizationResult.Finalized)

        val loaded = identityRepository.read(tournament(), MATCH_ONE) as GroupRotationMatchPersistenceResult.Loaded
        assertEquals(ALPHA_UUID, loaded.snapshot.participantResults.single().teamUUID)
        assertEquals("Alpha Renamed", loaded.snapshot.identity.teams.single().displayName)
    }

    @Test
    fun sameUuidAcrossPairingsKeepsEachSavedMatchIdentity() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 2 to alpha())
        createMatch(MATCH_TWO, pairing("A:C"), 9 to alpha())

        assertTrue(finalize(MATCH_ONE, participated(ALPHA_UUID, 1, 1)) is GroupRotationMatchFinalizationResult.Finalized)
        assertTrue(finalize(MATCH_TWO, participated(ALPHA_UUID, 1, 2)) is GroupRotationMatchFinalizationResult.Finalized)

        val first = identityRepository.read(tournament(), MATCH_ONE) as GroupRotationMatchPersistenceResult.Loaded
        val second = identityRepository.read(tournament(), MATCH_TWO) as GroupRotationMatchPersistenceResult.Loaded
        assertEquals(ALPHA_UUID, first.snapshot.participantResults.single().teamUUID)
        assertEquals(ALPHA_UUID, second.snapshot.participantResults.single().teamUUID)
        assertEquals(2, first.snapshot.identity.teams.single().lobbySlotNumber)
        assertEquals(9, second.snapshot.identity.teams.single().lobbySlotNumber)
    }

    @Test
    fun duplicateUuidResultsAreRejectedWithoutChangingDraftState() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 1 to alpha(), 2 to beta())

        val result = finalize(
            MATCH_ONE,
            participated(ALPHA_UUID, 1, 1),
            participated(ALPHA_UUID, 2, 2),
        )

        assertEquals(
            GroupRotationMatchFinalizationFailure.INVALID_DATA,
            (result as GroupRotationMatchFinalizationResult.Rejected).reason,
        )
        assertEquals(MatchStatus.DRAFT.name, database.matchDao().readById(MATCH_ONE)?.status)
        val loaded = identityRepository.read(tournament(), MATCH_ONE) as GroupRotationMatchPersistenceResult.Loaded
        assertTrue(loaded.snapshot.participantResults.isEmpty())
    }

    @Test
    fun invalidPlacementAndKillsAreRejected() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 1 to alpha(), 2 to beta())

        val result = finalize(
            MATCH_ONE,
            GroupRotationMatchParticipantResult(ALPHA_UUID, MatchParticipationStatus.PARTICIPATED, 3, -1),
            participated(BETA_UUID, 1, 0),
        )

        assertEquals(
            GroupRotationMatchFinalizationFailure.INVALID_DATA,
            (result as GroupRotationMatchFinalizationResult.Rejected).reason,
        )
        assertEquals(MatchStatus.DRAFT.name, database.matchDao().readById(MATCH_ONE)?.status)
    }

    @Test
    fun missingIdentitySnapshotIsRejected() = runBlocking {
        database.matchDao().upsert(
            MatchEntity(
                id = "missing-snapshot",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 3,
                date = "2026-10-01",
                mapName = "Bermuda",
                status = MatchStatus.DRAFT.name,
                groupPairingKey = "A:B",
            ),
        )

        val result = finalize("missing-snapshot", participated(ALPHA_UUID, 1, 1))

        assertEquals(
            GroupRotationMatchFinalizationFailure.INVALID_DATA,
            (result as GroupRotationMatchFinalizationResult.Rejected).reason,
        )
        assertEquals(MatchStatus.DRAFT.name, database.matchDao().readById("missing-snapshot")?.status)
    }

    @Test
    fun unauthorizedAndRepeatedFinalizationCannotOverwriteResults() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 1 to alpha())

        val unauthorized = repository.finalizeGroupRotationMatchByOwner(
            FinalizeGroupRotationMatchInput(
                TOURNAMENT_ID,
                MATCH_ONE,
                listOf(participated(ALPHA_UUID, 1, 1)),
            ),
            "other-owner",
        )
        assertEquals(
            GroupRotationMatchFinalizationFailure.TOURNAMENT_NOT_FOUND,
            (unauthorized as GroupRotationMatchFinalizationResult.Rejected).reason,
        )
        assertTrue(finalize(MATCH_ONE, participated(ALPHA_UUID, 1, 1)) is GroupRotationMatchFinalizationResult.Finalized)

        val repeated = finalize(MATCH_ONE, participated(ALPHA_UUID, 1, 9))
        assertEquals(
            GroupRotationMatchFinalizationFailure.MATCH_NOT_DRAFT,
            (repeated as GroupRotationMatchFinalizationResult.Rejected).reason,
        )
        val loaded = identityRepository.read(tournament(), MATCH_ONE) as GroupRotationMatchPersistenceResult.Loaded
        assertEquals(1, loaded.snapshot.participantResults.single().kills)
    }

    @Test
    fun preexistingUuidDraftResultsSurviveValidationFailure() = runBlocking {
        createMatch(MATCH_ONE, pairing("A:B"), 1 to alpha())
        val snapshot = GroupRotationMatchIdentitySnapshot(
            TOURNAMENT_ID,
            pairing("A:B"),
            listOf(alpha().copy(lobbySlotNumber = 1)),
        )
        assertTrue(
            identityRepository.save(
                tournament(),
                MATCH_ONE,
                snapshot,
                listOf(
                    GroupRotationMatchParticipantResult(
                        ALPHA_UUID,
                        MatchParticipationStatus.PARTICIPATED,
                        null,
                        4,
                    ),
                ),
            ) is GroupRotationMatchPersistenceResult.Saved,
        )

        val result = finalize(MATCH_ONE, participated(FOREIGN_UUID, 1, 1))

        assertEquals(
            GroupRotationMatchFinalizationFailure.INVALID_DATA,
            (result as GroupRotationMatchFinalizationResult.Rejected).reason,
        )
        assertEquals(MatchStatus.DRAFT.name, database.matchDao().readById(MATCH_ONE)?.status)
        val loaded = identityRepository.read(tournament(), MATCH_ONE) as GroupRotationMatchPersistenceResult.Loaded
        assertEquals(4, loaded.snapshot.participantResults.single().kills)
    }

    private suspend fun createMatch(
        matchId: String,
        pairing: GroupPairing,
        vararg teams: Pair<Int, GroupRotationMatchTeamIdentity>,
    ) {
        val match = Match(
            id = matchId,
            tournamentId = TOURNAMENT_ID,
            matchNumber = if (matchId == MATCH_ONE) 1 else 2,
            date = LocalDate.of(2026, 10, 1),
            mapName = "Bermuda",
            status = MatchStatus.DRAFT,
            groupPairing = pairing,
        )
        val result = repository.createGroupRotationMatchByOwner(
            match,
            GroupRotationMatchIdentitySnapshot(
                tournamentId = TOURNAMENT_ID,
                pairing = pairing,
                teams = teams.map { (lobbySlot, team) -> team.copy(lobbySlotNumber = lobbySlot) },
            ),
            OWNER_ID,
        )
        assertTrue(result is com.hoggamers.rankforge.domain.tournament.GroupRotationMatchCreationRepositoryResult.Created)
    }

    private suspend fun finalize(
        matchId: String,
        vararg results: GroupRotationMatchParticipantResult,
    ) = repository.finalizeGroupRotationMatchByOwner(
        FinalizeGroupRotationMatchInput(TOURNAMENT_ID, matchId, results.toList()),
        OWNER_ID,
    )

    private fun tournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation Cup",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER_ID,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(pairing("A:B"), pairing("A:C"), pairing("B:C")),
    )

    private fun identity(uuid: String, name: String) = GroupRotationTeamIdentityEntity(
        tournamentId = TOURNAMENT_ID,
        teamUUID = uuid,
        displayName = name,
        normalizedName = name.lowercase(),
    )

    private fun alpha() = GroupRotationMatchTeamIdentity(1, ALPHA_UUID, "Alpha")

    private fun beta() = GroupRotationMatchTeamIdentity(1, BETA_UUID, "Beta")

    private fun participated(uuid: String, placement: Int, kills: Int) =
        GroupRotationMatchParticipantResult(uuid, MatchParticipationStatus.PARTICIPATED, placement, kills)

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private companion object {
        const val TOURNAMENT_ID = "00000000-0000-0000-0000-000000000001"
        const val OWNER_ID = "owner"
        const val MATCH_ONE = "match-one"
        const val MATCH_TWO = "match-two"
        const val ALPHA_UUID = "00000000-0000-0000-0000-0000000000a1"
        const val BETA_UUID = "00000000-0000-0000-0000-0000000000b2"
        const val FOREIGN_UUID = "00000000-0000-0000-0000-0000000000f3"
    }
}
