package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class ObserveRosterByTournamentUseCaseTest {
    @Test
    fun signedInThenLoadingDoesNotEmitAnEmptyRoster() = runTest {
        val roster = mapOf(1 to listOf(RosterPlayer(TournamentId, 1, "Player A")))
        val repository = RecordingTournamentRepository(roster)
        val auth = MutableAuthRepository(signedIn(OwnerId))
        val useCase = ObserveRosterByTournamentUseCase(repository, auth)
        val emissions = mutableListOf<Map<Int, List<RosterPlayer>>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()
        assertEquals(listOf(roster), emissions)

        auth.state.value = AuthState.Loading
        advanceUntilIdle()

        assertEquals(listOf(roster), emissions)
    }

    @Test
    fun loadingThenSignedInEmitsTheOwnerScopedRoster() = runTest {
        val roster = mapOf(1 to listOf(RosterPlayer(TournamentId, 1, "Player A")))
        val repository = RecordingTournamentRepository(roster)
        val auth = MutableAuthRepository(AuthState.Loading)
        val useCase = ObserveRosterByTournamentUseCase(repository, auth)
        val emissions = mutableListOf<Map<Int, List<RosterPlayer>>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()
        assertTrue(emissions.isEmpty())

        auth.state.value = signedIn(OwnerId)
        advanceUntilIdle()

        assertEquals(listOf(roster), emissions)
        assertEquals(listOf(TournamentId to OwnerId), repository.observedRequests)
    }

    @Test
    fun signedOutEmitsAnEmptyRoster() = runTest {
        val roster = mapOf(1 to listOf(RosterPlayer(TournamentId, 1, "Player A")))
        val repository = RecordingTournamentRepository(roster)
        val auth = MutableAuthRepository(signedIn(OwnerId))
        val useCase = ObserveRosterByTournamentUseCase(repository, auth)
        val emissions = mutableListOf<Map<Int, List<RosterPlayer>>>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(TournamentId).toList(emissions)
        }

        advanceUntilIdle()
        auth.state.value = AuthState.SignedOut
        advanceUntilIdle()

        assertEquals(listOf(roster, emptyMap()), emissions)
    }

    @Test
    fun signedInPassesTournamentAndOwnerToRepository() = runTest {
        val repository = RecordingTournamentRepository(emptyMap())
        val auth = MutableAuthRepository(signedIn(OwnerId))
        val useCase = ObserveRosterByTournamentUseCase(repository, auth)

        useCase(TournamentId).first()

        assertEquals(listOf(TournamentId to OwnerId), repository.observedRequests)
    }

    private fun signedIn(ownerUserId: String): AuthState =
        AuthState.SignedIn(AuthUser(ownerUserId, "$ownerUserId@example.test"))

    private class MutableAuthRepository(initialState: AuthState) : AuthRepository {
        val state = MutableStateFlow(initialState)

        override fun observeAuthState(): Flow<AuthState> = state

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()

        override suspend fun login(email: String, password: String): AuthOperationResult = failure()

        override suspend fun logout(): AuthOperationResult = failure()

        private fun failure() = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
    }

    private class RecordingTournamentRepository(
        private val roster: Map<Int, List<RosterPlayer>>,
    ) : TournamentRepository {
        val observedRequests = mutableListOf<Pair<String, String>>()

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())

        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(null)

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = flowOf(emptyList())

        override fun observeRosterByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<Map<Int, List<RosterPlayer>>> {
            observedRequests += tournamentId to ownerUserId
            return flowOf(roster)
        }

        override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> = flowOf(emptyList())

        override fun observeMatchById(matchId: String): Flow<Match?> = flowOf(null)

        override fun observeDraftMatchValues(
            tournamentId: String,
            matchId: String,
        ): Flow<Map<Int, MatchDraftFieldValues>> = flowOf(emptyMap())

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
    }

    private companion object {
        const val TournamentId = "tournament-1"
        const val OwnerId = "owner-a"
    }
}
