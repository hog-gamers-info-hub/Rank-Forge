package com.hoggamers.rankforge.data.ocr

import com.hoggamers.rankforge.domain.ocr.OcrImageInput
import com.hoggamers.rankforge.domain.ocr.OcrRecognitionFailure
import com.hoggamers.rankforge.domain.ocr.OcrRecognitionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitOcrTextRecognizerTest {
    @Test
    fun returnsRecognizedTextWithoutParsingOrNormalization() = runTest {
        val recognizer = MlKitOcrTextRecognizer(
            FakeMlKitOcrEngine(result = "1  Team-One  12"),
        )

        val result = recognizer.recognize(OcrImageInput("content://picker/screenshot"))

        assertEquals(
            OcrRecognitionResult.RecognizedText("1  Team-One  12"),
            result,
        )
    }

    @Test
    fun propagatesUnavailableInputAsTypedFailure() = runTest {
        val recognizer = MlKitOcrTextRecognizer(
            FakeMlKitOcrEngine(failure = OcrInputException()),
        )

        val result = recognizer.recognize(OcrImageInput("content://picker/unavailable"))

        assertEquals(
            OcrRecognitionResult.Failed(OcrRecognitionFailure.INPUT_UNAVAILABLE),
            result,
        )
    }

    @Test
    fun propagatesRecognitionFailureAsTypedFailure() = runTest {
        val recognizer = MlKitOcrTextRecognizer(
            FakeMlKitOcrEngine(failure = IllegalStateException("recognition failed")),
        )

        val result = recognizer.recognize(OcrImageInput("content://picker/screenshot"))

        assertEquals(
            OcrRecognitionResult.Failed(OcrRecognitionFailure.RECOGNITION_FAILED),
            result,
        )
    }

    private class FakeMlKitOcrEngine(
        private val result: String? = null,
        private val failure: Throwable? = null,
    ) : MlKitOcrEngine {
        override suspend fun recognize(input: OcrImageInput): String {
            failure?.let { throw it }
            return requireNotNull(result)
        }
    }
}
