package com.hoggamers.rankforge.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultDocumentWriterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writesBytesToGrantedUserDestinationUri() {
        val displayName = "RankForge_DocumentWriterTest_${System.nanoTime()}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, ResultExportFileFormat.PDF.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/Rank Forge",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        val bytes = "%PDF-test".encodeToByteArray()
        try {
            val result = runBlocking {
                AndroidResultDocumentWriter(resolver).write(uri, bytes)
            }
            assertEquals(ResultDocumentWriteResult.Success, result)
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            val saved = resolver.openInputStream(uri).use { input ->
                checkNotNull(input).readBytes()
            }
            assertTrue(saved.contentEquals(bytes))
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
