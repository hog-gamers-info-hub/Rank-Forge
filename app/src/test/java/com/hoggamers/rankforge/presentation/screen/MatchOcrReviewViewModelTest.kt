package com.hoggamers.rankforge.presentation.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchOcrReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadInitializesEmptyStateFromRouteArguments() {
        val viewModel = MatchOcrReviewViewModel()

        viewModel.load("synthetic-tournament", "synthetic-match")

        val state = viewModel.uiState.value
        assertTrue(state is MatchOcrReviewUiState.Empty)
        state as MatchOcrReviewUiState.Empty
        assertEquals("synthetic-tournament", state.tournamentId)
        assertEquals("synthetic-match", state.matchId)
    }

    @Test
    fun initialStateIsLoadingBeforeRouteArgumentsAreLoaded() {
        val viewModel = MatchOcrReviewViewModel()

        assertEquals(MatchOcrReviewUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun repeatedLoadForSameRouteKeepsDeterministicEmptyState() {
        val viewModel = MatchOcrReviewViewModel()

        viewModel.load("synthetic-tournament", "synthetic-match")
        val firstState = viewModel.uiState.value
        viewModel.load("synthetic-tournament", "synthetic-match")

        assertEquals(firstState, viewModel.uiState.value)
    }

    @Test
    fun viewModelExposesOnlyInMemoryCorrectionActionsWithoutSaveFinalizationOrAssignmentActions() {
        val publicMethodNames = MatchOcrReviewViewModel::class.java.methods.map { it.name }.toSet()

        assertFalse(publicMethodNames.contains("finalize"))
        assertFalse(publicMethodNames.contains("save"))
        assertFalse(publicMethodNames.contains("assign"))
        assertFalse(publicMethodNames.contains("openCorrection"))
        assertFalse(publicMethodNames.contains("runOcr"))
        assertTrue(publicMethodNames.contains("load"))
        assertTrue(publicMethodNames.contains("onPlacementChanged"))
        assertTrue(publicMethodNames.contains("onKillsChanged"))
        assertTrue(publicMethodNames.contains("onAssignedTeamSlotChanged"))
        assertTrue(publicMethodNames.contains("onResetRowCorrection"))
        assertTrue(publicMethodNames.contains("onResetAllCorrections"))
    }
}
