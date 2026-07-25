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
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class TeamEntryViewModelTest {
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
    fun loadExposesAllTwelveSlotsInOrder() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals((1..12).toList(), viewModel.uiState.value.slots.map { it.slotNumber })
    }

    @Test
    fun editingOneSlotDoesNotChangeOtherSlots() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(slotNumber = 2, teamName = "Bravo")

        assertEquals("", viewModel.uiState.value.slots.first { it.slotNumber == 1 }.teamName)
        assertEquals("Bravo", viewModel.uiState.value.slots.first { it.slotNumber == 2 }.teamName)
    }

    @Test
    fun savePersistsEditedNamesAndTrimsWhitespace() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(slotNumber = 1, teamName = "  Alpha  ")
        viewModel.onTeamNameChanged(slotNumber = 2, teamName = "")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        val savedSlots = repository.observeSlotsByTournamentId("stable-id").first()
        assertEquals("Alpha", savedSlots.first { it.slotNumber == 1 }.teamName)
        assertEquals("", savedSlots.first { it.slotNumber == 2 }.teamName)
        assertEquals("Alpha", viewModel.uiState.value.slots.first { it.slotNumber == 1 }.teamName)
    }

    @Test
    fun reopeningTeamEntryShowsSavedNames() = runTest {
        repository.create(tournament())
        val firstViewModel = viewModel()
        firstViewModel.load("stable-id")
        advanceUntilIdle()
        firstViewModel.onTeamNameChanged(slotNumber = 3, teamName = "Charlie")
        firstViewModel.saveTeamNames()
        advanceUntilIdle()

        val reopenedViewModel = viewModel()
        reopenedViewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals("Charlie", reopenedViewModel.uiState.value.slots.first { it.slotNumber == 3 }.teamName)
    }

    @Test
    fun unknownTournamentRendersNotFoundState() = runTest {
        val viewModel = viewModel()

        viewModel.load("missing")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
    }

    @Test
    fun duplicateTeamNamesBlockSavingButMissingNamesAndCountsDoNot() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(1, " Alpha ")
        viewModel.onTeamNameChanged(2, "Alpha")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.validationIssues.none { it.isBlocking })
        assertEquals("", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
        assertEquals("", repository.observeSlotsByTournamentId("stable-id").first()[1].teamName)
    }

    private fun viewModel() = TeamEntryViewModel(
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
    )

    private fun tournament() = Tournament(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )
}
