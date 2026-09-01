package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrIgnoredLowerVisualRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrIgnoredLowerVisualRowReason
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrManualReviewReason
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrManualReviewRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrVisualRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchOcrReviewPreviewMappingTest {
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
    fun upperPreviewMapsOnlyPositionsOneThroughTen() {
        val result = MatchResultOcrPreviewUiStateMapper.map(
            listOf(
                MatchResultOcrPreviewRoleResult(
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    result = processed(
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        rows = (1..10).map { row(it, MatchResultOcrRowSource.UPPER_TEMPLATE) },
                    ),
                ),
            ),
        ) as MatchResultOcrPreviewUiState.Ready

        assertEquals((1..10).toList(), result.rows.map { it.position })
    }

    @Test
    fun lowerPreviewMapsEmittedPositionsElevenAndTwelveAndNotesIgnoredManualRows() {
        val result = MatchResultOcrPreviewUiStateMapper.map(
            listOf(
                MatchResultOcrPreviewRoleResult(
                    role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    result = processed(
                        role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                        rows = listOf(
                            row(11, MatchResultOcrRowSource.LOWER_ROW_A, MatchResultOcrVisualRow.A),
                            row(12, MatchResultOcrRowSource.LOWER_ROW_B, MatchResultOcrVisualRow.B),
                        ),
                        ignored = listOf(
                            MatchResultOcrIgnoredLowerVisualRow(
                                visualRow = MatchResultOcrVisualRow.A,
                                detectedPlacement = 10,
                                reason = MatchResultOcrIgnoredLowerVisualRowReason.UPPER_OWNS_POSITION,
                            ),
                        ),
                        manual = listOf(
                            MatchResultOcrManualReviewRow(
                                visualRow = MatchResultOcrVisualRow.B,
                                detectedPlacementText = "?",
                                reason = MatchResultOcrManualReviewReason.INVALID_PLACEMENT,
                            ),
                        ),
                    ),
                ),
            ),
        ) as MatchResultOcrPreviewUiState.Ready

        assertEquals(listOf(11, 12), result.rows.map { it.position })
        assertEquals(1, result.ignoredLowerRows.size)
        assertEquals(1, result.manualReviewRows.size)
    }

    @Test
    fun completePreviewSeedsTwelveEditableReviewRowsAndDraft() = runTest(dispatcher) {
        val viewModel = viewModelWithPreview(completePreview())

        viewModel.load("tournament", "match")
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(12, state.rowCount)
        assertEquals((0..11).toList(), state.rows.map { it.rowIndex })
        assertEquals(12, state.correctionDraft?.rows?.size)
        assertEquals(12, state.correctionDraft?.blockerCount)
    }

    @Test
    fun previewRowMappingUsesPositionIndexAndSumsNumericKills() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = (1..12).map { position ->
                if (position == 1) {
                    previewRow(
                        position = position,
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        slots = listOf(
                            previewSlot(1, "Alpha", "2"),
                            previewSlot(2, "Bravo", "3"),
                        ),
                    )
                } else {
                    previewRow(
                        position = position,
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        slots = listOf(previewSlot(1, "Player $position", "0")),
                    )
                }
            },
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!!
        val first = rows.first()

        assertEquals(0, first.rowIndex)
        assertEquals("P1 Alpha, P2 Bravo", first.detectedPlayerNameEvidenceLabel)
        assertEquals("5", first.detectedKillDisplayValue)
        assertEquals(5, first.originalParsedKillValue)
        assertEquals(listOf(1, 2), first.playerKillEvidence.map { it.playerSlot })
        assertEquals(listOf("2", "3"), first.playerKillEvidence.map { it.originalKillsValue })
        assertEquals("Unavailable", first.suggestedTeamSlotDisplayValue)
        assertEquals(MatchOcrReviewSeverity.BLOCKING, first.severity)
        assertTrue(first.blockerLabels.any { it.contains("Team assignment") })
    }

    @Test
    fun blankPlayerKillSurvivesPreviewEvidenceAndCorrectionDraft() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(
                previewRow(
                    position = 10,
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    slots = listOf(
                        previewSlot(1, "Player A", "4"),
                        previewSlot(2, "Player B", ""),
                        previewSlot(3, "Player C", "1"),
                        previewSlot(4, "Player D", "3"),
                    ),
                ),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

        val reviewRow = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!![9]
        assertEquals(listOf("4", "", "1", "3"), reviewRow.playerKillEvidence.map { it.originalKillsValue })
        assertEquals(null, reviewRow.originalParsedKillValue)

        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(listOf(reviewRow))
        assertEquals(listOf("4", "", "1", "3"), draft.rows.single().playerKillDrafts.map { it.killsDraftValue })
        assertEquals("", draft.rows.single().killsDraftValue)
        assertTrue(draft.rows.single().validation.blockers.contains(MatchOcrReviewCorrectionReason.MISSING_KILLS))

        val corrected = MatchOcrReviewCorrectionDraftReducer.onPlayerKillsChanged(
            draft = draft,
            rowIndex = 9,
            playerSlot = 2,
            value = "2",
        )
        assertEquals(listOf("4", "2", "1", "3"), corrected.rows.single().playerKillDrafts.map { it.killsDraftValue })
        assertEquals("10", corrected.rows.single().killsDraftValue)
        assertTrue(!corrected.rows.single().validation.blockers.contains(MatchOcrReviewCorrectionReason.MISSING_KILLS))
    }

    @Test
    fun partialUpperPositionElevenRemainsVisibleWithUnavailableSlots() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(
                previewRow(
                    position = 11,
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    slots = listOf(
                        previewSlot(1, "Player A", "2"),
                        previewSlot(2, "Player B", ""),
                    ),
                ),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

        val reviewRow = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!![10]

        assertEquals("11", reviewRow.expectedPlacementLabel)
        assertEquals(listOf(1, 2), reviewRow.playerKillEvidence.map { it.playerSlot })
        assertEquals(listOf("2", ""), reviewRow.playerKillEvidence.map { it.originalKillsValue })
        assertEquals(null, reviewRow.originalParsedKillValue)
    }

    @Test
    fun incompletePreviewCreatesManualCorrectionPlaceholders() = runTest(dispatcher) {
        val viewModel = viewModelWithPreview(
            MatchResultOcrPreviewUiStateMapper.map(
                listOf(
                    roleResult(
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        rows = (1..10).map { row(it, MatchResultOcrRowSource.UPPER_TEMPLATE) },
                    ),
                ),
            ),
        )

        viewModel.reprocess("tournament", "match", allowIncompleteEvidence = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(12, state.rows.size)
        assertEquals((0..9).toList(), state.rows.take(10).map { it.rowIndex })
        assertEquals(listOf(10, 11), state.rows.drop(10).map { it.rowIndex })
        assertEquals(null, state.rows[10].originalParsedPlacementValue)
        assertEquals(null, state.rows[10].originalParsedKillValue)
        assertEquals(null, state.rows[10].originalSuggestedTeamSlot)
        assertEquals("", state.correctionDraft?.rows?.get(10)?.placementDraftValue)
        assertEquals("", state.correctionDraft?.rows?.get(10)?.killsDraftValue)
        assertEquals("", state.correctionDraft?.rows?.get(10)?.assignedTeamSlotDraftValue)
        assertTrue(state.correctionDraft?.rows?.get(10)?.validation?.blockers?.isNotEmpty() == true)
    }

    @Test
    fun duplicatePreviewPositionCreatesPlaceholderForAmbiguousPosition() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = (listOf(1, 1) + (2..11).toList()).map { position ->
                previewRow(
                    position = position,
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    slots = listOf(previewSlot(1, "Player $position", "0")),
                )
            },
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!!

        assertEquals(12, rows.size)
        assertEquals("Unavailable", rows.first().detectedPlacementDisplayValue)
        assertEquals(null, rows.first().originalParsedPlacementValue)
        assertEquals("Manual correction required", rows.first().placementStatusLabel)
    }

    @Test
    fun outOfRangePreviewPositionDoesNotChangeCanonicalRows() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = (1..11).map { position ->
                previewRow(
                    position = position,
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    slots = listOf(previewSlot(1, "Player $position", "0")),
                )
            } + previewRow(
                position = 13,
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                slots = listOf(previewSlot(1, "Player 13", "0")),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!!

        assertEquals(12, rows.size)
        assertEquals((0..10).toList(), rows.take(11).map { it.rowIndex })
        assertEquals(11, rows.last().rowIndex)
        assertEquals(null, rows.last().originalParsedPlacementValue)
    }

    @Test
    fun seededCorrectionDraftStillBlocksFinalizationWithoutTeamSlots() = runTest(dispatcher) {
        val viewModel = viewModelWithPreview(completePreview())

        viewModel.load("tournament", "match")
        advanceUntilIdle()
        viewModel.onFinalizeOcrCorrection()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(
            MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED,
            state.finalization.error,
        )
        assertTrue(!state.finalization.isFinalized)
    }

    @Test
    fun missingConfirmedCropIsSafeAndUsesReviewMessage() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner {
                MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )

        viewModel.load("tournament", "match")
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Empty
        assertTrue(state.matchResultOcrPreview is MatchResultOcrPreviewUiState.Error)
        assertEquals(
            "Confirm the screenshot crop before running OCR preview.",
            (state.matchResultOcrPreview as MatchResultOcrPreviewUiState.Error).message,
        )
    }

    private fun processed(
        role: MatchResultScreenshotRole,
        rows: List<MatchResultOcrRow>,
        ignored: List<MatchResultOcrIgnoredLowerVisualRow> = emptyList(),
        manual: List<MatchResultOcrManualReviewRow> = emptyList(),
    ): MatchResultOcrPreviewProcessingResult.Processed =
        MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(
                role = role,
                fields = emptyList(),
                rows = rows,
                ignoredLowerRows = ignored,
                manualReviewRows = manual,
            ),
            pixelCrop = OcrPixelCropRect(0, 0, 1, 1),
            cropWidth = 1,
            cropHeight = 1,
        )

    private fun roleResult(
        role: MatchResultScreenshotRole,
        rows: List<MatchResultOcrRow>,
    ): MatchResultOcrPreviewRoleResult = MatchResultOcrPreviewRoleResult(
        role = role,
        result = processed(role = role, rows = rows),
    )

    private fun completePreview(): MatchResultOcrPreviewUiState =
        MatchResultOcrPreviewUiStateMapper.map(
            listOf(
                roleResult(
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    rows = (1..10).map { row(it, MatchResultOcrRowSource.UPPER_TEMPLATE) },
                ),
                roleResult(
                    role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    rows = listOf(
                        row(11, MatchResultOcrRowSource.LOWER_ROW_A, MatchResultOcrVisualRow.A),
                        row(12, MatchResultOcrRowSource.LOWER_ROW_B, MatchResultOcrVisualRow.B),
                    ),
                ),
            ),
        )

    private fun viewModelWithPreview(
        preview: MatchResultOcrPreviewUiState,
    ): MatchOcrReviewViewModel {
        val roleResults = when (preview) {
            is MatchResultOcrPreviewUiState.Ready -> listOf(
                MatchResultOcrPreviewRoleResult(
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    result = processed(
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        rows = preview.rows
                            .filter { it.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER }
                            .map { row(it.position, MatchResultOcrRowSource.UPPER_TEMPLATE) },
                    ),
                ),
            )
            else -> emptyList()
        }
        val runnerResults = if (preview is MatchResultOcrPreviewUiState.Ready && preview.rows.any { it.position > 10 }) {
            roleResults + MatchResultOcrPreviewRoleResult(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                result = processed(
                    role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    rows = preview.rows
                        .filter { it.position > 10 }
                        .map {
                            row(
                                it.position,
                                if (it.position == 11) MatchResultOcrRowSource.LOWER_ROW_A
                                else MatchResultOcrRowSource.LOWER_ROW_B,
                                if (it.position == 11) MatchResultOcrVisualRow.A else MatchResultOcrVisualRow.B,
                            )
                        },
                ),
            )
        } else {
            roleResults
        }
        return MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(),
            matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
                runnerResults.firstOrNull { it.role == identity.role }?.result
                    ?: MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop
            },
            initialUiState = MatchOcrReviewUiState.Loading,
        )
    }

    private fun previewRow(
        position: Int,
        role: MatchResultScreenshotRole,
        slots: List<MatchResultOcrPreviewSlotUiState>,
    ): MatchResultOcrPreviewRowUiState = MatchResultOcrPreviewRowUiState(
        position = position,
        role = role,
        sourceLabel = "PREVIEW",
        placementText = position.toString(),
        slots = slots,
    )

    private fun previewSlot(
        slot: Int,
        player: String,
        kill: String,
    ): MatchResultOcrPreviewSlotUiState = MatchResultOcrPreviewSlotUiState(
        slot = slot,
        playerText = player,
        playerOcrText = player,
        playerStatusLabel = "DIRECT_TEXT",
        killText = kill,
        killOcrText = kill,
        killStatusLabel = "DIRECT_NUMERIC",
    )

    private fun row(
        position: Int,
        source: MatchResultOcrRowSource,
        visualRow: MatchResultOcrVisualRow? = null,
    ): MatchResultOcrRow {
        val placement = field("placement", MatchResultOcrFieldType.PLACEMENT, position, visualRow, null, position.toString())
        val player = field("player", MatchResultOcrFieldType.PLAYER, position, visualRow, 1, "Player $position")
        val kill = field("kill", MatchResultOcrFieldType.KILL, position, visualRow, 1, "0")
        return MatchResultOcrRow(
            position = position,
            source = source,
            placement = placement,
            playerSlots = listOf(MatchResultOcrPlayerSlot(1, player, kill)),
        )
    }

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        visualRow: MatchResultOcrVisualRow?,
        slot: Int?,
        text: String,
    ): MatchResultOcrField = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = visualRow,
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

    private fun createFinalizeUseCase(): FinalizeOcrCorrectionMatchUseCase {
        val repository = InMemoryTournamentRepository()
        return FinalizeOcrCorrectionMatchUseCase(
            repository = repository,
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        )
    }
}
