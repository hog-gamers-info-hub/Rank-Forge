package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultPositionPageSelectionTest {
    @Test
    fun positionElevenPrefersSemanticLowerRegardlessOfPhysicalSlot() {
        val semanticUpperInLowerSlot = ResultPositionPageItem(
            position = 11,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            semanticRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val semanticLowerInUpperSlot = ResultPositionPageItem(
            position = 11,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            semanticRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        )

        assertEquals(
            semanticLowerInUpperSlot,
            selectResultPositionPageItem(
                listOf(semanticUpperInLowerSlot, semanticLowerInUpperSlot),
            ),
        )
    }

    @Test
    fun legacyPositionElevenFallbackKeepsPhysicalLowerPriority() {
        val upper = ResultPositionPageItem(
            position = 11,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val lower = ResultPositionPageItem(
            position = 11,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        )

        assertEquals(lower, selectResultPositionPageItem(listOf(upper, lower)))
    }
}
