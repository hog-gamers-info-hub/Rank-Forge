package com.hoggamers.rankforge.data.export

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ResultFileSaveFailure {
    EMPTY_CONTENT,
    INSERT_FAILED,
    OUTPUT_STREAM_UNAVAILABLE,
    WRITE_FAILED,
    PUBLISH_FAILED,
}

enum class ResultFileSaveRoute {
    MEDIA_STORE_DOWNLOADS,
    USER_SELECTED_DESTINATION_REQUIRED,
}

object ResultFileSavePolicy {
    fun routeForSdk(sdkInt: Int): ResultFileSaveRoute =
        if (sdkInt >= Build.VERSION_CODES.Q) {
            ResultFileSaveRoute.MEDIA_STORE_DOWNLOADS
        } else {
            ResultFileSaveRoute.USER_SELECTED_DESTINATION_REQUIRED
        }
}

sealed interface ResultFileSaveResult {
    data class Success(
        val uri: Uri,
        val displayName: String,
    ) : ResultFileSaveResult

    data object UserSelectedDestinationRequired : ResultFileSaveResult

    data class Failure(
        val reason: ResultFileSaveFailure,
    ) : ResultFileSaveResult
}

class ResultFileSaver(
    private val contentResolver: ContentResolver,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    suspend fun save(
        bytes: ByteArray,
        displayName: String,
        format: ResultExportFileFormat,
    ): ResultFileSaveResult = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            return@withContext ResultFileSaveResult.Failure(ResultFileSaveFailure.EMPTY_CONTENT)
        }

        when (ResultFileSavePolicy.routeForSdk(sdkInt)) {
            ResultFileSaveRoute.USER_SELECTED_DESTINATION_REQUIRED ->
                ResultFileSaveResult.UserSelectedDestinationRequired
            ResultFileSaveRoute.MEDIA_STORE_DOWNLOADS -> saveToMediaStore(
                bytes = bytes,
                displayName = displayName,
                format = format,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(
        bytes: ByteArray,
        displayName: String,
        format: ResultExportFileFormat,
    ): ResultFileSaveResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/PointIQ",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {
            null
        } ?: return ResultFileSaveResult.Failure(ResultFileSaveFailure.INSERT_FAILED)

        var published = false
        return try {
            val output = contentResolver.openOutputStream(uri)
                ?: return ResultFileSaveResult.Failure(ResultFileSaveFailure.OUTPUT_STREAM_UNAVAILABLE)
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            val updatedRows = contentResolver.update(uri, publishValues, null, null)
            if (updatedRows != 1) {
                ResultFileSaveResult.Failure(ResultFileSaveFailure.PUBLISH_FAILED)
            } else {
                published = true
                ResultFileSaveResult.Success(uri = uri, displayName = displayName)
            }
        } catch (_: Exception) {
            ResultFileSaveResult.Failure(ResultFileSaveFailure.WRITE_FAILED)
        } finally {
            if (!published) {
                runCatching { contentResolver.delete(uri, null, null) }
            }
        }
    }
}
