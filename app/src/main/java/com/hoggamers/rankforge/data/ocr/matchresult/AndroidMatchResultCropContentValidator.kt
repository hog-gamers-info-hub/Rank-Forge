package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitRecognitionResult
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitRecognizer
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.validation.MatchResultCropContentClassifier
import com.hoggamers.rankforge.domain.ocr.validation.MatchResultCropContentEvidenceEvaluator
import com.hoggamers.rankforge.domain.ocr.validation.OcrCropContentIndeterminateReason
import com.hoggamers.rankforge.domain.ocr.validation.OcrCropContentValidationResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMatchResultCropContentValidator @Inject constructor(
    private val recognizer: PreparedCropMlKitRecognizer,
    private val evaluator: MatchResultCropContentEvidenceEvaluator,
    private val classifier: MatchResultCropContentClassifier,
) : MatchResultCropContentValidator {
    override suspend fun validate(
        role: com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole,
        localFile: File,
        pixelCrop: com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect,
    ): OcrCropContentValidationResult {
        val original = try {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(localFile.absolutePath)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return OcrCropContentValidationResult.Indeterminate(
            OcrCropContentIndeterminateReason.IMAGE_DECODE_FAILED,
        )

        return try {
            val prepared = try {
                withContext(Dispatchers.IO) {
                    if (!isUsable(original) || !pixelCropFits(original, pixelCrop)) {
                        null
                    } else {
                        Bitmap.createBitmap(
                            original,
                            pixelCrop.left,
                            pixelCrop.top,
                            pixelCrop.width,
                            pixelCrop.height,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            } ?: return OcrCropContentValidationResult.Indeterminate(
                OcrCropContentIndeterminateReason.INVALID_PREPARED_BITMAP,
            )

            try {
                if (!isUsable(prepared)) {
                    return OcrCropContentValidationResult.Indeterminate(
                        OcrCropContentIndeterminateReason.INVALID_PREPARED_BITMAP,
                    )
                }
                when (val recognition = recognizer.recognize(prepared)) {
                    PreparedCropMlKitRecognitionResult.InvalidBitmap ->
                        OcrCropContentValidationResult.Indeterminate(
                            OcrCropContentIndeterminateReason.INVALID_PREPARED_BITMAP,
                        )

                    PreparedCropMlKitRecognitionResult.RecognitionFailed ->
                        OcrCropContentValidationResult.Indeterminate(
                            OcrCropContentIndeterminateReason.OCR_RECOGNITION_FAILED,
                        )

                    is PreparedCropMlKitRecognitionResult.Recognized -> {
                        val blocks = recognition.text.toRawOcrBlocks()
                        classifier.classify(
                            evaluator.evaluate(
                                role = role,
                                cropWidth = prepared.width,
                                cropHeight = prepared.height,
                                blocks = blocks,
                            ),
                        )
                    }
                }
            } finally {
                if (!prepared.isRecycled) prepared.recycle()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            OcrCropContentValidationResult.Indeterminate(
                OcrCropContentIndeterminateReason.VALIDATION_EXECUTION_FAILED,
            )
        } finally {
            if (!original.isRecycled) original.recycle()
        }
    }

    private fun isUsable(bitmap: Bitmap): Boolean =
        !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0

    private fun pixelCropFits(
        bitmap: Bitmap,
        crop: com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect,
    ): Boolean =
        crop.left >= 0 && crop.top >= 0 && crop.right <= bitmap.width && crop.bottom <= bitmap.height &&
            crop.width > 0 && crop.height > 0
}
