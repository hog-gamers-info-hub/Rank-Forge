package com.hoggamers.rankforge.data.ocr.extraction

import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImage
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionFailure
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrTextExtractor
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

interface MlKitRawOcrEngine { suspend fun recognize(candidate: OcrPreprocessingCandidate): RawOcrEngineOutput }

@Singleton
class MlKitRawOcrEngineImpl @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : MlKitRawOcrEngine {
    override suspend fun recognize(candidate: OcrPreprocessingCandidate): RawOcrEngineOutput = withContext(Dispatchers.Default) {
        val bitmap = (candidate.image as? AndroidBitmapOcrImage)?.bitmap
            ?.takeIf { !it.isRecycled } ?: throw RawOcrInputException()
        val recognizer = recognizerFactory.create()
        try { recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitText().toRawOutput() } finally { recognizer.close() }
    }
}

@Singleton
class MlKitRawOcrTextExtractor @Inject constructor(
    private val engine: MlKitRawOcrEngine,
) : RawOcrTextExtractor {
    override suspend fun extract(input: RawOcrExtractionInput): List<RawOcrExtractionResult> = input.candidates.map { candidate ->
        try {
            val output = engine.recognize(candidate)
            if (output.fullText.isEmpty()) RawOcrExtractionResult.Empty(candidate)
            else RawOcrExtractionResult.Extracted(candidate, output.fullText, output.blocks)
        } catch (cancellation: CancellationException) { throw cancellation
        } catch (_: RawOcrInputException) { RawOcrExtractionResult.Failed(candidate, RawOcrExtractionFailure.INPUT_UNAVAILABLE)
        } catch (_: Throwable) { RawOcrExtractionResult.Failed(candidate, RawOcrExtractionFailure.ENGINE_FAILED) }
    }
}

class RawOcrInputException : Exception()

private fun Text.toRawOutput() = RawOcrEngineOutput(text, textBlocks.map { block ->
    RawOcrBlock(block.text, geometry(block.boundingBox, block.cornerPoints), block.recognizedLanguage, RawOcrConfidence.Unavailable, block.lines.map { line ->
        RawOcrLine(line.text, geometry(line.boundingBox, line.cornerPoints), line.recognizedLanguage, RawOcrConfidence.Unavailable, line.elements.map { element ->
            RawOcrElement(element.text, geometry(element.boundingBox, element.cornerPoints), element.recognizedLanguage, RawOcrConfidence.Unavailable)
        })
    })
})

private fun geometry(boundingBox: Rect?, cornerPoints: Array<Point>?): RawOcrGeometry? =
    if (boundingBox == null && cornerPoints == null) null else RawOcrGeometry(boundingBox.toRawBox(), cornerPoints?.map(Point::toRawPoint))

private fun Rect?.toRawBox() = this?.let { RawOcrBoundingBox(left, top, right, bottom) }
private fun Point.toRawPoint() = RawOcrPoint(x, y)

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
