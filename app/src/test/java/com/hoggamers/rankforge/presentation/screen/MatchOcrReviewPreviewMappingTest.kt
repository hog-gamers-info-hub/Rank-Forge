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
