package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import kotlin.math.ceil
import kotlin.math.floor

data class MatchResultAutoCropObservation(
    val text: String,
    val boundingBox: RawOcrBoundingBox?,
)

data class MatchResultAutoCropEvidence(
    val observations: List<MatchResultAutoCropObservation>,
    val imageDimensions: OcrImageDimensions,
)

sealed interface MatchResultAutoCropResult {
    data class Proposed(
        val crop: OcrNormalizedCropRect,
    ) : MatchResultAutoCropResult

    data object AnchorFourMissing : MatchResultAutoCropResult
    data object AnchorFiveMissing : MatchResultAutoCropResult
    data object RightBoundaryMissing : MatchResultAutoCropResult
    data object InvalidRowPitch : MatchResultAutoCropResult
    data object InvalidCalculatedCrop : MatchResultAutoCropResult
    data object OcrFailed : MatchResultAutoCropResult
}

private fun MatchResultAutoCropObservation.usableBoundingBoxOrNull(
    dimensions: OcrImageDimensions,
): RawOcrBoundingBox? =
    boundingBox?.takeIf { text.trim().isNotEmpty() && it.isUsableFor(dimensions) }

private fun RawOcrBoundingBox.isUsableFor(dimensions: OcrImageDimensions): Boolean =
    right > left &&
        bottom > top &&
        right > 0 &&
        bottom > 0 &&
        left < dimensions.width &&
        top < dimensions.height

class MatchResultAutoCropAnchorDetector {
    fun findAnchorFour(evidence: MatchResultAutoCropEvidence): RawOcrBoundingBox? =
        findExactAnchor(evidence, "4")

    fun findAnchorFive(evidence: MatchResultAutoCropEvidence): RawOcrBoundingBox? =
        findExactAnchor(evidence, "5")

    private fun findExactAnchor(
        evidence: MatchResultAutoCropEvidence,
        expectedText: String,
    ): RawOcrBoundingBox? = evidence.observations
        .asSequence()
        .filter { it.text.trim() == expectedText }
        .mapNotNull { it.usableBoundingBoxOrNull(evidence.imageDimensions) }
        .minWithOrNull(compareBy<RawOcrBoundingBox> { it.left }
            .thenBy { it.top }
            .thenBy { it.right }
            .thenBy { it.bottom })

}

class MatchResultAutoCropCalculator(
    private val anchorDetector: MatchResultAutoCropAnchorDetector = MatchResultAutoCropAnchorDetector(),
) {
    fun calculate(evidence: MatchResultAutoCropEvidence): MatchResultAutoCropResult {
        val anchorFour = anchorDetector.findAnchorFour(evidence)
            ?: return MatchResultAutoCropResult.AnchorFourMissing
        val anchorFive = anchorDetector.findAnchorFive(evidence)
            ?: return MatchResultAutoCropResult.AnchorFiveMissing

        val rightBoundary = evidence.observations
            .asSequence()
            .mapNotNull { it.usableBoundingBoxOrNull(evidence.imageDimensions) }
            .map { it.right }
            .maxOrNull()
            ?: return MatchResultAutoCropResult.RightBoundaryMissing

        val p4CenterY = (anchorFour.top + anchorFour.bottom) / 2.0
        val p5CenterX = (anchorFive.left + anchorFive.right) / 2.0
        val p5CenterY = (anchorFive.top + anchorFive.bottom) / 2.0
        val rowPitch = p5CenterY - p4CenterY
        if (!rowPitch.isFinite() || rowPitch <= 0.0) {
            return MatchResultAutoCropResult.InvalidRowPitch
        }

        val leftRaw = p5CenterX - LEFT_ROW_PITCH_FACTOR * rowPitch
        val topRaw = p5CenterY - TOP_ROW_PITCH_FACTOR * rowPitch
        val bottomRaw = p5CenterY + BOTTOM_ROW_PITCH_FACTOR * rowPitch
        if (!leftRaw.isFinite() || !topRaw.isFinite() || !bottomRaw.isFinite()) {
            return MatchResultAutoCropResult.InvalidCalculatedCrop
        }

        val dimensions = evidence.imageDimensions
        val pixelLeft = floor(leftRaw).toInt().coerceIn(0, dimensions.width)
        val pixelTop = floor(topRaw).toInt().coerceIn(0, dimensions.height)
        val pixelRight = ceil(rightBoundary.toDouble()).toInt().coerceIn(0, dimensions.width)
        val pixelBottom = ceil(bottomRaw).toInt().coerceIn(0, dimensions.height)

        if (pixelLeft >= pixelRight || pixelTop >= pixelBottom) {
            return MatchResultAutoCropResult.InvalidCalculatedCrop
        }

        val pixelCrop = runCatching {
            OcrPixelCropRect(
                left = pixelLeft,
                top = pixelTop,
                right = pixelRight,
                bottom = pixelBottom,
            )
        }.getOrElse { return MatchResultAutoCropResult.InvalidCalculatedCrop }

        return MatchResultAutoCropResult.Proposed(
            crop = OcrNormalizedCropRect.fromPixelRect(pixelCrop, dimensions),
        )
    }

    private companion object {
        const val LEFT_ROW_PITCH_FACTOR = 0.45
        const val TOP_ROW_PITCH_FACTOR = 4.5
        const val BOTTOM_ROW_PITCH_FACTOR = 0.5
    }
}
