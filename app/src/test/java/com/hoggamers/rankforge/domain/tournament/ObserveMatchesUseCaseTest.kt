package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMatchesUseCaseTest {
    @Test
    fun signedInThenLoadingDoesNotEmitAnEmptyMatchList() = runTest {
        val match = match("match-1")
        val repository = RecordingTournamentRepository(listOf(match))
        val auth = MutableAuthRepository(signedIn("owner-a"))
        val useCase = ObserveMatchesUseCase(repository, auth)
        val emissions = mutableListOf<List<Match>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()
        assertEquals(listOf(listOf(match)), emissions)

        auth.state.value = AuthState.Loading
        advanceUntilIdle()

        assertEquals(listOf(listOf(match)), emissions)
    }

    @Test
    fun loadingThenSignedInEmitsTheOwnerScopedMatchList() = runTest {
        val match = match("match-1")
        val repository = RecordingTournamentRepository(listOf(match))
        val auth = MutableAuthRepository(AuthState.Loading)
        val useCase = ObserveMatchesUseCase(repository, auth)
        val emissions = mutableListOf<List<Match>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()
        assertTrue(emissions.isEmpty())

        auth.state.value = signedIn("owner-a")
        advanceUntilIdle()

        assertEquals(listOf(listOf(match)), emissions)
        assertEquals(listOf("owner-a"), repository.observedOwnerIds)
    }

    @Test
    fun signedOutEmitsAnEmptyMatchList() = runTest {
        val repository = RecordingTournamentRepository(listOf(match("match-1")))
        val auth = MutableAuthRepository(AuthState.SignedOut)
        val useCase = ObserveMatchesUseCase(repository, auth)
        val emissions = mutableListOf<List<Match>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()

        assertEquals(listOf(emptyList<Match>()), emissions)
    }

    @Test
    fun sessionExpiredEmitsAnEmptyMatchList() = runTest {
        val repository = RecordingTournamentRepository(listOf(match("match-1")))
        val auth = MutableAuthRepository(
            AuthState.SessionExpired(
                AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
            ),
        )
        val useCase = ObserveMatchesUseCase(repository, auth)
        val emissions = mutableListOf<List<Match>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()

        assertEquals(listOf(emptyList<Match>()), emissions)
    }

    @Test
    fun signedInUsesTheCurrentOwnerScopedRepositoryObservation() = runTest {
        val repository = RecordingTournamentRepository(listOf(match("match-1")))
        val auth = MutableAuthRepository(signedIn("owner-b"))
        val useCase = ObserveMatchesUseCase(repository, auth)
        val emissions = mutableListOf<List<Match>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()

        assertEquals(listOf("owner-b"), repository.observedOwnerIds)
        assertEquals(listOf(listOf("match-1")), emissions.map { matches -> matches.map(Match::id) })
    }

    private fun signedIn(ownerUserId: String): AuthState =
        AuthState.SignedIn(AuthUser(ownerUserId, "$ownerUserId@example.test"))

    private fun match(id: String): Match = Match(
        id = id,
        tournamentId = TournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 9, 12),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    private class MutableAuthRepository(initialState: AuthState) : AuthRepository {
        val state = MutableStateFlow(initialState)

        override fun observeAuthState(): Flow<AuthState> = state

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = unusedOperation()

        override suspend fun login(email: String, password: String): AuthOperationResult = unusedOperation()

        override suspend fun logout(): AuthOperationResult = unusedOperation()
    }

    private class RecordingTournamentRepository(
        matches: List<Match>,
    ) : TournamentRepository {
        private val matches = MutableStateFlow(matches)
        val observedOwnerIds = mutableListOf<String>()

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())

        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(null)

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = flowOf(emptyList())

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false

        override fun observeMatchesByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<Match>> {
            observedOwnerIds += ownerUserId
            return matches
        }
    }

    private companion object {
        const val TournamentId = "tournament-1"

        fun unusedOperation(): AuthOperationResult = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
    }
}
