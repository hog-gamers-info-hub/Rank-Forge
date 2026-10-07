package com.hoggamers.rankforge.data.tournament

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.RoomGroupRotationTeamSetupDraftRepository
import com.hoggamers.rankforge.data.local.RosterPlayerEntity
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftSaveResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupLocalSaveResult
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupResult
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationTeamSetupRepositoryTest {
    private lateinit var database: RankForgeDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RankForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveReplacesCompleteMappingSetAndUsesOwnerScopedUseCase() = runBlocking {
        val tournament = createTournament(TournamentStatus.DRAFT)
        val repository = RoomTournamentRepository(database)
        repository.create(tournament)
        val useCase = SaveGroupRotationTeamSetupUseCase(repository, SignedInAuthRepository)

        assertEquals(
            SaveGroupRotationTeamSetupResult.Saved,
            useCase(candidate(teamNamePrefix = "First")),
        )
        assertEquals(
            (1..12).toList(),
            database.groupRotationPairingLobbySlotDao()
                .observeByTournamentId(TOURNAMENT_ID)
                .first()
                .map { it.teamSlotNumber },
        )

        assertEquals(
            SaveGroupRotationTeamSetupResult.Saved,
            useCase(candidate(teamNamePrefix = "Second")),
        )
        assertEquals(
            (1..12).map { "Second $it" },
            database.teamSlotDao().observeByTournamentId(TOURNAMENT_ID).first().map { it.teamName },
        )
        assertEquals(
            (1..12).toList(),
            database.groupRotationPairingLobbySlotDao()
                .observeByTournamentId(TOURNAMENT_ID)
                .first()
                .map { it.teamSlotNumber },
        )
    }

    @Test
    fun confirmedAuthoritativeChangeReturnsTournamentToDraft() = runBlocking {
        val tournament = createTournament(TournamentStatus.DRAFT)
        val repository = RoomTournamentRepository(database)
        repository.create(tournament)
        assertEquals(
            GroupRotationTeamSetupLocalSaveResult.Saved,
            repository.saveGroupRotationTeamSetup(candidate("Initial"), OWNER_ID),
        )
        assertTrue(repository.confirmTournament(TOURNAMENT_ID))

        assertEquals(
            GroupRotationTeamSetupLocalSaveResult.Saved,
            repository.saveGroupRotationTeamSetup(candidate("Changed"), OWNER_ID),
        )

        assertEquals(TournamentStatus.DRAFT, repository.observeById(TOURNAMENT_ID).first()?.status)
    }

    @Test
    fun historyProtectionRejectsRosterBeforeMutation() = runBlocking {
        val tournament = createTournament(TournamentStatus.DRAFT)
        val repository = RoomTournamentRepository(database)
        repository.create(tournament)
        repository.saveGroupRotationTeamSetup(candidate("Initial"), OWNER_ID)
        val beforeSlots = database.teamSlotDao().observeByTournamentId(TOURNAMENT_ID).first()
        val beforeMappings = database.groupRotationPairingLobbySlotDao()
            .observeByTournamentId(TOURNAMENT_ID)
            .first()
        database.rosterPlayerDao().upsertAll(
            listOf(RosterPlayerEntity(TOURNAMENT_ID, 1, 1, "Player")),
        )

        val result = repository.saveGroupRotationTeamSetup(candidate("Changed"), OWNER_ID)

        assertEquals(GroupRotationTeamSetupLocalSaveResult.ProtectedHistory, result)
        assertEquals(beforeSlots, database.teamSlotDao().observeByTournamentId(TOURNAMENT_ID).first())
        assertEquals(
            beforeMappings,
            database.groupRotationPairingLobbySlotDao().observeByTournamentId(TOURNAMENT_ID).first(),
        )
    }

    @Test
    fun historyProtectionRejectsMatchBeforeMutation() = runBlocking {
        val tournament = createTournament(TournamentStatus.DRAFT)
        val repository = RoomTournamentRepository(database)
        repository.create(tournament)
        repository.saveGroupRotationTeamSetup(candidate("Initial"), OWNER_ID)
        database.matchDao().upsert(
            MatchEntity(
                id = "match-1",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = "2026-10-06",
                mapName = "Alpine",
                status = "DRAFT",
                groupPairingKey = "A:B",
            ),
        )

        assertEquals(
            GroupRotationTeamSetupLocalSaveResult.ProtectedHistory,
            repository.saveGroupRotationTeamSetup(candidate("Changed"), OWNER_ID),
        )
    }

    @Test
    fun invalidOwnerCannotSaveGroupRotationSetup() = runBlocking {
        val tournament = createTournament(TournamentStatus.DRAFT)
        val repository = RoomTournamentRepository(database)
        repository.create(tournament)

        assertEquals(
            GroupRotationTeamSetupLocalSaveResult.TournamentNotFound,
            repository.saveGroupRotationTeamSetup(candidate("Changed"), "other-owner"),
        )
        assertTrue(
            database.groupRotationPairingLobbySlotDao()
                .observeByTournamentId(TOURNAMENT_ID)
                .first()
                .isEmpty(),
        )
    }

    @Test
    fun draftRepositoryReplacesRawSnapshotWithoutTouchingFinalSetupState() = runBlocking {
        val tournament = createTournament(TournamentStatus.CONFIRMED)
        database.tournamentDao().upsert(tournament.toEntity(1))
        database.teamSlotDao().upsertAll(tournament.formatDerivedSlots().map { it.toEntity() })
        database.tournamentGroupPairingDao().upsertAll(tournament.selectedGroupPairings.map { it.toEntity(tournament.id) })
        database.syncRevisionDao().upsert(
            com.hoggamers.rankforge.data.local.SyncRevisionEntity(TOURNAMENT_ID, 7, 6),
        )
        val repository = RoomGroupRotationTeamSetupDraftRepository(database)
        val draft = candidate("  Raw  $", blankNames = true)

        assertEquals(GroupRotationTeamSetupDraftSaveResult.Saved, repository.replaceDraft(tournament, draft))
        assertEquals(draft.entries, repository.readDraft(TOURNAMENT_ID))
        assertEquals(TournamentStatus.CONFIRMED.name, database.tournamentDao().observeById(TOURNAMENT_ID).first()?.status)
        assertEquals(7, database.syncRevisionDao().readByTournamentId(TOURNAMENT_ID)?.localRevision)
        assertEquals(
            GroupRotationTeamSetupDraftSaveResult.InvalidDraft(
                listOf(
                    com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftIssue(
                        com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftIssueCode.INVALID_PAIRING_ENTRY_COUNT,
                        pairingKey = "A:B",
                    ),
                    com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftIssue(
                        com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftIssueCode.MISSING_LOBBY_SLOT,
                        pairingKey = "A:B",
                        lobbySlotNumber = 12,
                    ),
                ),
            ),
            repository.replaceDraft(
                tournament,
                draft.copy(entries = draft.entries.dropLast(1)),
            ),
        )
        assertEquals(draft.entries, repository.readDraft(TOURNAMENT_ID))
        repository.clearDraft(TOURNAMENT_ID)
        assertTrue(repository.readDraft(TOURNAMENT_ID).isEmpty())
    }

    private suspend fun createTournament(status: TournamentStatus): Tournament {
        val tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Rotation",
            stageName = "Stage",
            organizerContactNumber = "123",
            status = status,
            ownerUserId = OWNER_ID,
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = listOf(GroupPairing.fromCanonicalKey("A:B")),
        )
        return tournament
    }

    private fun candidate(
        teamNamePrefix: String,
        blankNames: Boolean = false,
    ): GroupRotationTeamSetupCandidate = GroupRotationTeamSetupCandidate(
        tournamentId = TOURNAMENT_ID,
        entries = (1..12).map { lobbySlot ->
            GroupRotationPairingTeamEntry(
                pairing = GroupPairing.fromCanonicalKey("A:B"),
                lobbySlotNumber = lobbySlot,
                teamName = if (blankNames && lobbySlot > 1) "" else "$teamNamePrefix $lobbySlot",
            )
        },
    )

    private companion object {
        const val TOURNAMENT_ID = "rotation-repository-test"
        const val OWNER_ID = "owner"
    }

    private object SignedInAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")),
        )

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")

        override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")

        override suspend fun logout(): AuthOperationResult = error("unused")
    }
}

