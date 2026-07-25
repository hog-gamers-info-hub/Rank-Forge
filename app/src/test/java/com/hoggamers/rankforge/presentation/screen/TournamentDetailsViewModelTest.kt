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
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentDetailsViewModelTest {
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
    fun foundTournamentRendersDetailsState() = runTest {
        val tournament = tournament(id = "stable-id")
        repository.create(tournament)
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals("Summer Cup", viewModel.uiState.value.tournament?.name)
        assertEquals("123", viewModel.uiState.value.tournament?.organizerContactNumber)
    }

    @Test
    fun foundTournamentExposesTwelveSlots() = runTest {
        repository.create(tournament(id = "stable-id"))
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals((1..12).toList(), viewModel.uiState.value.tournament?.slots?.map { it.slotNumber })
    }

    @Test
    fun unknownTournamentRendersNotFoundState() = runTest {
        val viewModel = detailsViewModel()

        viewModel.load("missing")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
    }

    private fun detailsViewModel() = TournamentDetailsViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
    )

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Summer Cup",
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

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    TeamSlot.fixedSlotsForTournament(tournamentId)
                } else {
                    emptyList()
                }
            }
    }
}
