package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
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
import org.junit.Assert.assertNull
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
        repository.saveTeamNames(
            "tournament-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
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
    fun tenTeamFinalizedMatchProducesTenRowsWithoutInactivePlaceholders() = runTest {
        repository.create(tournament())
        repository.saveTeamNames(
            "tournament-id",
            (1..10).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.createDraftMatch(match("ten-team-finalized", 1))
        repository.createDraftMatch(match("draft", 2))
        repository.finalizeDraftMatch(
            matchId = "ten-team-finalized",
            placements = (1..10).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..10).map { slot -> MatchKill(slot, if (slot == 2) 5 else 0) },
        )

        val viewModel = standingsViewModel()
        viewModel.load("tournament-id")
        advanceUntilIdle()

        val rows = viewModel.uiState.value.rows
        assertEquals(10, rows.size)
        assertEquals((1..10).toSet(), rows.map { it.teamSlotNumber }.toSet())
        assertTrue(rows.none { it.teamSlotNumber > 10 })
        assertEquals(14, rows.first { it.teamSlotNumber == 2 }.totalPoints)
        assertEquals("Team 2", rows.first().teamName)
        assertEquals("Team 1", rows.first { it.teamSlotNumber == 1 }.teamName)
        assertTrue(rows.all { it.matchesIncluded == 1 })
    }

    @Test
    fun blankTeamNamesRemainNullInPresentationRows() = runTest {
        repository.create(tournament())
        repository.saveTeamNames(
            "tournament-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.createDraftMatch(match("finalized", 1))
        repository.finalizeDraftMatch(
            matchId = "finalized",
            placements = (1..12).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..12).map { slot -> MatchKill(slot, 0) },
        )
        repository.saveTeamNames("tournament-id", mapOf(2 to "   ", 3 to "\t"))

        val viewModel = standingsViewModel()
        viewModel.load("tournament-id")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.rows.first { it.teamSlotNumber == 2 }.teamName)
        assertNull(viewModel.uiState.value.rows.first { it.teamSlotNumber == 3 }.teamName)
    }

    @Test
    fun savedTeamNameChangesUpdateWithoutMatchChanges() = runTest {
        repository.create(tournament())
        repository.saveTeamNames(
            "tournament-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.createDraftMatch(match("finalized", 1))
        repository.finalizeDraftMatch(
            matchId = "finalized",
            placements = (1..12).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..12).map { slot -> MatchKill(slot, 0) },
        )
        repository.saveTeamNames("tournament-id", mapOf(2 to ""))

        val viewModel = standingsViewModel()
        viewModel.load("tournament-id")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.rows.first { it.teamSlotNumber == 2 }.teamName)

        repository.saveTeamNames("tournament-id", mapOf(2 to "Updated Team"))
        advanceUntilIdle()

        assertEquals(
            "Updated Team",
            viewModel.uiState.value.rows.first { it.teamSlotNumber == 2 }.teamName,
        )
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
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
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
