package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.io.ByteArrayOutputStream
import java.time.LocalDate

sealed interface ResultPngRenderResult {
    data class Success(
        val pngBytes: ByteArray,
    ) : ResultPngRenderResult

    data class Failure(
        val reason: ResultRenderFailure,
    ) : ResultPngRenderResult
}

class ResultPngRenderer(
    private val canvasRenderer: ResultCanvasRenderer = ResultCanvasRenderer(),
    private val logoCanvasRenderer: PointTableLogoCanvasRenderer = PointTableLogoCanvasRenderer(),
) {
    fun render(model: MatchResultExportModel): ResultPngRenderResult =
        render(model, null)

    fun render(
        model: MatchResultExportModel,
        displayDate: LocalDate?,
    ): ResultPngRenderResult = renderBitmap(
        layout = ResultLayoutSpec.legacyLayoutForRowCount(model.rows.size),
        draw = { canvas -> canvasRenderer.render(canvas, model, displayDate) },
    )

    fun render(
        model: MatchResultExportModel,
        displayDate: LocalDate?,
        logoRenderData: PointTableLogoRenderData?,
    ): ResultPngRenderResult = renderBitmap(
        layout = ResultLayoutSpec.legacyLayoutForRowCount(model.rows.size),
        draw = { canvas -> canvasRenderer.render(canvas, model, displayDate) },
        logoRenderData = logoRenderData,
    )

    fun render(model: TournamentResultExportModel): ResultPngRenderResult =
        render(model, null)

    fun render(
        model: TournamentResultExportModel,
        displayDate: LocalDate?,
    ): ResultPngRenderResult = renderBitmap(
        layout = ResultLayoutSpec.overallImageLayoutForRowCount(model.rows.size),
        draw = { canvas -> canvasRenderer.renderForOverallImage(canvas, model, displayDate) },
    )

    fun render(
        model: TournamentResultExportModel,
        displayDate: LocalDate?,
        logoRenderData: PointTableLogoRenderData?,
    ): ResultPngRenderResult = renderBitmap(
        layout = ResultLayoutSpec.overallImageLayoutForRowCount(model.rows.size),
        draw = { canvas -> canvasRenderer.renderForOverallImage(canvas, model, displayDate) },
        logoRenderData = logoRenderData,
    )

    private fun renderBitmap(
        layout: ResultRenderLayout?,
        draw: (Canvas) -> ResultCanvasRenderResult,
        logoRenderData: PointTableLogoRenderData? = null,
    ): ResultPngRenderResult {
        if (layout == null) {
            return ResultPngRenderResult.Failure(ResultRenderFailure.INVALID_ROW_COUNT)
        }
        val bitmap = try {
            Bitmap.createBitmap(
                ResultLayoutSpec.PNG_WIDTH,
                layout.pngHeight,
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: RuntimeException) {
            return ResultPngRenderResult.Failure(ResultRenderFailure.RENDERING_FAILED)
        }

        return try {
            val canvas = Canvas(bitmap)
            canvas.scale(ResultLayoutSpec.PNG_SCALE, ResultLayoutSpec.PNG_SCALE)
            when (val renderResult = draw(canvas)) {
                ResultCanvasRenderResult.Success -> {
                    // The base renderer leaves the canvas scaled to logical page units. Draw the
                    // logo on a fresh canvas so normalized placement uses final PNG pixels.
                    logoCanvasRenderer.draw(
                        canvas = Canvas(bitmap),
                        targetWidth = ResultLayoutSpec.PNG_WIDTH,
                        targetHeight = layout.pngHeight,
                        renderData = logoRenderData,
                    )
                    encode(bitmap)
                }
                is ResultCanvasRenderResult.Failure ->
                    ResultPngRenderResult.Failure(renderResult.reason)
            }
        } catch (_: RuntimeException) {
            ResultPngRenderResult.Failure(ResultRenderFailure.RENDERING_FAILED)
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap): ResultPngRenderResult {
        val output = ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            ResultPngRenderResult.Success(output.toByteArray())
        } else {
            ResultPngRenderResult.Failure(ResultRenderFailure.PNG_COMPRESSION_FAILED)
        }
    }
}
