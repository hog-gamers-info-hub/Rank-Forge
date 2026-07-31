package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchOcrReviewViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

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
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.load("synthetic-tournament", "synthetic-match")

        val state = viewModel.uiState.value
        assertTrue(state is MatchOcrReviewUiState.Empty)
        state as MatchOcrReviewUiState.Empty
        assertEquals("synthetic-tournament", state.tournamentId)
        assertEquals("synthetic-match", state.matchId)
    }

    @Test
    fun initialStateIsLoadingBeforeRouteArgumentsAreLoaded() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        assertEquals(MatchOcrReviewUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun repeatedLoadForSameRouteKeepsDeterministicEmptyState() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.load("synthetic-tournament", "synthetic-match")
        val firstState = viewModel.uiState.value
        viewModel.load("synthetic-tournament", "synthetic-match")

        assertEquals(firstState, viewModel.uiState.value)
    }

    @Test
    fun finalizeUnavailableWhenNoCorrectionDraftExists() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = viewModelWith(repository, readyState(correctionDraft = null))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT, ready.finalization.error)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun finalizeBlockedWhenCorrectionDraftHasBlockers() = runTest(dispatcher) {
        val repository = createRepository()
        val blockedDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, 0, "")
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = blockedDraft))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED, ready.finalization.error)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun warningsRequireConfirmationBeforeFinalization() = runTest(dispatcher) {
        val repository = createRepository()
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = warningDraft))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.showWarningConfirmation)
        assertFalse(ready.finalization.isFinalized)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun warningConfirmationCallsSafeFinalizationUseCase() = runTest(dispatcher) {
        val repository = createRepository()
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = warningDraft))

        viewModel.onFinalizeOcrCorrection()
        viewModel.onConfirmFinalizeWarnings()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertFalse(ready.finalization.showWarningConfirmation)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun successStateAfterValidFinalization() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = viewModelWith(repository, readyState(correctionDraft = correctionDraft()))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(null, ready.finalization.error)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun deterministicErrorStateOnFinalizationFailure() = runTest(dispatcher) {
        val repository = InMemoryTournamentRepository()
        val viewModel = viewModelWith(repository, readyState(correctionDraft = correctionDraft()))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(MatchOcrReviewFinalizationError.MISSING_TOURNAMENT, ready.finalization.error)
        assertFalse(ready.finalization.isFinalized)
    }

    @Test
    fun repeatedFinalizeAfterSuccessIsIdempotentlyIgnoredByViewModel() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = viewModelWith(repository, readyState(correctionDraft = correctionDraft()))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()
        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(null, ready.finalization.error)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun dismissFinalizeWarningsHidesConfirmationWithoutFinalizing() = runTest(dispatcher) {
        val repository = createRepository()
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = warningDraft))

        viewModel.onFinalizeOcrCorrection()
        viewModel.onDismissFinalizeWarnings()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertFalse(ready.finalization.showWarningConfirmation)
        assertFalse(ready.finalization.isFinalized)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun viewModelExposesOnlyApprovedCorrectionAndFinalizationActions() {
        val publicMethodNames = MatchOcrReviewViewModel::class.java.methods.map { it.name }.toSet()

        assertFalse(publicMethodNames.contains("save"))
        assertFalse(publicMethodNames.contains("export"))
        assertFalse(publicMethodNames.contains("sync"))
        assertFalse(publicMethodNames.contains("assign"))
        assertFalse(publicMethodNames.contains("openCorrection"))
        assertFalse(publicMethodNames.contains("runOcr"))
        assertFalse(publicMethodNames.contains("retryOcr"))
        assertFalse(publicMethodNames.contains("editRoster"))
        assertTrue(publicMethodNames.contains("load"))
        assertTrue(publicMethodNames.contains("onPlacementChanged"))
        assertTrue(publicMethodNames.contains("onKillsChanged"))
        assertTrue(publicMethodNames.contains("onAssignedTeamSlotChanged"))
        assertTrue(publicMethodNames.contains("onResetRowCorrection"))
        assertTrue(publicMethodNames.contains("onResetAllCorrections"))
        assertTrue(publicMethodNames.contains("onFinalizeOcrCorrection"))
        assertTrue(publicMethodNames.contains("onConfirmFinalizeWarnings"))
        assertTrue(publicMethodNames.contains("onDismissFinalizeWarnings"))
    }

    @Test
    fun viewModelDoesNotExposeScoringOrStandingsMutationActions() {
        val declaredMethodNames = MatchOcrReviewViewModel::class.java.declaredMethods.map { it.name }

        assertTrue(declaredMethodNames.none { it.contains("score", ignoreCase = true) })
        assertTrue(declaredMethodNames.none { it.contains("standing", ignoreCase = true) })
    }

    private fun viewModelWith(
        repository: InMemoryTournamentRepository,
        initialUiState: MatchOcrReviewUiState,
    ): MatchOcrReviewViewModel =
        MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            initialUiState = initialUiState,
        )

    private fun createFinalizeUseCase(repository: InMemoryTournamentRepository): FinalizeOcrCorrectionMatchUseCase =
        FinalizeOcrCorrectionMatchUseCase(
            repository = repository,
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        )

    private suspend fun createRepository(): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Synthetic Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.createDraftMatch(
            Match(
                id = MATCH_ID,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        return repository
    }

    private fun readyState(
        correctionDraft: MatchOcrReviewCorrectionDraft? = correctionDraft(),
    ): MatchOcrReviewUiState.Ready = MatchOcrReviewUiState.Ready(
        tournamentId = TOURNAMENT_ID,
        matchId = MATCH_ID,
        matchDisplayLabel = "Synthetic Match",
        rowCount = 12,
        rows = correctionRows(),
        blockerCount = 0,
        warningCount = 0,
        safeRowCount = 12,
        manualRequiredRowCount = 0,
        reviewRequiredRowCount = 0,
        manualReviewRequired = false,
        hasUnavailableEvidence = false,
        correctionDraft = correctionDraft,
    )

    private fun correctionDraft(
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft = { it },
    ): MatchOcrReviewCorrectionDraft =
        transform(MatchOcrReviewCorrectionDraftReducer.createInitialDraft(correctionRows()))

    private fun correctionRows(): List<MatchOcrReviewRowUiState> =
        (0 until TeamSlot.MAX_SLOT_NUMBER).map { rowIndex ->
            MatchOcrReviewRowUiState(
                rowIndex = rowIndex,
                expectedPlacementLabel = (rowIndex + 1).toString(),
                detectedPlacementDisplayValue = (rowIndex + 1).toString(),
                placementStatusLabel = "Accepted",
                detectedKillDisplayValue = rowIndex.toString(),
                killStatusLabel = "Accepted",
                detectedPlayerNameEvidenceLabel = "Synthetic Unit ${rowIndex + 1}",
                playerNameStatusLabel = "Accepted",
                suggestedTeamSlotDisplayValue = (rowIndex + 1).toString(),
                confidenceScoreDisplayValue = "96",
                confidenceTierLabel = "Automatic candidate",
                assignmentSafetyStatusLabel = "Safe automatic assignment",
                topThreeSuggestionsSummary = listOf(
                    "Rank 1: Slot ${rowIndex + 1}, confidence 96, matches 4, coverage 100",
                ),
                warningLabels = emptyList(),
                blockerLabels = emptyList(),
                severity = MatchOcrReviewSeverity.INFORMATIONAL,
                originalParsedPlacementValue = rowIndex + 1,
                originalParsedKillValue = rowIndex,
                originalSuggestedTeamSlot = rowIndex + 1,
            )
        }

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
        const val MATCH_ID = "synthetic-match"
    }
}
