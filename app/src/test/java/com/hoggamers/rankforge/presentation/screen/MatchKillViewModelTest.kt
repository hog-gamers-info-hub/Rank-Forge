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
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.KillValidationError
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

@OptIn(ExperimentalCoroutinesApi::class)
class MatchKillViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var viewModel: MatchKillViewModel
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
        viewModel = MatchKillViewModel(
            observeMatches = ObserveMatchesUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
            saveMatchKills = SaveMatchKillsUseCase(repository),
            saveDraftValue = SaveMatchDraftValueUseCase(repository),
            clearDraftMatch = ClearDraftMatchUseCase(repository),
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
        assertTrue(viewModel.uiState.value.rows.all { it.killsInput.isEmpty() })
    }

    @Test
    fun negativeKillsAreSurfacedAsInvalid() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onKillsChanged(1, "-1")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("-1", viewModel.uiState.value.rows.first().killsInput)
        assertEquals(KillValidationError.INVALID, viewModel.uiState.value.validationErrors[1])
        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun successfulSaveRecordsNavigationAndUpdatesDraftMatch() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onKillsChanged(1, "0")
        viewModel.onKillsChanged(2, "7")
        viewModel.save()
        advanceUntilIdle()

        val navigation = viewModel.uiState.value.navigation
        assertTrue(navigation is MatchKillNavigation.Saved)
        assertEquals("tournament-id", (navigation as MatchKillNavigation.Saved).tournamentId)
        assertEquals(matchId, navigation.matchId)
        assertEquals(
            listOf(MatchKill(1, 0), MatchKill(2, 7)),
            repository.observeMatchById(matchId).first()?.kills,
        )
    }

    @Test
    fun successfulSaveNavigationCanBeConsumed() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onKillsChanged(1, "0")
        viewModel.onKillsChanged(2, "7")

        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigation is MatchKillNavigation.Saved)

        viewModel.onNavigationHandled()

        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun draftKillInputRestoresWhenTheEntryViewModelIsRecreated() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onKillsChanged(1, "8")
        advanceUntilIdle()

        val recreated = MatchKillViewModel(
            observeMatches = ObserveMatchesUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
            saveMatchKills = SaveMatchKillsUseCase(repository),
            saveDraftValue = SaveMatchDraftValueUseCase(repository),
            clearDraftMatch = ClearDraftMatchUseCase(repository),
        )
        recreated.load("tournament-id", matchId)
        advanceUntilIdle()

        assertEquals("8", recreated.uiState.value.rows.first().killsInput)
        recreated.resetDraft()
        advanceUntilIdle()
        assertEquals("", recreated.uiState.value.rows.first().killsInput)
    }

    @Test
    fun latestKillValueIsPersistedBeforeBackNavigation() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onKillsChanged(1, "1")
        viewModel.onKillsChanged(1, "12")
        viewModel.onBackPressed()
        advanceUntilIdle()

        assertEquals(
            MatchDraftFieldValues("", "12"),
            repository.observeDraftMatchValues("tournament-id", matchId).first()[1],
        )
        assertEquals(MatchKillNavigation.Back, viewModel.uiState.value.navigation)
    }

    @Test
    fun missingMatchIsHandledAsNotFound() = runTest {
        viewModel.load("tournament-id", "missing")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAvailable)
        assertTrue(viewModel.uiState.value.isNotFound)
    }
}
