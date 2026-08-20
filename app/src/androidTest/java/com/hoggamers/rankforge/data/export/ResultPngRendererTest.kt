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
    fun tournamentModelRendersAllTwelveStandingsRows() {
        val result = renderer.render(tournamentModel())

        val bytes = pngSuccess(result)
        assertPngHeader(bytes)
        assertDecodedDimensions(bytes)
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
    fun invalidRowCountReturnsExplicitFailure() {
        val result = renderer.render(matchModel().copy(rows = rows(null).dropLast(1)))

        assertEquals(
            ResultRenderFailure.INVALID_ROW_COUNT,
            (result as ResultPngRenderResult.Failure).reason,
        )
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
        longTeamName: String? = null,
    ): MatchResultExportModel =
        MatchResultExportModel(
            tournamentName = "Synthetic Cup",
            matchNumber = 3,
            matchDate = LocalDate.of(2026, 7, 31),
            mapName = "Bermuda",
            rows = rows(longTeamName),
        )

    private fun tournamentModel(
        longTeamName: String? = null,
    ): TournamentResultExportModel =
        TournamentResultExportModel(
            tournamentName = "Synthetic Cup",
            finalizedMatchCount = 2,
            rows = rows(longTeamName),
        )

    private fun rows(
        longTeamName: String?,
    ): List<ResultExportRow> =
        (1..12).map { rank ->
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
