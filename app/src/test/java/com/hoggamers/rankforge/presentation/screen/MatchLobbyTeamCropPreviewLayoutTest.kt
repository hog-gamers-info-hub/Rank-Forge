package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchLobbyTeamCropPreviewLayoutTest {
    @Test
    fun tallestCropDeterminesCommonPagerHeightRatio() {
        val commonDisplayWidth = 1000f
        val dimensions = listOf(
            TeamCropBitmapDimensions(width = 1000, height = 300),
            TeamCropBitmapDimensions(width = 1000, height = 360),
            TeamCropBitmapDimensions(width = 1000, height = 320),
        )

        val maxHeight = commonDisplayWidth * calculateMaxTeamCropHeightRatio(dimensions)

        assertEquals(360f, maxHeight, 0.001f)
    }

    @Test
    fun eachCropKeepsItsNaturalHeightInsideTheCommonOuterHeight() {
        val commonDisplayWidth = 1000f
        val dimensions = listOf(
            TeamCropBitmapDimensions(width = 1000, height = 300),
            TeamCropBitmapDimensions(width = 1000, height = 360),
            TeamCropBitmapDimensions(width = 1000, height = 320),
        )
        val commonOuterHeight = commonDisplayWidth * calculateMaxTeamCropHeightRatio(dimensions)
        val naturalHeights = dimensions.map { dimension ->
            commonDisplayWidth * dimension.height / dimension.width
        }

        assertEquals(360f, commonOuterHeight, 0.001f)
        assertEquals(listOf(300f, 360f, 320f), naturalHeights)
        naturalHeights.zip(listOf(60f, 0f, 40f)).forEach { (naturalHeight, expectedInset) ->
            assertEquals(expectedInset, commonOuterHeight - naturalHeight, 0.001f)
        }
    }
}
