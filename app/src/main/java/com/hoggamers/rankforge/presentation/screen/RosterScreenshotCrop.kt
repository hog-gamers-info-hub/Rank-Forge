package com.hoggamers.rankforge.presentation.screen

data class NormalizedCropRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

enum class NormalizedCropRectValidationError {
    NON_FINITE_VALUE,
    OUT_OF_BOUNDS,
    INVALID_EDGES,
    TOO_SMALL,
}

sealed interface RosterScreenshotCropValidationResult {
    data class Valid(
        val crop: NormalizedCropRect,
    ) : RosterScreenshotCropValidationResult

    data class Invalid(
        val error: NormalizedCropRectValidationError,
    ) : RosterScreenshotCropValidationResult
}

object NormalizedCropRectValidator {
    const val MINIMUM_CROP_WIDTH = 0.10
    const val MINIMUM_CROP_HEIGHT = 0.10

    fun validate(crop: NormalizedCropRect): RosterScreenshotCropValidationResult = when {
        !crop.left.isFinite() ||
            !crop.top.isFinite() ||
            !crop.right.isFinite() ||
            !crop.bottom.isFinite() -> {
            RosterScreenshotCropValidationResult.Invalid(
                NormalizedCropRectValidationError.NON_FINITE_VALUE,
            )
        }

        crop.left !in 0.0..1.0 ||
            crop.top !in 0.0..1.0 ||
            crop.right !in 0.0..1.0 ||
            crop.bottom !in 0.0..1.0 -> {
            RosterScreenshotCropValidationResult.Invalid(
                NormalizedCropRectValidationError.OUT_OF_BOUNDS,
            )
        }

        crop.right <= crop.left || crop.bottom <= crop.top -> {
            RosterScreenshotCropValidationResult.Invalid(
                NormalizedCropRectValidationError.INVALID_EDGES,
            )
        }

        crop.right - crop.left < MINIMUM_CROP_WIDTH ||
            crop.bottom - crop.top < MINIMUM_CROP_HEIGHT -> {
            RosterScreenshotCropValidationResult.Invalid(
                NormalizedCropRectValidationError.TOO_SMALL,
            )
        }

        else -> RosterScreenshotCropValidationResult.Valid(crop)
    }
}

enum class RosterScreenshotCropCoordinate {
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
}

data class RosterScreenshotCropDraft(
    val left: String = "",
    val top: String = "",
    val right: String = "",
    val bottom: String = "",
) {
    fun withValue(
        coordinate: RosterScreenshotCropCoordinate,
        value: String,
    ): RosterScreenshotCropDraft = when (coordinate) {
        RosterScreenshotCropCoordinate.LEFT -> copy(left = value)
        RosterScreenshotCropCoordinate.TOP -> copy(top = value)
        RosterScreenshotCropCoordinate.RIGHT -> copy(right = value)
        RosterScreenshotCropCoordinate.BOTTOM -> copy(bottom = value)
    }

    fun toNormalizedCropRectOrNull(): NormalizedCropRect? {
        val leftValue = left.toDoubleOrNull() ?: return null
        val topValue = top.toDoubleOrNull() ?: return null
        val rightValue = right.toDoubleOrNull() ?: return null
        val bottomValue = bottom.toDoubleOrNull() ?: return null
        return NormalizedCropRect(leftValue, topValue, rightValue, bottomValue)
    }
}

sealed interface RosterScreenshotCropState {
    data object NotSet : RosterScreenshotCropState

    data class Set(
        val crop: NormalizedCropRect,
    ) : RosterScreenshotCropState
}

enum class RosterScreenshotCropError {
    MISSING_SELECTED_IMAGE,
    INVALID_NUMBER,
    NON_FINITE_VALUE,
    OUT_OF_BOUNDS,
    INVALID_EDGES,
    TOO_SMALL,
}

fun NormalizedCropRectValidationError.toRosterScreenshotCropError(): RosterScreenshotCropError = when (this) {
    NormalizedCropRectValidationError.NON_FINITE_VALUE -> RosterScreenshotCropError.NON_FINITE_VALUE
    NormalizedCropRectValidationError.OUT_OF_BOUNDS -> RosterScreenshotCropError.OUT_OF_BOUNDS
    NormalizedCropRectValidationError.INVALID_EDGES -> RosterScreenshotCropError.INVALID_EDGES
    NormalizedCropRectValidationError.TOO_SMALL -> RosterScreenshotCropError.TOO_SMALL
}
