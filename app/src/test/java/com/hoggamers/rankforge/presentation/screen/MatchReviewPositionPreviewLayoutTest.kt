package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchReviewPositionPreviewLayoutTest {
    @Test
    fun resultPositionPreviewHeightUsesTheNaturalCropRatioWithoutACap() {
        assertEquals(43.2f, calculateResultPositionPreviewHeight(360.dp, 0.12f).value, 0.001f)
        assertEquals(54f, calculateResultPositionPreviewHeight(360.dp, 0.15f).value, 0.001f)
        assertEquals(72f, calculateResultPositionPreviewHeight(360.dp, 0.20f).value, 0.001f)
        assertEquals(180f, calculateResultPositionPreviewHeight(360.dp, 0.50f).value, 0.001f)
    }
}
