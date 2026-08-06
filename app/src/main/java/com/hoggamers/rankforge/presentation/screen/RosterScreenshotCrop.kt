package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationError
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect

typealias NormalizedCropRect = OcrNormalizedCropRect

object RosterScreenshotCropDefaults {
    val FullImageCrop = NormalizedCropRect(
        left = 0.0,
        top = 0.0,
        right = 1.0,
        bottom = 1.0,
    )
}

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

    fun validate(crop: NormalizedCropRect): RosterScreenshotCropValidationResult = when (
        val result = OcrCropValidator.validate(crop, OcrCropValidationProfiles.Roster)
    ) {
        is OcrCropValidationResult.Valid -> RosterScreenshotCropValidationResult.Valid(result.crop)
        is OcrCropValidationResult.Invalid -> RosterScreenshotCropValidationResult.Invalid(
            result.error.toNormalizedCropRectValidationError(),
        )
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

    fun isBlank(): Boolean =
        left.isBlank() && top.isBlank() && right.isBlank() && bottom.isBlank()
}

fun NormalizedCropRect.toRosterScreenshotCropDraft(): RosterScreenshotCropDraft =
    RosterScreenshotCropDraft(
        left = left.toString(),
        top = top.toString(),
        right = right.toString(),
        bottom = bottom.toString(),
    )

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

private fun OcrCropValidationError.toNormalizedCropRectValidationError(): NormalizedCropRectValidationError =
    when (this) {
        OcrCropValidationError.NON_FINITE_VALUE -> NormalizedCropRectValidationError.NON_FINITE_VALUE
        OcrCropValidationError.OUT_OF_BOUNDS -> NormalizedCropRectValidationError.OUT_OF_BOUNDS
        OcrCropValidationError.INVALID_EDGES -> NormalizedCropRectValidationError.INVALID_EDGES
        OcrCropValidationError.TOO_SMALL -> NormalizedCropRectValidationError.TOO_SMALL
        OcrCropValidationError.INVALID_IMAGE_DIMENSIONS,
        OcrCropValidationError.EMPTY_PIXEL_CROP,
        -> NormalizedCropRectValidationError.INVALID_EDGES
    }
