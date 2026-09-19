package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import java.time.LocalDate
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeDesignCanvasRendererTest {
    private val template = FreeDesignTemplateRegistry.default()
    private val template2 = requireNotNull(
        FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID),
    )
    private val renderer = FreeDesignCanvasRenderer()

    @Test
    fun validTournamentModelRendersMetadataAndConfiguredRows() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, tournamentModel(), template),
            )
            assertTrue(canvas.texts.any { it.text == "Champions Cup 2026" })
            assertTrue(canvas.texts.any { it.text == "HOG Gamers" })
            assertTrue(canvas.texts.any { it.text == "Overall Standings - 03 Sep 2026" })
            assertEquals(3 + 5, canvas.texts.size)
            assertTrue(canvas.texts.any { it.text == "Team 1" && it.startX == 190f })
            assertEquals(392f, canvas.texts.first { it.text == "Team 1" }.centerY, 1f)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun validMatchModelUsesMatchHeadingAndTournamentDate() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, matchModel(), template),
            )
            assertTrue(canvas.texts.any { it.text == "Match 4 - 03 Sep 2026" })
            assertFalse(canvas.texts.any { it.text == "31 Aug 2026" })
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun blankOrganizerIsSkippedWithoutMovingOtherHeaderAnchors() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, tournamentModel(organizerName = "  "), template),
            )
            assertFalse(canvas.texts.any { it.text == "HOG Gamers" })
            assertTrue(canvas.texts.any { it.text == "Overall Standings - 03 Sep 2026" })
            assertEquals(
                template.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).centerY,
                canvas.texts.first { it.text == "Overall Standings - 03 Sep 2026" }.centerY,
                1f,
            )
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun fewerThanTwelveRowsOnlyRenderSuppliedRows() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, tournamentModel(rows = rows(3)), template),
            )
            assertEquals(3 + 3 * 5, canvas.texts.size)
            assertFalse(canvas.texts.any { it.text == "Team 4" })
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun invalidRowCountFailsBeforeDrawing() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Failure(
                    FreeDesignCanvasRenderFailure.INVALID_ROW_COUNT,
                ),
                renderer.render(canvas, tournamentModel(rows = rows(13)), template),
            )
            assertTrue(canvas.texts.isEmpty())
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun delegatedRowRendererFailureIsPropagated() {
        val failingRenderer = FreeDesignCanvasRenderer(
            FreeDesignResultRowsRenderer { _, _, _, _, _ ->
                CustomDesignCanvasRenderResult.Failure(
                    CustomDesignCanvasRenderFailure.INVALID_ROW_COUNT,
                )
            },
        )
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Failure(
                    reason = FreeDesignCanvasRenderFailure.RESULT_ROWS_RENDER_FAILED,
                    delegatedFailure = CustomDesignCanvasRenderFailure.INVALID_ROW_COUNT,
                ),
                failingRenderer.render(canvas, tournamentModel(), template),
            )
            assertTrue(canvas.texts.isEmpty())
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun longHeaderTextFitsConfiguredWidthByShrinkingOrEllipsizing() {
        val canvas = RecordingCanvas()
        val longName = "A tournament name that is deliberately much longer than the safe header region"
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(
                    canvas,
                    tournamentModel(tournamentName = longName),
                    template,
                ),
            )
            val title = canvas.texts.first { it.text == longName || it.text.endsWith("…") }
            val style = template.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).style
            assertTrue(title.width <= style.maxWidthPx + 1f)
            assertTrue(title.text != longName || title.textSize < style.textSize)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun configuredHeaderAlignmentIsRespected() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, tournamentModel(), template),
            )
            FreeDesignHeaderField.entries.forEach { field ->
                val text = when (field) {
                    FreeDesignHeaderField.TOURNAMENT_NAME -> "Champions Cup 2026"
                    FreeDesignHeaderField.ORGANIZER_NAME -> "HOG Gamers"
                    FreeDesignHeaderField.RESULT_HEADING -> "Overall Standings - 03 Sep 2026"
                    FreeDesignHeaderField.DATE -> "Overall Standings - 03 Sep 2026"
                }
                val drawn = canvas.texts.first { it.text == text }
                assertEquals(
                    template.headerAnchors.getValue(
                        if (field == FreeDesignHeaderField.DATE) {
                            FreeDesignHeaderField.RESULT_HEADING
                        } else {
                            field
                        },
                    ).centerX,
                    drawn.centerX,
                    1f,
                )
            }
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun freeDesignResultValuesUseSlightlyLargerTextThanLegacyDefault() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, tournamentModel(), template),
            )
            val teamText = canvas.texts.first { it.text == "Team 1" }
            assertEquals(24f * 1.1f, teamText.textSize, 0.01f)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun perTemplateResultStyleGeometryAndColorsReachRowsRenderer() {
        var receivedGeometry: CustomDesignEffectiveGridGeometry? = null
        var receivedTextColors: CustomDesignColumnTextColors? = null
        var receivedResultTextStyle: FreeDesignResultTextStyle? = null
        val recordingRenderer = FreeDesignCanvasRenderer(
            FreeDesignResultRowsRenderer { _, _, geometry, textColors, resultTextStyle ->
                receivedGeometry = geometry
                receivedTextColors = textColors
                receivedResultTextStyle = resultTextStyle
                CustomDesignCanvasRenderResult.Success
            },
        )
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                recordingRenderer.render(canvas, tournamentModel(), template),
            )
            assertEquals(template.tableGeometry, receivedGeometry)
            assertEquals(template.resultColumnTextColors, receivedTextColors)
            assertEquals(template.resultTextStyle, receivedResultTextStyle)

            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                recordingRenderer.render(canvas, tournamentModel(), template2),
            )
            assertEquals(template2.tableGeometry, receivedGeometry)
            assertEquals(template2.resultColumnTextColors, receivedTextColors)
            assertEquals(template2.resultTextStyle, receivedResultTextStyle)
            assertEquals(1.30f, receivedResultTextStyle?.textSizeMultiplier)
            assertEquals(16f, receivedResultTextStyle?.teamNameStartPaddingPx)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun template2HeaderUsesConfiguredAnchorsForOverallAndMatch() {
        val overallCanvas = RecordingCanvas()
        val matchCanvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(overallCanvas, tournamentModel(), template2),
            )
            assertEquals(
                template2.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).centerY,
                overallCanvas.texts.first { it.text == "Champions Cup 2026" }.centerY,
                1f,
            )
            assertEquals(
                template2.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).centerY,
                overallCanvas.texts.first { it.text == "HOG Gamers" }.centerY,
                1f,
            )
            assertEquals(
                template2.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).centerY,
                overallCanvas.texts.first { it.text == "Overall Standings - 03 Sep 2026" }.centerY,
                1f,
            )

            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(matchCanvas, matchModel(), template2),
            )
            assertEquals(
                template2.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).centerY,
                matchCanvas.texts.first { it.text == "Match 4 - 03 Sep 2026" }.centerY,
                1f,
            )
        } finally {
            overallCanvas.recycle()
            matchCanvas.recycle()
        }
    }

    @Test
    fun template2ResultValuesUseTemplateSpecificTextStyleAndGeometry() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(
                FreeDesignCanvasRenderResult.Success,
                renderer.render(canvas, tournamentModel(), template2),
            )
            val teamText = canvas.texts.first { it.text == "Team 1" }
            assertEquals(24f * 1.30f, teamText.textSize, 0.01f)
            assertEquals(
                template2.tableGeometry.columnX.getValue(CustomDesignAnchorField.TEAM_NAME) + 16f,
                teamText.startX,
                0.01f,
            )
            assertEquals(375f, teamText.centerY, 1f)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun invalidResultTextStyleFailsBeforeRowDrawing() {
        val invalidStyles = listOf(
            FreeDesignResultTextStyle(Float.NaN, 16f),
            FreeDesignResultTextStyle(0f, 16f),
            FreeDesignResultTextStyle(1.1f, -1f),
            FreeDesignResultTextStyle(1.1f, template.sourceWidth.toFloat()),
        )

        invalidStyles.forEach { invalidStyle ->
            var rowsRendered = false
            val renderer = FreeDesignCanvasRenderer(
                FreeDesignResultRowsRenderer { _, _, _, _, _ ->
                    rowsRendered = true
                    CustomDesignCanvasRenderResult.Success
                },
            )
            val canvas = RecordingCanvas()
            try {
                assertEquals(
                    FreeDesignCanvasRenderResult.Failure(
                        FreeDesignCanvasRenderFailure.INVALID_RESULT_TEXT_STYLE,
                    ),
                    renderer.render(
                        canvas,
                        tournamentModel(),
                        template.copy(resultTextStyle = invalidStyle),
                    ),
                )
                assertFalse(rowsRendered)
                assertTrue(canvas.texts.isEmpty())
            } finally {
                canvas.recycle()
            }
        }
    }

    private fun matchModel() = MatchResultExportModel(
        tournamentName = "Champions Cup 2026",
        organizerName = "HOG Gamers",
        tournamentDate = LocalDate.of(2026, 9, 3),
        matchNumber = 4,
        matchDate = LocalDate.of(2026, 8, 31),
        mapName = "Bermuda",
        rows = rows(1),
    )

    private fun tournamentModel(
        tournamentName: String = "Champions Cup 2026",
        organizerName: String = "HOG Gamers",
        rows: List<ResultExportRow> = rows(1),
    ) = TournamentResultExportModel(
        tournamentName = tournamentName,
        organizerName = organizerName,
        tournamentDate = LocalDate.of(2026, 9, 3),
        finalizedMatchCount = 2,
        rows = rows,
    )

    private fun rows(count: Int) = (1..count).map { rank ->
        ResultExportRow(
            rank = rank,
            teamName = "Team $rank",
            win = if (rank == 1) 1 else 0,
            totalKills = rank,
            positionPoints = 13 - rank,
            totalPoints = rank * 2,
        )
    }

    private class RecordingCanvas(
        private val bitmap: Bitmap = Bitmap.createBitmap(1254, 1254, Bitmap.Config.ARGB_8888),
    ) : Canvas(bitmap) {
        val texts = mutableListOf<DrawnText>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            val width = paint.measureText(text)
            texts += DrawnText(
                text = text,
                startX = x,
                centerX = x + width / 2f,
                centerY = y + (paint.ascent() + paint.descent()) / 2f,
                width = width,
                textSize = paint.textSize,
            )
            super.drawText(text, x, y, paint)
        }

        fun recycle() {
            bitmap.recycle()
        }
    }

    private data class DrawnText(
        val text: String,
        val startX: Float,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val textSize: Float,
    )
}
