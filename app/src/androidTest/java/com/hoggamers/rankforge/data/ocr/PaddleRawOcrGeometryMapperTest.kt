package com.hoggamers.rankforge.data.ocr

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
        assertEquals(RawOcrBoundingBox(20, 10, 120, 30), line.geometry?.boundingBox)
        assertEquals(
            listOf(
                RawOcrPoint(20, 10),
                RawOcrPoint(120, 10),
                RawOcrPoint(120, 30),
                RawOcrPoint(20, 30),
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
                        PointF(-2.4f, 9.2f),
                        PointF(120.2f, 8.7f),
                        PointF(493.1f, 31.6f),
                        PointF(20.8f, 30.4f),
                    ),
                ),
            ),
            cropWidth = 491,
            cropHeight = 82,
        )

        val geometry = blocks.single().lines.single().geometry
        assertEquals(RawOcrBoundingBox(0, 8, 491, 32), geometry?.boundingBox)
        assertTrue(geometry?.cornerPoints.orEmpty().all { it.x in 0..491 && it.y in 0..82 })
    }

    @Test
    fun rejectsMalformedCollapsedAndFullyOutsideResultsIndividually() {
        val blocks = PaddleRawOcrGeometryMapper.map(
            results = listOf(
                result("valid", 0.8f, points(10f, 10f, 20f, 20f)),
                result("outside", 0.8f, points(500f, 10f, 510f, 20f)),
                result("collapsed", 0.8f, points(30f, 30f, 30f, 40f)),
                result("nan", 0.8f, listOf(PointF(Float.NaN, 1f), PointF(2f, 1f), PointF(2f, 2f), PointF(1f, 2f))),
                result("infinity", 0.8f, listOf(PointF(Float.POSITIVE_INFINITY, 1f), PointF(2f, 1f), PointF(2f, 2f), PointF(1f, 2f))),
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
        PointF(left, top),
        PointF(right, top),
        PointF(right, bottom),
        PointF(left, bottom),
    )
}
