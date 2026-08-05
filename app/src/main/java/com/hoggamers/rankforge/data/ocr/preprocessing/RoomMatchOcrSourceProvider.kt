package com.hoggamers.rankforge.data.ocr.preprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrPreparedSource
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrSourceProviderResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RoomMatchOcrSourceProvider @Inject constructor(
    private val metadataRepository: ScreenshotMetadataRepository,
    private val localImagePreserver: LocalImagePreserver,
) : MatchOcrSourceProvider {

    override suspend fun load(
        tournamentId: String,
        matchId: String,
    ): MatchOcrSourceProviderResult = withContext(Dispatchers.IO) {
        if (tournamentId.isBlank() || matchId.isBlank()) {
            return@withContext MatchOcrSourceProviderResult.InvalidContext
        }

        val metadata = try {
            metadataRepository.getByMatchId(matchId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return@withContext MatchOcrSourceProviderResult.LoadingFailure
        } ?: return@withContext MatchOcrSourceProviderResult.MetadataNotFound

        if (metadata.tournamentId != tournamentId) {
            return@withContext MatchOcrSourceProviderResult.TournamentMismatch
        }

        if (!isSupportedMetadata(
                mimeType = metadata.mimeType,
                width = metadata.width,
                height = metadata.height,
            )
        ) {
            return@withContext MatchOcrSourceProviderResult.UnsafeImage
        }

        val file = try {
            localImagePreserver.resolveRelativePath(metadata.localRelativePath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return@withContext MatchOcrSourceProviderResult.LocalFileMissing

        if (!file.isFile) {
            return@withContext MatchOcrSourceProviderResult.LocalFileMissing
        }

        val bounds = readBounds(file)
        when (bounds) {
            BoundsResult.Missing ->
                return@withContext MatchOcrSourceProviderResult.LocalFileMissing

            BoundsResult.Unreadable ->
                return@withContext MatchOcrSourceProviderResult.UnreadableImage

            BoundsResult.Unsafe ->
                return@withContext MatchOcrSourceProviderResult.UnsafeImage

            is BoundsResult.Valid -> {
                if (
                    bounds.width != metadata.width ||
                    bounds.height != metadata.height
                ) {
                    return@withContext MatchOcrSourceProviderResult.UnsafeImage
                }
            }
        }

        val bitmap = when (val decoded = decode(file)) {
            DecodeResult.Missing ->
                return@withContext MatchOcrSourceProviderResult.LocalFileMissing

            DecodeResult.Unreadable ->
                return@withContext MatchOcrSourceProviderResult.UnreadableImage

            DecodeResult.Unsafe ->
                return@withContext MatchOcrSourceProviderResult.UnsafeImage

            is DecodeResult.Decoded -> decoded.bitmap
        }

        if (
            bitmap.width != metadata.width ||
            bitmap.height != metadata.height
        ) {
            recycle(bitmap)
            return@withContext MatchOcrSourceProviderResult.UnsafeImage
        }

        MatchOcrSourceProviderResult.Loaded(
            AndroidMatchOcrPreparedSource(bitmap),
        )
    }

    private fun readBounds(file: File): BoundsResult {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight

            when {
                width == -1 || height == -1 ->
                    BoundsResult.Unreadable

                !isSafeDimensions(width, height) ->
                    BoundsResult.Unsafe

                else ->
                    BoundsResult.Valid(width, height)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: FileNotFoundException) {
            BoundsResult.Missing
        } catch (_: OutOfMemoryError) {
            BoundsResult.Unsafe
        } catch (_: IOException) {
            BoundsResult.Unreadable
        } catch (_: RuntimeException) {
            BoundsResult.Unreadable
        }
    }

    private fun decode(file: File): DecodeResult {
        var bitmap: Bitmap? = null
        var handedOff = false

        return try {
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return DecodeResult.Unreadable

            if (!isSafeDimensions(bitmap!!.width, bitmap!!.height)) {
                return DecodeResult.Unsafe
            }

            handedOff = true
            DecodeResult.Decoded(bitmap!!)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: FileNotFoundException) {
            DecodeResult.Missing
        } catch (_: OutOfMemoryError) {
            DecodeResult.Unsafe
        } catch (_: IOException) {
            DecodeResult.Unreadable
        } catch (_: RuntimeException) {
            DecodeResult.Unreadable
        } finally {
            if (!handedOff) {
                recycle(bitmap)
            }
        }
    }

    private fun isSupportedMetadata(
        mimeType: String,
        width: Int,
        height: Int,
    ): Boolean =
        mimeType.lowercase(Locale.ROOT) in SUPPORTED_MIME_TYPES &&
            isSafeDimensions(width, height)

    private fun isSafeDimensions(
        width: Int,
        height: Int,
    ): Boolean =
        width > 0 &&
            height > 0 &&
            width <= MAX_IMAGE_DIMENSION &&
            height <= MAX_IMAGE_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_IMAGE_PIXELS

    private fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private sealed interface BoundsResult {
        data class Valid(
            val width: Int,
            val height: Int,
        ) : BoundsResult

        data object Missing : BoundsResult
        data object Unreadable : BoundsResult
        data object Unsafe : BoundsResult
    }

    private sealed interface DecodeResult {
        data class Decoded(
            val bitmap: Bitmap,
        ) : DecodeResult

        data object Missing : DecodeResult
        data object Unreadable : DecodeResult
        data object Unsafe : DecodeResult
    }

    private class AndroidMatchOcrPreparedSource(
        private val bitmap: Bitmap,
    ) : MatchOcrPreparedSource {

        override val image: OcrPreprocessingImage =
            AndroidBitmapOcrImage(bitmap)

        private var released = false

        override fun release() {
            if (released) return
            released = true

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
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