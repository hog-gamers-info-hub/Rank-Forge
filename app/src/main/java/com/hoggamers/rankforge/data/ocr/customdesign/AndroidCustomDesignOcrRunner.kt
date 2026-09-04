package com.hoggamers.rankforge.data.ocr.customdesign

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrRunner
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrSource
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRawOcrDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class AndroidCustomDesignOcrRunner @Inject constructor(
    @ApplicationContext context: Context,
    private val recognizerFactory: MlKitTextRecognizerFactory,
) : CustomDesignOcrRunner {
    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument = try {
        withContext(Dispatchers.IO) {
            val sourceBitmap = decodeFullSource(source)
            try {
                val recognizer = recognizerFactory.create()
                try {
                    val text = recognizer
                        .process(InputImage.fromBitmap(sourceBitmap, 0))
                        .awaitText()
                    CustomDesignRawOcrDocument(
                        sourceWidth = source.sourceWidth,
                        sourceHeight = source.sourceHeight,
                        blocks = text.toRawOcrBlocks(),
                    )
                } finally {
                    recognizer.close()
                }
            } finally {
                if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        throw CustomDesignOcrInputException()
    } catch (_: IOException) {
        throw CustomDesignOcrInputException()
    } catch (_: RuntimeException) {
        throw CustomDesignOcrInputException()
    } catch (_: OutOfMemoryError) {
        throw CustomDesignOcrInputException()
    }

    private fun decodeFullSource(source: CustomDesignOcrSource): Bitmap {
        val uri = Uri.parse(source.imageReference)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = contentResolver.openInputStream(uri)
            ?: throw CustomDesignOcrInputException()
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw CustomDesignOcrInputException()
        }
        if (bounds.outWidth != source.sourceWidth || bounds.outHeight != source.sourceHeight) {
            throw CustomDesignOcrInputException()
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw CustomDesignOcrInputException()
        if (bitmap.width != source.sourceWidth || bitmap.height != source.sourceHeight) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw CustomDesignOcrInputException()
        }
        return bitmap
    }
}

class CustomDesignOcrInputException : Exception()

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
