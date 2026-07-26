package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftUseCase
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MatchCorrectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository

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
        repository.createDraftMatch(
            com.hoggamers.rankforge.domain.tournament.Match(
                id = "match-1",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        repository.finalizeDraftMatch(
            "match-1",
            (1..12).map { MatchPlacement(it, it) },
            (1..12).map { MatchKill(it, it - 1) },
        )
        Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun correctionStateLoadsFinalizedValues() = runTest {
        val viewModel = viewModel()
        viewModel.load("tournament-id", "match-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAvailable)
        assertEquals("1", viewModel.uiState.value.rows.first().previousPlacement)
        assertEquals("0", viewModel.uiState.value.rows.first().previousKills)
        assertEquals("12", viewModel.uiState.value.rows.last().placementInput)
    }

    @Test
    fun validCorrectionSubmissionNavigatesBackAndPreservesHistory() = runTest {
        (1..12).forEach { slot ->
            repository.saveDraftMatchValue(
                "tournament-id",
                "match-1",
                slot,
                placementInput = when (slot) {
                    1 -> "2"
                    2 -> "1"
                    else -> slot.toString()
                },
                killsInput = if (slot == 1) "1" else (slot - 1).toString(),
            )
        }
        val viewModel = viewModel()
        viewModel.load("tournament-id", "match-1")
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(MatchCorrectionNavigation.REVIEW, viewModel.uiState.value.navigation)
        assertEquals(1, repository.observeMatchById("match-1").first()!!.correctionHistory.size)
    }

    @Test
    fun invalidCorrectionBlocksSubmission() = runTest {
        repository.saveDraftMatchValue("tournament-id", "match-1", 1, "", "0")
        val viewModel = viewModel()
        viewModel.load("tournament-id", "match-1")
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(null, viewModel.uiState.value.navigation)
        assertTrue(repository.observeMatchById("match-1").first()!!.correctionHistory.isEmpty())
    }

    @Test
    fun discardClearsCorrectionDraftAndReturnsToReview() = runTest {
        repository.saveDraftMatchValue("tournament-id", "match-1", 1, "12", "99")
        val viewModel = viewModel()
        viewModel.load("tournament-id", "match-1")
        advanceUntilIdle()

        viewModel.discard()
        advanceUntilIdle()

        assertEquals(MatchCorrectionNavigation.REVIEW, viewModel.uiState.value.navigation)
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-1").first().isEmpty())
        assertEquals(1, repository.observeMatchById("match-1").first()!!.placements.first().position)
    }

    private fun viewModel() = MatchCorrectionViewModel(
        observeMatches = ObserveMatchesUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
        validateMatchResult = ValidateMatchResultUseCase(),
        submitCorrection = SubmitMatchCorrectionUseCase(repository, ValidateMatchResultUseCase()),
        saveDraftValue = SaveMatchDraftValueUseCase(repository),
        clearCorrectionDraft = ClearMatchCorrectionDraftUseCase(repository),
    )
}
