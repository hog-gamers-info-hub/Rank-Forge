package com.hoggamers.rankforge.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FreeDesignCanvasRenderFailure {
    INVALID_TEMPLATE_DIMENSIONS,
    INVALID_TEMPLATE_GEOMETRY,
    INVALID_HEADER_ANCHOR,
    INVALID_HEADER_STYLE,
    INVALID_ROW_COUNT,
    RESULT_ROWS_RENDER_FAILED,
    RENDERING_FAILED,
}

sealed interface FreeDesignCanvasRenderResult {
    data object Success : FreeDesignCanvasRenderResult

    data class Failure(
        val reason: FreeDesignCanvasRenderFailure,
        val delegatedFailure: CustomDesignCanvasRenderFailure? = null,
    ) : FreeDesignCanvasRenderResult
}

fun interface FreeDesignResultRowsRenderer {
    fun render(
        canvas: Canvas,
        rows: List<ResultExportRow>,
        geometry: CustomDesignEffectiveGridGeometry,
        textColors: CustomDesignColumnTextColors,
    ): CustomDesignCanvasRenderResult
}

class FreeDesignCanvasRenderer(
    private val resultRowsRenderer: FreeDesignResultRowsRenderer =
        FreeDesignResultRowsRenderer { canvas, rows, geometry, textColors ->
            CustomDesignCanvasRenderer().render(
                canvas = canvas,
                rows = rows,
                geometry = geometry,
                textColors = textColors,
                resultTextSizeMultiplier = FREE_DESIGN_RESULT_TEXT_SIZE_MULTIPLIER,
                teamNameStartPaddingPx = FREE_DESIGN_TEAM_NAME_START_PADDING_PX,
            )
        },
) {
    fun render(
        canvas: Canvas,
        model: MatchResultExportModel,
        template: FreeDesignTemplate,
    ): FreeDesignCanvasRenderResult = render(
        canvas = canvas,
        template = template,
        tournamentName = model.tournamentName,
        organizerName = model.organizerName,
        resultHeading = "Match ${model.matchNumber}",
        date = model.tournamentDate.format(DATE_FORMATTER),
        rows = model.rows,
    )

    fun render(
        canvas: Canvas,
        model: TournamentResultExportModel,
        template: FreeDesignTemplate,
    ): FreeDesignCanvasRenderResult = render(
        canvas = canvas,
        template = template,
        tournamentName = model.tournamentName,
        organizerName = model.organizerName,
        resultHeading = "Overall Standings",
        date = model.tournamentDate.format(DATE_FORMATTER),
        rows = model.rows,
    )

    private fun render(
        canvas: Canvas,
        template: FreeDesignTemplate,
        tournamentName: String,
        organizerName: String,
        resultHeading: String,
        date: String,
        rows: List<ResultExportRow>,
    ): FreeDesignCanvasRenderResult {
        validateTemplate(template)?.let { return FreeDesignCanvasRenderResult.Failure(it) }
        if (rows.isEmpty() || rows.size > FREE_DESIGN_ROW_COUNT) {
            return FreeDesignCanvasRenderResult.Failure(
                FreeDesignCanvasRenderFailure.INVALID_ROW_COUNT,
            )
        }

        return try {
            val rowRenderResult = resultRowsRenderer.render(
                canvas = canvas,
                rows = rows,
                geometry = template.tableGeometry,
                textColors = template.resultColumnTextColors,
            )
            when (rowRenderResult) {
                CustomDesignCanvasRenderResult.Success -> {
                    drawHeader(
                        canvas = canvas,
                        template = template,
                        tournamentName = tournamentName.trim(),
                        organizerName = organizerName.trim(),
                        resultHeading = resultHeading,
                        date = date,
                    )
                    FreeDesignCanvasRenderResult.Success
                }

                is CustomDesignCanvasRenderResult.Failure ->
                    FreeDesignCanvasRenderResult.Failure(
                        reason = FreeDesignCanvasRenderFailure.RESULT_ROWS_RENDER_FAILED,
                        delegatedFailure = rowRenderResult.reason,
                    )
            }
        } catch (_: RuntimeException) {
            FreeDesignCanvasRenderResult.Failure(FreeDesignCanvasRenderFailure.RENDERING_FAILED)
        }
    }

    private fun drawHeader(
        canvas: Canvas,
        template: FreeDesignTemplate,
        tournamentName: String,
        organizerName: String,
        resultHeading: String,
        date: String,
    ) {
        drawHeaderText(
            canvas,
            template.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME),
            tournamentName,
        )
        if (organizerName.isNotEmpty()) {
            drawHeaderText(
                canvas,
                template.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME),
                organizerName,
            )
        }
        drawHeaderText(
            canvas,
            template.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING),
            "$resultHeading - $date",
        )
    }

    private fun drawHeaderText(
        canvas: Canvas,
        anchor: FreeDesignHeaderAnchor,
        text: String,
    ) {
        val style = anchor.style
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(style.color)
            textSize = style.textSize
            typeface = typefaceFor(style.typographyRole)
        }
        val fittedText = fitText(text, paint, style.maxWidthPx, style.minimumTextSizePx)
        val startX = when (style.alignment) {
            FreeDesignTextAlignment.CENTER -> anchor.centerX - paint.measureText(fittedText) / 2f
            FreeDesignTextAlignment.START -> anchor.centerX
            FreeDesignTextAlignment.END -> anchor.centerX - paint.measureText(fittedText)
        }
        val baseline = anchor.centerY - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(fittedText, startX, baseline, paint)
    }

    private fun fitText(
        text: String,
        paint: Paint,
        maxWidthPx: Float,
        minimumTextSizePx: Float,
    ): String {
        var fittedTextSize = paint.textSize
        while (
            fittedTextSize > minimumTextSizePx &&
            paint.measureText(text) > maxWidthPx
        ) {
            fittedTextSize = maxOf(
                minimumTextSizePx,
                fittedTextSize - HEADER_TEXT_SIZE_STEP,
            )
            paint.textSize = fittedTextSize
        }
        if (paint.measureText(text) <= maxWidthPx) return text

        var end = text.length
        while (
            end > 0 &&
            paint.measureText(text.substring(0, end) + HEADER_ELLIPSIS) > maxWidthPx
        ) {
            end--
        }
        return if (end == 0) HEADER_ELLIPSIS else text.substring(0, end) + HEADER_ELLIPSIS
    }

    private fun validateTemplate(template: FreeDesignTemplate): FreeDesignCanvasRenderFailure? {
        if (
            template.sourceWidth <= 0 ||
            template.sourceHeight <= 0 ||
            template.tableGeometry.sourceWidth != template.sourceWidth ||
            template.tableGeometry.sourceHeight != template.sourceHeight
        ) {
            return FreeDesignCanvasRenderFailure.INVALID_TEMPLATE_DIMENSIONS
        }

        if (
            template.tableGeometry.columnX.keys != CustomDesignAnchorField.entries.toSet() ||
            template.tableGeometry.rowY.keys != (1..FREE_DESIGN_ROW_COUNT).toSet()
        ) {
            return FreeDesignCanvasRenderFailure.INVALID_TEMPLATE_GEOMETRY
        }

        val width = template.sourceWidth.toFloat()
        val height = template.sourceHeight.toFloat()
        if (
            template.tableGeometry.columnX.values.any { it !in 0f..width || !it.isFinite() } ||
            template.tableGeometry.rowY.values.any { it !in 0f..height || !it.isFinite() }
        ) {
            return FreeDesignCanvasRenderFailure.INVALID_TEMPLATE_GEOMETRY
        }

        if (template.headerAnchors.keys != FreeDesignHeaderField.entries.toSet()) {
            return FreeDesignCanvasRenderFailure.INVALID_HEADER_ANCHOR
        }
        if (template.headerAnchors.values.any { anchor ->
                val style = anchor.style
                anchor.centerX !in 0f..width ||
                    anchor.centerY !in 0f..height ||
                    !anchor.centerX.isFinite() ||
                    !anchor.centerY.isFinite() ||
                    !style.textSize.isFinite() ||
                    style.textSize <= 0f ||
                    !style.maxWidthPx.isFinite() ||
                    style.maxWidthPx <= 0f ||
                    !style.minimumTextSizePx.isFinite() ||
                    style.minimumTextSizePx <= 0f ||
                    style.minimumTextSizePx > style.textSize ||
                    runCatching { Color.parseColor(style.color) }.isFailure
            }
        ) {
            return FreeDesignCanvasRenderFailure.INVALID_HEADER_STYLE
        }
        return null
    }

    private fun typefaceFor(role: FreeDesignTypographyRole): Typeface = when (role) {
        FreeDesignTypographyRole.TITLE -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        FreeDesignTypographyRole.SECONDARY -> Typeface.create("sans-serif", Typeface.NORMAL)
        FreeDesignTypographyRole.RESULT_HEADING -> Typeface.create("sans-serif", Typeface.BOLD)
        FreeDesignTypographyRole.DATE -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private companion object {
        const val FREE_DESIGN_ROW_COUNT = 12
        const val FREE_DESIGN_RESULT_TEXT_SIZE_MULTIPLIER = 1.1f
        const val FREE_DESIGN_TEAM_NAME_START_PADDING_PX = 16f
        const val HEADER_TEXT_SIZE_STEP = 0.5f
        const val HEADER_ELLIPSIS = "…"
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    }
}
