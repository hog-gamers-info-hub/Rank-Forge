package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview-only result-position crops. These images are never passed to the Result OCR pipeline. */
interface MatchResultPositionCropPreviewImage {
    fun release() = Unit
}

data class AndroidMatchResultPositionCropPreviewImage(
    val bitmap: Bitmap,
) : MatchResultPositionCropPreviewImage {
    override fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

data class MatchResultPositionCropPreview(
    val position: Int,
    val image: MatchResultPositionCropPreviewImage,
    val geometry: MatchResultPositionCrop? = null,
    val sourceScreenshotRole: MatchResultScreenshotRole? = null,
) {
    init {
        require(position in 1..12) { "Result position must be in 1..12." }
    }

    fun release() {
        image.release()
    }
}

enum class MatchResultPositionCropPreviewUnavailableReason {
    NOT_READY,
    SOURCE_UNAVAILABLE,
    GENERATION_FAILED,
    INCOMPLETE_CROPS,
}

sealed interface MatchResultPositionCropPreviewState {
    data object Loading : MatchResultPositionCropPreviewState

    data class Available(
        val crops: List<MatchResultPositionCropPreview>,
    ) : MatchResultPositionCropPreviewState {
        init {
            require(crops.map(MatchResultPositionCropPreview::position).distinct().size == crops.size) {
                "Result position crop previews must have unique positions."
            }
        }
    }

    data class Unavailable(
        val reason: MatchResultPositionCropPreviewUnavailableReason,
    ) : MatchResultPositionCropPreviewState
}

fun MatchResultPositionCropPreviewState.release() {
    (this as? MatchResultPositionCropPreviewState.Available)
        ?.crops
        ?.forEach(MatchResultPositionCropPreview::release)
}

internal fun releaseReplacedResultPositionCropPreviewStates(
    previous: Map<MatchResultScreenshotRole, MatchResultPositionCropPreviewState>,
    current: Map<MatchResultScreenshotRole, MatchResultPositionCropPreviewState>,
) {
    previous.forEach { (role, state) ->
        if (current[role] !== state) state.release()
    }
}

internal fun MatchResultPositionCropPreviewState.sortedCrops(): List<MatchResultPositionCropPreview> =
    (this as? MatchResultPositionCropPreviewState.Available)
        ?.crops
        ?.sortedBy(MatchResultPositionCropPreview::position)
        .orEmpty()

fun interface MatchResultPositionCropPreviewGenerator {
    suspend fun generate(
        localFile: File,
        confirmedCrop: OcrNormalizedCropRect,
        storedRole: MatchResultScreenshotRole,
        authoritativeCrops: List<MatchResultPositionCrop>,
    ): MatchResultPositionCropPreviewState
}

@Singleton
class AndroidMatchResultPositionCropPreviewGenerator @Inject constructor() :
    MatchResultPositionCropPreviewGenerator {
    override suspend fun generate(
        localFile: File,
        confirmedCrop: OcrNormalizedCropRect,
        storedRole: MatchResultScreenshotRole,
        authoritativeCrops: List<MatchResultPositionCrop>,
    ): MatchResultPositionCropPreviewState = withContext(Dispatchers.IO) {
        val source = decodeConfirmedCrop(localFile, confirmedCrop)
            ?: return@withContext MatchResultPositionCropPreviewState.Unavailable(
                MatchResultPositionCropPreviewUnavailableReason.SOURCE_UNAVAILABLE,
            )
        try {
            rasterizeResultPositionCropPreviews(
                source = source,
                sourceScreenshotRole = storedRole,
                authoritativeCrops = authoritativeCrops,
            )
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    internal fun decodeConfirmedCrop(
        localFile: File,
        crop: OcrNormalizedCropRect,
    ): Bitmap? = decodeConfirmedCropForRestoration(localFile, crop)
}

internal fun rasterizeResultPositionCropPreviews(
    source: Bitmap,
    sourceScreenshotRole: MatchResultScreenshotRole,
    authoritativeCrops: List<MatchResultPositionCrop>,
): MatchResultPositionCropPreviewState {
    if (authoritativeCrops.isEmpty()) {
        return MatchResultPositionCropPreviewState.Unavailable(
            MatchResultPositionCropPreviewUnavailableReason.INCOMPLETE_CROPS,
        )
    }
    val previews = mutableListOf<MatchResultPositionCropPreview>()
    return try {
        authoritativeCrops
            .sortedBy { it.position }
            .forEach { crop ->
                val extracted = Bitmap.createBitmap(
                    source,
                    crop.bounds.left,
                    crop.bounds.top,
                    crop.bounds.width,
                    crop.bounds.height,
                )
                val previewBitmap = try {
                    extracted.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    if (extracted !== source && !extracted.isRecycled) extracted.recycle()
                } ?: throw IllegalStateException("Unable to copy result position preview bitmap.")
                previews += MatchResultPositionCropPreview(
                    position = crop.position,
                    image = AndroidMatchResultPositionCropPreviewImage(previewBitmap),
                    geometry = crop,
                    sourceScreenshotRole = sourceScreenshotRole,
                )
            }
        if (previews.isEmpty()) {
            MatchResultPositionCropPreviewState.Unavailable(
                MatchResultPositionCropPreviewUnavailableReason.INCOMPLETE_CROPS,
            )
        } else {
            MatchResultPositionCropPreviewState.Available(previews)
        }
    } catch (_: Throwable) {
        previews.forEach(MatchResultPositionCropPreview::release)
        MatchResultPositionCropPreviewState.Unavailable(
            MatchResultPositionCropPreviewUnavailableReason.GENERATION_FAILED,
        )
    }
}

/** Decodes only the already-confirmed parent crop; no OCR or geometry detection is performed. */
internal fun decodeConfirmedCropForRestoration(
    localFile: File,
    crop: OcrNormalizedCropRect,
): Bitmap? {
    if (!localFile.isFile || !localFile.canRead() || localFile.length() <= 0L) return null
    val decoded = BitmapFactory.decodeFile(localFile.path) ?: return null
    return try {
        val dimensions = OcrImageDimensions.from(decoded.width, decoded.height) ?: return null
        val bounds = crop.toPixelRectOrNull(dimensions) ?: return null
        val extracted = Bitmap.createBitmap(
            decoded,
            bounds.left,
            bounds.top,
            bounds.width,
            bounds.height,
        )
        try {
            extracted.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            if (extracted !== decoded && !extracted.isRecycled) extracted.recycle()
        }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    } finally {
        if (!decoded.isRecycled) decoded.recycle()
    }
}

object NoOpMatchResultPositionCropPreviewGenerator : MatchResultPositionCropPreviewGenerator {
    override suspend fun generate(
        localFile: File,
        confirmedCrop: OcrNormalizedCropRect,
        storedRole: MatchResultScreenshotRole,
        authoritativeCrops: List<MatchResultPositionCrop>,
    ): MatchResultPositionCropPreviewState = MatchResultPositionCropPreviewState.Unavailable(
        MatchResultPositionCropPreviewUnavailableReason.NOT_READY,
    )
}
