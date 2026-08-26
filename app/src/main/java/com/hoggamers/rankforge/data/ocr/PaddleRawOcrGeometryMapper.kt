package com.hoggamers.rankforge.data.ocr

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.model.OCRRunResult
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Maps PP-OCR regions to the generic raw OCR hierarchy without adding semantics. */
object PaddleRawOcrGeometryMapper {
    fun map(
        runResult: OCRRunResult,
        cropWidth: Int,
        cropHeight: Int,
    ): List<RawOcrBlock> = map(runResult.results, cropWidth, cropHeight)

    fun map(
        results: List<OCRResult>,
        cropWidth: Int,
        cropHeight: Int,
    ): List<RawOcrBlock> {
        if (cropWidth <= 0 || cropHeight <= 0) return emptyList()

        val lines = results.mapIndexedNotNull { index, result ->
            result.toMappedLineOrNull(index, cropWidth, cropHeight)
        }.sortedWith(
            compareBy<MappedPaddleResult>(
                { it.boundingBox.top },
                { it.centerY },
                { it.boundingBox.left },
                { it.centerX },
                { it.index },
            ),
        ).map { mapped ->
            val geometry = RawOcrGeometry(
                boundingBox = mapped.boundingBox,
                cornerPoints = mapped.points,
            )
            RawOcrLine(
                text = mapped.text,
                geometry = geometry,
                recognizedLanguage = null,
                confidence = RawOcrConfidence.Available(mapped.confidence),
                elements = listOf(
                    RawOcrElement(
                        text = mapped.text,
                        geometry = geometry,
                        recognizedLanguage = null,
                        confidence = RawOcrConfidence.Available(mapped.confidence),
                        symbols = emptyList(),
                    ),
                ),
            )
        }

        return if (lines.isEmpty()) {
            emptyList()
        } else {
            listOf(
                RawOcrBlock(
                    text = lines.joinToString("\n", transform = RawOcrLine::text),
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    lines = lines,
                ),
            )
        }
    }

    private fun OCRResult.toMappedLineOrNull(
        index: Int,
        cropWidth: Int,
        cropHeight: Int,
    ): MappedPaddleResult? {
        val sourcePoints = box.points
        if (sourcePoints.size != 4 || sourcePoints.any { !it.x.isFinite() || !it.y.isFinite() }) {
            return null
        }

        val normalizedPoints = sourcePoints.map { point ->
            RawOcrPoint(
                x = point.x.coerceIn(0f, cropWidth.toFloat()).roundToInt(),
                y = point.y.coerceIn(0f, cropHeight.toFloat()).roundToInt(),
            )
        }
        val minX = sourcePoints.minOf { it.x }.coerceIn(0f, cropWidth.toFloat())
        val minY = sourcePoints.minOf { it.y }.coerceIn(0f, cropHeight.toFloat())
        val maxX = sourcePoints.maxOf { it.x }.coerceIn(0f, cropWidth.toFloat())
        val maxY = sourcePoints.maxOf { it.y }.coerceIn(0f, cropHeight.toFloat())
        val boundingBox = RawOcrBoundingBox(
            left = floor(minX.toDouble()).toInt(),
            top = floor(minY.toDouble()).toInt(),
            right = ceil(maxX.toDouble()).toInt(),
            bottom = ceil(maxY.toDouble()).toInt(),
        )
        if (boundingBox.left >= boundingBox.right || boundingBox.top >= boundingBox.bottom) {
            return null
        }

        return MappedPaddleResult(
            index = index,
            text = text,
            confidence = confidence,
            points = normalizedPoints,
            boundingBox = boundingBox,
        )
    }

    private data class MappedPaddleResult(
        val index: Int,
        val text: String,
        val confidence: Float,
        val points: List<RawOcrPoint>,
        val boundingBox: RawOcrBoundingBox,
    ) {
        val centerX: Double = (boundingBox.left + boundingBox.right) / 2.0
        val centerY: Double = (boundingBox.top + boundingBox.bottom) / 2.0
    }
}
