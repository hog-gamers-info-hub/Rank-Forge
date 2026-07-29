package com.hoggamers.rankforge.data.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hoggamers.rankforge.domain.ocr.OcrImageInput
import com.hoggamers.rankforge.domain.ocr.OcrRecognitionFailure
import com.hoggamers.rankforge.domain.ocr.OcrRecognitionResult
import com.hoggamers.rankforge.domain.ocr.OcrTextRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

fun interface MlKitTextRecognizerFactory {
    fun create(): TextRecognizer
}

@Singleton
class DefaultMlKitTextRecognizerFactory @Inject constructor() : MlKitTextRecognizerFactory {
    override fun create(): TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS,
    )
}

interface MlKitOcrEngine {
    suspend fun recognize(input: OcrImageInput): String
}

@Singleton
class MlKitOcrEngineImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : MlKitOcrEngine {
    override suspend fun recognize(input: OcrImageInput): String = withContext(Dispatchers.IO) {
        val sourceUri = input.sourceUri.takeIf { it.isNotBlank() }
            ?: throw OcrInputException()
        val image = try {
            InputImage.fromFilePath(context, Uri.parse(sourceUri))
        } catch (throwable: Throwable) {
            throw OcrInputException(throwable)
        }
        val recognizer = recognizerFactory.create()
        try {
            recognizer.process(image).awaitText().text
        } finally {
            recognizer.close()
        }
    }
}

@Singleton
class MlKitOcrTextRecognizer @Inject constructor(
    private val engine: MlKitOcrEngine,
) : OcrTextRecognizer {
    override suspend fun recognize(input: OcrImageInput): OcrRecognitionResult = try {
        OcrRecognitionResult.RecognizedText(engine.recognize(input))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: OcrInputException) {
        OcrRecognitionResult.Failed(OcrRecognitionFailure.INPUT_UNAVAILABLE)
    } catch (_: Throwable) {
        OcrRecognitionResult.Failed(OcrRecognitionFailure.RECOGNITION_FAILED)
    }
}

class OcrInputException(
    cause: Throwable? = null,
) : Exception(cause)

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { text ->
        if (continuation.isActive) {
            continuation.resume(text)
        }
    }
    addOnFailureListener { throwable ->
        if (continuation.isActive) {
            continuation.resumeWithException(throwable)
        }
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
