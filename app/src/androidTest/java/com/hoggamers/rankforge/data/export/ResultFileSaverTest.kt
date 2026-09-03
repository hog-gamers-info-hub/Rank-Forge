package com.hoggamers.rankforge.data.export

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultFileSaverTest {
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var saver: ResultFileSaver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        contentResolver = context.contentResolver
        saver = ResultFileSaver(contentResolver)
    }

    @Test
    fun savesPdfToDownloadsAndCleansUpTestRow() {
        val model = matchModel()
        val displayName = ResultExportFileName.forMatch(model, ResultExportFileFormat.PDF)
        val bytes = pdfBytes(model)

        val result = runBlocking {
            saver.save(bytes, displayName, ResultExportFileFormat.PDF)
        }
        val success = result as ResultFileSaveResult.Success
        try {
            assertEquals(displayName, success.displayName)
            assertStoredMediaStoreRow(
                uri = success.uri,
                expectedDisplayName = displayName,
                expectedMimeType = ResultExportFileFormat.PDF.mimeType,
                expectedBytesPrefix = "%PDF-".encodeToByteArray(),
            )
        } finally {
            contentResolver.delete(success.uri, null, null)
        }
    }

    @Test
    fun savesPngToDownloadsAndCleansUpTestRow() {
        val model = tournamentModel()
        val displayName = ResultExportFileName.forTournament(model, ResultExportFileFormat.PNG)
        val bytes = pngBytes(model)

        val result = runBlocking {
            saver.save(bytes, displayName, ResultExportFileFormat.PNG)
        }
        val success = result as ResultFileSaveResult.Success
        try {
            assertEquals(displayName, success.displayName)
            val savedBytes = assertStoredMediaStoreRow(
                uri = success.uri,
                expectedDisplayName = displayName,
                expectedMimeType = ResultExportFileFormat.PNG.mimeType,
                expectedBytesPrefix = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            )
            assertNotNull(BitmapFactory.decodeByteArray(savedBytes, 0, savedBytes.size))
        } finally {
            contentResolver.delete(success.uri, null, null)
        }
    }

    @Test
    fun emptyBytesFailWithoutInsertingMediaStoreRow() {
        val displayName = "RankForge_Empty_Test_${System.nanoTime()}.pdf"

        val result = runBlocking {
            saver.save(ByteArray(0), displayName, ResultExportFileFormat.PDF)
        }

        assertEquals(
            ResultFileSaveResult.Failure(ResultFileSaveFailure.EMPTY_CONTENT),
            result,
        )
        val rows = queryByDisplayName(displayName)
        try {
            assertEquals(0, rows.size)
        } finally {
            rows.forEach { uri -> contentResolver.delete(uri, null, null) }
        }
    }

    private fun assertStoredMediaStoreRow(
        uri: Uri,
        expectedDisplayName: String,
        expectedMimeType: String,
        expectedBytesPrefix: ByteArray,
    ): ByteArray {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING,
        )
        contentResolver.query(uri, projection, null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertTrue(cursor!!.moveToFirst())
            assertEquals(expectedDisplayName, cursor.getString(0))
            assertEquals(expectedMimeType, cursor.getString(1))
            assertEquals(
                "${Environment.DIRECTORY_DOWNLOADS}/Rank Forge",
                cursor.getString(2).trimEnd('/'),
            )
            assertEquals(0, cursor.getInt(3))
        }
        val savedBytes = contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input).readBytes()
        }
        assertTrue(savedBytes.isNotEmpty())
        assertTrue(savedBytes.copyOfRange(0, expectedBytesPrefix.size).contentEquals(expectedBytesPrefix))
        return savedBytes
    }

    private fun queryByDisplayName(displayName: String): List<Uri> {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val result = mutableListOf<Uri>()
        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        ).use { cursor ->
            if (cursor != null) {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    result += Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex).toString(),
                    )
                }
            }
        }
        return result
    }

    private fun pdfBytes(model: MatchResultExportModel): ByteArray =
        (ResultPdfRenderer().render(model) as ResultPdfRenderResult.Success).bytes

    private fun pngBytes(model: TournamentResultExportModel): ByteArray =
        (ResultPngRenderer().render(model) as ResultPngRenderResult.Success).pngBytes

    private fun matchModel(): MatchResultExportModel =
        MatchResultExportModel(
            tournamentName = "HOG Championship",
            organizerName = "Organizer",
            tournamentDate = LocalDate.of(2026, 8, 20),
            matchNumber = 1,
            matchDate = LocalDate.of(2026, 8, 20),
            mapName = "Bermuda",
            rows = rows(),
        )

    private fun tournamentModel(): TournamentResultExportModel =
        TournamentResultExportModel(
            tournamentName = "HOG Championship",
            organizerName = "Organizer",
            tournamentDate = LocalDate.of(2026, 8, 20),
            finalizedMatchCount = 2,
            rows = rows(),
        )

    private fun rows(): List<ResultExportRow> =
        (1..12).map { rank ->
            ResultExportRow(
                rank = rank,
                teamName = "Team $rank",
                win = if (rank == 1) 1 else 0,
                totalKills = rank,
                positionPoints = 13 - rank,
                totalPoints = rank * 2,
            )
        }
}
