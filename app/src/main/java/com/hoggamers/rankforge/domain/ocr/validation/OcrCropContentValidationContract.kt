package com.hoggamers.rankforge.domain.ocr.validation

/**
 * Semantic reasons that prove a crop is not safe for the expected OCR role.
 *
 * These reasons describe crop-content failures only. Geometry failures remain owned by the
 * existing crop geometry contract.
 */
enum class OcrCropContentInvalidReason {
    EXPECTED_STRUCTURE_MISSING,
    REQUIRED_REGION_MISSING,
    CROP_INCOMPLETE,
    WRONG_CONTENT,
}

/**
 * Technical reasons that prevented Rank Forge from deciding whether the crop content is valid.
 *
 * These reasons are intentionally separate from [OcrCropContentInvalidReason]: inability to
 * validate is not evidence that the user selected an incorrect crop.
 */
enum class OcrCropContentIndeterminateReason {
    IMAGE_DECODE_FAILED,
    INVALID_PREPARED_BITMAP,
    OCR_RECOGNITION_FAILED,
    VALIDATION_EXECUTION_FAILED,
}

/**
 * Common semantic crop-content validation outcome used by all OCR-critical screenshot roles.
 *
 * Only [Valid] is eligible to become authoritative OCR input. Coroutine cancellation is
 * intentionally not modeled as an outcome and must propagate through implementations.
 */
sealed interface OcrCropContentValidationResult {
    object Valid : OcrCropContentValidationResult

    data class Invalid(
        val reason: OcrCropContentInvalidReason,
    ) : OcrCropContentValidationResult

    data class Indeterminate(
        val reason: OcrCropContentIndeterminateReason,
    ) : OcrCropContentValidationResult
}
