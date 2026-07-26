package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentStandingsViewModelTest {
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
    fun finalizedMatchesProduceDerivedStandingsAndDraftMatchesAreExcluded() = runTest {
        repository.create(tournament())
        repository.createDraftMatch(match("finalized", 1))
        repository.createDraftMatch(match("draft", 2))
        repository.finalizeDraftMatch(
            matchId = "finalized",
            placements = (1..12).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..12).map { slot -> MatchKill(slot, if (slot == 2) 10 else 0) },
        )

        val viewModel = standingsViewModel()
        viewModel.load("tournament-id")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(!state.isLoading)
        assertEquals(12, state.rows.size)
        assertEquals(2, state.rows.first().teamSlotNumber)
        assertEquals(19, state.rows.first().totalPoints)
        assertEquals(9, state.rows.first().totalPositionPoints)
        assertEquals(10, state.rows.first().totalKillPoints)
        assertEquals(0, state.rows.first().firstPlaceFinishes)
        assertEquals(2, state.rows.first().latestMatchPlacement)
        assertEquals(1, state.rows.first().matchesIncluded)
        assertEquals(12, state.rows.first { it.teamSlotNumber == 1 }.totalPoints)
        assertTrue(state.rows.all { it.matchesIncluded == 1 })
    }

    @Test
    fun noFinalizedMatchesProduceEmptyStandings() = runTest {
        repository.create(tournament())
        repository.createDraftMatch(match("draft", 1))

        val viewModel = standingsViewModel()
        viewModel.load("tournament-id")
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.rows.isEmpty())
    }

    private fun standingsViewModel() = TournamentStandingsViewModel(
        observeMatches = ObserveMatchesUseCase(repository),
        cumulativeStandings = CumulativeTournamentStandingsEngine(),
        tieBreakRules = TieBreakRules(),
    )

    private fun tournament() = Tournament(
        id = "tournament-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )

    private fun match(id: String, matchNumber: Int) = Match(
        id = id,
        tournamentId = "tournament-id",
        matchNumber = matchNumber,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )
}
