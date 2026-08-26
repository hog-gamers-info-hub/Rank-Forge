package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionRowCropPreviewTest {
    @Test
    fun oneRowStateExposesExactlyOneRow() {
        val state = available(1)

        assertEquals(listOf(1), state.sortedRows().map { it.rowIndex })
    }

    @Test
    fun twoRowStateExposesExactlyTwoRowsInOrder() {
        val state = available(2)

        assertEquals(listOf(1, 2), state.sortedRows().map { it.rowIndex })
    }

    @Test
    fun unavailableRowsRemainIndependentFromPositionPreviews() {
        val state = MatchResultPositionRowCropPreviewState.Unavailable(
            MatchResultPositionRowCropPreviewUnavailableReason.OCR_UNAVAILABLE,
        )

        assertTrue(state.sortedRows().isEmpty())
    }

    @Test
    fun replacingRowsReleasesRetiredBitmapsOnly() {
        val retired = TrackingPreviewImage()
        val retained = TrackingPreviewImage()
        val previous = mapOf(
            3 to MatchResultPositionRowCropPreviewState.Available(
                listOf(MatchResultPositionRowCropPreview(1, retired)),
            ),
            4 to MatchResultPositionRowCropPreviewState.Available(
                listOf(MatchResultPositionRowCropPreview(1, retained)),
            ),
        )
        val current = mapOf(
            3 to MatchResultPositionRowCropPreviewState.Unavailable(
                MatchResultPositionRowCropPreviewUnavailableReason.OCR_UNAVAILABLE,
            ),
            4 to previous.getValue(4),
        )

        releaseReplacedResultPositionRowCropPreviewStates(previous, current)

        assertEquals(1, retired.releaseCount)
        assertEquals(0, retained.releaseCount)
    }

    private fun available(count: Int): MatchResultPositionRowCropPreviewState.Available =
        MatchResultPositionRowCropPreviewState.Available(
            (1..count).map { row -> MatchResultPositionRowCropPreview(row, FakePreviewImage) },
        )

    private data object FakePreviewImage : MatchResultPositionRowCropPreviewImage

    private class TrackingPreviewImage : MatchResultPositionRowCropPreviewImage {
        var releaseCount = 0
        override fun release() { releaseCount++ }
    }
}
