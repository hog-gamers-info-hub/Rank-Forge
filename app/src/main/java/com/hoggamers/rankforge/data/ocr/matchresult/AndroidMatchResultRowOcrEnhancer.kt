package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationPrefixType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticTextParser
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class MatchResultRowOcrCandidate(val scale: Int) {
    SCALE_3X(3),
    SCALE_4X(4),
}

/** Creates owned, lossless-in-memory row candidates without changing Phase 1 geometry. */
class AndroidMatchResultRowOcrPreprocessor {
    fun create(source: Bitmap, scale: MatchResultRowOcrCandidate): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val width = source.width.toLong() * scale.scale
        val height = source.height.toLong() * scale.scale
        if (width !in 1..MAX_BITMAP_DIMENSION || height !in 1..MAX_BITMAP_DIMENSION) return null
        val pixelCount = width * height
        if (pixelCount > MAX_PIXEL_COUNT) return null

        val scaled = try {
            Bitmap.createScaledBitmap(source, width.toInt(), height.toInt(), true)
        } catch (_: Throwable) {
            return null
        }
        try {
            val pixels = IntArray(pixelCount.toInt())
            scaled.getPixels(pixels, 0, width.toInt(), 0, 0, width.toInt(), height.toInt())

            val grayscale = IntArray(pixels.size)
            for (index in pixels.indices) {
                val color = pixels[index]
                grayscale[index] = ((77 * ((color shr 16) and 0xff) +
                    150 * ((color shr 8) and 0xff) + 29 * (color and 0xff)) / 256)
            }

            // A restrained global contrast pass is deterministic and keeps the row text intact.
            val contrasted = IntArray(pixels.size)
            for (index in grayscale.indices) {
                contrasted[index] = ((grayscale[index] - 128) * CONTRAST + 128)
                    .roundToInt().coerceIn(0, 255)
            }

            // Small unsharp kernel; this is intentionally bounded to avoid ringing.
            val sharpened = IntArray(pixels.size)
            for (y in 0 until height.toInt()) {
                for (x in 0 until width.toInt()) {
                    val center = contrasted[y * width.toInt() + x] * 5
                    val left = contrasted[y * width.toInt() + x.coerceAtLeast(1) - 1]
                    val right = contrasted[y * width.toInt() + x.coerceAtMost(width.toInt() - 2) + 1]
                    val top = contrasted[(y.coerceAtLeast(1) - 1) * width.toInt() + x]
                    val bottom = contrasted[(y.coerceAtMost(height.toInt() - 2) + 1) * width.toInt() + x]
                    val neighbors = left + right + top + bottom
                    sharpened[y * width.toInt() + x] = (center - neighbors).coerceIn(0, 255)
                }
            }

            val histogram = IntArray(256)
            sharpened.forEach { histogram[it]++ }
            val threshold = otsuThreshold(histogram, sharpened.size)
            var output: Bitmap? = null
            try {
                output = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
                for (index in sharpened.indices) {
                    val value = if (sharpened[index] > threshold) 0xff else 0x00
                    pixels[index] = (0xff shl 24) or (value shl 16) or (value shl 8) or value
                }
                output.setPixels(pixels, 0, width.toInt(), 0, 0, width.toInt(), height.toInt())
                return output
            } catch (_: Throwable) {
                output?.takeUnless { it.isRecycled }?.recycle()
                return null
            }
        } catch (_: Throwable) {
            return null
        } finally {
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0L
        histogram.forEachIndexed { index, count -> sum += index.toLong() * count }
        var weightBackground = 0L
        var sumBackground = 0L
        var bestThreshold = 127
        var bestVariance = -1.0
        for (threshold in histogram.indices) {
            weightBackground += histogram[threshold]
            if (weightBackground == 0L) continue
            val weightForeground = total - weightBackground
            if (weightForeground <= 0) break
            sumBackground += threshold.toLong() * histogram[threshold]
            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sum - sumBackground).toDouble() / weightForeground
            val variance = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }
        return bestThreshold
    }

    private companion object {
        const val CONTRAST = 1.35
        const val MAX_BITMAP_DIMENSION = 16_384L
        const val MAX_PIXEL_COUNT = 64_000_000L
    }
}

data class MatchResultRowOcrCandidateEvaluation(
    val candidate: MatchResultRowOcrCandidate,
    val result: MatchResultPositionPaddleOcrResult,
    val resultCount: Int,
    val markerCount: Int,
    val explicitKillCount: Int,
    val nonEmptyCount: Int,
    val averageConfidence: Double,
)

data class MatchResultRowOcrCandidateSelection(
    val selected: MatchResultRowOcrCandidateEvaluation,
    val reason: String,
)

object MatchResultRowOcrCandidateSelector {
    fun evaluate(
        candidate: MatchResultRowOcrCandidate,
        result: MatchResultPositionPaddleOcrResult,
    ): MatchResultRowOcrCandidateEvaluation {
        val evidence = (result as? MatchResultPositionPaddleOcrResult.Success)?.evidence
        val lines = evidence?.blocks.orEmpty().flatMap { it.lines }
        val parsed = lines.map { MatchResultPositionSemanticTextParser.parse(it.text) }
        val confidence = lines.mapNotNull { (it.confidence as? RawOcrConfidence.Available)?.value?.toDouble() }
        return MatchResultRowOcrCandidateEvaluation(
            candidate = candidate,
            result = result,
            resultCount = lines.size,
            markerCount = parsed.count { it.markerMatched },
            explicitKillCount = parsed.count {
                it.markerMatched && it.prefixType in setOf(
                    MatchResultEliminationPrefixType.EXPLICIT_NUMERIC,
                    MatchResultEliminationPrefixType.O_NORMALIZED,
                )
            },
            nonEmptyCount = lines.count { it.text.isNotBlank() },
            averageConfidence = confidence.averageOrZero(),
        )
    }

