package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrVisualRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

@OptIn(ExperimentalCoroutinesApi::class)
class MatchOcrReviewTeamSuggestionTest {
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
    fun completePreviewMapsTopSuggestionsAndSafeAssignmentPrefillsDraft() = runTest(dispatcher) {
        val repository = repositoryWithRoster()
        val viewModel = viewModel(
            repository,
            completePreviewRunner(),
            lobbyRunner(mapOf(1 to exactPlayers)),
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val firstRow = state.rows.first()
        val firstDraft = state.correctionDraft!!.rows.first()
        assertEquals("1", firstRow.suggestedTeamSlotDisplayValue)
        assertEquals("Automatic candidate", firstRow.confidenceTierLabel)
        assertEquals("Safe automatic assignment", firstRow.assignmentSafetyStatusLabel)
        assertTrue(firstRow.topThreeSuggestionsSummary.first().startsWith("Rank 1: Slot 1"))
        assertEquals(1, firstRow.originalSuggestedTeamSlot)
        assertEquals("1", firstDraft.assignedTeamSlotDraftValue)
        assertFalse(firstDraft.validation.blockers.contains(MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT))
    }

    @Test
    fun reviewRequiredShowsSuggestionButDoesNotPrefillDraft() {
        val rows = mapWithLobbyEvidence(
            preview = completePreviewUiState(slotsForPosition = mapOf(1 to listOf("Alpha"))),
            lobbyOverrides = mapOf(1 to exactPlayers),
        )
        val row = rows.first()
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)

        assertEquals("1", row.suggestedTeamSlotDisplayValue)
        assertEquals("Review required", row.assignmentSafetyStatusLabel)
        assertEquals(null, row.originalSuggestedTeamSlot)
        assertEquals("", draft.rows.first().assignedTeamSlotDraftValue)
        assertTrue(row.blockerLabels.any { it.contains("review required") })
    }

    @Test
    fun manualRequiredDoesNotPrefillTeamSlot() {
        val rows = mapWithLobbyEvidence(
            preview = completePreviewUiState(slotsForPosition = mapOf(1 to listOf("Unrelated"))),
            lobbyOverrides = mapOf(1 to exactPlayers),
        )
        val row = rows.first()

        assertEquals("Manual required", row.assignmentSafetyStatusLabel)
        assertEquals(null, row.originalSuggestedTeamSlot)
        assertTrue(row.blockerLabels.any { it.contains("manual assignment required") })
    }

    @Test
    fun duplicateSafeSuggestionsAreNotPrefilled() {
        val duplicateNames = listOf("Alpha", "Bravo", "Charlie", "Delta")
        val rows = mapWithLobbyEvidence(
            preview = completePreviewUiState(
                slotsForPosition = mapOf(1 to duplicateNames, 2 to duplicateNames),
            ),
            lobbyOverrides = mapOf(1 to exactPlayers),
        )

        assertTrue(rows[0].assignmentSafetyStatusLabel == "Review required")
        assertTrue(rows[1].assignmentSafetyStatusLabel == "Review required")
        assertEquals(null, rows[0].originalSuggestedTeamSlot)
        assertEquals(null, rows[1].originalSuggestedTeamSlot)
    }

    @Test
    fun unavailableLobbyEvidenceLeavesEditableRowsManualWithoutAssignment() {
        val rows = mapWithLobbyEvidence(
            preview = completePreviewUiState(),
            lobbyResult = MatchLobbyPlayersOcrResult.unavailable(),
        )
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)

