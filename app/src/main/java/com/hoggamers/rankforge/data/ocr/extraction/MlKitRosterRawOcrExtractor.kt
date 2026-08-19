package com.hoggamers.rankforge.data.ocr.extraction

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImage
import com.hoggamers.rankforge.domain.ocr.extraction.DefaultRosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrEngine
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrInputException
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionInput
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class MlKitRosterRawOcrEngineImpl @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : RosterRawOcrEngine {
    override suspend fun recognize(input: RosterRawOcrRegionInput): RawOcrEngineOutput =
        withContext(Dispatchers.Default) {
            val sourceBitmap = (input.croppedPanelImage as? AndroidBitmapOcrImage)?.bitmap
            val rect = input.pixelRect
            val rectIsWithin = sourceBitmap
                ?.takeIf { !it.isRecycled }
                ?.let { rect.isWithin(it) }
                ?: false
            val source = sourceBitmap?.takeIf { !it.isRecycled }
                ?: throw RosterRawOcrInputException()
            if (!rectIsWithin) throw RosterRawOcrInputException()

            val regionBitmap = try {
                Bitmap.createBitmap(source, rect.x, rect.y, rect.width, rect.height)
            } catch (_: IllegalArgumentException) {
                throw RosterRawOcrInputException()
            }
            val recognizer = try {
                recognizerFactory.create()
            } catch (throwable: Throwable) {
                recycleBitmap(regionBitmap)
                throw throwable
            }
            try {
                val authoritativeText = recognizer
                    .process(InputImage.fromBitmap(regionBitmap, 0))
                    .awaitText()
                authoritativeText.toRawOutput()
            } catch (throwable: Throwable) {
                throw throwable
            } finally {
                recognizer.close()
                recycleBitmap(regionBitmap)
            }
        }

    private fun com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect.isWithin(
        bitmap: Bitmap,
    ): Boolean = x >= 0 &&
        y >= 0 &&
        width > 0 &&
        height > 0 &&
        x.toLong() + width <= bitmap.width &&
        y.toLong() + height <= bitmap.height

    private fun recycleBitmap(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

}

@Singleton
class MlKitRosterRawOcrExtractor @Inject constructor(
    engine: MlKitRosterRawOcrEngineImpl,
) : RosterRawOcrExtractor {
    private val delegate = DefaultRosterRawOcrExtractor(engine)

    override suspend fun extract(
        input: RosterRawOcrExtractionInput,
    ): List<RosterRawOcrExtractionResult> = delegate.extract(input)
}

private fun Text.toRawOutput() = RawOcrEngineOutput(text, textBlocks.map { block ->
    RawOcrBlock(
        text = block.text,
        geometry = geometry(block.boundingBox, block.cornerPoints),
        recognizedLanguage = block.recognizedLanguage,
        confidence = RawOcrConfidence.Unavailable,
        lines = block.lines.map { line ->
            RawOcrLine(
                text = line.text,
                geometry = geometry(line.boundingBox, line.cornerPoints),
                recognizedLanguage = line.recognizedLanguage,
                confidence = RawOcrConfidence.Unavailable,
                elements = line.elements.map { element ->
                    RawOcrElement(
                        text = element.text,
                        geometry = geometry(element.boundingBox, element.cornerPoints),
                        recognizedLanguage = element.recognizedLanguage,
                        confidence = RawOcrConfidence.Unavailable,
                    )
                },
            )
        },
    )
})

private fun geometry(
    boundingBox: Rect?,
    cornerPoints: Array<Point>?,
): RawOcrGeometry? = if (boundingBox == null && cornerPoints == null) {
    null
} else {
    RawOcrGeometry(
        boundingBox = boundingBox?.let { RawOcrBoundingBox(it.left, it.top, it.right, it.bottom) },
        cornerPoints = cornerPoints?.map { RawOcrPoint(it.x, it.y) },
    )
}

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { text ->
        if (continuation.isActive) continuation.resume(text)
    }
    addOnFailureListener { throwable ->
        if (continuation.isActive) continuation.resumeWithException(throwable)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
