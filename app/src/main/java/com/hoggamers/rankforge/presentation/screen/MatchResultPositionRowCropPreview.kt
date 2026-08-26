package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionPaddleVerificationDiagnostic
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultPositionRowCropGenerator
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionRowCropGenerationResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MatchResultPositionRowCropPreviewImage {
    fun release() = Unit
}

data class AndroidMatchResultPositionRowCropPreviewImage(
    val bitmap: Bitmap,
) : MatchResultPositionRowCropPreviewImage {
    override fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

data class MatchResultPositionRowCropPreview(
    val rowIndex: Int,
    val image: MatchResultPositionRowCropPreviewImage,
) {
    init { require(rowIndex in 1..2) { "Result position row must be 1 or 2." } }
    fun release() = image.release()
}

enum class MatchResultPositionRowCropPreviewUnavailableReason {
    NOT_READY,
    OCR_UNAVAILABLE,
    INCOMPLETE_CROPS,
}

sealed interface MatchResultPositionRowCropPreviewState {
    data object Loading : MatchResultPositionRowCropPreviewState
    data class Available(val rows: List<MatchResultPositionRowCropPreview>) : MatchResultPositionRowCropPreviewState {
        init { require(rows.map { it.rowIndex }.distinct().size == rows.size && rows.size in 1..2) }
    }
    data class Unavailable(val reason: MatchResultPositionRowCropPreviewUnavailableReason) : MatchResultPositionRowCropPreviewState
}

fun MatchResultPositionRowCropPreviewState.release() {
    (this as? MatchResultPositionRowCropPreviewState.Available)?.rows?.forEach(MatchResultPositionRowCropPreview::release)
}

fun interface MatchResultPositionRowCropPreviewGenerator {
    suspend fun generate(positionCrop: MatchResultPositionCropPreview): MatchResultPositionRowCropPreviewState
}

@Singleton
class AndroidMatchResultPositionRowCropPreviewGenerator @Inject constructor(
    private val generator: AndroidMatchResultPositionRowCropGenerator,
    private val semanticVerificationDiagnostic: MatchResultPositionPaddleVerificationDiagnostic,
) : MatchResultPositionRowCropPreviewGenerator {
    override suspend fun generate(positionCrop: MatchResultPositionCropPreview): MatchResultPositionRowCropPreviewState =
        withContext(Dispatchers.IO) {
            val image = positionCrop.image as? AndroidMatchResultPositionCropPreviewImage
                ?: return@withContext MatchResultPositionRowCropPreviewState.Unavailable(
                    MatchResultPositionRowCropPreviewUnavailableReason.OCR_UNAVAILABLE,
                )
            when (val result = generator.generate(image.bitmap, positionCrop.position)) {
                is MatchResultPositionRowCropGenerationResult.Generated -> {
                    // TEMPORARY Phase 2 semantic verification diagnostic. REMOVE BEFORE COMMIT.
                    try {
                        semanticVerificationDiagnostic.verify(positionCrop, result)
                    } catch (cancellation: java.util.concurrent.CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Diagnostics must never change the existing row-preview result.
                    }
                    val rows = result.crops.sortedBy { it.geometry.rowIndex }.map { crop ->
                        MatchResultPositionRowCropPreview(
                            rowIndex = crop.geometry.rowIndex,
                            image = AndroidMatchResultPositionRowCropPreviewImage(crop.bitmap),
                        )
                    }
                    if (rows.isEmpty() || rows.size > 2) {
                        rows.forEach(MatchResultPositionRowCropPreview::release)
                        MatchResultPositionRowCropPreviewState.Unavailable(
                            MatchResultPositionRowCropPreviewUnavailableReason.INCOMPLETE_CROPS,
                        )
                    } else {
                        MatchResultPositionRowCropPreviewState.Available(rows)
                    }
                }

                MatchResultPositionRowCropGenerationResult.Unavailable ->
                    MatchResultPositionRowCropPreviewState.Unavailable(
                        MatchResultPositionRowCropPreviewUnavailableReason.OCR_UNAVAILABLE,
                    )
            }
        }
}

object NoOpMatchResultPositionRowCropPreviewGenerator : MatchResultPositionRowCropPreviewGenerator {
    override suspend fun generate(positionCrop: MatchResultPositionCropPreview): MatchResultPositionRowCropPreviewState =
        MatchResultPositionRowCropPreviewState.Unavailable(
            MatchResultPositionRowCropPreviewUnavailableReason.NOT_READY,
        )
}

internal fun MatchResultPositionRowCropPreviewState.sortedRows(): List<MatchResultPositionRowCropPreview> =
    (this as? MatchResultPositionRowCropPreviewState.Available)?.rows?.sortedBy { it.rowIndex }.orEmpty()

internal fun releaseReplacedResultPositionRowCropPreviewStates(
    previous: Map<Int, MatchResultPositionRowCropPreviewState>,
    current: Map<Int, MatchResultPositionRowCropPreviewState>,
) {
    previous.forEach { (position, state) ->
        if (current[position] !== state) state.release()
    }
}
