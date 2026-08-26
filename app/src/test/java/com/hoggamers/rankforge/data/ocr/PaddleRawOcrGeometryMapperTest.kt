package com.hoggamers.rankforge.data.ocr

import android.graphics.PointF
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaddleRawOcrGeometryMapperTest {
    @Test
    fun mapsPolygonTextConfidenceAndEmptySymbols() {
        val blocks = PaddleRawOcrGeometryMapper.map(
            results = listOf(result("exact text", 0.93f, points(20f, 10f, 120f, 30f))),
            cropWidth = 491,
            cropHeight = 82,
        )

        val line = blocks.single().lines.single()
        assertEquals("exact text", line.text)
        assertEquals("exact text", line.elements.single().text)
        assertEquals(
            com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox(20, 10, 120, 30),
            line.geometry?.boundingBox,
        )
        assertEquals(
            listOf(
                com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint(20, 10),
                com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint(120, 10),
                com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint(120, 30),
                com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint(20, 30),
            ),
            line.geometry?.cornerPoints,
        )
        assertEquals(RawOcrConfidence.Available(0.93f), line.confidence)
        assertEquals(emptyList<Any>(), line.elements.single().symbols)
        assertEquals(null, line.recognizedLanguage)
    }

    @Test
    fun floorsAndCeilsAngledPolygonBoundsAndClampsToCrop() {
        val blocks = PaddleRawOcrGeometryMapper.map(
            results = listOf(
                result(
                    text = "angled",
                    confidence = 0.5f,
                    points = listOf(
                        point(-2.4f, 9.2f),
                        point(120.2f, 8.7f),
                        point(493.1f, 31.6f),
                        point(20.8f, 30.4f),
                    ),
                ),
            ),
            cropWidth = 491,
            cropHeight = 82,
        )

        val geometry = blocks.single().lines.single().geometry
        assertEquals(
            com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox(0, 8, 491, 32),
            geometry?.boundingBox,
        )
        assertTrue(geometry?.cornerPoints.orEmpty().all { it.x in 0..491 && it.y in 0..82 })
    }

    @Test
    fun rejectsMalformedCollapsedAndFullyOutsideResultsIndividually() {
        val blocks = PaddleRawOcrGeometryMapper.map(
            results = listOf(
                result("valid", 0.8f, points(10f, 10f, 20f, 20f)),
                result("outside", 0.8f, points(500f, 10f, 510f, 20f)),
                result("collapsed", 0.8f, points(30f, 30f, 30f, 40f)),
                result("nan", 0.8f, listOf(point(Float.NaN, 1f), point(2f, 1f), point(2f, 2f), point(1f, 2f))),
                result("infinity", 0.8f, listOf(point(Float.POSITIVE_INFINITY, 1f), point(2f, 1f), point(2f, 2f), point(1f, 2f))),
            ),
            cropWidth = 491,
            cropHeight = 82,
        )

        assertEquals(1, blocks.single().lines.size)
        assertEquals("valid", blocks.single().lines.single().text)
    }

    @Test
    fun ordersResultsBySpatialGeometryThenOriginalIndex() {
        val blocks = PaddleRawOcrGeometryMapper.map(
            results = listOf(
                result("right", 0.8f, points(200f, 10f, 240f, 20f)),
                result("lower", 0.8f, points(10f, 30f, 50f, 40f)),
                result("left", 0.8f, points(100f, 10f, 140f, 20f)),
            ),
            cropWidth = 491,
            cropHeight = 82,
        )

        assertEquals(listOf("left", "right", "lower"), blocks.single().lines.map { it.text })
    }

    private fun result(text: String, confidence: Float, points: List<PointF>) =
        OCRResult(OCRBox(points), text, confidence)

    private fun points(left: Float, top: Float, right: Float, bottom: Float) = listOf(
        point(left, top),
        point(right, top),
        point(right, bottom),
        point(left, bottom),
    )

    private fun point(x: Float, y: Float) = PointF().apply {
        this.x = x
        this.y = y
    }
}
