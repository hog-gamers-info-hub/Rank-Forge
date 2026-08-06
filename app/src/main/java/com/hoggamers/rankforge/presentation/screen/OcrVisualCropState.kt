package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfile
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect

object OcrVisualCropDefaults {
    val FullImageCrop = OcrNormalizedCropRect(
        left = 0.0,
        top = 0.0,
        right = 1.0,
        bottom = 1.0,
    )
}

enum class OcrVisualCropResizeHandle {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
}

data class OcrVisualCropPixelSize(
    val width: Int,
    val height: Int,
)

fun calculateVisualCropPixelSize(
    crop: OcrNormalizedCropRect,
    sourceWidth: Int?,
    sourceHeight: Int?,
): OcrVisualCropPixelSize? {
    val dimensions = OcrImageDimensions.from(
        width = sourceWidth ?: return null,
        height = sourceHeight ?: return null,
    ) ?: return null
    val pixelCrop = crop.toPixelRectOrNull(dimensions) ?: return null
    return OcrVisualCropPixelSize(
        width = pixelCrop.width,
        height = pixelCrop.height,
    )
}

object OcrVisualCropGeometry {
    fun move(
        crop: OcrNormalizedCropRect,
        normalizedDeltaX: Double,
        normalizedDeltaY: Double,
    ): OcrNormalizedCropRect {
        val width = crop.normalizedWidth
        val height = crop.normalizedHeight
        val left = (crop.left + normalizedDeltaX).coerceIn(0.0, 1.0 - width)
        val top = (crop.top + normalizedDeltaY).coerceIn(0.0, 1.0 - height)
        return OcrNormalizedCropRect(
            left = left,
            top = top,
            right = left + width,
            bottom = top + height,
        )
    }

    fun resize(
        crop: OcrNormalizedCropRect,
        handle: OcrVisualCropResizeHandle,
        normalizedDeltaX: Double,
        normalizedDeltaY: Double,
        profile: OcrCropValidationProfile,
    ): OcrNormalizedCropRect = when (handle) {
        OcrVisualCropResizeHandle.TOP -> resizeTop(crop, normalizedDeltaY, profile)
        OcrVisualCropResizeHandle.BOTTOM -> resizeBottom(crop, normalizedDeltaY, profile)
        OcrVisualCropResizeHandle.LEFT -> resizeLeft(crop, normalizedDeltaX, profile)
        OcrVisualCropResizeHandle.RIGHT -> resizeRight(crop, normalizedDeltaX, profile)
    }

    private fun resizeTop(
        crop: OcrNormalizedCropRect,
        normalizedDeltaY: Double,
        profile: OcrCropValidationProfile,
    ): OcrNormalizedCropRect {
        val maximumTop = (crop.bottom - profile.minimumNormalizedHeight).coerceAtLeast(0.0)
        val top = (crop.top + normalizedDeltaY).coerceIn(0.0, maximumTop)
        return crop.copy(top = top)
    }

    private fun resizeBottom(
        crop: OcrNormalizedCropRect,
        normalizedDeltaY: Double,
        profile: OcrCropValidationProfile,
    ): OcrNormalizedCropRect {
        val minimumBottom = (crop.top + profile.minimumNormalizedHeight).coerceAtMost(1.0)
        val bottom = (crop.bottom + normalizedDeltaY).coerceIn(minimumBottom, 1.0)
        return crop.copy(bottom = bottom)
    }

    private fun resizeLeft(
        crop: OcrNormalizedCropRect,
        normalizedDeltaX: Double,
        profile: OcrCropValidationProfile,
    ): OcrNormalizedCropRect {
        val maximumLeft = (crop.right - profile.minimumNormalizedWidth).coerceAtLeast(0.0)
        val left = (crop.left + normalizedDeltaX).coerceIn(0.0, maximumLeft)
        return crop.copy(left = left)
    }

    private fun resizeRight(
        crop: OcrNormalizedCropRect,
        normalizedDeltaX: Double,
        profile: OcrCropValidationProfile,
    ): OcrNormalizedCropRect {
        val minimumRight = (crop.left + profile.minimumNormalizedWidth).coerceAtMost(1.0)
        val right = (crop.right + normalizedDeltaX).coerceIn(minimumRight, 1.0)
        return crop.copy(right = right)
    }
}
