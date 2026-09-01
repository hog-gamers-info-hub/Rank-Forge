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
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class RosterReviewViewModelTest {
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
    fun reviewExposesAllTwelveTeamsAndPlayersInOrder() = runTest {
        createValidRoster()
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals((1..12).toList(), viewModel.uiState.value.teams.map { it.slotNumber })
        assertEquals((1..12).map { "Team $it" }, viewModel.uiState.value.teams.map { it.teamName })
        assertEquals(listOf("Player 0", "Player 1", "Player 2", "Player 3"), viewModel.uiState.value.teams[2].players.map { it.displayName })
    }

    @Test
    fun invalidRosterCannotConfirm() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canConfirm)
        viewModel.confirmRoster()
        advanceUntilIdle()

        assertEquals(TournamentStatus.DRAFT, repository.observeById("stable-id").first()?.status)
        assertEquals(null, viewModel.uiState.value.navigation)
    }

    @Test
    fun validRosterConfirmsOnceAndNavigatesToTournamentDetails() = runTest {
        createValidRoster()
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canConfirm)
        viewModel.confirmRoster()
        advanceUntilIdle()
        viewModel.confirmRoster()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isConfirmed)
        assertFalse(viewModel.uiState.value.canConfirm)
        assertEquals(TournamentStatus.CONFIRMED, repository.observeById("stable-id").first()?.status)
        val navigation = viewModel.uiState.value.navigation
        assertTrue(navigation is RosterReviewNavigation.TournamentDetails)
        assertEquals("stable-id", (navigation as RosterReviewNavigation.TournamentDetails).tournamentId)
    }

    @Test
    fun successfulConfirmationNavigationCanBeConsumed() = runTest {
        createValidRoster()
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.confirmRoster()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigation is RosterReviewNavigation.TournamentDetails)

        viewModel.onNavigationHandled()

        assertEquals(null, viewModel.uiState.value.navigation)
    }

    @Test
    fun savedTeamOrPlayerEditInvalidatesConfirmation() = runTest {
        createValidRoster()
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.confirmRoster()
        advanceUntilIdle()

        repository.saveTeamNames("stable-id", mapOf(1 to "Renamed Team"))
        advanceUntilIdle()
        assertEquals(TournamentStatus.DRAFT, repository.observeById("stable-id").first()?.status)

        repository.confirmTournament("stable-id")
        repository.saveRoster(
            tournamentId = "stable-id",
            slotNumber = 1,
            players = listOf(RosterPlayer.create("stable-id", 1, "Changed")),
        )
        assertEquals(TournamentStatus.DRAFT, repository.observeById("stable-id").first()?.status)
    }

    private fun viewModel() = RosterReviewViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        confirmTournamentRoster = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
            authRepository = SignedInTournamentTestAuthRepository(),
        ),
    )

    private suspend fun createValidRoster() {
        repository.create(tournament())
        SaveTeamSlotNamesUseCase(repository, SignedInTournamentTestAuthRepository())(
            "stable-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        val saveRoster = SaveRosterUseCase(repository, SignedInTournamentTestAuthRepository())
        (1..12).forEach { slotNumber ->
            saveRoster(
                tournamentId = "stable-id",
                slotNumber = slotNumber,
                players = (0..3).map { playerIndex ->
                    RosterPlayer.create("stable-id", slotNumber, "Player $playerIndex")
                },
            )
        }
    }

    private fun tournament() = Tournament(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
    )
}
