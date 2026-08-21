package com.hoggamers.rankforge.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.time.format.DateTimeFormatter

enum class ResultRenderFailure {
    INVALID_ROW_COUNT,
    RENDERING_FAILED,
    PNG_COMPRESSION_FAILED,
}

sealed interface ResultCanvasRenderResult {
    data object Success : ResultCanvasRenderResult

    data class Failure(
        val reason: ResultRenderFailure,
    ) : ResultCanvasRenderResult
}

class ResultCanvasRenderer {
    fun render(
        canvas: Canvas,
        model: MatchResultExportModel,
    ): ResultCanvasRenderResult = render(
        canvas = canvas,
        tournamentName = model.tournamentName,
        subtitle = "Match ${model.matchNumber} • ${model.mapName} • " +
            model.matchDate.format(DATE_FORMATTER),
        rows = model.rows,
    )

    fun render(
        canvas: Canvas,
        model: TournamentResultExportModel,
    ): ResultCanvasRenderResult = render(
        canvas = canvas,
        tournamentName = model.tournamentName,
        subtitle = "Tournament Standings • ${model.finalizedMatchCount} Finalized Matches",
        rows = model.rows,
    )

    private fun render(
        canvas: Canvas,
        tournamentName: String,
        subtitle: String,
        rows: List<ResultExportRow>,
    ): ResultCanvasRenderResult {
        if (rows.size != ResultLayoutSpec.RESULT_ROW_COUNT) {
            return ResultCanvasRenderResult.Failure(ResultRenderFailure.INVALID_ROW_COUNT)
        }

        return try {
            canvas.drawColor(Color.WHITE)
            drawHeader(canvas, tournamentName, subtitle)
            drawTable(canvas, rows)
            ResultCanvasRenderResult.Success
        } catch (_: RuntimeException) {
            ResultCanvasRenderResult.Failure(ResultRenderFailure.RENDERING_FAILED)
        }
    }

    private fun drawHeader(
        canvas: Canvas,
        tournamentName: String,
        subtitle: String,
    ) {
        canvas.drawText(
            "RANK FORGE",
            ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN,
            ResultLayoutSpec.TITLE_BASELINE,
            titlePaint,
        )
        canvas.drawText(
            tournamentName,
            ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN,
            ResultLayoutSpec.TOURNAMENT_BASELINE,
            tournamentPaint,
        )
        canvas.drawText(
            subtitle,
            ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN,
            ResultLayoutSpec.SUBTITLE_BASELINE,
            subtitlePaint,
        )
    }

    private fun drawTable(
        canvas: Canvas,
        rows: List<ResultExportRow>,
    ) {
        val left = ResultLayoutSpec.OUTER_HORIZONTAL_MARGIN
        val right = left + ResultLayoutSpec.TABLE_WIDTH
        val top = ResultLayoutSpec.TABLE_TOP
        val bottom = ResultLayoutSpec.TABLE_BOTTOM
        val boundaries = ResultLayoutSpec.COLUMN_BOUNDARIES

        canvas.drawRect(
            left,
            top,
            right,
            top + ResultLayoutSpec.TABLE_HEADER_HEIGHT,
            headerFillPaint,
        )
        canvas.drawRect(
            boundaries[5],
            top + ResultLayoutSpec.TABLE_HEADER_HEIGHT,
            right,
            bottom,
            totalPointsFillPaint,
        )

        val headers = listOf(
            "Rank",
            "Team Name",
            "Win",
            "Total Kills",
            "Position Pts",
            "Total Points",
        )
        headers.forEachIndexed { index, header ->
            drawCenteredText(
                canvas = canvas,
                text = header,
                centerX = (boundaries[index] + boundaries[index + 1]) / 2f,
                centerY = top + ResultLayoutSpec.TABLE_HEADER_HEIGHT / 2f,
                paint = headerPaint,
            )
        }

        rows.forEachIndexed { rowIndex, row ->
            val centerY = top + ResultLayoutSpec.TABLE_HEADER_HEIGHT +
                rowIndex * ResultLayoutSpec.RESULT_ROW_HEIGHT +
                ResultLayoutSpec.RESULT_ROW_HEIGHT / 2f
            drawCenteredText(canvas, row.rank?.toString().orEmpty(), centerX(boundaries, 0), centerY, bodyPaint)
            drawTeamName(canvas, row.teamName, boundaries[1], centerY)
            drawCenteredText(canvas, row.win.toString(), centerX(boundaries, 2), centerY, bodyPaint)
            drawCenteredText(canvas, row.totalKills.toString(), centerX(boundaries, 3), centerY, bodyPaint)
            drawCenteredText(canvas, row.positionPoints.toString(), centerX(boundaries, 4), centerY, bodyPaint)
            drawCenteredText(
                canvas,
                row.totalPoints.toString(),
                centerX(boundaries, 5),
                centerY,
                totalPointsPaint,
            )
        }

        canvas.drawRect(left, top, right, bottom, outerBorderPaint)
        boundaries.drop(1).dropLast(1).forEach { boundary ->
            canvas.drawLine(boundary, top, boundary, bottom, gridPaint)
        }
        canvas.drawLine(left, top + ResultLayoutSpec.TABLE_HEADER_HEIGHT, right, top + ResultLayoutSpec.TABLE_HEADER_HEIGHT, gridPaint)
        repeat(ResultLayoutSpec.RESULT_ROW_COUNT - 1) { rowIndex ->
            val y = top + ResultLayoutSpec.TABLE_HEADER_HEIGHT +
                (rowIndex + 1) * ResultLayoutSpec.RESULT_ROW_HEIGHT
            canvas.drawLine(left, y, right, y, gridPaint)
        }
    }

