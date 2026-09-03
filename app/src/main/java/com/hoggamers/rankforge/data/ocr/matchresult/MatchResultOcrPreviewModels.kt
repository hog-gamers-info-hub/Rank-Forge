package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File

sealed interface MatchResultOcrPreviewProcessingResult {
    data class Processed(
        val extraction: MatchResultOcrExtractionResult,
        val pixelCrop: OcrPixelCropRect,
        val cropWidth: Int,
        val cropHeight: Int,
        val source: MatchResultOcrPreviewSource = MatchResultOcrPreviewSource.NEW_PP_POSITION,
    ) : MatchResultOcrPreviewProcessingResult

    data object MissingAsset : MatchResultOcrPreviewProcessingResult
    data object MissingConfirmedCrop : MatchResultOcrPreviewProcessingResult
    data object MissingLocalOriginal : MatchResultOcrPreviewProcessingResult
    data object InvalidCrop : MatchResultOcrPreviewProcessingResult
    data object DecodeFailed : MatchResultOcrPreviewProcessingResult
    data object RecognitionFailed : MatchResultOcrPreviewProcessingResult
    data object SemanticRoleResolutionFailed : MatchResultOcrPreviewProcessingResult
    data class SemanticRoleProcessingFailed(
        val role: MatchResultScreenshotRole,
    ) : MatchResultOcrPreviewProcessingResult
}

enum class MatchResultOcrPreviewSource {
    NEW_PP_POSITION,
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

internal interface MatchResultPairOcrPreviewRunner : MatchResultOcrPreviewRunner {
    suspend fun processPair(
        identities: Map<MatchResultScreenshotRole, MatchResultScreenshotIdentity>,
    ): Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>
}

fun interface MatchResultOcrPreviewLocalFileResolver {
    fun resolve(relativePath: String): File?
}
