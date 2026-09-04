package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.geometry.Offset
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomDesignGridInteractionTest {
    private val transform = SourceToPreviewTransform.fit(
        sourceWidth = 1000,
        sourceHeight = 1000,
        containerWidth = 1000f,
        containerHeight = 1000f,
    ) ?: error("Expected a valid transform")

    @Test
    fun closeToVerticalLineSelectsVerticalCandidate() {
        val candidates = findCustomDesignGridHitCandidates(
            pointer = Offset(300f, 550f),
            geometry = geometry(),
            transform = transform,
            hitTolerancePx = 8f,
        )

        assertEquals(CustomDesignAnchorField.TEAM_NAME, candidates.columnField)
        assertNull(candidates.rowRank)
    }

    @Test
    fun closeToHorizontalLineSelectsHorizontalCandidate() {
        val candidates = findCustomDesignGridHitCandidates(
            pointer = Offset(500f, 400f),
            geometry = geometry(),
            transform = transform,
            hitTolerancePx = 8f,
        )

        assertNull(candidates.columnField)
        assertEquals(2, candidates.rowRank)
    }

    @Test
    fun pointerAwayFromLinesSelectsNothing() {
        val candidates = findCustomDesignGridHitCandidates(
            pointer = Offset(600f, 600f),
            geometry = geometry(),
            transform = transform,
            hitTolerancePx = 8f,
        )

        assertNull(candidates.columnField)
        assertNull(candidates.rowRank)
    }

    @Test
    fun intersectionUsesDragDirectionToChooseOneAxis() {
        val candidates = findCustomDesignGridHitCandidates(
            pointer = Offset(300f, 400f),
            geometry = geometry(),
            transform = transform,
            hitTolerancePx = 8f,
        )

        assertEquals(
            CustomDesignGridSelection.Column(CustomDesignAnchorField.TEAM_NAME),
            chooseCustomDesignGridSelection(candidates, Offset(40f, 3f)),
        )
        assertEquals(
            CustomDesignGridSelection.Row(2),
            chooseCustomDesignGridSelection(candidates, Offset(3f, 40f)),
        )
    }

    @Test
    fun pointerCoordinatesClampToImageBounds() {
        assertEquals(0f, customDesignColumnSourceX(-10f, transform, 1000) ?: Float.NaN, 0f)
        assertEquals(1000f, customDesignColumnSourceX(1100f, transform, 1000) ?: Float.NaN, 0f)
        assertEquals(0f, customDesignRowSourceY(-10f, transform, 1000) ?: Float.NaN, 0f)
        assertEquals(1000f, customDesignRowSourceY(1100f, transform, 1000) ?: Float.NaN, 0f)
    }

    @Test
    fun rowCannotCrossResolvedNeighbors() {
        val geometry = geometry()

        assertEquals(
            499f,
            constrainCustomDesignRowSourceY(2, 900f, geometry) ?: Float.NaN,
            0f,
        )
        assertEquals(
            301f,
            constrainCustomDesignRowSourceY(2, 0f, geometry) ?: Float.NaN,
            0f,
        )
    }

    private fun geometry() = CustomDesignEffectiveGridGeometry(
        sourceWidth = 1000,
        sourceHeight = 1000,
        columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 300f),
        rowY = mapOf(1 to 300f, 2 to 400f, 3 to 500f),
    )
}
