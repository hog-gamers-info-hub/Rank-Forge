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
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.MatchField
import com.hoggamers.rankforge.domain.tournament.MatchValidationError
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class MatchCreationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var viewModel: MatchCreationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryTournamentRepository()
        viewModel = MatchCreationViewModel(CreateMatchUseCase(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun inputUpdatesAreReflectedInUiState() {
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("3")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        assertEquals("3", viewModel.uiState.value.matchNumber)
        assertEquals(LocalDate.of(2026, 7, 24), viewModel.uiState.value.matchDate)
        assertEquals("Bermuda", viewModel.uiState.value.mapName)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun invalidSubmitSurfacesValidationErrors() = runTest {
        repository.create(tournament(TournamentStatus.CONFIRMED))
        viewModel.load("stable-id")

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(MatchValidationError.REQUIRED, viewModel.uiState.value.validationErrors[MatchField.MATCH_NUMBER])
        assertEquals(MatchValidationError.REQUIRED, viewModel.uiState.value.validationErrors[MatchField.DATE])
        assertEquals(MatchValidationError.REQUIRED, viewModel.uiState.value.validationErrors[MatchField.MAP])
    }

    @Test
    fun successfulCreationRecordsNavigationCompletion() = runTest {
        repository.create(tournament(TournamentStatus.CONFIRMED))
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("1")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(MatchCreationNavigation.CREATED, viewModel.uiState.value.navigation)
        val createdMatch = repository.observeMatchesByTournamentId("stable-id").first().single()
        assertEquals(LocalDate.of(2026, 7, 24), createdMatch.date)
    }

    @Test
    fun unconfirmedTournamentIsHandledSafely() = runTest {
        repository.create(tournament(TournamentStatus.DRAFT))
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("1")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            MatchValidationError.TOURNAMENT_NOT_CONFIRMED,
            viewModel.uiState.value.validationErrors[MatchField.TOURNAMENT],
        )
        assertEquals(null, viewModel.uiState.value.navigation)
    }

    @Test
    fun limitReachedStateBlocksCreation() = runTest {
        repository.create(tournament(TournamentStatus.CONFIRMED))
        val createMatch = CreateMatchUseCase(repository)
        (1..10).forEach { number ->
            createMatch(
                com.hoggamers.rankforge.domain.tournament.CreateMatchInput(
                    tournamentId = "stable-id",
                    matchNumber = number.toString(),
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("11")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(MatchValidationError.LIMIT_REACHED, viewModel.uiState.value.validationErrors[MatchField.TOURNAMENT])
        assertEquals(null, viewModel.uiState.value.navigation)
    }

    private fun tournament(status: TournamentStatus) = Tournament(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = status,
    )
}
