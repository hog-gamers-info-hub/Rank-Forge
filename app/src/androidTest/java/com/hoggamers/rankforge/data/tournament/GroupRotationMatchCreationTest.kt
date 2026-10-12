package com.hoggamers.rankforge.data.tournament

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.tournament.CreateGroupRotationMatchUseCase
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchCreationInput
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchCreationRepositoryResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchCreationResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationMatchCreationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var databaseFile: File
    private lateinit var database: RankForgeDatabase
    private lateinit var repository: RoomTournamentRepository
    private lateinit var identityRepository: RoomGroupRotationTeamIdentityRepository
    private lateinit var persistenceRepository: RoomGroupRotationMatchIdentityRepository
    private lateinit var useCase: CreateGroupRotationMatchUseCase

    @Before
    fun setUp() = runBlocking {
        databaseFile = File(context.cacheDir, "group-rotation-match-creation-${System.nanoTime()}.db")
        database = Room.databaseBuilder(
            context,
            RankForgeDatabase::class.java,
            databaseFile.path,
        ).allowMainThreadQueries().build()
        repository = RoomTournamentRepository(database)
        identityRepository = RoomGroupRotationTeamIdentityRepository(database)
        persistenceRepository = RoomGroupRotationMatchIdentityRepository(database)
        useCase = CreateGroupRotationMatchUseCase(repository, identityRepository, SignedInAuthRepository)
        repository.create(tournament())
        database.groupRotationTeamIdentityDao().upsertAll(
            listOf(identity(ALPHA_UUID, "Alpha"), identity(BETA_UUID, "Beta")),
        )
        database.groupRotationPairingTeamIdentityAssignmentDao().upsertAll(
            listOf(
                assignment("A:B", 3, ALPHA_UUID),
                assignment("A:B", 11, BETA_UUID),
                assignment("A:C", 9, ALPHA_UUID),
            ),
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
    fun savedSparseUuidAssignmentsArePersistedAndObservable() = runBlocking {
        val result = useCase(input(pairing("A:B")))

        val created = result.created()
        assertEquals(listOf(3, 11), created.identitySnapshot.teams.map { it.lobbySlotNumber })
        assertEquals(listOf(ALPHA_UUID, BETA_UUID), created.identitySnapshot.teams.map { it.teamUUID })

        val observed = repository.observeMatchesByTournamentIdAndOwner(TOURNAMENT_ID, OWNER_ID)
            .first { it.size == 1 }
            .single()
        assertEquals(created.match.id, observed.id)
        assertEquals(pairing("A:B"), observed.groupPairing)
        assertEquals(
            created.identitySnapshot,
            (persistenceRepository.read(tournament(), created.match.id) as GroupRotationMatchPersistenceResult.Loaded)
                .snapshot.identity,
        )
    }

    @Test
    fun sameUuidCanBeCreatedAtDifferentLobbyPositionsAcrossPairings() = runBlocking {
        val first = useCase(input(pairing("A:B"))).created()
        val second = useCase(input(pairing("A:C"))).created()

        assertEquals(ALPHA_UUID, first.identitySnapshot.teams.first { it.teamUUID == ALPHA_UUID }.teamUUID)
        assertEquals(9, second.identitySnapshot.teams.single().lobbySlotNumber)
        assertEquals(ALPHA_UUID, second.identitySnapshot.teams.single().teamUUID)
        assertEquals(2, repository.observeMatchesByTournamentId(TOURNAMENT_ID).first().size)
    }

    @Test
    fun emptyOrUnselectedPairingsAreRejectedWithoutCreatingRows() = runBlocking {
        val unselected = useCase(input(pairing("A:D")))
        assertEquals(
            GroupRotationMatchCreationFailure.INVALID_PAIRING,
            (unselected as GroupRotationMatchCreationResult.Rejected).failure,
        )

        val empty = useCase(input(pairing("B:C")))
        assertEquals(
            GroupRotationMatchCreationFailure.EMPTY_PAIRING,
            (empty as GroupRotationMatchCreationResult.Rejected).failure,
        )
        assertTrue(repository.observeMatchesByTournamentId(TOURNAMENT_ID).first().isEmpty())
        assertTrue(database.groupRotationMatchIdentityHeaderDao().readByMatchId("missing").let { it == null })
    }

    @Test
    fun invalidSnapshotLeavesMatchAndIdentityRowsAbsent() = runBlocking {
        val match = Match(
            id = "invalid-match",
            tournamentId = TOURNAMENT_ID,
            matchNumber = 1,
            date = LocalDate.of(2026, 10, 11),
            mapName = "Bermuda",
            status = MatchStatus.DRAFT,
            groupPairing = pairing("A:B"),
        )
        val invalidSnapshot = GroupRotationMatchIdentitySnapshot(
            tournamentId = TOURNAMENT_ID,
            pairing = pairing("A:B"),
            teams = listOf(
                com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity(
                    lobbySlotNumber = 3,
                    teamUUID = "missing-uuid",
                    displayName = "Missing",
                ),
            ),
        )

        val result = repository.createGroupRotationMatchByOwner(match, invalidSnapshot, OWNER_ID)

        assertTrue(result is GroupRotationMatchCreationRepositoryResult.Rejected)
        assertEquals(null, database.matchDao().readById(match.id))
        assertEquals(null, database.groupRotationMatchIdentityHeaderDao().readByMatchId(match.id))
        assertFalse(repository.observeMatchesByTournamentId(TOURNAMENT_ID).first().any { it.id == match.id })
    }

    @Test
    fun renamedRegistryTeamUsesCurrentNameAndPreservesUuid() = runBlocking {
        database.groupRotationTeamIdentityDao().upsertAll(listOf(identity(ALPHA_UUID, "Alpha Renamed")))

        val created = useCase(input(pairing("A:B"))).created()
        val alpha = created.identitySnapshot.teams.first { it.teamUUID == ALPHA_UUID }

        assertEquals(ALPHA_UUID, alpha.teamUUID)
        assertEquals("Alpha Renamed", alpha.displayName)
    }

    @Test
    fun standardCreationMethodRemainsSeparateFromUuidSnapshotPersistence() = runBlocking {
        val standardId = "standard-tournament"
        val standard = tournament(standardId, TournamentFormat.STANDARD, emptyList())
        repository.create(standard)
        repository.saveTeamNames(standardId, mapOf(1 to "Standard Team"))
        val match = Match(
            id = "standard-match",
            tournamentId = standardId,
            matchNumber = 1,
            date = LocalDate.of(2026, 10, 11),
            mapName = "Bermuda",
            status = MatchStatus.DRAFT,
        )

        assertTrue(repository.createDraftMatchByOwner(match, OWNER_ID) is com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult.Created)
        assertNotNull(database.matchDao().readById(match.id))
        assertEquals(null, database.groupRotationMatchIdentityHeaderDao().readByMatchId(match.id))
    }

    private fun input(pairing: GroupPairing) = GroupRotationMatchCreationInput(
        tournamentId = TOURNAMENT_ID,
        pairing = pairing,
        date = LocalDate.of(2026, 10, 11),
        mapName = "Bermuda",
    )

    private fun tournament(
        id: String = TOURNAMENT_ID,
        format: TournamentFormat = TournamentFormat.GROUP_ROTATION,
        pairings: List<GroupPairing> = listOf(pairing("A:B"), pairing("A:C"), pairing("B:C")),
    ) = Tournament(
        id = id,
        name = "Rotation Cup",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER_ID,
        format = format,
        groupCount = if (format == TournamentFormat.GROUP_ROTATION) 3 else null,
        selectedGroupPairings = pairings,
    )

    private fun identity(teamUUID: String, displayName: String) = GroupRotationTeamIdentityEntity(
        tournamentId = TOURNAMENT_ID,
        teamUUID = teamUUID,
        displayName = displayName,
        normalizedName = normalizer.normalize(displayName),
    )

    private fun assignment(pairingKey: String, lobbySlot: Int, teamUUID: String) =
        GroupRotationPairingTeamIdentityAssignmentEntity(
            tournamentId = TOURNAMENT_ID,
            pairingKey = pairingKey,
            lobbySlotNumber = lobbySlot,
            teamUUID = teamUUID,
        )

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private fun GroupRotationMatchCreationResult.created() =
        (this as GroupRotationMatchCreationResult.Created)

    private companion object {
        const val TOURNAMENT_ID = "00000000-0000-0000-0000-000000000001"
        const val OWNER_ID = "owner"
        const val ALPHA_UUID = "00000000-0000-0000-0000-0000000000a1"
        const val BETA_UUID = "00000000-0000-0000-0000-0000000000b2"
        val normalizer = GroupRotationTeamIdentityNormalizer()
    }

    private object SignedInAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")),
        )

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = unused()

        override suspend fun login(email: String, password: String): AuthOperationResult = unused()

        override suspend fun logout(): AuthOperationResult = unused()

        override suspend fun clearLocalSession() = Unit

        private fun unused(): AuthOperationResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
    }
}
