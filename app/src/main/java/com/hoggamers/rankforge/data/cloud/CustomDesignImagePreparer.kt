package com.hoggamers.rankforge.data.cloud

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedCustomDesignImage(
    val file: File,
    val sha256: String,
    val byteSize: Long,
    val mimeType: String,
    val extension: String,
) {
    fun cleanup() {
        runCatching { file.delete() }
    }
}

fun interface CustomDesignImagePreparer {
    suspend fun prepare(imageReference: String): PreparedCustomDesignImage
}

class CustomDesignImagePreparationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AndroidCustomDesignImagePreparer internal constructor(
    private val readMimeType: (String) -> String?,
    private val openInputStream: (String) -> InputStream?,
    private val createTempFile: () -> File,
) : CustomDesignImagePreparer {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        readMimeType = { reference ->
            context.contentResolver.getType(Uri.parse(reference))
        },
        openInputStream = { reference ->
            context.contentResolver.openInputStream(Uri.parse(reference))
        },
        createTempFile = {
            File.createTempFile("custom-design-", ".tmp", context.cacheDir)
        },
    )

    override suspend fun prepare(imageReference: String): PreparedCustomDesignImage =
        withContext(Dispatchers.IO) {
            if (imageReference.isBlank()) {
                throw CustomDesignImagePreparationException("Image reference is required")
            }
            val mimeType = readMimeType(imageReference)
                ?.lowercase(Locale.ROOT)
                ?: throw CustomDesignImagePreparationException("Image MIME type is unavailable")
            val format = CustomDesignImageFormat.fromMimeType(mimeType)
                ?: throw CustomDesignImagePreparationException("Unsupported image MIME type")
            val tempFile = try {
                createTempFile()
            } catch (throwable: Throwable) {
                throw CustomDesignImagePreparationException("Temporary image file could not be created", throwable)
            }
            var complete = false
            try {
                val input = openInputStream(imageReference)
                    ?: throw CustomDesignImagePreparationException("Image source is unreadable")
                val digest = MessageDigest.getInstance("SHA-256")
                var byteSize = 0L
                input.use { source ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            byteSize += read.toLong()
                        }
                    }
                }
                if (byteSize <= 0L) {
                    throw CustomDesignImagePreparationException("Image source is empty")
                }
                complete = true
                PreparedCustomDesignImage(
                    file = tempFile,
                    sha256 = digest.digest().toHexStringLowercase(),
                    byteSize = byteSize,
                    mimeType = format.mimeType,
                    extension = format.extension,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (preparation: CustomDesignImagePreparationException) {
                throw preparation
            } catch (throwable: Throwable) {
                throw CustomDesignImagePreparationException("Image source could not be prepared", throwable)
            } finally {
                if (!complete) runCatching { tempFile.delete() }
            }
        }
}

data class CustomDesignImageFormat(
    val extension: String,
    val mimeType: String,
) {
    companion object {
        fun fromMimeType(mimeType: String): CustomDesignImageFormat? = when (mimeType.lowercase(Locale.ROOT)) {
            "image/png" -> CustomDesignImageFormat("png", "image/png")
            "image/jpeg" -> CustomDesignImageFormat("jpg", "image/jpeg")
            "image/webp" -> CustomDesignImageFormat("webp", "image/webp")
            else -> null
        }

        fun fromPair(extension: String, mimeType: String): CustomDesignImageFormat? {
            val normalizedExtension = extension.lowercase(Locale.ROOT)
            val normalizedMimeType = mimeType.lowercase(Locale.ROOT)
            return fromMimeType(normalizedMimeType)
                ?.takeIf { it.extension == normalizedExtension }
        }
    }
}

private fun ByteArray.toHexStringLowercase(): String = joinToString("") { byte ->
    "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
}
