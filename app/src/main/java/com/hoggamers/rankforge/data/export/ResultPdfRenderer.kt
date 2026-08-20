package com.hoggamers.rankforge.data.export

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.io.ByteArrayOutputStream

enum class ResultPdfRenderFailure {
    INVALID_ROW_COUNT,
    PDF_RENDER_FAILED,
    PDF_WRITE_FAILED,
}

sealed interface ResultPdfRenderResult {
    data class Success(
        val bytes: ByteArray,
    ) : ResultPdfRenderResult

    data class Failure(
        val reason: ResultPdfRenderFailure,
    ) : ResultPdfRenderResult
}

class ResultPdfRenderer(
    private val canvasRenderer: ResultCanvasRenderer = ResultCanvasRenderer(),
) {
    fun render(model: MatchResultExportModel): ResultPdfRenderResult =
        renderPage { canvas -> canvasRenderer.render(canvas, model) }

    fun render(model: TournamentResultExportModel): ResultPdfRenderResult =
        renderPage { canvas -> canvasRenderer.render(canvas, model) }

    private fun renderPage(
        draw: (Canvas) -> ResultCanvasRenderResult,
    ): ResultPdfRenderResult {
        val document = PdfDocument()
        return try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                ResultLayoutSpec.LOGICAL_PAGE_WIDTH,
                ResultLayoutSpec.LOGICAL_PAGE_HEIGHT,
                1,
            ).create()
            val page = document.startPage(pageInfo)
            val canvasResult = draw(page.canvas)
            document.finishPage(page)

            when (canvasResult) {
                ResultCanvasRenderResult.Success -> writeDocument(document)
                is ResultCanvasRenderResult.Failure ->
                    ResultPdfRenderResult.Failure(canvasResult.reason.toPdfFailure())
            }
        } catch (_: Exception) {
            ResultPdfRenderResult.Failure(ResultPdfRenderFailure.PDF_RENDER_FAILED)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun writeDocument(document: PdfDocument): ResultPdfRenderResult =
        ByteArrayOutputStream().use { output ->
            try {
                document.writeTo(output)
                ResultPdfRenderResult.Success(output.toByteArray())
            } catch (_: Exception) {
                ResultPdfRenderResult.Failure(ResultPdfRenderFailure.PDF_WRITE_FAILED)
            }
        }

    private fun ResultRenderFailure.toPdfFailure(): ResultPdfRenderFailure =
        when (this) {
            ResultRenderFailure.INVALID_ROW_COUNT -> ResultPdfRenderFailure.INVALID_ROW_COUNT
            ResultRenderFailure.RENDERING_FAILED,
            ResultRenderFailure.PNG_COMPRESSION_FAILED,
            -> ResultPdfRenderFailure.PDF_RENDER_FAILED
        }
}
