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

    @Test
    fun handleDragOutwardIncreasesLogoSize() {
        val input = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.2f)

        val resized = resizePointTableLogoFromHandleDrag(
            placement = input,
            dragX = 60f,
            dragY = 60f,
            containerWidthPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertTrue(resized.widthRatio > input.widthRatio)
        assertEquals(input.centerXRatio, resized.centerXRatio, 0f)
        assertEquals(input.centerYRatio, resized.centerYRatio, 0f)
    }

    @Test
    fun handleDragInwardDecreasesLogoSize() {
        val input = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.3f)

        val resized = resizePointTableLogoFromHandleDrag(
            placement = input,
            dragX = -60f,
            dragY = -60f,
            containerWidthPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertTrue(resized.widthRatio < input.widthRatio)
    }

    @Test
    fun handleResizePreservesLogoAspectRatio() {
        val resized = resizePointTableLogoFromHandleDrag(
            placement = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.2f),
            dragX = 80f,
            dragY = 20f,
            containerWidthPx = 1200f,
            designAspectRatio = 1.5f,
            logoAspectRatio = 2f,
        )
        val bounds = pointTableLogoBounds(
            placement = resized,
            containerWidthPx = 1200f,
            containerHeightPx = 800f,
            logoAspectRatio = 2f,
        )

        assertEquals(2f, bounds.widthPx / bounds.heightPx, 0.0001f)
    }

    @Test
    fun handleResizeHonorsMinimumLogoSize() {
        val resized = resizePointTableLogoFromHandleDrag(
            placement = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.04f),
            dragX = -10_000f,
            dragY = -10_000f,
            containerWidthPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertEquals(0.03f, resized.widthRatio, 0f)
    }

    @Test
    fun handleResizeHonorsMaximumSizeAndBoundsClamp() {
        val resized = resizePointTableLogoFromHandleDrag(
            placement = PointTableLogoPlacementGeometry(0.9f, 0.9f, 0.2f),
            dragX = 10_000f,
            dragY = 10_000f,
            containerWidthPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )
        val bounds = pointTableLogoBounds(
            placement = resized,
            containerWidthPx = 1000f,
            containerHeightPx = 1000f,
            logoAspectRatio = 1f,
        )

        assertEquals(1f, resized.widthRatio, 0f)
        assertEquals(0f, bounds.leftPx, 0f)
        assertEquals(0f, bounds.topPx, 0f)
        assertEquals(1000f, bounds.rightPx, 0f)
        assertEquals(1000f, bounds.bottomPx, 0f)
    }

    @Test
    fun normalLogoDragMovesWithoutResizing() {
        val moved = transformPointTableLogoPlacement(
            placement = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.2f),
            panX = 100f,
            panY = -50f,
            zoom = 1f,
            containerWidthPx = 1000f,
            containerHeightPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertEquals(0.6f, moved.centerXRatio, 0f)
        assertEquals(0.45f, moved.centerYRatio, 0f)
        assertEquals(0.2f, moved.widthRatio, 0f)
    }

    @Test
    fun pinchResizeStillUpdatesSharedWidthGeometry() {
        val resized = transformPointTableLogoPlacement(
            placement = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.2f),
            panX = 0f,
            panY = 0f,
            zoom = 2f,
            containerWidthPx = 1000f,
            containerHeightPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertEquals(0.4f, resized.widthRatio, 0f)
    }

    @Test
    fun selectionOverlayUsesCircularHandleHitTestingWithoutChangingPersistedGeometry() {
        val placement = PointTableLogoPlacementGeometry(0.4f, 0.6f, 0.2f)
        val bounds = pointTableLogoBounds(
            placement = placement,
            containerWidthPx = 1000f,
            containerHeightPx = 1000f,
            logoAspectRatio = 2f,
        )
        val handle = pointTableLogoResizeHandle(
            logoBounds = bounds,
            handleDiameterPx = 44f,
        )

        assertEquals(
            PointTableLogoPlacementGeometry(0.4f, 0.6f, 0.2f),
            placement,
        )
        assertEquals(bounds.rightPx + 22f, handle.centerXPx, 0f)
        assertEquals(bounds.bottomPx + 22f, handle.centerYPx, 0f)
        assertTrue(
            isPointTableLogoResizeHandleHit(
                pointerX = handle.centerXPx,
                pointerY = handle.centerYPx,
                handle = handle,
            ),
        )
        assertTrue(
            !isPointTableLogoResizeHandleHit(
                pointerX = handle.centerXPx + handle.radiusPx + 1f,
                pointerY = handle.centerYPx,
                handle = handle,
            ),
        )
    }

    @Test
    fun smallLogoNearBottomRightOutsideHandleStillUsesMovePath() {
        val placement = PointTableLogoPlacementGeometry(0.95f, 0.95f, 0.04f)
        val bounds = pointTableLogoBounds(
            placement = placement,
            containerWidthPx = 1000f,
            containerHeightPx = 1000f,
            logoAspectRatio = 1f,
        )
        val handle = pointTableLogoResizeHandle(
            logoBounds = bounds,
            handleDiameterPx = 44f,
        )

        assertTrue(bounds.rightPx - 1f in bounds.leftPx..bounds.rightPx)
        assertTrue(bounds.bottomPx - 1f in bounds.topPx..bounds.bottomPx)
        assertTrue(
            !isPointTableLogoResizeHandleHit(
                pointerX = bounds.rightPx - 1f,
                pointerY = bounds.bottomPx - 1f,
                handle = handle,
            ),
        )

        val moved = transformPointTableLogoPlacement(
            placement = placement,
            panX = 10f,
            panY = 10f,
            zoom = 1f,
            containerWidthPx = 1000f,
            containerHeightPx = 1000f,
            designAspectRatio = 1f,
            logoAspectRatio = 1f,
        )

        assertTrue(moved.centerXRatio > placement.centerXRatio)
        assertTrue(moved.centerYRatio > placement.centerYRatio)
        assertEquals(placement.widthRatio, moved.widthRatio, 0f)
    }
}
