package com.hoggamers.rankforge.domain.ocr.customdesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignEditableGridInitializerTest {
    @Test
    fun preservesAllAutomaticColumnsExactly() {
        val automatic = automaticGeometry().copy(columnX = allColumns())

        val editable = initialize(automatic)

        CustomDesignAnchorField.entries.forEach { field ->
            assertEquals(automatic.columnX[field], editable.columnX[field]?.x)
            assertEquals(
                CustomDesignEditableCoordinateSource.AUTOMATIC,
                editable.columnX[field]?.source,
            )
        }
    }

    @Test
    fun missingColumnStillProducesAllFiveColumns() {
        val automatic = automaticGeometry().copy(
            columnX = allColumns() - CustomDesignAnchorField.TOTAL_KILLS,
        )

        val editable = initialize(automatic)

        assertEquals(CustomDesignAnchorField.entries.size, editable.columnX.size)
        assertEquals(
            CustomDesignEditableCoordinateSource.ESTIMATED,
            editable.columnX[CustomDesignAnchorField.TOTAL_KILLS]?.source,
        )
    }

    @Test
    fun severalMissingColumnsUseDistinctBoundedFallbacks() {
        val editable = initialize(
            automaticGeometry().copy(
                columnX = mapOf(CustomDesignAnchorField.WIN to 450f),
            ),
        )

        val missing = CustomDesignAnchorField.entries
            .filter { it != CustomDesignAnchorField.WIN }
            .map { editable.columnX[it]!! }
        assertEquals(CustomDesignAnchorField.entries.size, editable.columnX.size)
        assertTrue(missing.all { it.source == CustomDesignEditableCoordinateSource.FALLBACK })
        assertTrue(missing.all { it.x in 0f..1080f && it.x.isFinite() })
        assertEquals(missing.size, missing.map { it.x }.toSet().size)
    }

    @Test
    fun zeroAutomaticColumnsStillProducesAllFiveFallbackColumns() {
        val editable = initialize(null)

        assertEquals(CustomDesignAnchorField.entries.size, editable.columnX.size)
        assertTrue(editable.columnX.values.all { it.source == CustomDesignEditableCoordinateSource.FALLBACK })
    }

    @Test
    fun safeSemanticInterpolationMarksMissingColumnEstimated() {
        val automatic = automaticGeometry().copy(
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 100f,
                CustomDesignAnchorField.WIN to 400f,
                CustomDesignAnchorField.POSITION_POINTS to 700f,
                CustomDesignAnchorField.TOTAL_POINTS to 1000f,
            ),
        )

        val editable = initialize(automatic)

        assertEquals(550f, editable.columnX[CustomDesignAnchorField.TOTAL_KILLS]?.x)
        assertEquals(
            CustomDesignEditableCoordinateSource.ESTIMATED,
            editable.columnX[CustomDesignAnchorField.TOTAL_KILLS]?.source,
        )
    }

    @Test
    fun unsafeDetectedOrderingIsNotReorderedAndMissingColumnFallsBack() {
        val automatic = automaticGeometry().copy(
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 100f,
                CustomDesignAnchorField.WIN to 400f,
                CustomDesignAnchorField.TOTAL_KILLS to 700f,
                CustomDesignAnchorField.POSITION_POINTS to 600f,
            ),
        )

        val editable = initialize(automatic)

        assertEquals(700f, editable.columnX[CustomDesignAnchorField.TOTAL_KILLS]?.x)
        assertEquals(600f, editable.columnX[CustomDesignAnchorField.POSITION_POINTS]?.x)
        assertEquals(
            CustomDesignEditableCoordinateSource.FALLBACK,
            editable.columnX[CustomDesignAnchorField.TOTAL_POINTS]?.source,
        )
    }

    @Test
    fun automaticRowsRemainExactAndMissingInternalRowsAreEstimated() {
        val automatic = automaticGeometry().copy(
            rowY = mapOf(
                1 to row(100f, CustomDesignRowCoordinateSource.OCR),
                3 to row(300f, CustomDesignRowCoordinateSource.OCR),
            ),
        )

        val editable = initialize(automatic)

        assertEquals(100f, editable.rowY[1]?.y)
        assertEquals(200f, editable.rowY[2]?.y)
        assertEquals(CustomDesignEditableCoordinateSource.ESTIMATED, editable.rowY[2]?.source)
        assertEquals(300f, editable.rowY[3]?.y)
    }

    @Test
    fun largeInternalMissingRunIsFilledForEditor() {
        val automatic = automaticGeometry().copy(
            rowY = mapOf(
                1 to row(100f, CustomDesignRowCoordinateSource.OCR),
                12 to row(1200f, CustomDesignRowCoordinateSource.OCR),
            ),
            estimatedRowStep = null,
        )

        val editable = initialize(automatic)

        assertEquals(12, editable.rowY.size)
        assertEquals(600f, editable.rowY[6]?.y)
        assertTrue((2..11).all { editable.rowY[it]?.source == CustomDesignEditableCoordinateSource.ESTIMATED })
    }

    @Test
    fun reliableSpacingEstimatesLeadingAndTrailingRows() {
        val automatic = automaticGeometry().copy(
            rowY = mapOf(
                3 to row(300f, CustomDesignRowCoordinateSource.OCR),
                10 to row(1000f, CustomDesignRowCoordinateSource.OCR),
            ),
            estimatedRowStep = 100f,
        )

        val editable = initialize(automatic)

        assertEquals(100f, editable.rowY[1]?.y)
        assertEquals(200f, editable.rowY[2]?.y)
        assertEquals(1100f, editable.rowY[11]?.y)
        assertEquals(1200f, editable.rowY[12]?.y)
        assertTrue(editable.rowY[1]?.source == CustomDesignEditableCoordinateSource.ESTIMATED)
        assertTrue(editable.rowY[12]?.source == CustomDesignEditableCoordinateSource.ESTIMATED)
    }

    @Test
    fun zeroOneOrTwoAutomaticRowsStillProduceCompleteStrictRows() {
        listOf(
            null,
            mapOf(6 to row(600f, CustomDesignRowCoordinateSource.OCR)),
            mapOf(
                4 to row(400f, CustomDesignRowCoordinateSource.OCR),
                9 to row(900f, CustomDesignRowCoordinateSource.OCR),
            ),
        ).forEach { rows ->
            val editable = initialize(automaticGeometry().copy(rowY = rows.orEmpty()))
            val values = (1..12).map { editable.rowY[it]!!.y }
            assertEquals(12, values.size)
            assertTrue(values.all { it.isFinite() && it in 0f..1350f })
            assertTrue(values.zipWithNext().all { (left, right) -> right > left })
        }

        val oneAutomaticRow = initialize(
            automaticGeometry().copy(
                rowY = mapOf(6 to row(600f, CustomDesignRowCoordinateSource.OCR)),
            ),
        )
        assertEquals(600f, oneAutomaticRow.rowY[6]?.y)
        assertEquals(CustomDesignEditableCoordinateSource.AUTOMATIC, oneAutomaticRow.rowY[6]?.source)
    }

    @Test
    fun manualOverrideWinsOverEstimatedAndFallbackCoordinates() {
        val automatic = automaticGeometry().copy(
            columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 100f),
            rowY = emptyMap(),
        )
        val editable = initialize(automatic)
        val effective = resolveCustomDesignEffectiveGridGeometry(
            editable = editable,
            overrides = CustomDesignGridOverrides(
                columnX = mapOf(CustomDesignAnchorField.WIN to 777f),
                rowY = mapOf(7 to 888f),
            ),
        )

        assertEquals(777f, effective?.columnX?.get(CustomDesignAnchorField.WIN))
        assertEquals(888f, effective?.rowY?.get(7))
        assertNotEquals(777f, editable.columnX[CustomDesignAnchorField.WIN]?.x)
        assertNotEquals(888f, editable.rowY[7]?.y)
    }

    @Test
    fun allEditorRowsAndColumnsStayInsideSourceBounds() {
        val editable = initialize(null)

        assertTrue(editable.columnX.values.all { it.x.isFinite() && it.x in 0f..1080f })
        assertTrue(editable.rowY.values.all { it.y.isFinite() && it.y in 0f..1350f })
    }

    private fun initialize(automatic: CustomDesignGridGeometry?) =
        CustomDesignEditableGridInitializer.initialize(
            sourceWidth = 1080,
            sourceHeight = 1350,
            automatic = automatic,
        ) ?: error("Expected valid editor geometry")

    private fun allColumns() = mapOf(
        CustomDesignAnchorField.TEAM_NAME to 100f,
        CustomDesignAnchorField.WIN to 400f,
        CustomDesignAnchorField.TOTAL_KILLS to 700f,
        CustomDesignAnchorField.POSITION_POINTS to 850f,
        CustomDesignAnchorField.TOTAL_POINTS to 1000f,
    )

    private fun automaticGeometry() = CustomDesignGridGeometry(
        sourceWidth = 1080,
        sourceHeight = 1350,
        columnX = allColumns(),
        rowY = (1..12).associateWith { rank ->
            row(rank * 100f, CustomDesignRowCoordinateSource.OCR)
        },
        estimatedRowStep = 100f,
    )

    private fun row(y: Float, source: CustomDesignRowCoordinateSource) =
        CustomDesignRowCoordinate(y = y, source = source)
}
