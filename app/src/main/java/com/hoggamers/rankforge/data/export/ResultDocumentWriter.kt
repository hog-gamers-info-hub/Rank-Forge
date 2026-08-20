package com.hoggamers.rankforge.data.export

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ResultDocumentWriteFailure {
    EMPTY_CONTENT,
    OUTPUT_STREAM_UNAVAILABLE,
    WRITE_FAILED,
}

sealed interface ResultDocumentWriteResult {
    data object Success : ResultDocumentWriteResult

    data class Failure(
        val reason: ResultDocumentWriteFailure,
    ) : ResultDocumentWriteResult
}

interface ResultDocumentWriter {
    suspend fun write(
        uri: Uri?,
        bytes: ByteArray,
    ): ResultDocumentWriteResult
}

object NoOpResultDocumentWriter : ResultDocumentWriter {
    override suspend fun write(
        uri: Uri?,
        bytes: ByteArray,
    ): ResultDocumentWriteResult = ResultDocumentWriteResult.Failure(
        ResultDocumentWriteFailure.WRITE_FAILED,
    )
}

class AndroidResultDocumentWriter(
    private val contentResolver: ContentResolver,
) : ResultDocumentWriter {
    override suspend fun write(
        uri: Uri?,
        bytes: ByteArray,
    ): ResultDocumentWriteResult = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            return@withContext ResultDocumentWriteResult.Failure(
                ResultDocumentWriteFailure.EMPTY_CONTENT,
            )
        }
        if (uri == null) {
            return@withContext ResultDocumentWriteResult.Failure(
                ResultDocumentWriteFailure.OUTPUT_STREAM_UNAVAILABLE,
            )
        }
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: return@withContext ResultDocumentWriteResult.Failure(
                    ResultDocumentWriteFailure.OUTPUT_STREAM_UNAVAILABLE,
                )
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            ResultDocumentWriteResult.Success
        } catch (_: IOException) {
            ResultDocumentWriteResult.Failure(ResultDocumentWriteFailure.WRITE_FAILED)
        } catch (_: RuntimeException) {
            ResultDocumentWriteResult.Failure(ResultDocumentWriteFailure.WRITE_FAILED)
        }
    }
}
