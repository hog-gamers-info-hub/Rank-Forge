package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.io.ByteArrayOutputStream

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
) {
    fun render(model: MatchResultExportModel): ResultPngRenderResult =
        renderBitmap { canvas -> canvasRenderer.render(canvas, model) }

    fun render(model: TournamentResultExportModel): ResultPngRenderResult =
        renderBitmap { canvas -> canvasRenderer.render(canvas, model) }

    private fun renderBitmap(
        draw: (Canvas) -> ResultCanvasRenderResult,
    ): ResultPngRenderResult {
        val bitmap = try {
            Bitmap.createBitmap(
                ResultLayoutSpec.PNG_WIDTH,
                ResultLayoutSpec.PNG_HEIGHT,
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: RuntimeException) {
            return ResultPngRenderResult.Failure(ResultRenderFailure.RENDERING_FAILED)
        }

        return try {
            val canvas = Canvas(bitmap)
            canvas.scale(ResultLayoutSpec.PNG_SCALE, ResultLayoutSpec.PNG_SCALE)
            when (val renderResult = draw(canvas)) {
                ResultCanvasRenderResult.Success -> encode(bitmap)
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
