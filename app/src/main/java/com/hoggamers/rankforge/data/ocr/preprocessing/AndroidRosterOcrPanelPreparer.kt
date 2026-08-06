package com.hoggamers.rankforge.data.ocr.preprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationError
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrLocalRelativePath
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPreparedPanel
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProviderResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal fun interface RosterOcrInputStreamOpener {
    fun open(uri: Uri): InputStream?
}

@Singleton
class RoomRosterOcrSourceProvider @Inject constructor(
    private val metadataRepository: RosterScreenshotMetadataRepository,
) : RosterOcrSourceProvider {
    override suspend fun load(tournamentId: String): RosterOcrSourceProviderResult {
        if (tournamentId.isBlank()) {
            return RosterOcrSourceProviderResult.InvalidTournamentContext
        }

        val metadata = try {
            metadataRepository.observeByTournamentId(tournamentId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return RosterOcrSourceProviderResult.LoadingFailure
        }

        val unsupported = metadata.firstOrNull {
            RosterScreenshotPosition.fromIndex(it.rosterScreenshotIndex) == null
        }
        if (unsupported != null) {
            return RosterOcrSourceProviderResult.UnsupportedScreenshotPosition(
                unsupported.rosterScreenshotIndex,
            )
        }

        val duplicateIndices = metadata
            .groupingBy { it.rosterScreenshotIndex }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicateIndices.isNotEmpty()) {
            return RosterOcrSourceProviderResult.DuplicateScreenshotPositions(duplicateIndices)
        }

        val expectedIndices = RosterScreenshotPosition.entries.map { it.index }
        if (metadata.size != expectedIndices.size ||
            metadata.map { it.rosterScreenshotIndex }.toSet() != expectedIndices.toSet()
        ) {
            return RosterOcrSourceProviderResult.IncompleteScreenshotSet
        }

        val missingCrop = metadata.firstOrNull { item ->
            item.cropLeft == null ||
                item.cropTop == null ||
                item.cropRight == null ||
                item.cropBottom == null
        }
        if (missingCrop != null) {
            return RosterOcrSourceProviderResult.MissingCropMetadata(
                missingCrop.rosterScreenshotIndex,
            )
        }

        val sources = metadata.sortedBy { it.rosterScreenshotIndex }.map { item ->
            val screenshotPosition = RosterScreenshotPosition.fromIndex(item.rosterScreenshotIndex)
                ?: return RosterOcrSourceProviderResult.UnsupportedScreenshotPosition(
                    item.rosterScreenshotIndex,
                )
            RosterOcrScreenshotSource(
                tournamentId = item.tournamentId,
                rosterScreenshotIndex = item.rosterScreenshotIndex,
                screenshotPosition = screenshotPosition,
                localRelativePath = RosterOcrLocalRelativePath(item.localRelativePath),
                sourceWidth = item.width,
                sourceHeight = item.height,
                cropLeft = item.cropLeft!!,
                cropTop = item.cropTop!!,
                cropRight = item.cropRight!!,
                cropBottom = item.cropBottom!!,
            )
        }
        return RosterOcrSourceProviderResult.Loaded(sources)
    }
}

