package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.export.TournamentCsvExportInput
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeDesignResultDownloadCoordinatorTest {
    @Test
    fun currentMatchBuildsAndSavesFreeDesignPngWithMatchFilename() {
        var receivedRows = 0
        var savingCalls = 0
        val bitmap = Bitmap.createBitmap(4, 5, Bitmap.Config.ARGB_8888)
        val coordinator = coordinator(
            composeMatch = { model, _ ->
                receivedRows = model.rows.size
                FreeDesignBitmapComposeResult.Success(bitmap)
            },
            saveFile = { bytes, displayName, format ->
                assertFalse(bytes.isEmpty())
                assertEquals(ResultExportFileFormat.PNG, format)
                assertEquals("PointIQ_Tournament_Match_1_Result.png", displayName)
                ResultFileSaveResult.Success(Uri.EMPTY, displayName)
            },
        )

        val result = runBlocking {
            coordinator.execute(
                request = ResultDownloadRequest.CurrentMatch(
                    MatchCsvExportInput(
                        tournament = tournament(),
                        match = match(),
                        teamSlots = teamSlots(),
                        rosterPlayers = emptyList(),
                    ),
                ),
                onSaving = { savingCalls++ },
            )
        }

        assertEquals(
            ResultDownloadExecutionResult.Saved(
                uri = Uri.EMPTY,
                format = ResultExportFileFormat.PNG,
                displayName = "PointIQ_Tournament_Match_1_Result.png",
            ),
            result,
        )
        assertEquals(1, receivedRows)
        assertEquals(1, savingCalls)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun wholeTournamentUsesOverallFilenameAndSupportsDestinationFallback() {
        var receivedRows = 0
        val bitmap = Bitmap.createBitmap(4, 5, Bitmap.Config.ARGB_8888)
        val coordinator = coordinator(
            composeTournament = { model, _ ->
                receivedRows = model.rows.size
                FreeDesignBitmapComposeResult.Success(bitmap)
            },
            saveFile = { _, displayName, format ->
                assertEquals(ResultExportFileFormat.PNG, format)
                assertEquals("PointIQ_Tournament_Tournament_Result.png", displayName)
                ResultFileSaveResult.UserSelectedDestinationRequired
            },
        )

        val result = runBlocking {
            coordinator.execute(
                request = ResultDownloadRequest.WholeTournament(
                    TournamentCsvExportInput(
                        tournament = tournament(),
                        matches = listOf(match()),
                        teamSlots = teamSlots(),
                        rosterPlayers = emptyList(),
                    ),
                ),
            )
        }

        assertTrue(result is ResultDownloadExecutionResult.UserDestinationRequired)
        assertEquals(1, receivedRows)
        assertTrue((result as ResultDownloadExecutionResult.UserDestinationRequired).bytes.isNotEmpty())
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun composeFailureDoesNotSaveOrCallSaving() {
        var saves = 0
        var savingCalls = 0
        val coordinator = coordinator(
            composeMatch = { _, _ ->
                FreeDesignBitmapComposeResult.Failure(FreeDesignBitmapComposeFailure.RENDER_FAILED)
            },
            saveFile = { _, _, _ ->
                saves++
                ResultFileSaveResult.Success(Uri.EMPTY, "unexpected.png")
            },
        )

        val result = runBlocking {
            coordinator.execute(
                request = ResultDownloadRequest.CurrentMatch(
                    MatchCsvExportInput(
                        tournament = tournament(),
                        match = match(),
                        teamSlots = teamSlots(),
                        rosterPlayers = emptyList(),
                    ),
                ),
                onSaving = { savingCalls++ },
            )
        }

        assertEquals(
            ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED),
            result,
        )
        assertEquals(0, saves)
        assertEquals(0, savingCalls)
    }

    @Test
    fun missingTemplateDoesNotSave() {
        var saves = 0
        val coordinator = coordinator(
            templateProvider = { null },
            saveFile = { _, _, _ ->
                saves++
                ResultFileSaveResult.Success(Uri.EMPTY, "unexpected.png")
            },
        )

        val result = runBlocking {
            coordinator.execute(
                ResultDownloadRequest.CurrentMatch(
                    MatchCsvExportInput(
                        tournament = tournament(),
                        match = match(),
                        teamSlots = teamSlots(),
                        rosterPlayers = emptyList(),
                    ),
                ),
            )
        }

        assertEquals(
            ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED),
            result,
        )
        assertEquals(0, saves)
    }

    private fun coordinator(
        composeMatch: (com.hoggamers.rankforge.domain.export.MatchResultExportModel, FreeDesignTemplate) -> FreeDesignBitmapComposeResult = { _, _ ->
            FreeDesignBitmapComposeResult.Failure(FreeDesignBitmapComposeFailure.RENDER_FAILED)
        },
        composeTournament: (com.hoggamers.rankforge.domain.export.TournamentResultExportModel, FreeDesignTemplate) -> FreeDesignBitmapComposeResult = { _, _ ->
            FreeDesignBitmapComposeResult.Failure(FreeDesignBitmapComposeFailure.RENDER_FAILED)
        },
        templateProvider: () -> FreeDesignTemplate? = { FreeDesignTemplateRegistry.default() },
        saveFile: suspend (ByteArray, String, ResultExportFileFormat) -> ResultFileSaveResult,
    ): DefaultFreeDesignResultDownloadCoordinator = DefaultFreeDesignResultDownloadCoordinator(
        modelBuilder = ResultExportModelBuilder(),
        composeMatch = composeMatch,
        composeTournament = composeTournament,
        templateProvider = templateProvider,
        saveFile = saveFile,
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
        participantResults = listOf(
            MatchParticipantResult(
                teamSlotNumber = 1,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = 1,
                kills = 5,
            ),
        ),
    )

    private fun teamSlots(): List<TeamSlot> = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        TeamSlot.create("tournament-id", slotNumber, "Team $slotNumber")
    }
}