        assertEquals("Manual required", rows.first().assignmentSafetyStatusLabel)
        assertEquals("1", rows.first().suggestedTeamSlotDisplayValue)
        assertNull(rows.first().originalSuggestedTeamSlot)
        assertEquals("", draft.rows.first().assignedTeamSlotDraftValue)
        assertTrue(draft.rows.first().validation.blockers.contains(MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT))
    }

    @Test
    fun incompletePreviewStillDoesNotCreateCorrectionDraft() = runTest(dispatcher) {
        val repository = repositoryWithRoster()
        val viewModel = viewModel(
            repository = repository,
            runner = MatchResultOcrPreviewRunner { identity ->
                if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                    processed(
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        positions = 1..10,
                    )
                } else {
                    MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop
                }
            },
            lobbyRunner = lobbyRunner(emptyMap()),
        )

        viewModel.load(TOURNAMENT_ID, MATCH_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Empty
        assertTrue(state.matchResultOcrPreview is MatchResultOcrPreviewUiState.Ready)
    }

    private fun viewModel(
        repository: InMemoryTournamentRepository,
        runner: MatchResultOcrPreviewRunner,
        lobbyRunner: MatchLobbyPlayersOcrRunner = lobbyRunner(emptyMap()),
    ): MatchOcrReviewViewModel = MatchOcrReviewViewModel(
        finalizeOcrCorrectionMatch = FinalizeOcrCorrectionMatchUseCase(
            repository = repository,
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        ),
        matchResultOcrPreviewRunner = runner,
        matchLobbyPlayersOcrRunner = lobbyRunner,
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        initialUiState = MatchOcrReviewUiState.Loading,
    )

    private fun repositoryWithRoster(): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        kotlinx.coroutines.runBlocking {
            repository.create(
                Tournament(
                    id = TOURNAMENT_ID,
                    name = "Synthetic Cup",
                    date = LocalDate.of(2026, 8, 8),
                    organizerName = "Organizer",
                    organizerContactNumber = "123",
                    status = TournamentStatus.CONFIRMED,
                ),
            )
            repository.saveRoster(
                tournamentId = TOURNAMENT_ID,
                slotNumber = 1,
                players = listOf("Alpha", "Bravo", "Charlie", "Delta").map { name ->
                    RosterPlayer.create(TOURNAMENT_ID, 1, name)
                },
            )
        }
        return repository
    }

    private fun completePreviewRunner(): MatchResultOcrPreviewRunner =
        MatchResultOcrPreviewRunner { identity ->
            processed(
                role = identity.role,
                positions = if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1..10 else 11..12,
                slotsForPosition = mapOf(
                    1 to listOf("Alpha", "Bravo", "Charlie", "Delta"),
                ),
            )
        }

    private fun completePreviewUiState(
        slotsForPosition: Map<Int, List<String>> = emptyMap(),
    ): MatchResultOcrPreviewUiState.Ready = MatchResultOcrPreviewUiState.Ready(
        roles = listOf(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        ),
        rows = (1..12).map { position ->
            MatchResultOcrPreviewRowUiState(
                position = position,
                role = if (position <= 10) {
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER
                } else {
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER
                },
                sourceLabel = "PREVIEW",
                placementText = position.toString(),
                slots = (slotsForPosition[position] ?: listOf("Player $position"))
                    .mapIndexed { index, player ->
                        MatchResultOcrPreviewSlotUiState(
                            slot = index + 1,
                            playerText = player,
                            playerOcrText = player,
                            playerStatusLabel = "DIRECT_TEXT",
                            killText = "0",
                            killOcrText = "0",
                            killStatusLabel = "DIRECT_NUMERIC",
                        )
                    },
            )
        },
        ignoredLowerRows = emptyList(),
        manualReviewRows = emptyList(),
    )

    private fun mapWithLobbyEvidence(
        preview: MatchResultOcrPreviewUiState.Ready,
        lobbyOverrides: Map<Int, List<String?>> = emptyMap(),
        lobbyResult: MatchLobbyPlayersOcrResult = lobbyResult(lobbyOverrides),
    ): List<MatchOcrReviewRowUiState> = MatchResultOcrPreviewTeamSuggestionMapper.map(
        preview = preview,
        resultRows = resultRowsForPreview(preview),
        lobbyOcrResult = lobbyResult,
    )!!

    private fun resultRowsForPreview(
        preview: MatchResultOcrPreviewUiState.Ready,
    ): List<MatchResultOcrRow> = preview.rows.map { previewRow ->
        MatchResultOcrRow(
            position = previewRow.position,
            source = if (previewRow.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                MatchResultOcrRowSource.UPPER_TEMPLATE
            } else if (previewRow.position == 11) {
                MatchResultOcrRowSource.LOWER_ROW_A
            } else {
                MatchResultOcrRowSource.LOWER_ROW_B
            },
            placement = field(
                id = "placement-${previewRow.position}",
                type = MatchResultOcrFieldType.PLACEMENT,
                position = previewRow.position,
                text = previewRow.placementText,
            ),
            playerSlots = (1..4).map { slot ->
                val player = previewRow.slots.firstOrNull { it.slot == slot }?.playerText.orEmpty()
                MatchResultOcrPlayerSlot(
                    slot = slot,
                    player = field(
                        id = "player-${previewRow.position}-$slot",
                        type = MatchResultOcrFieldType.PLAYER,
                        position = previewRow.position,
                        text = player,
                    ),
                    kill = field(
                        id = "kill-${previewRow.position}-$slot",
                        type = MatchResultOcrFieldType.KILL,
                        position = previewRow.position,
                        text = "0",
                    ),
                )
            },
        )
    }

    private fun lobbyRunner(
        lobbyOverrides: Map<Int, List<String?>>,
    ): MatchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ ->
        lobbyResult(lobbyOverrides)
    }

    private fun lobbyResult(
        lobbyOverrides: Map<Int, List<String?>>,
    ): MatchLobbyPlayersOcrResult = MatchLobbyPlayersOcrResult(
        slots = (1..12).map { slotNumber ->
            MatchLobbyPlayersOcrSlot(
                slotNumber = slotNumber,
                players = (lobbyOverrides[slotNumber] ?: listOf(null, null, null, null))
                    .mapIndexed { index, playerName -> MatchLobbyPlayersOcrPlayer(index + 1, playerName) },
            )
        },
    )

    private fun processed(
        role: MatchResultScreenshotRole,
        positions: IntRange,
        slotsForPosition: Map<Int, List<String>> = emptyMap(),
    ): MatchResultOcrPreviewProcessingResult.Processed {
        val rows = positions.map { position ->
            MatchResultOcrRow(
                position = position,
                source = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                    MatchResultOcrRowSource.UPPER_TEMPLATE
                } else if (position == 11) {
                    MatchResultOcrRowSource.LOWER_ROW_A
                } else {
                    MatchResultOcrRowSource.LOWER_ROW_B
                },
                placement = field("placement", MatchResultOcrFieldType.PLACEMENT, position, position.toString()),
                playerSlots = (slotsForPosition[position] ?: listOf("Player $position"))
                    .mapIndexed { index, player ->
                        MatchResultOcrPlayerSlot(
                            slot = index + 1,
                            player = field("player$index", MatchResultOcrFieldType.PLAYER, position, player),
                            kill = field("kill$index", MatchResultOcrFieldType.KILL, position, "0"),
                        )
                    },
            )
        }
        return MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(
                role = role,
                fields = emptyList(),
                rows = rows,
            ),
            pixelCrop = OcrPixelCropRect(0, 0, 1, 1),
            cropWidth = 1,
            cropHeight = 1,
        )
    }

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        text: String,
    ): MatchResultOcrField = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = if (position == 11) MatchResultOcrVisualRow.A else if (position == 12) MatchResultOcrVisualRow.B else null,
        slot = null,
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
        val exactPlayers = listOf<String?>("Alpha", "Bravo", "Charlie", "Delta")
        const val TOURNAMENT_ID = "action5-tournament"
        const val MATCH_ID = "action5-match"
    }
}
