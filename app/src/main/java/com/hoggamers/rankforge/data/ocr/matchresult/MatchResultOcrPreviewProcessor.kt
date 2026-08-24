package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File
import kotlinx.coroutines.CancellationException

sealed interface MatchResultOcrPreviewProcessingResult {
    data class Processed(
        val extraction: MatchResultOcrExtractionResult,
        val pixelCrop: OcrPixelCropRect,
        val cropWidth: Int,
        val cropHeight: Int,
    ) : MatchResultOcrPreviewProcessingResult

    data object MissingAsset : MatchResultOcrPreviewProcessingResult
    data object MissingConfirmedCrop : MatchResultOcrPreviewProcessingResult
    data object MissingLocalOriginal : MatchResultOcrPreviewProcessingResult
    data object InvalidCrop : MatchResultOcrPreviewProcessingResult
    data object DecodeFailed : MatchResultOcrPreviewProcessingResult
    data object RecognitionFailed : MatchResultOcrPreviewProcessingResult
}

data class MatchResultOcrPreviewRoleResult(
    val role: MatchResultScreenshotRole,
    val result: MatchResultOcrPreviewProcessingResult,
)

fun interface MatchResultOcrPreviewRunner {
    suspend fun process(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultOcrPreviewProcessingResult
}

fun interface MatchResultOcrPreviewLocalFileResolver {
    fun resolve(relativePath: String): File?
}

fun interface MatchResultOcrPreviewRecognitionSource {
    suspend fun recognize(
        file: File,
        pixelCrop: OcrPixelCropRect,
    ): MatchResultOcrPreviewRecognitionResult
}

sealed interface MatchResultOcrPreviewRecognitionResult {
    data class Recognized(
        val cropWidth: Int,
        val cropHeight: Int,
        val blocks: List<RawOcrBlock>,
    ) : MatchResultOcrPreviewRecognitionResult

    data object DecodeFailed : MatchResultOcrPreviewRecognitionResult
    data object InvalidCrop : MatchResultOcrPreviewRecognitionResult
    data object RecognitionFailed : MatchResultOcrPreviewRecognitionResult
}

fun interface MatchResultOcrPreviewFieldExtractor {
    fun extract(
        role: MatchResultScreenshotRole,
        cropWidth: Int,
        cropHeight: Int,
        blocks: List<RawOcrBlock>,
    ): MatchResultOcrExtractionResult
}

class MatchResultOcrPreviewProcessor(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localFileResolver: MatchResultOcrPreviewLocalFileResolver,
    private val recognitionSource: MatchResultOcrPreviewRecognitionSource,
    private val fieldExtractor: MatchResultOcrPreviewFieldExtractor,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
) {
    suspend fun process(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultOcrPreviewProcessingResult {
        val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()
            ?.takeIf { it.isNotBlank() }
            ?: return MatchResultOcrPreviewProcessingResult.MissingAsset
        val asset = try {
            assetRepository.getByIdentityAndOwner(identity, ownerUserId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchResultOcrPreviewProcessingResult.MissingAsset
        } ?: return MatchResultOcrPreviewProcessingResult.MissingAsset

        val crop = asset.confirmedCropOrNull()
            ?: return MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop
        val dimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return MatchResultOcrPreviewProcessingResult.InvalidCrop
        val pixelCrop = when (
            val validation = OcrCropValidator.validate(
                crop = crop,
                dimensions = dimensions,
                profile = OcrCropValidationProfiles.MatchResult,
            )
        ) {
            is OcrCropValidationResult.Valid -> validation.pixelCrop
                ?: return MatchResultOcrPreviewProcessingResult.InvalidCrop
            is OcrCropValidationResult.Invalid -> return MatchResultOcrPreviewProcessingResult.InvalidCrop
        }

        val file = try {
            localFileResolver.resolve(asset.localRelativePath)
        } catch (_: Throwable) {
            null
        }
            ?.takeIf { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) }
            ?: return MatchResultOcrPreviewProcessingResult.MissingLocalOriginal

        val recognition = try {
            recognitionSource.recognize(file, pixelCrop)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchResultOcrPreviewProcessingResult.RecognitionFailed
        }
        return when (recognition) {
            MatchResultOcrPreviewRecognitionResult.DecodeFailed ->
                MatchResultOcrPreviewProcessingResult.DecodeFailed
            MatchResultOcrPreviewRecognitionResult.InvalidCrop ->
                MatchResultOcrPreviewProcessingResult.InvalidCrop
            MatchResultOcrPreviewRecognitionResult.RecognitionFailed ->
                MatchResultOcrPreviewProcessingResult.RecognitionFailed
            is MatchResultOcrPreviewRecognitionResult.Recognized -> {
                val extraction = try {
                    fieldExtractor.extract(
                        role = identity.role,
                        cropWidth = recognition.cropWidth,
                        cropHeight = recognition.cropHeight,
                        blocks = recognition.blocks,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return MatchResultOcrPreviewProcessingResult.RecognitionFailed
                }
                MatchResultOcrPreviewProcessingResult.Processed(
                    extraction = extraction,
                    pixelCrop = pixelCrop,
                    cropWidth = recognition.cropWidth,
                    cropHeight = recognition.cropHeight,
                )
            }
        }
    }
}

private fun com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity.confirmedCropOrNull(): OcrNormalizedCropRect? {
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    return OcrNormalizedCropRect(left, top, right, bottom)
}
