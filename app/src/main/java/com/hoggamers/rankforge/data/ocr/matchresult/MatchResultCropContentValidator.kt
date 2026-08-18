package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.validation.OcrCropContentValidationResult
import java.io.File

fun interface MatchResultCropContentValidator {
    suspend fun validate(
        role: MatchResultScreenshotRole,
        localFile: File,
        pixelCrop: OcrPixelCropRect,
    ): OcrCropContentValidationResult
}

/** Test/source-compatibility default; production Hilt binds the Android validator. */
object NoOpMatchResultCropContentValidator : MatchResultCropContentValidator {
    override suspend fun validate(
        role: MatchResultScreenshotRole,
        localFile: File,
        pixelCrop: OcrPixelCropRect,
    ): OcrCropContentValidationResult = OcrCropContentValidationResult.Valid
}
