package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultPdfRendererTest {
    private val renderer = ResultPdfRenderer()

    @Test
    fun currentMatchPdfIsValidOnePage842By595Document() {
        val model = matchModel()
        val original = model.copy(rows = model.rows.toList())

        val bytes = pdfSuccess(renderer.render(model))

        assertValidPdf(bytes)
        assertEquals(original, model)
    }

    @Test
    fun currentMatchLongTeamNameRendersWithoutChangingModel() {
        val model = matchModel(
            longTeamName = "A very long match team name that must remain bounded inside the shared table",
        )
        val original = model.copy(rows = model.rows.toList())

        val bytes = pdfSuccess(renderer.render(model))

        assertValidPdf(bytes)
        assertEquals(original, model)
    }

    @Test
    fun currentMatchWithoutOrganizerStillRendersOnePage() {
        assertValidPdf(pdfSuccess(renderer.render(matchModel(organizerName = ""))))
    }

    @Test
    fun wholeTournamentPdfIsValidOnePage842By595Document() {
        val model = tournamentModel()
        val original = model.copy(rows = model.rows.toList())

        val bytes = pdfSuccess(renderer.render(model))

        assertValidPdf(bytes)
        assertEquals(original, model)
    }

    @Test
    fun wholeTournamentLongTeamNamePreservesRowOrdering() {
        val model = tournamentModel(
            longTeamName = "A very long tournament team name that must be ellipsized deterministically",
        )
        val originalRows = model.rows.toList()

        val bytes = pdfSuccess(renderer.render(model))

        assertValidPdf(bytes)
        assertEquals(originalRows, model.rows)
    }

    @Test
    fun wholeTournamentWithoutOrganizerStillRendersOnePage() {
        assertValidPdf(pdfSuccess(renderer.render(tournamentModel(organizerName = ""))))
    }

    @Test
    fun longHeaderMetadataRendersWithinValidPdf() {
        assertValidPdf(
            pdfSuccess(
                renderer.render(
                    matchModel(
                        tournamentName = "A very long tournament name that must remain bounded inside the export page",
                        organizerName = "A very long organizer name that must remain bounded inside the export page",
                    ),
                ),
            ),
        )
    }

    @Test
    fun variableMatchRowCountsRenderValidOnePagePdfs() {
        listOf(8, 10, 12).forEach { rowCount ->
            assertValidPdf(pdfSuccess(renderer.render(matchModel(rowCount = rowCount))))
        }
    }

    @Test
    fun variableTournamentRowCountsRenderValidOnePagePdfs() {
        listOf(8, 10, 12).forEach { rowCount ->
            assertValidPdf(pdfSuccess(renderer.render(tournamentModel(rowCount = rowCount))))
        }
    }

    private fun pdfSuccess(result: ResultPdfRenderResult): ByteArray =
        (result as ResultPdfRenderResult.Success).bytes

    private fun assertValidPdf(bytes: ByteArray) {
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size >= PDF_SIGNATURE.length)
        assertEquals(
            PDF_SIGNATURE,
            String(bytes, 0, PDF_SIGNATURE.length, StandardCharsets.US_ASCII),
        )

        val file = File.createTempFile("rank-forge-result-", ".pdf")
        try {
            file.outputStream().use { output -> output.write(bytes) }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    assertEquals(1, pdf.pageCount)
                    pdf.openPage(0).use { page ->
                        assertEquals(ResultLayoutSpec.LOGICAL_PAGE_WIDTH, page.width)
                        assertEquals(ResultLayoutSpec.LOGICAL_PAGE_HEIGHT, page.height)
                        val bitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888,
                        )
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun matchModel(
        tournamentName: String = "Synthetic Cup",
        organizerName: String = "Synthetic Organizer",
        longTeamName: String? = null,
        rowCount: Int = 12,
    ): MatchResultExportModel =
        MatchResultExportModel(
            tournamentName = tournamentName,
            organizerName = organizerName,
            tournamentDate = LocalDate.of(2026, 9, 3),
            matchNumber = 3,
            matchDate = LocalDate.of(2026, 7, 31),
            mapName = "Bermuda",
            rows = rows(longTeamName, rowCount),
        )

    private fun tournamentModel(
        tournamentName: String = "Synthetic Cup",
        organizerName: String = "Synthetic Organizer",
        longTeamName: String? = null,
        rowCount: Int = 12,
    ): TournamentResultExportModel =
        TournamentResultExportModel(
            tournamentName = tournamentName,
            organizerName = organizerName,
            tournamentDate = LocalDate.of(2026, 9, 3),
            finalizedMatchCount = 2,
            rows = rows(longTeamName, rowCount),
        )

    private fun rows(
        longTeamName: String?,
        rowCount: Int = 12,
    ): List<ResultExportRow> =
        (1..rowCount).map { rank ->
            ResultExportRow(
                rank = rank,
                teamName = if (rank == 1 && longTeamName != null) longTeamName else "Team $rank",
                win = if (rank == 1) 1 else 0,
                totalKills = rank * 2,
                positionPoints = 13 - rank,
                totalPoints = rank * 3,
            )
        }

    private companion object {
        const val PDF_SIGNATURE = "%PDF-"
    }
}
