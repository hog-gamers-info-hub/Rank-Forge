package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReadResult
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReader
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchOcrCacheRestoreTest {
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
    fun noCacheLeavesProcessingActionAvailableWithoutRunningOcr() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
            },
        )

        viewModel.loadCached("tournament", "match")
        advanceUntilIdle()

        assertEquals(MatchOcrCacheAvailability.NOT_AVAILABLE, viewModel.cacheAvailability.value)
        assertTrue(viewModel.uiState.value is MatchOcrReviewUiState.Loading)
    }

    @Test
    fun fullCacheRestoresExistingReviewStateWithoutRunningOcr() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(
                    availability = MatchOcrCacheAvailability.READY,
                    resultRoleResults = cachedResultRoles(),
                    lobbyResult = MatchLobbyPlayersOcrResult(
                        slots = listOf(
                            MatchLobbyPlayersOcrSlot(
                                slotNumber = 1,
                                players = listOf(MatchLobbyPlayersOcrPlayer(1, "Cached player")),
                            ),
                        ),
                    ),
                )
            },
        )

        viewModel.loadCached("tournament", "match")
        advanceUntilIdle()

        val state = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertEquals(MatchOcrCacheAvailability.READY, viewModel.cacheAvailability.value)
        assertEquals((0..11).toList(), state.rows.map { it.rowIndex })
        assertEquals("Cached player", state.lobbyPlayers.single().players.single().playerName)
        assertFalse(state.finalization.isFinalized)
    }

    @Test
    fun partialCacheIsReportedStaleAndDoesNotRestoreAsReady() = runTest(dispatcher) {
        val viewModel = MatchOcrReviewViewModel(
            finalizeOcrCorrectionMatch = createFinalizeUseCase(),
            matchOcrCacheReader = MatchOcrCacheReader { _, _ ->
                MatchOcrCacheReadResult(MatchOcrCacheAvailability.STALE_OR_INCOMPLETE)
            },
        )

        viewModel.loadCached("tournament", "match")
        advanceUntilIdle()

        assertEquals(MatchOcrCacheAvailability.STALE_OR_INCOMPLETE, viewModel.cacheAvailability.value)
        assertTrue(viewModel.uiState.value is MatchOcrReviewUiState.Loading)
    }

    private fun createFinalizeUseCase(): FinalizeOcrCorrectionMatchUseCase {
        val repository = InMemoryTournamentRepository()
        return FinalizeOcrCorrectionMatchUseCase(
            repository = repository,
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        )
    }

    private fun cachedResultRoles(): List<MatchResultOcrPreviewRoleResult> = listOf(
        MatchResultOcrPreviewRoleResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            result = processed(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                positions = 1..10,
                source = MatchResultOcrRowSource.UPPER_TEMPLATE,
            ),
        ),
        MatchResultOcrPreviewRoleResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            result = processed(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                positions = 11..12,
                source = MatchResultOcrRowSource.LOWER_ROW_A,
            ),
        ),
    )

    private fun processed(
        role: MatchResultScreenshotRole,
        positions: IntRange,
        source: MatchResultOcrRowSource,
    ): MatchResultOcrPreviewProcessingResult.Processed =
        MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(
                role = role,
                fields = emptyList(),
                rows = positions.map { position ->
                    MatchResultOcrRow(
                        position = position,
                        source = source,
                        placement = field(position.toString(), MatchResultOcrFieldType.PLACEMENT),
                        playerSlots = emptyList(),
                    )
                },
            ),
            pixelCrop = OcrPixelCropRect(0, 0, 1, 1),
            cropWidth = 1,
            cropHeight = 1,
        )

    private fun field(value: String, type: MatchResultOcrFieldType) = MatchResultOcrField(
        id = "cached",
        type = type,
        position = null,
        visualRow = null,
        slot = null,
        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        ocrText = value,
        resolvedText = value,
        status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
    )
}
