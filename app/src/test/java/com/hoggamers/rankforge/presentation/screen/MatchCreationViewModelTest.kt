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
import org.junit.Assert.assertNull
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
        createReadyTournament(TournamentStatus.CONFIRMED)
        viewModel.load("stable-id")

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(MatchValidationError.REQUIRED, viewModel.uiState.value.validationErrors[MatchField.MATCH_NUMBER])
        assertEquals(MatchValidationError.REQUIRED, viewModel.uiState.value.validationErrors[MatchField.DATE])
        assertEquals(MatchValidationError.REQUIRED, viewModel.uiState.value.validationErrors[MatchField.MAP])
        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun successfulCreationRecordsNavigationWithCreatedMatchId() = runTest {
        createReadyTournament(TournamentStatus.CONFIRMED)
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("1")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        viewModel.submit()
        advanceUntilIdle()

        val createdMatch = repository.observeMatchesByTournamentId("stable-id").first().single()
        val navigation = viewModel.uiState.value.navigation
        assertTrue(navigation is MatchCreationNavigation.Created)
        assertEquals("stable-id", (navigation as MatchCreationNavigation.Created).tournamentId)
        assertEquals(createdMatch.id, navigation.matchId)
        assertEquals(LocalDate.of(2026, 7, 24), createdMatch.date)
    }

    @Test
    fun successfulCreationNavigationCanBeConsumed() = runTest {
        createReadyTournament(TournamentStatus.CONFIRMED)
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("1")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigation is MatchCreationNavigation.Created)

        viewModel.onNavigationHandled()

        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun unconfirmedTournamentCanStartMatchCreation() = runTest {
        createReadyTournament(TournamentStatus.DRAFT)
        viewModel.load("stable-id")
        viewModel.onMatchNumberChanged("1")
        viewModel.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onMapNameChanged("Bermuda")

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.navigation is MatchCreationNavigation.Created)
        assertTrue(viewModel.uiState.value.validationErrors.isEmpty())
    }

    @Test
    fun limitReachedStateBlocksCreation() = runTest {
        createReadyTournament(TournamentStatus.CONFIRMED)
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

    private suspend fun createReadyTournament(status: TournamentStatus) {
        repository.create(tournament(status))
        repository.saveTeamNames("stable-id", mapOf(1 to "Team 1"))
    }
}