    private fun drawTeamName(
        canvas: Canvas,
        teamName: String,
        left: Float,
        centerY: Float,
    ) {
        val right = left + ResultLayoutSpec.COLUMN_WIDTHS[1]
        val fitted = fittedTeamName(teamName, right - left - TEAM_NAME_HORIZONTAL_PADDING * 2f)
        canvas.drawText(
            fitted.text,
            left + TEAM_NAME_HORIZONTAL_PADDING,
            centeredBaseline(centerY, teamNamePaintFor(fitted.textSize)),
            teamNamePaint,
        )
    }

    private fun fittedTeamName(
        text: String,
        maxWidth: Float,
    ): FittedTeamName {
        var textSize = TEAM_NAME_TEXT_SIZE
        teamNamePaint.textSize = textSize
        while (textSize > MIN_TEAM_NAME_TEXT_SIZE && teamNamePaint.measureText(text) > maxWidth) {
            textSize = maxOf(
                MIN_TEAM_NAME_TEXT_SIZE,
                textSize - TEAM_NAME_TEXT_SIZE_STEP,
            )
            teamNamePaint.textSize = textSize
        }
        if (teamNamePaint.measureText(text) <= maxWidth) {
            return FittedTeamName(text, textSize)
        }

        val ellipsis = "…"
        var end = text.length
        while (end > 0 && teamNamePaint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        val fittedText = if (end == 0) ellipsis else text.substring(0, end) + ellipsis
        return FittedTeamName(fittedText, textSize)
    }

    private fun teamNamePaintFor(textSize: Float): Paint {
        teamNamePaint.textSize = textSize
        return teamNamePaint
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        paint: Paint,
    ) {
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, centeredBaseline(centerY, paint), paint)
    }

    private fun centeredBaseline(
        centerY: Float,
        paint: Paint,
    ): Float = centerY - (paint.ascent() + paint.descent()) / 2f

    private fun centerX(
        boundaries: List<Float>,
        columnIndex: Int,
    ): Float = (boundaries[columnIndex] + boundaries[columnIndex + 1]) / 2f

    private fun centeredBaseline(
        centerY: Float,
        textSize: Float,
    ): Float {
        teamNamePaint.textSize = textSize
        return centeredBaseline(centerY, teamNamePaint)
    }

    private data class FittedTeamName(
        val text: String,
        val textSize: Float,
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        const val TEAM_NAME_TEXT_SIZE = 14f
        const val MIN_TEAM_NAME_TEXT_SIZE = 9f
        const val TEAM_NAME_TEXT_SIZE_STEP = 0.5f
        const val TEAM_NAME_HORIZONTAL_PADDING = 8f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 35, 48)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tournamentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 35, 48)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(82, 92, 105)
            textSize = 14f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 35, 48)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 43, 52)
            textSize = 12f
        }
        val teamNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 43, 52)
            textSize = TEAM_NAME_TEXT_SIZE
        }
        val totalPointsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 55, 90)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerFillPaint = Paint().apply {
            color = Color.rgb(232, 237, 243)
            style = Paint.Style.FILL
        }
        val totalPointsFillPaint = Paint().apply {
            color = Color.rgb(244, 248, 252)
            style = Paint.Style.FILL
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 199, 209)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(95, 107, 120)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
    }
}
