package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyPpOcrFallbackPolicyTest {
    @Test
    fun zeroResolvedMlKitAnchorsDoNotRunPpOcr() {
        assertFalse(
            LobbyPpOcrFallbackPolicy.shouldRunPpOcr(
                listOf(group(1), group(2), group(3)),
            ),
        )
    }

    @Test
    fun exactlyOneResolvedMlKitAnchorRunsPpOcr() {
        assertTrue(
            LobbyPpOcrFallbackPolicy.shouldRunPpOcr(
                listOf(group(1, 2), group(2), group(3)),
            ),
        )
    }

    @Test
    fun twoResolvedMlKitAnchorsDoNotRunPpOcr() {
        assertFalse(
            LobbyPpOcrFallbackPolicy.shouldRunPpOcr(
                listOf(group(1, 1, 2), group(2), group(3)),
            ),
        )
    }

    @Test
    fun threeOrMoreResolvedMlKitAnchorsDoNotRunPpOcr() {
        assertFalse(
            LobbyPpOcrFallbackPolicy.shouldRunPpOcr(
                listOf(group(1, 1, 2, 4), group(2), group(3)),
            ),
        )
    }

    @Test
    fun mergeDropsPpObservationForSlotAlreadyResolvedByMlKit() {
        val mlGroups = listOf(group(1, 2), group(2), group(3))
        val ppObservations = listOf(
            observation("2", 1064, 222, 1086, 250),
            observation("3", 574, 427, 592, 455),
        )

        val mergeEligible = LobbyPpOcrFallbackPolicy.ppObservationsForMerge(
            mlKitGroups = mlGroups,
            ppObservations = ppObservations,
        )

        assertEquals(listOf("3"), mergeEligible.map { it.text })
    }

    private fun group(
        screenshotIndex: Int,
        vararg slots: Int,
    ): LobbyResolvedOcrAnchorGroup {
        val anchors = slots.mapIndexed { index, slot ->
            LobbyResolvedOcrAnchor(
                anchor = LobbyObservedSlotAnchor(
                    slotNumber = slot,
                    centerX = 500.0 + (index * 100.0),
                    centerY = 200.0 + (index * 100.0),
                ),
                level = LobbyOcrAnchorLevel.ELEMENT,
            )
        }
        return LobbyResolvedOcrAnchorGroup(
            screenshotIndex = screenshotIndex,
            anchors = anchors,
            directlyObservedAnchorCount = anchors.size,
            alignmentError = 0.0,
        )
    }

    private fun observation(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = LobbyOcrAnchorObservation(
        text = text,
        boundingBox = RawOcrBoundingBox(left, top, right, bottom),
        level = LobbyOcrAnchorLevel.LINE,
    )
}
