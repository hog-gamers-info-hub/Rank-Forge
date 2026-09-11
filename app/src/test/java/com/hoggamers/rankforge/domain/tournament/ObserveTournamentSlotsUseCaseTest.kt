package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTournamentSlotsUseCaseTest {
    @Test
    fun returnsSlotsByTournamentIdInFixedOrder() = runTest {
        val repository = TestTournamentRepository()
        repository.create(tournament(id = "stable-id"))

        val result = ObserveTournamentSlotsUseCase(repository)("stable-id").first()

        assertEquals((1..12).toList(), result.map { it.slotNumber })
        assertEquals(List(12) { "stable-id" }, result.map { it.tournamentId })
    }

    @Test
    fun authLoadingEmitsNothingUntilSignedInThenReturnsOwnerScopedSlots() = runTest {
        val repository = TestTournamentRepository()
        repository.create(tournament(id = "stable-id", ownerUserId = "owner-id"))
        val authRepository = MutableTestAuthRepository(AuthState.Loading)
        val emissions = mutableListOf<List<TeamSlot>>()
        val collection = launch {
            ObserveTournamentSlotsUseCase(repository, authRepository)("stable-id").collect { slots ->
                emissions += slots
            }
        }

        runCurrent()

        assertTrue(emissions.isEmpty())

        authRepository.state.value = AuthState.SignedIn(
            AuthUser(id = "owner-id", email = "owner@example.test"),
        )
        advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals((1..12).toList(), emissions.single().map { it.slotNumber })
        assertEquals(List(12) { "stable-id" }, emissions.single().map { it.tournamentId })
        collection.cancel()
    }

    private fun tournament(
        id: String,
        ownerUserId: String? = null,
    ) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = ownerUserId,
    )

    private class MutableTestAuthRepository(initialState: AuthState) : AuthRepository {
        val state = MutableStateFlow(initialState)

        override fun observeAuthState(): Flow<AuthState> = state

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)

        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)

        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private class TestTournamentRepository : TournamentRepository {
        private val state = MutableStateFlow<List<Tournament>>(emptyList())

        override suspend fun create(tournament: Tournament) {
            state.value = state.value + tournament
        }

        override fun observeAll(): Flow<List<Tournament>> = state

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            state.map { tournaments -> tournaments.firstOrNull { it.id == tournamentId } }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    TeamSlot.fixedSlotsForTournament(tournamentId)
                } else {
                    emptyList()
                }
            }

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false
    }
}
