package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitRecognitionResult
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitTextRecognizer
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RAW_MLKIT_TAG = "RF_MLKIT_RAW"

object MlKitScreenshot1RawDiagnostic {
    suspend fun run(
        identity: MatchResultScreenshotIdentity,
        assetRepository: MatchResultScreenshotAssetRepository,
        localImagePreserver: LocalImagePreserver,
    ) {
        if (identity.role != MatchResultScreenshotRole.MATCH_RESULT_UPPER) return

        val asset = assetRepository.getByIdentity(identity)
        if (asset == null) {
            Log.e(RAW_MLKIT_TAG, "ERROR asset unavailable")
            return
        }

        val crop = confirmedCropOrNull(
            left = asset.cropLeft,
            top = asset.cropTop,
            right = asset.cropRight,
            bottom = asset.cropBottom,
        )
        if (crop == null) {
            Log.e(RAW_MLKIT_TAG, "ERROR confirmed crop unavailable")
            return
        }

        val file = localImagePreserver.resolveRelativePath(asset.localRelativePath)
        if (file == null || !file.isFile || file.length() <= 0L) {
            Log.e(RAW_MLKIT_TAG, "ERROR local screenshot unavailable")
            return
        }

        val prepared = withContext(Dispatchers.IO) {
            val original = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@withContext null

            val dimensions = OcrImageDimensions.from(original.width, original.height)
            val pixelCrop = dimensions?.let(crop::toPixelRectOrNull)
            if (pixelCrop == null) {
                original.recycle()
                return@withContext null
            }

            val cropped = try {
                Bitmap.createBitmap(
                    original,
                    pixelCrop.left,
                    pixelCrop.top,
                    pixelCrop.width,
                    pixelCrop.height,
                )
            } catch (throwable: Throwable) {
                original.recycle()
                throw throwable
            }

            PreparedBitmap(
                original = original,
                cropped = cropped,
                cropLeft = pixelCrop.left,
                cropTop = pixelCrop.top,
                cropRight = pixelCrop.right,
                cropBottom = pixelCrop.bottom,
            )
        }

        if (prepared == null) {
            Log.e(RAW_MLKIT_TAG, "ERROR could not create confirmed Screenshot 1 crop")
            return
        }

        try {
            Log.i(RAW_MLKIT_TAG, "===== SCREENSHOT_1_MLKIT_BEGIN =====")
            Log.i(
                RAW_MLKIT_TAG,
                "ORIGINAL size=${prepared.original.width}x${prepared.original.height}",
            )
            Log.i(
                RAW_MLKIT_TAG,
                "CROP rect=(${prepared.cropLeft},${prepared.cropTop})-" +
                    "(${prepared.cropRight},${prepared.cropBottom}) " +
                    "size=${prepared.cropped.width}x${prepared.cropped.height}",
            )

            val recognizer = PreparedCropMlKitTextRecognizer(
                DefaultMlKitTextRecognizerFactory(),
            )
            when (val result = recognizer.recognize(prepared.cropped)) {
                is PreparedCropMlKitRecognitionResult.Recognized -> dumpText(result.text)
                PreparedCropMlKitRecognitionResult.InvalidBitmap ->
                    Log.e(RAW_MLKIT_TAG, "RESULT InvalidBitmap")
                PreparedCropMlKitRecognitionResult.RecognitionFailed ->
                    Log.e(RAW_MLKIT_TAG, "RESULT RecognitionFailed")
            }

            Log.i(RAW_MLKIT_TAG, "===== SCREENSHOT_1_MLKIT_END =====")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(RAW_MLKIT_TAG, "ERROR diagnostic failed", throwable)
        } finally {
            if (prepared.cropped !== prepared.original && !prepared.cropped.isRecycled) {
                prepared.cropped.recycle()
            }
            if (!prepared.original.isRecycled) {
                prepared.original.recycle()
            }
        }
    }

    private fun dumpText(text: Text) {
        Log.i(RAW_MLKIT_TAG, "FULL_TEXT_BEGIN")
        text.text.lineSequence().forEachIndexed { index, line ->
            Log.i(RAW_MLKIT_TAG, "FULL_TEXT[$index]=$line")
        }
        Log.i(RAW_MLKIT_TAG, "FULL_TEXT_END")
        Log.i(RAW_MLKIT_TAG, "BLOCK_COUNT=${text.textBlocks.size}")

        text.textBlocks.forEachIndexed { blockIndex, block ->
            Log.i(
                RAW_MLKIT_TAG,
                "BLOCK[$blockIndex] text=${quoted(block.text)} " +
                    "box=${block.boundingBox} corners=${corners(block.cornerPoints)} " +
                    "language=${block.recognizedLanguage} lines=${block.lines.size}",
            )

            block.lines.forEachIndexed { lineIndex, line ->
                Log.i(
                    RAW_MLKIT_TAG,
                    "LINE[$blockIndex,$lineIndex] text=${quoted(line.text)} " +
                        "box=${line.boundingBox} corners=${corners(line.cornerPoints)} " +
                        "language=${line.recognizedLanguage} confidence=${line.confidence} " +
                        "angle=${line.angle} elements=${line.elements.size}",
                )

                line.elements.forEachIndexed { elementIndex, element ->
                    Log.i(
                        RAW_MLKIT_TAG,
                        "ELEMENT[$blockIndex,$lineIndex,$elementIndex] " +
                            "text=${quoted(element.text)} box=${element.boundingBox} " +
                            "corners=${corners(element.cornerPoints)} " +
                            "language=${element.recognizedLanguage} " +
                            "confidence=${element.confidence} angle=${element.angle} " +
                            "symbols=${element.symbols.size}",
                    )

                    element.symbols.forEachIndexed { symbolIndex, symbol ->
                        Log.i(
                            RAW_MLKIT_TAG,
                            "SYMBOL[$blockIndex,$lineIndex,$elementIndex,$symbolIndex] " +
                                "text=${quoted(symbol.text)} box=${symbol.boundingBox} " +
                                "corners=${corners(symbol.cornerPoints)} " +
                                "language=${symbol.recognizedLanguage} " +
                                "confidence=${symbol.confidence} angle=${symbol.angle}",
                        )
                    }
                }
            }
        }
    }

    private fun confirmedCropOrNull(
        left: Double?,
        top: Double?,
        right: Double?,
        bottom: Double?,
    ): OcrNormalizedCropRect? {
        if (left == null || top == null || right == null || bottom == null) return null
        return runCatching {
            OcrNormalizedCropRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            )
        }.getOrNull()
    }

    private fun corners(points: Array<android.graphics.Point>?): String =
        points?.joinToString(prefix = "[", postfix = "]") { point ->
            "(${point.x},${point.y})"
        } ?: "null"

    private fun quoted(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\""

    private data class PreparedBitmap(
        val original: Bitmap,
        val cropped: Bitmap,
        val cropLeft: Int,
        val cropTop: Int,
        val cropRight: Int,
        val cropBottom: Int,
    )
}
