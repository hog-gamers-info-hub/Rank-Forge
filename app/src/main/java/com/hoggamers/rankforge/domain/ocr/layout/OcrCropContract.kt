package com.hoggamers.rankforge.domain.ocr.layout

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class OcrImageDimensions(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "Original image width must be positive." }
        require(height > 0) { "Original image height must be positive." }
    }

    companion object {
        fun from(width: Int, height: Int): OcrImageDimensions? =
            if (width > 0 && height > 0) OcrImageDimensions(width, height) else null
    }
}

data class OcrNormalizedCropRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val normalizedWidth: Double
        get() = right - left

    val normalizedHeight: Double
        get() = bottom - top

    fun toPixelRectOrNull(dimensions: OcrImageDimensions): OcrPixelCropRect? {
        if (!allValuesAreFinite()) return null
        if (left !in 0.0..1.0 || top !in 0.0..1.0 || right !in 0.0..1.0 || bottom !in 0.0..1.0) {
            return null
        }
        if (right <= left || bottom <= top) return null

        val pixelLeft = floor(left * dimensions.width).toInt().coerceIn(0, dimensions.width)
        val pixelTop = floor(top * dimensions.height).toInt().coerceIn(0, dimensions.height)
        val pixelRight = ceil(right * dimensions.width).toInt().coerceIn(pixelLeft, dimensions.width)
        val pixelBottom = ceil(bottom * dimensions.height).toInt().coerceIn(pixelTop, dimensions.height)

        return if (pixelRight > pixelLeft && pixelBottom > pixelTop) {
            OcrPixelCropRect(
                left = pixelLeft,
                top = pixelTop,
                right = pixelRight,
                bottom = pixelBottom,
            )
        } else {
            null
        }
    }

    internal fun allValuesAreFinite(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    companion object {
        fun fromPixelRect(
            pixelRect: OcrPixelCropRect,
            dimensions: OcrImageDimensions,
        ): OcrNormalizedCropRect {
            require(pixelRect.right <= dimensions.width) {
                "Pixel crop must not extend beyond original image width."
            }
            require(pixelRect.bottom <= dimensions.height) {
                "Pixel crop must not extend beyond original image height."
            }

            return OcrNormalizedCropRect(
                left = pixelRect.left.toDouble() / dimensions.width,
                top = pixelRect.top.toDouble() / dimensions.height,
                right = pixelRect.right.toDouble() / dimensions.width,
                bottom = pixelRect.bottom.toDouble() / dimensions.height,
            )
        }
    }
}

data class OcrPixelCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "Pixel crop left must not be negative." }
        require(top >= 0) { "Pixel crop top must not be negative." }
        require(right > left) { "Pixel crop right must be greater than left." }
        require(bottom > top) { "Pixel crop bottom must be greater than top." }
    }

    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

    companion object {
        fun fromLeftTopWidthHeight(
            left: Int,
            top: Int,
            width: Int,
            height: Int,
        ): OcrPixelCropRect {
            require(width > 0) { "Pixel crop width must be positive." }
            require(height > 0) { "Pixel crop height must be positive." }
            return OcrPixelCropRect(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
            )
        }
    }
}

data class OcrCropPoint(
    val x: Int,
    val y: Int,
)

enum class OcrCropValidationError {
    NON_FINITE_VALUE,
    OUT_OF_BOUNDS,
    INVALID_EDGES,
    TOO_SMALL,
    INVALID_IMAGE_DIMENSIONS,
    EMPTY_PIXEL_CROP,
}

sealed interface OcrCropValidationResult {
    data class Valid(
        val crop: OcrNormalizedCropRect,
        val pixelCrop: OcrPixelCropRect? = null,
    ) : OcrCropValidationResult

    data class Invalid(
        val error: OcrCropValidationError,
    ) : OcrCropValidationResult
}

data class OcrCropValidationProfile(
    val id: String,
    val minimumNormalizedWidth: Double,
    val minimumNormalizedHeight: Double,
) {
    init {
        require(id.isNotBlank()) { "Crop validation profile id must not be blank." }
        require(minimumNormalizedWidth.isFinite() && minimumNormalizedWidth > 0.0) {
            "Minimum normalized width must be positive and finite."
        }
        require(minimumNormalizedHeight.isFinite() && minimumNormalizedHeight > 0.0) {
            "Minimum normalized height must be positive and finite."
        }
        require(minimumNormalizedWidth <= 1.0) { "Minimum normalized width must not exceed 1.0." }
        require(minimumNormalizedHeight <= 1.0) { "Minimum normalized height must not exceed 1.0." }
    }
}

