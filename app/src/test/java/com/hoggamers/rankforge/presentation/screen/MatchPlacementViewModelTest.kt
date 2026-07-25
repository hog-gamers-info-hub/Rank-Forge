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
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.PlacementValidationError
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class MatchPlacementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var viewModel: MatchPlacementViewModel
    private lateinit var matchId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryTournamentRepository()
        repository.create(
            Tournament(
                id = "tournament-id",
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        matchId = (CreateMatchUseCase(repository)(
            CreateMatchInput(
                tournamentId = "tournament-id",
                matchNumber = "1",
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
        viewModel = MatchPlacementViewModel(
            observeMatches = ObserveMatchesUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            saveMatchPlacements = SaveMatchPlacementsUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadingDraftExposesTwelveEditableTeamRows() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAvailable)
        assertEquals((1..12).toList(), viewModel.uiState.value.rows.map { it.teamSlotNumber })
        assertTrue(viewModel.uiState.value.rows.all { it.placementInput.isEmpty() })
    }

    @Test
    fun inputUpdatesAreReflectedAndInvalidPlacementIsSurfaced() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPlacementChanged(1, "13")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("13", viewModel.uiState.value.rows.first().placementInput)
        assertEquals(PlacementValidationError.INVALID, viewModel.uiState.value.validationErrors[1])
        assertEquals(null, viewModel.uiState.value.navigation)
    }

    @Test
    fun duplicatePlacementIsSurfacedForBothRows() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPlacementChanged(1, "1")
        viewModel.onPlacementChanged(2, "1")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(PlacementValidationError.DUPLICATE, viewModel.uiState.value.validationErrors[1])
        assertEquals(PlacementValidationError.DUPLICATE, viewModel.uiState.value.validationErrors[2])
    }

    @Test
    fun successfulSaveRecordsNavigationAndUpdatesDraftMatch() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPlacementChanged(1, "1")
        viewModel.onPlacementChanged(2, "2")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(MatchPlacementNavigation.SAVED, viewModel.uiState.value.navigation)
        assertEquals(
            listOf(MatchPlacement(1, 1), MatchPlacement(2, 2)),
            repository.observeMatchById(matchId).first()?.placements,
        )
    }

    @Test
    fun missingMatchIsHandledAsNotFound() = runTest {
        viewModel.load("tournament-id", "missing")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAvailable)
        assertTrue(viewModel.uiState.value.isNotFound)
    }
}
