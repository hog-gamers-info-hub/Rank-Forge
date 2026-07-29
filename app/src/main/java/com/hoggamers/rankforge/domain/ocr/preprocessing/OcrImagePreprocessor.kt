package com.hoggamers.rankforge.domain.ocr.preprocessing

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect

interface OcrPreprocessingImage {
    val width: Int
    val height: Int
}

data class OcrPreprocessingInput(
    val image: OcrPreprocessingImage,
)

enum class OcrPreprocessingCrop {
    OVERALL_SCOREBOARD,
}

enum class OcrPreprocessingStep {
    CROP,
    SCALE,
    CONTRAST_ADJUSTMENT,
}

data class OcrPreprocessingCandidate(
    val order: Int,
    val crop: OcrPreprocessingCrop,
    val cropRect: OcrPixelRect,
    val image: OcrPreprocessingImage,
    val appliedSteps: List<OcrPreprocessingStep>,
    val scaleFactor: Double?,
) {
    init {
        require(order >= 0) { "Candidate order must not be negative." }
        require(appliedSteps.firstOrNull() == OcrPreprocessingStep.CROP) {
            "Every preprocessing candidate must start with a crop step."
        }
        require(scaleFactor == null || OcrPreprocessingStep.SCALE in appliedSteps) {
            "A scale factor requires the scale step."
        }
    }
}

enum class OcrPreprocessingFailure {
    INVALID_DIMENSIONS,
    UNSUPPORTED_LAYOUT,
    UNREADABLE_INPUT,
    INVALID_CROP_BOUNDS,
    RESOURCE_ALLOCATION_FAILED,
    PREPROCESSING_FAILED,
}

sealed interface OcrPreprocessingResult {
    data class Candidates(
        val candidates: List<OcrPreprocessingCandidate>,
    ) : OcrPreprocessingResult {
        init {
            require(candidates.isNotEmpty()) { "Preprocessing candidates must not be empty." }
        }
    }

    data class Failed(
        val failure: OcrPreprocessingFailure,
    ) : OcrPreprocessingResult
}

interface OcrImagePreprocessor {
    suspend fun preprocess(input: OcrPreprocessingInput): OcrPreprocessingResult
}
