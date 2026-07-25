package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTournamentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = TestTournamentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialEmptyRepositoryRendersEmptyState() = runTest {
        val viewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun createdTournamentsRenderInNewestFirstOrder() = runTest {
        val viewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))
        repository.create(tournament(id = "older", name = "Older Cup"))
        repository.create(tournament(id = "newer", name = "Newer Cup"))

        advanceUntilIdle()

        assertEquals(listOf("Newer Cup", "Older Cup"), viewModel.uiState.value.tournaments.map { it.name })
    }

    private fun tournament(
        id: String,
        name: String,
    ) = Tournament(
        id = id,
        name = name,
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )

    private class TestTournamentRepository : TournamentRepository {
        private val state = MutableStateFlow<List<Tournament>>(emptyList())

        override suspend fun create(tournament: Tournament) {
            state.value = state.value + tournament
        }

        override fun observeAll(): Flow<List<Tournament>> = state

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            state.map { tournaments -> tournaments.firstOrNull { it.id == tournamentId } }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<com.hoggamers.rankforge.domain.tournament.TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    com.hoggamers.rankforge.domain.tournament.TeamSlot.fixedSlotsForTournament(tournamentId)
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
        ): Flow<List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>,
        ) = Unit
    }
}
