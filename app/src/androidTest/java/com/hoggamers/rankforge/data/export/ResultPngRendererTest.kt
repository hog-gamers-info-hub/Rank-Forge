package com.hoggamers.rankforge.data.export

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultPngRendererTest {
    private val renderer = ResultPngRenderer()

    @Test
    fun finalizedMatchModelRendersValidPngAtExactDimensions() {
        val result = renderer.render(matchModel())

        val bytes = pngSuccess(result)
        assertPngHeader(bytes)
        assertDecodedDimensions(bytes)
    }

    @Test
    fun longMatchTeamNameDoesNotChangePngDimensions() {
        val model = matchModel(
            longTeamName = "A very long team name that must remain bounded inside the Team Name column",
        )

        val bytes = pngSuccess(renderer.render(model))

        assertTrue(bytes.isNotEmpty())
        assertDecodedDimensions(bytes)
    }

    @Test
    fun currentMatchWithoutOrganizerStillRendersValidPng() {
        val bytes = pngSuccess(renderer.render(matchModel(organizerName = "")))

        assertPngHeader(bytes)
        assertDecodedDimensions(bytes)
    }

    @Test
    fun tournamentModelRendersAllTwelveStandingsRows() {
        val result = renderer.render(tournamentModel())

        val bytes = pngSuccess(result)
        assertPngHeader(bytes)
        assertDecodedDimensions(bytes)
    }

    @Test
    fun variableMatchRowCountsRenderAtExactDimensions() {
        listOf(8, 10, 12).forEach { rowCount ->
            val result = renderer.render(matchModel(rowCount = rowCount))

            val bytes = pngSuccess(result)
            assertPngHeader(bytes)
            assertDecodedDimensions(bytes)
        }
    }

    @Test
    fun longTournamentTeamNameRemainsBounded() {
        val model = tournamentModel(
            longTeamName = "A very long tournament team name that must be ellipsized deterministically",
        )

        val bytes = pngSuccess(renderer.render(model))

        assertTrue(bytes.isNotEmpty())
        assertDecodedDimensions(bytes)
    }

    @Test
    fun wholeTournamentWithoutOrganizerStillRendersValidPng() {
        val bytes = pngSuccess(renderer.render(tournamentModel(organizerName = "")))

        assertPngHeader(bytes)
        assertDecodedDimensions(bytes)
    }

    @Test
    fun longHeaderMetadataRemainsBounded() {
        val bytes = pngSuccess(
            renderer.render(
                tournamentModel(
                    tournamentName = "A very long tournament name that must remain bounded inside the export page",
                    organizerName = "A very long organizer name that must remain bounded inside the export page",
                ),
            ),
        )

        assertPngHeader(bytes)
        assertDecodedDimensions(bytes)
    }

    @Test
    fun teamNameFittingStopsAtMinimumTextSizeAndEllipsizes() {
        val method = ResultCanvasRenderer::class.java.getDeclaredMethod(
            "fittedTeamName",
            String::class.java,
            Float::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val fitted = method.invoke(
            ResultCanvasRenderer(),
            "A team name that cannot fit",
            0f,
        )
        val textSize = fitted.javaClass.getDeclaredField("textSize").apply { isAccessible = true }
            .getFloat(fitted)
        val text = fitted.javaClass.getDeclaredField("text").apply { isAccessible = true }
            .get(fitted) as String

        assertEquals(9f, textSize, 0f)
        assertEquals("…", text)
    }

    @Test
    fun blankMatchMetadataRendersWithoutFailure() {
        val model = matchModel().copy(
            tournamentName = "",
            mapName = "",
        )

        val bytes = pngSuccess(renderer.render(model))

        assertTrue(bytes.isNotEmpty())
        assertDecodedDimensions(bytes)
    }

    @Test
    fun zeroRowsReturnsExplicitFailure() {
        val result = renderer.render(matchModel().copy(rows = emptyList()))

        assertEquals(ResultRenderFailure.INVALID_ROW_COUNT, (result as ResultPngRenderResult.Failure).reason)
    }

    @Test
    fun rowsAboveMaximumReturnExplicitFailure() {
        val result = renderer.render(matchModel(rowCount = 13))

        assertEquals(ResultRenderFailure.INVALID_ROW_COUNT, (result as ResultPngRenderResult.Failure).reason)
    }

    private fun pngSuccess(result: ResultPngRenderResult): ByteArray =
        (result as ResultPngRenderResult.Success).pngBytes

    private fun assertPngHeader(bytes: ByteArray) {
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    private fun assertDecodedDimensions(bytes: ByteArray) {
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertNotNull(bitmap)
        assertEquals(ResultLayoutSpec.PNG_WIDTH, bitmap.width)
        assertEquals(ResultLayoutSpec.PNG_HEIGHT, bitmap.height)
        bitmap.recycle()
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
}
