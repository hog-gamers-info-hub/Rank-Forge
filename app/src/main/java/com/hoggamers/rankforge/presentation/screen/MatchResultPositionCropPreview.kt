package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultPositionCropGenerator
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultLowerProcessingFallback
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionCropGenerationResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionCropObservationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
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
        allowUpperPositionElevenFallback: Boolean,
        activeTeamCount: Int?,
    ): MatchResultPositionCropPreviewState
}

@Singleton
class AndroidMatchResultPositionCropPreviewGenerator @Inject constructor(
    private val positionCropGenerator: AndroidMatchResultPositionCropGenerator,
) : MatchResultPositionCropPreviewGenerator {
    private val lowerProcessingFallback = MatchResultLowerProcessingFallback()

    override suspend fun generate(
        localFile: File,
        confirmedCrop: OcrNormalizedCropRect,
        storedRole: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
        activeTeamCount: Int?,
    ): MatchResultPositionCropPreviewState = withContext(Dispatchers.IO) {
        val source = decodeConfirmedCrop(localFile, confirmedCrop)
            ?: return@withContext MatchResultPositionCropPreviewState.Unavailable(
                MatchResultPositionCropPreviewUnavailableReason.SOURCE_UNAVAILABLE,
            )
        try {
            // The stored role identifies the physical preview slot only. It must not
            // influence semantic geometry or expected positions.
            val observation = when (val result = positionCropGenerator.observe(source)) {
                is MatchResultPositionCropObservationResult.Observed -> result
                MatchResultPositionCropObservationResult.InvalidSource,
                MatchResultPositionCropObservationResult.OcrFailed,
                -> return@withContext MatchResultPositionCropPreviewState.Unavailable(
                    MatchResultPositionCropPreviewUnavailableReason.GENERATION_FAILED,
                )
            }
            // The stored slot is the canonical role for this preview path. Role selection for a
            // complete pair happens in the production pair runner before PP-OCR.
            val assignedRole = storedRole
            val allowAssignedUpperFallback = assignedRole == MatchResultScreenshotRole.MATCH_RESULT_UPPER &&
                allowUpperPositionElevenFallback
            val resolvedGeometry = when (assignedRole) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER -> positionCropGenerator.calculate(
                    evidence = observation.evidence,
                    role = assignedRole,
                    allowUpperPositionElevenFallback = allowAssignedUpperFallback,
                )

                MatchResultScreenshotRole.MATCH_RESULT_LOWER -> lowerProcessingFallback.recover(observation.evidence)
            }
            val generated = when (resolvedGeometry) {
                is MatchResultPositionCropCalculationResult.Unavailable ->
                    return@withContext MatchResultPositionCropPreviewState.Unavailable(
                        MatchResultPositionCropPreviewUnavailableReason.GENERATION_FAILED,
                    )
                is MatchResultPositionCropCalculationResult.Available ->
                    positionCropGenerator.generate(source, resolvedGeometry)
                null -> return@withContext MatchResultPositionCropPreviewState.Unavailable(
                    MatchResultPositionCropPreviewUnavailableReason.GENERATION_FAILED,
                )
            }
            generated.toPreviewState(
                semanticRole = assignedRole,
                allowUpperPositionElevenFallback = allowAssignedUpperFallback,
            )
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    internal fun decodeConfirmedCrop(
        localFile: File,
        crop: OcrNormalizedCropRect,
    ): Bitmap? = decodeConfirmedCropForRestoration(localFile, crop)

    private fun MatchResultPositionCropGenerationResult.toPreviewState(
        semanticRole: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
    ): MatchResultPositionCropPreviewState = when (this) {
        is MatchResultPositionCropGenerationResult.Generated -> {
            val previews = crops
                .sortedBy { it.geometry.position }
                .map { crop ->
                    MatchResultPositionCropPreview(
                        position = crop.geometry.position,
                        image = AndroidMatchResultPositionCropPreviewImage(crop.bitmap),
                        geometry = crop.geometry,
                        sourceScreenshotRole = semanticRole,
                    )
                }
            val positions = previews.map(MatchResultPositionCropPreview::position)
            val hasExpectedPositions = when (semanticRole) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                    positions == (1..10).toList() ||
                        (allowUpperPositionElevenFallback && positions == (1..11).toList())

                MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                    positions == listOf(11) || positions == listOf(11, 12)
            }
            if (hasExpectedPositions) {
                MatchResultPositionCropPreviewState.Available(previews)
            } else {
                previews.forEach(MatchResultPositionCropPreview::release)
                MatchResultPositionCropPreviewState.Unavailable(
                    MatchResultPositionCropPreviewUnavailableReason.INCOMPLETE_CROPS,
                )
            }
        }

        else -> MatchResultPositionCropPreviewState.Unavailable(
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
        allowUpperPositionElevenFallback: Boolean,
        activeTeamCount: Int?,
    ): MatchResultPositionCropPreviewState = MatchResultPositionCropPreviewState.Unavailable(
        MatchResultPositionCropPreviewUnavailableReason.NOT_READY,
    )
}
