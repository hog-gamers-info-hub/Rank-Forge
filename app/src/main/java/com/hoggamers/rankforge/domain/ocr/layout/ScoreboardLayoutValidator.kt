package com.hoggamers.rankforge.domain.ocr.layout

sealed interface ScoreboardLayoutValidationResult {
    data object Compatible : ScoreboardLayoutValidationResult

    data class Incompatible(
        val error: ScoreboardLayoutValidationError,
    ) : ScoreboardLayoutValidationResult
}

enum class ScoreboardLayoutValidationError {
    INVALID_DIMENSIONS,
    NOT_LANDSCAPE,
    UNSUPPORTED_ASPECT_RATIO,
}

class ScoreboardLayoutValidator {
    fun validate(
        layout: ScoreboardLayoutDefinition,
        imageWidth: Int,
        imageHeight: Int,
    ): ScoreboardLayoutValidationResult {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return ScoreboardLayoutValidationResult.Incompatible(
                ScoreboardLayoutValidationError.INVALID_DIMENSIONS,
            )
        }
        if (imageWidth <= imageHeight) {
            return ScoreboardLayoutValidationResult.Incompatible(
                ScoreboardLayoutValidationError.NOT_LANDSCAPE,
            )
        }

        val aspectRatio = imageWidth.toDouble() / imageHeight.toDouble()
        return if (aspectRatio in layout.minimumAspectRatio..layout.maximumAspectRatio) {
            ScoreboardLayoutValidationResult.Compatible
        } else {
            ScoreboardLayoutValidationResult.Incompatible(
                ScoreboardLayoutValidationError.UNSUPPORTED_ASPECT_RATIO,
            )
        }
    }
}
