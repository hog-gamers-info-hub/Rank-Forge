package com.hoggamers.rankforge.domain.ocr.customdesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignGridBuilderTest {
    private val builder = CustomDesignGridBuilder()

    @Test
    fun preservesDetectedOcrRowsExactlyWithoutSnappingThemToFit() {
        val geometry = builder.build(detection(rows = mapOf(2 to 399f, 3 to 453f, 4 to 506f, 5 to 560f)))

        assertEquals(506f, geometry.rowY[4]?.y)
        assertEquals(CustomDesignRowCoordinateSource.OCR, geometry.rowY[4]?.source)
    }

    @Test
    fun extrapolatesReliableRankOneFromRealisticRows() {
        val geometry = builder.build(
            detection(
                rows = mapOf(
                    2 to 399f,
                    3 to 453f,
                    4 to 506f,
                    5 to 560f,
                    6 to 614f,
                    7 to 668f,
                    8 to 722f,
                    9 to 775f,
                    10 to 828.5f,
                    11 to 883f,
                    12 to 937f,
                ),
            ),
        )

        assertEquals(CustomDesignRowCoordinateSource.EXTRAPOLATED, geometry.rowY[1]?.source)
        assertEquals(345f, geometry.rowY[1]?.y ?: Float.NaN, 2f)
        assertEquals(53.8f, geometry.estimatedRowStep ?: Float.NaN, 1f)
    }

    @Test
    fun interpolatesAOneRankInternalGap() {
        val geometry = builder.build(
            detection(rows = mapOf(2 to 399f, 3 to 453f, 5 to 560f, 6 to 614f)),
        )

        assertEquals(CustomDesignRowCoordinateSource.INTERPOLATED, geometry.rowY[4]?.source)
        assertEquals(506.5f, geometry.rowY[4]?.y ?: Float.NaN, 2f)
    }

    @Test
    fun doesNotInferWithOnlyTwoDetectedRows() {
        val geometry = builder.build(detection(rows = mapOf(2 to 399f, 3 to 453f)))

        assertEquals(setOf(2, 3), geometry.rowY.keys)
        assertNull(geometry.estimatedRowStep)
    }

    @Test
    fun preservesRowsAndDoesNotInferWhenDetectedRowsAreNonMonotonic() {
        val geometry = builder.build(detection(rows = mapOf(4 to 200f, 5 to 190f, 6 to 250f)))

        assertEquals(setOf(4, 5, 6), geometry.rowY.keys)
        assertEquals(190f, geometry.rowY[5]?.y)
        assertNull(geometry.estimatedRowStep)
    }

    @Test
    fun rejectsLatticeWithResidualBeyondFitTolerance() {
        val geometry = builder.build(
            detection(rows = mapOf(1 to 100f, 2 to 154f, 3 to 208f, 4 to 300f)),
        )

        assertEquals(setOf(1, 2, 3, 4), geometry.rowY.keys)
        assertNull(geometry.estimatedRowStep)
    }

    @Test
    fun neverInterpolatesAnAmbiguousRank() {
        val geometry = builder.build(
            detection(
                rows = mapOf(2 to 399f, 3 to 453f, 5 to 560f, 6 to 614f),
                ambiguousRanks = setOf(4),
            ),
        )

        assertFalse(4 in geometry.rowY)
        assertEquals(CustomDesignRowCoordinateSource.OCR, geometry.rowY[3]?.source)
    }

    @Test
    fun extrapolatesAtMostOneMissingEdgeRank() {
        val geometry = builder.build(
            detection(rows = (2..12).associateWith { rank -> 291f + rank * 54f }),
        )

        assertTrue(1 in geometry.rowY)
        assertEquals(CustomDesignRowCoordinateSource.EXTRAPOLATED, geometry.rowY[1]?.source)
    }

    @Test
    fun doesNotExtrapolateTwoMissingRanksAtAnEdge() {
        val geometry = builder.build(
            detection(rows = (3..12).associateWith { rank -> 237f + rank * 54f }),
        )

        assertFalse(1 in geometry.rowY)
        assertFalse(2 in geometry.rowY)
    }

    @Test
    fun limitsInternalInferenceToTwoConsecutiveMissingRanks() {
        val twoMissing = builder.build(
            detection(rows = mapOf(2 to 399f, 3 to 453f, 6 to 615f, 7 to 669f)),
        )
        val threeMissing = builder.build(
            detection(rows = mapOf(2 to 399f, 3 to 453f, 7 to 669f, 8 to 723f)),
        )

        assertEquals(CustomDesignRowCoordinateSource.INTERPOLATED, twoMissing.rowY[4]?.source)
        assertEquals(CustomDesignRowCoordinateSource.INTERPOLATED, twoMissing.rowY[5]?.source)
        assertFalse(4 in threeMissing.rowY)
        assertFalse(5 in threeMissing.rowY)
        assertFalse(6 in threeMissing.rowY)
    }

    @Test
    fun omitsInferredRowsOutsideSourceBoundsInsteadOfClamping() {
        val geometry = builder.build(detection(rows = mapOf(2 to 0f, 3 to 100f, 4 to 200f)))

        assertFalse(1 in geometry.rowY)
        assertEquals(100f, geometry.estimatedRowStep)
    }

    @Test
    fun copiesDetectedColumnsWithoutInferenceOrReordering() {
        val columns = mapOf(
            CustomDesignAnchorField.POSITION_POINTS to 718f,
            CustomDesignAnchorField.TOTAL_KILLS to 837f,
        )
        val geometry = builder.build(
            detection(
                rows = mapOf(2 to 399f, 3 to 453f, 4 to 506f),
                columns = columns,
            ),
        )

        assertEquals(columns, geometry.columnX)
        assertFalse(geometry.hasAllColumns)
    }

    @Test
    fun preservesOriginalSourceDimensionsAndCoordinates() {
        val geometry = builder.build(
            detection(
                sourceWidth = 1080,
                sourceHeight = 1080,
                rows = mapOf(2 to 399f, 3 to 453f, 5 to 560f),
            ),
        )

        assertEquals(1080, geometry.sourceWidth)
        assertEquals(1080, geometry.sourceHeight)
        assertTrue(geometry.rowY.values.all { it.y in 0f..1080f })
    }

    @Test
    fun cellCenterCombinesKnownSourceSpaceAxesAndReturnsNullWhenMissing() {
        val geometry = builder.build(
            detection(
                columns = mapOf(CustomDesignAnchorField.TOTAL_POINTS to 945.5f),
                rows = mapOf(2 to 399f, 3 to 453f, 4 to 506f),
            ),
        )

        assertEquals(CustomDesignGridPoint(945.5f, 453f), geometry.cellCenter(CustomDesignAnchorField.TOTAL_POINTS, 3))
        assertNull(geometry.cellCenter(CustomDesignAnchorField.TEAM_NAME, 3))
        assertNull(geometry.cellCenter(CustomDesignAnchorField.TOTAL_POINTS, 12))
    }

    private fun detection(
        rows: Map<Int, Float>,
        columns: Map<CustomDesignAnchorField, Float> = emptyMap(),
        ambiguousRanks: Set<Int> = emptySet(),
        sourceWidth: Int = 1080,
        sourceHeight: Int = 1080,
    ) = CustomDesignAnchorDetectionResult(
        anchors = CustomDesignOcrAnchors(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            columnX = columns,
            rowY = rows,
        ),
        headerCenterY = emptyMap(),
        missingFields = emptySet(),
        ambiguousFields = emptySet(),
        ambiguousRanks = ambiguousRanks,
    )
}
