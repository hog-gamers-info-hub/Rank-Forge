package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomDesignCanvasRendererTest {
    private val renderer = CustomDesignCanvasRenderer()

    @Test
    fun validOneRowRenderSucceeds() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Success,
                renderer.render(canvas, rows(1), geometry()),
            )
            assertEquals(5, canvas.texts.size)
            assertTrue(canvas.texts.all { it.color == Color.BLACK })
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun validTwelveRowRenderSucceeds() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Success,
                renderer.render(canvas, rows(12), geometry()),
            )
            assertEquals(60, canvas.texts.size)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun suppliedRowOrderIsPreservedAndRankDoesNotSelectGeometryRow() {
        val canvas = RecordingCanvas()
        val suppliedRows = listOf(
            row(rank = 12, teamName = "First supplied"),
            row(rank = 1, teamName = "Second supplied"),
        )
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Success,
                renderer.render(canvas, suppliedRows, geometry()),
            )
            assertEquals(
                listOf("First supplied", "0", "1", "12", "24", "Second supplied", "0", "1", "12", "24"),
                canvas.texts.map { it.text },
            )
            assertEquals(100f, canvas.texts.first { it.text == "First supplied" }.centerY, 0.01f)
            assertEquals(200f, canvas.texts.first { it.text == "Second supplied" }.centerY, 0.01f)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun crossedColumnPositionsKeepSemanticFieldIdentity() {
        val canvas = RecordingCanvas()
        val crossed = geometry(
            columnX = linkedMapOf(
                CustomDesignAnchorField.TEAM_NAME to 900f,
                CustomDesignAnchorField.WIN to 100f,
                CustomDesignAnchorField.TOTAL_KILLS to 700f,
                CustomDesignAnchorField.POSITION_POINTS to 300f,
                CustomDesignAnchorField.TOTAL_POINTS to 500f,
            ),
        )
        try {
            assertEquals(CustomDesignCanvasRenderResult.Success, renderer.render(canvas, rows(1), crossed))
            assertEquals(900f, canvas.texts[0].centerX, 0.01f)
            assertEquals(100f, canvas.texts[1].centerX, 0.01f)
            assertEquals(700f, canvas.texts[2].centerX, 0.01f)
            assertEquals(300f, canvas.texts[3].centerX, 0.01f)
            assertEquals(500f, canvas.texts[4].centerX, 0.01f)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun semanticColorsFollowFieldsEvenWhenColumnsAreCrossed() {
        val canvas = RecordingCanvas()
        val colors = CustomDesignColumnTextColors.fromMap(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to "#112233",
                CustomDesignAnchorField.WIN to "#223344",
                CustomDesignAnchorField.TOTAL_KILLS to "#334455",
                CustomDesignAnchorField.POSITION_POINTS to "#445566",
                CustomDesignAnchorField.TOTAL_POINTS to "#556677",
            ),
        )!!
        val crossed = geometry(
            columnX = linkedMapOf(
                CustomDesignAnchorField.TEAM_NAME to 900f,
                CustomDesignAnchorField.WIN to 100f,
                CustomDesignAnchorField.TOTAL_KILLS to 700f,
                CustomDesignAnchorField.POSITION_POINTS to 300f,
                CustomDesignAnchorField.TOTAL_POINTS to 500f,
            ),
        )
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Success,
                renderer.render(canvas, rows(1), crossed, colors),
            )
            assertEquals(
                listOf("#112233", "#223344", "#334455", "#445566", "#556677")
                    .map(Color::parseColor),
                canvas.texts.map { it.color },
            )
            assertEquals(900f, canvas.texts[0].centerX, 0.01f)
            assertEquals(100f, canvas.texts[1].centerX, 0.01f)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun fewerThanTwelveRowsOnlyRenderSuppliedRows() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(CustomDesignCanvasRenderResult.Success, renderer.render(canvas, rows(3), geometry()))
            assertEquals(15, canvas.texts.size)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun moreThanTwelveRowsFailsBeforeDrawing() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Failure(CustomDesignCanvasRenderFailure.INVALID_ROW_COUNT),
                renderer.render(canvas, rows(13), geometry()),
            )
            assertTrue(canvas.texts.isEmpty())
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun missingSemanticColumnFailsBeforeDrawing() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Failure(CustomDesignCanvasRenderFailure.MISSING_COLUMN),
                renderer.render(
                    canvas,
                    rows(1),
                    geometry(columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 100f)),
                ),
            )
            assertTrue(canvas.texts.isEmpty())
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun missingGeometryRowFailsBeforeDrawing() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                CustomDesignCanvasRenderResult.Failure(CustomDesignCanvasRenderFailure.MISSING_ROW),
                renderer.render(
                    canvas,
                    rows(1),
                    geometry(rowY = (1..11).associateWith { it * 100f }),
                ),
            )
            assertTrue(canvas.texts.isEmpty())
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun outOfBoundsOrNonFiniteCoordinatesFailBeforeDrawing() {
        listOf(
            geometry(columnX = columns(CustomDesignAnchorField.TEAM_NAME to -1f)) to
                CustomDesignCanvasRenderFailure.INVALID_COORDINATE,
            geometry(columnX = columns(CustomDesignAnchorField.WIN to Float.POSITIVE_INFINITY)) to
                CustomDesignCanvasRenderFailure.INVALID_COORDINATE,
            geometry(rowY = rowsY(1 to 1301f)) to
                CustomDesignCanvasRenderFailure.INVALID_COORDINATE,
            geometry(rowY = rowsY(2 to Float.NaN)) to
                CustomDesignCanvasRenderFailure.INVALID_COORDINATE,
        ).forEach { (invalidGeometry, failure) ->
            val canvas = RecordingCanvas()
            try {
                assertEquals(
                    CustomDesignCanvasRenderResult.Failure(failure),
                    renderer.render(canvas, rows(1), invalidGeometry),
                )
                assertTrue(canvas.texts.isEmpty())
            } finally {
                canvas.recycle()
            }
        }
    }

    @Test
    fun nonPositiveSourceDimensionsFailBeforeDrawing() {
        listOf(
            geometry(sourceWidth = 0),
            geometry(sourceHeight = 0),
        ).forEach { invalidGeometry ->
            val canvas = RecordingCanvas()
            try {
                assertEquals(
                    CustomDesignCanvasRenderResult.Failure(
                        CustomDesignCanvasRenderFailure.INVALID_SOURCE_DIMENSIONS,
                    ),
                    renderer.render(canvas, rows(1), invalidGeometry),
                )
                assertTrue(canvas.texts.isEmpty())
            } finally {
                canvas.recycle()
            }
        }
    }

    private fun geometry(
        sourceWidth: Int = 1000,
        sourceHeight: Int = 1300,
        columnX: Map<CustomDesignAnchorField, Float> = columns(),
        rowY: Map<Int, Float> = rowsY(),
    ) = CustomDesignEffectiveGridGeometry(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        columnX = columnX,
        rowY = rowY,
    )

    private fun columns(
        vararg replacements: Pair<CustomDesignAnchorField, Float>,
    ): Map<CustomDesignAnchorField, Float> =
        linkedMapOf(
            CustomDesignAnchorField.TEAM_NAME to 100f,
            CustomDesignAnchorField.WIN to 300f,
            CustomDesignAnchorField.TOTAL_KILLS to 500f,
            CustomDesignAnchorField.POSITION_POINTS to 700f,
            CustomDesignAnchorField.TOTAL_POINTS to 900f,
        ).apply { putAll(replacements) }

    private fun rowsY(
        vararg replacements: Pair<Int, Float>,
    ): Map<Int, Float> =
        (1..12).associateWith { it * 100f }.toMutableMap().apply { putAll(replacements) }

    private fun rows(count: Int) = (1..count).map { row(rank = it, teamName = "Team $it") }

    private fun row(rank: Int?, teamName: String) = ResultExportRow(
        rank = rank,
        teamName = teamName,
        win = 0,
        totalKills = 1,
        positionPoints = 12,
        totalPoints = 24,
    )

    private class RecordingCanvas(
        private val bitmap: Bitmap = Bitmap.createBitmap(1000, 1300, Bitmap.Config.ARGB_8888),
    ) : Canvas(bitmap) {
        val texts = mutableListOf<DrawnText>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            val width = paint.measureText(text)
            texts += DrawnText(
                text = text,
                color = paint.color,
                centerX = x + width / 2f,
                centerY = y + (paint.ascent() + paint.descent()) / 2f,
            )
            super.drawText(text, x, y, paint)
        }

        fun recycle() {
            bitmap.recycle()
        }
    }

    private data class DrawnText(
        val text: String,
        val color: Int,
        val centerX: Float,
        val centerY: Float,
    )
}