@Singleton
class AndroidRosterOcrPanelPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localImageStore: com.hoggamers.rankforge.presentation.screen.RosterScreenshotLocalImageStore,
) : RosterOcrPanelPreparer {
    internal var inputStreamOpener: RosterOcrInputStreamOpener =
        RosterOcrInputStreamOpener { uri -> context.contentResolver.openInputStream(uri) }

    override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            prepareOnIo(source)
        }

    private suspend fun prepareOnIo(
        source: RosterOcrScreenshotSource,
    ): RosterOcrPanelPreparationResult {
        val crop = normalizedCropOrNull(source)?.takeIf {
            OcrCropValidator.validate(it, OcrCropValidationProfiles.Roster) is OcrCropValidationResult.Valid
        }
            ?: return RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.INVALID_CROP,
            )

        val displayUri = try {
            localImageStore.displayUriOrNull(source.localRelativePath.value)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return RosterOcrPanelPreparationResult.Failed(
            RosterOcrPanelPreparationFailure.MISSING_LOCAL_ORIGINAL,
        )
        if (displayUri.isBlank()) {
            return RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.MISSING_LOCAL_ORIGINAL,
            )
        }

        val uri = try {
            Uri.parse(displayUri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE,
            )
        }

        currentCoroutineContext().ensureActive()
        return when (val bounds = readBounds(uri)) {
            BoundsResult.Unreadable -> RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE,
            )
            BoundsResult.Unsafe -> RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS,
            )
            is BoundsResult.Valid -> prepareDecoded(source, uri, crop, bounds.width, bounds.height)
        }
    }

    private fun readBounds(uri: Uri): BoundsResult {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            val stream = inputStreamOpener.open(uri)
                ?: return BoundsResult.Unreadable
            stream.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: OutOfMemoryError) {
            return BoundsResult.Unsafe
        } catch (_: Throwable) {
            return BoundsResult.Unreadable
        }

        val width = options.outWidth
        val height = options.outHeight
        if (width == -1 || height == -1) return BoundsResult.Unreadable
        if (width <= 0 || height <= 0) return BoundsResult.Unsafe
        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) return BoundsResult.Unsafe
        if (width.toLong() * height.toLong() > MAX_IMAGE_PIXELS) return BoundsResult.Unsafe
        return BoundsResult.Valid(width, height)
    }

    private suspend fun prepareDecoded(
        source: RosterOcrScreenshotSource,
        uri: Uri,
        crop: OcrNormalizedCropRect,
        boundsWidth: Int,
        boundsHeight: Int,
    ): RosterOcrPanelPreparationResult {
        if (source.sourceWidth != boundsWidth || source.sourceHeight != boundsHeight) {
            return RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS,
            )
        }

        val pixelCrop = when (
            val result = OcrCropValidator.validate(
                crop = crop,
                dimensions = OcrImageDimensions.from(boundsWidth, boundsHeight),
                profile = OcrCropValidationProfiles.Roster,
            )
        ) {
            is OcrCropValidationResult.Valid -> result.pixelCrop
            is OcrCropValidationResult.Invalid -> {
                val failure = when (result.error) {
                    OcrCropValidationError.INVALID_IMAGE_DIMENSIONS -> RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS
                    else -> RosterOcrPanelPreparationFailure.INVALID_CROP
                }
                return RosterOcrPanelPreparationResult.Failed(failure)
            }
        } ?: return RosterOcrPanelPreparationResult.Failed(
            RosterOcrPanelPreparationFailure.INVALID_CROP,
        )
        if (pixelCrop.width.toLong() * pixelCrop.height.toLong() > MAX_IMAGE_PIXELS) {
            return RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS,
            )
        }

        var decoded: Bitmap? = null
        var cropped: Bitmap? = null
        var handedOff = false
        return try {
            currentCoroutineContext().ensureActive()
            decoded = when (val decode = decodeSource(uri)) {
                DecodeResult.Unreadable -> return RosterOcrPanelPreparationResult.Failed(
                    RosterOcrPanelPreparationFailure.UNREADABLE_OR_DECODE_FAILURE,
                )
                DecodeResult.Unsafe -> return RosterOcrPanelPreparationResult.Failed(
                    RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS,
                )
                is DecodeResult.Decoded -> decode.bitmap
            }
            currentCoroutineContext().ensureActive()
            if (decoded!!.width != boundsWidth || decoded!!.height != boundsHeight) {
                return RosterOcrPanelPreparationResult.Failed(
                    RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS,
                )
            }

            cropped = try {
                Bitmap.createBitmap(
                    decoded!!,
                    pixelCrop.left,
                    pixelCrop.top,
                    pixelCrop.width,
                    pixelCrop.height,
                )
            } catch (_: OutOfMemoryError) {
                return RosterOcrPanelPreparationResult.Failed(
                    RosterOcrPanelPreparationFailure.CROP_FAILURE,
                )
            } catch (_: IllegalArgumentException) {
                return RosterOcrPanelPreparationResult.Failed(
                    RosterOcrPanelPreparationFailure.CROP_FAILURE,
                )
            } catch (_: RuntimeException) {
                return RosterOcrPanelPreparationResult.Failed(
                    RosterOcrPanelPreparationFailure.UNKNOWN,
                )
            }
            currentCoroutineContext().ensureActive()

            if (cropped !== decoded) {
                recycle(decoded)
                decoded = null
            }
            val ownedBitmap = cropped ?: return RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.CROP_FAILURE,
            )
            cropped = null
            decoded = null
            handedOff = true
            RosterOcrPanelPreparationResult.Prepared(
                AndroidRosterOcrPreparedPanel(ownedBitmap, source.screenshotPosition),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: OutOfMemoryError) {
            RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNSAFE_DIMENSIONS,
            )
        } catch (_: Throwable) {
            RosterOcrPanelPreparationResult.Failed(
                RosterOcrPanelPreparationFailure.UNKNOWN,
            )
        } finally {
            if (!handedOff) {
                recycle(cropped)
                recycle(decoded)
            }
        }
    }

    private suspend fun decodeSource(uri: Uri): DecodeResult {
        var decoded: Bitmap? = null
        var handedOff = false
        return try {
            val stream = inputStreamOpener.open(uri) ?: return DecodeResult.Unreadable
            decoded = stream.use { BitmapFactory.decodeStream(it) }
                ?: return DecodeResult.Unreadable
            currentCoroutineContext().ensureActive()
            handedOff = true
            DecodeResult.Decoded(decoded!!)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: OutOfMemoryError) {
            DecodeResult.Unsafe
        } catch (_: FileNotFoundException) {
            DecodeResult.Unreadable
        } catch (_: IOException) {
            DecodeResult.Unreadable
        } catch (_: SecurityException) {
            DecodeResult.Unreadable
        } catch (_: RuntimeException) {
            DecodeResult.Unreadable
        } finally {
            if (!handedOff) recycle(decoded)
        }
    }

    private fun normalizedCropOrNull(source: RosterOcrScreenshotSource): OcrNormalizedCropRect =
        OcrNormalizedCropRect(
            source.cropLeft,
            source.cropTop,
            source.cropRight,
            source.cropBottom,
        )

    private fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    private sealed interface BoundsResult {
        data class Valid(val width: Int, val height: Int) : BoundsResult
        data object Unreadable : BoundsResult
        data object Unsafe : BoundsResult
    }

    private sealed interface DecodeResult {
        data class Decoded(val bitmap: Bitmap) : DecodeResult
        data object Unreadable : DecodeResult
        data object Unsafe : DecodeResult
    }

    private class AndroidRosterOcrPreparedPanel(
        bitmap: Bitmap,
        screenshotPosition: RosterScreenshotPosition,
    ) : RosterOcrPreparedPanel {
        private val ownedBitmap = bitmap

        override val croppedPanelImage: OcrPreprocessingImage = AndroidBitmapOcrImage(ownedBitmap)
        override val croppedPanelInput = CroppedRosterPanelInput(
            screenshotPosition = screenshotPosition,
            isPreparedRosterCrop = true,
            imageWidth = ownedBitmap.width,
            imageHeight = ownedBitmap.height,
        )

        private var released = false

        override fun release() {
            if (released) return
            released = true
            recycle(ownedBitmap)
        }

        private fun recycle(bitmap: Bitmap) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_IMAGE_DIMENSION = 8_192
        const val MAX_IMAGE_PIXELS = 16_000_000L
    }
}
