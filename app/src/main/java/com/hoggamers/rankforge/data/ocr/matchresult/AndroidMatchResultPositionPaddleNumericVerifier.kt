package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import com.hoggamers.rankforge.data.ocr.PaddleRawOcrGeometryMapper
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericCandidate
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericConsensus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericCropVariant
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultFocusedKillNumericConsensus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultFocusedNumericField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionFocusedNumericCropLayout
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Runs conservative multi-view PP-OCR over a caller-owned numeric micro-crop. */
@Singleton
class AndroidMatchResultPositionPaddleNumericVerifier @Inject constructor(
    private val engineProvider: MatchResultPositionPaddleOcrEngineProvider,
) {
    suspend fun verify(
        source: Bitmap,
        role: MatchResultScreenshotRole,
        position: Int,
        field: MatchResultFocusedNumericField,
        row: MatchResultPositionRowCrop? = null,
    ): MatchResultNumericVerification {
        val bounds = MatchResultPositionFocusedNumericCropLayout.boundsOrNull(
            role = role,
            position = position,
            field = field,
            imageWidth = source.safeDimensions()?.first ?: 0,
            imageHeight = source.safeDimensions()?.second ?: 0,
            row = row,
        ) ?: return MatchResultNumericVerification.Unresolved(emptyList())
        return verify(source, bounds, NumericVerificationMode.STANDARD)
    }

    suspend fun verifyKillFallback(
        source: Bitmap,
        role: MatchResultScreenshotRole,
        position: Int,
        slot: Int,
        row: MatchResultPositionRowCrop,
    ): MatchResultNumericVerification {
        val field = when (slot) {
            1 -> MatchResultFocusedNumericField.KILL_SLOT_1
            2 -> MatchResultFocusedNumericField.KILL_SLOT_2
            3 -> MatchResultFocusedNumericField.KILL_SLOT_3
            4 -> MatchResultFocusedNumericField.KILL_SLOT_4
            else -> return MatchResultNumericVerification.Unresolved(emptyList())
        }
        val bounds = MatchResultPositionFocusedNumericCropLayout.boundsOrNull(
            role = role,
            position = position,
            field = field,
            imageWidth = source.safeDimensions()?.first ?: 0,
            imageHeight = source.safeDimensions()?.second ?: 0,
            row = row,
        ) ?: return MatchResultNumericVerification.Unresolved(emptyList())
        return verify(source, bounds, NumericVerificationMode.FOCUSED_KILL)
    }

    suspend fun verify(
        source: Bitmap,
        bounds: OcrPixelCropRect,
    ): MatchResultNumericVerification = verify(source, bounds, NumericVerificationMode.STANDARD)

    private suspend fun verify(
        source: Bitmap,
        bounds: OcrPixelCropRect,
        mode: NumericVerificationMode,
    ): MatchResultNumericVerification {
        val sourceDimensions = source.safeDimensions() ?: return unresolved()
        val cropBounds = bounds.clampTo(sourceDimensions.first, sourceDimensions.second)
            ?: return unresolved()
        val engine = try {
            engineProvider.getOrCreate()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unresolved()
        }

        val candidates = mutableListOf<MatchResultNumericCandidate>()
        NUMERIC_VARIANTS.forEach { variant ->
            val microCrop = try {
                source.createOwnedCrop(cropBounds)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (microCrop == null) {
                candidates += MatchResultNumericCandidate(variant.publicVariant, "", null, null)
                return@forEach
            }

            val candidateBitmap = try {
                if (variant.scale == 1) {
                    microCrop
                } else {
                    Bitmap.createScaledBitmap(
                        microCrop,
                        microCrop.width * variant.scale,
                        microCrop.height * variant.scale,
                        true,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }

            if (candidateBitmap == null) {
                if (!microCrop.isRecycled) microCrop.recycle()
                candidates += MatchResultNumericCandidate(variant.publicVariant, "", null, null)
                return@forEach
            }

            try {
                val runResult = try {
                    engine.recognize(candidateBitmap)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
                if (runResult == null) {
                    candidates += MatchResultNumericCandidate(variant.publicVariant, "", null, null)
                } else {
                    candidates += runResult.toNumericCandidate(
                        variant = variant,
                        width = candidateBitmap.width,
                        height = candidateBitmap.height,
                        mode = mode,
                    )
                }
            } finally {
                if (candidateBitmap !== microCrop && !candidateBitmap.isRecycled) candidateBitmap.recycle()
                if (!microCrop.isRecycled) microCrop.recycle()
            }
        }
        return when (mode) {
            NumericVerificationMode.STANDARD -> MatchResultNumericConsensus.resolve(candidates)
            NumericVerificationMode.FOCUSED_KILL -> MatchResultFocusedKillNumericConsensus.resolve(candidates)
        }
    }

    private fun unresolved() = MatchResultNumericVerification.Unresolved(emptyList())

    private companion object {
        val NUMERIC_VARIANTS = listOf(
            NumericVariant(MatchResultNumericCropVariant.ORIGINAL, 1),
            NumericVariant(MatchResultNumericCropVariant.UPSCALE_2X, 2),
            NumericVariant(MatchResultNumericCropVariant.UPSCALE_3X, 3),
        )
    }
}

private fun Bitmap.safeDimensions(): Pair<Int, Int>? = try {
    if (isRecycled || width <= 0 || height <= 0) null else width to height
} catch (_: Throwable) {
    null
}

private fun OcrPixelCropRect.clampTo(width: Int, height: Int): OcrPixelCropRect? {
    val left = left.coerceIn(0, width)
    val top = top.coerceIn(0, height)
    val right = right.coerceIn(0, width)
    val bottom = bottom.coerceIn(0, height)
    return if (right > left && bottom > top) {
        OcrPixelCropRect(left, top, right, bottom)
    } else {
        null
    }
}

private fun Bitmap.createOwnedCrop(bounds: OcrPixelCropRect): Bitmap {
    val extracted = Bitmap.createBitmap(this, bounds.left, bounds.top, bounds.width, bounds.height)
    return if (extracted === this) {
        extracted.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalStateException("Unable to copy numeric crop bitmap.")
    } else {
        extracted
    }
}

private fun com.paddle.ocr.model.OCRRunResult.toNumericCandidate(
    variant: NumericVariant,
    width: Int,
    height: Int,
    mode: NumericVerificationMode,
): MatchResultNumericCandidate {
    val lines = PaddleRawOcrGeometryMapper.map(this, width, height).flatMap { it.lines }
    val rawText = lines.joinToString("\n") { it.text.trim() }
    val parsed = lines.mapNotNull { line ->
        parseNumericToken(line.text, mode)?.let { line to it }
    }
    val value = parsed.singleOrNull()?.second?.takeIf { parsed.size == 1 }
    val usable = parsed.map { it.first }
    val confidence = usable.singleOrNull()?.let {
        (it.confidence as? RawOcrConfidence.Available)?.value
    }
    return MatchResultNumericCandidate(
        variant = variant.publicVariant,
        rawText = rawText,
        value = value,
        confidence = confidence,
    )
}

private fun parseNumericToken(text: String, mode: NumericVerificationMode): Int? = when (mode) {
    NumericVerificationMode.STANDARD -> text.trim()
        .takeIf { it.isNumericToken() }
        ?.normalizeNumericToken()
        ?.toIntOrNull()
    NumericVerificationMode.FOCUSED_KILL -> parseFocusedKillToken(text)
}

internal fun parseFocusedKillToken(text: String): Int? {
    val token = FOCUSED_KILL_TOKEN_PATTERN.matchEntire(text)?.groupValues?.get(1) ?: return null
    return if (token == "D" || token.equals("O", ignoreCase = true)) 0 else token.toIntOrNull()
}

private fun String.isNumericToken(): Boolean = isNotBlank() && all { it.isDigit() || it == 'O' || it == 'o' }

private fun String.normalizeNumericToken(): String = map { if (it == 'O' || it == 'o') '0' else it }.joinToString("")

private data class NumericVariant(
    val publicVariant: MatchResultNumericCropVariant,
    val scale: Int,
)

private enum class NumericVerificationMode {
    STANDARD,
    FOCUSED_KILL,
}

private val FOCUSED_KILL_TOKEN_PATTERN = Regex("^\\s*([0-9]+|[OoD])\\s*$")
