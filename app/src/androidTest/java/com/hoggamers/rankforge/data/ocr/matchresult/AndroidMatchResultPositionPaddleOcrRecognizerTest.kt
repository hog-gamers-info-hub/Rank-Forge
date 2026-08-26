package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.model.OCRRunResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMatchResultPositionPaddleOcrRecognizerTest {
    @Test
    fun preservesPositionMetadataAndRawPpEvidence() = runTest {
        val bitmap = Bitmap.createBitmap(491, 82, Bitmap.Config.ARGB_8888)
        try {
            val recognizer = recognizer { sampleRunResult() }
            val result = recognizer.recognize(crop(bitmap, position = 7), MatchResultScreenshotRole.MATCH_RESULT_UPPER)

            val success = result as MatchResultPositionPaddleOcrResult.Success
            assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER, success.evidence.role)
            assertEquals(7, success.evidence.position)
            assertEquals(MatchResultPositionColumn.RIGHT, success.evidence.column)
            assertEquals(491, success.evidence.cropWidth)
            assertEquals(82, success.evidence.cropHeight)
            assertEquals("player", success.evidence.blocks.single().lines.single().text)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun invalidRecycledBitmapIsReportedWithoutCallingEngine() = runTest {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        var called = false
        val recognizer = recognizer {
            called = true
            sampleRunResult()
        }

        val result = recognizer.recognize(crop(bitmap, 11), MatchResultScreenshotRole.MATCH_RESULT_UPPER)

        assertEquals(
            MatchResultPositionPaddleOcrFailure.INVALID_SOURCE,
            (result as MatchResultPositionPaddleOcrResult.Failed).reason,
        )
        assertTrue(!called)
    }

    @Test
    fun providerAndRecognitionFailuresAreDistinct() = runTest {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            val initializationFailure = AndroidMatchResultPositionPaddleOcrRecognizer(
                object : MatchResultPositionPaddleOcrEngineProvider {
                    override suspend fun getOrCreate(): MatchResultPositionPaddleOcrEngine = error("init")
                },
            ).recognize(crop(bitmap, 7), MatchResultScreenshotRole.MATCH_RESULT_UPPER)
            assertEquals(
                MatchResultPositionPaddleOcrFailure.OCR_INITIALIZATION_FAILED,
                (initializationFailure as MatchResultPositionPaddleOcrResult.Failed).reason,
            )

            val recognitionFailure = recognizer { error("recognition") }
                .recognize(crop(bitmap, 7), MatchResultScreenshotRole.MATCH_RESULT_UPPER)
            assertEquals(
                MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED,
                (recognitionFailure as MatchResultPositionPaddleOcrResult.Failed).reason,
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun cancellationPropagates() = runTest {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            var propagated = false
            try {
                recognizer { throw CancellationException("cancelled") }
                    .recognize(crop(bitmap, 7), MatchResultScreenshotRole.MATCH_RESULT_LOWER)
            } catch (_: CancellationException) {
                propagated = true
            }
            assertTrue(propagated)
        } finally {
            bitmap.recycle()
        }
    }

    private fun recognizer(
        recognize: suspend () -> OCRRunResult,
    ) = AndroidMatchResultPositionPaddleOcrRecognizer(
        object : MatchResultPositionPaddleOcrEngineProvider {
            override suspend fun getOrCreate(): MatchResultPositionPaddleOcrEngine =
                object : MatchResultPositionPaddleOcrEngine {
                    override suspend fun recognize(bitmap: Bitmap): OCRRunResult = recognize()
                }
        },
    )

    private fun crop(bitmap: Bitmap, position: Int) = MatchResultPositionBitmapCrop(
        geometry = MatchResultPositionCrop(
            position = position,
            column = MatchResultPositionColumn.RIGHT,
            bounds = com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect(0, 0, bitmap.width, bitmap.height),
        ),
        bitmap = bitmap,
    )

    private fun sampleRunResult() = OCRRunResult(
        results = listOf(
            OCRResult(
                box = OCRBox(
                    listOf(
                        PointF(20f, 10f),
                        PointF(120f, 10f),
                        PointF(120f, 30f),
                        PointF(20f, 30f),
                    ),
                ),
                text = "player",
                confidence = 0.93f,
            ),
        ),
        detectionTimeMs = 0,
        recognitionTimeMs = 0,
        totalTimeMs = 0,
        lineCount = 1,
    )
}
