package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitRecognitionResult
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitRecognizer
import com.hoggamers.rankforge.data.ocr.PreparedCropMlKitTextRecognizer
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldExtractor
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.validation.MatchResultCropContentEvidence
import com.hoggamers.rankforge.domain.ocr.validation.MatchResultCropContentEvidenceEvaluator
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MATCH_RESULT_OCR_PREVIEW_TAG = "RF_MATCH_RESULT_OCR_PREVIEW"
private const val MATCH_RESULT_CROP_CALIBRATION_TAG = "RF_RESULT_CROP_CALIBRATION"

class AndroidMatchResultOcrPreviewProcessor(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localFileResolver: MatchResultOcrPreviewLocalFileResolver,
) : MatchResultOcrPreviewRunner {
    override suspend fun process(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultOcrPreviewProcessingResult {
        if (!isAndroidRuntime()) return MatchResultOcrPreviewProcessingResult.RecognitionFailed

        return try {
            MatchResultOcrPreviewProcessor(
                assetRepository = assetRepository,
                localFileResolver = localFileResolver,
                recognitionSource = AndroidMatchResultOcrPreviewRecognitionSource(
                    PreparedCropMlKitTextRecognizer(DefaultMlKitTextRecognizerFactory()),
                ),
                fieldExtractor = MatchResultOcrPreviewFieldExtractor {
                    role, cropWidth, cropHeight, blocks ->
                    logCalibrationEvidenceSafely(
                        roleName = role.name,
                        evidenceProvider = {
                            MatchResultCropContentEvidenceEvaluator().evaluate(
                                role = role,
                                cropWidth = cropWidth,
                                cropHeight = cropHeight,
                                blocks = blocks,
                            )
                        },
                    )
                    MatchResultOcrFieldExtractor().extract(role, cropWidth, cropHeight, blocks)
                },
            ).process(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchResultOcrPreviewProcessingResult.RecognitionFailed
        }
    }

    suspend fun processAndLog(
        identity: MatchResultScreenshotIdentity,
    ) {
        if (!isAndroidRuntime()) return

        val result = process(identity)

        logResult(identity, result)
    }

    private fun logResult(
        identity: MatchResultScreenshotIdentity,
        result: MatchResultOcrPreviewProcessingResult,
    ) {
        when (result) {
            is MatchResultOcrPreviewProcessingResult.Processed -> {
                val extraction = result.extraction
                Log.i(MATCH_RESULT_OCR_PREVIEW_TAG, "===== MATCH_RESULT_OCR_PREVIEW_BEGIN =====")
                Log.i(
                    MATCH_RESULT_OCR_PREVIEW_TAG,
                    "role=${identity.role.name} crop=${result.cropWidth}x${result.cropHeight}",
                )
                extraction.rows.sortedBy { it.position }.forEach { row ->
                    Log.i(MATCH_RESULT_OCR_PREVIEW_TAG, row.toLogLine())
                }
                Log.i(MATCH_RESULT_OCR_PREVIEW_TAG, "ROW_COUNT=${extraction.rows.size}")
                Log.i(
                    MATCH_RESULT_OCR_PREVIEW_TAG,
                    "IGNORED_ROW_COUNT=${extraction.ignoredLowerRows.size}",
                )
                Log.i(
                    MATCH_RESULT_OCR_PREVIEW_TAG,
                    "MANUAL_REVIEW_COUNT=${extraction.manualReviewRows.size}",
                )
                Log.i(MATCH_RESULT_OCR_PREVIEW_TAG, "===== MATCH_RESULT_OCR_PREVIEW_END =====")
            }

            MatchResultOcrPreviewProcessingResult.MissingAsset -> logResultOnly("MissingAsset")
            MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop ->
                logResultOnly("MissingConfirmedCrop")
            MatchResultOcrPreviewProcessingResult.MissingLocalOriginal ->
                logResultOnly("MissingLocalOriginal")
            MatchResultOcrPreviewProcessingResult.InvalidCrop -> logResultOnly("InvalidCrop")
            MatchResultOcrPreviewProcessingResult.DecodeFailed -> logResultOnly("DecodeFailed")
            MatchResultOcrPreviewProcessingResult.RecognitionFailed ->
                logResultOnly("RecognitionFailed")
        }
    }

    private fun logCalibrationEvidenceSafely(
        roleName: String,
        evidenceProvider: () -> MatchResultCropContentEvidence,
    ) {
        try {
            logCalibrationEvidence(evidenceProvider())
        } catch (throwable: Throwable) {
            Log.w(
                MATCH_RESULT_CROP_CALIBRATION_TAG,
                "CALIBRATION_LOG_FAILED role=$roleName type=${throwable::class.java.simpleName}",
            )
        }
    }

    private fun logCalibrationEvidence(evidence: MatchResultCropContentEvidence) {
        Log.i(
            MATCH_RESULT_CROP_CALIBRATION_TAG,
            "===== RESULT_CROP_CALIBRATION_BEGIN =====",
        )
        Log.i(
            MATCH_RESULT_CROP_CALIBRATION_TAG,
            "role=${evidence.role.name} crop=${evidence.cropWidth}x${evidence.cropHeight} " +
                "observations=${evidence.nonBlankObservationCount} " +
                "playerLike=${evidence.playerLikeObservationCount} " +
                "killLike=${evidence.killLikeObservationCount}",
        )
        evidence.placementEvidence.forEach { placement ->
            Log.i(
                MATCH_RESULT_CROP_CALIBRATION_TAG,
                "PLACEMENT expected=${placement.expectedPlacement} " +
                    "candidates=${placement.matchingCandidateCount} " +
                    "minCenter=${placement.minimumNormalizedCenterDistance.calibrationValue()}",
            )
        }
        evidence.playerFieldEvidence.forEach { field ->
            Log.i(
                MATCH_RESULT_CROP_CALIBRATION_TAG,
                "PLAYER field=${field.fieldId} " +
                    "coverage=${field.maximumExpectedRegionCoverageRatio.calibrationValue()} " +
                    "containment=${field.maximumObservationContainmentRatio.calibrationValue()} " +
                    "minCenter=${field.minimumNormalizedCenterDistance.calibrationValue()}",
            )
        }
        evidence.killFieldEvidence.forEach { field ->
            Log.i(
                MATCH_RESULT_CROP_CALIBRATION_TAG,
                "KILL field=${field.fieldId} " +
                    "coverage=${field.maximumExpectedRegionCoverageRatio.calibrationValue()} " +
                    "containment=${field.maximumObservationContainmentRatio.calibrationValue()} " +
                    "minCenter=${field.minimumNormalizedCenterDistance.calibrationValue()}",
            )
        }
        Log.i(
            MATCH_RESULT_CROP_CALIBRATION_TAG,
            "H_BANDS=${evidence.spatialDistribution.horizontalBandCounts.joinToString(",")} " +
                "V_BANDS=${evidence.spatialDistribution.verticalBandCounts.joinToString(",")}",
        )
        Log.i(
            MATCH_RESULT_CROP_CALIBRATION_TAG,
            "===== RESULT_CROP_CALIBRATION_END =====",
        )
    }

    private fun Double?.calibrationValue(): String =
        this?.let { String.format(Locale.US, "%.6f", it) } ?: "null"

    private fun MatchResultOcrRow.toLogLine(): String {
        val slots = (1..4).joinToString(" | ") { slot ->
            val playerSlot = playerSlots.firstOrNull { it.slot == slot }
            "P$slot=${quoted(playerSlot?.player?.resolvedText.orEmpty())} " +
                "K$slot=${quoted(playerSlot?.kill?.resolvedText.orEmpty())}"
        }
        return "ROW[$position] $slots"
    }

    private fun logResultOnly(result: String) {
        Log.i(MATCH_RESULT_OCR_PREVIEW_TAG, "RESULT=$result")
    }

    private fun quoted(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun isAndroidRuntime(): Boolean =
        System.getProperty("java.runtime.name") == "Android Runtime" ||
            System.getProperty("java.vm.name") == "Dalvik"
}

private class AndroidMatchResultOcrPreviewRecognitionSource(
    private val recognizer: PreparedCropMlKitRecognizer,
) : MatchResultOcrPreviewRecognitionSource {
    override suspend fun recognize(
        file: File,
        pixelCrop: OcrPixelCropRect,
    ): MatchResultOcrPreviewRecognitionResult = withContext(Dispatchers.IO) {
        val original = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Throwable) {
            null
        } ?: return@withContext MatchResultOcrPreviewRecognitionResult.DecodeFailed

        if (
            pixelCrop.left < 0 ||
            pixelCrop.top < 0 ||
            pixelCrop.right > original.width ||
            pixelCrop.bottom > original.height
        ) {
            recycle(original)
            return@withContext MatchResultOcrPreviewRecognitionResult.InvalidCrop
        }

        val cropped = try {
            Bitmap.createBitmap(
                original,
                pixelCrop.left,
                pixelCrop.top,
                pixelCrop.width,
                pixelCrop.height,
            )
        } catch (_: Throwable) {
            recycle(original)
            return@withContext MatchResultOcrPreviewRecognitionResult.InvalidCrop
        }

        try {
            when (val result = recognizer.recognize(cropped)) {
                is PreparedCropMlKitRecognitionResult.Recognized ->
                    MatchResultOcrPreviewRecognitionResult.Recognized(
                        cropWidth = cropped.width,
                        cropHeight = cropped.height,
                        blocks = result.text.toRawOcrBlocks(),
                    )
                PreparedCropMlKitRecognitionResult.InvalidBitmap ->
                    MatchResultOcrPreviewRecognitionResult.InvalidCrop
                PreparedCropMlKitRecognitionResult.RecognitionFailed ->
                    MatchResultOcrPreviewRecognitionResult.RecognitionFailed
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchResultOcrPreviewRecognitionResult.RecognitionFailed
        } finally {
            if (cropped !== original) recycle(cropped)
            recycle(original)
        }
    }

    private fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