object OcrCropValidationProfiles {
    val MatchResult = OcrCropValidationProfile(
        id = "match-result",
        minimumNormalizedWidth = 0.10,
        minimumNormalizedHeight = 0.10,
    )

    val Roster = OcrCropValidationProfile(
        id = "roster",
        minimumNormalizedWidth = 0.10,
        minimumNormalizedHeight = 0.10,
    )

    val Lobby = OcrCropValidationProfile(
        id = "lobby",
        minimumNormalizedWidth = 0.10,
        minimumNormalizedHeight = 0.10,
    )
}

object OcrCropValidator {
    fun validate(
        crop: OcrNormalizedCropRect,
        profile: OcrCropValidationProfile,
    ): OcrCropValidationResult = when {
        !crop.allValuesAreFinite() -> OcrCropValidationResult.Invalid(
            OcrCropValidationError.NON_FINITE_VALUE,
        )

        crop.left !in 0.0..1.0 ||
            crop.top !in 0.0..1.0 ||
            crop.right !in 0.0..1.0 ||
            crop.bottom !in 0.0..1.0 -> OcrCropValidationResult.Invalid(
            OcrCropValidationError.OUT_OF_BOUNDS,
        )

        crop.right <= crop.left || crop.bottom <= crop.top -> OcrCropValidationResult.Invalid(
            OcrCropValidationError.INVALID_EDGES,
        )

        crop.normalizedWidth < profile.minimumNormalizedWidth ||
            crop.normalizedHeight < profile.minimumNormalizedHeight -> OcrCropValidationResult.Invalid(
            OcrCropValidationError.TOO_SMALL,
        )

        else -> OcrCropValidationResult.Valid(crop)
    }

    fun validate(
        crop: OcrNormalizedCropRect,
        dimensions: OcrImageDimensions?,
        profile: OcrCropValidationProfile,
    ): OcrCropValidationResult {
        if (dimensions == null) {
            return OcrCropValidationResult.Invalid(OcrCropValidationError.INVALID_IMAGE_DIMENSIONS)
        }

        val normalized = validate(crop, profile)
        if (normalized is OcrCropValidationResult.Invalid) return normalized

        val pixelCrop = crop.toPixelRectOrNull(dimensions)
            ?: return OcrCropValidationResult.Invalid(OcrCropValidationError.EMPTY_PIXEL_CROP)
        return OcrCropValidationResult.Valid(crop = crop, pixelCrop = pixelCrop)
    }
}

enum class OcrCropPreprocessingSourceMode {
    ORIGINAL_WITH_CONFIRMED_CROP,
    PREPARED_CONFIRMED_CROP,
    LEGACY_FIXED_LAYOUT,
}

data class OcrCandidateToOriginalCoordinateMapper(
    val cropInOriginal: OcrPixelCropRect,
    val candidateScaleFactor: Double = 1.0,
) {
    init {
        require(candidateScaleFactor.isFinite() && candidateScaleFactor > 0.0) {
            "Candidate scale factor must be positive and finite."
        }
    }

    fun mapPointToOriginal(point: OcrCropPoint): OcrCropPoint = OcrCropPoint(
        x = cropInOriginal.left + (point.x / candidateScaleFactor).roundToInt(),
        y = cropInOriginal.top + (point.y / candidateScaleFactor).roundToInt(),
    )

    fun mapRectToOriginal(candidateLocalRect: OcrPixelCropRect): OcrPixelCropRect {
        val mappedLeft = cropInOriginal.left + floor(candidateLocalRect.left / candidateScaleFactor).toInt()
        val mappedTop = cropInOriginal.top + floor(candidateLocalRect.top / candidateScaleFactor).toInt()
        val mappedRight = cropInOriginal.left + ceil(candidateLocalRect.right / candidateScaleFactor).toInt()
        val mappedBottom = cropInOriginal.top + ceil(candidateLocalRect.bottom / candidateScaleFactor).toInt()
        return OcrPixelCropRect(
            left = mappedLeft,
            top = mappedTop,
            right = mappedRight,
            bottom = mappedBottom,
        )
    }
}
