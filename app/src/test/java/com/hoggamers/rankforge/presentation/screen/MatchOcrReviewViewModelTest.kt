package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReadResult
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReader
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidenceOrigin
import com.hoggamers.rankforge.data.local.LobbyCalculatedEvidence
import com.hoggamers.rankforge.data.local.LobbyTeamCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
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
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftState
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftMutationResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationValidator
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.FinalizeGroupRotationMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveGroupRotationMatchDraftUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationMatchParticipantResultsUseCase
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContextReadResult
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContextRepository
import com.hoggamers.rankforge.domain.tournament.ReadMatchTeamIdentityContextUseCase
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
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNotNull
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
    fun loadInitializesCalculatingStateFromRouteArguments() {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.load("synthetic-tournament", "synthetic-match")

        val state = viewModel.uiState.value
        assertTrue(state is MatchOcrReviewUiState.Calculating)
        state as MatchOcrReviewUiState.Calculating
        assertEquals("synthetic-tournament", state.tournamentId)
        assertEquals("synthetic-match", state.matchId)
    }

    @Test
    fun uuidFinalizeSavesLatestReviewedResultBeforeFinalization() = runTest(dispatcher) {
        val row = correctionRows().first()
        val correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(listOf(row))
        val snapshot = GroupRotationMatchIdentitySnapshot(
            tournamentId = TOURNAMENT_ID,
            pairing = GroupPairing.fromCanonicalKey("A:B"),
            teams = listOf(GroupRotationMatchTeamIdentity(1, "uuid-alpha", "Alpha")),
        )
        val draftRepository = RecordingGroupRotationMatchDraftRepository(
            GroupRotationMatchDraftState(snapshot, emptyList(), emptyMap()),
        )
        val finalizationRepository = RecordingGroupRotationMatchFinalizationRepository()
        val viewModel = viewModelWith(
            repository = InMemoryTournamentRepository(),
            initialUiState = MatchOcrReviewUiState.Ready(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
                isUuidBackedGroupRotation = true,
                rowCount = 1,
                rows = listOf(row),
                blockerCount = correctionDraft.blockerCount,
                warningCount = correctionDraft.warningCount,
                safeRowCount = 1,
                manualRequiredRowCount = 0,
                reviewRequiredRowCount = 0,
                manualReviewRequired = false,
                hasUnavailableEvidence = false,
                correctionDraft = correctionDraft,
                teamNamesBySlot = mapOf(1 to "Alpha"),
            ),
            observeGroupRotationMatchDraft = ObserveGroupRotationMatchDraftUseCase(
                draftRepository,
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
            saveGroupRotationMatchParticipantResults = SaveGroupRotationMatchParticipantResultsUseCase(
                draftRepository,
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
            finalizeGroupRotationMatch = FinalizeGroupRotationMatchUseCase(
                finalizationRepository,
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val expected = GroupRotationMatchParticipantResult(
            teamUUID = "uuid-alpha",
            participationStatus = MatchParticipationStatus.PARTICIPATED,
            placement = 1,
            kills = 0,
        )
        assertEquals(listOf(expected), draftRepository.savedParticipantResults)
        assertEquals(listOf(expected), finalizationRepository.input?.participantResults)
        assertTrue((viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun uuidSlotSwapUsesFinalLobbyAssignmentsForExactUuidResults() = runTest(dispatcher) {
        val snapshot = uuidSnapshot(listOf(1, 2))
        val existing = listOf(
            GroupRotationMatchParticipantResult("uuid-1", MatchParticipationStatus.PARTICIPATED, 1, 10, 3),
            GroupRotationMatchParticipantResult("uuid-2", MatchParticipationStatus.PARTICIPATED, 2, 20, 4),
        )
        val result = runUuidFinalize(snapshot, existing, transform = { initial ->
            initial
                .let { MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(it, 0, "2") }
                .let { MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(it, 1, "1") }
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 0, "1") }
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 1, "2") }
                .let { MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 0, "11") }
                .let { MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 1, "21") }
        })
        advanceUntilIdle()

        val expected = listOf(
            GroupRotationMatchParticipantResult("uuid-1", MatchParticipationStatus.PARTICIPATED, 2, 21, 3),
            GroupRotationMatchParticipantResult("uuid-2", MatchParticipationStatus.PARTICIPATED, 1, 11, 4),
        )
        assertEquals(expected, result.draftRepository.savedParticipantResults)
        assertEquals(expected, result.finalizationRepository.input?.participantResults)
    }

    @Test
    fun uuidSlotSwapProducesSameResultsWhenCorrectionRowsAreReordered() = runTest(dispatcher) {
        val snapshot = uuidSnapshot(listOf(1, 2))
        val existing = listOf(
            GroupRotationMatchParticipantResult("uuid-1", MatchParticipationStatus.PARTICIPATED, 1, 10),
            GroupRotationMatchParticipantResult("uuid-2", MatchParticipationStatus.PARTICIPATED, 2, 20),
        )
        val swap: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft = { initial ->
            initial
                .let { MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(it, 0, "2") }
                .let { MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(it, 1, "1") }
                .let { MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 0, "11") }
                .let { MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 1, "21") }
        }
        val ordered = runUuidFinalize(snapshot, existing, transform = swap)
        val reordered = runUuidFinalize(snapshot, existing, transform = { initial ->
            swap(initial).let { draft -> draft.copy(rows = draft.rows.reversed()) }
        })
        advanceUntilIdle()

        assertEquals(ordered.draftRepository.savedParticipantResults, reordered.draftRepository.savedParticipantResults)
        assertEquals(ordered.finalizationRepository.input?.participantResults, reordered.finalizationRepository.input?.participantResults)
    }

    @Test
    fun uuidDuplicateCorrectedLobbySlotsPreventSaveAndFinalization() = runTest(dispatcher) {
        val result = runUuidFinalize(uuidSnapshot(listOf(1, 2)), emptyList(), transform = { initial ->
            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initial, 0, "2")
        })
        advanceUntilIdle()

        assertNull(result.draftRepository.savedParticipantResults)
        assertNull(result.finalizationRepository.input)
        assertFalse((result.viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun uuidBlankCorrectedLobbySlotPreventsSaveAndFinalization() = runTest(dispatcher) {
        val result = runUuidFinalize(uuidSnapshot(listOf(1, 2)), emptyList(), transform = { initial ->
            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initial, 0, "")
        })
        advanceUntilIdle()

        assertNull(result.draftRepository.savedParticipantResults)
        assertNull(result.finalizationRepository.input)
        assertFalse((result.viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun uuidSparseTenTeamPairingProducesOneExactResultPerSnapshotTeam() = runTest(dispatcher) {
        val lobbies = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 12)
        val snapshot = uuidSnapshot(lobbies)
        val existing = snapshot.teams.mapIndexed { index, team ->
            GroupRotationMatchParticipantResult(
                teamUUID = team.teamUUID,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = index + 1,
                kills = index,
            )
        }
        val result = runUuidFinalize(snapshot, existing)
        advanceUntilIdle()

        assertEquals(existing, result.draftRepository.savedParticipantResults)
        assertEquals(existing, result.finalizationRepository.input?.participantResults)
    }

    @Test
    fun uuidTwelveTeamPairingUsesContinuousPlacementsWithoutDeletion() = runTest(dispatcher) {
        val snapshot = uuidSnapshot((1..12).toList())
        val existing = snapshot.teams.mapIndexed { index, team ->
            GroupRotationMatchParticipantResult(
                teamUUID = team.teamUUID,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = index + 1,
                kills = index,
            )
        }

        val result = runUuidFinalize(snapshot, existing)
        advanceUntilIdle()

        assertEquals(existing, result.draftRepository.savedParticipantResults)
        assertEquals(existing, result.finalizationRepository.input?.participantResults)
        assertUuidResultsPassFinalizationValidator(snapshot, existing)
    }

    @Test
    fun uuidCorrectedPositionSwapIsAuthoritativeForFinalization() = runTest(dispatcher) {
        val snapshot = uuidSnapshot(listOf(1, 2))
        val result = runUuidFinalize(snapshot, emptyList(), transform = { initial ->
            initial
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 0, "2") }
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 1, "1") }
        })
        advanceUntilIdle()

        val saved = requireNotNull(result.draftRepository.savedParticipantResults)
        assertEquals(2, saved.single { it.teamUUID == "uuid-1" }.placement)
        assertEquals(1, saved.single { it.teamUUID == "uuid-2" }.placement)
        assertEquals(saved, result.finalizationRepository.input?.participantResults)
        assertUuidResultsPassFinalizationValidator(snapshot, saved)
    }

    @Test
    fun uuidDeletedMiddleRowReindexesPlacementsAndPreservesIdentityData() = runTest(dispatcher) {
        val snapshot = uuidSnapshot((1..12).toList())
        val existing = snapshot.teams.mapIndexed { index, team ->
            GroupRotationMatchParticipantResult(
                teamUUID = team.teamUUID,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = index + 1,
                kills = index + 10,
                pointAdjustment = index + 100,
            )
        }

        val result = runUuidFinalize(snapshot, existing, transform = { initial ->
            MatchOcrReviewCorrectionDraftReducer.onRowExcluded(initial, rowIndex = 4)
        })
        advanceUntilIdle()

        val saved = requireNotNull(result.draftRepository.savedParticipantResults)
        assertEquals((1..11).toList(), saved
            .filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
            .mapNotNull { it.placement })
        assertEquals(
            GroupRotationMatchParticipantResult(
                teamUUID = "uuid-5",
                participationStatus = MatchParticipationStatus.NO_SHOW,
                placement = null,
                kills = 0,
                pointAdjustment = 104,
            ),
            saved.single { it.teamUUID == "uuid-5" },
        )
        assertEquals((listOf(0, 1, 2, 3) + (5..11).toList()), saved
            .filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
            .map { it.kills })
        assertEquals((100..111).toList(), saved.map { it.pointAdjustment })
        assertEquals(saved, result.finalizationRepository.input?.participantResults)
        assertUuidResultsPassFinalizationValidator(snapshot, saved)
    }

    @Test
    fun uuidMultipleFirstMiddleAndLastDeletionsRemainContinuous() = runTest(dispatcher) {
        val snapshot = uuidSnapshot((1..12).toList())
        val result = runUuidFinalize(snapshot, emptyList(), transform = { initial ->
            initial
                .let { MatchOcrReviewCorrectionDraftReducer.onRowExcluded(it, rowIndex = 0) }
                .let { MatchOcrReviewCorrectionDraftReducer.onRowExcluded(it, rowIndex = 5) }
                .let { MatchOcrReviewCorrectionDraftReducer.onRowExcluded(it, rowIndex = 11) }
        })
        advanceUntilIdle()

        val saved = requireNotNull(result.draftRepository.savedParticipantResults)
        assertEquals((1..9).toList(), saved
            .filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
            .mapNotNull { it.placement })
        assertEquals(
            setOf("uuid-1", "uuid-6", "uuid-12"),
            saved.filter { it.participationStatus == MatchParticipationStatus.NO_SHOW }
                .map { it.teamUUID }
                .toSet(),
        )
        assertTrue((result.finalizationRepository.input?.participantResults ?: emptyList())
            .filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
            .mapNotNull { it.placement }
            .all { it in 1..9 })
        assertUuidResultsPassFinalizationValidator(snapshot, saved)
    }

    @Test
    fun uuidDeletedUnassignedStructuralRowsAreIgnored() = runTest(dispatcher) {
        val snapshot = uuidSnapshot((1..10).toList())
        val registeredRows = uuidCorrectionRows((1..10).toList())
        val structuralRows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows().drop(10)
        val rows = registeredRows + structuralRows
        val result = runUuidFinalize(
            snapshot = snapshot,
            existing = emptyList(),
            rows = rows,
            transform = { initial ->
                initial
                    .let { MatchOcrReviewCorrectionDraftReducer.onRowExcluded(it, rowIndex = 10) }
                    .let { MatchOcrReviewCorrectionDraftReducer.onRowExcluded(it, rowIndex = 11) }
            },
        )
        advanceUntilIdle()

        val saved = requireNotNull(result.draftRepository.savedParticipantResults)
        assertEquals(10, saved.size)
        assertEquals((1..10).toList(), saved.mapNotNull { it.placement })
        assertEquals((1..10).map { "uuid-$it" }, saved.map { it.teamUUID })
        assertTrue((result.finalizationRepository.input?.participantResults ?: emptyList()).isNotEmpty())
        assertUuidResultsPassFinalizationValidator(snapshot, saved)
    }

    @Test
    fun uuidFirstTimeManualEntryWithTenTeamsAndTwoDeletedEmptyRowsFinalizes() = runTest(dispatcher) {
        val snapshot = uuidSnapshot((1..10).toList())
        val result = runUuidFinalize(
            snapshot = snapshot,
            existing = emptyList(),
            rows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows(),
            transform = { initial ->
                var draft = initial
                (0 until 10).forEach { index ->
                    draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(
                        draft, index, (index + 1).toString(),
                    )
                    draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(
                        draft, index, index.toString(),
                    )
                    draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(
                        draft, index, (index + 1).toString(),
                    )
                }
                MatchOcrReviewCorrectionDraftReducer.onRowExcluded(
                    MatchOcrReviewCorrectionDraftReducer.onRowExcluded(draft, 10), 11,
                )
            },
        )
        advanceUntilIdle()

        val expected = (1..10).map { position ->
            GroupRotationMatchParticipantResult(
                teamUUID = "uuid-$position",
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = position,
                kills = position - 1,
            )
        }
        assertEquals(expected, result.draftRepository.savedParticipantResults)
        assertEquals(expected, result.finalizationRepository.input?.participantResults)
        assertTrue((result.viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
        assertUuidResultsPassFinalizationValidator(snapshot, expected)
    }

    @Test
    fun uuidFirstTimeManualEntryWithUndeletedEmptyRowCannotFinalize() = runTest(dispatcher) {
        val snapshot = uuidSnapshot((1..10).toList())
        val result = runUuidFinalize(
            snapshot = snapshot,
            existing = emptyList(),
            rows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows(),
            transform = { initial ->
                var draft = initial
                (0 until 10).forEach { index ->
                    draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(
                        draft, index, (index + 1).toString(),
                    )
                    draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(
                        draft, index, index.toString(),
                    )
                    draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(
                        draft, index, (index + 1).toString(),
                    )
                }
                MatchOcrReviewCorrectionDraftReducer.onRowExcluded(draft, 11)
            },
        )
        advanceUntilIdle()

        assertNull(result.draftRepository.savedParticipantResults)
        assertNull(result.finalizationRepository.input)
        assertFalse((result.viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun uuidNoShowAndPointAdjustmentRemainAttachedToPermanentUuid() = runTest(dispatcher) {
        val snapshot = uuidSnapshot(listOf(1, 2))
        val existing = listOf(
            GroupRotationMatchParticipantResult("uuid-1", MatchParticipationStatus.NO_SHOW, null, 0, 7),
            GroupRotationMatchParticipantResult("uuid-2", MatchParticipationStatus.PARTICIPATED, 2, 8, 9),
        )
        val result = runUuidFinalize(snapshot, existing, transform = { initial ->
            initial
                .let { MatchOcrReviewCorrectionDraftReducer.onRowExcluded(it, 0) }
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 1, "1") }
                .let { MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 1, "4") }
        })
        advanceUntilIdle()

        val expected = listOf(
            GroupRotationMatchParticipantResult("uuid-1", MatchParticipationStatus.NO_SHOW, null, 0, 7),
            GroupRotationMatchParticipantResult("uuid-2", MatchParticipationStatus.PARTICIPATED, 1, 4, 9),
        )
        assertEquals(expected, result.draftRepository.savedParticipantResults)
        assertEquals(expected, result.finalizationRepository.input?.participantResults)
    }

    @Test
    fun uuidInvalidSnapshotSlotPreventsSaveAndFinalization() = runTest(dispatcher) {
        val result = runUuidFinalize(uuidSnapshot(listOf(1, 2)), emptyList(), transform = { initial ->
            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initial, 0, "3")
        })
        advanceUntilIdle()

        assertNull(result.draftRepository.savedParticipantResults)
        assertNull(result.finalizationRepository.input)
        assertFalse((result.viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun uuidDraftPersistenceFailurePreventsFinalization() = runTest(dispatcher) {
        val result = runUuidFinalize(
            snapshot = uuidSnapshot(listOf(1, 2)),
            existing = emptyList(),
            saveResult = GroupRotationMatchDraftMutationResult.INVALID_DATA,
        )
        advanceUntilIdle()

        assertNotNull(result.draftRepository.savedParticipantResults)
        assertNull(result.finalizationRepository.input)
        assertFalse((result.viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
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
        assertTrue(state is MatchOcrReviewUiState.Calculating)
        state as MatchOcrReviewUiState.Calculating
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
    fun calculatedEvidenceRestoresReviewValuesWithoutRunningOcr() = runTest(dispatcher) {
        var resultRunnerCalls = 0
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(InMemoryTournamentRepository()),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                resultRunnerCalls++
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            initialUiState = MatchOcrReviewUiState.Loading,
            screenshotOwnerProvider = ownerProvider,
        )

        viewModel.restoreCalculatedEvidence(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            evidence = calculatedEvidenceForRestoreTest(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val row = state.rows.single()
        val preview = state.matchResultOcrPreview as MatchResultOcrPreviewUiState.Ready
        assertEquals(MatchOcrReviewEvidenceSource.RESTORED_CALCULATED, state.evidenceSource)
        assertEquals(
            "P1 Saved Player, P2 Not detected, P3 Not detected, P4 Not detected",
            row.detectedPlayerNameEvidenceLabel,
        )
        assertFalse(row.allPlayersSemanticallyNotDetected)
        assertEquals("12", row.suggestedTeamSlotDisplayValue)
        assertEquals("7", row.detectedKillDisplayValue)
        assertEquals(
            listOf("Saved Player", "Not detected", "Not detected", "Not detected"),
            preview.rows.single().slots.map { it.playerText },
        )
        assertEquals(listOf("2", "", "", ""), preview.rows.single().slots.map { it.killText })
        assertEquals(listOf(1, 2, 3, 4), row.playerKillEvidence.map { it.playerSlot })
        assertEquals(
            listOf(true, false, false, false),
            row.playerKillEvidence.map { it.isPlayerDetected },
        )
        assertEquals(listOf(1, 2, 3, 4), state.correctionDraft!!.rows.single().playerKillDrafts.map { it.playerSlot })
        assertEquals(0, resultRunnerCalls)
    }

    @Test
    fun restoredManualPositionKeepsIdentityAcrossDeleteAndAdd() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.restoreCalculatedEvidence(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            evidence = calculatedEvidenceWithManualPosition12(),
        )
        advanceUntilIdle()

        var ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(setOf(12), ready.manuallyRevealedPositions)

        viewModel.onExcludeRow(11)
        ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.manuallyRevealedPositions.isEmpty())
        assertFalse(ready.correctionDraft!!.rows[11].isExcluded)

        viewModel.onCompactAddTeam()
        ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val manualDraft = ready.correctionDraft!!.rows[11]
        assertEquals(setOf(12), ready.manuallyRevealedPositions)
        assertEquals("", manualDraft.placementDraftValue)
        assertEquals("", manualDraft.killsDraftValue)
        assertEquals("", manualDraft.assignedTeamSlotDraftValue)
        assertFalse(manualDraft.isExcluded)
        assertTrue(manualDraft.playerKillDrafts.isEmpty())
    }

    @Test
    fun manualCorrectedPositionSurvivesDeleteAndRestore() {
        var correctionDraft = requireNotNull(manualReadyState().correctionDraft)
        (0 until TeamSlot.MAX_SLOT_NUMBER).forEach { rowIndex ->
            correctionDraft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(
                correctionDraft,
                rowIndex,
                "0",
            )
            correctionDraft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(
                correctionDraft,
                rowIndex,
                (rowIndex + 1).toString(),
            )
        }
        correctionDraft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(correctionDraft, 0, "2")
        correctionDraft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(correctionDraft, 1, "1")
        val viewModel = viewModelWith(
            repository = InMemoryTournamentRepository(),
            initialUiState = manualReadyState(correctionDraft),
        )

        viewModel.onExcludeRow(0)
        viewModel.onCompactAddTeam()

        val restored = (viewModel.uiState.value as MatchOcrReviewUiState.Ready).correctionDraft
            ?: error("Expected correction draft")
        assertEquals(listOf("2", "1", "3"), restored.rows.take(3).map { it.placementDraftValue })
        assertFalse(restored.rows[0].isExcluded)
        assertEquals(0, restored.blockerCount)
    }

    @Test
    fun fullyAbsentFreshPreviewRowIsEffectivelyExcluded() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            rows = emptyList(),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

        val row = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!!.last()
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(listOf(row))

        assertTrue(row.allPlayersSemanticallyNotDetected)
        assertTrue(draft.rows.single().isImplicitlyAbsent)
        assertEquals(0, draft.blockerCount)
    }

    @Test
    fun fullyAbsentRestoredCalculatedRowIsEffectivelyExcluded() = runTest(dispatcher) {
        val evidence = calculatedEvidenceForRestoreTest().copy(
            result = ResultCalculatedEvidence(
                positions = listOf(
                    ResultPositionCalculatedEvidence(
                        position = 12,
                        sourceScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                        slotNumber = null,
                        playerNames = List(4) { "Not detected" },
                        playerKills = List(4) { null },
                        totalKills = null,
                        placement = null,
                        playerKillApplicable = List(4) { false },
                    ),
                ),
            ),
        )
        val viewModel = MatchOcrReviewViewModel(createFinalizeUseCase(InMemoryTournamentRepository()))

        viewModel.restoreCalculatedEvidence(TOURNAMENT_ID, MATCH_ID, evidence)
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.rows.single().allPlayersSemanticallyNotDetected)
        assertTrue(ready.correctionDraft!!.rows.single().isImplicitlyAbsent)
        assertEquals(0, ready.correctionDraft.blockerCount)
    }

    @Test
    fun completeResultCacheWithoutLobbyRestoresResultOnlyReviewState() = runTest(dispatcher) {
        val repository = createRepository()
        val cachedResult = MatchOcrCacheReadResult(
            availability = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE,
            resultRoleResults = completeResultRoleResults(),
            lobbyResult = MatchLobbyPlayersOcrResult.unavailable(),
        )
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ -> cachedResult },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(state.matchResultOcrPreview is MatchResultOcrPreviewUiState.Ready)
        assertEquals(12, state.rows.size)
        assertTrue(state.lobbyPlayers.isEmpty())
        assertTrue(state.rows.all { it.originalSuggestedTeamSlot == null })
        assertTrue(state.rows.none { it.resultLobbyVoteEvidencePresent })
        assertTrue(state.correctionDraft!!.rows.all { it.assignedTeamSlotDraftValue.isBlank() })
        assertTrue(state.rows.all { it.playerKillEvidence.isNotEmpty() })
    }

    @Test
    fun cachedResultWithMissingPlayerKeepsLogicalPlayerKillSlots() = runTest(dispatcher) {
        val partialResult = completeResultRoleResults().map { roleResult ->
            if (roleResult.role != MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                roleResult
            } else {
                val processed = roleResult.result as MatchResultOcrPreviewProcessingResult.Processed
                roleResult.copy(
                    result = processed.copy(
                        extraction = processed.extraction.copy(
                            rows = processed.extraction.rows.map { row ->
                                if (row.position == 1) row.copy(playerSlots = row.playerSlots.take(3)) else row
                            },
                        ),
                    ),
                )
            }
        }
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(createRepository()),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(
                    availability = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE,
                    resultRoleResults = partialResult,
                    lobbyResult = MatchLobbyPlayersOcrResult.unavailable(),
                )
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(listOf(1, 2, 3, 4), state.rows.first().playerKillEvidence.map { it.playerSlot })
    }

    @Test
    fun completeResultCacheDoesNotEraseAnExistingReadyStateWhenLobbyIsMissing() = runTest(dispatcher) {
        val repository = createRepository()
        val resultRoleResults = completeResultRoleResults()
        val initialState = readyState().copy(
            matchResultOcrPreview = MatchResultOcrPreviewUiStateMapper.map(resultRoleResults),
        )
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(
                    availability = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE,
                    resultRoleResults = resultRoleResults,
                    lobbyResult = MatchLobbyPlayersOcrResult.unavailable(),
                )
            },
            initialUiState = initialState,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertEquals(initialState, viewModel.uiState.value)
        assertTrue(viewModel.uiState.value is MatchOcrReviewUiState.Ready)
        assertTrue(
            (viewModel.uiState.value as MatchOcrReviewUiState.Ready).matchResultOcrPreview is
                MatchResultOcrPreviewUiState.Ready,
        )
    }

    @Test
    fun completeCombinedCacheStillUsesLobbyMatching() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(
                    availability = MatchOcrCacheAvailability.READY,
                    resultRoleResults = completeResultRoleResultsWithUniqueLobbyMatch(),
                    lobbyResult = completeLobbyResultForMatching(),
                )
            },
            initialUiState = MatchOcrReviewUiState.Loading,
            screenshotOwnerProvider = ownerProvider,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(state.lobbyPlayers.isNotEmpty())
        assertTrue(state.rows.first().resultLobbyVoteEvidencePresent)
        assertEquals(12, state.rows.first().originalSuggestedTeamSlot)
    }

    @Test
    fun missingResultCacheDoesNotFabricateResultReadyState() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value !is MatchOcrReviewUiState.Ready)
        assertTrue(viewModel.uiState.value is MatchOcrReviewUiState.Loading)
    }

    @Test
    fun matchingReadyStateSurvivesCacheReadFailure() = runTest(dispatcher) {
        val repository = createRepository()
        val initialState = readyState()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                error("synthetic cache read failure")
            },
            initialUiState = initialState,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun matchingReadyStateSurvivesUnavailableCache() = runTest(dispatcher) {
        val repository = createRepository()
        val initialState = readyState()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
            },
            initialUiState = initialState,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun matchingReadyStateSurvivesIncompleteCache() = runTest(dispatcher) {
        val repository = createRepository()
        val initialState = readyState()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.STALE_OR_INCOMPLETE)
            },
            initialUiState = initialState,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun sameMatchReadyRowsSuppressCalculatedEvidenceCacheFallback() {
        val state = readyState()

        listOf(
            CalculatedEvidenceRestoreStatus.NOT_FOUND,
            CalculatedEvidenceRestoreStatus.FAILED,
            CalculatedEvidenceRestoreStatus.NOT_REQUESTED,
        ).forEach { restoreStatus ->
            assertFalse(
                shouldLoadCachedForCalculatedEvidenceRestore(
                    restoreStatus = restoreStatus,
                    ocrUiState = state,
                    tournamentId = TOURNAMENT_ID,
                    matchId = MATCH_ID,
                ),
            )
        }
    }

    @Test
    fun sameMatchReadyPreviewRowsSuppressCalculatedEvidenceCacheFallback() {
        val state = readyState(rows = emptyList(), correctionDraft = null).copy(
            matchResultOcrPreview = MatchResultOcrPreviewUiState.Ready(
                roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                rows = listOf(
                    MatchResultOcrPreviewRowUiState(
                        position = 1,
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        sourceLabel = "Synthetic",
                        placementText = "1",
                        slots = emptyList(),
                    ),
                ),
                ignoredLowerRows = emptyList(),
                manualReviewRows = emptyList(),
            ),
        )

        assertFalse(
            shouldLoadCachedForCalculatedEvidenceRestore(
                restoreStatus = CalculatedEvidenceRestoreStatus.NOT_FOUND,
                ocrUiState = state,
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            ),
        )
    }

    @Test
    fun differentMatchReadyStateDoesNotSuppressCalculatedEvidenceCacheFallback() {
        assertTrue(
            shouldLoadCachedForCalculatedEvidenceRestore(
                restoreStatus = CalculatedEvidenceRestoreStatus.NOT_FOUND,
                ocrUiState = readyState().copy(matchId = "other-match"),
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            ),
        )
    }

    @Test
    fun nonDisplayableOrLoadingStateUsesCalculatedEvidenceCacheFallback() {
        listOf(
            MatchOcrReviewUiState.Loading,
            MatchOcrReviewUiState.Empty(TOURNAMENT_ID, MATCH_ID),
            readyState(rows = emptyList(), correctionDraft = null),
        ).forEach { state ->
            assertTrue(
                shouldLoadCachedForCalculatedEvidenceRestore(
                    restoreStatus = CalculatedEvidenceRestoreStatus.FAILED,
                    ocrUiState = state,
                    tournamentId = TOURNAMENT_ID,
                    matchId = MATCH_ID,
                ),
            )
        }
    }

    @Test
    fun nonFallbackRestoreStatusesDoNotRequestCalculatedEvidenceCacheFallback() {
        listOf(
            CalculatedEvidenceRestoreStatus.RESTORED,
            CalculatedEvidenceRestoreStatus.CLEARED,
            CalculatedEvidenceRestoreStatus.CHECKING,
        ).forEach { restoreStatus ->
            assertFalse(
                shouldLoadCachedForCalculatedEvidenceRestore(
                    restoreStatus = restoreStatus,
                    ocrUiState = readyState(),
                    tournamentId = TOURNAMENT_ID,
                    matchId = MATCH_ID,
                ),
            )
        }
    }

    @Test
    fun emptyStateStillSurvivesUnavailableCache() = runTest(dispatcher) {
        val repository = createRepository()
        val initialState = MatchOcrReviewUiState.Empty(TOURNAMENT_ID, MATCH_ID)
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
            },
            initialUiState = initialState,
        )

        viewModel.loadCached(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun clearCalculatedEvidenceDisplayStillClearsMatchingReadyState() = runTest(dispatcher) {
        val repository = createRepository()
        val initialState = readyState()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
            },
            initialUiState = initialState,
        )

        viewModel.clearCalculatedEvidenceDisplay(TOURNAMENT_ID, MATCH_ID)

        assertEquals(
            MatchOcrReviewUiState.Empty(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
                teamNamesBySlot = initialState.teamNamesBySlot,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun clearCalculatedEvidenceDisplayIgnoresDifferentMatch() = runTest(dispatcher) {
        val repository = createRepository()
        val initialState = readyState()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
            },
            initialUiState = initialState,
        )

        viewModel.clearCalculatedEvidenceDisplay(TOURNAMENT_ID, "different-match")

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun repeatedLoadForSameRouteKeepsDeterministicCalculatingState() {
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

            val state = viewModel.uiState.value as MatchOcrReviewUiState.Calculating
            assertEquals(TOURNAMENT_ID, state.tournamentId)
            assertEquals(MATCH_ID, state.matchId)
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

    @Test
    fun groupRotationLiveOcrUsesPersistedLobbyIdentityContext() = runTest(dispatcher) {
        val repository = createGroupRotationRepository()
        val resultRoleResults = groupRotationResultRoleResults()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
                resultRoleResults.first { it.role == identity.role }.result
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                groupRotationLobbyEvidence()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            initialUiState = MatchOcrReviewUiState.Loading,
            tournamentRepository = repository,
            readMatchTeamIdentityContext = ReadMatchTeamIdentityContextUseCase(
                repository = repository,
                authRepository = com.hoggamers.rankforge.domain.tournament
                    .SignedInTournamentTestAuthRepository(),
            ),
        )

        viewModel.load(GROUP_TOURNAMENT_ID, GROUP_MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(18, state.rows.first().originalSuggestedTeamSlot)
        assertEquals("18", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
        assertEquals(3, state.lobbySlotByTeamSlot[18])
        assertEquals(
            setOf(1, 2, 3, 4, 5, 6, 13, 14, 15, 16, 17, 18),
            state.eligibleTeamSlots,
        )
        assertTrue(state.usesPairRelativeIdentity)
    }

    @Test
    fun groupRotationCachedOcrUsesTheSamePersistedIdentityContext() = runTest(dispatcher) {
        val repository = createGroupRotationRepository()
        val cached = MatchOcrCacheReadResult(
            availability = MatchOcrCacheAvailability.READY,
            resultRoleResults = groupRotationResultRoleResults(),
            lobbyResult = groupRotationLobbyEvidence(),
        )
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                MatchLobbyPlayersOcrResult.unavailable()
            },
            matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                phase1SlotNumberResult()
            },
            matchOcrCacheReader = MatchOcrCacheReader { _, _ -> cached },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            tournamentRepository = repository,
            readMatchTeamIdentityContext = ReadMatchTeamIdentityContextUseCase(
                repository = repository,
                authRepository = com.hoggamers.rankforge.domain.tournament
                    .SignedInTournamentTestAuthRepository(),
            ),
        )

        viewModel.loadCached(GROUP_TOURNAMENT_ID, GROUP_MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(18, state.rows.first().originalSuggestedTeamSlot)
        assertEquals(3, state.lobbySlotByTeamSlot[18])
        assertTrue(state.usesPairRelativeIdentity)
    }

    @Test
    fun groupRotationManualReviewResolvesIdentityBeforeCreatingDraft() = runTest(dispatcher) {
        val repository = createGroupRotationRepository()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                MatchLobbyPlayersOcrResult.unavailable()
            },
            matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                phase1SlotNumberResult()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            tournamentRepository = repository,
            readMatchTeamIdentityContext = ReadMatchTeamIdentityContextUseCase(
                repository = repository,
                authRepository = com.hoggamers.rankforge.domain.tournament
                    .SignedInTournamentTestAuthRepository(),
            ),
        )

        viewModel.openManualReview(GROUP_TOURNAMENT_ID, GROUP_MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(
            setOf(1, 2, 3, 4, 5, 6, 13, 14, 15, 16, 17, 18),
            state.eligibleTeamSlots,
        )
        assertEquals(3, state.lobbySlotByTeamSlot[18])
        assertTrue(state.usesPairRelativeIdentity)
        assertFalse(state.isUuidBackedGroupRotation)
        assertTrue(state.correctionDraft != null)
    }

    @Test
    fun firstOpenUuidBackedGroupRotationManualReviewUsesUuidFinalization() = runTest(dispatcher) {
        val repository = createGroupRotationRepository()
        val snapshot = uuidSnapshot(
            lobbySlots = (1..12).toList(),
            tournamentId = GROUP_TOURNAMENT_ID,
            pairing = groupRotationPairing(),
        )
        val draftRepository = RecordingGroupRotationMatchDraftRepository(
            GroupRotationMatchDraftState(snapshot, emptyList(), emptyMap()),
        )
        val finalizationRepository = RecordingGroupRotationMatchFinalizationRepository()
        val authRepository = com.hoggamers.rankforge.domain.tournament
            .SignedInTournamentTestAuthRepository()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                MatchLobbyPlayersOcrResult.unavailable()
            },
            matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                phase1SlotNumberResult()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            tournamentRepository = repository,
            readMatchTeamIdentityContext = ReadMatchTeamIdentityContextUseCase(
                repository = repository,
                authRepository = authRepository,
            ),
            observeGroupRotationMatchDraft = ObserveGroupRotationMatchDraftUseCase(
                draftRepository,
                authRepository,
            ),
            saveGroupRotationMatchParticipantResults = SaveGroupRotationMatchParticipantResultsUseCase(
                draftRepository,
                authRepository,
            ),
            finalizeGroupRotationMatch = FinalizeGroupRotationMatchUseCase(
                finalizationRepository,
                authRepository,
            ),
        )

        viewModel.openManualReview(GROUP_TOURNAMENT_ID, GROUP_MATCH_ID)
        advanceUntilIdle()

        val opened = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(opened.isUuidBackedGroupRotation)
        assertEquals((1..12).associateWith { it }, opened.lobbySlotByTeamSlot)

        (0 until 12).forEach { rowIndex ->
            viewModel.onPlacementChanged(rowIndex, (rowIndex + 1).toString())
            viewModel.onKillsChanged(rowIndex, "0")
            viewModel.onAssignedTeamSlotChanged(rowIndex, (rowIndex + 1).toString())
        }
        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        assertNotNull(finalizationRepository.input)
        assertEquals(
            finalizationRepository.input!!.participantResults,
            draftRepository.savedParticipantResults,
        )
        assertEquals(
            MatchStatus.DRAFT,
            repository.observeMatchById(GROUP_MATCH_ID).first()!!.status,
        )
        assertTrue((viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun standardManualReviewRemainsOnLegacyFinalizationPath() = runTest(dispatcher) {
        val repository = createRepository()
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                MatchLobbyPlayersOcrResult.unavailable()
            },
            matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                phase1SlotNumberResult()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            tournamentRepository = repository,
        )

        viewModel.openManualReview(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertFalse(state.isUuidBackedGroupRotation)
    }

    @Test
    fun groupRotationMissingIdentityFailsClosedWithoutStandardFallback() = runTest(dispatcher) {
        val repository = createGroupRotationRepository()
        val unavailableIdentity = ReadMatchTeamIdentityContextUseCase(
            repository = object : MatchTeamIdentityContextRepository {
                override suspend fun readForMatch(
                    matchId: String,
                    ownerUserId: String,
                ): MatchTeamIdentityContextReadResult = MatchTeamIdentityContextReadResult.SetupRequired

                override suspend fun readForPairing(
                    tournamentId: String,
                    pairing: GroupPairing,
                    ownerUserId: String,
                ): MatchTeamIdentityContextReadResult = MatchTeamIdentityContextReadResult.SetupRequired
            },
            authRepository = com.hoggamers.rankforge.domain.tournament
                .SignedInTournamentTestAuthRepository(),
        )
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            },
            matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
                MatchLobbyPlayersOcrResult.unavailable()
            },
            matchLobbySlotNumberOcrRunner = MatchLobbySlotNumberOcrRunner { _, _ ->
                phase1SlotNumberResult()
            },
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            tournamentRepository = repository,
            readMatchTeamIdentityContext = unavailableIdentity,
        )

        viewModel.openManualReview(GROUP_TOURNAMENT_ID, GROUP_MATCH_ID)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is MatchOcrReviewUiState.Error)
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
    fun compactAddTeamRevealsTheNextPositionAfterVisibleOcrCrops() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..6))

        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(setOf(7), ready.manuallyRevealedPositions)
        val manualDraft = ready.correctionDraft!!.rows[6]
        assertEquals("", manualDraft.placementDraftValue)
        assertEquals("", manualDraft.killsDraftValue)
        assertEquals("", manualDraft.assignedTeamSlotDraftValue)
        assertFalse(manualDraft.isExcluded)
        assertTrue(manualDraft.playerKillDrafts.isEmpty())
    }

    @Test
    fun compactAddTeamRevealsPositionsSequentially() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..6))

        viewModel.onCompactAddTeam()
        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(setOf(7, 8), ready.manuallyRevealedPositions)
    }

    @Test
    fun deletingManuallyRevealedPositionRemovesItWithoutExcludingIt() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..6))

        viewModel.onCompactAddTeam()
        viewModel.onExcludeRow(6)
        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(setOf(7), ready.manuallyRevealedPositions)
        assertFalse(ready.correctionDraft!!.rows[6].isExcluded)
    }

    @Test
    fun deletingAndReaddingManualPositionClearsTemporaryDraftValues() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..11))

        viewModel.onCompactAddTeam()
        viewModel.onPlacementChanged(11, "12")
        viewModel.onKillsChanged(11, "7")
        viewModel.onAssignedTeamSlotChanged(11, "5")
        viewModel.onExcludeRow(11)
        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val manualDraft = ready.correctionDraft!!.rows[11]
        assertEquals(setOf(12), ready.manuallyRevealedPositions)
        assertEquals("", manualDraft.placementDraftValue)
        assertEquals("", manualDraft.killsDraftValue)
        assertEquals("", manualDraft.assignedTeamSlotDraftValue)
        assertFalse(manualDraft.isExcluded)
        assertTrue(manualDraft.playerKillDrafts.isEmpty())
    }

    @Test
    fun repeatedManualAddDeleteCyclesKeepTheSameFreshPosition() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..11))

        repeat(3) {
            viewModel.onCompactAddTeam()
            viewModel.onExcludeRow(11)
        }
        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(setOf(12), ready.manuallyRevealedPositions)
        assertFalse(ready.correctionDraft!!.rows[11].isExcluded)
    }

    @Test
    fun compactAddTeamUsesTheExistingCorrectionDraftForManuallyRevealedRows() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..6))

        viewModel.onCompactAddTeam()
        viewModel.onPlacementChanged(6, "7")
        viewModel.onKillsChanged(6, "2")
        viewModel.onAssignedTeamSlotChanged(6, "7")

        val manualDraft = (viewModel.uiState.value as MatchOcrReviewUiState.Ready)
            .correctionDraft!!
            .rows[6]
        assertEquals("7", manualDraft.placementDraftValue)
        assertEquals("2", manualDraft.killsDraftValue)
        assertEquals("7", manualDraft.assignedTeamSlotDraftValue)
    }

    @Test
    fun compactAddTeamDoesNothingWhenAllTwelvePositionsAreAlreadyUsed() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = automaticReadyState(1..12))

        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(ready.manuallyRevealedPositions.isEmpty())
        assertNull(ready.nextManualResultPositionOrNull())
    }

    @Test
    fun manualCalculationWithAllRowsActiveHasNoAddTeamCandidate() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = manualReadyState())

        val before = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertNull(before.nextAddTeamPositionOrNull())

        viewModel.onCompactAddTeam()

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun manualCalculationWithoutCorrectionDraftCannotAddTeam() = runTest(dispatcher) {
        val viewModel = viewModelWith(
            repository = createRepository(),
            initialUiState = manualReadyState(correctionDraft = null),
        )

        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertNull(ready.nextAddTeamPositionOrNull())
        assertNull(ready.correctionDraft)
    }

    @Test
    fun manualCalculationRestoresDeletedLastPositionWithoutManualReveal() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = manualReadyState())

        viewModel.onExcludeRow(11)
        var ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(12, ready.nextAddTeamPositionOrNull())
        assertTrue(ready.correctionDraft!!.rows[11].isExcluded)

        viewModel.onCompactAddTeam()

        ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val restored = ready.correctionDraft!!.rows[11]
        assertEquals("12", restored.placementDraftValue)
        assertEquals("", restored.killsDraftValue)
        assertEquals("", restored.assignedTeamSlotDraftValue)
        assertFalse(restored.isExcluded)
        assertTrue(ready.manuallyRevealedPositions.isEmpty())
        assertNull(ready.nextAddTeamPositionOrNull())
    }

    @Test
    fun manualCalculationRestoresDeletedPositionWithInvalidCorrectionBlocked() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = manualReadyState())

        viewModel.onPlacementChanged(4, "99")
        viewModel.onKillsChanged(4, "7")
        viewModel.onAssignedTeamSlotChanged(4, "6")
        viewModel.onExcludeRow(4)
        viewModel.onCompactAddTeam()

        val restored = (viewModel.uiState.value as MatchOcrReviewUiState.Ready)
            .correctionDraft!!
            .rows[4]
        assertEquals("99", restored.placementDraftValue)
        assertEquals("7", restored.killsDraftValue)
        assertEquals("6", restored.assignedTeamSlotDraftValue)
        assertFalse(restored.isExcluded)
        assertTrue(restored.validation.blockers.contains(MatchOcrReviewCorrectionReason.INVALID_PLACEMENT))
    }

    @Test
    fun manualCalculationRestoresDeletedPositionsInStructuralOrder() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = manualReadyState())

        viewModel.onExcludeRow(7)
        viewModel.onExcludeRow(2)
        viewModel.onExcludeRow(10)

        assertEquals(3, (viewModel.uiState.value as MatchOcrReviewUiState.Ready).nextAddTeamPositionOrNull())
        viewModel.onCompactAddTeam()
        assertEquals(8, (viewModel.uiState.value as MatchOcrReviewUiState.Ready).nextAddTeamPositionOrNull())
        viewModel.onCompactAddTeam()
        assertEquals(11, (viewModel.uiState.value as MatchOcrReviewUiState.Ready).nextAddTeamPositionOrNull())
        viewModel.onCompactAddTeam()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertNull(ready.nextAddTeamPositionOrNull())
        assertTrue(ready.manuallyRevealedPositions.isEmpty())
        assertTrue(ready.correctionDraft!!.rows.none { it.isExcluded })
    }

    @Test
    fun manualCalculationCanRepeatDeleteAndRestoreAtCapacity() = runTest(dispatcher) {
        val viewModel = viewModelWith(repository = createRepository(), initialUiState = manualReadyState())

        repeat(3) {
            viewModel.onExcludeRow(4)
            assertEquals(5, (viewModel.uiState.value as MatchOcrReviewUiState.Ready).nextAddTeamPositionOrNull())
            viewModel.onCompactAddTeam()
        }

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertNull(ready.nextAddTeamPositionOrNull())
        assertFalse(ready.correctionDraft!!.rows[4].isExcluded)
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
    fun warningsFinalizeImmediatelyWithoutConfirmation() = runTest(dispatcher) {
        val repository = createRepository()
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = warningDraft))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertFalse(ready.finalization.showWarningConfirmation)
        assertTrue(ready.finalization.isFinalized)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
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
    fun standardCorrectedPositionSwapIsAuthoritativeForFinalizationAndEvidence() = runTest(dispatcher) {
        val repository = createRepository()
        val draft = correctionDraft { initial ->
            initial
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 0, "2") }
                .let { MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 1, "1") }
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = draft))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val finalized = repository.observeMatchById(MATCH_ID).first()!!
        val positionsByTeamSlot = finalized.placements.associateBy { it.teamSlotNumber }
        assertEquals(2, positionsByTeamSlot.getValue(1).position)
        assertEquals(1, positionsByTeamSlot.getValue(2).position)
        val evidence = repository.readPreservedOcrEvidence(MATCH_ID)!!
        assertEquals(2, evidence.correctionSnapshots.first { it.rowIndex == 0 }.correctedPlacement)
        assertEquals(1, evidence.correctionSnapshots.first { it.rowIndex == 1 }.correctedPlacement)
        assertTrue(evidence.correctionSnapshots.first { it.rowIndex == 0 }.placementChanged)
        assertTrue((viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
    }

    @Test
    fun standardOcrPositionCorrectionIsSavedAsTheOfficialPlacement() = runTest(dispatcher) {
        val repository = createRepository()
        val rows = correctionRows().map { row ->
            if (row.rowIndex == 0) {
                row.copy(
                    detectedPlacementDisplayValue = "2",
                    originalParsedPlacementValue = 2,
                )
            } else {
                row
            }
        }
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        val draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft, 0, "1")
        val viewModel = viewModelWith(repository, readyState(correctionDraft = draft, rows = rows))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val finalized = repository.observeMatchById(MATCH_ID).first()!!
        assertEquals(1, finalized.placements.single { it.teamSlotNumber == 1 }.position)
        val evidence = repository.readPreservedOcrEvidence(MATCH_ID)!!
        assertEquals(2, evidence.rows.single { it.rowIndex == 0 }.originalPlacement)
        val correction = evidence.correctionSnapshots.single { it.rowIndex == 0 }
        assertEquals(1, correction.correctedPlacement)
        assertTrue(correction.placementChanged)
    }

    @Test
    fun standardDeletedRowsUseCorrectedPositionOrderForOfficialPlacements() = runTest(dispatcher) {
        val repository = createRepository(activeTeamCount = 11)
        val draft = correctionDraft { initial ->
            val corrected = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(
                MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initial, 5, "7"),
                6,
                "6",
            )
            MatchOcrReviewCorrectionDraftReducer.onRowExcluded(corrected, 11)
        }
        val viewModel = viewModelWith(repository, readyState(correctionDraft = draft))

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val finalized = repository.observeMatchById(MATCH_ID).first()!!
        val positionsByTeamSlot = finalized.placements.associateBy { it.teamSlotNumber }
        assertEquals(7, positionsByTeamSlot.getValue(6).position)
        assertEquals(6, positionsByTeamSlot.getValue(7).position)
        assertEquals((1..11).toList(), finalized.placements.map { it.position }.sorted())
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
    fun excludedMiddleSourceRowProducesContiguousOfficialPlacements() = runTest(dispatcher) {
        val repository = createRepository(activeTeamCount = 11)
        val rows = correctionRows().map { row ->
            if (row.rowIndex == 11) {
                row.copy(
                    suggestedTeamSlotDisplayValue = "4",
                    originalSuggestedTeamSlot = 4,
                )
            } else {
                row
            }
        }
        val draft = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(
            MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows),
            rowIndex = 3,
        )
        val viewModel = viewModelWith(
            repository,
            readyState(correctionDraft = draft, rows = rows),
        )

        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val finalized = repository.observeMatchById(MATCH_ID).first()!!
        val positionsByTeamSlot = finalized.placements.associateBy { it.teamSlotNumber }
        assertEquals(11, finalized.placements.size)
        assertEquals((1..11).toList(), finalized.placements.map { it.position }.sorted())
        assertEquals(4, positionsByTeamSlot.getValue(5).position)
        assertEquals(11, positionsByTeamSlot.getValue(4).position)
    }

    @Test
    fun fullyAbsentRowsRemainStructuralInputsAndFinalizeAsExcludedForEightTeams() = runTest(dispatcher) {
        val repository = createRepository(activeTeamCount = 8)
        val rows = correctionRows().map { row ->
            if (row.rowIndex >= 8) {
                row.copy(
                    detectedPlacementDisplayValue = "",
                    detectedKillDisplayValue = "",
                    detectedPlayerNameEvidenceLabel = "Unavailable",
                    suggestedTeamSlotDisplayValue = "",
                    originalParsedPlacementValue = null,
                    originalParsedKillValue = null,
                    originalSuggestedTeamSlot = null,
                    allPlayersSemanticallyNotDetected = true,
                )
            } else {
                row
            }
        }
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        val viewModel = viewModelWith(repository, readyState(correctionDraft = draft, rows = rows))

        assertEquals(12, draft.rows.size)
        assertEquals(0, draft.blockerCount)
        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()

        val finalized = repository.observeMatchById(MATCH_ID).first()!!
        val evidence = repository.readPreservedOcrEvidence(MATCH_ID)!!
        assertTrue((viewModel.uiState.value as MatchOcrReviewUiState.Ready).finalization.isFinalized)
        assertEquals(8, finalized.placements.size)
        assertEquals(8, finalized.kills.size)
        assertEquals(12, evidence.rows.size)
        assertEquals((0..7).toList(), evidence.correctionSnapshots.map { it.rowIndex })
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
        observeGroupRotationMatchDraft: ObserveGroupRotationMatchDraftUseCase? = null,
        saveGroupRotationMatchParticipantResults: SaveGroupRotationMatchParticipantResultsUseCase? = null,
        finalizeGroupRotationMatch: FinalizeGroupRotationMatchUseCase? = null,
    ): MatchOcrReviewViewModel =
        MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(repository),
            finalizedMatchCloudSync = finalizedMatchCloudSync,
            initialUiState = initialUiState,
            screenshotOwnerProvider = ownerProvider,
            observeGroupRotationMatchDraft = observeGroupRotationMatchDraft,
            saveGroupRotationMatchParticipantResults = saveGroupRotationMatchParticipantResults,
            finalizeGroupRotationMatch = finalizeGroupRotationMatch,
        )

    private val ownerProvider = object : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String =
            com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository.OWNER_USER_ID
    }

    private class RecordingGroupRotationMatchDraftRepository(
        private val state: GroupRotationMatchDraftState,
        private val saveResult: GroupRotationMatchDraftMutationResult =
            GroupRotationMatchDraftMutationResult.SAVED,
    ) : GroupRotationMatchDraftRepository {
        var savedParticipantResults: List<GroupRotationMatchParticipantResult>? = null

        override fun observeByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
        ): Flow<GroupRotationMatchDraftReadResult> = flowOf(
            GroupRotationMatchDraftReadResult.Loaded(state),
        )

        override suspend fun saveValueByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
            lobbySlotNumber: Int,
            placementInput: String?,
            killsInput: String?,
            pointAdjustment: Int?,
        ): GroupRotationMatchDraftMutationResult = GroupRotationMatchDraftMutationResult.SAVED

        override suspend fun savePlacementsByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
            placementsByLobbySlot: Map<Int, String>,
        ): GroupRotationMatchDraftMutationResult = GroupRotationMatchDraftMutationResult.SAVED

        override suspend fun saveKillsByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
            killsByLobbySlot: Map<Int, String>,
        ): GroupRotationMatchDraftMutationResult = GroupRotationMatchDraftMutationResult.SAVED

        override suspend fun saveParticipantResultsByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
            participantResults: List<GroupRotationMatchParticipantResult>,
        ): GroupRotationMatchDraftMutationResult {
            savedParticipantResults = participantResults
            return saveResult
        }

        override suspend fun savePointAdjustmentByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
            lobbySlotNumber: Int,
            pointAdjustment: Int,
        ): GroupRotationMatchDraftMutationResult = GroupRotationMatchDraftMutationResult.SAVED

        override suspend fun clearByOwner(
            tournamentId: String,
            matchId: String,
            ownerUserId: String,
        ): GroupRotationMatchDraftMutationResult = GroupRotationMatchDraftMutationResult.SAVED
    }

    private class RecordingGroupRotationMatchFinalizationRepository :
        GroupRotationMatchFinalizationRepository {
        var input: com.hoggamers.rankforge.domain.tournament.FinalizeGroupRotationMatchInput? = null

        override suspend fun finalizeGroupRotationMatchByOwner(
            input: com.hoggamers.rankforge.domain.tournament.FinalizeGroupRotationMatchInput,
            ownerUserId: String,
        ): GroupRotationMatchFinalizationResult {
            this.input = input
            val match = Match(
                id = input.matchId,
                tournamentId = input.tournamentId,
                matchNumber = 1,
                date = LocalDate.of(2026, 1, 1),
                mapName = "Bermuda",
                status = MatchStatus.FINALIZED,
                groupPairing = GroupPairing.fromCanonicalKey("A:B"),
            )
            return GroupRotationMatchFinalizationResult.Finalized(
                match = match,
                snapshot = GroupRotationMatchPersistenceSnapshot(
                    identity = GroupRotationMatchIdentitySnapshot(
                        tournamentId = input.tournamentId,
                        pairing = GroupPairing.fromCanonicalKey("A:B"),
                        teams = listOf(GroupRotationMatchTeamIdentity(1, "uuid-alpha", "Alpha")),
                    ),
                    participantResults = input.participantResults,
                ),
            )
        }
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
                stageName = "Organizer",
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

    private suspend fun createGroupRotationRepository(): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        val pairing = groupRotationPairing()
        repository.create(
            Tournament(
                id = GROUP_TOURNAMENT_ID,
                name = "Group Rotation Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
                ownerUserId = com.hoggamers.rankforge.domain.tournament
                    .SignedInTournamentTestAuthRepository.OWNER_USER_ID,
                format = TournamentFormat.GROUP_ROTATION,
                groupCount = 3,
                selectedGroupPairings = listOf(pairing),
            ),
        )
        repository.saveTeamNames(
            tournamentId = GROUP_TOURNAMENT_ID,
            teamNamesBySlotNumber = (1..18).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.replaceGroupRotationPairingLobbySlots(groupRotationLobbyMappings())
        repository.createDraftMatch(
            Match(
                id = GROUP_MATCH_ID,
                tournamentId = GROUP_TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
                groupPairing = pairing,
            ),
        )
        return repository
    }

    private fun groupRotationPairing(): GroupPairing =
        GroupPairing(TournamentGroup.A, TournamentGroup.C)

    private fun groupRotationLobbyMappings(): List<GroupRotationPairingLobbySlot> =
        listOf(14, 3, 18, 13, 16, 5, 6, 15, 2, 17, 4, 1).mapIndexed { index, teamSlotNumber ->
            GroupRotationPairingLobbySlot(
                tournamentId = GROUP_TOURNAMENT_ID,
                pairing = groupRotationPairing(),
                lobbySlotNumber = index + 1,
                teamSlotNumber = teamSlotNumber,
            )
        }

    private fun groupRotationPlayerNames(): List<String> =
        listOf("Phoenix One", "Phoenix Two", "Phoenix Three", "Phoenix Four")

    private fun groupRotationLobbyEvidence(): MatchLobbyPlayersOcrResult =
        MatchLobbyPlayersOcrResult(
            slots = (1..12).map { lobbySlotNumber ->
                MatchLobbyPlayersOcrSlot(
                    slotNumber = lobbySlotNumber,
                    players = if (lobbySlotNumber == 3) {
                        groupRotationPlayerNames().mapIndexed { index, playerName ->
                            MatchLobbyPlayersOcrPlayer(index + 1, playerName)
                        }
                    } else {
                        (1..4).map { playerNumber ->
                            MatchLobbyPlayersOcrPlayer(playerNumber, null)
                        }
                    },
                )
            },
        )

    private suspend fun groupRotationResultRoleResults(): List<MatchResultOcrPreviewRoleResult> =
        completeResultRoleResults().map { roleResult ->
            val processed = roleResult.result as MatchResultOcrPreviewProcessingResult.Processed
            roleResult.copy(
                result = processed.copy(
                    extraction = processed.extraction.copy(
                        rows = processed.extraction.rows.map { row ->
                            if (row.position == 1) {
                                row.copy(
                                    playerSlots = row.playerSlots.mapIndexed { index, playerSlot ->
                                        playerSlot.copy(
                                            player = playerSlot.player.copy(
                                                ocrText = groupRotationPlayerNames()[index],
                                                resolvedText = groupRotationPlayerNames()[index],
                                            ),
                                        )
                                    },
                                )
                            } else {
                                row
                            }
                        },
                    ),
                ),
            )
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

    private data class UuidFinalizeRecording(
        val viewModel: MatchOcrReviewViewModel,
        val draftRepository: RecordingGroupRotationMatchDraftRepository,
        val finalizationRepository: RecordingGroupRotationMatchFinalizationRepository,
    )

    private fun runUuidFinalize(
        snapshot: GroupRotationMatchIdentitySnapshot,
        existing: List<GroupRotationMatchParticipantResult>,
        rows: List<MatchOcrReviewRowUiState> = uuidCorrectionRows(snapshot.teams.map { it.lobbySlotNumber }),
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft = { it },
        saveResult: GroupRotationMatchDraftMutationResult = GroupRotationMatchDraftMutationResult.SAVED,
    ): UuidFinalizeRecording {
        val correctionDraft = transform(
            MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows),
        )
        val draftRepository = RecordingGroupRotationMatchDraftRepository(
            GroupRotationMatchDraftState(snapshot, existing, emptyMap()),
            saveResult,
        )
        val finalizationRepository = RecordingGroupRotationMatchFinalizationRepository()
        val viewModel = viewModelWith(
            repository = InMemoryTournamentRepository(),
            initialUiState = readyState(
                correctionDraft = correctionDraft,
                rows = rows,
            ).copy(
                rowCount = rows.size,
                blockerCount = correctionDraft.blockerCount,
                warningCount = correctionDraft.warningCount,
                safeRowCount = rows.size,
                isUuidBackedGroupRotation = true,
            ),
            observeGroupRotationMatchDraft = ObserveGroupRotationMatchDraftUseCase(
                draftRepository,
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
            saveGroupRotationMatchParticipantResults = SaveGroupRotationMatchParticipantResultsUseCase(
                draftRepository,
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
            finalizeGroupRotationMatch = FinalizeGroupRotationMatchUseCase(
                finalizationRepository,
                com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository(),
            ),
        )
        viewModel.onFinalizeOcrCorrection()
        return UuidFinalizeRecording(viewModel, draftRepository, finalizationRepository)
    }

    private fun assertUuidResultsPassFinalizationValidator(
        snapshot: GroupRotationMatchIdentitySnapshot,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ) {
        val issues = GroupRotationMatchFinalizationValidator().validate(snapshot, participantResults)
        assertTrue(issues.isEmpty())
        assertFalse(
            issues.any {
                it.code == com.hoggamers.rankforge.domain.tournament
                    .GroupRotationMatchFinalizationIssueCode.PLACEMENTS_NOT_GAP_FREE
            },
        )
    }

    private fun uuidSnapshot(
        lobbySlots: List<Int>,
        tournamentId: String = TOURNAMENT_ID,
        pairing: GroupPairing = GroupPairing.fromCanonicalKey("A:B"),
    ): GroupRotationMatchIdentitySnapshot =
        GroupRotationMatchIdentitySnapshot(
            tournamentId = tournamentId,
            pairing = pairing,
            teams = lobbySlots.map { lobbySlot ->
                GroupRotationMatchTeamIdentity(
                    lobbySlotNumber = lobbySlot,
                    teamUUID = "uuid-$lobbySlot",
                    displayName = "Team $lobbySlot",
                )
            },
        )

    private fun uuidCorrectionRows(lobbySlots: List<Int>): List<MatchOcrReviewRowUiState> =
        correctionRows().take(lobbySlots.size).mapIndexed { index, row ->
            row.copy(
                rowIndex = index,
                expectedPlacementLabel = (index + 1).toString(),
                detectedPlacementDisplayValue = (index + 1).toString(),
                detectedKillDisplayValue = index.toString(),
                suggestedTeamSlotDisplayValue = lobbySlots[index].toString(),
                originalParsedPlacementValue = index + 1,
                originalParsedKillValue = index,
                originalSuggestedTeamSlot = lobbySlots[index],
            )
        }

    private fun automaticReadyState(
        visiblePositions: IntRange,
    ): MatchOcrReviewUiState.Ready {
        val role = MatchResultScreenshotRole.MATCH_RESULT_UPPER
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(role),
            rows = visiblePositions.map { position ->
                MatchResultOcrPreviewRowUiState(
                    position = position,
                    role = role,
                    sourceLabel = "SYNTHETIC",
                    placementText = position.toString(),
                    slots = emptyList(),
                )
            },
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
            authoritativePositionCropsByRole = mapOf(
                role to visiblePositions.map { position ->
                    MatchResultPositionCrop(
                        position = position,
                        column = MatchResultPositionColumn.LEFT,
                        bounds = OcrPixelCropRect(0, 0, 1, 1),
                        hasDetectedPositionAnchor = true,
                    )
                },
            ),
        )
        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!!
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        return MatchOcrReviewUiState.Ready(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            rowCount = rows.size,
            rows = rows,
            blockerCount = draft.blockerCount,
            warningCount = draft.warningCount,
            safeRowCount = 0,
            manualRequiredRowCount = rows.size,
            reviewRequiredRowCount = 0,
            manualReviewRequired = true,
            hasUnavailableEvidence = true,
            correctionDraft = draft,
            matchResultOcrPreview = preview,
        )
    }

    private fun manualReadyState(
        correctionDraft: MatchOcrReviewCorrectionDraft? = seededManualCorrectionDraft(),
    ): MatchOcrReviewUiState.Ready = readyState(
        correctionDraft = correctionDraft,
        rows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows(),
    ).copy(
        calculatedEvidenceOrigin = MatchCalculatedEvidenceOrigin.MANUAL,
    )

    private fun seededManualCorrectionDraft(): MatchOcrReviewCorrectionDraft {
        val rows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows()
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        return MatchOcrReviewCorrectionDraftReducer.validate(
            initialDraft.copy(
                rows = initialDraft.rows.map { row ->
                    val expectedPlacement = (row.rowIndex + 1).toString()
                    row.copy(
                        originalPlacementValue = expectedPlacement,
                        placementDraftValue = expectedPlacement,
                    )
                },
            ),
        )
    }

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

    private suspend fun completeResultRoleResults(): List<MatchResultOcrPreviewRoleResult> =
        MatchResultScreenshotRole.entries.map { role ->
            MatchResultOcrPreviewRoleResult(
                role = role,
                result = completePreviewRunner().process(
                    MatchResultScreenshotIdentity(
                        tournamentId = TOURNAMENT_ID,
                        matchId = MATCH_ID,
                        role = role,
                    ),
                ),
            )
        }

    private suspend fun completeResultRoleResultsWithUniqueLobbyMatch():
        List<MatchResultOcrPreviewRoleResult> = completeResultRoleResults().map { roleResult ->
        val processed = roleResult.result as MatchResultOcrPreviewProcessingResult.Processed
        roleResult.copy(
            result = processed.copy(
                extraction = processed.extraction.copy(
                    rows = processed.extraction.rows.map { row ->
                        if (row.position == 1) {
                            row
                        } else {
                            row.copy(
                                playerSlots = row.playerSlots.map { playerSlot ->
                                    playerSlot.copy(
                                        player = playerSlot.player.copy(
                                            ocrText = "",
                                            resolvedText = "",
                                        ),
                                    )
                                },
                            )
                        }
                    },
                ),
            ),
        )
    }

    private fun completeLobbyResultForMatching(): MatchLobbyPlayersOcrResult =
        MatchLobbyPlayersOcrResult(
            slots = (1..12).map { slotNumber ->
                MatchLobbyPlayersOcrSlot(
                    slotNumber = slotNumber,
                    players = if (slotNumber == 12) {
                        (1..4).map { playerNumber ->
                            MatchLobbyPlayersOcrPlayer(playerNumber, "Player 1-$playerNumber")
                        }
                    } else {
                        (1..4).map { playerNumber ->
                            MatchLobbyPlayersOcrPlayer(playerNumber, null)
                        }
                    },
                )
            },
        )

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

    private fun calculatedEvidenceForRestoreTest(): MatchCalculatedEvidence = MatchCalculatedEvidence(
        lobby = LobbyCalculatedEvidence(
            teams = listOf(
                LobbyTeamCalculatedEvidence(
                    slotNumber = 12,
                    teamName = "Team 12",
                    sourceScreenshotIndex = 3,
                    cropLeft = 1.0,
                    cropTop = 2.0,
                    cropRight = 30.0,
                    cropBottom = 40.0,
                    playerNames = listOf("Lobby P1", null, null, null),
                ),
            ),
        ),
        result = ResultCalculatedEvidence(
            positions = listOf(
                ResultPositionCalculatedEvidence(
                    position = 12,
                    sourceScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    cropLeft = 1,
                    cropTop = 2,
                    cropRight = 30,
                    cropBottom = 40,
                    slotNumber = 12,
                    teamName = "Team 12",
                    playerNames = listOf("Saved Player", null, null, null),
                    playerKills = listOf(2, null, null, null),
                    totalKills = 7,
                ),
            ),
        ),
    )

    private fun calculatedEvidenceWithManualPosition12(): MatchCalculatedEvidence =
        MatchCalculatedEvidence(
            result = ResultCalculatedEvidence(
                positions = (1..11).map { position ->
                    ResultPositionCalculatedEvidence(
                        position = position,
                        sourceScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        cropLeft = 0,
                        cropTop = position,
                        cropRight = 100,
                        cropBottom = position + 10,
                        slotNumber = position,
                        playerNames = List(4) { "Not detected" },
                        playerKills = List(4) { null },
                        totalKills = 0,
                        placement = position,
                        playerKillApplicable = List(4) { false },
                    )
                } + ResultPositionCalculatedEvidence(
                    position = 12,
                    playerNames = List(4) { "Not detected" },
                    playerKills = List(4) { null },
                    placement = null,
                    playerKillApplicable = List(4) { false },
                ),
                manuallyAddedPositions = listOf(12),
            ),
        )

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
        const val MATCH_ID = "synthetic-match"
        const val GROUP_TOURNAMENT_ID = "group-rotation-tournament"
        const val GROUP_MATCH_ID = "group-rotation-match"
    }
}
