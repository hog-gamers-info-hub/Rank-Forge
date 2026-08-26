package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionCropPreviewTest {
    @Test
    fun upperPreviewStateExposesPositionsOneThroughTenInAscendingOrder() {
        val state = available((10 downTo 1).toList())

        assertEquals((1..10).toList(), state.sortedCrops().map(MatchResultPositionCropPreview::position))
    }

    @Test
    fun upperPreviewStateCanExposeFallbackPositionElevenInAscendingOrder() {
        val state = available((11 downTo 1).toList())

        assertEquals((1..11).toList(), state.sortedCrops().map(MatchResultPositionCropPreview::position))
    }

    @Test
    fun lowerPreviewStateExposesPositionsElevenAndTwelveInAscendingOrder() {
        val state = available(listOf(12, 11))

        assertEquals(listOf(11, 12), state.sortedCrops().map(MatchResultPositionCropPreview::position))
    }

    @Test
    fun upperAndLowerPreviewStatesRemainIndependent() {
        val previews = defaultMatchResultPositionCropPreviewStates().toMutableMap().apply {
            this[MatchResultScreenshotRole.MATCH_RESULT_UPPER] = available((1..10).toList())
            this[MatchResultScreenshotRole.MATCH_RESULT_LOWER] = available(listOf(11, 12))
        }

        assertEquals(
            (1..10).toList(),
            previews.getValue(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
                .sortedCrops()
                .map(MatchResultPositionCropPreview::position),
        )
        assertEquals(
            listOf(11, 12),
            previews.getValue(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
                .sortedCrops()
                .map(MatchResultPositionCropPreview::position),
        )
    }

    @Test
    fun unavailableStateContainsNoStaleCrops() {
        val state = MatchResultPositionCropPreviewState.Unavailable(
            MatchResultPositionCropPreviewUnavailableReason.GENERATION_FAILED,
        )

        assertTrue(state.sortedCrops().isEmpty())
    }

    @Test
    fun replacingLowerStateDoesNotReleaseUpperBitmapsRetainedByTheCurrentMap() {
        val upperImage = TrackingPreviewImage()
        val upperState = MatchResultPositionCropPreviewState.Available(
            (1..10).map { position -> MatchResultPositionCropPreview(position, upperImage) },
        )
        val previous = mapOf(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER to upperState,
            MatchResultScreenshotRole.MATCH_RESULT_LOWER to MatchResultPositionCropPreviewState.Loading,
        )
        val current = previous.toMutableMap().apply {
            this[MatchResultScreenshotRole.MATCH_RESULT_LOWER] = available(listOf(11, 12))
        }

        releaseReplacedResultPositionCropPreviewStates(previous, current)

        assertEquals(0, upperImage.releaseCount)
    }

    @Test
    fun replacingUpperStateReleasesItsRetiredBitmaps() {
        val upperImage = TrackingPreviewImage()
        val previous = mapOf(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER to MatchResultPositionCropPreviewState.Available(
                (1..10).map { position -> MatchResultPositionCropPreview(position, upperImage) },
            ),
            MatchResultScreenshotRole.MATCH_RESULT_LOWER to MatchResultPositionCropPreviewState.Loading,
        )
        val current = previous.toMutableMap().apply {
            this[MatchResultScreenshotRole.MATCH_RESULT_UPPER] = MatchResultPositionCropPreviewState.Unavailable(
                MatchResultPositionCropPreviewUnavailableReason.NOT_READY,
            )
        }

        releaseReplacedResultPositionCropPreviewStates(previous, current)

        assertEquals(10, upperImage.releaseCount)
    }

    private fun available(positions: List<Int>): MatchResultPositionCropPreviewState.Available =
        MatchResultPositionCropPreviewState.Available(
            positions.map { position ->
                MatchResultPositionCropPreview(position, FakePreviewImage)
            },
        )

    private data object FakePreviewImage : MatchResultPositionCropPreviewImage

    private class TrackingPreviewImage : MatchResultPositionCropPreviewImage {
        var releaseCount = 0

        override fun release() {
            releaseCount++
        }
    }
}
