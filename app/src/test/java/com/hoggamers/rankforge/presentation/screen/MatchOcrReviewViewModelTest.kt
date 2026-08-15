package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.matching.RowTeamAssignmentSafetyResult
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamCandidateScore
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceAssessment
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceReason
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestion
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestions
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
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
    fun loadingEmptyOcrStateDoesNotMutateMatchData() = runTest(dispatcher) {
        val repository = createRepository()
        val beforeMatch = repository.observeMatchById(MATCH_ID).first()
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(repository))

        viewModel.load(TOURNAMENT_ID, MATCH_ID)

        val state = viewModel.uiState.value
        assertTrue(state is MatchOcrReviewUiState.Empty)
        state as MatchOcrReviewUiState.Empty
        assertEquals(TOURNAMENT_ID, state.tournamentId)
        assertEquals(MATCH_ID, state.matchId)
        assertEquals(beforeMatch, repository.observeMatchById(MATCH_ID).first())
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
    fun loadDisplayInputPreservesExactIdsAndSurfacesMatchingEvidence() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.loadDisplayInput(displayInputWithMatchingEvidence())

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val firstRow = state.rows.first()
        assertEquals(TOURNAMENT_ID, state.tournamentId)
        assertEquals(MATCH_ID, state.matchId)
        assertEquals("1", firstRow.suggestedTeamSlotDisplayValue)
        assertEquals("96", firstRow.confidenceScoreDisplayValue)
        assertEquals("Automatic candidate", firstRow.confidenceTierLabel)
        assertEquals("Safe automatic assignment", firstRow.assignmentSafetyStatusLabel)
        assertEquals(
            listOf("Rank 1: Slot 1, confidence 96, matches 4, coverage 100"),
            firstRow.topThreeSuggestionsSummary,
        )
    }

    @Test
    fun loadDisplayInputWithMissingMatchingEvidenceRequiresManualReview() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.loadDisplayInput(displayInputWithoutMatchingEvidence())

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(state.manualReviewRequired)
        assertTrue(state.hasUnavailableEvidence)
        assertTrue(state.rows.all { it.blockerLabels.isNotEmpty() })
        assertTrue(state.rows.all { it.topThreeSuggestionsSummary == listOf("No suggestions") })
    }

    @Test
    fun loadDisplayInputWithNoRowsKeepsEmptyStateWithoutFakeMatchingResults() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.loadDisplayInput(
            MatchOcrReviewDisplayInput(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
                rows = emptyList(),
            ),
        )

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Empty
        assertEquals(TOURNAMENT_ID, state.tournamentId)
        assertEquals(MATCH_ID, state.matchId)
    }

    @Test
    fun loadSurfacesPersistedTeamNamesWithoutChangingMatchingResult() = runTest(dispatcher) {
        val repository = createRepository()
        repository.saveTeamNames(TOURNAMENT_ID, mapOf(5 to "ETR ESPORTS"))
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = completePreviewRunner(),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals("ETR ESPORTS", state.teamNamesBySlot[5])
        assertEquals(12, state.rows.size)
        assertTrue(state.rows.all { it.suggestedTeamSlotDisplayValue == "Unavailable" })
    }

    @Test
    fun lobbyPlayersCoexistWithResultPreviewAndUsePersistedTeamContext() = runTest(dispatcher) {
        val repository = createRepository()
        repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to "ABC ESPORTS"))
        val lobbyRunner = MatchLobbyPlayersOcrRunner { _, _ ->
            MatchLobbyPlayersOcrResult(
                slots = listOf(
                    MatchLobbyPlayersOcrSlot(
                        slotNumber = 1,
                        players = listOf(
                            MatchLobbyPlayersOcrPlayer(1, "Lobby Player"),
                            MatchLobbyPlayersOcrPlayer(2, null),
                            MatchLobbyPlayersOcrPlayer(3, "Third Player"),
                            MatchLobbyPlayersOcrPlayer(4, null),
                        ),
                    ),
                ),
            )
        }
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = completePreviewRunner(),
            matchLobbyPlayersOcrRunner = lobbyRunner,
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals("ABC ESPORTS", state.teamNamesBySlot[1])
        assertEquals("Lobby Player", state.lobbyPlayers.first { it.slotNumber == 1 }
            .players.first { it.playerNumber == 1 }.playerName)
        assertEquals(12, state.rows.size)
        assertTrue(state.rows.all { it.suggestedTeamSlotDisplayValue == "Unavailable" })
    }

    @Test
    fun emptyStateWithUsefulResultPreviewStillCarriesPersistedLobbyTeamNames() = runTest(dispatcher) {
        val repository = createRepository()
        repository.saveTeamNames(TOURNAMENT_ID, mapOf(5 to "ETR ESPORTS"))
        val lobbyRunner = MatchLobbyPlayersOcrRunner { _, _ ->
            MatchLobbyPlayersOcrResult(
                slots = listOf(
                    MatchLobbyPlayersOcrSlot(
                        slotNumber = 5,
                        players = (1..4).map { player -> MatchLobbyPlayersOcrPlayer(player, null) },
                    ),
                ),
            )
        }
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = partialPreviewRunner(),
            matchLobbyPlayersOcrRunner = lobbyRunner,
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Empty
        assertEquals("ETR ESPORTS", state.teamNamesBySlot[5])
        assertEquals(5, state.lobbyPlayers.single().slotNumber)
        assertTrue(state.matchResultOcrPreview is MatchResultOcrPreviewUiState.Ready)
    }

    @Test
    fun invalidOcrDisplayInputKeepsExactContextInControlledErrorState() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.loadDisplayInput(
            displayInputWithMatchingEvidence().copy(
                rows = displayInputWithMatchingEvidence().rows.dropLast(1),
            ),
        )

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Error
        assertEquals(TOURNAMENT_ID, state.tournamentId)
        assertEquals(MATCH_ID, state.matchId)
        assertEquals("OCR review requires exactly 12 rows.", state.message)
    }

    @Test
    fun loadDisplayInputDoesNotMutateMatchData() = runTest(dispatcher) {
        val repository = createRepository()
        val beforeMatch = repository.observeMatchById(MATCH_ID).first()
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(repository))

        viewModel.loadDisplayInput(displayInputWithMatchingEvidence())

        assertEquals(beforeMatch, repository.observeMatchById(MATCH_ID).first())
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
    fun finalizationPreservesReviewEvidenceAndCorrectionSnapshot() = runTest(dispatcher) {
        val repository = createRepository()
        val rows = correctionRows().map { row ->
            if (row.rowIndex == 0) {
                row.copy(
                    confidenceScoreDisplayValue = "82",
                    confidenceTierLabel = "Manual review",
                    assignmentSafetyStatusLabel = "Review required",
                    warningLabels = listOf("Weak evidence"),
                )
            } else {
                row
            }
        }
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        val warningDraft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft, 0, "9")
        val viewModel = viewModelWith(repository, readyState(correctionDraft = warningDraft, rows = rows))

        viewModel.onFinalizeOcrCorrection()
        viewModel.onConfirmFinalizeWarnings()
        advanceUntilIdle()

        val evidence = repository.readPreservedOcrEvidence(MATCH_ID)!!
        val firstRow = evidence.rows.first { it.rowIndex == 0 }
        val firstCorrection = evidence.correctionSnapshots.first { it.rowIndex == 0 }
        assertEquals(12, evidence.rows.size)
        assertEquals(12, evidence.correctionSnapshots.size)
        assertEquals("Synthetic Unit 1", firstRow.originalOcrText)
        assertEquals(1, firstRow.originalPlacement)
        assertEquals(0, firstRow.originalKills)
        assertEquals(1, firstRow.originalSuggestedTeamSlot)
        assertEquals("Manual review|82", firstRow.confidenceSummary)
        assertEquals("Review required", firstRow.safetySummary)
        assertTrue(firstRow.manualReviewRequired)
        assertEquals(1, firstCorrection.correctedPlacement)
        assertEquals(9, firstCorrection.correctedKills)
        assertEquals(1, firstCorrection.correctedTeamSlot)
        assertFalse(firstCorrection.placementChanged)
        assertTrue(firstCorrection.killsChanged)
        assertFalse(firstCorrection.teamSlotChanged)
    }

    @Test
    fun successStateAfterValidFinalization() = runTest(dispatcher) {
        val repository = createRepository()
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = correctionDraft()),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(null, ready.finalization.error)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)
    }

    @Test
    fun ocrLocalFinalizationDoesNotWaitForCloudSync() = runTest(dispatcher) {
        val repository = createRepository()
        val finalizedSync = RecordingFinalizedMatchCloudSync().also {
            it.gate = kotlinx.coroutines.CompletableDeferred()
        }
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = correctionDraft()),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)

        finalizedSync.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun ocrCloudFinalizationFailureDoesNotRevertLocalFinalization() = runTest(dispatcher) {
        val repository = createRepository()
        val finalizedSync = RecordingFinalizedMatchCloudSync(
            FinalizedMatchCloudSyncResult.NetworkFailure,
        )
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = correctionDraft()),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)
    }

    @Test
    fun deterministicErrorStateOnFinalizationFailure() = runTest(dispatcher) {
        val repository = InMemoryTournamentRepository()
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = correctionDraft()),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(MatchOcrReviewFinalizationError.MISSING_TOURNAMENT, ready.finalization.error)
        assertFalse(ready.finalization.isFinalized)
        assertTrue(finalizedSync.tournamentIds.isEmpty())
    }

    @Test
    fun repeatedFinalizeAfterSuccessIsIdempotentlyIgnoredByViewModel() = runTest(dispatcher) {
        val repository = createRepository()
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = correctionDraft()),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()
        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(null, ready.finalization.error)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)
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
        finalizedMatchCloudSync: FinalizedMatchCloudSyncAction = RecordingFinalizedMatchCloudSync(),
    ): MatchOcrReviewViewModel =
        MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            finalizedMatchCloudSync = finalizedMatchCloudSync,
            initialUiState = initialUiState,
        )

    private class RecordingFinalizedMatchCloudSync(
        private val result: FinalizedMatchCloudSyncResult = FinalizedMatchCloudSyncResult.Success(1),
    ) : FinalizedMatchCloudSyncAction {
        val tournamentIds = mutableListOf<String>()
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun invoke(
            tournamentId: String,
        ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
            tournamentIds += tournamentId
            gate?.await()
            return QueueAwareActionResult(
                primaryResult = result,
                queueRecordingResult = QueueRecordingResult.RECORDED,
            )
        }
    }

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
        repository.saveTeamNames(
            tournamentId = TOURNAMENT_ID,
            teamNamesBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" },
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
        rows: List<MatchOcrReviewRowUiState> = correctionRows(),
    ): MatchOcrReviewUiState.Ready = MatchOcrReviewUiState.Ready(
        tournamentId = TOURNAMENT_ID,
        matchId = MATCH_ID,
        matchDisplayLabel = "Synthetic Match",
        rowCount = 12,
        rows = rows,
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

    private fun displayInputWithMatchingEvidence(): MatchOcrReviewDisplayInput =
        MatchOcrReviewDisplayInput(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            rows = (0 until TeamSlot.MAX_SLOT_NUMBER).map { rowIndex ->
                val suggestions = TopTeamCandidateSuggestions(
                    detectedPlayerCount = 4,
                    evaluatedCandidateCount = 1,
                    suggestions = listOf(
                        TopTeamCandidateSuggestion(
                            rank = 1,
                            teamCandidateScore = TeamCandidateScore(
                                candidateTeamSlot = rowIndex + 1,
                                confidenceScore = 96,
                                detectedPlayerCount = 4,
                                validDetectedPlayerCount = 4,
                                rosterPlayerCount = 4,
                                contributingMatchCount = 4,
                                averageMatchedPlayerScore = 100,
                                coverageScore = 100,
                                playerMatches = emptyList(),
                            ),
                        ),
                    ),
                )
                val confidence = TeamMatchConfidenceAssessment(
                    tier = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
                    selectedSuggestion = suggestions.suggestions.first(),
                    suggestions = suggestions,
                    reason = TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD,
                )
                MatchOcrReviewRowEvidenceInput(
                    rowIndex = rowIndex,
                    expectedPlacementId = rowIndex + 1,
                    detectedPlacementValue = rowIndex + 1,
                    detectedKillValue = rowIndex,
                    detectedPlayerName = "Synthetic Unit ${rowIndex + 1}",
                    suggestions = suggestions,
                    confidenceAssessment = confidence,
                    safetyResult = RowTeamAssignmentSafetyResult(
                        rowIndex = rowIndex,
                        confidenceAssessment = confidence,
                        safetyStatus = TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT,
                        proposedTeamSlot = rowIndex + 1,
                        reasons = emptySet(),
                    ),
                )
            },
        )

    private fun displayInputWithoutMatchingEvidence(): MatchOcrReviewDisplayInput =
        MatchOcrReviewDisplayInput(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            rows = (0 until TeamSlot.MAX_SLOT_NUMBER).map { rowIndex ->
                MatchOcrReviewRowEvidenceInput(
                    rowIndex = rowIndex,
                    expectedPlacementId = rowIndex + 1,
                    detectedPlacementValue = rowIndex + 1,
                    detectedKillValue = rowIndex,
                    detectedPlayerName = null,
                )
            },
        )

    private fun completePreviewRunner(): MatchResultOcrPreviewRunner =
        MatchResultOcrPreviewRunner { identity ->
            val positions = if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1..10 else 11..12
            MatchResultOcrPreviewProcessingResult.Processed(
                extraction = MatchResultOcrExtractionResult(
                    role = identity.role,
                    fields = emptyList(),
                    rows = positions.map { position ->
                        MatchResultOcrRow(
                            position = position,
                            source = if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                                MatchResultOcrRowSource.UPPER_TEMPLATE
                            } else if (position == 11) {
                                MatchResultOcrRowSource.LOWER_ROW_A
                            } else {
                                MatchResultOcrRowSource.LOWER_ROW_B
                            },
                            placement = ocrField(
                                id = "placement-$position",
                                type = MatchResultOcrFieldType.PLACEMENT,
                                position = position,
                                slot = null,
                                text = position.toString(),
                            ),
                            playerSlots = (1..4).map { slot ->
                                MatchResultOcrPlayerSlot(
                                    slot = slot,
                                    player = ocrField(
                                        id = "player-$position-$slot",
                                        type = MatchResultOcrFieldType.PLAYER,
                                        position = position,
                                        slot = slot,
                                        text = "Player $position-$slot",
                                    ),
                                    kill = ocrField(
                                        id = "kill-$position-$slot",
                                        type = MatchResultOcrFieldType.KILL,
                                        position = position,
                                        slot = slot,
                                        text = slot.toString(),
                                    ),
                                )
                            },
                        )
                    },
                ),
                pixelCrop = OcrPixelCropRect(0, 0, 1, 1),
                cropWidth = 1,
                cropHeight = 1,
            )
        }

    private fun partialPreviewRunner(): MatchResultOcrPreviewRunner =
        MatchResultOcrPreviewRunner { identity ->
            when (val result = completePreviewRunner().process(identity)) {
                is MatchResultOcrPreviewProcessingResult.Processed ->
                    result.copy(extraction = result.extraction.copy(rows = result.extraction.rows.take(1)))
                else -> result
            }
        }

    private fun ocrField(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        slot: Int?,
        text: String,
    ): MatchResultOcrField = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = null,
        slot = slot,
        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        ocrText = text,
        resolvedText = text,
        status = if (type == MatchResultOcrFieldType.KILL) {
            MatchResultOcrFieldStatus.DIRECT_NUMERIC
        } else {
            MatchResultOcrFieldStatus.DIRECT_TEXT
        },
    )

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
        const val MATCH_ID = "synthetic-match"
    }
}
