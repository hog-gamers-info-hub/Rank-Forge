package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointTableLogoPlacementEditorTest {
    @Test
    fun defaultPlacementIsCenteredAndVisibleForSquareDesignAndLogo() {
        val placement = defaultPointTableLogoPlacement(
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertEquals(0.5f, placement.centerXRatio, 0f)
        assertEquals(0.5f, placement.centerYRatio, 0f)
        assertEquals(0.2f, placement.widthRatio, 0f)
    }

    @Test
    fun clampKeepsWideLogoInsideDesignAndResizesToTheDynamicMaximum() {
        val placement = clampPointTableLogoPlacement(
            placement = PointTableLogoPlacementGeometry(0f, 1f, 2f),
            designAspectRatio = 2f,
            logoAspectRatio = 0.25f,
        )

        assertEquals(0.25f, placement.centerXRatio, 0f)
        assertEquals(0.5f, placement.centerYRatio, 0f)
        assertEquals(0.5f, placement.widthRatio, 0f)
    }

    @Test
    fun clampKeepsTallLogoInsideDesignAndHonorsMinimumSize() {
        val placement = clampPointTableLogoPlacement(
            placement = PointTableLogoPlacementGeometry(-1f, 2f, 0.001f),
            designAspectRatio = 2f,
            logoAspectRatio = 4f,
        )

        assertEquals(0.015f, placement.centerXRatio, 0f)
        assertEquals(0.998125f, placement.centerYRatio, 0f)
        assertEquals(0.03f, placement.widthRatio, 0f)
    }

    @Test
    fun clampRejectsNonFiniteSizeByFallingBackToMinimumAndClampsCenter() {
        val placement = clampPointTableLogoPlacement(
            placement = PointTableLogoPlacementGeometry(
                centerXRatio = Float.NaN,
                centerYRatio = Float.POSITIVE_INFINITY,
                widthRatio = Float.NaN,
            ),
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertEquals(0.5f, placement.centerXRatio, 0f)
        assertEquals(0.5f, placement.centerYRatio, 0f)
        assertEquals(0.03f, placement.widthRatio, 0f)
        assertTrue(placement.widthRatio.isFinite())
    }

    @Test
    fun invalidAspectRatiosLeaveInputGeometryUntouched() {
        val input = PointTableLogoPlacementGeometry(0.2f, 0.7f, 0.4f)

        assertEquals(
            input,
            clampPointTableLogoPlacement(input, Float.NaN, 1f),
        )
        assertEquals(
            input,
            clampPointTableLogoPlacement(input, 1f, 0f),
        )
    }
}
