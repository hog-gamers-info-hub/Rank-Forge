package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewImage
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrScreenshotResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrSlot
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrUnavailableReason
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
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrCorrectionSnapshot
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrRowEvidence
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun historicalEvidenceLoadsWithoutRunningEitherOcrRunner() = runTest(dispatcher) {
        val repository = createRepository()
        repository.finalizeDraftMatchWithOcrEvidenceByOwner(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            ownerUserId = com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            placements = (1..12).map { MatchPlacement(teamSlotNumber = it, position = it) },
            kills = (1..12).map { MatchKill(teamSlotNumber = it, kills = it - 1) },
            evidence = preservedEvidence(),
        )
        var resultRunnerCalls = 0
        var lobbyRunnerCalls = 0
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                resultRunnerCalls++
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                lobbyRunnerCalls++
                MatchLobbyPlayersOcrResult.unavailable()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
            screenshotOwnerProvider = ownerProvider,
            tournamentRepository = repository,
        )

        viewModel.loadHistoricalEvidence(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(TOURNAMENT_ID, state.tournamentId)
        assertEquals(MATCH_ID, state.matchId)
        assertEquals(12, state.rows.size)
        assertTrue(state.finalization.isFinalized)
        assertEquals(0, resultRunnerCalls)
        assertEquals(0, lobbyRunnerCalls)
    }

    @Test
    fun missingHistoricalEvidenceShowsEmptyStateWithoutRunningEitherOcrRunner() = runTest(dispatcher) {
        val repository = createRepository()
        var resultRunnerCalls = 0
        var lobbyRunnerCalls = 0
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                resultRunnerCalls++
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                lobbyRunnerCalls++
                MatchLobbyPlayersOcrResult.unavailable()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
            screenshotOwnerProvider = ownerProvider,
            tournamentRepository = repository,
        )

        viewModel.loadHistoricalEvidence(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is MatchOcrReviewUiState.Empty)
        assertEquals(0, resultRunnerCalls)
        assertEquals(0, lobbyRunnerCalls)
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
    fun explicitReprocessRunsAgainForTheSameMatch() = runTest(dispatcher) {
        var calls = 0
        var evidenceVersion = 1
        val completeRunner = completePreviewRunner()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
                calls++
                val processed = completeRunner.process(identity) as MatchResultOcrPreviewProcessingResult.Processed
                processed.copy(
                    extraction = processed.extraction.copy(
                        rows = processed.extraction.rows.map { row ->
                            row.copy(
                                placement = row.placement.copy(
                                    ocrText = evidenceVersion.toString(),
                                    resolvedText = evidenceVersion.toString(),
                                ),
                            )
                        },
                    ),
                )
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()
        assertEquals("1", (viewModel.uiState.value as MatchOcrReviewUiState.Ready)
            .rows.first().detectedPlacementDisplayValue)
        evidenceVersion = 2
        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence = false)
        advanceUntilIdle()

        assertEquals(4, calls)
        assertEquals("2", (viewModel.uiState.value as MatchOcrReviewUiState.Ready)
            .rows.first().detectedPlacementDisplayValue)
    }

    @Test
    fun calculatePointsUsesSlotOnlyLobbyOcrAndPopulatesAutomaticTeamAssignment() = runTest(dispatcher) {
        var oldLobbyRunnerCalls = 0
        var slotRunnerCalls = 0
        val resultRoles = mutableListOf<MatchResultScreenshotRole>()
        val slotNumberResult = phase1SlotNumberResult()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = slotOnlyResultPreviewRunner(resultRoles),
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                oldLobbyRunnerCalls++
                MatchLobbyPlayersOcrResult.unavailable()
            },
            matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                slotRunnerCalls++
                slotNumberResult
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(InMemoryTournamentRepository()),
            observeRoster = ObserveRosterByTournamentUseCase(InMemoryTournamentRepository()),
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.reprocess(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            allowIncompleteEvidence = true,
            useSlotNumberOnlyLobbyOcr = true,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(MatchResultScreenshotRole.entries, resultRoles)
        assertEquals(1, slotRunnerCalls)
        assertEquals(0, oldLobbyRunnerCalls)
        assertEquals(slotNumberResult, state.phase1LobbySlotNumberOcr)
        assertTrue(state.lobbyPlayers.isEmpty())
        assertTrue(state.rows.first().resultLobbyVoteEvidencePresent)
        assertEquals(4, state.rows.first().originalSuggestedTeamSlot)
        assertEquals("4", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
        assertNull(state.rows[1].originalSuggestedTeamSlot)
        assertEquals("", state.correctionDraft.rows[1].assignedTeamSlotDraftValue)
        assertEquals("1", state.rows.first().detectedPlacementDisplayValue)

        val firstScreenshot = state.phase1LobbySlotNumberOcr!!.screenshots.first()
            as MatchLobbySlotNumberOcrScreenshotResult.Processed
        assertEquals(listOf(4, 1, 3, 2), firstScreenshot.slots.map { it.candidate.detectedSlotNumber })
        assertEquals(
            listOf(
                RosterCandidateParseStatus.MISSING,
                RosterCandidateParseStatus.AMBIGUOUS,
                RosterCandidateParseStatus.MISSING,
                RosterCandidateParseStatus.PARSED,
            ),
            (state.phase1LobbySlotNumberOcr!!.screenshots[1]
                as MatchLobbySlotNumberOcrScreenshotResult.Processed)
                .slots.map { it.candidate.status },
        )
        assertEquals(
            MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
            (state.phase1LobbySlotNumberOcr!!.screenshots[2]
                as MatchLobbySlotNumberOcrScreenshotResult.Unavailable).reason,
        )
    }

    @Test
    fun calculatePointsPropagatesSlotOnlyLobbyOcrCancellationWithoutFallingBackToPlayerOcr() =
        runTest(dispatcher) {
            var oldLobbyRunnerCalls = 0
            val viewModel = MatchOcrReviewViewModel(
                finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
                matchResultOcrPreviewRunner = completePreviewRunner(),
                matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                    oldLobbyRunnerCalls++
                    MatchLobbyPlayersOcrResult.unavailable()
                },
                matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                    throw java.util.concurrent.CancellationException("test cancellation")
                },
                observeTournamentSlots = ObserveTournamentSlotsUseCase(InMemoryTournamentRepository()),
                observeRoster = ObserveRosterByTournamentUseCase(InMemoryTournamentRepository()),
                initialUiState = MatchOcrReviewUiState.Loading,
            )

            viewModel.reprocess(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
                allowIncompleteEvidence = true,
                useSlotNumberOnlyLobbyOcr = true,
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value as MatchOcrReviewUiState.Empty
            assertTrue(state.matchResultOcrPreview is MatchResultOcrPreviewUiState.Processing)
            assertEquals(0, oldLobbyRunnerCalls)
        }

    @Test
    fun resultAndLobbyCompletionOrderProducesTheSameLobbyDrivenAssignment() = runTest(dispatcher) {
        assertEquals(12, runGatedAssignment(resultCompletesFirst = true))
        assertEquals(12, runGatedAssignment(resultCompletesFirst = false))
    }

    @Test
    fun liveLobbyEvidenceOverridesConflictingPersistedRosterSlot() = runTest(dispatcher) {
        val repository = createRepository()
        repository.saveRoster(
            tournamentId = TOURNAMENT_ID,
            slotNumber = 1,
            players = listOf("Alpha", "Bravo", "Charlie", "Delta").map { name ->
                RosterPlayer.create(
                    TOURNAMENT_ID,
                    1,
                    name,
                )
            },
        )
        val resultPlayers = listOf("Alpha", "Bravo", "Charlie", "Delta")
        val resultRunner = MatchResultOcrPreviewRunner { identity ->
            when (val result = completePreviewRunner().process(identity)) {
                is MatchResultOcrPreviewProcessingResult.Processed -> result.copy(
                    extraction = result.extraction.copy(
                        rows = result.extraction.rows.map { row ->
                            if (row.position == 1) {
                                row.copy(
                                    playerSlots = row.playerSlots.mapIndexed { index, playerSlot ->
                                        playerSlot.copy(
                                            player = playerSlot.player.copy(
                                                ocrText = resultPlayers[index],
                                                resolvedText = resultPlayers[index],
                                            ),
                                        )
                                    },
                                )
                            } else {
                                row
                            }
                        },
                    ),
                )
                else -> result
            }
        }
        val lobbyRunner = MatchLobbyPlayersOcrRunner { _, _ ->
            MatchLobbyPlayersOcrResult(
                slots = listOf(
                    MatchLobbyPlayersOcrSlot(
                        slotNumber = 5,
                        players = resultPlayers.mapIndexed { index, name ->
                            MatchLobbyPlayersOcrPlayer(index + 1, name)
                        },
                    ),
                ),
            )
        }
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = resultRunner,
            matchLobbyPlayersOcrRunner = lobbyRunner,
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
            screenshotOwnerProvider = ownerProvider,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val row = state.rows.first()
        val draft = state.correctionDraft!!.rows.first()
        assertEquals("5", row.suggestedTeamSlotDisplayValue)
        assertEquals(5, row.originalSuggestedTeamSlot)
        assertEquals("5", draft.assignedTeamSlotDraftValue)
        assertFalse(row.suggestedTeamSlotDisplayValue == "1")
        assertFalse(row.originalSuggestedTeamSlot == 1)
        assertFalse(draft.assignedTeamSlotDraftValue == "1")
    }

    private suspend fun TestScope.runGatedAssignment(resultCompletesFirst: Boolean): Int {
        val resultStarted = CompletableDeferred<Unit>()
        val lobbyStarted = CompletableDeferred<Unit>()
        val resultRelease = CompletableDeferred<Unit>()
        val lobbyRelease = CompletableDeferred<Unit>()
        val lobbyEvidence = MatchLobbyPlayersOcrResult(
            slots = (1..12).map { slotNumber ->
                MatchLobbyPlayersOcrSlot(
                    slotNumber = slotNumber,
                    players = if (slotNumber == 12) {
                        (1..4).map { playerNumber ->
                            MatchLobbyPlayersOcrPlayer(playerNumber, "Player 1-$playerNumber")
                        }
                    } else {
                        (1..4).map { playerNumber -> MatchLobbyPlayersOcrPlayer(playerNumber, null) }
                    },
                )
            },
        )
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
                resultStarted.complete(Unit)
                resultRelease.await()
                val processed = completePreviewRunner().process(identity)
                    as MatchResultOcrPreviewProcessingResult.Processed
                processed.copy(
                    extraction = processed.extraction.copy(
                        rows = if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                            processed.extraction.rows.take(1)
                        } else {
                            emptyList()
                        },
                    ),
                )
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                lobbyStarted.complete(Unit)
                lobbyRelease.await()
                lobbyEvidence
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(InMemoryTournamentRepository()),
            observeRoster = ObserveRosterByTournamentUseCase(InMemoryTournamentRepository()),
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence = true)
        runCurrent()
        resultStarted.await()
        lobbyStarted.await()
        if (resultCompletesFirst) {
            resultRelease.complete(Unit)
            runCurrent()
            lobbyRelease.complete(Unit)
        } else {
            lobbyRelease.complete(Unit)
            runCurrent()
            resultRelease.complete(Unit)
        }
        advanceUntilIdle()
        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        return requireNotNull(state.rows.first().originalSuggestedTeamSlot) {
            "Expected a safe Lobby assignment but got ${state.rows.first()}"
        }
    }

    @Test
    fun incompleteReprocessWithOnlyUpperCreatesManualPlaceholdersForLowerRows() = runTest(dispatcher) {
        val completeRunner = completePreviewRunner()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
                if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                    completeRunner.process(identity)
                } else {
                    MatchResultOcrPreviewProcessingResult.MissingAsset
                }
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(12, state.rows.size)
        assertEquals("Unavailable", state.rows[10].detectedPlacementDisplayValue)
        assertEquals("", state.correctionDraft!!.rows[10].placementDraftValue)
        assertTrue(state.rows[10].blockerLabels.isNotEmpty())
    }

    @Test
    fun incompleteReprocessWithOnlyLowerCreatesManualPlaceholdersForUpperRows() = runTest(dispatcher) {
        val completeRunner = completePreviewRunner()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
                if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_LOWER) {
                    completeRunner.process(identity)
                } else {
                    MatchResultOcrPreviewProcessingResult.MissingAsset
                }
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(12, state.rows.size)
        assertTrue(state.rows.take(10).all { it.detectedPlacementDisplayValue == "Unavailable" })
        assertEquals("", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
    }

    @Test
    fun incompleteReprocessWithZeroResultEvidenceCreatesTwelveBlankManualRows() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(12, state.rows.size)
        assertEquals((0..11).toList(), state.rows.map { it.rowIndex })
        assertEquals((1..12).map(Int::toString), state.rows.map { it.expectedPlacementLabel })
        assertTrue(state.rows.all { it.detectedPlacementDisplayValue == "Unavailable" })
        assertTrue(state.rows.all { it.detectedKillDisplayValue == "Unavailable" })
        assertTrue(state.rows.all { it.suggestedTeamSlotDisplayValue == "Unavailable" })
        assertTrue(state.correctionDraft!!.rows.all { row ->
            row.placementDraftValue.isBlank() &&
                row.killsDraftValue.isBlank() &&
                row.assignedTeamSlotDraftValue.isBlank()
        })
        assertEquals(12, state.correctionDraft.blockerCount)
    }

    @Test
    fun completeEvidenceModeDoesNotSilentlyCreateManualFallbackWithoutResults() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Empty
        assertTrue(state.matchResultOcrPreview is MatchResultOcrPreviewUiState.Error)
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
    fun loadSurfacesPersistedTeamNamesWithoutUsingRosterPlayersForMatching() = runTest(dispatcher) {
        val repository = createRepository()
        repository.saveTeamNames(TOURNAMENT_ID, mapOf(5 to "ETR ESPORTS"))
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = completePreviewRunner(),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
            screenshotOwnerProvider = ownerProvider,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals("ETR ESPORTS", state.teamNamesBySlot[5])
        assertEquals(12, state.rows.size)
        assertTrue(state.rows.all { it.assignmentSafetyStatusLabel == "Manual required" })
        assertTrue(state.rows.all { it.originalSuggestedTeamSlot == null })
        assertTrue(state.correctionDraft!!.rows.all { it.assignedTeamSlotDraftValue.isBlank() })
    }

    @Test
    fun lobbyPlayersCoexistWithResultPreviewWithoutBecomingResultMatchCandidates() = runTest(dispatcher) {
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
            screenshotOwnerProvider = ownerProvider,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals("ABC ESPORTS", state.teamNamesBySlot[1])
        assertEquals("Lobby Player", state.lobbyPlayers.first { it.slotNumber == 1 }
            .players.first { it.playerNumber == 1 }.playerName)
        assertEquals(12, state.rows.size)
        assertTrue(state.rows.all { it.assignmentSafetyStatusLabel == "Manual required" })
        assertTrue(state.rows.all { it.originalSuggestedTeamSlot == null })
    }

    @Test
    fun partialResultPreviewStillCarriesPersistedLobbyTeamNames() = runTest(dispatcher) {
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
            screenshotOwnerProvider = ownerProvider,
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
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
    fun onExcludeRowUpdatesOnlySelectedCorrectionRowAndPreservesReviewIdentity() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = viewModelWith(repository, readyState())

        viewModel.onExcludeRow(10)

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val draft = ready.correctionDraft!!
        assertEquals(TOURNAMENT_ID, ready.tournamentId)
        assertEquals(MATCH_ID, ready.matchId)
        assertEquals(12, ready.rows.size)
        assertEquals((0 until TeamSlot.MAX_SLOT_NUMBER).toList(), ready.rows.map { it.rowIndex })
        assertTrue(draft.rows[10].isExcluded)
        assertTrue(draft.rows.filterIndexed { index, _ -> index != 10 }.all { !it.isExcluded })
        assertTrue(draft.isDirty)
        assertEquals(1, draft.excludedCount)
        assertEquals(11, draft.includedRows.size)
    }

    @Test
    fun onExcludeRowDoesNotFinalizePersistOrCloudSync() = runTest(dispatcher) {
        val repository = createRepository()
        val beforeMatch = repository.observeMatchById(MATCH_ID).first()
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = viewModelWith(
            repository = repository,
            initialUiState = readyState().copy(
                finalization = MatchOcrReviewFinalizationUiState(
                    showWarningConfirmation = true,
                    error = MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED,
                ),
            ),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onExcludeRow(2)

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertFalse(ready.finalization.isFinalized)
        assertFalse(ready.finalization.showWarningConfirmation)
        assertEquals(null, ready.finalization.error)
        assertEquals(beforeMatch, repository.observeMatchById(MATCH_ID).first())
        assertTrue(finalizedSync.tournamentIds.isEmpty())
    }

    @Test
    fun onExcludeRowAfterFinalizationDoesNotMutateCorrectionDraft() = runTest(dispatcher) {
        val repository = createRepository()
        val initialDraft = correctionDraft()
        val viewModel = viewModelWith(
            repository = repository,
            initialUiState = readyState(correctionDraft = initialDraft).copy(
                finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
            ),
        )

        viewModel.onExcludeRow(5)

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(initialDraft, ready.correctionDraft)
        assertFalse(ready.correctionDraft!!.rows.any { it.isExcluded })
    }

    @Test
    fun resetRowAfterExclusionRestoresTheRowAndCorrectionDraftState() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = viewModelWith(repository, readyState())

        viewModel.onExcludeRow(3)
        viewModel.onResetRowCorrection(3)

        val draft = (viewModel.uiState.value as MatchOcrReviewUiState.Ready).correctionDraft!!
        assertFalse(draft.rows[3].isExcluded)
        assertEquals("4", draft.rows[3].placementDraftValue)
        assertEquals("3", draft.rows[3].killsDraftValue)
        assertEquals("4", draft.rows[3].assignedTeamSlotDraftValue)
        assertEquals(0, draft.excludedCount)
        assertEquals(12, draft.includedRows.size)
        assertFalse(draft.isDirty)
    }

    @Test
    fun resetAllAfterExclusionRestoresEveryCorrectionRow() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = viewModelWith(repository, readyState())

        viewModel.onExcludeRow(1)
        viewModel.onExcludeRow(9)
        viewModel.onResetAllCorrections()

        val draft = (viewModel.uiState.value as MatchOcrReviewUiState.Ready).correctionDraft!!
        assertTrue(draft.rows.all { !it.isExcluded })
        assertEquals(0, draft.excludedCount)
        assertEquals(12, draft.includedRows.size)
        assertFalse(draft.isDirty)
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
    fun excludedRowsRemainStructuralInputsWhileFinalizationUsesActiveParticipants() = runTest(dispatcher) {
        val repository = createRepository(activeTeamCount = 10)
        val excludedDraft = correctionDraft { draft ->
            val excluded = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(draft, 10)
            MatchOcrReviewCorrectionDraftReducer.onRowExcluded(excluded, 11)
        }
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = excludedDraft),
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val evidence = repository.readPreservedOcrEvidence(MATCH_ID)!!
        assertTrue(ready.finalization.isFinalized)
        assertEquals(10, repository.observeMatchById(MATCH_ID).first()!!.placements.size)
        assertEquals(12, evidence.rows.size)
        assertEquals(10, evidence.correctionSnapshots.size)
        assertEquals((0..11).toList(), evidence.rows.map { it.rowIndex })
        assertEquals((0..9).toList(), evidence.correctionSnapshots.map { it.rowIndex })
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
        assertTrue(publicMethodNames.contains("onPlayerKillsChanged"))
        assertTrue(publicMethodNames.contains("onAssignedTeamSlotChanged"))
        assertTrue(publicMethodNames.contains("onExcludeRow"))
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

    @Test
    fun playerKillChangeDelegatesToCorrectionDraftReducer() = runTest(dispatcher) {
        val rows = correctionRows().mapIndexed { index, row ->
            if (index == 0) {
                row.copy(
                    detectedKillDisplayValue = "10",
                    originalParsedKillValue = 10,
                    playerKillEvidence = listOf("3", "2", "1", "4").mapIndexed { playerIndex, kills ->
                        MatchOcrReviewPlayerKillEvidenceUiState(
                            playerSlot = playerIndex + 1,
                            originalKillsValue = kills,
                        )
                    },
                )
            } else {
                row
            }
        }
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        val viewModel = viewModelWith(createRepository(), readyState(correctionDraft = draft, rows = rows))

        viewModel.onPlayerKillsChanged(rowIndex = 0, playerSlot = 3, value = "5")

        val updated = (viewModel.uiState.value as MatchOcrReviewUiState.Ready).correctionDraft!!.rows[0]
        assertEquals(listOf("3", "2", "5", "4"), updated.playerKillDrafts.map { it.killsDraftValue })
        assertEquals("14", updated.killsDraftValue)
    }

    @Test
    fun finalizationUsesLiveDerivedPlayerKillsAndRetainsPlayerDraftValues() = runTest(dispatcher) {
        val repository = createRepository()
        val rows = correctionRows().withFirstRowPlayerKillEvidence(listOf("3", "2", "1", "4"))
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = viewModelWith(
            repository = repository,
            initialUiState = readyState(correctionDraft = initialDraft, rows = rows),
            finalizedMatchCloudSync = finalizedSync,
        )

        viewModel.onPlayerKillsChanged(rowIndex = 0, playerSlot = 3, value = "5")
        viewModel.onFinalizeOcrCorrection()
        viewModel.onConfirmFinalizeWarnings()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val finalizedRowDraft = ready.correctionDraft!!.rows.first()
        val finalizedMatch = repository.observeMatchById(MATCH_ID).first()!!
        val correctionSnapshot = repository.readPreservedOcrEvidence(MATCH_ID)!!
            .correctionSnapshots
            .first { it.rowIndex == 0 }

        assertTrue(ready.finalization.isFinalized)
        assertEquals(listOf("3", "2", "5", "4"), finalizedRowDraft.playerKillDrafts.map { it.killsDraftValue })
        assertEquals("14", finalizedRowDraft.killsDraftValue)
        assertEquals(14, finalizedMatch.kills.first { it.teamSlotNumber == 1 }.kills)
        assertFalse(finalizedMatch.kills.first { it.teamSlotNumber == 1 }.kills == 10)
        assertEquals(14, correctionSnapshot.correctedKills)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)
    }

    @Test
    fun sameTotalPlayerRedistributionFinalizesWithTeamTotalAndRetainsPlayerDrafts() = runTest(dispatcher) {
        val repository = createRepository()
        val rows = correctionRows().withFirstRowPlayerKillEvidence(listOf("2", "4"))
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        val viewModel = viewModelWith(repository, readyState(correctionDraft = initialDraft, rows = rows))

        viewModel.onPlayerKillsChanged(rowIndex = 0, playerSlot = 1, value = "3")
        viewModel.onPlayerKillsChanged(rowIndex = 0, playerSlot = 2, value = "3")

        val edited = (viewModel.uiState.value as MatchOcrReviewUiState.Ready).correctionDraft!!.rows.first()
        assertTrue(edited.isDirty)
        assertEquals("6", edited.killsDraftValue)

        viewModel.onFinalizeOcrCorrection()
        viewModel.onConfirmFinalizeWarnings()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(listOf("3", "3"), ready.correctionDraft!!.rows.first().playerKillDrafts.map { it.killsDraftValue })
        assertEquals(6, repository.observeMatchById(MATCH_ID).first()!!.kills.first {
            it.teamSlotNumber == 1
        }.kills)
    }

    @Test
    fun legacyAggregateKillsRemainAuthoritativeForFinalization() = runTest(dispatcher) {
        val repository = createRepository()
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(correctionDraft(), 0, "7")
        val viewModel = viewModelWith(repository, readyState(correctionDraft = draft))

        viewModel.onFinalizeOcrCorrection()
        viewModel.onConfirmFinalizeWarnings()
        advanceUntilIdle()

        assertEquals(7, repository.observeMatchById(MATCH_ID).first()!!.kills.first {
            it.teamSlotNumber == 1
        }.kills)
    }

    @Test
    fun playerKillChangesAfterFinalizationDoNotMutateTheCorrectionDraft() = runTest(dispatcher) {
        val repository = createRepository()
        val rows = correctionRows().withFirstRowPlayerKillEvidence(listOf("3", "2", "1", "4"))
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.onPlayerKillsChanged(
            draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows),
            rowIndex = 0,
            playerSlot = 3,
            value = "5",
        )
        val viewModel = viewModelWith(
            repository = repository,
            initialUiState = readyState(correctionDraft = initialDraft, rows = rows).copy(
                finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
            ),
        )

        viewModel.onPlayerKillsChanged(rowIndex = 0, playerSlot = 3, value = "9")

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.finalization.isFinalized)
        assertEquals(listOf("3", "2", "5", "4"), ready.correctionDraft!!.rows.first().playerKillDrafts.map {
            it.killsDraftValue
        })
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
            screenshotOwnerProvider = ownerProvider,
        )

    private val ownerProvider = object : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String =
            com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository.OWNER_USER_ID
    }

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
            finalizeMatch = FinalizeMatchUseCase(
                repository,
                ValidateMatchResultUseCase(),
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
            authRepository = com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
        )

    private suspend fun createRepository(
        activeTeamCount: Int = 12,
    ): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Synthetic Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
                ownerUserId = com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        repository.saveTeamNames(
            tournamentId = TOURNAMENT_ID,
            teamNamesBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber ->
                if (slotNumber <= activeTeamCount) "Team $slotNumber" else ""
            },
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

    private fun List<MatchOcrReviewRowUiState>.withFirstRowPlayerKillEvidence(
        kills: List<String>,
    ): List<MatchOcrReviewRowUiState> = map { row ->
        if (row.rowIndex == 0) {
            row.copy(
                detectedKillDisplayValue = kills.sumOf { it.toInt() }.toString(),
                originalParsedKillValue = kills.sumOf { it.toInt() },
                playerKillEvidence = kills.mapIndexed { index, value ->
                    MatchOcrReviewPlayerKillEvidenceUiState(
                        playerSlot = index + 1,
                        originalKillsValue = value,
                    )
                },
            )
        } else {
            row
        }
    }

    private fun preservedEvidence(): PreservedMatchOcrEvidence = PreservedMatchOcrEvidence(
        tournamentId = TOURNAMENT_ID,
        matchId = MATCH_ID,
        sourceScreenshotId = "result-upper",
        preservedAt = 123L,
        provenance = "OCR_REVIEW_FINALIZATION",
        rows = (0 until TeamSlot.MAX_SLOT_NUMBER).map { rowIndex ->
            PreservedMatchOcrRowEvidence(
                rowIndex = rowIndex,
                originalOcrText = "Synthetic OCR row ${rowIndex + 1}",
                originalPlacement = rowIndex + 1,
                originalKills = rowIndex,
                originalSuggestedTeamSlot = rowIndex + 1,
                confidenceSummary = "96|Automatic candidate",
                safetySummary = "Safe automatic assignment",
                manualReviewRequired = false,
            )
        },
        correctionSnapshots = (0 until TeamSlot.MAX_SLOT_NUMBER).map { rowIndex ->
            PreservedMatchOcrCorrectionSnapshot(
                rowIndex = rowIndex,
                correctedPlacement = rowIndex + 1,
                correctedKills = rowIndex,
                correctedTeamSlot = rowIndex + 1,
                placementChanged = false,
                killsChanged = false,
                teamSlotChanged = false,
            )
        },
    )

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

    private fun phase1SlotNumberResult(): MatchLobbySlotNumberOcrResult =
        MatchLobbySlotNumberOcrResult(
            listOf(
                processedSlotNumberScreenshot(
                    position = RosterScreenshotPosition.ONE,
                    candidates = listOf(
                        parsedSlotNumber(4),
                        parsedSlotNumber(1),
                        parsedSlotNumber(3),
                        parsedSlotNumber(2),
                    ),
                ).copy(
                    teamCropPreviews = MatchLobbyTeamCropPreviewResult.Available(
                        previews = listOf(
                            slotOnlyTeamCropPreview(RosterVisibleSlotPosition.TOP_LEFT, 4, listOf(
                                "Alpha",
                                "Bravo",
                                "Charlie",
                                "Delta",
                            )),
                            slotOnlyTeamCropPreview(RosterVisibleSlotPosition.TOP_RIGHT, 1, emptyList()),
                            slotOnlyTeamCropPreview(RosterVisibleSlotPosition.BOTTOM_LEFT, 3, emptyList()),
                            slotOnlyTeamCropPreview(RosterVisibleSlotPosition.BOTTOM_RIGHT, 2, emptyList()),
                        ),
                    ),
                ),
                processedSlotNumberScreenshot(
                    position = RosterScreenshotPosition.TWO,
                    candidates = listOf(
                        missingSlotNumber(),
                        ambiguousSlotNumber(),
                        missingSlotNumber(),
                        parsedSlotNumber(12),
                    ),
                ),
                MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                    screenshotPosition = RosterScreenshotPosition.THREE,
                    reason = MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
                ),
            ),
        )

    private fun slotOnlyTeamCropPreview(
        visibleSlotPosition: RosterVisibleSlotPosition,
        slotNumber: Int,
        playerNames: List<String>,
    ): MatchLobbyTeamCropPreview = MatchLobbyTeamCropPreview(
        visibleSlotPosition = visibleSlotPosition,
        detectedSlotNumber = slotNumber,
        image = SlotOnlyTeamCropPreviewImage,
        playerRowPreviews = playerNames.mapIndexed { index, playerName ->
            val row = LobbyPlayerRow.entries[index]
            LobbyPlayerRowCropPreview(
                row = row,
                boundsInTeamCrop = LobbyPlayerRowCropBounds(0, index, 10, index + 1),
                slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
                slotAnchorY = 5.0,
                structuralEvidence = playerName,
            )
        },
    )

    private data object SlotOnlyTeamCropPreviewImage : MatchLobbyTeamCropPreviewImage

    private fun slotOnlyResultPreviewRunner(
        resultRoles: MutableList<MatchResultScreenshotRole>,
    ): MatchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
        resultRoles += identity.role
        when (val result = completePreviewRunner().process(identity)) {
            is MatchResultOcrPreviewProcessingResult.Processed -> result.copy(
                extraction = result.extraction.copy(
                    rows = result.extraction.rows.map { row ->
                        if (row.position != 1) {
                            row
                        } else {
                            row.copy(
                                playerSlots = row.playerSlots.mapIndexed { index, playerSlot ->
                                    val playerName = listOf("Alpha", "Bravo", "Charlie", "Delta")[index]
                                    playerSlot.copy(
                                        player = playerSlot.player.copy(
                                            ocrText = playerName,
                                            resolvedText = playerName,
                                        ),
                                    )
                                },
                            )
                        }
                    },
                ),
            )
            else -> result
        }
    }

    private fun processedSlotNumberScreenshot(
        position: RosterScreenshotPosition,
        candidates: List<RosterSlotNumberCandidate>,
    ): MatchLobbySlotNumberOcrScreenshotResult.Processed =
        MatchLobbySlotNumberOcrScreenshotResult.Processed(
            screenshotPosition = position,
            slots = RosterVisibleSlotPosition.entries.mapIndexed { index, visiblePosition ->
                MatchLobbySlotNumberOcrSlot(visiblePosition, candidates[index])
            },
        )

    private fun parsedSlotNumber(number: Int): RosterSlotNumberCandidate = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.PARSED,
        detectedSlotNumber = number,
        failure = null,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun missingSlotNumber(): RosterSlotNumberCandidate = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.MISSING,
        detectedSlotNumber = null,
        failure = RosterCandidateParseFailure.MISSING_EVIDENCE,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun ambiguousSlotNumber(): RosterSlotNumberCandidate = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.AMBIGUOUS,
        detectedSlotNumber = null,
        failure = RosterCandidateParseFailure.MULTIPLE_FRAGMENTS,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Unavailable,
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
