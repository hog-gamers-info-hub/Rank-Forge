package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterPlayerNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTeamNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTournamentSlotCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrEvidence
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrUseCase
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPreparedPanel
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProviderResult
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterResult
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.ReplaceTournamentRosterInCloudUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementRepository
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

@OptIn(ExperimentalCoroutinesApi::class)
class RosterOcrReviewViewModelTest {
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
    fun blankTournamentIdBecomesControlledUnavailableState() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()

        viewModel.load(" ")

        assertEquals(
            RosterOcrReviewUiState.Unavailable(null, RosterOcrReviewLoadFailure.INVALID_TOURNAMENT_CONTEXT),
            viewModel.uiState.value,
        )
    }

    @Test
    fun missingTournamentBecomesControlledUnavailableState() = runTest(dispatcher) {
        val fixture = fixture(repository = FakeTournamentRepository())
        val viewModel = fixture.viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            RosterOcrReviewLoadFailure.TOURNAMENT_NOT_FOUND,
            (viewModel.uiState.value as RosterOcrReviewUiState.Unavailable).failure,
        )
    }

    @Test
    fun loadPreservesExactTwelveTeamNamesAndPerformsNoMutationOrOcr() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RosterOcrReviewUiState.ReadyToProcess
        assertEquals((1..12).map { "room-team-$it" }, ready.teamSlots.map { it.teamName })
        assertEquals(0, fixture.sourceProvider.reads)
        assertEquals(0, fixture.repository.replaceCalls)
        assertEquals(0, fixture.cloudRepository.calls)
        assertEquals(0, fixture.queueRepository.enqueueCalls)
        assertEquals(0, fixture.queueRepository.completeCalls)
    }

    @Test
    fun explicitProcessingCallsOcrOnceAndBuildsCompleteReview() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReady(viewModel)

        viewModel.startProcessing()
        viewModel.startProcessing()
        advanceUntilIdle()

        val reviewing = viewModel.uiState.value as RosterOcrReviewUiState.Reviewing
        assertEquals(1, fixture.sourceProvider.reads)
        assertEquals((1..12).toList(), reviewing.draft.slots.map { it.slotNumber })
        assertEquals(12, reviewing.draft.slots.size)
        assertEquals(6, reviewing.draft.slots.first().players.size)
        assertEquals(fixture.evidence, reviewing.evidence)
        assertTrue(reviewing.draft.slots.all { slot ->
            slot.players.take(4).map { it.draftValue } == (1..4).map { row -> "ocr-${slot.slotNumber}-$row" }
        })
        assertTrue(reviewing.draft.canConfirm)
        assertEquals(1, fixture.sourceProvider.reads)
        assertEquals(1, fixture.parser.calls)
        assertEquals(1, fixture.associator.calls)
        assertEquals(1, fixture.validator.calls)
    }

    @Test
    fun controlledProcessingFailureReturnsToRetryableReadyState() = runTest(dispatcher) {
        val fixture = fixture(sourceResult = RosterOcrSourceProviderResult.LoadingFailure)
        val viewModel = fixture.viewModel()
        loadReady(viewModel)

        viewModel.startProcessing()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RosterOcrReviewUiState.ReadyToProcess
        assertTrue(ready.processingFailure is RosterOcrReviewProcessingFailure.Controlled)
        assertEquals(0, fixture.repository.replaceCalls)
    }

    @Test
    fun duplicateTeamContextIsRejectedWithoutAnyOcrLocalCloudOrQueueAction() = runTest(dispatcher) {
        val duplicateSlots = TeamSlot.SLOT_NUMBERS.toList().dropLast(1).map { slot ->
            TeamSlot.create(TOURNAMENT_ID, slot, "room-team-$slot")
        } + TeamSlot.create(TOURNAMENT_ID, 1, "duplicate")
        val fixture = fixture(
            repository = FakeTournamentRepository(
                tournament = tournament(TOURNAMENT_ID),
                slots = duplicateSlots,
            ),
        )
        val viewModel = fixture.viewModel()

        loadReady(viewModel)
        assertEquals(RosterOcrReviewLoadFailure.INCOMPLETE_TEAM_CONTEXT, (viewModel.uiState.value as RosterOcrReviewUiState.Unavailable).failure)
        assertEquals(0, fixture.sourceProvider.reads)
        assertEquals(0, fixture.repository.replaceCalls)
        assertEquals(0, fixture.cloudRepository.calls)
        assertEquals(0, fixture.queueRepository.enqueueCalls)
        assertEquals(0, fixture.queueRepository.completeCalls)
    }

    @Test
    fun processingAndEditsPerformNoLocalCloudOrQueueMutationBeforeConfirmation() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReview(viewModel)

        viewModel.updatePlayerName(1, 1, "corrected")
        viewModel.resetPlayerCorrection(1, 1)
        viewModel.resetSlotCorrections(1)
        viewModel.resetAllCorrections()

        assertEquals(0, fixture.repository.replaceCalls)
        assertEquals(0, fixture.cloudRepository.calls)
        assertEquals(0, fixture.queueRepository.enqueueCalls)
        assertEquals(0, fixture.queueRepository.completeCalls)
    }

    @Test
    fun editsAndResetsUpdateImmutableReviewStateAndClearConfirmation() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReview(viewModel)

        viewModel.updatePlayerName(1, 1, "corrected")
        var reviewing = viewModel.uiState.value as RosterOcrReviewUiState.Reviewing
        assertEquals("corrected", reviewing.draft.slots.first().players.first().draftValue)
        viewModel.requestConfirmation()
        assertEquals(RosterOcrReviewConfirmationState.Requested, (viewModel.uiState.value as RosterOcrReviewUiState.Reviewing).confirmation)
        viewModel.updatePlayerName(1, 1, "corrected-again")
        reviewing = viewModel.uiState.value as RosterOcrReviewUiState.Reviewing
        assertEquals(RosterOcrReviewConfirmationState.NotRequested, reviewing.confirmation)
        viewModel.resetPlayerCorrection(1, 1)
        assertEquals("ocr-1-1", (viewModel.uiState.value as RosterOcrReviewUiState.Reviewing).draft.slots.first().players.first().draftValue)
    }

    @Test
    fun abandonmentReturnsReadyWithoutLocalOrCloudMutation() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReview(viewModel)

        viewModel.updatePlayerName(1, 1, "temporary")
        viewModel.abandonReview()

        assertTrue(viewModel.uiState.value is RosterOcrReviewUiState.ReadyToProcess)
        assertEquals(0, fixture.repository.replaceCalls)
        assertEquals(0, fixture.cloudRepository.calls)
    }

    @Test
    fun invalidDraftCannotRequestConfirmationAndValidDraftCanDismissIt() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReview(viewModel)

        viewModel.updatePlayerName(1, 1, "")
        viewModel.requestConfirmation()
        var reviewing = viewModel.uiState.value as RosterOcrReviewUiState.Reviewing
        assertEquals(RosterOcrReviewConfirmationState.NotRequested, reviewing.confirmation)
        viewModel.resetPlayerCorrection(1, 1)
        viewModel.requestConfirmation()
        reviewing = viewModel.uiState.value as RosterOcrReviewUiState.Reviewing
        assertEquals(RosterOcrReviewConfirmationState.Requested, reviewing.confirmation)
        viewModel.dismissConfirmation()
        assertEquals(RosterOcrReviewConfirmationState.NotRequested, (viewModel.uiState.value as RosterOcrReviewUiState.Reviewing).confirmation)
    }

    @Test
    fun confirmationRequiresExplicitRequestAndLocalSuccessPrecedesOneCloudCall() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReview(viewModel)

        viewModel.confirmReplacement()
        advanceUntilIdle()
        assertEquals(0, fixture.repository.replaceCalls)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        assertEquals(1, fixture.repository.replaceCalls)
        assertEquals(1, fixture.cloudRepository.calls)
        assertTrue(viewModel.uiState.value is RosterOcrReviewUiState.Completed)
    }

    @Test
    fun localFailuresAreDistinctAndBlockCloud() = runTest(dispatcher) {
        val expected = listOf(
            ReplaceConfirmedTournamentRosterResult.TournamentNotFound to RosterOcrReviewLocalReplacementError.TOURNAMENT_NOT_FOUND,
            ReplaceConfirmedTournamentRosterResult.InvalidCandidate to RosterOcrReviewLocalReplacementError.INVALID_CANDIDATE,
            ReplaceConfirmedTournamentRosterResult.BlockedByExistingMatches to RosterOcrReviewLocalReplacementError.BLOCKED_BY_EXISTING_MATCHES,
        )
        expected.forEach { (result, error) ->
            val fixture = fixture(localResult = result)
            val viewModel = fixture.viewModel()
            loadReview(viewModel)
            viewModel.requestConfirmation()
            viewModel.confirmReplacement()
            advanceUntilIdle()

            val reviewing = viewModel.uiState.value as RosterOcrReviewUiState.Reviewing
            assertEquals(RosterOcrLocalReplacementState.Failed(error), reviewing.localReplacement)
            assertEquals(0, fixture.cloudRepository.calls)
        }
    }

    @Test
    fun thrownLocalFailureIsControlledAndBlocksCloud() = runTest(dispatcher) {
        val fixture = fixture(localThrown = IllegalStateException("synthetic local failure"))
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        assertEquals(
            RosterOcrReviewLocalReplacementError.UNEXPECTED_FAILURE,
            ((viewModel.uiState.value as RosterOcrReviewUiState.Reviewing).localReplacement as RosterOcrLocalReplacementState.Failed).error,
        )
        assertEquals(0, fixture.cloudRepository.calls)
    }

    @Test
    fun cloudFailurePreservesLocalCommittedStateAndCloudOutcome() = runTest(dispatcher) {
        val fixture = fixture(
            cloudResult = TournamentRosterCloudReplacementResult.NetworkFailure,
        )
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        val committed = viewModel.uiState.value as RosterOcrReviewUiState.LocalReplacementCommitted
        val failed = committed.cloudSynchronization as RosterOcrCloudSynchronizationState.Failed
        assertEquals(TournamentRosterCloudReplacementResult.NetworkFailure, failed.result.primaryResult)
        assertEquals(1, fixture.repository.replaceCalls)
        assertEquals(1, fixture.cloudRepository.calls)
    }

    @Test
    fun everyReturnedCloudPrimaryResultIsPreservedWithoutReclassification() = runTest(dispatcher) {
        val conflict = TournamentRosterCloudReplacementResult.Conflict(
            RevisionConflict.StaleWrite(CloudRevision(1), CloudRevision(2)),
        )
        val results = listOf(
            TournamentRosterCloudReplacementResult.AuthenticationRequired,
            TournamentRosterCloudReplacementResult.NetworkFailure,
            TournamentRosterCloudReplacementResult.ValidationFailure,
            TournamentRosterCloudReplacementResult.BlockedByExistingMatches,
            TournamentRosterCloudReplacementResult.AuthorizationFailure,
            conflict,
            TournamentRosterCloudReplacementResult.UnknownFailure,
            TournamentRosterCloudReplacementResult.Success(2),
        )
        results.forEach { expected ->
            val fixture = fixture(cloudResult = expected)
            val viewModel = fixture.viewModel()
            loadReview(viewModel)
            viewModel.requestConfirmation()
            viewModel.confirmReplacement()
            advanceUntilIdle()

            if (expected is TournamentRosterCloudReplacementResult.Success) {
                assertEquals(expected, (viewModel.uiState.value as RosterOcrReviewUiState.Completed).cloudResult.primaryResult)
            } else {
                val committed = viewModel.uiState.value as RosterOcrReviewUiState.LocalReplacementCommitted
                val failed = committed.cloudSynchronization as RosterOcrCloudSynchronizationState.Failed
                assertEquals(expected, failed.result.primaryResult)
            }
            assertEquals(1, fixture.repository.replaceCalls)
            assertEquals(1, fixture.cloudRepository.calls)
        }
    }

    @Test
    fun thrownCloudExceptionHasDistinctStateAndCannotRepeatCommittedReplacement() = runTest(dispatcher) {
        val cloudInvoker = ThrowingCloudInvoker(IllegalStateException("synthetic cloud failure"))
        val fixture = fixture(cloudInvoker = cloudInvoker)
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RosterOcrReviewUiState.LocalReplacementCommitted)
        assertEquals(
            RosterOcrCloudSynchronizationState.UnexpectedFailure,
            (viewModel.uiState.value as RosterOcrReviewUiState.LocalReplacementCommitted).cloudSynchronization,
        )
        viewModel.updatePlayerName(1, 1, "must-not-change")
        viewModel.resetPlayerCorrection(1, 1)
        viewModel.resetSlotCorrections(1)
        viewModel.resetAllCorrections()
        viewModel.abandonReview()
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        assertEquals(1, fixture.repository.replaceCalls)
        assertEquals(1, cloudInvoker.calls)
        assertEquals(0, fixture.cloudRepository.calls)
    }

    @Test
    fun actionsCannotMutateOrRepeatAfterSuccessfulLocalCommit() = runTest(dispatcher) {
        val fixture = fixture(cloudResult = TournamentRosterCloudReplacementResult.NetworkFailure)
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        viewModel.updatePlayerName(1, 1, "must-not-change")
        viewModel.resetPlayerCorrection(1, 1)
        viewModel.resetSlotCorrections(1)
        viewModel.resetAllCorrections()
        viewModel.abandonReview()
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()

        assertEquals(1, fixture.repository.replaceCalls)
        assertEquals(1, fixture.cloudRepository.calls)
    }

    @Test
    fun changingContextPreventsStalePriorLoadFromPublishingState() = runTest(dispatcher) {
        val firstRepository = FakeTournamentRepository(tournament = tournament(TOURNAMENT_ID))
        val secondId = "synthetic-tournament-two"
        val fixture = fixture(repository = firstRepository)
        val viewModel = fixture.viewModel()

        viewModel.load(TOURNAMENT_ID)
        viewModel.load(secondId)
        advanceUntilIdle()

        assertEquals(secondId, (viewModel.uiState.value as RosterOcrReviewUiState.Unavailable).tournamentId)
    }

    @Test
    fun delayedOcrFromPriorTournamentCannotOverwriteNewContext() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(
            repository = FakeTournamentRepository(
                tournament = tournament(TOURNAMENT_ID),
                acceptAnyTournamentId = true,
            ),
            sourceGate = gate,
        )
        val viewModel = fixture.viewModel()
        loadReady(viewModel)
        viewModel.startProcessing()
        scheduler.runCurrent()
        viewModel.load("synthetic-tournament-two")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("synthetic-tournament-two", (viewModel.uiState.value as RosterOcrReviewUiState.ReadyToProcess).tournamentId)
    }

    @Test
    fun delayedLocalAndCloudResultsCannotPublishIntoNewContext() = runTest(dispatcher) {
        val localGate = CompletableDeferred<Unit>()
        val localFixture = fixture(
            repository = FakeTournamentRepository(
                tournament = tournament(TOURNAMENT_ID),
                acceptAnyTournamentId = true,
                replacementGate = localGate,
            ),
        )
        val localViewModel = localFixture.viewModel()
        loadReview(localViewModel)
        localViewModel.requestConfirmation()
        localViewModel.confirmReplacement()
        scheduler.runCurrent()
        localViewModel.load("synthetic-tournament-two")
        localGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("synthetic-tournament-two", (localViewModel.uiState.value as RosterOcrReviewUiState.ReadyToProcess).tournamentId)

        val cloudGate = CompletableDeferred<Unit>()
        val cloudFixture = fixture(
            repository = FakeTournamentRepository(
                tournament = tournament(TOURNAMENT_ID),
                acceptAnyTournamentId = true,
            ),
            cloudGate = cloudGate,
        )
        val cloudViewModel = cloudFixture.viewModel()
        loadReview(cloudViewModel)
        cloudViewModel.requestConfirmation()
        cloudViewModel.confirmReplacement()
        scheduler.runCurrent()
        assertTrue(cloudViewModel.uiState.value is RosterOcrReviewUiState.LocalReplacementCommitted)
        cloudViewModel.load("synthetic-tournament-two")
        cloudGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("synthetic-tournament-two", (cloudViewModel.uiState.value as RosterOcrReviewUiState.ReadyToProcess).tournamentId)
    }

    @Test
    fun cancellationDuringProcessingDoesNotBecomeUnexpectedFailure() = runTest(dispatcher) {
        val fixture = fixture(sourceCancellation = true)
        val viewModel = fixture.viewModel()
        loadReady(viewModel)
        viewModel.startProcessing()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RosterOcrReviewUiState.Processing)
    }

    @Test
    fun cancellationDuringLocalReplacementDoesNotBecomeLocalError() = runTest(dispatcher) {
        val fixture = fixture(localCancellation = true)
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        assertEquals(RosterOcrLocalReplacementState.InProgress, (viewModel.uiState.value as RosterOcrReviewUiState.Reviewing).localReplacement)
        assertEquals(0, fixture.cloudRepository.calls)
    }

    @Test
    fun cancellationDuringCloudKeepsLocalCommittedState() = runTest(dispatcher) {
        val fixture = fixture(cloudThrown = CancellationException("synthetic cloud cancellation"))
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        val committed = viewModel.uiState.value as RosterOcrReviewUiState.LocalReplacementCommitted
        assertEquals(RosterOcrCloudSynchronizationState.InProgress, committed.cloudSynchronization)
        assertEquals(1, fixture.repository.replaceCalls)
    }

    @Test
    fun duplicateConfirmationTapsCannotRepeatLocalOrCloudCalls() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        loadReview(viewModel)
        viewModel.requestConfirmation()
        viewModel.confirmReplacement()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        assertEquals(1, fixture.repository.replaceCalls)
        assertEquals(1, fixture.cloudRepository.calls)
    }

    private fun loadReady(viewModel: RosterOcrReviewViewModel) {
        viewModel.load(TOURNAMENT_ID)
        scheduler.runCurrent()
    }

    private fun loadReview(viewModel: RosterOcrReviewViewModel) {
        loadReady(viewModel)
        viewModel.startProcessing()
        scheduler.runCurrent()
        scheduler.advanceUntilIdle()
    }

    private fun fixture(
        repository: FakeTournamentRepository = FakeTournamentRepository(tournament = tournament(TOURNAMENT_ID)),
        sourceResult: RosterOcrSourceProviderResult = RosterOcrSourceProviderResult.Loaded(emptyList()),
        sourceGate: CompletableDeferred<Unit>? = null,
        sourceCancellation: Boolean = false,
        localResult: ReplaceConfirmedTournamentRosterResult = ReplaceConfirmedTournamentRosterResult.Replaced,
        cloudResult: TournamentRosterCloudReplacementResult = TournamentRosterCloudReplacementResult.Success(2),
        cloudThrown: Throwable? = null,
        cloudGate: CompletableDeferred<Unit>? = null,
        cloudInvoker: RosterOcrCloudReplacementInvoker? = null,
        localThrown: Throwable? = null,
        localCancellation: Boolean = false,
    ): Fixture {
        val evidence = validEvidence()
        val sourceProvider = CountingSourceProvider(
            result = if (sourceResult is RosterOcrSourceProviderResult.Loaded && sourceResult.sources.isEmpty()) {
                RosterOcrSourceProviderResult.Loaded(sources())
            } else sourceResult,
            gate = sourceGate,
            cancellation = sourceCancellation,
        )
        val cloudRepository = FakeCloudRepository(cloudResult, thrown = cloudThrown, gate = cloudGate)
        val queueRepository = FakeQueueRepository()
        val parser = EvidenceParser(evidence)
        val associator = EvidenceAssociator(evidence)
        val validator = EvidenceValidator(evidence)
        val process = ProcessRosterOcrUseCase(
            sourceProvider = sourceProvider,
            panelPreparer = FakePanelPreparer(),
            extractor = FakeExtractor(evidence),
            parser = parser,
            associator = associator,
            validator = validator,
        )
        val local = ReplaceConfirmedTournamentRosterUseCase(repository, RosterValidator())
        val cloud = ReplaceTournamentRosterInCloudUseCase(
            tournamentRepository = repository,
            authRepository = FakeAuthRepository(),
            cloudReplacementRepository = cloudRepository,
            cloudUploadRepository = object : TournamentCloudUploadRepository {
                override suspend fun upload(
                    snapshot: TournamentCloudUploadSnapshot,
                    ownerId: String,
                ) = TournamentCloudUploadResult.Success(2)
            },
            cloudRestorationRepository = object : TournamentCloudRestorationRepository {
                override suspend fun listOwnedTournaments() =
                    TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())

                override suspend fun readOwnedTournament(tournamentId: String) =
                    TournamentCloudRestorationRemoteResult.Failure(
                        TournamentCloudRestorationFailureCategory.NOT_FOUND,
                    )
            },
            queueRecorder = RecordSyncQueueOutcome(queueRepository),
        )
        return Fixture(
            repository = repository,
            sourceProvider = sourceProvider,
            cloudRepository = cloudRepository,
            evidence = evidence,
            getTournament = GetTournamentByIdUseCase(repository),
            observeSlots = ObserveTournamentSlotsUseCase(repository),
            process = process,
            local = local,
            cloud = cloud,
            parser = parser,
            associator = associator,
            validator = validator,
            queueRepository = queueRepository,
            cloudInvoker = cloudInvoker,
            localResult = localResult,
        )
            .also {
                repository.replacementThrowable = localThrown
                repository.replacementCancellation = localCancellation
            }
    }

    private data class Fixture(
        val repository: FakeTournamentRepository,
        val sourceProvider: CountingSourceProvider,
        val cloudRepository: FakeCloudRepository,
        val evidence: ProcessRosterOcrEvidence,
        val getTournament: GetTournamentByIdUseCase,
        val observeSlots: ObserveTournamentSlotsUseCase,
        val process: ProcessRosterOcrUseCase,
        val local: ReplaceConfirmedTournamentRosterUseCase,
        val cloud: ReplaceTournamentRosterInCloudUseCase,
        val parser: EvidenceParser,
        val associator: EvidenceAssociator,
        val validator: EvidenceValidator,
        val queueRepository: FakeQueueRepository,
        val cloudInvoker: RosterOcrCloudReplacementInvoker?,
        val localResult: ReplaceConfirmedTournamentRosterResult,
    ) {
        init {
            repository.replacementResult = localResult
        }

        fun viewModel(): RosterOcrReviewViewModel = cloudInvoker?.let { invoker ->
            RosterOcrReviewViewModel(
                getTournament,
                observeSlots,
                process,
                local,
                cloud,
                invoker,
            )
        } ?: RosterOcrReviewViewModel(
            getTournament,
            observeSlots,
            process,
            local,
            cloud,
        )
    }

    private class CountingSourceProvider(
        private val result: RosterOcrSourceProviderResult,
        private val gate: CompletableDeferred<Unit>? = null,
        private val cancellation: Boolean = false,
    ) : RosterOcrSourceProvider {
        var reads = 0
        override suspend fun load(tournamentId: String): RosterOcrSourceProviderResult {
            reads++
            if (cancellation) throw CancellationException("synthetic processing cancellation")
            gate?.await()
            return result
        }
    }

    private class FakePanelPreparer : RosterOcrPanelPreparer {
        override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult =
            RosterOcrPanelPreparationResult.Prepared(FakePanel)
    }

    private object FakePanel : RosterOcrPreparedPanel {
        override val croppedPanelImage: OcrPreprocessingImage = object : OcrPreprocessingImage {
            override val width: Int = 10
            override val height: Int = 10
        }
        override val croppedPanelInput = CroppedRosterPanelInput(
            screenshotPosition = RosterScreenshotPosition.ONE,
            isPreparedRosterCrop = true,
            imageWidth = 10,
            imageHeight = 10,
        )

        override fun release() = Unit
    }

    private class FakeExtractor(
        private val evidence: ProcessRosterOcrEvidence,
    ) : RosterRawOcrExtractor {
        var calls = 0
        override suspend fun extract(input: com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult> =
            evidence.rawExtractions.also { calls++ }
    }

    private class ThrowingCloudInvoker(
        private val thrown: Throwable,
    ) : RosterOcrCloudReplacementInvoker {
        var calls = 0
        override suspend fun invoke(tournamentId: String): com.hoggamers.rankforge.domain.sync.QueueAwareActionResult<TournamentRosterCloudReplacementResult> {
            calls++
            throw thrown
        }
    }

    private class EvidenceParser(
        private val evidence: ProcessRosterOcrEvidence,
    ) : RosterCandidateParser {
        var calls = 0
        override fun parse(input: com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput): RosterCandidateParseResult =
            evidence.parsedCandidates.also { calls++ }
    }

    private class EvidenceAssociator(
        private val evidence: ProcessRosterOcrEvidence,
    ) : com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator {
        var calls = 0
        override fun associate(input: com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationInput): RosterSlotAssociationResult =
            evidence.associatedCandidates.also { calls++ }
    }

    private class EvidenceValidator(
        private val evidence: ProcessRosterOcrEvidence,
    ) : RosterOcrValidator {
        var calls = 0
        override fun validate(input: RosterOcrValidationInput): RosterOcrValidationResult =
            evidence.validation.also { calls++ }
    }

    private class FakeCloudRepository(
        private val result: TournamentRosterCloudReplacementResult,
        private val thrown: Throwable? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : TournamentRosterCloudReplacementRepository {
        var calls = 0
        override suspend fun replace(
            snapshot: com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacement,
            ownerId: String,
        ): TournamentRosterCloudReplacementResult {
            calls++
            gate?.await()
            thrown?.let { throw it }
            return result
        }
    }

    private class FakeAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser("synthetic-user", "synthetic@example.test")),
        )

        override suspend fun restoreSession(): AuthRestorationResult =
            AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            error("unused")

        override suspend fun login(email: String, password: String): AuthOperationResult =
            error("unused")

        override suspend fun logout(): AuthOperationResult = error("unused")
    }

    private class FakeQueueRepository : PersistentSyncQueueRepository {
        var enqueueCalls = 0
        var completeCalls = 0

        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())
        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry {
            enqueueCalls++
            return SyncQueueEntry(
                id = "synthetic-queue",
                operationType = operationType,
                tournamentId = tournamentId,
                createdAtEpochMillis = 0L,
                status = status,
                failureCategory = failureCategory,
                attemptCount = 0,
            )
        }
        override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) {
            completeCalls++
        }
        override suspend fun incrementAttemptCount(id: String) = Unit
        override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
        override suspend fun markCompleted(id: String) = Unit
        override suspend fun remove(id: String) = Unit
    }

    private class FakeTournamentRepository(
        private val tournament: Tournament? = null,
        private val slots: List<TeamSlot> = tournament?.let { currentTournament ->
            TeamSlot.SLOT_NUMBERS.map { slot ->
                TeamSlot.create(currentTournament.id, slot, "room-team-$slot")
            }
        }
            ?: emptyList(),
        private val acceptAnyTournamentId: Boolean = false,
        private val replacementGate: CompletableDeferred<Unit>? = null,
    ) : TournamentRepository {
        var replacementResult: ReplaceConfirmedTournamentRosterResult = ReplaceConfirmedTournamentRosterResult.Replaced
        var replacementThrowable: Throwable? = null
        var replacementCancellation: Boolean = false
        var replaceCalls = 0

        override suspend fun create(tournament: Tournament) = Unit
        override fun observeAll(): Flow<List<Tournament>> = flowOf(listOfNotNull(tournament))
        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(
            tournament?.takeIf { acceptAnyTournamentId || it.id == tournamentId }?.copy(id = tournamentId),
        )
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(
            if (acceptAnyTournamentId || tournament?.id == tournamentId) {
                slots.map { slot -> slot.copy(tournamentId = tournamentId) }
            } else emptyList(),
        )
        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
        override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
        override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
        override suspend fun confirmTournament(tournamentId: String): Boolean = false
        override suspend fun replaceConfirmedTournamentRoster(
            candidate: com.hoggamers.rankforge.domain.tournament.ConfirmedRosterReplacementCandidate,
        ): com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult {
            replaceCalls++
            if (replacementCancellation) throw CancellationException("synthetic local cancellation")
            replacementGate?.await()
            replacementThrowable?.let { throw it }
            return when (replacementResult) {
                ReplaceConfirmedTournamentRosterResult.Replaced -> com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult.Replaced
                ReplaceConfirmedTournamentRosterResult.TournamentNotFound -> com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult.TournamentNotFound
                ReplaceConfirmedTournamentRosterResult.InvalidCandidate -> com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult.InvalidCandidate
                ReplaceConfirmedTournamentRosterResult.BlockedByExistingMatches -> com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult.BlockedByExistingMatches
            }
        }
    }

    private fun validEvidence(): ProcessRosterOcrEvidence {
        val candidates = (1..12).map { slot ->
            val screenshot = when (slot) {
                in 1..4 -> RosterScreenshotPosition.ONE
                in 5..8 -> RosterScreenshotPosition.TWO
                else -> RosterScreenshotPosition.THREE
            }
            val visible = RosterVisibleSlotPosition.entries[(slot - 1) % 4]
            RosterTournamentSlotCandidate(
                tournamentSlotNumber = slot,
                sourceScreenshotPosition = screenshot,
                sourceVisibleSlotPosition = visible,
                teamNameCandidate = RosterTeamNameCandidate(
                    status = RosterCandidateParseStatus.UNSUPPORTED,
                    failure = RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
                    rawSourceResults = emptyList(),
                    confidence = RawOcrConfidence.Unavailable,
                ),
                playerNameCandidates = (1..4).map { row ->
                    RosterPlayerNameCandidate(
                        regionIdentity = RosterRawOcrRegionIdentity(
                            screenshotPosition = screenshot,
                            visibleSlotPosition = visible,
                            regionType = RosterRawOcrRegionType.PLAYER_ROW,
                            playerRowIndex = row,
                        ),
                        playerRowIndex = row,
                        status = RosterCandidateParseStatus.PARSED,
                        candidateText = "ocr-$slot-$row",
                        failure = null,
                        rawSourceResults = emptyList(),
                        confidence = RawOcrConfidence.Unavailable,
                    )
                },
                associationStatus = com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationStatus.ASSOCIATED,
            )
        }
        val association = RosterSlotAssociationResult(candidates, emptyList())
        return ProcessRosterOcrEvidence(
            rawExtractions = emptyList(),
            parsedCandidates = RosterCandidateParseResult(emptyList(), emptyList()),
            associatedCandidates = association,
            validation = RosterOcrValidationResult(
                status = RosterOcrValidationStatus.READY_FOR_REVIEW,
                slotResults = emptyList(),
                globalIssues = emptyList(),
            ),
        )
    }

    private fun sources(): List<RosterOcrScreenshotSource> = RosterScreenshotPosition.entries.map { position ->
        RosterOcrScreenshotSource(
            tournamentId = TOURNAMENT_ID,
            rosterScreenshotIndex = position.index,
            screenshotPosition = position,
            localRelativePath = com.hoggamers.rankforge.domain.ocr.review.RosterOcrLocalRelativePath("synthetic-${position.index}"),
            sourceWidth = 10,
            sourceHeight = 10,
            cropLeft = 0.0,
            cropTop = 0.0,
            cropRight = 1.0,
            cropBottom = 1.0,
        )
    }

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Synthetic tournament",
        date = LocalDate.of(2026, 1, 1),
        organizerName = "Synthetic organizer",
        organizerContactNumber = "synthetic-contact",
        status = TournamentStatus.DRAFT,
    )

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
    }
}
