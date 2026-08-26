package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
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
class AndroidMatchResultPositionPaddleNumericVerifierTest {
    @Test
    fun matchingThreeVariantsVerifyThree() = runTest {
        withBitmap { verifier(run("3"), run("3"), run("3")).verify(it, bounds()) }
            .assertVerified(3)
    }

    @Test
    fun missingOriginalAndTwoZeroVariantsVerifyZero() = runTest {
        withBitmap {
            verifier(emptyRun(), run("0"), run("0")).verify(it, bounds())
        }.assertVerified(0)
    }

    @Test
    fun letterOAndZerosVerifyZero() = runTest {
        withBitmap { verifier(run("O"), run("0"), run("0")).verify(it, bounds()) }
            .assertVerified(0)
    }

    @Test
    fun majorityWinsButOneAgainstOneIsConflict() = runTest {
        withBitmap { verifier(run("3"), run("8"), run("3")).verify(it, bounds()) }
            .assertVerified(3)
        withBitmap { verifier(run("3"), run("8"), emptyRun()).verify(it, bounds()) }
            .assertTrueConflict()
    }

    @Test
    fun oneOrNonnumericCandidateIsUnresolved() = runTest {
        withBitmap { verifier(run("7"), emptyRun(), emptyRun()).verify(it, bounds()) }
            .assertTrueUnresolved()
        withBitmap { verifier(run("PLAYER7"), emptyRun(), emptyRun()).verify(it, bounds()) }
            .assertTrueUnresolved()
    }

    @Test
    fun malformedOrRecycledSourceIsUnresolved() = runTest {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        val result = verifier(run("7"), run("7"), run("7")).verify(bitmap, bounds())
        result.assertTrueUnresolved()
    }

    @Test
    fun cancellationPropagates() = runTest {
        withBitmap { bitmap ->
            var propagated = false
            try {
                AndroidMatchResultPositionPaddleNumericVerifier(
                    object : MatchResultPositionPaddleOcrEngineProvider {
                        override suspend fun getOrCreate(): MatchResultPositionPaddleOcrEngine =
                            object : MatchResultPositionPaddleOcrEngine {
                                override suspend fun recognize(bitmap: Bitmap): OCRRunResult =
                                    throw CancellationException("cancelled")
                            }
                    },
                ).verify(bitmap, bounds())
            } catch (_: CancellationException) {
                propagated = true
            }
            assertTrue(propagated)
        }
    }

    private fun verifier(vararg runs: OCRRunResult): AndroidMatchResultPositionPaddleNumericVerifier =
        AndroidMatchResultPositionPaddleNumericVerifier(
            object : MatchResultPositionPaddleOcrEngineProvider {
                override suspend fun getOrCreate(): MatchResultPositionPaddleOcrEngine =
                    object : MatchResultPositionPaddleOcrEngine {
                        var index = 0
                        override suspend fun recognize(bitmap: Bitmap): OCRRunResult = runs[index++]
                    }
            },
        )

    private suspend fun <T> withBitmap(block: suspend (Bitmap) -> T): T {
        val bitmap = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)
        try {
            return block(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun bounds() = OcrPixelCropRect(10, 10, 30, 30)

    private fun run(text: String) = OCRRunResult(
        results = listOf(
            OCRResult(
                box = OCRBox(listOf(PointF(1f, 1f), PointF(18f, 1f), PointF(18f, 18f), PointF(1f, 18f))),
                text = text,
                confidence = 0.9f,
            ),
        ),
        detectionTimeMs = 0,
        recognitionTimeMs = 0,
        totalTimeMs = 0,
        lineCount = 1,
    )

    private fun emptyRun() = OCRRunResult(emptyList(), 0, 0, 0, 0)

    private fun MatchResultNumericVerification.assertVerified(expected: Int) {
        assertTrue(this is MatchResultNumericVerification.Verified)
        assertEquals(expected, (this as MatchResultNumericVerification.Verified).value)
    }

    private fun MatchResultNumericVerification.assertTrueConflict() {
        assertTrue(this is MatchResultNumericVerification.Conflict)
    }

    private fun MatchResultNumericVerification.assertTrueUnresolved() {
        assertTrue(this is MatchResultNumericVerification.Unresolved)
    }
}
