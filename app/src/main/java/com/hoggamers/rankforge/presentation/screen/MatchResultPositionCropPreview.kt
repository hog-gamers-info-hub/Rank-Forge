package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultPositionPaddleOcrRecognizer
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultPositionCropGenerator
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionCropGenerationResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionPaddleOcrFailure
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionPaddleOcrEvidence
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionPaddleOcrResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
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
    // TEMPORARY Phase 2 semantic verification diagnostic. REMOVE BEFORE COMMIT.
    val role: MatchResultScreenshotRole? = null,
    // TEMPORARY Phase 2 semantic verification diagnostic. REMOVE BEFORE COMMIT.
    val allowUpperPositionElevenFallback: Boolean = false,
    // TEMPORARY Phase 2 semantic verification diagnostic. REMOVE BEFORE COMMIT.
    val temporaryPpEvidence: MatchResultPositionPaddleOcrEvidence? = null,
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
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
    ): MatchResultPositionCropPreviewState
}

@Singleton
class AndroidMatchResultPositionCropPreviewGenerator @Inject constructor(
    private val positionCropGenerator: AndroidMatchResultPositionCropGenerator,
    private val positionPaddleOcrRecognizer: AndroidMatchResultPositionPaddleOcrRecognizer,
) : MatchResultPositionCropPreviewGenerator {
    override suspend fun generate(
        localFile: File,
        confirmedCrop: OcrNormalizedCropRect,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
    ): MatchResultPositionCropPreviewState = withContext(Dispatchers.IO) {
        val source = decodeConfirmedCrop(localFile, confirmedCrop)
            ?: return@withContext MatchResultPositionCropPreviewState.Unavailable(
                MatchResultPositionCropPreviewUnavailableReason.SOURCE_UNAVAILABLE,
            )
        try {
            val generated = positionCropGenerator.generate(
                source = source,
                role = role,
                allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
            )
            // TEMPORARY Phase 2 PP raw segmentation diagnostic. REMOVE BEFORE COMMIT.
            val temporaryPpEvidence = generated.traceTemporaryPpRaw(role, positionPaddleOcrRecognizer)
            generated.toPreviewState(role, allowUpperPositionElevenFallback, temporaryPpEvidence)
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun decodeConfirmedCrop(
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

    private fun MatchResultPositionCropGenerationResult.toPreviewState(
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
        temporaryPpEvidence: Map<Int, MatchResultPositionPaddleOcrEvidence> = emptyMap(),
    ): MatchResultPositionCropPreviewState = when (this) {
        is MatchResultPositionCropGenerationResult.Generated -> {
            val previews = crops
                .sortedBy { it.geometry.position }
                .map { crop ->
                    MatchResultPositionCropPreview(
                        position = crop.geometry.position,
                        image = AndroidMatchResultPositionCropPreviewImage(crop.bitmap),
                        role = role,
                        allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
                        temporaryPpEvidence = temporaryPpEvidence[crop.geometry.position],
                    )
                }
            val positions = previews.map(MatchResultPositionCropPreview::position)
            val hasExpectedPositions = when (role) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                    positions == (1..10).toList() ||
                        (allowUpperPositionElevenFallback && positions == (1..11).toList())

                MatchResultScreenshotRole.MATCH_RESULT_LOWER -> positions == (11..12).toList()
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

// TEMPORARY Phase 2 PP raw segmentation diagnostic. REMOVE BEFORE COMMIT.
private const val RESULT_POSITION_PP_RAW_LOG_TAG = "RESULT_POSITION_PP_RAW"

// TEMPORARY Phase 2 PP raw segmentation diagnostic. REMOVE BEFORE COMMIT.
private suspend fun MatchResultPositionCropGenerationResult.traceTemporaryPpRaw(
    role: MatchResultScreenshotRole,
    recognizer: AndroidMatchResultPositionPaddleOcrRecognizer,
) : Map<Int, MatchResultPositionPaddleOcrEvidence> {
    val generated = this as? MatchResultPositionCropGenerationResult.Generated ?: return emptyMap()
    val evidenceByPosition = linkedMapOf<Int, MatchResultPositionPaddleOcrEvidence>()
    generated.crops
        .sortedBy { it.geometry.position }
        .forEach { crop ->
            val position = crop.geometry.position
            val dimensions = crop.bitmap.safeTemporaryPpDimensions()
            try {
                when (val result = recognizer.recognize(crop, role)) {
                    is MatchResultPositionPaddleOcrResult.Success -> {
                        val lines = result.evidence.blocks.flatMap { it.lines }
                        evidenceByPosition[position] = result.evidence
                        Log.i(
                            RESULT_POSITION_PP_RAW_LOG_TAG,
                            "role=$role position=$position crop=${dimensions.first}x${dimensions.second} " +
                                "resultCount=${lines.size} status=SUCCESS",
                        )
                        lines.forEachIndexed { index, line ->
                            logTemporaryPpLine(role, position, dimensions, index, line)
                        }
                    }

                    is MatchResultPositionPaddleOcrResult.Failed -> {
                        Log.i(
                            RESULT_POSITION_PP_RAW_LOG_TAG,
                            "role=$role position=$position crop=${dimensions.first}x${dimensions.second} " +
                                "resultCount=0 status=${result.reason.name}",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                Log.i(
                    RESULT_POSITION_PP_RAW_LOG_TAG,
                    "role=$role position=$position crop=${dimensions.first}x${dimensions.second} " +
                        "resultCount=0 status=${MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED.name}",
                )
            }
        }
    return evidenceByPosition
}

// TEMPORARY Phase 2 PP raw segmentation diagnostic. REMOVE BEFORE COMMIT.
private fun logTemporaryPpLine(
    role: MatchResultScreenshotRole,
    position: Int,
    dimensions: Pair<Int, Int>,
    index: Int,
    line: RawOcrLine,
) {
    val geometry = line.geometry ?: return
    val box = geometry.boundingBox ?: return
    val centerX = (box.left + box.right) / 2.0
    val centerY = (box.top + box.bottom) / 2.0
    val confidence = (line.confidence as? RawOcrConfidence.Available)
        ?.value
        ?.let { String.format(Locale.US, "%.4f", it) }
        ?: "na"
    val points = geometry.cornerPoints.orEmpty().joinToString(",", prefix = "[", postfix = "]") {
        "(${it.x},${it.y})"
    }
    Log.i(
        RESULT_POSITION_PP_RAW_LOG_TAG,
        "role=$role position=$position crop=${dimensions.first}x${dimensions.second} i=$index " +
            "text=\"${line.text.escapeTemporaryPpText()}\" confidence=$confidence " +
            "box=[${box.left},${box.top},${box.right},${box.bottom}] " +
            "center=[${String.format(Locale.US, "%.1f", centerX)},${String.format(Locale.US, "%.1f", centerY)}] " +
            "points=$points",
    )
}

// TEMPORARY Phase 2 PP raw segmentation diagnostic. REMOVE BEFORE COMMIT.
private fun String.escapeTemporaryPpText(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

// TEMPORARY Phase 2 PP raw segmentation diagnostic. REMOVE BEFORE COMMIT.
private fun Bitmap.safeTemporaryPpDimensions(): Pair<Int, Int> = try {
    if (isRecycled || width <= 0 || height <= 0) 0 to 0 else width to height
} catch (_: Throwable) {
    0 to 0
}

object NoOpMatchResultPositionCropPreviewGenerator : MatchResultPositionCropPreviewGenerator {
    override suspend fun generate(
        localFile: File,
        confirmedCrop: OcrNormalizedCropRect,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
    ): MatchResultPositionCropPreviewState = MatchResultPositionCropPreviewState.Unavailable(
        MatchResultPositionCropPreviewUnavailableReason.NOT_READY,
    )
}
