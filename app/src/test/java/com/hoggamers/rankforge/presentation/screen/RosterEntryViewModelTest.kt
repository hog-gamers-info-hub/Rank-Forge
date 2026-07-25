package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class RosterEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryTournamentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addEditRemoveAndSavePlayersForOneSlot() = runTest {
        repository.create(tournament("stable-id"))
        val viewModel = viewModel()
        viewModel.load("stable-id", 2)
        advanceUntilIdle()

        viewModel.addPlayer()
        viewModel.addPlayer()
        viewModel.onPlayerNameChanged(0, "Alpha")
        viewModel.onPlayerNameChanged(1, "Bravo")
        viewModel.removePlayer(0)
        viewModel.saveRoster()
        advanceUntilIdle()

        assertEquals(listOf("Bravo"), repository.observeRosterByTournamentAndSlot("stable-id", 2).first().map { it.displayName })
        assertEquals(1, viewModel.uiState.value.playerCount)
    }

    @Test
    fun supportsEmptyDraftAndMarksFewerThanFourIncomplete() = runTest {
        repository.create(tournament("stable-id"))
        val viewModel = viewModel()
        viewModel.load("stable-id", 1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isIncomplete)
        assertEquals(0, viewModel.uiState.value.playerCount)
        viewModel.saveRoster()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), repository.observeRosterByTournamentAndSlot("stable-id", 1).first())
    }

    @Test
    fun seventhPlayerCannotBeAddedAndSixthShowsMaximumState() = runTest {
        repository.create(tournament("stable-id"))
        val viewModel = viewModel()
        viewModel.load("stable-id", 1)
        advanceUntilIdle()

        repeat(6) { viewModel.addPlayer() }
        viewModel.addPlayer()

        assertEquals(6, viewModel.uiState.value.playerCount)
        assertTrue(viewModel.uiState.value.isAtMaximum)
        assertFalse(viewModel.uiState.value.canAddPlayer)
    }

    @Test
    fun reopeningRosterShowsSavedPlayersAndOtherSlotIsEmpty() = runTest {
        repository.create(tournament("stable-id"))
        val firstViewModel = viewModel()
        firstViewModel.load("stable-id", 3)
        advanceUntilIdle()
        firstViewModel.addPlayer()
        firstViewModel.onPlayerNameChanged(0, "Charlie")
        firstViewModel.saveRoster()
        advanceUntilIdle()

        val reopenedViewModel = viewModel()
        reopenedViewModel.load("stable-id", 3)
        advanceUntilIdle()
        val otherSlotViewModel = viewModel()
        otherSlotViewModel.load("stable-id", 4)
        advanceUntilIdle()

        assertEquals(listOf("Charlie"), reopenedViewModel.uiState.value.players.map { it.displayName })
        assertEquals(0, otherSlotViewModel.uiState.value.playerCount)
    }

    private fun viewModel() = RosterEntryViewModel(
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
        saveRoster = SaveRosterUseCase(repository),
    )

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )
}
