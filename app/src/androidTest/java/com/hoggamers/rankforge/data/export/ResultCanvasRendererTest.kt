package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultCanvasRendererTest {
    private val renderer = ResultCanvasRenderer()

    @Test
    fun currentMatchUsesTournamentMetadataAndRequiredSubtitle() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(ResultCanvasRenderResult.Success, renderer.render(canvas, matchModel()))

            assertEquals("Champions Cup 2026", canvas.texts[0].text)
            assertEquals("HOG Gamers", canvas.texts[1].text)
            assertEquals("Current match - Match 4 -- 03 Sep 2026", canvas.texts[2].text)
            assertEquals(listOf("Point", "IQ"), canvas.texts.takeLast(2).map { it.text })
            assertFalse(canvas.texts.any { it.text == "RANK FORGE" })
            assertFalse(canvas.texts.any { it.text.contains("Bermuda") })
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun wholeTournamentUsesRequiredSubtitleWithoutMatchMetadata() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(ResultCanvasRenderResult.Success, renderer.render(canvas, tournamentModel()))

            assertEquals("Champions Cup 2026", canvas.texts[0].text)
            assertEquals("HOG Gamers", canvas.texts[1].text)
            assertEquals("Overall standings -- 03 Sep 2026", canvas.texts[2].text)
            assertEquals(listOf("Point", "IQ"), canvas.texts.takeLast(2).map { it.text })
            assertFalse(canvas.texts.any { it.text.contains("Finalized Matches") })
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun blankOrganizerRemovesTheOrganizerLineForBothScopes() {
        listOf(
            renderer to { canvas: RecordingCanvas -> renderer.render(canvas, matchModel(organizerName = "  ")) },
            renderer to { canvas: RecordingCanvas -> renderer.render(canvas, tournamentModel(organizerName = "  ")) },
        ).forEach { (_, render) ->
            val canvas = RecordingCanvas()
            try {
                assertEquals(ResultCanvasRenderResult.Success, render(canvas))
                assertFalse(canvas.texts.any { it.text == "HOG Gamers" })
                assertFalse(canvas.texts.any { it.text.isBlank() })
                assertEquals(listOf("Point", "IQ"), canvas.texts.takeLast(2).map { it.text })
            } finally {
                canvas.recycle()
            }
        }
    }

    @Test
    fun longHeaderMetadataStaysInsidePageMargins() {
        val canvas = RecordingCanvas()
        try {
            renderer.render(
                canvas,
                matchModel(
                    tournamentName = "A very long tournament name that must remain inside the export page bounds",
                    organizerName = "A very long organizer name that must remain inside the export page bounds",
                ),
            )

            canvas.texts.take(2).forEach { text ->
                assertTrue(text.left >= ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN)
                assertTrue(
                    text.left + text.width <=
                        ResultLayoutSpec.LOGICAL_PAGE_WIDTH - ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN,
                )
            }
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun headerTypographyHasClearHierarchy() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(ResultCanvasRenderResult.Success, renderer.render(canvas, matchModel()))

            assertTrue(canvas.texts[0].textSize > canvas.texts[1].textSize)
            assertTrue(canvas.texts[1].textSize > canvas.texts[2].textSize)
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun scopeLineLeavesSmallerPositiveGapAboveTable() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(ResultCanvasRenderResult.Success, renderer.render(canvas, matchModel()))

            assertTrue(ResultLayoutSpec.TABLE_TOP > ResultLayoutSpec.SUBTITLE_BASELINE)
            assertTrue(ResultLayoutSpec.TABLE_TOP > ResultLayoutSpec.SUBTITLE_WITHOUT_ORGANIZER_BASELINE)
            assertTrue(
                ResultLayoutSpec.TABLE_TOP - ResultLayoutSpec.SUBTITLE_BASELINE <
                    36f,
            )
            assertTrue(
                ResultLayoutSpec.TABLE_TOP - ResultLayoutSpec.SUBTITLE_WITHOUT_ORGANIZER_BASELINE <
                    36f,
            )
        } finally {
            canvas.recycle()
        }
    }

    @Test
    fun footerUsesContinuousMeasuredTwoColorRightAlignedMark() {
        val canvas = RecordingCanvas()
        try {
            assertEquals(ResultCanvasRenderResult.Success, renderer.render(canvas, matchModel()))

            val footer = canvas.texts.takeLast(2)
            assertEquals(listOf("Point", "IQ"), footer.map { it.text })
            assertEquals(Color.BLACK, footer[0].color)
            assertEquals(Color.rgb(23, 106, 247), footer[1].color)
            assertEquals(footer[0].baseline, footer[1].baseline, 0f)
            assertEquals(footer[0].left + footer[0].width, footer[1].left, 0f)
            assertEquals(
                ResultLayoutSpec.LOGICAL_PAGE_WIDTH - ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN,
                footer[1].left + footer[1].width,
                0f,
            )
        } finally {
            canvas.recycle()
        }
    }

    private fun matchModel(
        tournamentName: String = "Champions Cup 2026",
        organizerName: String = "HOG Gamers",
    ) = MatchResultExportModel(
        tournamentName = tournamentName,
        organizerName = organizerName,
        tournamentDate = LocalDate.of(2026, 9, 3),
        matchNumber = 4,
        matchDate = LocalDate.of(2026, 8, 31),
        mapName = "Bermuda",
        rows = rows(),
    )

    private fun tournamentModel(
        organizerName: String = "HOG Gamers",
    ) = TournamentResultExportModel(
        tournamentName = "Champions Cup 2026",
        organizerName = organizerName,
        tournamentDate = LocalDate.of(2026, 9, 3),
        finalizedMatchCount = 2,
        rows = rows(),
    )

    private fun rows(): List<ResultExportRow> = (1..12).map { rank ->
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
        private val bitmap: Bitmap = Bitmap.createBitmap(
            ResultLayoutSpec.PNG_WIDTH,
            ResultLayoutSpec.PNG_HEIGHT,
            Bitmap.Config.ARGB_8888,
        ),
    ) : Canvas(bitmap) {
        val texts = mutableListOf<DrawnText>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            texts += DrawnText(
                text = text,
                left = x,
                width = paint.measureText(text),
                color = paint.color,
                baseline = y,
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
        val left: Float,
        val width: Float,
        val color: Int,
        val baseline: Float,
        val textSize: Float,
    )
}
