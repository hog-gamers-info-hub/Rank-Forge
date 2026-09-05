package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreFailure
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreResult
import com.hoggamers.rankforge.data.cloud.RestoredCustomDesign
import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentCsvExportInput
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomDesignResultDownloadCoordinatorTest {
    @Test
    fun currentMatchUsesExactIdRowsUnchangedAndEncodesFullResolutionPng() {
        val customDesignId = "a2000000-0000-0000-0000-000000000001"
        val rows = listOf(
            ResultExportRow(rank = 9, teamName = "Team B", win = 0, totalKills = 7, positionPoints = 8, totalPoints = 15),
            ResultExportRow(rank = 1, teamName = "Team A", win = 1, totalKills = 4, positionPoints = 12, totalPoints = 16),
        )
        var restoredId: String? = null
        var composedReference: String? = null
        var composedRows: List<ResultExportRow>? = null
        var composedGeometry: CustomDesignEffectiveGridGeometry? = null
        var composedTextColors: CustomDesignColumnTextColors? = null
        var savedBytes: ByteArray? = null
        var savedFormat: ResultExportFileFormat? = null
        var savedDisplayName: String? = null
        val bitmap = Bitmap.createBitmap(17, 19, Bitmap.Config.ARGB_8888)
        val coordinator = coordinator(
            restore = { id ->
                restoredId = id
                CustomDesignRestoreResult.Success(restoredDesign())
            },
            resolveRows = { CustomDesignResultRowsResult.Success(rows) },
            composeBitmap = { reference, resolvedRows, geometry, textColors ->
                composedReference = reference
                composedRows = resolvedRows
                composedGeometry = geometry
                composedTextColors = textColors
                CustomDesignBitmapComposeResult.Success(bitmap)
            },
            saveFile = { bytes, displayName, format ->
                savedBytes = bytes
                savedDisplayName = displayName
                savedFormat = format
                ResultFileSaveResult.Success(Uri.EMPTY, "result.png")
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.execute(customDesignId, currentMatchRequest())
        }

        assertEquals(ResultDownloadExecutionResult.Saved(ResultExportFileFormat.PNG, "result.png"), result)
        assertEquals(customDesignId, restoredId)
        assertEquals("file:///restored/custom-design.png", composedReference)
        assertEquals(rows, composedRows)
        assertEquals(restoredDesign().geometry, composedGeometry)
        assertEquals(restoredDesign().textColors, composedTextColors)
        assertEquals(ResultExportFileFormat.PNG, savedFormat)
        assertEquals("PointIQ_Tournament_Match_1_Result.png", savedDisplayName)
        assertTrue(bitmap.isRecycled)
        val encodedBytes = checkNotNull(savedBytes)
        val decoded = BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size)
        try {
            assertEquals(17, decoded.width)
            assertEquals(19, decoded.height)
        } finally {
            decoded.recycle()
        }
    }

    @Test
    fun wholeTournamentRowsArePassedUnchanged() {
        val rows = listOf(
            ResultExportRow(rank = 2, teamName = "Second", win = 0, totalKills = 3, positionPoints = 5, totalPoints = 8),
        )
        var receivedRequest: ResultDownloadRequest? = null
        var receivedRows: List<ResultExportRow>? = null
        var savedDisplayName: String? = null
        val bitmap = Bitmap.createBitmap(4, 5, Bitmap.Config.ARGB_8888)
        val coordinator = coordinator(
            resolveRows = { request ->
                receivedRequest = request
                CustomDesignResultRowsResult.Success(rows)
            },
            composeBitmap = { _, resolvedRows, _, _ ->
                receivedRows = resolvedRows
                CustomDesignBitmapComposeResult.Success(bitmap)
            },
            saveFile = { _, displayName, _ ->
                savedDisplayName = displayName
                ResultFileSaveResult.Success(Uri.EMPTY, "result.png")
            },
        )

        kotlinx.coroutines.runBlocking {
            coordinator.execute(
                "a2000000-0000-0000-0000-000000000001",
                wholeTournamentRequest(),
            )
        }

        assertTrue(receivedRequest is ResultDownloadRequest.WholeTournament)
        assertEquals(rows, receivedRows)
        assertEquals("PointIQ_Tournament_Tournament_Result.png", savedDisplayName)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun restoreFailureDoesNotSave() {
        var saves = 0
        val coordinator = coordinator(
            restore = {
                CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.NOT_FOUND)
            },
            saveFile = { _, _, _ ->
                saves++
                ResultFileSaveResult.Success(Uri.EMPTY, "unexpected.png")
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.execute("a2000000-0000-0000-0000-000000000001", currentMatchRequest())
        }

        assertEquals(ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED), result)
        assertEquals(0, saves)
    }

    @Test
    fun rowsFailureDoesNotSave() {
        var saves = 0
        val coordinator = coordinator(
            resolveRows = {
                CustomDesignResultRowsResult.MatchFailure(emptySet())
            },
            saveFile = { _, _, _ ->
                saves++
                ResultFileSaveResult.Success(Uri.EMPTY, "unexpected.png")
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.execute("a2000000-0000-0000-0000-000000000001", currentMatchRequest())
        }

        assertEquals(ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED), result)
        assertEquals(0, saves)
    }

    @Test
    fun compositionFailureDoesNotSave() {
        var saves = 0
        val coordinator = coordinator(
            composeBitmap = { _, _, _, _ ->
                CustomDesignBitmapComposeResult.Failure(CustomDesignBitmapComposeFailure.RENDER_FAILED)
            },
            saveFile = { _, _, _ ->
                saves++
                ResultFileSaveResult.Success(Uri.EMPTY, "unexpected.png")
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.execute("a2000000-0000-0000-0000-000000000001", currentMatchRequest())
        }

        assertEquals(ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED), result)
        assertEquals(0, saves)
    }

    @Test
    fun compressionFailureDoesNotSaveAndBitmapIsRecycled() {
        var saves = 0
        val bitmap = Bitmap.createBitmap(4, 5, Bitmap.Config.ARGB_8888).apply { recycle() }
        val coordinator = coordinator(
            composeBitmap = { _, _, _, _ -> CustomDesignBitmapComposeResult.Success(bitmap) },
            saveFile = { _, _, _ ->
                saves++
                ResultFileSaveResult.Success(Uri.EMPTY, "unexpected.png")
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.execute("a2000000-0000-0000-0000-000000000001", currentMatchRequest())
        }

        assertEquals(ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED), result)
        assertEquals(0, saves)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun userDestinationOutcomeIsPropagatedWithPngBytes() {
        var savingCalls = 0
        val bitmap = Bitmap.createBitmap(4, 5, Bitmap.Config.ARGB_8888)
        val coordinator = coordinator(
            composeBitmap = { _, _, _, _ -> CustomDesignBitmapComposeResult.Success(bitmap) },
            saveFile = { saved, _, format ->
                assertEquals(ResultExportFileFormat.PNG, format)
                assertFalse(saved.isEmpty())
                ResultFileSaveResult.UserSelectedDestinationRequired
            },
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.execute(
                "a2000000-0000-0000-0000-000000000001",
                currentMatchRequest(),
                onSaving = { savingCalls++ },
            )
        }

        assertTrue(result is ResultDownloadExecutionResult.UserDestinationRequired)
        assertEquals(ResultExportFileFormat.PNG, (result as ResultDownloadExecutionResult.UserDestinationRequired).format)
        assertTrue(result.bytes.isNotEmpty())
        assertEquals(1, savingCalls)
        assertTrue(bitmap.isRecycled)
    }

    private fun coordinator(
        restore: suspend (String) -> CustomDesignRestoreResult = {
            CustomDesignRestoreResult.Success(restoredDesign())
        },
        resolveRows: (ResultDownloadRequest) -> CustomDesignResultRowsResult = {
            CustomDesignResultRowsResult.Success(emptyList())
        },
        composeBitmap: (
            String,
            List<ResultExportRow>,
            CustomDesignEffectiveGridGeometry,
            CustomDesignColumnTextColors,
        ) -> CustomDesignBitmapComposeResult = { _, _, _, _ ->
            CustomDesignBitmapComposeResult.Failure(CustomDesignBitmapComposeFailure.RENDER_FAILED)
        },
        saveFile: suspend (ByteArray, String, ResultExportFileFormat) -> ResultFileSaveResult = { _, _, _ ->
            ResultFileSaveResult.Success(Uri.EMPTY, "result.png")
        },
    ) = DefaultCustomDesignResultDownloadCoordinator(
        restoreAction = CustomDesignRestoreAction(restore),
        resolveRows = resolveRows,
        composeBitmap = composeBitmap,
        saveFile = saveFile,
    )

    private fun restoredDesign() = RestoredCustomDesign(
        customDesignId = "a2000000-0000-0000-0000-000000000001",
        ownerUserId = "b2000000-0000-0000-0000-000000000001",
        localImageReference = "file:///restored/custom-design.png",
        sourceWidth = 17,
        sourceHeight = 19,
        labels = CustomDesignOcrLabels("TEAM", "WIN", "KILLS", "POSITION", "TOTAL"),
        geometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = 17,
            sourceHeight = 19,
            columnX = CustomDesignAnchorField.entries.associateWith { 1f },
            rowY = (1..12).associateWith { it.toFloat() },
        ),
        textColors = CustomDesignColumnTextColors.fromMap(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to "#112233",
                CustomDesignAnchorField.WIN to "#223344",
                CustomDesignAnchorField.TOTAL_KILLS to "#334455",
                CustomDesignAnchorField.POSITION_POINTS to "#445566",
                CustomDesignAnchorField.TOTAL_POINTS to "#556677",
            ),
        )!!,
    )

    private fun currentMatchRequest() = ResultDownloadRequest.CurrentMatch(
        MatchCsvExportInput(
            tournament = tournament(),
            match = match(),
            teamSlots = emptyList(),
            rosterPlayers = emptyList(),
        ),
    )

    private fun wholeTournamentRequest() = ResultDownloadRequest.WholeTournament(
        TournamentCsvExportInput(
            tournament = tournament(),
            matches = listOf(match()),
            teamSlots = emptyList(),
            rosterPlayers = emptyList(),
        ),
    )

    private fun tournament() = Tournament(
        id = "tournament-id",
        name = "Tournament",
        date = LocalDate.of(2026, 9, 5),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )

    private fun match() = Match(
        id = "match-id",
        tournamentId = "tournament-id",
        matchNumber = 1,
        date = LocalDate.of(2026, 9, 5),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
    )
}
