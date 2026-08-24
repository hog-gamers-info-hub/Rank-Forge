package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerScopedTournamentChildReadUseCasesTest {
    @Test
    fun slotsAreOwnerScopedAndSwitchWhenAuthChanges() = runTest {
        val repository = repository()
        val auth = MutableAuthRepository(signedIn("user-a"))
        val useCase = ObserveTournamentSlotsUseCase(repository, auth)
        val aEmissions = mutableListOf<List<TeamSlot>>()
        val bEmissions = mutableListOf<List<TeamSlot>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase("tournament-a").take(2).toList(aEmissions)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase("tournament-b").take(2).toList(bEmissions)
        }

        assertEquals(listOf("A Team"), aEmissions.single().map { it.teamName })
        assertTrue(bEmissions.single().isEmpty())
        assertTrue(useCase("tournament-legacy").first().isEmpty())

        auth.state.value = signedIn("user-b")
        advanceUntilIdle()
        assertEquals(listOf(listOf("A Team"), emptyList()), aEmissions.map { it.map(TeamSlot::teamName) })
        assertEquals(listOf(emptyList(), listOf("B Team")), bEmissions.map { it.map(TeamSlot::teamName) })

        auth.state.value = AuthState.SignedOut
        assertTrue(useCase("tournament-b").first().isEmpty())
        auth.state.value = signedIn("   ")
        assertTrue(useCase("tournament-b").first().isEmpty())
    }

    @Test
    fun rosterReadsAreOwnerScopedForTournamentAndSlot() = runTest {
        val repository = repository()
        val auth = MutableAuthRepository(signedIn("user-a"))
        val perSlot = ObserveRosterPlayersUseCase(repository, auth)
        val wholeTournament = ObserveRosterByTournamentUseCase(repository, auth)

        assertEquals(listOf("A Player"), perSlot("tournament-a", 1).first().map { it.displayName })
        assertTrue(perSlot("tournament-b", 1).first().isEmpty())
        assertTrue(perSlot("tournament-legacy", 1).first().isEmpty())
        assertEquals(listOf("A Player"), wholeTournament("tournament-a").first().getValue(1).map { it.displayName })

        auth.state.value = signedIn("user-b")
        assertEquals(listOf("B Player"), perSlot("tournament-b", 1).first().map { it.displayName })
        assertTrue(wholeTournament("tournament-a").first().isEmpty())
    }

    @Test
    fun matchesAreOwnerScopedAndPreserveChildComposition() = runTest {
        val repository = repository()
        val auth = MutableAuthRepository(signedIn("user-a"))
        val useCase = ObserveMatchesUseCase(repository, auth)

        val aMatch = useCase("tournament-a").first().single()
        assertEquals("match-a", aMatch.id)
        assertEquals(listOf(MatchPlacement(1, 1)), aMatch.placements)
        assertEquals(listOf(MatchKill(1, 4)), aMatch.kills)
        assertTrue(useCase("tournament-b").first().isEmpty())
        assertTrue(useCase("tournament-legacy").first().isEmpty())

        auth.state.value = signedIn("user-b")
        assertEquals("match-b", useCase("tournament-b").first().single().id)
        assertTrue(useCase("tournament-a").first().isEmpty())
    }

    @Test
    fun draftValuesAreOwnerScopedAndRequireTheRequestedTournamentMatchPair() = runTest {
        val repository = repository()
        val auth = MutableAuthRepository(signedIn("user-a"))
        val useCase = ObserveMatchDraftValuesUseCase(repository, auth)

        assertEquals(MatchDraftFieldValues("1", "4"), useCase("tournament-a", "match-a").first()[1])
        assertTrue(useCase("tournament-b", "match-b").first().isEmpty())
        assertTrue(useCase("tournament-legacy", "match-legacy").first().isEmpty())
        assertTrue(useCase("tournament-a", "match-b").first().isEmpty())

        auth.state.value = signedIn("user-b")
        assertEquals(MatchDraftFieldValues("2", "3"), useCase("tournament-b", "match-b").first()[1])
        auth.state.value = AuthState.SignedOut
        assertTrue(useCase("tournament-b", "match-b").first().isEmpty())
    }

    private fun repository(): OwnerScopedChildReadRepository {
        val tournaments = listOf(
            tournament("tournament-a", "user-a"),
            tournament("tournament-b", "user-b"),
            tournament("tournament-legacy", null),
        )
        return OwnerScopedChildReadRepository(
            tournaments = tournaments,
            slots = mapOf(
                "tournament-a" to listOf(TeamSlot("tournament-a", 1, "A Team")),
                "tournament-b" to listOf(TeamSlot("tournament-b", 1, "B Team")),
                "tournament-legacy" to listOf(TeamSlot("tournament-legacy", 1, "Legacy Team")),
            ),
            rosters = mapOf(
                "tournament-a" to mapOf(1 to listOf(RosterPlayer("tournament-a", 1, "A Player"))),
                "tournament-b" to mapOf(1 to listOf(RosterPlayer("tournament-b", 1, "B Player"))),
                "tournament-legacy" to mapOf(1 to listOf(RosterPlayer("tournament-legacy", 1, "Legacy Player"))),
            ),
            matches = listOf(
                match("match-a", "tournament-a", 1, 4),
                match("match-b", "tournament-b", 2, 3),
                match("match-legacy", "tournament-legacy", 3, 2),
            ),
            drafts = mapOf(
                "match-a" to mapOf(1 to MatchDraftFieldValues("1", "4")),
                "match-b" to mapOf(1 to MatchDraftFieldValues("2", "3")),
                "match-legacy" to mapOf(1 to MatchDraftFieldValues("3", "2")),
            ),
        )
    }

    private fun tournament(id: String, ownerUserId: String?) = Tournament(
        id = id,
        name = id,
        date = LocalDate.of(2026, 8, 23),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = ownerUserId,
    )

    private fun match(id: String, tournamentId: String, position: Int, kills: Int) = Match(
        id = id,
        tournamentId = tournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 8, 23),
        mapName = "Map",
        status = MatchStatus.DRAFT,
        placements = listOf(MatchPlacement(1, position)),
        kills = listOf(MatchKill(1, kills)),
    )

    private fun signedIn(userId: String) = AuthState.SignedIn(AuthUser(userId, "$userId@example.test"))

    private class MutableAuthRepository(initialState: AuthState) : AuthRepository {
        val state = MutableStateFlow(initialState)

        override fun observeAuthState(): Flow<AuthState> = state

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()

        override suspend fun login(email: String, password: String): AuthOperationResult = failure()

        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)

        private fun failure() = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
    }

    private class OwnerScopedChildReadRepository(
        tournaments: List<Tournament>,
        private val slots: Map<String, List<TeamSlot>>,
        private val rosters: Map<String, Map<Int, List<RosterPlayer>>>,
        private val matches: List<Match>,
        private val drafts: Map<String, Map<Int, MatchDraftFieldValues>>,
    ) : TournamentRepository {
        private val tournamentsById = tournaments.associateBy { it.id }

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = error("Unscoped child read must not be used")

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            error("Unscoped child read must not be used")

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            error("Unscoped child read must not be used")

        override fun observeSlotsByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<TeamSlot>> = flowOf(
            if (isOwned(tournamentId, ownerUserId)) slots[tournamentId].orEmpty() else emptyList(),
        )

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = error("Unscoped child read must not be used")

        override fun observeRosterByTournamentAndSlotAndOwner(
            tournamentId: String,
            slotNumber: Int,
            ownerUserId: String,
        ): Flow<List<RosterPlayer>> = flowOf(
            if (isOwned(tournamentId, ownerUserId)) {
                rosters[tournamentId].orEmpty()[slotNumber].orEmpty()
            } else {
                emptyList()
            },
        )

        override fun observeRosterByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<Map<Int, List<RosterPlayer>>> = flowOf(
            if (isOwned(tournamentId, ownerUserId)) rosters[tournamentId].orEmpty() else emptyMap(),
        )

        override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
            error("Unscoped child read must not be used")

        override fun observeMatchesByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<Match>> = flowOf(
            if (isOwned(tournamentId, ownerUserId)) {
                matches.filter { it.tournamentId == tournamentId }
            } else {
                emptyList()
            },
        )

        override fun observeMatchById(matchId: String): Flow<Match?> =
            error("Unscoped child read must not be used")

        override fun observeDraftMatchValues(tournamentId: String, matchId: String): Flow<Map<Int, MatchDraftFieldValues>> =
            error("Unscoped child read must not be used")

        override fun observeDraftMatchValuesByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
        ): Flow<Map<Int, MatchDraftFieldValues>> = flowOf(
            drafts[matchId].orEmpty().takeIf {
                tournamentsById[tournamentId]?.ownerUserId == ownerUserId &&
                    matches.firstOrNull { it.id == matchId }?.tournamentId == tournamentId
            }.orEmpty(),
        )

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override fun observeRosterByTournamentId(tournamentId: String): Flow<Map<Int, List<RosterPlayer>>> =
            error("Unscoped child read must not be used")

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false

        private fun isOwned(tournamentId: String, ownerUserId: String): Boolean =
            tournamentsById[tournamentId]?.ownerUserId == ownerUserId
    }
}
