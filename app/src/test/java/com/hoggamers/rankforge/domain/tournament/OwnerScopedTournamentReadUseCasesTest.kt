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
import kotlinx.coroutines.flow.map
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
class OwnerScopedTournamentReadUseCasesTest {
    @Test
    fun observeTournamentsScopesRowsToCurrentOwnerAndSwitchesWithAuth() = runTest {
        val repository = OwnerScopedTournamentRepository(
            tournaments = listOf(
                tournament("a", "user-a"),
                tournament("b", "user-b"),
                tournament("legacy", null),
            ),
        )
        val auth = MutableAuthRepository(signedIn("user-a"))
        val useCase = ObserveTournamentsUseCase(repository, auth)
        val emissions = mutableListOf<List<Tournament>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().take(2).toList(emissions)
        }

        assertEquals(listOf("a"), emissions.single().map { it.id })
        auth.state.value = signedIn("user-b")
        advanceUntilIdle()
        assertEquals(listOf(listOf("a"), listOf("b")), emissions.map { it.map(Tournament::id) })

        auth.state.value = AuthState.SignedOut
        assertTrue(useCase().first().isEmpty())
        auth.state.value = signedIn("   ")
        assertTrue(useCase().first().isEmpty())
        assertEquals(listOf("user-a", "user-b"), repository.observedOwners)
    }

    @Test
    fun observeSummariesScopesRowsAndRetainsRoomSummaryFields() = runTest {
        val a = tournament("a", "user-a")
        val b = tournament("b", "user-b")
        val legacy = tournament("legacy", null)
        val repository = OwnerScopedTournamentRepository(
            tournaments = listOf(a, b, legacy),
            summaries = listOf(
                TournamentSummary(a, totalTeams = 8, totalMatches = 3, lastUpdatedEpochMillis = 101L),
                TournamentSummary(b, totalTeams = 4, totalMatches = 1, lastUpdatedEpochMillis = 202L),
                TournamentSummary(legacy, totalTeams = 12, totalMatches = 7, lastUpdatedEpochMillis = 303L),
            ),
        )
        val auth = MutableAuthRepository(signedIn("user-a"))
        val useCase = ObserveTournamentSummariesUseCase(repository, auth)
        val emissions = mutableListOf<List<TournamentSummary>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().take(2).toList(emissions)
        }

        assertEquals(
            listOf(TournamentSummary(a, totalTeams = 8, totalMatches = 3, lastUpdatedEpochMillis = 101L)),
            emissions.single(),
        )
        auth.state.value = signedIn("user-b")
        advanceUntilIdle()
        assertEquals(
            listOf(
                listOf(TournamentSummary(a, totalTeams = 8, totalMatches = 3, lastUpdatedEpochMillis = 101L)),
                listOf(TournamentSummary(b, totalTeams = 4, totalMatches = 1, lastUpdatedEpochMillis = 202L)),
            ),
            emissions,
        )

        auth.state.value = AuthState.SignedOut
        assertTrue(useCase().first().isEmpty())
        assertEquals(listOf("user-a", "user-b"), repository.observedSummaryOwners)
    }

    @Test
    fun getTournamentByIdScopesLookupAndInvalidatesOnAccountSwitch() = runTest {
        val repository = OwnerScopedTournamentRepository(
            tournaments = listOf(
                tournament("a", "user-a"),
                tournament("b", "user-b"),
                tournament("legacy", null),
            ),
        )
        val auth = MutableAuthRepository(signedIn("user-a"))
        val useCase = GetTournamentByIdUseCase(repository, auth)
        val aEmissions = mutableListOf<Tournament?>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase("a").take(2).toList(aEmissions)
        }

        assertEquals("a", aEmissions.single()?.id)
        assertEquals(null, useCase("b").first())
        assertEquals(null, useCase("legacy").first())

        auth.state.value = signedIn("user-b")
        advanceUntilIdle()
        assertEquals(listOf("a", null), aEmissions.map { it?.id })
        assertEquals("b", useCase("b").first()?.id)
        assertEquals(null, useCase("legacy").first())
    }

    private fun tournament(id: String, ownerUserId: String?) = Tournament(
        id = id,
        name = "Tournament $id",
        date = LocalDate.of(2026, 8, 23),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = ownerUserId,
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

    private class OwnerScopedTournamentRepository(
        tournaments: List<Tournament>,
        summaries: List<TournamentSummary> = tournaments.map { TournamentSummary(it, 0, 0, null) },
    ) : TournamentRepository {
        private val tournaments = MutableStateFlow(tournaments)
        private val summaries = MutableStateFlow(summaries)
        val observedOwners = mutableListOf<String>()
        val observedSummaryOwners = mutableListOf<String>()

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = error("Unscoped tournament reads must not be used")

        override fun observeAllByOwner(ownerUserId: String): Flow<List<Tournament>> {
            observedOwners += ownerUserId
            return tournaments.map { values -> values.filter { it.ownerUserId == ownerUserId } }
        }

        override fun observeSummaries(): Flow<List<TournamentSummary>> =
            error("Unscoped summary reads must not be used")

        override fun observeSummariesByOwner(ownerUserId: String): Flow<List<TournamentSummary>> {
            observedSummaryOwners += ownerUserId
            return summaries.map { values ->
                values.filter { it.tournament.ownerUserId == ownerUserId }
            }
        }

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            error("Unscoped tournament lookups must not be used")

        override fun observeByIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<Tournament?> = tournaments.map { values ->
            values.firstOrNull { it.id == tournamentId && it.ownerUserId == ownerUserId }
        }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            error("Not used by owner-scoped read tests")

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = error("Not used by owner-scoped read tests")

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false
    }
}