    fun select(
        first: MatchResultRowOcrCandidateEvaluation,
        second: MatchResultRowOcrCandidateEvaluation,
    ): MatchResultRowOcrCandidateSelection? {
        val firstValid = first.result is MatchResultPositionPaddleOcrResult.Success && first.resultCount > 0
        val secondValid = second.result is MatchResultPositionPaddleOcrResult.Success && second.resultCount > 0
        if (!firstValid && !secondValid) return null
        if (firstValid && !secondValid) return MatchResultRowOcrCandidateSelection(first, "ONLY_3X_VALID")
        if (!firstValid && secondValid) return MatchResultRowOcrCandidateSelection(second, "ONLY_4X_VALID")
        val comparison = compareQuality(first, second)
        if (comparison > 0) return MatchResultRowOcrCandidateSelection(first, reasonFor(first, second))
        if (comparison < 0) return MatchResultRowOcrCandidateSelection(second, reasonFor(second, first))
        return MatchResultRowOcrCandidateSelection(
            if (first.candidate == MatchResultRowOcrCandidate.SCALE_3X) first else second,
            "TIE_PREFER_3X",
        )
    }

    private fun reasonFor(winner: MatchResultRowOcrCandidateEvaluation, loser: MatchResultRowOcrCandidateEvaluation): String = when {
        winner.markerCount != loser.markerCount -> "MORE_MARKERS"
        winner.explicitKillCount != loser.explicitKillCount -> "MORE_EXPLICIT_KILL_EVIDENCE"
        winner.nonEmptyCount != loser.nonEmptyCount -> "MORE_NON_EMPTY_RESULTS"
        else -> "HIGHER_AVERAGE_CONFIDENCE"
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun compareQuality(
        first: MatchResultRowOcrCandidateEvaluation,
        second: MatchResultRowOcrCandidateEvaluation,
    ): Int = compareValues(first.markerCount, second.markerCount).takeIf { it != 0 }
        ?: compareValues(first.explicitKillCount, second.explicitKillCount).takeIf { it != 0 }
        ?: compareValues(first.nonEmptyCount, second.nonEmptyCount).takeIf { it != 0 }
        ?: compareValues(first.averageConfidence, second.averageConfidence)
}

object MatchResultRowOcrGeometryMapper {
    fun mapBlocks(
        blocks: List<RawOcrBlock>,
        scale: MatchResultRowOcrCandidate,
        row: MatchResultPositionRowCrop,
        positionWidth: Int,
        positionHeight: Int,
    ): List<RawOcrBlock> = blocks.map { block ->
        block.copy(
            geometry = mapGeometry(block.geometry, scale.scale, row.bounds.top, positionWidth, positionHeight),
            lines = block.lines.map { line ->
                line.copy(
                    geometry = mapGeometry(line.geometry, scale.scale, row.bounds.top, positionWidth, positionHeight),
                    elements = line.elements.map { element ->
                        element.copy(
                            geometry = mapGeometry(element.geometry, scale.scale, row.bounds.top, positionWidth, positionHeight),
                            symbols = element.symbols.map { symbol ->
                                symbol.copy(geometry = mapGeometry(symbol.geometry, scale.scale, row.bounds.top, positionWidth, positionHeight))
                            },
                        )
                    },
                )
            },
        )
    }

    private fun mapGeometry(
        geometry: RawOcrGeometry?,
        scale: Int,
        rowTop: Int,
        width: Int,
        height: Int,
    ): RawOcrGeometry? {
        if (geometry == null) return null
        val box = geometry.boundingBox?.let { original ->
            val left = clampCoordinate(original.left.toDouble() / scale, 0, width, false)
            val top = clampCoordinate(rowTop + original.top.toDouble() / scale, 0, height, false)
            val right = clampCoordinate(original.right.toDouble() / scale, 0, width, true)
            val bottom = clampCoordinate(rowTop + original.bottom.toDouble() / scale, 0, height, true)
            RawOcrBoundingBox(left, top, right, bottom).takeIf { it.right > it.left && it.bottom > it.top }
        }
        val points = geometry.cornerPoints?.mapNotNull { point ->
            val x = (point.x.toDouble() / scale).takeIf(Double::isFinite) ?: return@mapNotNull null
            val y = (rowTop + point.y.toDouble() / scale).takeIf(Double::isFinite) ?: return@mapNotNull null
            RawOcrPoint(x.roundToInt().coerceIn(0, width), y.roundToInt().coerceIn(0, height))
        }?.takeIf { it.isNotEmpty() }
        return if (box == null && points == null) null else RawOcrGeometry(box, points)
    }

    private fun clampCoordinate(value: Double, min: Int, max: Int, upper: Boolean): Int {
        if (!value.isFinite()) return min
        val rounded = if (upper) ceil(value).toInt() else floor(value).toInt()
        return rounded.coerceIn(min, max)
    }
}
