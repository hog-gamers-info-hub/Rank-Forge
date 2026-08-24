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
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.PlacementValidationError
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository
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
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        repository.saveTeamNames(
            "tournament-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        matchId = (CreateMatchUseCase(repository, SignedInTournamentTestAuthRepository())(
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
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
            saveMatchPlacements = SaveMatchPlacementsUseCase(repository, SignedInTournamentTestAuthRepository()),
            saveDraftValue = SaveMatchDraftValueUseCase(repository, SignedInTournamentTestAuthRepository()),
            clearDraftMatch = ClearDraftMatchUseCase(repository, SignedInTournamentTestAuthRepository()),
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
        assertNull(viewModel.uiState.value.navigation)
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

        val navigation = viewModel.uiState.value.navigation
        assertTrue(navigation is MatchPlacementNavigation.Saved)
        assertEquals("tournament-id", (navigation as MatchPlacementNavigation.Saved).tournamentId)
        assertEquals(matchId, navigation.matchId)
        assertEquals(
            listOf(MatchPlacement(1, 1), MatchPlacement(2, 2)),
            repository.observeMatchById(matchId).first()?.placements,
        )
    }

    @Test
    fun successfulSaveNavigationCanBeConsumed() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPlacementChanged(1, "1")
        viewModel.onPlacementChanged(2, "2")

        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigation is MatchPlacementNavigation.Saved)

        viewModel.onNavigationHandled()

        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun draftInputRestoresWhenTheEntryViewModelIsRecreated() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPlacementChanged(1, "7")
        advanceUntilIdle()

        val recreated = MatchPlacementViewModel(
            observeMatches = ObserveMatchesUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
            saveMatchPlacements = SaveMatchPlacementsUseCase(repository, SignedInTournamentTestAuthRepository()),
            saveDraftValue = SaveMatchDraftValueUseCase(repository, SignedInTournamentTestAuthRepository()),
            clearDraftMatch = ClearDraftMatchUseCase(repository, SignedInTournamentTestAuthRepository()),
        )
        recreated.load("tournament-id", matchId)
        advanceUntilIdle()

        assertEquals("7", recreated.uiState.value.rows.first().placementInput)
        recreated.resetDraft()
        advanceUntilIdle()
        assertEquals("", recreated.uiState.value.rows.first().placementInput)
    }

    @Test
    fun latestPlacementValueIsPersistedBeforeBackNavigation() = runTest {
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPlacementChanged(1, "1")
        viewModel.onPlacementChanged(1, "12")
        viewModel.onBackPressed()
        advanceUntilIdle()

        assertEquals(
            MatchDraftFieldValues("12", ""),
            repository.observeDraftMatchValues("tournament-id", matchId).first()[1],
        )
        assertEquals(MatchPlacementNavigation.Back, viewModel.uiState.value.navigation)
    }

    @Test
    fun confirmedRosterPlayerNamesAreLoadedIntoRows() = runTest {
        repository.saveRoster(
            tournamentId = "tournament-id",
            slotNumber = 1,
            players = listOf(
                com.hoggamers.rankforge.domain.tournament.RosterPlayer("tournament-id", 1, "Player One"),
            ),
        )
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertEquals(listOf("Player One"), viewModel.uiState.value.rows.first().playerNames)
    }

    @Test
    fun missingMatchIsHandledAsNotFound() = runTest {
        viewModel.load("tournament-id", "missing")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAvailable)
        assertTrue(viewModel.uiState.value.isNotFound)
    }

    @Test
    fun finalizedMatchLoadsPlacementsReadOnlyWithoutMutation() = runTest {
        repository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { slotNumber -> MatchPlacement(slotNumber, slotNumber) },
            kills = (1..12).map { slotNumber -> com.hoggamers.rankforge.domain.tournament.MatchKill(slotNumber, 0) },
        )

        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAvailable)
        assertTrue(viewModel.uiState.value.isReadOnly)
        assertEquals("1", viewModel.uiState.value.rows.first().placementInput)
        viewModel.onPlacementChanged(1, "9")
        viewModel.save()
        viewModel.resetDraft()
        advanceUntilIdle()

        assertEquals(1, repository.observeMatchById(matchId).first()?.placements?.first()?.position)
    }
}
