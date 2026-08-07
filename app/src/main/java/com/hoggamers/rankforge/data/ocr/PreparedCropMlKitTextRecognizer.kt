package com.hoggamers.rankforge.data.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed interface PreparedCropMlKitRecognitionResult {
    data class Recognized(
        val text: Text,
    ) : PreparedCropMlKitRecognitionResult

    data object InvalidBitmap : PreparedCropMlKitRecognitionResult
    data object RecognitionFailed : PreparedCropMlKitRecognitionResult
}

fun interface PreparedCropMlKitRecognizer {
    suspend fun recognize(bitmap: Bitmap): PreparedCropMlKitRecognitionResult
}

@Singleton
class PreparedCropMlKitTextRecognizer @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : PreparedCropMlKitRecognizer {
    override suspend fun recognize(bitmap: Bitmap): PreparedCropMlKitRecognitionResult =
        withContext(Dispatchers.Default) {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return@withContext PreparedCropMlKitRecognitionResult.InvalidBitmap
            }

            val inputImage = try {
                InputImage.fromBitmap(bitmap, 0)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: RuntimeException) {
                return@withContext PreparedCropMlKitRecognitionResult.InvalidBitmap
            }

            val recognizer = recognizerFactory.create()
            try {
                PreparedCropMlKitRecognitionResult.Recognized(
                    recognizer.process(inputImage).awaitPreparedCropText(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                PreparedCropMlKitRecognitionResult.RecognitionFailed
            } finally {
                recognizer.close()
            }
        }
}

private suspend fun Task<Text>.awaitPreparedCropText(): Text = suspendCancellableCoroutine { continuation ->
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
