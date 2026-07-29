package com.hoggamers.rankforge.presentation.screen

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImageValidationError {
    EMPTY_URI,
    NON_IMAGE_CONTENT,
    UNSUPPORTED_FORMAT,
    UNREADABLE_URI,
    DECODE_FAILED,
    INVALID_DIMENSIONS,
    IMAGE_TOO_LARGE,
}

sealed interface ImageCandidateValidationResult {
    data object Valid : ImageCandidateValidationResult

    data class Invalid(
        val error: ImageValidationError,
    ) : ImageCandidateValidationResult
}

sealed interface ImageCandidateReadResult {
    data class Metadata(
        val mimeType: String?,
        val width: Int,
        val height: Int,
    ) : ImageCandidateReadResult

    data object Unreadable : ImageCandidateReadResult
    data object DecodeFailure : ImageCandidateReadResult
}

fun interface ImageCandidateMetadataReader {
    suspend fun read(uri: String): ImageCandidateReadResult
}

class ImageCandidateValidator(
    private val metadataReader: ImageCandidateMetadataReader,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(AndroidImageCandidateMetadataReader(context.contentResolver))

    suspend fun validate(uri: String?): ImageCandidateValidationResult {
        if (uri.isNullOrBlank()) {
            return ImageCandidateValidationResult.Invalid(ImageValidationError.EMPTY_URI)
        }

        return when (val readResult = metadataReader.read(uri)) {
            ImageCandidateReadResult.Unreadable -> {
                ImageCandidateValidationResult.Invalid(ImageValidationError.UNREADABLE_URI)
            }
            ImageCandidateReadResult.DecodeFailure -> {
                ImageCandidateValidationResult.Invalid(ImageValidationError.DECODE_FAILED)
            }
            is ImageCandidateReadResult.Metadata -> validateMetadata(readResult)
        }
    }

    suspend fun readValidMetadata(uri: String?): ImageCandidateReadResult.Metadata? {
        if (uri.isNullOrBlank()) return null
        val metadata = metadataReader.read(uri) as? ImageCandidateReadResult.Metadata ?: return null
        return if (validateMetadata(metadata) == ImageCandidateValidationResult.Valid) {
            metadata
        } else {
            null
        }
    }

    private fun validateMetadata(
        metadata: ImageCandidateReadResult.Metadata,
    ): ImageCandidateValidationResult {
        val mimeType = metadata.mimeType?.lowercase(Locale.ROOT)
        if (mimeType == null || !mimeType.startsWith("image/")) {
            return ImageCandidateValidationResult.Invalid(ImageValidationError.NON_IMAGE_CONTENT)
        }
        if (mimeType !in SUPPORTED_MIME_TYPES) {
            return ImageCandidateValidationResult.Invalid(ImageValidationError.UNSUPPORTED_FORMAT)
        }
        if (metadata.width <= 0 || metadata.height <= 0) {
            return ImageCandidateValidationResult.Invalid(ImageValidationError.INVALID_DIMENSIONS)
        }
        if (
            metadata.width > MAX_IMAGE_DIMENSION ||
            metadata.height > MAX_IMAGE_DIMENSION ||
            metadata.width.toLong() * metadata.height > MAX_IMAGE_PIXELS
        ) {
            return ImageCandidateValidationResult.Invalid(ImageValidationError.IMAGE_TOO_LARGE)
        }
        return ImageCandidateValidationResult.Valid
    }

    private companion object {
        const val MAX_IMAGE_DIMENSION = 8_192
        const val MAX_IMAGE_PIXELS = 16_000_000L

        val SUPPORTED_MIME_TYPES = setOf(
            "image/png",
            "image/jpeg",
            "image/webp",
        )
    }
}

private class AndroidImageCandidateMetadataReader(
    private val contentResolver: ContentResolver,
) : ImageCandidateMetadataReader {
    override suspend fun read(uri: String): ImageCandidateReadResult = withContext(Dispatchers.IO) {
        try {
            val contentUri = Uri.parse(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val inputStream = contentResolver.openInputStream(contentUri)
                ?: return@withContext ImageCandidateReadResult.Unreadable
            inputStream.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth < 0 || options.outHeight < 0) {
                ImageCandidateReadResult.DecodeFailure
            } else {
                ImageCandidateReadResult.Metadata(
                    mimeType = contentResolver.getType(contentUri),
                    width = options.outWidth,
                    height = options.outHeight,
                )
            }
        } catch (_: SecurityException) {
            ImageCandidateReadResult.Unreadable
        } catch (_: FileNotFoundException) {
            ImageCandidateReadResult.Unreadable
        } catch (_: IOException) {
            ImageCandidateReadResult.Unreadable
        } catch (_: RuntimeException) {
            ImageCandidateReadResult.DecodeFailure
        }
    }
}
