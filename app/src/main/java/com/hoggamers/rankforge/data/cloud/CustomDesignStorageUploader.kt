package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import io.ktor.http.ContentType
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

const val CUSTOM_DESIGNS_BUCKET = "custom-designs"

enum class CustomDesignStorageFailure {
    MISSING_AUTH_SESSION,
    AUTHORIZATION,
    INVALID_ID,
    MISSING_LOCAL_FILE,
    LOCAL_FILE_READ_FAILED,
    UNSUPPORTED_FORMAT,
    NETWORK,
    UPLOAD_FAILED,
    DELETE_FAILED,
}

sealed interface CustomDesignStorageUploadResult {
    data class Uploaded(val objectPath: String) : CustomDesignStorageUploadResult
    data class Failed(val failure: CustomDesignStorageFailure) : CustomDesignStorageUploadResult
}

sealed interface CustomDesignStorageDeleteResult {
    data object Deleted : CustomDesignStorageDeleteResult
    data class Failed(val failure: CustomDesignStorageFailure) : CustomDesignStorageDeleteResult
}

interface CustomDesignStorageUploader {
    suspend fun upload(
        expectedOwnerUserId: String,
        customDesignId: String,
        preparedFile: File,
        extension: String,
        mimeType: String,
    ): CustomDesignStorageUploadResult

    suspend fun delete(
        expectedOwnerUserId: String,
        customDesignId: String,
        extension: String,
    ): CustomDesignStorageDeleteResult
}

@Singleton
class SupabaseCustomDesignStorageUploader internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val uploadFile: suspend (String, String, File, String, Boolean) -> Unit,
    private val deleteFile: suspend (String, List<String>) -> Unit,
) : CustomDesignStorageUploader {
    @Inject
    constructor(
        config: SupabaseAuthConfig,
        clientProvider: SupabaseClientProvider,
    ) : this(
        isConfigured = { config.isConfigured },
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        uploadFile = { bucket, path, file, mimeType, upsert ->
            clientProvider.client.storage.from(bucket).upload(path, file) {
                this.upsert = upsert
                contentType = ContentType.parse(mimeType)
            }
        },
        deleteFile = { bucket, paths ->
            clientProvider.client.storage.from(bucket).delete(paths)
        },
    )

    override suspend fun upload(
        expectedOwnerUserId: String,
        customDesignId: String,
        preparedFile: File,
        extension: String,
        mimeType: String,
    ): CustomDesignStorageUploadResult {
        if (!isConfigured()) return CustomDesignStorageUploadResult.Failed(
            CustomDesignStorageFailure.UPLOAD_FAILED,
        )
        val userId = currentUserId()
            ?: return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.MISSING_AUTH_SESSION)
        if (expectedOwnerUserId.isBlank() || userId != expectedOwnerUserId) {
            return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.AUTHORIZATION)
        }
        if (!isCanonicalUuid(customDesignId)) {
            return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.INVALID_ID)
        }
        if (!preparedFile.isFile || !preparedFile.canRead()) {
            return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.LOCAL_FILE_READ_FAILED)
        }
        if (preparedFile.length() <= 0L) {
            return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.MISSING_LOCAL_FILE)
        }
        val format = CustomDesignImageFormat.fromPair(extension, mimeType)
            ?: return CustomDesignStorageUploadResult.Failed(CustomDesignStorageFailure.UNSUPPORTED_FORMAT)
        val objectPath = objectPath(userId, customDesignId, format.extension)
        return try {
            uploadFile(CUSTOM_DESIGNS_BUCKET, objectPath, preparedFile, format.mimeType, false)
            CustomDesignStorageUploadResult.Uploaded(objectPath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            CustomDesignStorageUploadResult.Failed(throwable.toUploadFailure())
        }
    }

    override suspend fun delete(
        expectedOwnerUserId: String,
        customDesignId: String,
        extension: String,
    ): CustomDesignStorageDeleteResult {
        if (!isConfigured()) return CustomDesignStorageDeleteResult.Failed(
            CustomDesignStorageFailure.DELETE_FAILED,
        )
        val userId = currentUserId()
            ?: return CustomDesignStorageDeleteResult.Failed(CustomDesignStorageFailure.MISSING_AUTH_SESSION)
        if (expectedOwnerUserId.isBlank() || userId != expectedOwnerUserId) {
            return CustomDesignStorageDeleteResult.Failed(CustomDesignStorageFailure.AUTHORIZATION)
        }
        if (!isCanonicalUuid(customDesignId)) {
            return CustomDesignStorageDeleteResult.Failed(CustomDesignStorageFailure.INVALID_ID)
        }
        val format = CustomDesignImageFormat.fromMimeType(mimeTypeForExtension(extension).orEmpty())
            ?: return CustomDesignStorageDeleteResult.Failed(CustomDesignStorageFailure.UNSUPPORTED_FORMAT)
        return try {
            deleteFile(CUSTOM_DESIGNS_BUCKET, listOf(objectPath(userId, customDesignId, format.extension)))
            CustomDesignStorageDeleteResult.Deleted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val failure = throwable.toDeleteFailure()
            if (failure == CustomDesignStorageFailure.AUTHORIZATION) {
                CustomDesignStorageDeleteResult.Failed(failure)
            } else if (throwable.isMissingStorageObject()) {
                CustomDesignStorageDeleteResult.Deleted
            } else {
                CustomDesignStorageDeleteResult.Failed(failure)
            }
        }
    }

    companion object {
        fun objectPath(userId: String, customDesignId: String, extension: String): String =
            "users/$userId/custom-designs/$customDesignId/original.$extension"

        private fun isCanonicalUuid(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

        private fun mimeTypeForExtension(extension: String): String? = when (
            extension.lowercase(Locale.ROOT)
        ) {
            "png" -> "image/png"
            "jpg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> null
        }
    }
}

private fun Throwable.toUploadFailure(): CustomDesignStorageFailure {
    val message = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        this is IOException || message.contains("network") || message.contains("timeout") ||
            message.contains("unable to resolve") || message.contains("connect") ->
            CustomDesignStorageFailure.NETWORK
        message.contains("401") || message.contains("403") || message.contains("unauthor") ||
            message.contains("forbidden") || message.contains("row-level security") || message.contains("42501") ->
            CustomDesignStorageFailure.AUTHORIZATION
        else -> CustomDesignStorageFailure.UPLOAD_FAILED
    }
}

private fun Throwable.toDeleteFailure(): CustomDesignStorageFailure {
    val message = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        message.contains("401") || message.contains("403") || message.contains("unauthor") ||
            message.contains("forbidden") || message.contains("row-level security") || message.contains("42501") ->
            CustomDesignStorageFailure.AUTHORIZATION
        this is IOException || message.contains("network") || message.contains("timeout") ||
            message.contains("connect") -> CustomDesignStorageFailure.NETWORK
        else -> CustomDesignStorageFailure.DELETE_FAILED
    }
}

private fun Throwable.isMissingStorageObject(): Boolean {
    val message = message.orEmpty().lowercase(Locale.ROOT)
    return message.contains("404") ||
        message.contains("not found") ||
        message.contains("object does not exist")
}
