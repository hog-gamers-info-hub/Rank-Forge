package com.hoggamers.rankforge.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry

enum class CustomDesignCanvasRenderFailure {
    INVALID_SOURCE_DIMENSIONS,
    INVALID_ROW_COUNT,
    MISSING_COLUMN,
    MISSING_ROW,
    INVALID_COORDINATE,
    RENDERING_FAILED,
}

sealed interface CustomDesignCanvasRenderResult {
    data object Success : CustomDesignCanvasRenderResult

    data class Failure(
        val reason: CustomDesignCanvasRenderFailure,
    ) : CustomDesignCanvasRenderResult
}

class CustomDesignCanvasRenderer {
    fun render(
        canvas: Canvas,
        rows: List<ResultExportRow>,
        geometry: CustomDesignEffectiveGridGeometry,
        textColors: CustomDesignColumnTextColors = CustomDesignColumnTextColors.allBlack(),
        averageRankingBoundingBoxHeightPx: Float? = null,
    ): CustomDesignCanvasRenderResult {
        validate(geometry, rows)?.let { return CustomDesignCanvasRenderResult.Failure(it) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = resolveResultTextSizePx(averageRankingBoundingBoxHeightPx)
            typeface = mediumTypeface()
        }

        return try {
            rows.forEachIndexed { rowIndex, row ->
                val sourceY = geometry.rowY.getValue(rowIndex + 1)
                drawLeftAlignedText(
                    canvas = canvas,
                    text = row.teamName,
                    startX = geometry.columnX.getValue(CustomDesignAnchorField.TEAM_NAME),
                    centerY = sourceY,
                    color = textColors.colorFor(CustomDesignAnchorField.TEAM_NAME),
                    paint = paint,
                )
                drawCenteredText(
                    canvas = canvas,
                    text = row.win.toString(),
                    centerX = geometry.columnX.getValue(CustomDesignAnchorField.WIN),
                    centerY = sourceY,
                    color = textColors.colorFor(CustomDesignAnchorField.WIN),
                    paint = paint,
                )
                drawCenteredText(
                    canvas = canvas,
                    text = row.totalKills.toString(),
                    centerX = geometry.columnX.getValue(CustomDesignAnchorField.TOTAL_KILLS),
                    centerY = sourceY,
                    color = textColors.colorFor(CustomDesignAnchorField.TOTAL_KILLS),
                    paint = paint,
                )
                drawCenteredText(
                    canvas = canvas,
                    text = row.positionPoints.toString(),
                    centerX = geometry.columnX.getValue(CustomDesignAnchorField.POSITION_POINTS),
                    centerY = sourceY,
                    color = textColors.colorFor(CustomDesignAnchorField.POSITION_POINTS),
                    paint = paint,
                )
                drawCenteredText(
                    canvas = canvas,
                    text = row.totalPoints.toString(),
                    centerX = geometry.columnX.getValue(CustomDesignAnchorField.TOTAL_POINTS),
                    centerY = sourceY,
                    color = textColors.colorFor(CustomDesignAnchorField.TOTAL_POINTS),
                    paint = paint,
                )
            }
            CustomDesignCanvasRenderResult.Success
        } catch (_: RuntimeException) {
            CustomDesignCanvasRenderResult.Failure(CustomDesignCanvasRenderFailure.RENDERING_FAILED)
        }
    }

    private fun validate(
        geometry: CustomDesignEffectiveGridGeometry,
        rows: List<ResultExportRow>,
    ): CustomDesignCanvasRenderFailure? {
        if (geometry.sourceWidth <= 0 || geometry.sourceHeight <= 0) {
            return CustomDesignCanvasRenderFailure.INVALID_SOURCE_DIMENSIONS
        }
        if (rows.isEmpty() || rows.size > CUSTOM_DESIGN_ROW_COUNT) {
            return CustomDesignCanvasRenderFailure.INVALID_ROW_COUNT
        }
        if (CustomDesignAnchorField.entries.any { it !in geometry.columnX }) {
            return CustomDesignCanvasRenderFailure.MISSING_COLUMN
        }
        if ((1..CUSTOM_DESIGN_ROW_COUNT).any { it !in geometry.rowY }) {
            return CustomDesignCanvasRenderFailure.MISSING_ROW
        }
        if (CustomDesignAnchorField.entries.any { field ->
                val x = geometry.columnX.getValue(field)
                !x.isFinite() || x !in 0f..geometry.sourceWidth.toFloat()
            } || (1..CUSTOM_DESIGN_ROW_COUNT).any { rank ->
                val y = geometry.rowY.getValue(rank)
                !y.isFinite() || y !in 0f..geometry.sourceHeight.toFloat()
            }
        ) {
            return CustomDesignCanvasRenderFailure.INVALID_COORDINATE
        }
        return null
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        color: String,
        paint: Paint,
    ) {
        paint.color = Color.parseColor(color)
        val textWidth = paint.measureText(text)
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, centerX - textWidth / 2f, baseline, paint)
    }

    private fun drawLeftAlignedText(
        canvas: Canvas,
        text: String,
        startX: Float,
        centerY: Float,
        color: String,
        paint: Paint,
    ) {
        paint.color = Color.parseColor(color)
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, startX, baseline, paint)
    }

    private companion object {
        const val CUSTOM_DESIGN_ROW_COUNT = 12
        const val LEGACY_RESULT_TEXT_SIZE_PX = 24f
        const val RESULT_TEXT_SIZE_MULTIPLIER = 1.3f
        const val RESULT_TEXT_WEIGHT = 500

        fun resolveResultTextSizePx(averageRankingBoundingBoxHeightPx: Float?): Float {
            val scaled = averageRankingBoundingBoxHeightPx
                ?.takeIf { it.isFinite() && it > 0f }
                ?.times(RESULT_TEXT_SIZE_MULTIPLIER)
            return scaled?.takeIf { it.isFinite() && it > 0f }
                ?: LEGACY_RESULT_TEXT_SIZE_PX
        }

        fun mediumTypeface(): Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, RESULT_TEXT_WEIGHT, false)
        } else {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
}
