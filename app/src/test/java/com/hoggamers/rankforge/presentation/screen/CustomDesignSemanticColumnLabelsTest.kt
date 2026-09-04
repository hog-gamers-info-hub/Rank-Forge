package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDesignSemanticColumnLabelsTest {
    @Test
    fun labelsUseTheirSemanticFieldKeysAndEffectiveXCoordinates() {
        val geometry = geometry(
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 100f,
                CustomDesignAnchorField.WIN to 300f,
                CustomDesignAnchorField.POSITION_POINTS to 500f,
                CustomDesignAnchorField.TOTAL_KILLS to 700f,
                CustomDesignAnchorField.TOTAL_POINTS to 900f,
            ),
        )

        val labels = customDesignSemanticColumnLabels(geometry)

        assertEquals(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to "TEAM_NAME",
                CustomDesignAnchorField.WIN to "WIN",
                CustomDesignAnchorField.TOTAL_KILLS to "TOTAL_KILLS",
                CustomDesignAnchorField.POSITION_POINTS to "POSITION_POINTS",
                CustomDesignAnchorField.TOTAL_POINTS to "TOTAL_POINTS",
            ),
            labels.associate { it.field to it.text },
        )
        assertEquals(100f, labels.single { it.field == CustomDesignAnchorField.TEAM_NAME }.sourceX)
        assertEquals(300f, labels.single { it.field == CustomDesignAnchorField.WIN }.sourceX)
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            labels.single { it.field == CustomDesignAnchorField.TEAM_NAME }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.LOWER,
            labels.single { it.field == CustomDesignAnchorField.WIN }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            labels.single { it.field == CustomDesignAnchorField.POSITION_POINTS }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.LOWER,
            labels.single { it.field == CustomDesignAnchorField.TOTAL_KILLS }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            labels.single { it.field == CustomDesignAnchorField.TOTAL_POINTS }.level,
        )
    }

    @Test
    fun crossingColumnPositionsRecalculateLanesWithoutSwappingIdentities() {
        val labels = customDesignSemanticColumnLabels(
            geometry(
                columnX = mapOf(
                    CustomDesignAnchorField.WIN to 100f,
                    CustomDesignAnchorField.TOTAL_POINTS to 250f,
                    CustomDesignAnchorField.TEAM_NAME to 500f,
                    CustomDesignAnchorField.POSITION_POINTS to 700f,
                    CustomDesignAnchorField.TOTAL_KILLS to 900f,
                ),
            ),
        )

        assertEquals(500f, labels.single { it.field == CustomDesignAnchorField.TEAM_NAME }.sourceX)
        assertEquals(100f, labels.single { it.field == CustomDesignAnchorField.WIN }.sourceX)
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            labels.single { it.field == CustomDesignAnchorField.TEAM_NAME }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            labels.single { it.field == CustomDesignAnchorField.WIN }.level,
        )
        assertEquals("TEAM_NAME", labels.single { it.field == CustomDesignAnchorField.TEAM_NAME }.text)
        assertEquals("WIN", labels.single { it.field == CustomDesignAnchorField.WIN }.text)
        assertEquals(
            listOf(
                CustomDesignAnchorField.WIN,
                CustomDesignAnchorField.TOTAL_POINTS,
                CustomDesignAnchorField.TEAM_NAME,
                CustomDesignAnchorField.POSITION_POINTS,
                CustomDesignAnchorField.TOTAL_KILLS,
            ),
            labels.sortedBy { it.sourceX }.map { it.field },
        )
    }

    @Test
    fun movingAFieldCanChangeItsLaneUsingCurrentEffectiveX() {
        val before = customDesignSemanticColumnLabels(
            geometry(
                columnX = mapOf(
                    CustomDesignAnchorField.TEAM_NAME to 100f,
                    CustomDesignAnchorField.WIN to 300f,
                    CustomDesignAnchorField.TOTAL_KILLS to 500f,
                    CustomDesignAnchorField.POSITION_POINTS to 700f,
                    CustomDesignAnchorField.TOTAL_POINTS to 900f,
                ),
            ),
        )
        val after = customDesignSemanticColumnLabels(
            geometry(
                columnX = mapOf(
                    CustomDesignAnchorField.TEAM_NAME to 400f,
                    CustomDesignAnchorField.WIN to 300f,
                    CustomDesignAnchorField.TOTAL_KILLS to 500f,
                    CustomDesignAnchorField.POSITION_POINTS to 700f,
                    CustomDesignAnchorField.TOTAL_POINTS to 900f,
                ),
            ),
        )

        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            before.single { it.field == CustomDesignAnchorField.TEAM_NAME }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.LOWER,
            after.single { it.field == CustomDesignAnchorField.TEAM_NAME }.level,
        )
        assertEquals(400f, after.single { it.field == CustomDesignAnchorField.TEAM_NAME }.sourceX)
    }

    @Test
    fun equalXValuesUseSemanticOrdinalAsDeterministicTieBreaker() {
        val labels = customDesignSemanticColumnLabels(
            geometry(
                columnX = mapOf(
                    CustomDesignAnchorField.TEAM_NAME to 100f,
                    CustomDesignAnchorField.WIN to 100f,
                    CustomDesignAnchorField.TOTAL_KILLS to 300f,
                    CustomDesignAnchorField.POSITION_POINTS to 500f,
                    CustomDesignAnchorField.TOTAL_POINTS to 700f,
                ),
            ),
        )

        assertEquals(
            CustomDesignSemanticColumnLabelLevel.UPPER,
            labels.single { it.field == CustomDesignAnchorField.TEAM_NAME }.level,
        )
        assertEquals(
            CustomDesignSemanticColumnLabelLevel.LOWER,
            labels.single { it.field == CustomDesignAnchorField.WIN }.level,
        )
    }

    @Test
    fun fallbackColumnsStillProduceAllFiveSemanticIdentitiesWithoutChangingRows() {
        val rows = mapOf(1 to 100f, 2 to 200f, 12 to 1200f)
        val geometry = geometry(
            columnX = CustomDesignAnchorField.entries
                .mapIndexed { index, field -> field to (100 + index * 100).toFloat() }
                .toMap(),
            rowY = rows,
        )
        val labels = customDesignSemanticColumnLabels(
            geometry,
        )

        assertEquals(CustomDesignAnchorField.entries.toSet(), labels.map { it.field }.toSet())
        assertEquals(rows, geometry.rowY)
        assertEquals(CustomDesignAnchorField.entries.map { it.name }, labels.map { it.text })
    }

    private fun geometry(
        columnX: Map<CustomDesignAnchorField, Float> = mapOf(
            CustomDesignAnchorField.TEAM_NAME to 100f,
            CustomDesignAnchorField.WIN to 300f,
            CustomDesignAnchorField.TOTAL_KILLS to 500f,
            CustomDesignAnchorField.POSITION_POINTS to 700f,
            CustomDesignAnchorField.TOTAL_POINTS to 900f,
        ),
        rowY: Map<Int, Float> = mapOf(1 to 100f, 2 to 200f, 12 to 1200f),
    ) = CustomDesignEffectiveGridGeometry(
        sourceWidth = 1080,
        sourceHeight = 1350,
        columnX = columnX,
        rowY = rowY,
    )
}
